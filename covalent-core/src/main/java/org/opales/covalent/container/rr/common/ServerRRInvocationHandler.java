/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.rr.common;

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

import org.opales.covalent.container.introspection.IntrospectionServices;
import org.opales.covalent.container.metamodel.MetamodelPortReadServices;
import org.opales.covalent.container.registry.PortRegistryServices;
import org.opales.covalent.container.util.RuntimeCheck;

/**
 * Provider-side request/response invocation handler: backs the proxy
 * published for a {@code @Services} or {@code @Parameters} provider,
 * lazily resolving and forwarding calls to the actual provider instance.
 * Passivated components refuse calls with an {@link IllegalStateException}
 * (see {@link #activate()}/{@link #passivate()} on the parent class). Used
 * by both the services and parameters flavors of the request/response
 * ({@code RR}) infrastructure; concrete subclasses supply the
 * flavor-specific lookup and validation behavior via the abstract methods
 * below.
 *
 * @param <T> the request/response interface (a services or parameters
 *            interface) this handler provides
 */
public abstract class ServerRRInvocationHandler<T> extends RRInvocationHandler {

	private static Logger log = Logger.getLogger(ServerRRInvocationHandler.class.getName());

	/** The services or parameters interface this handler provides. */
	protected final Class<T> providedInterface;

	/** The component type providing {@link #providedInterface}, resolved lazily. */
	protected volatile Class<?> componentType;

	/** The actual provider instance calls are forwarded to, resolved lazily. */
	protected volatile T providedInstance;

	/** The introspection service used to resolve/validate the provider. */
	protected IntrospectionServices introspectionServices;

	/** The port registry service used to resolve the provider instance. */
	protected PortRegistryServices portRegistryServices;

	/** The metamodel-port-read service used to resolve the provider. */
	protected MetamodelPortReadServices MetamodelPortReadServices;

	/** Supplies whether this handler's owning container has been disposed. */
	protected BooleanSupplier disposedSupplier;

	/** "services" or "parameters", used in log and error messages. */
	private final String kind;

	/**
	 * Creates a new handler for the given provided interface.
	 *
	 * @param providedInterface the services or parameters interface this
	 *                          handler provides; must not be {@code null}
	 * @param kind              a human-readable port kind ("services" or
	 *                          "parameters"), used in log and error messages
	 */
	public ServerRRInvocationHandler(final Class<T> providedInterface, final String kind) {
		RuntimeCheck.notNull(providedInterface, "providedInterface");
		this.providedInterface = providedInterface;
		this.kind = kind;
	}

	/**
	 * Sets the internal service used for introspection.
	 *
	 * @param introspectionServices the introspection service
	 */
	public void setIntrospectionServices(final IntrospectionServices introspectionServices) {
		this.introspectionServices = introspectionServices;
	}

	/**
	 * Sets the internal service used to look up ports in the registry.
	 *
	 * @param portRegistryServices the port registry service
	 */
	public void setPortRegistryServices(final PortRegistryServices portRegistryServices) {
		this.portRegistryServices = portRegistryServices;
	}

	/**
	 * Sets the internal service used to read the metamodel's port
	 * information.
	 *
	 * @param MetamodelPortReadServices the metamodel port read service
	 */
	public void setMetamodelPortReadServices(final MetamodelPortReadServices MetamodelPortReadServices) {
		this.MetamodelPortReadServices = MetamodelPortReadServices;
	}

	/**
	 * Sets the supplier consulted at the start of every call to decide
	 * whether this handler's owning container has been disposed. Checked in
	 * {@link #invokeMethod} before the generic passivation check, so a call
	 * made after {@code dispose()} gets a dedicated, clear message instead of
	 * the generic "Component is passivated" one - which remains accurate for
	 * every other, unrelated reason a provider can be passivated.
	 *
	 * @param disposedSupplier supplies whether the owning container is
	 *                         currently disposed
	 */
	public void setDisposedSupplier(final BooleanSupplier disposedSupplier) {
		this.disposedSupplier = disposedSupplier;
	}

	@Override
	public Object invokeMethod(final Object proxy, final Method method, final Object[] args) throws Throwable {
		if (this.disposedSupplier != null && this.disposedSupplier.getAsBoolean()) {
			throw new IllegalStateException("Container is disposed and no longer available.");
		}
		if (this.isPassivated()) {
			throw new IllegalStateException("Cannot invoke " + this.kind + ": " + this.providedInterface.getSimpleName()
					+ ". Component is passivated.");
		}
		if (this.providedInstance == null) {
			this.lookupInstance();
		}
		return this.invokeTarget(this.providedInstance, method, args);
	}

	@Override
	protected String describeCall(final Method method) {
		return this.kind + ": " + method.getName() + " from " + this.providedInterface;
	}

	private void lookupInstance() {
		if (this.componentType == null) {
			// may happen if user was declared before provider
			this.componentType = this.getComponentTypeForProvidedInterface();
			log.fine("Lazily get " + this.kind + " provider component type: " + this.componentType);
		}
		if (this.componentType == null) {
			throw new IllegalStateException("No component type found for " + this.kind + " " + this.providedInterface);
		}
		// we must not make a lookup or we'll get the same proxy as the one we're into
		// and make an infinite loop on next method call
		this.providedInstance = this.getInstance(this.providedInterface);
		// try to lazily get instance
		if (this.providedInstance == null) {
			String message = "No provider found for " + this.kind + " " + this.providedInterface + ".";
			if (!this.checkInterface(this.providedInterface)) {
				message += " This interface is not annotated with @" + this.getRRAnnotation().getSimpleName() + ".";
			}
			throw new IllegalStateException(message);
		} else {
			log.fine("Lazily get " + this.kind + " provider instance: " + this.providedInstance);
		}
	}

	/**
	 * Resolves the component type that currently provides
	 * {@link #providedInterface}, or {@code null} if none is declared.
	 *
	 * @return the providing component type, or {@code null}
	 */
	protected abstract Class<?> getComponentTypeForProvidedInterface();

	/**
	 * Resolves the actual provider instance for the given interface.
	 *
	 * @param anInterface the services or parameters interface to resolve
	 * @return the provider instance, or {@code null} if none is available
	 */
	protected abstract T getInstance(final Class<T> anInterface);

	/**
	 * Checks whether the given interface is annotated with the annotation
	 * expected for this handler's flavor (e.g. {@code @Services} or
	 * {@code @Parameters}), used to produce a clearer error message when no
	 * provider is found.
	 *
	 * @param anInterface the interface to check
	 * @return {@code true} if the interface carries the expected annotation
	 */
	protected abstract boolean checkInterface(final Class<T> anInterface);

	/**
	 * Returns the flavor-specific annotation type expected on the provided
	 * interface (e.g. {@code Services.class} or {@code Parameters.class}),
	 * used to name it in error messages.
	 *
	 * @return the expected annotation type
	 */
	protected abstract Class<?> getRRAnnotation();

}
