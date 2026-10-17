/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.registry.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opales.covalent.container.metamodel.metamodel.ReadOnlyComponentMetamodel;
import org.opales.covalent.container.registry.ComponentDescriptor;
import org.opales.covalent.container.registry.ItemAndField;
import org.opales.covalent.container.registry.ItemDescriptor;

/**
 * Runtime record for a whole component, implementing
 * {@link ComponentDescriptor}: extends {@link ItemDescriptorImpl} with the
 * component's child items, its child (composed) components, and the
 * port-family index maps (provider/consumer/offeror/subscriber, and the
 * reverse used/produced/accessed field maps) used to resolve a port to the
 * item or field that implements it.
 */
public class ComponentDescriptorImpl extends ItemDescriptorImpl implements ComponentDescriptor {
	List<ItemDescriptorImpl> items = new ArrayList<>();

	List<ComponentDescriptorImpl> components = new ArrayList<>();

	Map<Class<?>, Object> servicesProviders = new HashMap<>();

	Map<Class<?>, Object> eventsConsumers = new HashMap<>();

	Map<Class<?>, Object> parametersOfferors = new HashMap<>();

	Map<Class<?>, Object> eventsSubscribers = new HashMap<>();

	private final Map<Class<?>, List<ItemAndField>> usedServices = new HashMap<>();

	private final Map<Class<?>, List<ItemAndField>> producedEvents = new HashMap<>();

	private final Map<Class<?>, List<ItemAndField>> accessedParameters = new HashMap<>();

	private final Map<Class<?>, List<ItemAndField>> usedSubscribes = new HashMap<>();;

	ComponentDescriptorImpl(final ComponentDescriptorImpl parent, final ReadOnlyComponentMetamodel metamodel) {
		super(parent, metamodel);
	}

	ComponentDescriptorImpl(final ReadOnlyComponentMetamodel metamodel) {
		super(null, metamodel);
	}

	void addItem(final ItemDescriptorImpl item) {
		this.items.add(item);
	}

	@Override
	public List<ItemDescriptor> getItems() {
		return new ArrayList<>(this.items);
	}

	void addComponent(final ComponentDescriptorImpl component) {
		this.components.add(component);
	}

	void removeComponent(final ComponentDescriptorImpl component) {
		this.components.remove(component);
	}

	@Override
	public List<ComponentDescriptor> getComponents() {
		return new ArrayList<>(this.components);
	}

	void clearPortIndexes() {
		this.servicesProviders.clear();
		this.eventsConsumers.clear();
		this.parametersOfferors.clear();
		this.eventsSubscribers.clear();
		this.usedServices.clear();
		this.producedEvents.clear();
		this.accessedParameters.clear();
		this.usedSubscribes.clear();
	}

	@Override
	public <SERVICES> SERVICES getServicesProvider(final Class<SERVICES> servicesInterface) {
		@SuppressWarnings("unchecked")
		SERVICES implementingService = (SERVICES) this.servicesProviders.get(servicesInterface);
		return implementingService;
	}

	<SERVICES> void setServicesProvider(final Class<SERVICES> servicesInterface, final SERVICES provider) {
		this.servicesProviders.put(servicesInterface, provider);
	}

	@Override
	public <EVENT> EVENT getEventsConsumer(final Class<EVENT> eventsInterface) {
		@SuppressWarnings("unchecked")
		EVENT implementingConsumer = (EVENT) this.eventsConsumers.get(eventsInterface);
		return implementingConsumer;
	}

	<EVENTS> void setEventsConsumer(final Class<EVENTS> eventsInterface, final EVENTS consumer) {
		this.eventsConsumers.put(eventsInterface, consumer);
	}

	@Override
	public <PARAMETERS> PARAMETERS getParametersOfferor(final Class<PARAMETERS> parametersInterface) {
		@SuppressWarnings("unchecked")
		PARAMETERS implementingParameters = (PARAMETERS) this.parametersOfferors.get(parametersInterface);
		return implementingParameters;
	}

	<PARAMETERS> void setParametersOfferor(final Class<PARAMETERS> parametersInterface, final PARAMETERS provider) {
		this.parametersOfferors.put(parametersInterface, provider);
	}

	@Override
	public <SUBSCRIBE> SUBSCRIBE getSubscribeDelegate(final Class<SUBSCRIBE> subscribeInterface) {
		@SuppressWarnings("unchecked")
		SUBSCRIBE implementingSubscriber = (SUBSCRIBE) this.eventsSubscribers.get(subscribeInterface);
		return implementingSubscriber;
	}

