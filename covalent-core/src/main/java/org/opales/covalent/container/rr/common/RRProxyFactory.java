/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.rr.common;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

import org.opales.covalent.container.directory.NamingServices;
import org.opales.covalent.container.exceptions.NotBoundException;
import org.opales.covalent.container.introspection.IntrospectionServices;
import org.opales.covalent.container.invocation.CovalentInvocationHandler;
import org.opales.covalent.container.metamodel.MetamodelPortReadServices;
import org.opales.covalent.container.registry.PortRegistryServices;
import org.opales.covalent.container.util.RuntimeCheck;

/**
 * Creates the JDK dynamic proxies backing the request/response ({@code RR})
 * infrastructure: provider-side proxies (wrapping a
 * {@link ServerRRInvocationHandler}), client-side proxies (wrapping a
 * {@link ClientRRInvocationHandler}), and temporary surrogate proxies
 * (wrapping a {@link RRSurrogateProxyInvocationHandler}) used while no real
 * provider is declared yet. Collaborates with {@link RRManagerImpl}, which
 * owns the proxy lifecycle and bookkeeping.
 */
public class RRProxyFactory {

	private static Logger log = Logger.getLogger(RRProxyFactory.class.getName());

	/** Creates a new proxy factory. */
	public RRProxyFactory() {
	}

	/** The introspection service, propagated to provider-side proxies. */
	protected IntrospectionServices introspectionServices;

	/** The port registry service, propagated to provider-side proxies. */
	protected PortRegistryServices portRegistryServices;

	/** The metamodel-port-read service, propagated to provider-side proxies. */
	protected MetamodelPortReadServices MetamodelPortReadServices;

	/** Supplies whether calls through created proxies should be instrumented. */
	protected BooleanSupplier instrumentationSupplier;

	/** Supplies whether the owning container has been disposed, propagated to provider-side proxies. */
	protected BooleanSupplier disposedSupplier;

	/**
	 * Sets the internal service used for introspection, propagated to
	 * provider-side proxies created afterward.
	 *
	 * @param introspectionServices the introspection service
	 */
	public void setIntrospectionServices(final IntrospectionServices introspectionServices) {
		this.introspectionServices = introspectionServices;
	}

	/**
	 * Sets the internal service used to look up ports in the registry,
	 * propagated to provider-side proxies created afterward.
	 *
	 * @param portRegistryServices the port registry service
	 */
	public void setPortRegistryServices(final PortRegistryServices portRegistryServices) {
		this.portRegistryServices = portRegistryServices;
	}

	/**
	 * Sets the internal service used to read the metamodel's port
	 * information, propagated to provider-side proxies created afterward.
	 *
	 * @param MetamodelPortReadServices the metamodel port read service
	 */
	public void setMetamodelPortReadServices(final MetamodelPortReadServices MetamodelPortReadServices) {
		this.MetamodelPortReadServices = MetamodelPortReadServices;
	}

	/**
	 * Sets the supplier consulted by created proxies to decide whether
	 * instrumentation callbacks should fire.
	 *
	 * @param instrumentationSupplier supplies whether instrumentation is
	 *                                currently enabled
	 */
	public void setInstrumentationSupplier(final BooleanSupplier instrumentationSupplier) {
		this.instrumentationSupplier = instrumentationSupplier;
	}

	/**
	 * Sets the supplier consulted by provider-side proxies created afterward
	 * to decide whether their owning container has been disposed.
	 *
	 * @param disposedSupplier supplies whether the owning container is
	 *                         currently disposed
	 */
	public void setDisposedSupplier(final BooleanSupplier disposedSupplier) {
		this.disposedSupplier = disposedSupplier;
	}

	/**
	 * Creates a provider-side dynamic proxy wrapping the given invocation
	 * handler, wiring it with this factory's internal services and
	 * instrumentation supplier first.
	 *
	 * @param <RR>              the request/response interface type
	 * @param invocationHandler the handler backing the proxy
	 * @param rrInterface       the interface the proxy implements
	 * @return the new provider-side proxy
	 */
	protected <RR> RR newServerRRProxy(final ServerRRInvocationHandler<RR> invocationHandler,
			final Class<RR> rrInterface) {
		invocationHandler.setIntrospectionServices(this.introspectionServices);
		invocationHandler.setPortRegistryServices(this.portRegistryServices);
		invocationHandler.setMetamodelPortReadServices(this.MetamodelPortReadServices);
		invocationHandler.setInstrumentationSupplier(this.instrumentationSupplier);
		invocationHandler.setDisposedSupplier(this.disposedSupplier);

		@SuppressWarnings("unchecked")
		RR proxy = (RR) Proxy.newProxyInstance(rrInterface.getClassLoader(), new Class[] { rrInterface },
				invocationHandler);

		return proxy;

	}

