/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.observed;

import java.lang.reflect.Method;

import org.opales.covalent.container.component.annotations.Events;

/**
 * Mirror of {@link org.opales.covalent.container.rr.services.ServicesManagerEvents},
 * rooted in the <em>observed</em> container - see {@link ObservedRegistryReadServices}
 * for why a distinct type is needed instead of reusing the original.
 */
@Events
public interface ObservedServicesManagerEvents {

	/**
	 * Notifies that a component type provides a services interface.
	 *
	 * @param <CT> component type marker
	 * @param <SERVICES> services interface type
	 * @param componentType provider component type
	 * @param servicesInterface provided services interface
	 */
	<CT, SERVICES> void onServicesProvided(Class<CT> componentType, Class<SERVICES> servicesInterface);

	/**
	 * Notifies that a component type stops providing a services interface.
	 *
	 * @param <CT> component type marker
	 * @param <SERVICES> services interface type
	 * @param componentType provider component type
	 * @param servicesInterface unprovided services interface
	 */
	<CT, SERVICES> void onServicesUnprovided(Class<CT> componentType, Class<SERVICES> servicesInterface);

	/**
	 * Notifies that a component type uses a services interface.
	 *
	 * @param <CT> component type marker
	 * @param <SERVICES> services interface type
	 * @param servicesUser user component type
	 * @param servicesInterface used services interface
	 */
	<CT, SERVICES> void onServicesUsed(Class<CT> servicesUser, Class<SERVICES> servicesInterface);

	/**
	 * Notifies that a component type stops using a services interface.
	 *
	 * @param <CT> component type marker
	 * @param <SERVICES> services interface type
	 * @param servicesUser user component type
	 * @param servicesInterface unused services interface
	 */
	<CT, SERVICES> void onServicesUnused(Class<CT> servicesUser, Class<SERVICES> servicesInterface);

	/**
	 * Notifies that a used service method invocation has started on the caller side.
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
	 * @param <SERVICES> services interface type
	 * @param callThread thread the observed call runs on
	 * @param callTimeNanos time of the call as given by {@code System.nanoTime()}, meaningful only
	 *     relative to another such time
	 * @param callSequence order number of the call among its thread's call notifications, from 1
	 * @param servicesInterface called services interface
	 * @param method called service method
	 * @param servicesUser user component type
	 * @param argumentsDescription the method arguments, described as text where the call happened
	 */
	<SERVICES> void onUsedServiceCallStarted(Thread callThread, long callTimeNanos, long callSequence,
			Class<SERVICES> servicesInterface, Method method,
			Class<?> servicesUser, String argumentsDescription);

	/**
	 * Notifies that a used service method invocation has finished on the caller side.
	 *
	 * @param <SERVICES> services interface type
	 * @param callThread thread the observed call runs on
	 * @param callTimeNanos time of the call as given by {@code System.nanoTime()}, meaningful only
	 *     relative to another such time
	 * @param callSequence order number of the call among its thread's call notifications, from 1
	 * @param servicesInterface called services interface
	 * @param method called service method
	 * @param servicesUser user component type
	 * @param durationNanos invocation duration in nanoseconds
	 * @param failure invocation failure, or {@code null} when the invocation succeeded
	 * @param outcome how the call ended: nothing, {@code null}, a value or a failure
	 */
	<SERVICES> void onUsedServiceCallFinished(Thread callThread, long callTimeNanos, long callSequence,
			Class<SERVICES> servicesInterface, Method method,
			Class<?> servicesUser, long durationNanos, Throwable failure,
			CallOutcome outcome);

	/**
	 * Notifies that a provided service method invocation has started on the provider side.
	 *
	 * @param <SERVICES> services interface type
	 * @param callThread thread the observed call runs on
	 * @param callTimeNanos time of the call as given by {@code System.nanoTime()}, meaningful only
	 *     relative to another such time
	 * @param callSequence order number of the call among its thread's call notifications, from 1
	 * @param servicesInterface called services interface
	 * @param method called service method
	 * @param servicesProvider provider component type
	 * @param argumentsDescription the method arguments, described as text where the call happened
	 */
	<SERVICES> void onProvidedServiceCallStarted(Thread callThread, long callTimeNanos, long callSequence,
			Class<SERVICES> servicesInterface, Method method,
			Class<?> servicesProvider, String argumentsDescription);

	/**
	 * Notifies that a provided service method invocation has finished on the provider side.
	 *
	 * @param <SERVICES> services interface type
	 * @param callThread thread the observed call runs on
	 * @param callTimeNanos time of the call as given by {@code System.nanoTime()}, meaningful only
	 *     relative to another such time
	 * @param callSequence order number of the call among its thread's call notifications, from 1
	 * @param servicesInterface called services interface
	 * @param method called service method
	 * @param servicesProvider provider component type
	 * @param durationNanos invocation duration in nanoseconds
	 * @param failure invocation failure, or {@code null} when the invocation succeeded
	 * @param outcome how the call ended: nothing, {@code null}, a value or a failure
	 */
	<SERVICES> void onProvidedServiceCallFinished(Thread callThread, long callTimeNanos, long callSequence,
			Class<SERVICES> servicesInterface, Method method,
			Class<?> servicesProvider, long durationNanos, Throwable failure,
			CallOutcome outcome);

}
