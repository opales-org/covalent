/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.raw.impl;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.opales.covalent.container.directory.NamingServices;
import org.opales.covalent.container.events.EventsManagerServices;
import org.opales.covalent.container.events.SubscribeParameters;
import org.opales.covalent.container.metamodel.MetamodelPortReadServices;
import org.opales.covalent.container.metamodel.MetamodelPortWriteServices;
import org.opales.covalent.container.metamodel.MetamodelWriteServices;
import org.opales.covalent.container.registry.DescriptorRegistryWriteServices;
import org.opales.covalent.container.registry.PortRegistryServices;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.util.CallerFinder;
import org.opales.covalent.container.util.RuntimeCheck;
import org.opales.covalent.container.validation.ValidationServices;

/**
 * <p>
 * Owns {@code RawComponentsImpl}'s raw events ports: their tracking state,
 * their creation/removal, and their bulk recovery cascades used by
 * {@code onComponentFailed}, {@code unregisterComponent}, and
 * {@code stop()}.
 * </p>
 * <p>
 * Kept separate from {@link RawServicesAndParametersPort} because events is a
 * distinct publish/subscribe mechanism ({@code EventsManagerImpl}), with its
 * own dispatch and worker-pool concerns, unlike services and parameters,
 * which share a common request/response manager base ({@code RRManagerImpl}).
 * </p>
 * <p>
 * This is a plain internal collaborator, not a Covalent component: it is
 * constructed and wired by {@code RawComponentsImpl} once its own
 * {@code @UsedServices} fields are populated, from its {@code @Initialization} method.
 * </p>
 * <p>
 * The class is {@code public} only so that
 * {@code org.opales.covalent.container.util.CallerFinder}, in another package,
 * can name it among the internal frames to skip when resolving the real caller.
 * Its constructor and methods are package-private on purpose: it is not an
 * entry point, only {@code RawComponentsImpl} uses it.
 * </p>
 */
public final class RawEventsPort {

	private static Logger log = Logger.getLogger(RawEventsPort.class.getName());

	private final ValidationServices useValidationServices;
	private final MetamodelPortReadServices useMetamodelPortReadServices;
	private final MetamodelPortWriteServices useMetamodelPortWriteServices;
	private final MetamodelWriteServices useMetamodelWriteServices;
	private final PortRegistryServices usePortRegistryServices;
	private final DescriptorRegistryWriteServices useDescriptorRegistryWriteServices;
	private final NamingServices useNamingServices;
	private final EventsManagerServices useEventsManagerServices;
	private final RegistryReadServices useRegistryReadServices;

	private final Map<Class<?>, Set<Class<?>>> rawProducers = new ConcurrentHashMap<>();

	// Component owner -> events interface -> real subscriber instances (possibly delegates).
	private final Map<Class<?>, Map<Class<?>, Set<Object>>> rawConsumers = new ConcurrentHashMap<>();

	RawEventsPort(final ValidationServices useValidationServices,
			final MetamodelPortReadServices useMetamodelPortReadServices,
			final MetamodelPortWriteServices useMetamodelPortWriteServices,
			final MetamodelWriteServices useMetamodelWriteServices, final PortRegistryServices usePortRegistryServices,
			final DescriptorRegistryWriteServices useDescriptorRegistryWriteServices,
			final NamingServices useNamingServices, final EventsManagerServices useEventsManagerServices,
			final RegistryReadServices useRegistryReadServices) {
		this.useValidationServices = useValidationServices;
		this.useMetamodelPortReadServices = useMetamodelPortReadServices;
		this.useMetamodelPortWriteServices = useMetamodelPortWriteServices;
		this.useMetamodelWriteServices = useMetamodelWriteServices;
		this.usePortRegistryServices = usePortRegistryServices;
		this.useDescriptorRegistryWriteServices = useDescriptorRegistryWriteServices;
		this.useNamingServices = useNamingServices;
		this.useEventsManagerServices = useEventsManagerServices;
		this.useRegistryReadServices = useRegistryReadServices;
	}

	// ---- produce events methods with component type as argument ----

