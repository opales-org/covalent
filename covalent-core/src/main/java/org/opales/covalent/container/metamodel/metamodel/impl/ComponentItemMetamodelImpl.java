/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.metamodel.metamodel.impl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.opales.covalent.container.metamodel.metamodel.ComponentItemMetamodel;

/**
 * Default, mutable implementation of {@link ComponentItemMetamodel}:
 * stores the implementation class, the port interface lists (provided/used
 * services, produced/consumed events, provided/implemented/used
 * subscribers, offered/accessed parameters), the subscribe-to-events
 * mappings, and the fields backing each used/produced/accessed port, as
 * plain in-memory collections.
 */
public class ComponentItemMetamodelImpl implements ComponentItemMetamodel {

	private Class<?> implementation = null;

	private List<Class<?>> providedServices = new ArrayList<>();

	private List<Class<?>> usedServices = new ArrayList<>();

	private List<Class<?>> providedSubscribers = new ArrayList<>();

	private List<Class<?>> implementedSubscribers = new ArrayList<>();

	private List<Class<?>> usedSubscribers = new ArrayList<>();

	private List<Class<?>> producedEvents = new ArrayList<>();

	private List<Class<?>> consumedEvents = new ArrayList<>();

	private List<Class<?>> offeredParameters = new ArrayList<>();

	private List<Class<?>> accessedParameters = new ArrayList<>();

	private final Map<Class<?>, Class<?>> consumedEventsForUsedSubscriber = new LinkedHashMap<>();

	private final Map<Class<?>, Class<?>> providedSubscriberForProducedEvents = new LinkedHashMap<>();

	private final Map<Class<?>, Class<?>> producedEventsForProvidedSubscriber = new LinkedHashMap<>();

	private final Map<Class<?>, Field> usedServicesFields = new LinkedHashMap<>();

	private final Map<Class<?>, Field> producedEventsFields = new LinkedHashMap<>();

	private final Map<Class<?>, Field> usedSubscribersFields = new LinkedHashMap<>();

	private final Map<Class<?>, Field> accessedParametersFields = new LinkedHashMap<>();

	/**
	 * Creates a new, empty metamodel.
	 */
	public ComponentItemMetamodelImpl() {
	}

	@Override
	public void setImplementation(final Class<?> implementation) {
		this.implementation = implementation;
	}

	@Override
	public Class<?> getImplementation() {
		return this.implementation;
	}

	@Override
	public void setProvidedServices(final List<Class<?>> providedServices) {
		this.providedServices = new ArrayList<>(providedServices);
	}

	@Override
	public List<Class<?>> getProvidedServices() {
		return new ArrayList<>(this.providedServices);
	}

	@Override
	public void setUsedServices(final List<Class<?>> usedServices) {
		this.usedServices = new ArrayList<>(usedServices);
	}

	@Override
	public List<Class<?>> getUsedServices() {
		return new ArrayList<>(this.usedServices);
	}

	@Override
	public void setProducedEvents(final List<Class<?>> producedEvents) {
		this.producedEvents = new ArrayList<>(producedEvents);
	}

	@Override
	public List<Class<?>> getProducedEvents() {
		return new ArrayList<>(this.producedEvents);
	}

	@Override
	public void setConsumedEvents(final List<Class<?>> consumedEvents) {
		this.consumedEvents = new ArrayList<>(consumedEvents);
	}

	@Override
	public List<Class<?>> getConsumedEvents() {
		return new ArrayList<>(this.consumedEvents);
	}

	@Override
	public void setProvidedSubscribers(final List<Class<?>> providedSubscribers) {
		this.providedSubscribers = new ArrayList<>(providedSubscribers);
	}

	@Override
	public List<Class<?>> getProvidedSubscribers() {
		return new ArrayList<>(this.providedSubscribers);
	}

	@Override
	public void setImplementedSubscribers(final List<Class<?>> implementedSubscribers) {
		this.implementedSubscribers = new ArrayList<>(implementedSubscribers);
	}

	@Override
	public List<Class<?>> getImplementedSubscribers() {
		return new ArrayList<>(this.implementedSubscribers);
	}

	@Override
	public void setUsedSubscribers(final List<Class<?>> usedEventsSubscribes) {
		this.usedSubscribers = new ArrayList<>(usedEventsSubscribes);
	}

	@Override
	public List<Class<?>> getUsedSubscribers() {
		return new ArrayList<>(this.usedSubscribers);
	}