	<SUBSCRIBE> void setSubscribeDelegate(final Class<SUBSCRIBE> subscribeInterface, final SUBSCRIBE delegate) {
		this.eventsSubscribers.put(subscribeInterface, delegate);
	}

	@Override
	public <SERVICES> List<ItemAndField> getUsedServicesItemAndField(final Class<SERVICES> servicesInterface) {
		final List<ItemAndField> result = this.usedServices.get(servicesInterface);
		return result == null ? new ArrayList<>() : new ArrayList<>(result);
	}

	/**
	 * Records the items/fields associated with a used-services interface,
	 * for later lookup by {@link #getUsedServicesItemAndField}.
	 *
	 * @param <SERVICES>        the services interface
	 * @param servicesInterface the services interface class
	 * @param itemsAndFields    the items/fields using it; copied defensively,
	 *                          so later changes to the caller's list do not
	 *                          affect this descriptor's stored state
	 */
	public <SERVICES> void addUsedServicesItemAndField(final Class<SERVICES> servicesInterface,
			final List<ItemAndField> itemsAndFields) {
		this.usedServices.put(servicesInterface, new ArrayList<>(itemsAndFields));
	}

	@Override
	public <EVENTS> List<ItemAndField> getProducedEventsItemAndField(final Class<EVENTS> eventsInterface) {
		final List<ItemAndField> result = this.producedEvents.get(eventsInterface);
		return result == null ? new ArrayList<>() : new ArrayList<>(result);
	}

	/**
	 * Records the items/fields associated with a produced-events interface,
	 * for later lookup by {@link #getProducedEventsItemAndField}.
	 *
	 * @param <EVENTS>        the events interface
	 * @param eventsInterface the events interface class
	 * @param itemsAndFields  the items/fields producing it; copied
	 *                        defensively, so later changes to the caller's
	 *                        list do not affect this descriptor's stored
	 *                        state
	 */
	public <EVENTS> void addProducedEventsItemAndField(final Class<EVENTS> eventsInterface,
			final List<ItemAndField> itemsAndFields) {
		this.producedEvents.put(eventsInterface, new ArrayList<>(itemsAndFields));
	}

	@Override
	public <PARAMETERS> List<ItemAndField> getAccessedParametersItemAndField(
			final Class<PARAMETERS> parametersInterface) {
		final List<ItemAndField> result = this.accessedParameters.get(parametersInterface);
		return result == null ? new ArrayList<>() : new ArrayList<>(result);
	}

	/**
	 * Records the items/fields associated with an accessed-parameters
	 * interface, for later lookup by {@link #getAccessedParametersItemAndField}.
	 *
	 * @param <PARAMETERS>        the parameters interface
	 * @param parametersInterface the parameters interface class
	 * @param itemsAndFields      the items/fields accessing it; copied
	 *                            defensively, so later changes to the
	 *                            caller's list do not affect this
	 *                            descriptor's stored state
	 */
	public <PARAMETERS> void addAccessedParametersItemAndField(final Class<PARAMETERS> parametersInterface,
			final List<ItemAndField> itemsAndFields) {
		this.accessedParameters.put(parametersInterface, new ArrayList<>(itemsAndFields));
	}

	@Override
	public <SUBSCRIBE> List<ItemAndField> getUsedSubscribeItemAndField(final Class<SUBSCRIBE> subscribeInterface) {
		final List<ItemAndField> result = this.usedSubscribes.get(subscribeInterface);
		return result == null ? new ArrayList<>() : new ArrayList<>(result);
	}

	/**
	 * Records the items/fields associated with a used-subscribe interface,
	 * for later lookup by {@link #getUsedSubscribeItemAndField}.
	 *
	 * @param <SUBSCRIBE>        the events subscribe interface
	 * @param subscribeInterface the events subscribe interface class
	 * @param itemsAndFields     the items/fields using it; copied
	 *                           defensively, so later changes to the
	 *                           caller's list do not affect this
	 *                           descriptor's stored state
	 */
	public <SUBSCRIBE> void addUsedSubscribeItemAndField(final Class<SUBSCRIBE> subscribeInterface,
			final List<ItemAndField> itemsAndFields) {
		this.usedSubscribes.put(subscribeInterface, new ArrayList<>(itemsAndFields));
	}

}