	<EVENTS> EVENTS produceEvents(final Class<?> producerComponentType, final Class<EVENTS> eventsInterface) {
		RuntimeCheck.notNull(producerComponentType, "componentType");
		return this.produceEvents(producerComponentType, eventsInterface, null, null);
	}

	<EVENTS> EVENTS produceEvents(final Class<?> producerComponentType, final Class<EVENTS> eventsInterface,
			final Consumer<EVENTS> subscribeDelegateConsumer) {
		RuntimeCheck.notNull(producerComponentType, "componentType");
		return this.produceEvents(producerComponentType, eventsInterface, subscribeDelegateConsumer, null);
	}

	<EVENTS> EVENTS produceEvents(final Class<?> producerComponentType, final Class<EVENTS> eventsInterface,
			final Consumer<EVENTS> subscribeDelegateConsumer, final Consumer<EVENTS> unsubscribeDelegateConsumer) {
		RuntimeCheck.notNull(producerComponentType, "componentType");
		if (RawPortSupport.isImplementation(this.useValidationServices, producerComponentType)) {
			return this.produceEvents(eventsInterface, subscribeDelegateConsumer, unsubscribeDelegateConsumer);
		}
		return this.produceEventsInternal(producerComponentType, eventsInterface, subscribeDelegateConsumer,
				unsubscribeDelegateConsumer);
	}

	// ---- produce events methods without component type as argument ----

	/**
	 * Produces events without an explicit producer component type: the producer is
	 * the component whose metamodel declares these produced events (a managed
	 * component) or, failing that, the registered component type of the calling
	 * class.
	 *
	 * @param <EVENTS>        the events interface
	 * @param eventsInterface the events interface to produce
	 * @return the producer through which the events are fired
	 */
	<EVENTS> EVENTS produceEvents(final Class<EVENTS> eventsInterface) {
		return this.produceEvents(eventsInterface, (Consumer<EVENTS>) null, (Consumer<EVENTS>) null);
	}

	<EVENTS> EVENTS produceEvents(final Class<EVENTS> eventsInterface,
			final Consumer<EVENTS> subscribeDelegateConsumer) {
		return this.produceEvents(eventsInterface, subscribeDelegateConsumer, null);
	}

	<EVENTS> EVENTS produceEvents(final Class<EVENTS> eventsInterface,
			final Consumer<EVENTS> subscribeDelegateConsumer, final Consumer<EVENTS> unsubscribeDelegateConsumer) {
		Class<?> producerComponentType = this.tryToGetComponentTypeForEventsProducer(eventsInterface);
		return this.produceEventsInternal(producerComponentType, eventsInterface, subscribeDelegateConsumer,
				unsubscribeDelegateConsumer);
	}

	private <EVENTS> EVENTS produceEventsInternal(final Class<?> producerComponentType,
			final Class<EVENTS> eventsInterface, final Consumer<EVENTS> subscribeDelegateConsumer,
			final Consumer<EVENTS> unsubscribeDelegateConsumer) {
		// Unlike a metamodel-only requireKnownComponentType check, this requires full
		// registration (registerComponent reaching at least
		// instantiated), the same bar as provideServices/offerParameters: producing
		// events for a type that has never been registered as an actual raw
		// component instance would leave it invisible to the component registry
		// while still fully wired into the event system, the same identity gap this
		// check exists to close for every raw port family.
		RawPortSupport.requireRegisteredComponentType(this.useRegistryReadServices, producerComponentType,
				"produce events " + eventsInterface);
		this.useValidationServices.checkEvents(eventsInterface);
		// Ownership is checked, and the metamodel updated, BEFORE any of the
		// events-manager / raw-port-tracking / activation side effects below - see the
		// identical reasoning in RawServicesAndParametersPort.provideServicesInternal.
		// completeMetamodel() (reached from here through addProducedEvents) is pure
		// metamodel introspection and does not depend on the producer proxy created below,
		// so reordering is safe.
		Class<?> existingOwner = this.useMetamodelPortReadServices.getComponentTypeForProducedEvents(eventsInterface);
		boolean producedByAnotherComponent = existingOwner != null && !existingOwner.equals(producerComponentType)
				&& this.useEventsManagerServices.isEventsProduced(eventsInterface);
		this.updateMetamodelWithProducedEvents(producerComponentType, eventsInterface);
		if (producedByAnotherComponent) {
			// Each events interface has exactly one producer: the conflict has been reported by the
			// metamodel above (it throws at the strict level), and the first producer is kept. This
			// second declaration must leave no trace in the events manager or in this port's own
			// tracking, otherwise its entry would outlive the producer it never became.
			return eventsInterface.cast(this.useEventsManagerServices.getProvidedSubscriber(eventsInterface));
		}
		EVENTS producer = this.useEventsManagerServices.createProducer(eventsInterface, producerComponentType,
				subscribeDelegateConsumer, unsubscribeDelegateConsumer);
		RawPortSupport.trackRawPort(this.rawProducers, producerComponentType, eventsInterface);
		// after activation, producer is ready to be used
		this.useEventsManagerServices.activateProducer(eventsInterface);
		this.useNamingServices.bindSubscriber(eventsInterface, producer);

		return producer;
	}

