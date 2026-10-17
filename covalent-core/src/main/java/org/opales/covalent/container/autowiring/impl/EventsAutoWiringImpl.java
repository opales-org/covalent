/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.autowiring.impl;

import java.util.Set;
import java.util.logging.Logger;

import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.events.EventsManagerServices;
import org.opales.covalent.container.events.GenericSubscribe;
import org.opales.covalent.container.events.SubscribeParameters;
import org.opales.covalent.container.metamodel.MetamodelPortReadServices;
import org.opales.covalent.container.registry.PortRegistryServices;

@Item
class EventsAutoWiringImpl {
	private static final Logger log = Logger.getLogger(EventsAutoWiringImpl.class.getName());

	private EventsManagerServices useEventsManagerServices;

	private MetamodelPortReadServices useMetamodelPortReadServices;

	private PortRegistryServices usePortRegistryServices;

	private FieldInjectionImpl fieldInjection;

	void setEventManagerServices(final EventsManagerServices eventsManagerServices) {
		this.useEventsManagerServices = eventsManagerServices;
	}

	void setMetamodelPortReadServices(final MetamodelPortReadServices metamodelPortReadServices) {
		this.useMetamodelPortReadServices = metamodelPortReadServices;
	}

	void setPortRegistryServices(final PortRegistryServices portRegistryServices) {
		this.usePortRegistryServices = portRegistryServices;
	}

	void setFieldInjection(final FieldInjectionImpl fieldInjection) {
		this.fieldInjection = fieldInjection;
	}

	<EVENTS> void createProducedEvents(final Class<?> componentType) {
		Set<Class<?>> allProducedEvents = this.useMetamodelPortReadServices.getProducedEvents(componentType);
		for (Class<?> producedEvents : allProducedEvents) {
			@SuppressWarnings("unchecked")
			Class<EVENTS> eventsInterface = (Class<EVENTS>) producedEvents;
			if (!this.useEventsManagerServices.isEventsProduced(eventsInterface)) {
				log.fine("Automatic creation of produced events " + eventsInterface.getSimpleName());
				EVENTS eventsProducer = this.useEventsManagerServices.createProducer(eventsInterface, componentType,
						null, null);
				this.fieldInjection.injectIfNull(
						this.usePortRegistryServices.getProducedEventsItemAndField(componentType, eventsInterface),
						eventsProducer, "producedEvents");
			}
		}
	}

	<EVENTS> void deleteProducedEvents(final Class<?> componentType) {
		Set<Class<?>> allProducedEvents = this.useMetamodelPortReadServices.getProducedEvents(componentType);
		for (Class<?> producedEvents : allProducedEvents) {
			@SuppressWarnings("unchecked")
			Class<EVENTS> eventsInterface = (Class<EVENTS>) producedEvents;
			// events production could have been manually stopped manually before this
			if (this.useEventsManagerServices.isEventsProduced(eventsInterface)) {
				log.fine("Automatic removal of produced events " + eventsInterface.getSimpleName());
				this.useEventsManagerServices.deleteProducer(componentType, eventsInterface);
			}
			// we do not nullify the field in the component item, so that the caller has
			// an explicit error instead of a null pointer exception if it tries to use
			// the producer after it has been deleted.
		}
	}

	void createConsumedEvents(final Class<?> componentType) {

		// Technical note: this implementation manages events and subscribers
		// globally for all the component (impl and items).
		// That way, even if a subscriber is used in an other
		// item than the consumer item, it will still removed this events from
		// automatic subscription mechanism...

		Set<Class<?>> consumedEventsWithAutomaticSubscribe = this.useMetamodelPortReadServices
				.getConsumedEventsWithoutSubscriber(componentType);

		for (Class<?> eventsInterface : consumedEventsWithAutomaticSubscribe) {
			this.autoSubscribe(componentType, eventsInterface);
		}
	}

	private <EVENT> void autoSubscribe(final Class<?> componentType, final Class<EVENT> eventsInterface) {
		// note : it is not possible to pass subscribe parameters with auto-subscribe
		SubscribeParameters subscribeParameters = null;
		EVENT consumer = this.usePortRegistryServices.getEventsConsumer(componentType, eventsInterface);

		if (!this.useEventsManagerServices.isSubscribed(eventsInterface, consumer)) {
			log.fine("Automatic subscribe to events " + eventsInterface.getSimpleName());
			this.useEventsManagerServices.subscribe(eventsInterface, consumer, subscribeParameters);
		}
	}

