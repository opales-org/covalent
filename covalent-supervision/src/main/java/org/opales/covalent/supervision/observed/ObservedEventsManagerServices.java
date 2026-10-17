/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.observed;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.events.SubscribeParameters;
import org.opales.covalent.container.events.impl.ConsumerMetadata;

/**
 * Mirror of {@link org.opales.covalent.container.events.EventsManagerServices},
 * rooted in the <em>observed</em> container - see {@link ObservedRegistryReadServices}
 * for why a distinct type is needed instead of reusing the original. Full
 * surface duplicated, same rationale as {@link ObservedServicesManagerServices}.
 */
@Services
public interface ObservedEventsManagerServices {

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
	 * @param <EVENTS> events interface type
	 * @param eventsInterface events interface implemented by the producer proxy
	 * @param componentType producer component type
	 * @param subscribeDelegateConsumer delegate called when a consumer subscribes
	 * @param unsubscribeDelegateConsumer delegate called when a consumer unsubscribes
	 * @return producer proxy implementing the events interface
	 */
	<EVENTS> EVENTS createProducer(Class<EVENTS> eventsInterface, Class<?> componentType,
			Consumer<EVENTS> subscribeDelegateConsumer, Consumer<EVENTS> unsubscribeDelegateConsumer);

	/**
	 * Deletes the producer proxy registered for a component type and events interface.
	 *
	 * @param <EVENTS> events interface type
	 * @param componentType producer component type
	 * @param eventsInterface produced events interface
	 */
	<EVENTS> void deleteProducer(Class<?> componentType, Class<EVENTS> eventsInterface);

	/**
	 * Activates the producer proxy associated with an events interface.
	 *
	 * @param <EVENTS> events interface type
	 * @param eventsInterface events interface to activate
	 */
	<EVENTS> void activateProducer(Class<EVENTS> eventsInterface);

	/**
	 * Passivates the producer proxy associated with an events interface.
	 *
	 * @param <EVENTS> events interface type
	 * @param eventsInterface events interface to passivate
	 */
	<EVENTS> void passivateProducer(Class<EVENTS> eventsInterface);

	/**
	 * Reports whether an events interface currently has a producer.
	 *
	 * @param <EVENTS> events interface type
	 * @param eventsInterface events interface to inspect
	 * @return {@code true} when the events interface is produced
	 */
	<EVENTS> boolean isEventsProduced(Class<EVENTS> eventsInterface);

	/**
	 * Creates the consumer-side subscribe proxy used by a component.
	 *
	 * @param <EVENTS> events interface type
	 * @param <SUBSCRIBE> subscribe interface type
	 * @param eventsInterface consumed events interface
	 * @param subscribeInterface subscribe interface to expose to the consumer
	 * @return consumer-side subscribe proxy
	 */
	<EVENTS, SUBSCRIBE> SUBSCRIBE createUsedSubscriber(Class<EVENTS> eventsInterface,
			Class<SUBSCRIBE> subscribeInterface);

	/**
	 * Subscribes a consumer to an events interface.
	 *
	 * @param <EVENTS> events interface type
	 * @param eventsInterface events interface to consume
	 * @param consumer consumer receiving event method calls
	 * @param subscribeParameters subscription options
	 */
	<EVENTS> void subscribe(Class<EVENTS> eventsInterface, EVENTS consumer,
			SubscribeParameters subscribeParameters);

	/**
	 * Returns the producer-side subscribe proxy for an events interface.
	 *
	 * @param <EVENTS> events interface type
	 * @param <SUBSCRIBE> subscribe interface type
	 * @param eventsInterface events interface to inspect
	 * @return producer-side subscribe proxy
	 */
	<EVENTS, SUBSCRIBE> SUBSCRIBE getProvidedSubscriber(Class<EVENTS> eventsInterface);

	/**
	 * Unsubscribes a consumer from an events interface.
	 *
	 * @param <EVENTS> events interface type
	 * @param eventsInterface events interface to unsubscribe from
	 * @param consumer consumer to remove
	 */
	<EVENTS> void unsubscribe(Class<EVENTS> eventsInterface, EVENTS consumer);

	/**
	 * Reports whether a consumer is subscribed to an events interface.
	 *
	 * @param <EVENTS> events interface type
	 * @param eventsInterface events interface to inspect
	 * @param consumer consumer instance to inspect
	 * @return {@code true} when the consumer is subscribed
	 */
	<EVENTS> boolean isSubscribed(Class<EVENTS> eventsInterface, EVENTS consumer);

}
