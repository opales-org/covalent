/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.observed;

import java.lang.reflect.Method;

import org.opales.covalent.container.component.annotations.Events;

/**
 * Mirror of {@link org.opales.covalent.container.events.EventsManagerEvents},
 * rooted in the <em>observed</em> container - see {@link ObservedRegistryReadServices}
 * for why a distinct type is needed instead of reusing the original.
 */
@Events
public interface ObservedEventsManagerEvents {

	/**
	 * Notifies that a component type produces an events interface.
	 *
	 * @param <EVENTS> events interface type
	 * @param componentType producer component type
	 * @param eventsInterface produced events interface
	 */
	<EVENTS> void onEventsProduced(Class<?> componentType, Class<EVENTS> eventsInterface);

	/**
	 * Notifies that a component type stops producing an events interface.
	 *
	 * @param <EVENTS> events interface type
	 * @param componentType producer component type
	 * @param eventsInterface unproduced events interface
	 */
	<EVENTS> void onEventsUnproduced(Class<?> componentType, Class<EVENTS> eventsInterface);

	/**
	 * Notifies that an events consumer subscribes to an events interface.
	 *
	 * @param <EVENTS> events interface type
	 * @param eventsConsumer runtime class of the consumer object, which implements the events
	 *     interface (a proxy class when subscribing); not the consumer component type
	 * @param eventsInterface consumed events interface
	 */
	<EVENTS> void onEventsSubscribed(Class<? extends EVENTS> eventsConsumer, Class<EVENTS> eventsInterface);

	/**
	 * Notifies that an events consumer unsubscribes from an events interface.
	 *
	 * @param <EVENTS> events interface type
	 * @param eventsConsumer runtime class of the consumer object, which implements the events
	 *     interface (a proxy class when subscribing); not the consumer component type
	 * @param eventsInterface events interface to stop consuming
	 */
	<EVENTS> void onEventsUnsubscribed(Class<? extends EVENTS> eventsConsumer, Class<EVENTS> eventsInterface);

	/**
	 * Notifies that an event notification to a consumer has started, while the call stream is enabled.
	 * <p>
	 * Call notifications carry the thread the observed call runs on, its time as given by
	 * {@code System.nanoTime()} (only the difference between two such times is meaningful), and
	 * its order number among that thread's call notifications (from 1, restarting each time the
	 * stream is enabled), all captured where the call happens: their delivery to consumers is
	 * asynchronous, and may not preserve their order. They are only sent while enabled through
	 * {@link ObservedParameters}. The values they carry (the arguments) are described as text where the call
	 * happens (see {@link DataPrinter}): as they were then, without any reference to the observed
	 * application's objects.
	 * <p>
	 * For an event, {@code eventsConsumer} is the consuming component, and {@code subscriberType} the
	 * class of the object actually receiving it: the component's implementation, or any other object
	 * the component subscribed. {@code asynchronous} tells whether the event is delivered on another
	 * thread than the producer's, and {@code queueWaitNanos} how long the notification waited between
	 * the moment the event was fired and its start.
	 *
	 * @param <EVENTS> events interface type
	 * @param callThread thread the observed call runs on
	 * @param callTimeNanos time of the call as given by {@code System.nanoTime()}, meaningful only
	 *     relative to another such time
	 * @param callSequence order number of the call among its thread's call notifications, from 1
	 * @param eventsInterface dispatched events interface
	 * @param method dispatched event method
	 * @param eventsProducer producer component type
	 * @param eventsConsumer consuming component type
	 * @param subscriberType class of the object actually receiving the event
	 * @param asynchronous whether the event is delivered on another thread than the producer's
	 * @param queueWaitNanos time in nanoseconds the notification waited between the firing of the event
	 *     and its start
	 * @param argumentsDescription the event arguments, described as text where the call happened
	 */
	<EVENTS> void onEventNotificationStarted(Thread callThread, long callTimeNanos, long callSequence,
			Class<EVENTS> eventsInterface, Method method,
			Class<?> eventsProducer, Class<?> eventsConsumer, Class<? extends EVENTS> subscriberType, boolean asynchronous,
			long queueWaitNanos, String argumentsDescription);

	/**
	 * Notifies that an event notification to a consumer has finished, while the call stream is enabled.
	 *
	 * @param <EVENTS> events interface type
	 * @param callThread thread the observed call runs on
	 * @param callTimeNanos time of the call as given by {@code System.nanoTime()}, meaningful only
	 *     relative to another such time
	 * @param callSequence order number of the call among its thread's call notifications, from 1
	 * @param eventsInterface dispatched events interface
	 * @param method dispatched event method
	 * @param eventsProducer producer component type
	 * @param eventsConsumer consuming component type
	 * @param subscriberType class of the object actually receiving the event
	 * @param durationNanos notification duration in nanoseconds
	 * @param failure notification failure, or {@code null} when the notification succeeded
	 */
	<EVENTS> void onEventNotificationFinished(Thread callThread, long callTimeNanos, long callSequence,
			Class<EVENTS> eventsInterface, Method method,
			Class<?> eventsProducer, Class<?> eventsConsumer, Class<? extends EVENTS> subscriberType, long durationNanos, Throwable failure);

}
