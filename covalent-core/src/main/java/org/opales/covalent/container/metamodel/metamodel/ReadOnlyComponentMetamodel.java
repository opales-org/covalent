/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.metamodel.metamodel;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import org.opales.covalent.container.component.ComponentAnnotations;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;

/**
 * Read-only view of a component metamodel. Extends the item-level contract
 * ({@link ReadOnlyComponentItemMetamodel}) with everything specific to a
 * whole component: its component type, its place in a parent/child
 * composition hierarchy, its aggregated ports (own ports plus those of its
 * child items), and the maps used to resolve child items and composed
 * components from their declaring fields.
 */
public interface ReadOnlyComponentMetamodel extends ReadOnlyComponentItemMetamodel {

	/**
	 * Returns the component type described by this metamodel.
	 *
	 * @return the component type
	 */
	Class<?> getComponentType();

	/**
	 * Returns the parent component's metamodel when this component is
	 * composed by another component.
	 *
	 * @return the parent component metamodel, or {@code null} for a
	 *         top-level (root) component
	 */
	ReadOnlyComponentMetamodel getParentMetamodel();

	/**
	 * Returns the parent component's type when this component is composed
	 * by another component.
	 *
	 * @return the parent component type, or {@code null} for a top-level
	 *         (root) component
	 */
	Class<?> getParentComponentType();

	/**
	 * Returns the services interfaces provided by this component's
	 * implementation together with those provided by its child items.
	 *
	 * @return all provided services interfaces
	 */
	Set<Class<?>> getAllProvidedServices();

	/**
	 * Returns the services interfaces used by this component's
	 * implementation together with those used by its child items.
	 *
	 * @return all used services interfaces
	 */
	Set<Class<?>> getAllUsedServices();

	/**
	 * Returns the events interfaces produced by this component's
	 * implementation together with those produced by its child items.
	 *
	 * @return all produced events interfaces
	 */
	Set<Class<?>> getAllProducedEvents();

	/**
	 * Returns the events interfaces consumed by this component's
	 * implementation together with those consumed by its child items.
	 *
	 * @return all consumed events interfaces
	 */
	Set<Class<?>> getAllConsumedEvents();

	/**
	 * Returns the parameters interfaces offered by this component's
	 * implementation together with those offered by its child items.
	 *
	 * @return all offered parameters interfaces
	 */
	Set<Class<?>> getAllOfferedParameters();

	/**
	 * Returns the parameters interfaces accessed by this component's
	 * implementation together with those accessed by its child items.
	 *
	 * @return all accessed parameters interfaces
	 */
	Set<Class<?>> getAllAccessedParameters();

	/**
	 * Returns, among {@link #getAllConsumedEvents()}, the events interfaces
	 * for which an explicit subscribe interface is used. These are not
	 * eligible for automatic subscription, since the component (or one of
	 * its items) manages its subscription manually.
	 *
	 * @return the consumed events interfaces with an explicit subscriber
	 */
	Set<Class<?>> getAllConsumedEventsWithSubscriber();

	/**
	 * Returns, among {@link #getAllConsumedEvents()}, the events interfaces
	 * for which no explicit subscribe interface is used. These are the
	 * ones that should be automatically subscribed by the container.
	 *
	 * @return the consumed events interfaces without an explicit subscriber
	 */
	Set<Class<?>> getAllConsumedEventsWithoutSubscriber();

	/**
	 * Returns the events subscribe interfaces provided by this component's
	 * implementation together with those provided by its child items.
	 *
	 * @return all provided subscribe interfaces
	 */
	Set<Class<?>> getAllProvidedSubscribers();

	/**
	 * Returns the events subscribe interfaces used by this component's
	 * implementation together with those used by its child items.
	 *
	 * @return all used subscribe interfaces
	 */
	Set<Class<?>> getAllUsedSubscribers();

	/**
	 * Returns the consumed events interface associated with a used
	 * subscribe interface, looking first at this component's own fields
	 * and then, if not found, at its child items. Only returns a match
	 * when the events interface is actually declared as consumed, so this
	 * method never returns an events interface for a subscriber that
	 * builds a custom consumer object instead.
	 *
	 * @param <EVENTS>    the events interface type
	 * @param <SUBSCRIBE> the subscribe interface type
	 * @param subscriber  the used subscribe interface
	 * @return the consumed events interface associated with the subscribe
	 *         interface, or {@code null} if none
	 */
	<EVENTS, SUBSCRIBE> Class<EVENTS> getConsumedEventsForUsedSubscriber(final Class<SUBSCRIBE> subscriber);