	/**
	 * Unsubscribes every consumed events interface of the given component,
	 * including those whose subscription is managed by the component itself
	 * through a used subscribe field. Does nothing when the component has no
	 * descriptor.
	 */
	void deleteConsumedEvents(final Class<?> componentType) {
		// An instantiation failure can leave no descriptor, hence no subscriptions.
		if (this.usePortRegistryServices.getComponentDescriptor(componentType) == null) {
			return;
		}
		Set<Class<?>> consumedEventsWithAutomaticSubscribe = this.useMetamodelPortReadServices
				.getConsumedEvents(componentType);

		// technical note : as we are destroying the events producer,
		// we must disconnect all consumers, even those which have a use subscriber
		// and are supposed to unsubscribe manually. Hence the call of
		// MetamodelPortReadServices.getConsumedEvents

		for (Class<?> eventsInterface : consumedEventsWithAutomaticSubscribe) {
			this.autoUnsubscribe(componentType, eventsInterface);
		}
	}

	private <EVENT> void autoUnsubscribe(final Class<?> componentType, final Class<EVENT> eventsInterface) {
		// note : it is not possible to pass subscribe parameters with auto-subscribe
		// reminder: there can be only one component item consuming a given events type
		// in a component
		EVENT consumer = this.usePortRegistryServices.getEventsConsumer(componentType, eventsInterface);

		if (this.useEventsManagerServices.isSubscribed(eventsInterface, consumer)) {
			log.fine("Automatic unsubscribe to events " + eventsInterface.getSimpleName());
			this.useEventsManagerServices.unsubscribe(eventsInterface, consumer);
		}
	}

	<SUBSCRIBE> void createUsedSubscribers(final Class<?> componentType) {
		Set<Class<?>> subscribeInterfaces = this.useMetamodelPortReadServices.getUsedSubscribers(componentType);
		for (Class<?> aSubscribeInterface : subscribeInterfaces) {
			Class<?> eventsInterface;
			if (aSubscribeInterface.equals(GenericSubscribe.class)) {
				// Note: it is only possible to use generic subscribe for manual subscribe
				// if the consumer consumes only one events interface, and this events interface
				// does not provide an events subscribe interface
				Set<Class<?>> consumedEvents = this.useMetamodelPortReadServices.getConsumedEvents(componentType);
				if (consumedEvents.size() > 1) {
					throw new IllegalStateException("Consumer " + componentType.getSimpleName()
							+ " cannot use generic subscribe because it consumes more than one kind of events.");
				}
				eventsInterface = consumedEvents.iterator().next();
				Class<?> providedSubscribe = this.useMetamodelPortReadServices.getSubscribeInterface(eventsInterface);
				if (!GenericSubscribe.class.equals(providedSubscribe)) {
					throw new IllegalStateException("Consumer " + componentType.getSimpleName()
							+ " cannot use generic subscribe to subscribe to " + eventsInterface.getSimpleName()
							+ "\nbecause this events interface provides its own subscribe interface: "
							+ providedSubscribe.getSimpleName()
							+ ".\nIf manual subscribe is required, please use provided subscribe interface.");
				}
			} else {
				eventsInterface = this.useMetamodelPortReadServices.getEventsForSubscriber(aSubscribeInterface);
			}
			@SuppressWarnings("unchecked")
			Class<SUBSCRIBE> subscribeInterface = (Class<SUBSCRIBE>) aSubscribeInterface;
			this.connectUsedSubscriber(eventsInterface, subscribeInterface, componentType);
		}
	}

	private <EVENTS, SUBSCRIBE> void connectUsedSubscriber(final Class<EVENTS> eventsInterface,
			final Class<SUBSCRIBE> subscribeInterface, final Class<?> subscribeUserComponentType) {
		log.fine("Automatic connection of used subscribe " + subscribeInterface.getSimpleName());
		SUBSCRIBE usedSubscriber = this.useEventsManagerServices.createUsedSubscriber(eventsInterface,
				subscribeInterface, subscribeUserComponentType);
		this.fieldInjection.injectIfNull(
				this.usePortRegistryServices.getUsedSubscribeItemAndField(subscribeUserComponentType,
						subscribeInterface),
				usedSubscriber, "usedSubscriber");
	}

	<SUBSCRIBE> void deleteUsedSubscribers(final Class<?> componentType) {
		// Note: there is nothing to do as subscriber and producer on producer side
		// are handled by the same proxy, which is invalidated in method
		// #deleteProducedEvents(Class)
	}

}
