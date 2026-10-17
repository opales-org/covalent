/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.metamodel.metamodel;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

/**
 * Describes one component type, its implementation item, nested items, composed
 * components, and aggregated port declarations.
 */
public interface ComponentMetamodel extends ReadOnlyComponentMetamodel, ComponentItemMetamodel {

	/**
	 * Returns the component type described by this metamodel.
	 *
	 * @return component type
	 */
	Class<?> getComponentType();

	/**
	 * Sets the component type described by this metamodel.
	 *
	 * @param componentType component type
	 */
	void setComponentType(Class<?> componentType);

	/**
	 * Returns the parent component metamodel when this component is composed by
	 * another component.
	 *
	 * @return parent component metamodel, or null for root components
	 */
	@Override
	ComponentMetamodel getParentMetamodel();

	/**
	 * Sets the parent component metamodel.
	 *
	 * @param parentMetamodel parent component metamodel
	 */
	void setParentMetamodel(ComponentMetamodel parentMetamodel);

	/**
	 * Returns the parent component type when this component is composed by another
	 * component.
	 *
	 * @return parent component type, or null for root components
	 */
	Class<?> getParentComponentType();

	/**
	 * Returns provided services from component implementation and items.
	 * 
	 * @return provided services from the component and its items
	 */
	Set<Class<?>> getAllProvidedServices();

	/**
	 * Returns used services from component implementation and items.
	 * 
	 * @return used services from the component and its items
	 */
	Set<Class<?>> getAllUsedServices();

	/**
	 * Returns produced events from component implementation and items.
	 * 
	 * @return produced events from the component and its items
	 */
	Set<Class<?>> getAllProducedEvents();

	/**
	 * Returns consumed events from component implementation and items.
	 * 
	 * @return consumed events from the component and its items
	 */
	Set<Class<?>> getAllConsumedEvents();

	/**
	 * Returns offered parameters from component implementation and items.
	 *
	 * @return offered parameters from the component and its items
	 */
	Set<Class<?>> getAllOfferedParameters();

	/**
	 * Returns accessed parameters from component implementation and items.
	 *
	 * @return accessed parameters from the component and its items
	 */
	Set<Class<?>> getAllAccessedParameters();

	/**
	 * Returns consumed events from component implementation and items of which
	 * subscriber interface is used. The consumed events should not be automatically
	 * subscribed.
	 * 
	 * @return consumed events with an explicit subscribe interface
	 */
	Set<Class<?>> getAllConsumedEventsWithSubscriber();

	/**
	 * Returns consumed events from component implementation and items of which
	 * subscriber interface is not used. The consumed events should be automatically
	 * subscribed.
	 * 
	 * @return consumed events without an explicit subscribe interface
	 */
	Set<Class<?>> getAllConsumedEventsWithoutSubscriber();

	/**
	 * Returns provided subscribers interfaces from component implementation and
	 * items.
	 * 
	 * @return provided subscribe interfaces
	 */
	Set<Class<?>> getAllProvidedSubscribers();

	/**
	 * Returns used subscribers interfaces from component implementation and items.
	 * 
	 * @return used subscribe interfaces
	 */
	Set<Class<?>> getAllUsedSubscribers();

	/**
	 * Returns events interface associated with subscriber interface from component
	 * implementation or items. Only returns events interface if the item implements
	 * it, so this method does not return items which create custom consumer
	 * objects.
	 * 
	 * @param <EVENTS>   events interface type
	 * @param <SUBSCRIBE> subscribe interface type
	 * @param subscriber used subscribe interface
	 * @return consumed events interface associated with the subscribe interface
	 */
	<EVENTS, SUBSCRIBE> Class<EVENTS> getConsumedEventsForUsedSubscriber(final Class<SUBSCRIBE> subscriber);

	/**
	 * Get provided subscribe interface for given events.
	 * 
	 * @param <EVENTS>        events interface type
	 * @param <SUBSCRIBE>     subscribe interface type
	 * @param eventsInterface produced events interface
	 * @return provided subscribe interface associated with the events interface
	 */
	<EVENTS, SUBSCRIBE> Class<SUBSCRIBE> getProvidedSubscriberForProducedEvents(final Class<EVENTS> eventsInterface);

