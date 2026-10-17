/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.rr.parameters.impl;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import org.opales.covalent.container.rr.common.ClientRRInvocationHandler;
import org.opales.covalent.container.rr.parameters.ParametersManagerEvents;

/**
 * {@code @AccessedParameters} flavor of {@link ClientRRInvocationHandler}:
 * client-side invocation handler for a component accessing another
 * component's offered parameters, additionally reporting each call's start
 * and end through {@link ParametersManagerEvents} for the supervision UI.
 *
 * @param <PARAMETERS> the parameters interface this handler proxies calls
 *                     for
 */
public class AccessedParametersInvocationHandler<PARAMETERS> extends ClientRRInvocationHandler<PARAMETERS> {
	private final Class<PARAMETERS> parametersInterface;

	private final Class<?> parametersAccessor;

	private final Supplier<ParametersManagerEvents> parametersManagerEventsSupplier;

	/**
	 * Creates a new handler forwarding to the given offered-parameters
	 * target.
	 *
	 * @param parametersInterface             the parameters interface
	 * @param accessedParameters              the initial provider-side
	 *                                        target
	 * @param parametersAccessor              the component type accessing
	 *                                        the parameters, reported in
	 *                                        events
	 * @param parametersManagerEventsSupplier supplies the events sink to
	 *                                        report calls to
	 */
	public AccessedParametersInvocationHandler(final Class<PARAMETERS> parametersInterface,
			final PARAMETERS accessedParameters, final Class<?> parametersAccessor,
			final Supplier<ParametersManagerEvents> parametersManagerEventsSupplier) {
		super(accessedParameters);
		this.parametersInterface = parametersInterface;
		this.parametersAccessor = parametersAccessor;
		this.parametersManagerEventsSupplier = parametersManagerEventsSupplier;
	}

	@Override
	protected void notifyCallStarted(final Method method, final Object[] args) {
		this.parametersManagerEventsSupplier.get().onAccessedParametersCallStarted(this.parametersInterface, method,
				this.parametersAccessor, args);
	}

	@Override
	protected void notifyCallFinished(final Method method, final Object[] args, final long durationNanos,
			final Throwable failure, final Object returnValue) {
		this.parametersManagerEventsSupplier.get().onAccessedParametersCallFinished(this.parametersInterface, method,
				this.parametersAccessor, durationNanos, failure, returnValue, args);
	}
}