	private Class<?> tryToGetComponentTypeForEventsProducer(final Class<?> eventsInterface) {
		log.fine("Trying to guess component type for events producer");
		Class<?> producerComponentType = this.useMetamodelPortReadServices.getComponentTypeForProducedEvents(eventsInterface);
		if (producerComponentType == null) {
			// assume the component is not managed and the method is called from the
			// provider
			producerComponentType = CallerFinder.getCaller();
			// try to get type from impl
			Class<?> componentType = this.useMetamodelWriteServices.getType(producerComponentType);
			if (componentType != null) {
				producerComponentType = componentType;
			} else {
				String message = "Could not retrieve component type producing events " + eventsInterface
						+ ", this may cause problems with component view. Maybe you should call produceEvents method with component type as parameter.";
				this.useValidationServices.handleStructuralVerification(message);
			}
		}
		return producerComponentType;
	}

	private <EVENTS> void updateMetamodelWithProducedEvents(final Class<?> producerComponentType,
			final Class<EVENTS> eventsInterface) {
		this.updateMetamodelOf(producerComponentType,
				() -> this.useMetamodelPortWriteServices.addProducedEvents(producerComponentType, eventsInterface));
	}

	private void updateMetamodelOf(final Class<?> componentType, final Runnable metamodelUpdate) {
		RawPortSupport.updateMetamodelOf(log, this.useMetamodelWriteServices, this.usePortRegistryServices,
				this.useDescriptorRegistryWriteServices, componentType, metamodelUpdate);
	}

	<EVENTS> void unproduceEvents(final Class<?> producerComponentType, final Class<EVENTS> eventsInterface) {
		RuntimeCheck.notNull(producerComponentType, "componentType");
		if (RawPortSupport.isImplementation(this.useValidationServices, producerComponentType)) {
			this.unproduceEvents(eventsInterface);
			return;
		}
		this.unproduceEventsInternal(producerComponentType, eventsInterface);
	}

	<EVENTS> void unproduceEvents(final Class<EVENTS> eventsInterface) {
		Class<?> producerComponentType = this.tryToGetComponentTypeForEventsProducer(eventsInterface);
		this.unproduceEventsInternal(producerComponentType, eventsInterface);
	}

	private <EVENTS> void unproduceEventsInternal(final Class<?> producerComponentType,
			final Class<EVENTS> eventsInterface) {
		// The owner is checked BEFORE anything is torn down: a refused withdrawal must
		// leave the owner's events fully working.
		Class<?> existingProducer = this.useMetamodelPortReadServices.getComponentTypeForProducedEvents(eventsInterface);
		if (existingProducer != null && !existingProducer.equals(producerComponentType)) {
			throw new IllegalArgumentException("Component type " + producerComponentType
					+ " does not match events producer component type " + existingProducer);
		}
		this.useNamingServices.unbindSubscriber(eventsInterface);

		this.useEventsManagerServices.passivateProducer(eventsInterface);

		// The events manager indexes producers with the same component type used at
		// creation time. Using the reflective caller here would leave stale producer
		// entries for raw components.
		this.useEventsManagerServices.deleteProducer(producerComponentType, eventsInterface);
		this.updateMetamodelWithUnproducedEvents(producerComponentType, eventsInterface);

		RawPortSupport.untrackRawPort(this.rawProducers, producerComponentType, eventsInterface);
	}

