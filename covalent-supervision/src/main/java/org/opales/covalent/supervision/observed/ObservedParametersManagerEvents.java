/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.observed;

import java.lang.reflect.Method;

import org.opales.covalent.container.component.annotations.Events;

/**
 * Mirror of {@link org.opales.covalent.container.rr.parameters.ParametersManagerEvents},
 * rooted in the <em>observed</em> container - see {@link ObservedRegistryReadServices}
 * for why a distinct type is needed instead of reusing the original.
 */
@Events
public interface ObservedParametersManagerEvents {

	/**
	 * Notifies that a component type offers a parameters interface.
	 *
	 * @param <PARAMETERS> parameters interface type
	 * @param componentType offeror component type
	 * @param parametersInterface offered parameters interface
	 */
	<PARAMETERS> void onParametersOffered(Class<?> componentType, Class<PARAMETERS> parametersInterface);

	/**
	 * Notifies that a component type stops offering a parameters interface.
	 *
	 * @param <PARAMETERS> parameters interface type
	 * @param componentType offeror component type
	 * @param parametersInterface unoffered parameters interface
	 */
	<PARAMETERS> void onParametersUnoffered(Class<?> componentType, Class<PARAMETERS> parametersInterface);

	/**
	 * Notifies that a component type accesses a parameters interface.
	 *
	 * @param <PARAMETERS> parameters interface type
	 * @param parametersAccessor accessor component type
	 * @param parametersInterface accessed parameters interface
	 */
	<PARAMETERS> void onParametersAccessed(Class<?> parametersAccessor, Class<PARAMETERS> parametersInterface);

	/**
	 * Notifies that a component type stops accessing a parameters interface.
	 *
	 * @param <PARAMETERS> parameters interface type
	 * @param parametersAccessor accessor component type
	 * @param parametersInterface unaccessed parameters interface
	 */
	<PARAMETERS> void onParametersUnaccessed(Class<?> parametersAccessor, Class<PARAMETERS> parametersInterface);

	/**
	 * Notifies that an accessed parameters method invocation has started on the accessor side.
	 * <p>
	 * Call notifications carry the thread the observed call runs on, its time as given by
	 * {@code System.nanoTime()} (only the difference between two such times is meaningful), and
	 * its order number among that thread's call notifications (from 1, restarting each time the
	 * stream is enabled), all captured where the call happens: their delivery to consumers is
	 * asynchronous, and may not preserve their order. They are only sent while enabled through
	 * {@link ObservedParameters}. The values they carry - arguments, return value - are described as text where the call
	 * happens (see {@link DataPrinter}): as they were then, without any reference to the observed
	 * application's objects. A finished call also carries its outcome: nothing, {@code null}, a value, or a failure.
	 *
	 * @param <PARAMETERS> parameters interface type
	 * @param callThread thread the observed call runs on
	 * @param callTimeNanos time of the call as given by {@code System.nanoTime()}, meaningful only
	 *     relative to another such time
	 * @param callSequence order number of the call among its thread's call notifications, from 1
	 * @param parametersInterface called parameters interface
	 * @param method called parameters method
	 * @param parametersAccessor accessor component type
	 * @param argumentsDescription the method arguments, described as text where the call happened
	 */
	<PARAMETERS> void onAccessedParametersCallStarted(Thread callThread, long callTimeNanos, long callSequence,
			Class<PARAMETERS> parametersInterface, Method method,
			Class<?> parametersAccessor, String argumentsDescription);

	/**
	 * Notifies that an accessed parameters method invocation has finished on the accessor side.
	 *
	 * @param <PARAMETERS> parameters interface type
	 * @param callThread thread the observed call runs on
	 * @param callTimeNanos time of the call as given by {@code System.nanoTime()}, meaningful only
	 *     relative to another such time
	 * @param callSequence order number of the call among its thread's call notifications, from 1
	 * @param parametersInterface called parameters interface
	 * @param method called parameters method
	 * @param parametersAccessor accessor component type
	 * @param durationNanos invocation duration in nanoseconds
	 * @param failure invocation failure, or {@code null} when the invocation succeeded
	 * @param outcome how the call ended: nothing, {@code null}, a value or a failure
	 */
	<PARAMETERS> void onAccessedParametersCallFinished(Thread callThread, long callTimeNanos, long callSequence,
			Class<PARAMETERS> parametersInterface, Method method,
			Class<?> parametersAccessor, long durationNanos, Throwable failure,
			CallOutcome outcome);

	/**
	 * Notifies that an offered parameters method invocation has started on the offeror side.
	 *
	 * @param <PARAMETERS> parameters interface type
	 * @param callThread thread the observed call runs on
	 * @param callTimeNanos time of the call as given by {@code System.nanoTime()}, meaningful only
	 *     relative to another such time
	 * @param callSequence order number of the call among its thread's call notifications, from 1
	 * @param parametersInterface called parameters interface
	 * @param method called parameters method
	 * @param parametersOfferor offeror component type
	 * @param argumentsDescription the method arguments, described as text where the call happened
	 */
	<PARAMETERS> void onOfferedParametersCallStarted(Thread callThread, long callTimeNanos, long callSequence,
			Class<PARAMETERS> parametersInterface, Method method,
			Class<?> parametersOfferor, String argumentsDescription);

	/**
	 * Notifies that an offered parameters method invocation has finished on the offeror side.
	 *
	 * @param <PARAMETERS> parameters interface type
	 * @param callThread thread the observed call runs on
	 * @param callTimeNanos time of the call as given by {@code System.nanoTime()}, meaningful only
	 *     relative to another such time
	 * @param callSequence order number of the call among its thread's call notifications, from 1
	 * @param parametersInterface called parameters interface
	 * @param method called parameters method
	 * @param parametersOfferor offeror component type
	 * @param durationNanos invocation duration in nanoseconds
	 * @param failure invocation failure, or {@code null} when the invocation succeeded
	 * @param outcome how the call ended: nothing, {@code null}, a value or a failure
	 */
	<PARAMETERS> void onOfferedParametersCallFinished(Thread callThread, long callTimeNanos, long callSequence,
			Class<PARAMETERS> parametersInterface, Method method,
			Class<?> parametersOfferor, long durationNanos, Throwable failure,
			CallOutcome outcome);

}
