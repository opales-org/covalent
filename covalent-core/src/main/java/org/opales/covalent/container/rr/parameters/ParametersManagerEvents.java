/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.rr.parameters;

import java.lang.reflect.Method;

import org.opales.covalent.container.component.annotations.Events;

/**
 * Internal {@code @Events} port produced by
 * {@link org.opales.covalent.container.rr.parameters.impl.ParametersManagerImpl}:
 * reports parameters port lifecycle changes (offered/unoffered,
 * accessed/unaccessed) and, when instrumentation is enabled, the
 * start/finish of each individual offered- and accessed-parameters method
 * call, primarily for consumption by the supervision UI. See
 * {@link ParametersManagerEventsAdapter} for a no-op base to subclass when
 * only some events are of interest.
 */
@Events
public interface ParametersManagerEvents {

	/**
	 * Notifies that a component type offers a parameters interface.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param componentType       offeror component type
	 * @param parametersInterface offered parameters interface
	 */
	<PARAMETERS> void onParametersOffered(Class<?> componentType, Class<PARAMETERS> parametersInterface);

	/**
	 * Notifies that a component type no longer offers a parameters interface.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param componentType       offeror component type
	 * @param parametersInterface unoffered parameters interface
	 */
	<PARAMETERS> void onParametersUnoffered(Class<?> componentType, Class<PARAMETERS> parametersInterface);

	/**
	 * Notifies that a component type accesses a parameters interface.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param parametersAccessor  accessor component type
	 * @param parametersInterface accessed parameters interface
	 */
	<PARAMETERS> void onParametersAccessed(Class<?> parametersAccessor, Class<PARAMETERS> parametersInterface);

	/**
	 * Notifies that a component type no longer accesses a parameters interface.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param parametersAccessor  accessor component type
	 * @param parametersInterface unaccessed parameters interface
	 */
	<PARAMETERS> void onParametersUnaccessed(Class<?> parametersAccessor, Class<PARAMETERS> parametersInterface);

	/**
	 * Notifies that an accessed parameters method invocation has started on the
	 * accessor side while instrumentation is enabled.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param parametersInterface called parameters interface
	 * @param method              called parameters method
	 * @param parametersAccessor  accessor component type
	 * @param inputData           parameters method arguments
	 */
	<PARAMETERS> void onAccessedParametersCallStarted(Class<PARAMETERS> parametersInterface, Method method,
			Class<?> parametersAccessor, Object... inputData);

	/**
	 * Notifies that an accessed parameters method invocation has finished on the
	 * accessor side while instrumentation is enabled.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param parametersInterface called parameters interface
	 * @param method              called parameters method
	 * @param parametersAccessor  accessor component type
	 * @param durationNanos       invocation duration in nanoseconds
	 * @param failure             invocation failure, or {@code null} when the
	 *                            invocation succeeded
	 * @param returnValue       value the invocation returned, or {@code null}
	 *                          when it failed or returns nothing
	 * @param inputData           parameters method arguments
	 */
	<PARAMETERS> void onAccessedParametersCallFinished(Class<PARAMETERS> parametersInterface, Method method,
			Class<?> parametersAccessor, long durationNanos, Throwable failure, Object returnValue,
			Object... inputData);

	/**
	 * Notifies that an offered parameters method invocation has started on the
	 * offeror side while instrumentation is enabled.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param parametersInterface called parameters interface
	 * @param method              called parameters method
	 * @param parametersOfferor   offeror component type
	 * @param inputData           parameters method arguments
	 */
	<PARAMETERS> void onOfferedParametersCallStarted(Class<PARAMETERS> parametersInterface, Method method,
			Class<?> parametersOfferor, Object... inputData);

	/**
	 * Notifies that an offered parameters method invocation has finished on the
	 * offeror side while instrumentation is enabled.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param parametersInterface called parameters interface
	 * @param method              called parameters method
	 * @param parametersOfferor   offeror component type
	 * @param durationNanos       invocation duration in nanoseconds
	 * @param failure             invocation failure, or {@code null} when the
	 *                            invocation succeeded
	 * @param returnValue       value the invocation returned, or {@code null}
	 *                          when it failed or returns nothing
	 * @param inputData           parameters method arguments
	 */
	<PARAMETERS> void onOfferedParametersCallFinished(Class<PARAMETERS> parametersInterface, Method method,
			Class<?> parametersOfferor, long durationNanos, Throwable failure, Object returnValue,
			Object... inputData);

	/**
	 * No-op adapter implementing every {@link ParametersManagerEvents}
	 * method with an empty body, so a consumer interested in only some of
	 * the events can subclass it and override just those it needs.
	 */
	public class ParametersManagerEventsAdapter implements ParametersManagerEvents {

		/** Creates a new no-op adapter. */
		public ParametersManagerEventsAdapter() {
		}

		@Override
		public <PARAMETERS> void onParametersOffered(final Class<?> componentType,
				final Class<PARAMETERS> parametersInterface) {
		}

		@Override
		public <PARAMETERS> void onParametersUnoffered(final Class<?> componentType,
				final Class<PARAMETERS> parametersInterface) {
		}

		@Override
		public <PARAMETERS> void onParametersAccessed(final Class<?> parametersAccessor,
				final Class<PARAMETERS> parametersInterface) {
		}

		@Override
		public <PARAMETERS> void onParametersUnaccessed(final Class<?> parametersAccessor,
				final Class<PARAMETERS> parametersInterface) {
		}

		@Override
		public <PARAMETERS> void onAccessedParametersCallStarted(final Class<PARAMETERS> parametersInterface,
				final Method method, final Class<?> parametersAccessor, final Object... inputData) {
		}

		@Override
		public <PARAMETERS> void onAccessedParametersCallFinished(final Class<PARAMETERS> parametersInterface,
				final Method method, final Class<?> parametersAccessor, final long durationNanos,
				final Throwable failure, final Object returnValue, final Object... inputData) {
		}

		@Override
		public <PARAMETERS> void onOfferedParametersCallStarted(final Class<PARAMETERS> parametersInterface,
				final Method method, final Class<?> parametersOfferor, final Object... inputData) {
		}

		@Override
		public <PARAMETERS> void onOfferedParametersCallFinished(final Class<PARAMETERS> parametersInterface,
				final Method method, final Class<?> parametersOfferor, final long durationNanos,
				final Throwable failure, final Object returnValue, final Object... inputData) {
		}

	}
}