	private <EVENTS> void updateMetamodelWithUnproducedEvents(final Class<?> producerComponentType,
			final Class<EVENTS> eventsInterface) {
		this.updateMetamodelOf(producerComponentType,
				() -> this.useMetamodelPortWriteServices.removeProducedEvents(producerComponentType, eventsInterface));
	}

	// ---- subscribe/unsubscribe ----

	<EVENTS> void subscribe(final Class<?> consumerComponentType, final Class<EVENTS> eventsInterface,
			final EVENTS subscriber) {
		this.subscribe(consumerComponentType, eventsInterface, subscriber, null);
	}

	<EVENTS> void subscribe(final Class<?> consumerComponentType, final Class<EVENTS> eventsInterface,
			final EVENTS subscriber, final SubscribeParameters subscribeParameters) {
		RuntimeCheck.notNull(consumerComponentType, "componentType");
		if (RawPortSupport.isImplementation(this.useValidationServices, consumerComponentType)) {
			this.subscribe(eventsInterface, subscriber, subscribeParameters);
			return;
		}
		this.subscribeInternal(consumerComponentType, eventsInterface, subscriber, subscribeParameters);
	}

	<EVENTS> void subscribe(final Class<EVENTS> eventsInterface, final EVENTS subscriber) {
		this.subscribe(eventsInterface, subscriber, null);
	}

	<EVENTS> void subscribe(final Class<EVENTS> eventsInterface, final EVENTS subscriber,
			final SubscribeParameters subscribeParameters) {
		Class<?> consumerComponentType = this.tryToGetComponentTypeForEventsConsumer(eventsInterface);
		this.subscribeInternal(consumerComponentType, eventsInterface, subscriber, subscribeParameters);
	}

	private <EVENTS> void subscribeInternal(final Class<?> consumerComponentType, final Class<EVENTS> eventsInterface,
			final EVENTS subscriber, final SubscribeParameters subscribeParameters) {
		RawPortSupport.requireRegisteredComponentType(this.useRegistryReadServices, consumerComponentType,
				"subscribe to events " + eventsInterface);
		Class<?> subscribeInterface = this.useMetamodelPortReadServices.getSubscribeInterface(eventsInterface);
		this.useValidationServices.checkSubscribe(subscribeInterface);
		this.useEventsManagerServices.subscribe(eventsInterface, subscriber, subscribeParameters);
		this.rawConsumers.computeIfAbsent(consumerComponentType, key -> new ConcurrentHashMap<>())
				.computeIfAbsent(eventsInterface, key -> new CopyOnWriteArraySet<>()).add(subscriber);
		this.updateMetamodelWithConsumedEvents(consumerComponentType, eventsInterface);
	}

	private <EVENTS> void updateMetamodelWithConsumedEvents(final Class<?> consumerComponentType,
			final Class<EVENTS> eventsInterface) {
		this.updateMetamodelOf(consumerComponentType,
				() -> this.useMetamodelPortWriteServices.addConsumedEvents(consumerComponentType, eventsInterface));
	}

	<EVENTS> void unsubscribe(final Class<?> consumerComponentType, final Class<EVENTS> eventsInterface,
			final EVENTS subscriber) {
		RuntimeCheck.notNull(consumerComponentType, "componentType");
		if (RawPortSupport.isImplementation(this.useValidationServices, consumerComponentType)) {
			this.unsubscribe(eventsInterface, subscriber);
			return;
		}
		this.unsubscribeInternal(consumerComponentType, eventsInterface, subscriber);
	}

	<EVENTS> void unsubscribe(final Class<EVENTS> eventsInterface, final EVENTS subscriber) {
		Class<?> consumerComponentType = this.tryToGetComponentTypeForEventsConsumer(eventsInterface);
		this.unsubscribeInternal(consumerComponentType, eventsInterface, subscriber);
	}