	@Override
	public void setOfferedParameters(final List<Class<?>> offeredParameters) {
		this.offeredParameters = new ArrayList<>(offeredParameters);
	}

	@Override
	public List<Class<?>> getOfferedParameters() {
		return new ArrayList<>(this.offeredParameters);
	}

	@Override
	public void setAccessedParameters(final List<Class<?>> accessedParameters) {
		this.accessedParameters = new ArrayList<>(accessedParameters);
	}

	@Override
	public List<Class<?>> getAccessedParameters() {
		return new ArrayList<>(this.accessedParameters);
	}

	/**
	 * <p>
	 * Returns the events interface associated with given subscriber interface, in
	 * event consumer.
	 * </p>
	 *
	 * @param <EVENT>    the consumed events interface
	 * @param <SUBSCRIBE> the used subscribe interface
	 * @param subscriber the used subscribe interface class
	 * @return the consumed events interface mapped to the given subscribe
	 *         interface, or {@code null} if none is mapped
	 */
	public <EVENT, SUBSCRIBE> Class<EVENT> getItemConsumedEventsForUsedSubscriber(final Class<SUBSCRIBE> subscriber) {
		@SuppressWarnings("unchecked")
		Class<EVENT> eventsForSubscriber = (Class<EVENT>) this.consumedEventsForUsedSubscriber.get(subscriber);
		return eventsForSubscriber;
	}

	/**
	 * <p>
	 * Returns the subscribe interface associated with given event type, in event
	 * producer.
	 * </p>
	 * 
	 * @param <EVENT>         the produced events interface
	 * @param <SUBSCRIBE>     the provided subscribe interface
	 * @param eventsInterface the produced events interface class
	 * @return the subscribe interface associated with given event type
	 */
	public <EVENT, SUBSCRIBE> Class<SUBSCRIBE> getProvidedSubscriberForItemProducedEvents(
			final Class<EVENT> eventsInterface) {
		@SuppressWarnings("unchecked")
		Class<SUBSCRIBE> subscriberForProducedEvents = (Class<SUBSCRIBE>) this.providedSubscriberForProducedEvents
				.get(eventsInterface);
		return subscriberForProducedEvents;
	}

	/**
	 * <p>
	 * Returns the events interface associated with the given provided subscribe
	 * interface, in event producer.
	 * </p>
	 *
	 * @param <EVENT>     the produced events interface
	 * @param <SUBSCRIBE> the provided subscribe interface
	 * @param subscriber  the subscribe interface class
	 * @return the events interface associated with the given subscribe interface
	 */
	public <EVENT, SUBSCRIBE> Class<EVENT> getItemProducedEventsForProvidedSubscriber(
			final Class<SUBSCRIBE> subscriber) {
		@SuppressWarnings("unchecked")
		Class<EVENT> producedEventsForSubscriber = (Class<EVENT>) this.producedEventsForProvidedSubscriber
				.get(subscriber);
		return producedEventsForSubscriber;
	}

	@Override
	public void mapUsedSubscriberAndConsumedEvents(final Class<?> subscribeInterface, final Class<?> eventsInterface) {
		this.consumedEventsForUsedSubscriber.put(subscribeInterface, eventsInterface);
	}

	@Override
	public void removeUsedSubscriberAndConsumedEvents(final Class<?> eventsInterface) {
		this.consumedEventsForUsedSubscriber.entrySet().removeIf(entry -> eventsInterface.equals(entry.getValue()));
	}

	@Override
	public void mapProvidedSubscriberAndProducedEvents(final Class<?> subscribeInterface,
			final Class<?> eventsInterface) {
		this.providedSubscriberForProducedEvents.put(eventsInterface, subscribeInterface);
		this.producedEventsForProvidedSubscriber.put(subscribeInterface, eventsInterface);
	}

	@Override
	public void removeProvidedSubscriberAndProducedEvents(final Class<?> eventsInterface) {
		Class<?> subscribeInterface = this.providedSubscriberForProducedEvents.remove(eventsInterface);
		if (subscribeInterface != null) {
			this.producedEventsForProvidedSubscriber.remove(subscribeInterface);
		}
	}

	private void appendSection(final StringBuilder strbuff, final String title, final List<Class<?>> interfaces) {
		if (interfaces.size() > 0) {
			strbuff.append("  > " + title + " :\n");
			for (Class<?> anInterface : interfaces) {
				strbuff.append("    - " + anInterface.getSimpleName() + "\n");
			}
		}
	}

