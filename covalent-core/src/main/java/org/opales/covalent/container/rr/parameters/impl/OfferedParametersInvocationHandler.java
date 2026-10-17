/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.rr.parameters.impl;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import org.opales.covalent.container.component.annotations.Parameters;
import org.opales.covalent.container.rr.common.ServerRRInvocationHandler;
import org.opales.covalent.container.rr.parameters.ParametersManagerEvents;

/**
 * {@code @Parameters} flavor of {@link ServerRRInvocationHandler}:
 * provider-side invocation handler backing the proxy published for a
 * component offering a parameters interface, lazily resolving the offering
 * component instance and additionally reporting each call's start and end
 * through {@link ParametersManagerEvents} for the supervision UI.
 *
 * @param <PARAMETERS> the parameters interface this handler provides
 */
public class OfferedParametersInvocationHandler<PARAMETERS> extends ServerRRInvocationHandler<PARAMETERS> {

	/** Supplies the events sink to report offered-parameters calls to. */
	private Supplier<ParametersManagerEvents> parametersManagerEventsSupplier;

	/**
	 * Creates a new handler for the given offered parameters interface.
	 *
	 * @param parametersInterface the parameters interface this handler
	 *                            provides
	 */
	public OfferedParametersInvocationHandler(final Class<PARAMETERS> parametersInterface) {
		super(parametersInterface, "parameters");
	}

	/**
	 * Sets the supplier consulted to report offered-parameters call events.
	 *
	 * @param parametersManagerEventsSupplier supplies the events sink to
	 *                                        report calls to
	 */
	public void setProducedParametersManagerEvents(
			final Supplier<ParametersManagerEvents> parametersManagerEventsSupplier) {
		this.parametersManagerEventsSupplier = parametersManagerEventsSupplier;
	}

	@Override
	protected void notifyCallStarted(final Method method, final Object[] args) {
		this.parametersManagerEventsSupplier.get().onOfferedParametersCallStarted(this.providedInterface, method,
				this.componentType, args);
	}

	@Override
	protected void notifyCallFinished(final Method method, final Object[] args, final long durationNanos,
			final Throwable failure, final Object returnValue) {
		this.parametersManagerEventsSupplier.get().onOfferedParametersCallFinished(this.providedInterface, method,
				this.componentType, durationNanos, failure, returnValue, args);
	}

	@Override
	protected Class<?> getComponentTypeForProvidedInterface() {
		return this.MetamodelPortReadServices.getComponentTypeForOfferedParameters(this.providedInterface);
	}

	@Override
	protected PARAMETERS getInstance(final Class<PARAMETERS> anInterface) {
		return this.portRegistryServices.getParametersOfferor(anInterface);
	}

	@Override
	protected boolean checkInterface(final Class<PARAMETERS> anInterface) {
		return this.introspectionServices.isComponentParameters(anInterface);
	}

	@Override
	protected Class<?> getRRAnnotation() {
		return Parameters.class;
	}

}
