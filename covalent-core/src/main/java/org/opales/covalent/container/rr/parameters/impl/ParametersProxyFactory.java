/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.rr.parameters.impl;

import java.util.function.Supplier;

import org.opales.covalent.container.directory.NamingServices;
import org.opales.covalent.container.rr.common.RRProxyFactory;
import org.opales.covalent.container.rr.parameters.ParametersManagerEvents;
import org.opales.covalent.container.util.RuntimeCheck;

/**
 * Parameters flavor of {@link RRProxyFactory}: creates the offeror-side
 * proxies (wrapping an {@link OfferedParametersInvocationHandler}),
 * accessor-side proxies (wrapping an
 * {@link AccessedParametersInvocationHandler}), and temporary surrogate
 * proxies (wrapping an
 * {@link AccessedParametersSurrogateProxyInvocationHandler}) that back
 * Covalent parameters ports. Collaborates with {@link ParametersManagerImpl},
 * which owns the proxy lifecycle and bookkeeping. This is the parameters
 * flavor-specific counterpart of
 * {@code org.opales.covalent.container.rr.services.impl.ServicesProxyFactory}.
 */
public class ParametersProxyFactory extends RRProxyFactory {

	/** Creates a new parameters proxy factory. */
	public ParametersProxyFactory() {
	}

	/** Supplies the events sink propagated to created invocation handlers. */
	private Supplier<ParametersManagerEvents> parametersManagerEventsSupplier;

	/**
	 * Sets the supplier consulted by created invocation handlers to report
	 * offered- and accessed-parameters call events.
	 *
	 * @param supplier supplies the events sink to report calls to
	 */
	void setProducedParametersManagerEvents(final Supplier<ParametersManagerEvents> supplier) {
		this.parametersManagerEventsSupplier = supplier;
	}

	/**
	 * Creates a new offeror-side dynamic proxy for the given parameters
	 * interface.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param parametersInterface the parameters interface to create a proxy
	 *                            for
	 * @return the new offeror-side proxy
	 */
	public <PARAMETERS> PARAMETERS newOfferedParametersProxy(final Class<PARAMETERS> parametersInterface) {
		OfferedParametersInvocationHandler<PARAMETERS> invocationHandler = new OfferedParametersInvocationHandler<>(
				parametersInterface);
		invocationHandler.setProducedParametersManagerEvents(this.parametersManagerEventsSupplier);
		return super.newServerRRProxy(invocationHandler, parametersInterface);
	}

	/**
	 * Creates a new accessor-side dynamic proxy for the given parameters
	 * interface, forwarding calls to the given offeror-side target.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param parametersInterface the parameters interface to use
	 * @param parameters          the offeror-side target (real or
	 *                            surrogate) to forward calls to; must not
	 *                            be {@code null}
	 * @param parametersAccessor  the accessor component type, reported in
	 *                            call events
	 * @return the new accessor-side proxy
	 */
	public <PARAMETERS> PARAMETERS newAccessedParametersProxy(final Class<PARAMETERS> parametersInterface,
			final PARAMETERS parameters, final Class<?> parametersAccessor) {
		RuntimeCheck.notNull(parameters, "parameters");
		AccessedParametersInvocationHandler<PARAMETERS> invocationHandler = new AccessedParametersInvocationHandler<>(
				parametersInterface, parameters, parametersAccessor, this.parametersManagerEventsSupplier);
		return super.newClientRRProxy(invocationHandler, parametersInterface);
	};

	/**
	 * Creates a temporary surrogate proxy for the given parameters
	 * interface, used in place of an offeror-side proxy while no offeror is
	 * declared yet.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param parametersInterface the parameters interface to create a
	 *                            surrogate for
	 * @param namingServices      the naming service used by the surrogate
	 *                            to resolve the interface's offeror
	 * @return the new surrogate proxy
	 */
	public <PARAMETERS> PARAMETERS newSurrogateProxy(final Class<PARAMETERS> parametersInterface,
			final NamingServices namingServices) {
		AccessedParametersSurrogateProxyInvocationHandler<PARAMETERS> invocationHandeler = new AccessedParametersSurrogateProxyInvocationHandler<>(
				parametersInterface, namingServices);

		return super.newSurrogateProxy(invocationHandeler, parametersInterface);
	}

	/**
	 * Parameters flavor of {@link RRSurrogateProxyInvocationHandler}:
	 * resolves the offeror through {@link NamingServices#lookupParameters},
	 * standing in for an offeror-side proxy while no offeror is declared yet
	 * for the parameters interface.
	 *
	 * @param <PARAMETERS> the parameters interface this surrogate stands in
	 *                     for
	 */
	class AccessedParametersSurrogateProxyInvocationHandler<PARAMETERS>
			extends RRSurrogateProxyInvocationHandler<PARAMETERS> {

		/**
		 * Creates a new surrogate handler for the given parameters
		 * interface.
		 *
		 * @param parametersInterface the parameters interface this
		 *                            surrogate stands in for
		 * @param namingServices      the naming service used to resolve
		 *                            the interface's offeror
		 */
		public AccessedParametersSurrogateProxyInvocationHandler(final Class<PARAMETERS> parametersInterface,
				final NamingServices namingServices) {
			super(parametersInterface, namingServices, "parameters");
		}

		@Override
		protected PARAMETERS lookup(final Class<PARAMETERS> parametersInterface) {
			return this.namingServices.lookupParameters(parametersInterface);
		}
	}

}