	/**
	 * Creates a client-side dynamic proxy wrapping the given invocation
	 * handler, wiring it with this factory's instrumentation supplier
	 * first.
	 *
	 * @param <RR>              the request/response interface type
	 * @param invocationHandler the handler backing the proxy
	 * @param rrInterface       the interface the proxy implements
	 * @return the new client-side proxy
	 */
	public <RR> RR newClientRRProxy(final ClientRRInvocationHandler<RR> invocationHandler,
			final Class<RR> rrInterface) {
		invocationHandler.setInstrumentationSupplier(this.instrumentationSupplier);

		@SuppressWarnings("unchecked")
		RR proxy = (RR) Proxy.newProxyInstance(rrInterface.getClassLoader(), new Class[] { rrInterface },
				invocationHandler);

		return proxy;
	}

	/**
	 * Creates a temporary surrogate proxy wrapping the given invocation
	 * handler, used in place of a provider-side proxy while no provider is
	 * declared yet.
	 *
	 * @param <RR>              the request/response interface type
	 * @param invocationHandler the surrogate handler backing the proxy
	 * @param rrInterface       the interface the proxy implements
	 * @return the new surrogate proxy
	 */
	public <RR> RR newSurrogateProxy(final RRSurrogateProxyInvocationHandler<RR> invocationHandler,
			final Class<RR> rrInterface) {
		@SuppressWarnings("unchecked")
		RR proxy = (RR) Proxy.newProxyInstance(rrInterface.getClassLoader(), new Class[] { rrInterface },
				invocationHandler);
		return proxy;
	}

	/**
	 * Invocation handler for a temporary surrogate proxy, standing in for a
	 * provider-side proxy while no provider is declared yet for
	 * {@code rrInterface}. On first invocation, it looks the interface up in
	 * the directory; if a provider has since become available, it hands the
	 * resolved target over to the associated
	 * {@link ClientRRInvocationHandler} (see
	 * {@link #setClientRRInvocationHandler}) and forwards the call, so
	 * subsequent calls go straight to the real provider without going
	 * through the surrogate again. If no provider is available yet, the
	 * directory's internal "not bound" exception is translated into a
	 * clearer, user-facing {@link IllegalStateException}.
	 *
	 * @param <RR> the request/response interface this surrogate stands in
	 *             for
	 */
	public abstract class RRSurrogateProxyInvocationHandler<RR> extends CovalentInvocationHandler {

		/**
		 * Creates a new surrogate handler for the given interface.
		 *
		 * @param rrInterface    the interface this surrogate stands in for
		 * @param namingServices the naming service used to resolve the
		 *                       interface's provider
		 * @param kind           a human-readable port kind ("services" or
		 *                       "parameters"), used in the error message
		 */
		public RRSurrogateProxyInvocationHandler(final Class<RR> rrInterface, final NamingServices namingServices,
				final String kind) {
			this.rrInterface = rrInterface;
			this.namingServices = namingServices;
			this.kind = kind;
		}

		@Override
		public Object invokeMethod(final Object proxy, final Method method, final Object[] args) throws Throwable {
			RuntimeCheck.notNull(this.clientRRInvocationHandler, "clientRRInvocationHandler");
			// try lookup
			RR clientRR;
			try {
				clientRR = this.lookup(this.rrInterface);
			} catch (NotBoundException e) {
				// NotBoundException's own message is directory-internal jargon;
				// this is the exception a caller actually sees when invoking a
				// used-service/accessed-parameters method too early, so it must
				// be understandable without knowing what "bound in directory"
				// means.
				throw new IllegalStateException("No provider is currently declared for the " + this.kind
						+ " interface " + this.rrInterface.getSimpleName() + ".", e);
			}
			log.fine("Replacing surrogate with real " + this.kind + ": " + this.rrInterface.getSimpleName());
			this.clientRRInvocationHandler.replaceClientRR(clientRR);
			log.fine("invoking " + this.kind + " on genuine proxy...");
			return method.invoke(clientRR, args);
		}

		/**
		 * Sets the client-side handler this surrogate hands the resolved
		 * provider target to, once found.
		 *
		 * @param clientRRInvocationHandler the client-side handler to update
		 */
		public void setClientRRInvocationHandler(final ClientRRInvocationHandler<RR> clientRRInvocationHandler) {
			this.clientRRInvocationHandler = clientRRInvocationHandler;
		}

		/**
		 * Resolves the current provider-side target for the given
		 * interface, throwing {@link NotBoundException} if none is declared
		 * yet.
		 *
		 * @param anInterface the interface to look up
		 * @return the resolved provider-side target
		 */
		protected abstract RR lookup(Class<RR> anInterface);

		/** The client-side handler updated once a real provider is found. */
		protected ClientRRInvocationHandler<RR> clientRRInvocationHandler;

		/** The naming service used to resolve {@link #rrInterface}'s provider. */
		protected final NamingServices namingServices;

		/** The interface this surrogate stands in for. */
		private final Class<RR> rrInterface;

		/** "services" or "parameters", used in the error message. */
		private final String kind;

	}
}
