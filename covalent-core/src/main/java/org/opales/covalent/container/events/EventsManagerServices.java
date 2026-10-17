/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.events;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.events.impl.ConsumerMetadata;

/**
 * Creates, tracks, activates, passivates, subscribes, and unsubscribes Covalent
 * event ports.
 *
 * <p>Producer and subscriber bookkeeping here is not independently
 * synchronized: like
 * {@link org.opales.covalent.container.registry.RegistryReadServices} and
 * {@link org.opales.covalent.container.metamodel.MetamodelReadServices},
 * safe concurrent access relies on the caller going through
 * {@link org.opales.covalent.container.lifecycle.LifecycleServices}'s
 * per-container lock, since producers and subscriptions are normally
 * created and torn down during lifecycle transitions. Firing an
 * already-established event (invoking a producer proxy method) is a
 * separate concern from this bookkeeping and is safe to do concurrently
 * from multiple threads. This is unlike
 * {@link org.opales.covalent.container.async.AsyncServices}, whose worker
 * pools are independently thread-safe by design because tasks are meant to
 * be submitted from arbitrary application threads outside any lifecycle
 * operation.
 */
@Services
public interface EventsManagerServices {

	/**
	 * Returns the consumers registered by events interface.
	 *
	 * @return subscribers grouped by events interface
	 */
	Map<Class<?>, Set<ConsumerMetadata<?>>> getSubscribers();

	/**
	 * Returns the events interfaces produced by each component type.
	 *
	 * @return produced events interfaces grouped by producer component type
	 */
	Map<Class<?>, Set<Class<?>>> getProducerToEvent();

	/**
	 * Creates an event producer proxy for a producer component type.
	 *
	 * @param <EVENTS>                    events interface type
	 * @param <SUBSCRIBE>                 subscribe interface type exposed by the
	 *                                    producer proxy
	 * @param eventsInterface             events interface implemented by the
	 *                                    producer proxy
	 * @param componentType               producer component type
	 * @param subscribeDelegateConsumer   delegate called when a consumer subscribes
	 * @param unsubscribeDelegateConsumer delegate called when a consumer
	 *                                    unsubscribes
	 * @return producer proxy implementing the events interface
	 */
	<EVENTS, SUBSCRIBE> EVENTS createProducer(final Class<EVENTS> eventsInterface, final Class<?> componentType,
			final Consumer<EVENTS> subscribeDelegateConsumer, final Consumer<EVENTS> unsubscribeDelegateConsumer);

	/**
	 * Deletes the producer proxy registered for a component type and events
	 * interface.
	 *
	 * @param <EVENTS>        events interface type
	 * @param <SUBSCRIBE>     subscribe interface type exposed by the producer proxy
	 * @param componentType   producer component type
	 * @param eventsInterface produced events interface
	 */
	<EVENTS, SUBSCRIBE> void deleteProducer(final Class<?> componentType, final Class<EVENTS> eventsInterface);

	/**
	 * Activates the producer proxy associated with an events interface.
	 *
	 * @param <EVENTS>        events interface type
	 * @param eventsInterface events interface to activate
	 */
	<EVENTS> void activateProducer(Class<EVENTS> eventsInterface);

	/**
	 * Passivates the producer proxy associated with an events interface.
	 *
	 * @param <EVENTS>        events interface type
	 * @param eventsInterface events interface to passivate
	 */
	<EVENTS> void passivateProducer(Class<EVENTS> eventsInterface);

	/**
	 * Reports whether an events interface currently has a producer.
	 *
	 * @param <EVENTS>        events interface type
	 * @param eventsInterface events interface to inspect
	 * @return {@code true} when the events interface is produced
	 */
	<EVENTS> boolean isEventsProduced(final Class<EVENTS> eventsInterface);

	/**
	 * Creates the consumer-side subscribe proxy used by a component.
	 *
	 * @param <EVENTS>           events interface type
	 * @param <SUBSCRIBE>        subscribe interface type
	 * @param eventsInterface    consumed events interface
	 * @param subscribeInterface subscribe interface to expose to the consumer
	 * @param subscribingComponentType the component whose
	 *                           {@code @UsedEventsSubscribe} port this proxy
	 *                           backs: instrumentation reports it as the
	 *                           consumer of every object subscribed through
	 *                           it that is not a component itself
	 * @return consumer-side subscribe proxy
	 */
	<EVENTS, SUBSCRIBE> SUBSCRIBE createUsedSubscriber(final Class<EVENTS> eventsInterface,
			final Class<SUBSCRIBE> subscribeInterface, final Class<?> subscribingComponentType);

	/**
	 * Same as {@link #createUsedSubscriber(Class, Class, Class)}, for a used
	 * subscriber backing no component's {@code @UsedEventsSubscribe} port.
	 *
	 * @param <EVENTS>           the events interface type
	 * @param <SUBSCRIBE>        the subscribe interface type
	 * @param eventsInterface    the events interface to subscribe to
	 * @param subscribeInterface the producer's subscribe interface
	 * @return the used subscriber proxy
	 */
	<EVENTS, SUBSCRIBE> SUBSCRIBE createUsedSubscriber(final Class<EVENTS> eventsInterface,
			final Class<SUBSCRIBE> subscribeInterface);

	/**
	 * Subscribes a consumer to an events interface.
	 *
	 * @param <EVENTS>            events interface type
	 * @param <SUBSCRIBE>         subscribe interface type used by the producer
	 * @param eventsInterface     events interface to consume
	 * @param consumer            consumer receiving event method calls
	 * @param subscribeParameters subscription options
	 */
	<EVENTS, SUBSCRIBE> void subscribe(final Class<EVENTS> eventsInterface, final EVENTS consumer,
			final SubscribeParameters subscribeParameters);

	/**
	 * Returns the producer-side subscribe proxy for an events interface.
	 *
	 * @param <EVENTS>        events interface type
	 * @param <SUBSCRIBE>     subscribe interface type
	 * @param eventsInterface events interface to inspect
	 * @return producer-side subscribe proxy
	 */
	<EVENTS, SUBSCRIBE> SUBSCRIBE getProvidedSubscriber(Class<EVENTS> eventsInterface);

	/**
	 * Unsubscribes a consumer from an events interface.
	 *
	 * @param <EVENTS>        events interface type
	 * @param <SUBSCRIBE>     subscribe interface type used by the producer
	 * @param eventsInterface events interface to unsubscribe from
	 * @param consumer        consumer to remove
	 */
	<EVENTS, SUBSCRIBE> void unsubscribe(final Class<EVENTS> eventsInterface, final EVENTS consumer);

	/**
	 * Reports whether a consumer is subscribed to an events interface.
	 *
	 * @param <EVENTS>        events interface type
	 * @param eventsInterface events interface to inspect
	 * @param consumer        consumer instance to inspect
	 * @return {@code true} when the consumer is subscribed
	 */
	<EVENTS> boolean isSubscribed(final Class<EVENTS> eventsInterface, final EVENTS consumer);

}