	/**
	 * Returns the produced events interface for a provided subscribe interface.
	 * 
	 * @param <EVENTS>   events interface type
	 * @param <SUBSCRIBE> subscribe interface type
	 * @param subscriber provided subscribe interface
	 * @return produced events interface associated with the subscribe interface
	 */
	<EVENTS, SUBSCRIBE> Class<EVENTS> getProducedEventsForProvidedSubscriber(final Class<SUBSCRIBE> subscriber);

	/**
	 * Adds the metamodel for an events interface.
	 *
	 * @param <EVENTS>        events interface type
	 * @param eventsMetamodel events metamodel to add
	 */
	<EVENTS> void addEventsMetamodel(EventsMetamodel<EVENTS> eventsMetamodel);

	/**
	 * Removes the metamodel for an events interface.
	 *
	 * @param <EVENTS>        events interface type
	 * @param eventsInterface events interface to remove
	 */
	<EVENTS> void removeEventsMetamodel(Class<EVENTS> eventsInterface);

	/**
	 * Returns the metamodel for an events interface.
	 *
	 * @param <EVENTS>        events interface type
	 * @param eventsInterface events interface to inspect
	 * @return events metamodel
	 */
	<EVENTS> EventsMetamodel<EVENTS> getEventsMetamodel(Class<EVENTS> eventsInterface);

	// Component items

	/**
	 * Returns the metamodels of component items.
	 *
	 * @return component item metamodels
	 */
	@Override
	List<? extends ComponentItemMetamodel> getItemMetamodels();

	/**
	 * Sets the metamodels of component items.
	 *
	 * @param componentItemStructures component item metamodels
	 */
	void setItemMetamodels(List<ComponentItemMetamodel> componentItemStructures);

	/**
	 * Returns component item types.
	 *
	 * @return component item types
	 */
	List<Class<?>> getItemTypes();

	/**
	 * Sets component item types.
	 *
	 * @param itemTypes component item types
	 */
	void setItemTypes(List<Class<?>> itemTypes);

	/**
	 * Records the field declaring a component item.
	 *
	 * @param itemClass item class
	 * @param field     field declaring the item
	 */
	void addItemField(Class<?> itemClass, Field field);

	/**
	 * Returns fields declaring component items.
	 *
	 * @return component item fields
	 */
	List<Field> getItemsFields();

	/**
	 * Returns the field declaring an item class.
	 *
	 * @param itemClass item class
	 * @return field declaring the item
	 */
	Field getItemField(Class<?> itemClass);

	// Composed components

	/**
	 * Returns metamodels of composed components.
	 *
	 * @return composed component metamodels
	 */
	@Override
	List<? extends ComponentMetamodel> getComposedMetamodels();

	/**
	 * Sets metamodels of composed components.
	 *
	 * @param composedComponentsMetamodels composed component metamodels
	 */
	void setComposedMetamodels(final List<ComponentMetamodel> composedComponentsMetamodels);

	/**
	 * Returns composed component types.
	 *
	 * @return composed component types
	 */
	List<Class<?>> getComposedTypes();

	/**
	 * Sets composed component types.
	 *
	 * @param composedTypes composed component types
	 */
	void setComposedTypes(List<Class<?>> composedTypes);

	/**
	 * Records the field declaring a composed component.
	 *
	 * @param composedClass composed component class
	 * @param field         field declaring the composed component
	 */
	void addComposedComponentField(Class<?> composedClass, Field field);

	/**
	 * Returns fields declaring composed components.
	 *
	 * @return composed component fields
	 */
	List<Field> getComposedComponentsFields();

	/**
	 * Returns the field declaring a composed component class.
	 *
	 * @param componentClass composed component class
	 * @return field declaring the composed component
	 */
	Field getComposedComponentField(final Class<?> componentClass);

}