	private Class<?> tryToGetComponentTypeForEventsConsumer(final Class<?> eventsInterface) {
		// Unlike produceEvents, there is no metamodel lookup from the events
		// interface to a single owner here: several different components can
		// legitimately consume the same events interface. The caller's own class
		// is resolved back to its registered component type instead, so a manual
		// subscription is tracked under the same key unregisterComponent and
		// onComponentFailed use to clean it up.
		return RawPortSupport.resolveManualPortOwner(this.useMetamodelWriteServices, this.useValidationServices,
				CallerFinder.getCaller(), "consuming events " + eventsInterface, "subscribe");
	}

	private <EVENTS> void unsubscribeInternal(final Class<?> consumerComponentType, final Class<EVENTS> eventsInterface,
			final EVENTS subscriber) {
		this.useEventsManagerServices.unsubscribe(eventsInterface, subscriber);
		Map<Class<?>, Set<Object>> subscriptions = this.rawConsumers.get(consumerComponentType);
		if (subscriptions != null) {
			Set<Object> subscribers = subscriptions.get(eventsInterface);
			if (subscribers != null) {
				subscribers.remove(subscriber);
				if (!subscribers.isEmpty()) {
					return;
				}
				subscriptions.remove(eventsInterface);
			}
			if (subscriptions.isEmpty()) {
				this.rawConsumers.remove(consumerComponentType);
			}
		}
		this.updateMetamodelWithUnconsumedEvents(consumerComponentType, eventsInterface);
	}

	private <EVENTS> void updateMetamodelWithUnconsumedEvents(final Class<?> consumerComponentType,
			final Class<EVENTS> eventsInterface) {
		this.updateMetamodelOf(consumerComponentType,
				() -> this.useMetamodelPortWriteServices.removeConsumedEvents(consumerComponentType, eventsInterface));
	}

	// ---- bulk recovery, used by RawComponentsImpl.onComponentFailed/unregisterComponent/stop() ----

	void unproduceAllEvents() {
		for (Class<?> owner : new LinkedHashSet<>(this.rawProducers.keySet())) {
			this.unproduceAllEventsFor(owner);
		}
	}

	<EVENTS> void unproduceAllEventsFor(final Class<?> producerComponentType) {
		Set<Class<?>> trackedInterfaces = this.rawProducers.get(producerComponentType);
		if (trackedInterfaces == null) {
			return;
		}
		for (Class<?> trackedInterface : new LinkedHashSet<>(trackedInterfaces)) {
			@SuppressWarnings("unchecked")
			Class<EVENTS> eventsInterface = (Class<EVENTS>) trackedInterface;
			if (this.useEventsManagerServices.isEventsProduced(eventsInterface)) {
				this.unproduceEventsInternal(producerComponentType, eventsInterface);
			} else {
				// Common lifecycle cleanup may already have deleted the producer.
				RawPortSupport.untrackRawPort(this.rawProducers, producerComponentType, eventsInterface);
			}
		}
	}

	void unsubscribeAll() {
		for (Class<?> owner : new LinkedHashSet<>(this.rawConsumers.keySet())) {
			this.unsubscribeAllFor(owner);
		}
	}

	<EVENTS> void unsubscribeAllFor(final Class<?> consumerComponentType) {
		Map<Class<?>, Set<Object>> subscriptions = this.rawConsumers.get(consumerComponentType);
		if (subscriptions == null) {
			return;
		}
		for (Map.Entry<Class<?>, Set<Object>> entry : new HashMap<>(subscriptions).entrySet()) {
			@SuppressWarnings("unchecked")
			Class<EVENTS> eventsInterface = (Class<EVENTS>) entry.getKey();
			for (Object instance : new LinkedHashSet<>(entry.getValue())) {
				@SuppressWarnings("unchecked")
				EVENTS subscriber = (EVENTS) instance;
				if (this.useEventsManagerServices.isSubscribed(eventsInterface, subscriber)) {
					this.unsubscribeInternal(consumerComponentType, eventsInterface, subscriber);
				} else {
					entry.getValue().remove(instance);
				}
			}
			if (entry.getValue().isEmpty()) {
				subscriptions.remove(eventsInterface);
			}
		}
		if (subscriptions.isEmpty()) {
			this.rawConsumers.remove(consumerComponentType);
		}
	}

}
