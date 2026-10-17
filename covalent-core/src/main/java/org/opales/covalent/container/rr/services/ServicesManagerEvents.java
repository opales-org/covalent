/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.rr.services;

import java.lang.reflect.Method;

import org.opales.covalent.container.component.annotations.Events;

/**
 * Reports service port lifecycle changes and instrumented service calls.
 * Produced by {@link org.opales.covalent.container.rr.services.impl.ServicesManagerImpl}
 * (provided/unprovided/used/unused notifications) and by its collaborating
 * invocation handlers (call-started/call-finished notifications, only fired
 * while instrumentation is enabled - see
 * {@link org.opales.covalent.container.rr.common.ClientRRInvocationHandler}
 * and {@link org.opales.covalent.container.rr.common.ServerRRInvocationHandler}).
 * Consumed internally, e.g. by the supervision UI.
 */
@Events
public interface ServicesManagerEvents {

	/**
	 * Notifies that a component type provides a services interface.
	 *
	 * @param <CT>              component type marker
	 * @param <SERVICES>        services interface type
	 * @param componentType     provider component type
	 * @param servicesInterface provided services interface
	 */
	<CT, SERVICES> void onServicesProvided(Class<CT> componentType, Class<SERVICES> servicesInterface);

	/**
	 * Notifies that a component type no longer provides a services interface.
	 *
	 * @param <CT>              component type marker
	 * @param <SERVICES>        services interface type
	 * @param componentType     provider component type
	 * @param servicesInterface unprovided services interface
	 */
	<CT, SERVICES> void onServicesUnprovided(Class<CT> componentType, Class<SERVICES> servicesInterface);

	/**
	 * Notifies that a component type uses a services interface.
	 *
	 * @param <CT>              component type marker
	 * @param <SERVICES>        services interface type
	 * @param servicesUser      user component type
	 * @param servicesInterface used services interface
	 */
	<CT, SERVICES> void onServicesUsed(Class<CT> servicesUser, Class<SERVICES> servicesInterface);

	/**
	 * Notifies that a component type no longer uses a services interface.
	 *
	 * @param <CT>              component type marker
	 * @param <SERVICES>        services interface type
	 * @param servicesUser      user component type
	 * @param servicesInterface unused services interface
	 */
	<CT, SERVICES> void onServicesUnused(Class<CT> servicesUser, Class<SERVICES> servicesInterface);

	/**
	 * Notifies that a used service method invocation has started on the caller side
	 * while instrumentation is enabled.
	 *
	 * @param <SERVICES>        services interface type
	 * @param servicesInterface called services interface
	 * @param method            called service method
	 * @param servicesUser      user component type
	 * @param inputData         service method arguments
	 */
	<SERVICES> void onUsedServiceCallStarted(Class<SERVICES> servicesInterface, Method method,
			Class<?> servicesUser, Object... inputData);

	/**
	 * Notifies that a used service method invocation has finished on the caller side
	 * while instrumentation is enabled.
	 *
	 * @param <SERVICES>        services interface type
	 * @param servicesInterface called services interface
	 * @param method            called service method
	 * @param servicesUser      user component type
	 * @param durationNanos     invocation duration in nanoseconds
	 * @param failure           invocation failure, or {@code null} when the
	 *                          invocation succeeded
	 * @param returnValue       value the invocation returned, or {@code null}
	 *                          when it failed or returns nothing
	 * @param inputData         service method arguments
	 */
	<SERVICES> void onUsedServiceCallFinished(Class<SERVICES> servicesInterface, Method method,
			Class<?> servicesUser, long durationNanos, Throwable failure, Object returnValue,
			Object... inputData);

	/**
	 * Notifies that a provided service method invocation has started on the provider
	 * side while instrumentation is enabled.
	 *
	 * @param <SERVICES>        services interface type
	 * @param servicesInterface called services interface
	 * @param method            called service method
	 * @param servicesProvider  provider component type
	 * @param inputData         service method arguments
	 */
	<SERVICES> void onProvidedServiceCallStarted(Class<SERVICES> servicesInterface, Method method,
			Class<?> servicesProvider, Object... inputData);

	/**
	 * Notifies that a provided service method invocation has finished on the
	 * provider side while instrumentation is enabled.
	 *
	 * @param <SERVICES>        services interface type
	 * @param servicesInterface called services interface
	 * @param method            called service method
	 * @param servicesProvider  provider component type
	 * @param durationNanos     invocation duration in nanoseconds
	 * @param failure           invocation failure, or {@code null} when the
	 *                          invocation succeeded
	 * @param returnValue       value the invocation returned, or {@code null}
	 *                          when it failed or returns nothing
	 * @param inputData         service method arguments
	 */
	<SERVICES> void onProvidedServiceCallFinished(Class<SERVICES> servicesInterface, Method method,
			Class<?> servicesProvider, long durationNanos, Throwable failure, Object returnValue,
			Object... inputData);

	/**
	 * Empty adapter for services manager event consumers: every method is a
	 * no-op, so a consumer only interested in a subset of events can extend
	 * this class and override the ones it needs instead of implementing the
	 * whole interface.
	 */
	public class ServicesManagerEventsAdapter implements ServicesManagerEvents {

		/** Creates a new no-op adapter. */
		public ServicesManagerEventsAdapter() {
		}

		@Override
		public <CT, SERVICES> void onServicesProvided(final Class<CT> componentType,
				final Class<SERVICES> servicesInterface) {
		}

		@Override
		public <CT, SERVICES> void onServicesUnprovided(final Class<CT> componentType,
				final Class<SERVICES> servicesInterface) {
		}

		@Override
		public <CT, SERVICES> void onServicesUsed(final Class<CT> servicesUser,
				final Class<SERVICES> servicesInterface) {
		}

		@Override
		public <CT, SERVICES> void onServicesUnused(final Class<CT> servicesUser,
				final Class<SERVICES> servicesInterface) {
		}

		@Override
		public <SERVICES> void onUsedServiceCallStarted(final Class<SERVICES> servicesInterface, final Method method,
				final Class<?> servicesUser, final Object... inputData) {
		}

		@Override
		public <SERVICES> void onUsedServiceCallFinished(final Class<SERVICES> servicesInterface, final Method method,
				final Class<?> servicesUser, final long durationNanos, final Throwable failure,
				final Object returnValue, final Object... inputData) {
		}

		@Override
		public <SERVICES> void onProvidedServiceCallStarted(final Class<SERVICES> servicesInterface,
				final Method method, final Class<?> servicesProvider, final Object... inputData) {
		}

		@Override
		public <SERVICES> void onProvidedServiceCallFinished(final Class<SERVICES> servicesInterface,
				final Method method, final Class<?> servicesProvider, final long durationNanos,
				final Throwable failure, final Object returnValue, final Object... inputData) {
		}

	}
}
