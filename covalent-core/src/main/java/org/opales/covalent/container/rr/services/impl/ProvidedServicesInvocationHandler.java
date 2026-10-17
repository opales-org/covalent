/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.rr.services.impl;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.rr.common.ServerRRInvocationHandler;
import org.opales.covalent.container.rr.services.ServicesManagerEvents;

/**
 * Provider-side invocation handler for the services flavor of the
 * request/response ({@code RR}) infrastructure: backs the proxy published
 * for a {@code @Services} provider, lazily resolving the provider component
 * and forwarding calls to it, and reporting instrumented calls through
 * {@link ServicesManagerEvents}. Created by
 * {@link ServicesProxyFactory#newProvidedServicesProxy}.
 *
 * @param <SERVICES> the services interface this handler provides
 */
public class ProvidedServicesInvocationHandler<SERVICES> extends ServerRRInvocationHandler<SERVICES> {

	/** Supplies the events port instrumented calls are reported through. */
	private Supplier<ServicesManagerEvents> servicesManagerEventsSupplier;

	/**
	 * Creates a new handler for the given provided services interface.
	 *
	 * @param servicesInterface the services interface this handler provides
	 */
	public ProvidedServicesInvocationHandler(final Class<SERVICES> servicesInterface) {
		super(servicesInterface, "services");
	}

	/**
	 * Sets the supplier used to report instrumented provided-service calls.
	 *
	 * @param supplier supplies the events port to notify
	 */
	void setProducedServiceManagerEvents(final Supplier<ServicesManagerEvents> supplier) {
		this.servicesManagerEventsSupplier = supplier;
	}

	@Override
	protected void notifyCallStarted(final Method method, final Object[] args) {
		this.servicesManagerEventsSupplier.get().onProvidedServiceCallStarted(this.providedInterface, method,
				this.componentType, args);
	}

	@Override
	protected void notifyCallFinished(final Method method, final Object[] args, final long durationNanos,
			final Throwable failure, final Object returnValue) {
		this.servicesManagerEventsSupplier.get().onProvidedServiceCallFinished(this.providedInterface, method,
				this.componentType, durationNanos, failure, returnValue, args);
	}

	@Override
	protected Class<?> getComponentTypeForProvidedInterface() {
		return this.MetamodelPortReadServices.getComponentTypeForProvidedServices(this.providedInterface);
	}

	@Override
	protected SERVICES getInstance(final Class<SERVICES> anInterface) {
		return this.portRegistryServices.getServicesProvider(anInterface);
	}

	@Override
	protected boolean checkInterface(final Class<SERVICES> anInterface) {
		return this.introspectionServices.isComponentServices(anInterface);
	}

	@Override
	protected Class<?> getRRAnnotation() {
		return Services.class;
	}

}