	/**
	 * Returns the provided subscribe interface associated with a produced
	 * events interface, looking first at this component's own fields and
	 * then, if not found, at its child items.
	 *
	 * @param <EVENTS>        the events interface type
	 * @param <SUBSCRIBE>     the subscribe interface type
	 * @param eventsInterface the produced events interface
	 * @return the provided subscribe interface associated with the events
	 *         interface, or {@code null} if none
	 */
	<EVENTS, SUBSCRIBE> Class<SUBSCRIBE> getProvidedSubscriberForProducedEvents(final Class<EVENTS> eventsInterface);

	/**
	 * Returns the produced events interface associated with a provided
	 * subscribe interface, looking first at this component's own fields
	 * and then, if not found, at its child items.
	 *
	 * @param <EVENTS>    the events interface type
	 * @param <SUBSCRIBE> the subscribe interface type
	 * @param subscriber  the provided subscribe interface
	 * @return the produced events interface associated with the subscribe
	 *         interface, or {@code null} if none
	 */
	<EVENTS, SUBSCRIBE> Class<EVENTS> getProducedEventsForProvidedSubscriber(final Class<SUBSCRIBE> subscriber);

	/**
	 * Returns the metamodel describing clone behavior and method membership
	 * for the given events interface.
	 *
	 * @param <EVENTS>        the events interface type
	 * @param eventsInterface the events interface to look up
	 * @return the events metamodel, or {@code null} if this component does
	 *         not produce that events interface
	 */
	<EVENTS> EventsMetamodel<EVENTS> getEventsMetamodel(Class<EVENTS> eventsInterface);

	/**
	 * Returns the metamodels of this component's child items.
	 *
	 * @return the child item metamodels
	 */
	List<? extends ReadOnlyComponentItemMetamodel> getItemMetamodels();

	/**
	 * Returns the implementation classes of this component's child items.
	 *
	 * @return the child item types
	 */
	List<Class<?>> getItemTypes();

	/**
	 * Returns every field that declares a child item.
	 *
	 * @return the item-declaring fields
	 */
	List<Field> getItemsFields();

	/**
	 * Returns the field that declares the given child item class.
	 *
	 * @param itemClass the item implementation class
	 * @return the matching field, or {@code null} if none exists
	 */
	Field getItemField(Class<?> itemClass);

	/**
	 * Returns the metamodels of the components composed into this
	 * component.
	 *
	 * @return the composed component metamodels
	 */
	List<? extends ReadOnlyComponentMetamodel> getComposedMetamodels();

	/**
	 * Returns the component types composed into this component.
	 *
	 * @return the composed component types
	 */
	List<Class<?>> getComposedTypes();

	/**
	 * Returns every field that declares a composed component.
	 *
	 * @return the composed-component-declaring fields
	 */
	List<Field> getComposedComponentsFields();

	/**
	 * Returns the field that declares the given composed component class.
	 *
	 * @param componentClass the composed component class
	 * @return the matching field, or {@code null} if none exists
	 */
	Field getComposedComponentField(final Class<?> componentClass);

	/**
	 * Tells whether this component is composed of other components.
	 *
	 * @return {@code true} when at least one component is composed into this one
	 */
	default boolean isComposite() {
		return !this.getComposedTypes().isEmpty();
	}

	/**
	 * Tells whether the container does not manage this component: it is registered as it is, without the
	 * component annotations a managed one carries. A component is managed when its type is annotated
	 * {@link Component}, when its type is itself an implementation, or when its implementation is annotated
	 * {@link Implementation}.
	 *
	 * @return {@code true} for a raw component
	 */
	default boolean isRaw() {
		Class<?> type = this.getComponentType();
		if (type != null && (ComponentAnnotations.isComponent(type) || ComponentAnnotations.isImplementation(type))) {
			return false;
		}
		Class<?> implementation = this.getImplementation();
		return implementation == null || !ComponentAnnotations.isImplementation(implementation);
	}
}
