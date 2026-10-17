/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.registry;

import java.util.List;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Reads component descriptors and resolves component item instances that own
 * ports.
 */
@Services
public interface PortRegistryServices {
	/**
	 * <p>
	 * Returns the component item instance implementing the given provided services
	 * interface.
	 * </p>
	 * 
	 * @param <SERVICES>        services interface type
	 * @param servicesInterface a services interface
	 * @return the component item instance implementing the given provided services
	 *         interface, or {@code null} if there is none
	 */
	<SERVICES> SERVICES getServicesProvider(final Class<SERVICES> servicesInterface);

	/**
	 * <p>
	 * Returns the component item instance implementing the given consumed events
	 * interface.
	 * </p>
	 * 
	 * @param <EVENT>         events interface type
	 * @param componentType   a component type
	 * @param eventsInterface an events interface
	 * @return the component item instance implementing the given consumed events
	 *         interface, or {@code null} if there is none
	 */
	<EVENT> EVENT getEventsConsumer(final Class<?> componentType, final Class<EVENT> eventsInterface);

	/**
	 * <p>
	 * Returns the component item instance implementing the given offered parameters
	 * interface.
	 * </p>
	 * 
	 * @param <PARAMETERS>        parameters interface type
	 * @param parametersInterface a parameters interface
	 * @return the component item instance implementing the given offered parameters
	 *         interface, or {@code null} if there is none
	 */
	<PARAMETERS> PARAMETERS getParametersOfferor(final Class<PARAMETERS> parametersInterface);

	/**
	 * <p>
	 * Returns the component item instance implementing the given subscribe
	 * interface.
	 * </p>
	 * 
	 * @param <EVENT>           events interface type
	 * @param <SUBSCRIBE>       subscribe interface type
	 * @param eventsInterface   events interface associated with the subscribe
	 *                          delegate
	 * @return the component item instance implementing the given subscribe
	 *         interface, or {@code null} if there is none
	 */
	<EVENT, SUBSCRIBE> SUBSCRIBE getSubscribeDelegate(final Class<EVENT> eventsInterface);

	/**
	 * <p>
	 * Returns the component descriptor for the given component type.
	 * </p>
	 * 
	 * @param componentType a component type
	 * @return the component descriptor for the given component type
	 */
	ComponentDescriptor getComponentDescriptor(final Class<?> componentType);

	/**
	 * Returns fields and item instances declaring a used services port.
	 *
	 * @param <SERVICES>        services interface type
	 * @param componentType     component type owning the declaration
	 * @param servicesInterface used services interface
	 * @return item-and-field entries declaring the port, empty when the component type or the port is unknown
	 */
	<SERVICES> List<ItemAndField> getUsedServicesItemAndField(final Class<?> componentType,
			final Class<SERVICES> servicesInterface);

	/**
	 * Returns fields and item instances declaring a produced events port.
	 *
	 * @param <EVENTS>        events interface type
	 * @param componentType   component type owning the declaration
	 * @param eventsInterface produced events interface
	 * @return item-and-field entries declaring the port, empty when the component type or the port is unknown
	 */
	<EVENTS> List<ItemAndField> getProducedEventsItemAndField(final Class<?> componentType,
			final Class<EVENTS> eventsInterface);

	/**
	 * Returns fields and item instances declaring an accessed parameters port.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param componentType       component type owning the declaration
	 * @param parametersInterface accessed parameters interface
	 * @return item-and-field entries declaring the port, empty when the component type or the port is unknown
	 */
	<PARAMETERS> List<ItemAndField> getAccessedParametersItemAndField(final Class<?> componentType,
			final Class<PARAMETERS> parametersInterface);

	/**
	 * Returns fields and item instances declaring a used subscribe port.
	 *
	 * @param <SUBSCRIBE>        subscribe interface type
	 * @param componentType      component type owning the declaration
	 * @param subscribeInterface used subscribe interface
	 * @return item-and-field entries declaring the port, empty when the component type or the port is unknown
	 */
	<SUBSCRIBE> List<ItemAndField> getUsedSubscribeItemAndField(final Class<?> componentType,
			final Class<SUBSCRIBE> subscribeInterface);

}
