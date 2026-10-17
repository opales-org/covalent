/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.rr.services.impl;

import java.util.function.Supplier;

import org.opales.covalent.container.directory.NamingServices;
import org.opales.covalent.container.rr.common.RRProxyFactory;
import org.opales.covalent.container.rr.services.ServicesManagerEvents;
import org.opales.covalent.container.util.RuntimeCheck;

/**
 * Creates the JDK dynamic proxies backing the services flavor of the
 * request/response ({@code RR}) infrastructure: provider-side proxies
 * (wrapping a {@link ProvidedServicesInvocationHandler}), client-side
 * proxies (wrapping a {@link UsedServicesInvocationHandler}), and temporary
 * surrogate proxies used while no provider is declared yet. Collaborates
 * with {@link ServicesManagerImpl}, which owns the proxy lifecycle and
 * bookkeeping.
 */
public class ServicesProxyFactory extends RRProxyFactory {

	/** Creates a new services proxy factory. */
	public ServicesProxyFactory() {
	}

	/** Supplies the events port instrumented calls are reported through. */
	private Supplier<ServicesManagerEvents> servicesManagerEventsSupplier;

	/**
	 * Sets the supplier used to report instrumented service calls,
	 * propagated to handlers created afterward.
	 *
	 * @param supplier supplies the events port to notify
	 */
	void setProducedServiceManagerEvents(final Supplier<ServicesManagerEvents> supplier) {
		this.servicesManagerEventsSupplier = supplier;
	}

	/**
	 * Creates a provider-side proxy for the given services interface.
	 *
	 * @param <SERVICES>        the services interface type
	 * @param servicesInterface the interface the proxy implements
	 * @return the new provider-side proxy
	 */
	public <SERVICES> SERVICES newProvidedServicesProxy(final Class<SERVICES> servicesInterface) {
		ProvidedServicesInvocationHandler<SERVICES> invocationHandler = new ProvidedServicesInvocationHandler<>(
				servicesInterface);
		invocationHandler.setProducedServiceManagerEvents(this.servicesManagerEventsSupplier);
		return super.newServerRRProxy(invocationHandler, servicesInterface);
	}

	/**
	 * Creates a client-side proxy for the given services interface,
	 * forwarding to the given provider-side target.
	 *
	 * @param <SERVICES>        the services interface type
	 * @param servicesInterface the interface the proxy implements
	 * @param services          the provider-side target (real or surrogate)
	 *                          to forward calls to; must not be {@code null}
	 * @param servicesUser      the client component type using
	 *                          {@code servicesInterface}
	 * @return the new client-side proxy
	 */
	public <SERVICES> SERVICES newUsedServicesProxy(final Class<SERVICES> servicesInterface, final SERVICES services,
			final Class<?> servicesUser) {
		RuntimeCheck.notNull(services, "services");
		UsedServicesInvocationHandler<SERVICES> invocationHandler = new UsedServicesInvocationHandler<>(
				servicesInterface, services, servicesUser, this.servicesManagerEventsSupplier);
		return super.newClientRRProxy(invocationHandler, servicesInterface);
	};

	/**
	 * Creates a temporary surrogate proxy for the given services interface,
	 * used in place of a provider-side proxy while no provider is declared
	 * yet.
	 *
	 * @param <SERVICES>        the services interface type
	 * @param servicesInterface the interface the proxy implements
	 * @param namingServices    the naming service used to resolve the
	 *                          interface's provider once available
	 * @return the new surrogate proxy
	 */
	public <SERVICES> SERVICES newSurrogateProxy(final Class<SERVICES> servicesInterface,
			final NamingServices namingServices) {
		UsedServicesSurrogateProxyInvocationHandler<SERVICES> invocationHandeler = new UsedServicesSurrogateProxyInvocationHandler<>(
				servicesInterface, namingServices);
		return super.newSurrogateProxy(invocationHandeler, servicesInterface);
	}

	/**
	 * Services-flavor surrogate invocation handler: resolves the provider
	 * through {@link NamingServices#lookupServices}, handed the "services"
	 * kind used in log and error messages.
	 *
	 * @param <SERVICES> the services interface this surrogate stands in for
	 */
	class UsedServicesSurrogateProxyInvocationHandler<SERVICES> extends RRSurrogateProxyInvocationHandler<SERVICES> {
		/**
		 * Creates a new surrogate handler for the given services interface.
		 *
		 * @param servicesInterface the interface this surrogate stands in
		 *                          for
		 * @param namingServices    the naming service used to resolve the
		 *                          interface's provider
		 */
		public UsedServicesSurrogateProxyInvocationHandler(final Class<SERVICES> servicesInterface,
				final NamingServices namingServices) {
			super(servicesInterface, namingServices, "services");
		}

		@Override
		protected SERVICES lookup(final Class<SERVICES> servicesInterface) {
			return this.namingServices.lookupServices(servicesInterface);
		}

	}

}