	/**
	 * Returns the simple name of {@code type}, or {@code "null"} when it is not set
	 * yet, so that a metamodel can be printed while it is being built.
	 *
	 * @param type the class to name, possibly {@code null}
	 * @return the simple name, or {@code "null"}
	 */
	static String simpleName(final Class<?> type) {
		return type == null ? "null" : type.getSimpleName();
	}

	@Override
	public String toString() {
		StringBuilder strbuff = new StringBuilder();
		strbuff.append("--------------------------------------------------------------\n");
		strbuff.append("  Implementation :" + simpleName(this.implementation) + "\n");

		List<Class<?>> providedServices = this.providedServices;
		if (providedServices.size() > 0) {
			strbuff.append("  > Provided services :\n");
			for (Class<?> providedService : providedServices) {
				strbuff.append("    - " + providedService.getSimpleName() + "\n");
			}
		}

		List<Class<?>> usedServices = this.usedServices;
		if (usedServices.size() > 0) {
			strbuff.append("  > Used services :\n");
			for (Class<?> usedService : usedServices) {
				strbuff.append("    - " + usedService.getSimpleName() + "\n");
			}
		}

		List<Class<?>> producedEvents = this.producedEvents;
		if (producedEvents.size() > 0) {
			strbuff.append("  > Produced events :\n");
			for (Class<?> produceEvent : producedEvents) {
				strbuff.append("    - " + produceEvent.getSimpleName() + "\n");
			}
		}

		List<Class<?>> consumedEvents = this.consumedEvents;
		if (consumedEvents.size() > 0) {
			strbuff.append("  > Consumed events :\n");
			for (Class<?> consumedEvent : consumedEvents) {
				strbuff.append("    - " + consumedEvent.getSimpleName() + "\n");
			}
		}

		List<Class<?>> providedEventsSubscribes = this.providedSubscribers;
		if (providedEventsSubscribes.size() > 0) {
			strbuff.append("  > Provided events subscribes :\n");
			for (Class<?> providedEventsSubscribe : providedEventsSubscribes) {
				strbuff.append("    - " + providedEventsSubscribe.getSimpleName() + "\n");
			}
		}

		this.appendSection(strbuff, "Offered parameters", this.offeredParameters);
		this.appendSection(strbuff, "Accessed parameters", this.accessedParameters);
		this.appendSection(strbuff, "Implemented events subscribes", this.implementedSubscribers);

		List<Class<?>> usedEventsSubscribes = this.usedSubscribers;
		if (usedEventsSubscribes.size() > 0) {
			strbuff.append("  > Used events subscribes :\n");
			for (Class<?> usedEventsSubscribe : usedEventsSubscribes) {
				strbuff.append("    - " + usedEventsSubscribe.getSimpleName() + "\n");
			}
		}
		return strbuff.toString();
	}

	@Override
	public void addUsedServicesField(final Class<?> servicesInterface, final Field field) {
		this.usedServicesFields.put(servicesInterface, field);
	}

	@Override
	public List<Field> getUsedServicesFields() {
		return new ArrayList<>(this.usedServicesFields.values());
	}

	@Override
	public Field getUsedServicesField(final Class<?> servicesInterface) {
		return this.usedServicesFields.get(servicesInterface);
	}

	@Override
	public void addProducedEventsField(final Class<?> eventsInterface, final Field field) {
		this.producedEventsFields.put(eventsInterface, field);
	}

	@Override
	public List<Field> getProducedEventsFields() {
		return new ArrayList<>(this.producedEventsFields.values());
	}

	@Override
	public Field getProducedEventsField(final Class<?> eventsInterface) {
		return this.producedEventsFields.get(eventsInterface);
	}

	@Override
	public void addUsedSubscriberField(final Class<?> subscribeInterface, final Field field) {
		this.usedSubscribersFields.put(subscribeInterface, field);
	}

	@Override
	public List<Field> getUsedSubscriberFields() {
		return new ArrayList<>(this.usedSubscribersFields.values());
	}

	@Override
	public Field getUsedSubscriberField(final Class<?> subscribeInterface) {
		return this.usedSubscribersFields.get(subscribeInterface);
	}

	@Override
	public void addAccessedParametersField(final Class<?> parametersInterface, final Field field) {
		this.accessedParametersFields.put(parametersInterface, field);
	}

	@Override
	public List<Field> getAccessedParametersFields() {
		return new ArrayList<>(this.accessedParametersFields.values());
	}

	@Override
	public Field getAccessedParametersField(final Class<?> parametersInterface) {
		return this.accessedParametersFields.get(parametersInterface);
	}

}
