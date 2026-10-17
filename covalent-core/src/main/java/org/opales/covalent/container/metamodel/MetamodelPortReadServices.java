/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.metamodel;

import java.util.Set;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.metamodel.metamodel.EventsMetamodel;

/**
 * Reads component metamodel port declarations and port type mappings.
 * <p>
 * This is the read-only counterpart used internally by other managers to
 * inspect a deployed component type's structure (its provided/used
 * services, produced/consumed events, offered/accessed parameters and
 * provided/used subscribers) and to resolve a port interface back to the
 * component type that declares it, without pulling in the full metamodel
 * write API.
 */
@Services
public interface MetamodelPortReadServices {

	/**
	 * Returns the events interface associated with a used subscribe
	 * interface, resolved from the component that provides that subscriber.
	 *
	 * @param <EVENTS>          the events interface type
	 * @param <SUBSCRIBE>       the subscribe interface type
	 * @param subscribeInterface the subscribe interface to resolve
	 * @return the events interface the subscriber subscribes to
	 */
	<EVENTS, SUBSCRIBE> Class<EVENTS> getEventsForSubscriber(final Class<SUBSCRIBE> subscribeInterface);

	/**
	 * Returns the subscribe interface associated with an events interface,
	 * resolved from the component that produces those events, falling back
	 * to a generic subscribe interface when the producer has none of its
	 * own.
	 *
	 * @param <EVENTS>        the events interface type
	 * @param <SUBSCRIBE>     the subscribe interface type
	 * @param eventsInterface the events interface to resolve
	 * @return the subscribe interface associated with the events interface
	 */
	<EVENTS, SUBSCRIBE> Class<SUBSCRIBE> getSubscribeInterface(final Class<EVENTS> eventsInterface);

	/**
	 * Returns the component type that provides the given services
	 * interface.
	 *
	 * @param <CT>              the component type
	 * @param <SERVICES>        the services interface type
	 * @param servicesInterface the provided services interface
	 * @return the component type providing that services interface, or
	 *         {@code null} if none is registered
	 */
	<CT, SERVICES> Class<CT> getComponentTypeForProvidedServices(Class<SERVICES> servicesInterface);

	/**
	 * Returns the component type that produces the given events interface.
	 *
	 * @param <CT>            the component type
	 * @param <EVENTS>        the events interface type
	 * @param eventsInterface the produced events interface
	 * @return the component type producing that events interface, or
	 *         {@code null} if none is registered
	 */
	<CT, EVENTS> Class<CT> getComponentTypeForProducedEvents(final Class<EVENTS> eventsInterface);

	/**
	 * Returns the component type that offers the given parameters
	 * interface.
	 *
	 * @param <CT>                the component type
	 * @param <PARAMETERS>        the parameters interface type
	 * @param parametersInterface the offered parameters interface
	 * @return the component type offering that parameters interface, or
	 *         {@code null} if none is registered
	 */
	<CT, PARAMETERS> Class<CT> getComponentTypeForOfferedParameters(Class<PARAMETERS> parametersInterface);

	/**
	 * Returns the services interfaces provided by the given component type,
	 * including those provided by its child items, that it owns. An
	 * interface declared by several components is owned by the first one
	 * registered and is not returned for the others.
	 *
	 * @param componentType the component type
	 * @return the provided services interfaces owned by the component type
	 */
	Set<Class<?>> getProvidedServices(Class<?> componentType);

	/**
	 * Returns the services interfaces used by the given component type,
	 * including those used by its child items.
	 *
	 * @param componentType the component type
	 * @return the used services interfaces
	 */
	Set<Class<?>> getUsedServices(Class<?> componentType);

	/**
	 * Returns the events interfaces produced by the given component type,
	 * including those produced by its child items, that it owns. An
	 * interface declared by several components is owned by the first one
	 * registered and is not returned for the others.
	 *
	 * @param componentType the component type
	 * @return the produced events interfaces owned by the component type
	 */
	Set<Class<?>> getProducedEvents(Class<?> componentType);

	/**
	 * Returns the events interfaces consumed by the given component type,
	 * including those consumed by its child items.
	 *
	 * @param componentType the component type
	 * @return the consumed events interfaces
	 */
	Set<Class<?>> getConsumedEvents(Class<?> componentType);

	/**
	 * Returns the parameters interfaces offered by the given component
	 * type, including those offered by its child items, that it owns. An
	 * interface declared by several components is owned by the first one
	 * registered and is not returned for the others.
	 *
	 * @param componentType the component type
	 * @return the offered parameters interfaces owned by the component type
	 */
	Set<Class<?>> getOfferedParameters(Class<?> componentType);

	/**
	 * Returns the parameters interfaces accessed by the given component
	 * type, including those accessed by its child items.
	 *
	 * @param componentType the component type
	 * @return the accessed parameters interfaces
	 */
	Set<Class<?>> getAccessedParameters(Class<?> componentType);

	/**
	 * Returns the events subscribe interfaces provided by the given
	 * component type, including those provided by its child items.
	 *
	 * @param componentType the component type
	 * @return the provided subscribe interfaces
	 */
	Set<Class<?>> getProvidedSubscribers(Class<?> componentType);

	/**
	 * Returns the events subscribe interfaces used by the given component
	 * type, including those used by its child items.
	 *
	 * @param componentType the component type
	 * @return the used subscribe interfaces
	 */
	Set<Class<?>> getUsedSubscribers(Class<?> componentType);

	/**
	 * Returns, among the given component type's consumed events, the ones
	 * for which no explicit subscribe interface is used and which the
	 * container should therefore subscribe to automatically.
	 *
	 * @param componentType the component type
	 * @return the consumed events interfaces without an explicit subscriber
	 */
	Set<Class<?>> getConsumedEventsWithoutSubscriber(Class<?> componentType);

	/**
	 * Returns the metamodel describing clone behavior and method membership
	 * for the given produced events interface.
	 *
	 * @param <EVENTS>        the events interface type
	 * @param eventsInterface the events interface to look up
	 * @return the events metamodel for that interface
	 */
	<EVENTS> EventsMetamodel<EVENTS> getEventsMetamodel(final Class<EVENTS> eventsInterface);

}
