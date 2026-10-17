/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.events;

import java.lang.reflect.Method;

import org.opales.covalent.container.component.annotations.Events;

/**
 * Reports event port lifecycle changes and instrumented event notifications.
 */
@Events
public interface EventsManagerEvents {
	/**
	 * Notifies that a component type produces an events interface.
	 *
	 * @param <EVENTS>        events interface type
	 * @param componentType   producer component type
	 * @param eventsInterface produced events interface
	 */
	<EVENTS> void onEventsProduced(Class<?> componentType, Class<EVENTS> eventsInterface);

	/**
	 * Notifies that a component type no longer produces an events interface.
	 *
	 * @param <EVENTS>        events interface type
	 * @param componentType   producer component type
	 * @param eventsInterface unproduced events interface
	 */
	<EVENTS> void onEventsUnproduced(Class<?> componentType, Class<EVENTS> eventsInterface);

	/**
	 * Notifies that an events consumer subscribes to an events interface.
	 *
	 * @param <EVENTS>        events interface type
	 * @param eventsConsumer  runtime class of the consumer object, which
	 *                        implements the events interface (a proxy class
	 *                        when subscribing); not the consumer component
	 *                        type
	 * @param eventsInterface consumed events interface
	 */
	<EVENTS> void onEventsSubscribed(Class<? extends EVENTS> eventsConsumer, Class<EVENTS> eventsInterface);

	/**
	 * Notifies that an events consumer unsubscribes from an events interface.
	 *
	 * @param <EVENTS>        events interface type
	 * @param eventsConsumer  runtime class of the consumer object, which
	 *                        implements the events interface (a proxy class
	 *                        when subscribing); not the consumer component
	 *                        type
	 * @param eventsInterface consumed events interface
	 */
	<EVENTS> void onEventsUnsubscribed(Class<? extends EVENTS> eventsConsumer, Class<EVENTS> eventsInterface);

	/**
	 * Notifies that an event consumer notification has started while
	 * instrumentation is enabled.
	 *
	 * @param <EVENTS>        events interface type
	 * @param eventsInterface dispatched events interface
	 * @param method          dispatched event method
	 * @param eventsProducer  producer component type
	 * @param eventsConsumer  consumer component type: the component that
	 *                        subscribed, also when the subscribed object is
	 *                        not the component itself; the subscribed
	 *                        object's own class when no component is known
	 * @param subscriberType  the class of the object actually receiving the
	 *                        event: the component's implementation, or any
	 *                        other object it subscribed
	 * @param asynchronous    whether the event is delivered on another thread
	 *                        than the producer's, rather than in the
	 *                        producer's own call
	 * @param queueWaitNanos  how long the notification waited, in
	 *                        nanoseconds, between the moment the producer
	 *                        fired the event and the start of this
	 *                        notification: for an asynchronous event, its
	 *                        time in the delivery queue; for a synchronous
	 *                        one, the time spent notifying the subscribers
	 *                        notified before this one. A subscription not yet
	 *                        active when the event is fired keeps it until
	 *                        then, which counts as waiting too.
	 * @param inputData       event method arguments
	 */
	<EVENTS> void onEventNotificationStarted(Class<EVENTS> eventsInterface, Method method,
			Class<?> eventsProducer, Class<?> eventsConsumer, Class<? extends EVENTS> subscriberType, boolean asynchronous,
			long queueWaitNanos, Object... inputData);

	/**
	 * Notifies that an event consumer notification has finished while
	 * instrumentation is enabled.
	 * 
	 * @param <EVENTS>        events interface type
	 * @param eventsInterface dispatched events interface
	 * @param method          dispatched event method
	 * @param eventsProducer  producer component type
	 * @param eventsConsumer  consumer component type - see
	 *                        {@link #onEventNotificationStarted}
	 * @param subscriberType  the class of the object actually receiving the
	 *                        event - see {@link #onEventNotificationStarted}
	 * @param durationNanos   notification duration in nanoseconds
	 * @param failure         notification failure, or {@code null} when the
	 *                        notification succeeded
	 * @param inputData       event method arguments
	 */
	<EVENTS> void onEventNotificationFinished(Class<EVENTS> eventsInterface, Method method,
			Class<?> eventsProducer, Class<?> eventsConsumer, Class<? extends EVENTS> subscriberType, long durationNanos,
			Throwable failure, Object... inputData);

	/**
	 * Empty adapter for events manager event consumers.
	 */
	public class EventsManagerEventsAdapter implements EventsManagerEvents {

		/** Creates a new adapter with every callback a no-op. */
		public EventsManagerEventsAdapter() {
		}

		@Override
		public <EVENTS> void onEventsProduced(final Class<?> componentType, final Class<EVENTS> eventsInterface) {
		}

		@Override
		public <EVENTS> void onEventsUnproduced(final Class<?> componentType, final Class<EVENTS> eventsInterface) {
		}

		@Override
		public <EVENTS> void onEventsSubscribed(final Class<? extends EVENTS> eventsConsumer,
				final Class<EVENTS> eventsInterface) {
		}

		@Override
		public <EVENTS> void onEventsUnsubscribed(final Class<? extends EVENTS> eventsConsumer,
				final Class<EVENTS> eventsInterface) {
		}

		@Override
		public <EVENTS> void onEventNotificationStarted(final Class<EVENTS> eventsInterface, final Method method,
				final Class<?> eventsProducer, final Class<?> eventsConsumer, final Class<? extends EVENTS> subscriberType,
				final boolean asynchronous, final long queueWaitNanos, final Object... inputData) {
		}

		@Override
		public <EVENTS> void onEventNotificationFinished(final Class<EVENTS> eventsInterface, final Method method,
				final Class<?> eventsProducer, final Class<?> eventsConsumer, final Class<? extends EVENTS> subscriberType,
				final long durationNanos, final Throwable failure, final Object... inputData) {
		}

	}

}
