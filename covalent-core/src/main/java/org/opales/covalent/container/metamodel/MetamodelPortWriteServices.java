/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.metamodel;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Applies targeted dynamic port mutations to existing component metamodels.
 * <p>
 * Unlike {@link MetamodelWriteServices}, which creates, updates and deletes
 * a whole {@code ComponentMetamodel}, these methods add or remove a single
 * port (a provided services, used services, offered parameters, accessed
 * parameters, produced events or consumed events interface) on a component
 * type that is already
 * registered, keeping the manager's port-to-type index maps consistent with
 * the change.
 */
@Services
public interface MetamodelPortWriteServices extends MetamodelPortReadServices {

	/**
	 * Adds a services interface to the set of services provided by a
	 * component type.
	 *
	 * @param <SERVICES>            the services interface type
	 * @param providerComponentType the component type gaining the provided
	 *                              service
	 * @param servicesInterface     the services interface to add
	 */
	<SERVICES> void addProvidedServices(final Class<?> providerComponentType,
			final Class<SERVICES> servicesInterface);

	/**
	 * Removes a services interface from the set of services provided by a
	 * component type, also removing it from any child item that provided
	 * it.
	 *
	 * @param <SERVICES>            the services interface type
	 * @param providerComponentType the component type losing the provided
	 *                              service; must match the type currently
	 *                              registered as the interface's provider
	 * @param servicesInterface     the services interface to remove
	 * @throws IllegalArgumentException if {@code providerComponentType} does
	 *                                  not match the component type
	 *                                  currently registered as the
	 *                                  interface's provider
	 */
	<SERVICES> void removeProvidedServices(final Class<?> providerComponentType,
			final Class<SERVICES> servicesInterface);

	/**
	 * Adds a parameters interface to the set of parameters offered by a
	 * component type.
	 *
	 * @param <PARAMETERS>          the parameters interface type
	 * @param offeringComponentType the component type gaining the offered
	 *                              parameters
	 * @param parametersInterface   the parameters interface to add
	 */
	<PARAMETERS> void addOfferedParameters(final Class<?> offeringComponentType,
			final Class<PARAMETERS> parametersInterface);

	/**
	 * Removes a parameters interface from the set of parameters offered by
	 * a component type, also removing it from any child item that offered
	 * it.
	 *
	 * @param <PARAMETERS>          the parameters interface type
	 * @param offeringComponentType the component type losing the offered
	 *                              parameters; must match the type
	 *                              currently registered as the interface's
	 *                              offeror
	 * @param parametersInterface   the parameters interface to remove
	 * @throws IllegalArgumentException if {@code offeringComponentType}
	 *                                  does not match the component type
	 *                                  currently registered as the
	 *                                  interface's offeror
	 */
	<PARAMETERS> void removeOfferedParameters(final Class<?> offeringComponentType,
			final Class<PARAMETERS> parametersInterface);

	/**
	 * Adds an events interface to the set of events produced by a component
	 * type, completing that component's metamodel so the corresponding
	 * events metamodel and subscriber mappings are (re)built.
	 *
	 * @param <EVENTS>             the events interface type
	 * @param producerComponentType the component type gaining the produced
	 *                              events
	 * @param eventsInterface       the events interface to add
	 */
	<EVENTS> void addProducedEvents(final Class<?> producerComponentType,
			final Class<EVENTS> eventsInterface);

	/**
	 * Removes an events interface from the set of events produced by a
	 * component type, also removing it (and its associated subscriber
	 * mapping) from any child item that produced it.
	 *
	 * @param <EVENTS>             the events interface type
	 * @param producerComponentType the component type losing the produced
	 *                              events; must match the type currently
	 *                              registered as the interface's producer
	 * @param eventsInterface       the events interface to remove
	 * @throws IllegalArgumentException if {@code producerComponentType}
	 *                                  does not match the component type
	 *                                  currently registered as the
	 *                                  interface's producer
	 */
	<EVENTS> void removeProducedEvents(final Class<?> producerComponentType,
			final Class<EVENTS> eventsInterface);

	/**
	 * Adds an events interface to the set of events consumed by a component
	 * type, completing that component's metamodel so the corresponding
	 * consumer/subscriber mapping is rebuilt.
	 *
	 * @param <EVENTS>             the events interface type
	 * @param consumerComponentType the component type gaining the consumed
	 *                              events
	 * @param eventsInterface       the events interface to add
	 */
	<EVENTS> void addConsumedEvents(final Class<?> consumerComponentType,
			final Class<EVENTS> eventsInterface);

	/**
	 * Removes an events interface from the set of events consumed by a
	 * component type, also removing it (and its associated subscriber
	 * mapping) from any child item that consumed it.
	 *
	 * @param <EVENTS>             the events interface type
	 * @param consumerComponentType the component type losing the consumed
	 *                              events
	 * @param eventsInterface       the events interface to remove
	 */
	<EVENTS> void removeConsumedEvents(final Class<?> consumerComponentType,
			final Class<EVENTS> eventsInterface);

	/**
	 * Adds a services interface to the set of services used by a component
	 * type.
	 *
	 * @param <SERVICES>        the services interface type
	 * @param userComponentType the component type gaining the used services
	 * @param servicesInterface the services interface to add
	 */
	<SERVICES> void addUsedServices(final Class<?> userComponentType, final Class<SERVICES> servicesInterface);

	/**
	 * Removes a services interface from the set of services used by a
	 * component type.
	 *
	 * @param <SERVICES>        the services interface type
	 * @param userComponentType the component type losing the used services
	 * @param servicesInterface the services interface to remove
	 */
	<SERVICES> void removeUsedServices(final Class<?> userComponentType, final Class<SERVICES> servicesInterface);

	/**
	 * Adds a parameters interface to the set of parameters accessed by a
	 * component type.
	 *
	 * @param <PARAMETERS>          the parameters interface type
	 * @param accessorComponentType the component type gaining the accessed
	 *                              parameters
	 * @param parametersInterface   the parameters interface to add
	 */
	<PARAMETERS> void addAccessedParameters(final Class<?> accessorComponentType,
			final Class<PARAMETERS> parametersInterface);

	/**
	 * Removes a parameters interface from the set of parameters accessed by
	 * a component type.
	 *
	 * @param <PARAMETERS>          the parameters interface type
	 * @param accessorComponentType the component type losing the accessed
	 *                              parameters
	 * @param parametersInterface   the parameters interface to remove
	 */
	<PARAMETERS> void removeAccessedParameters(final Class<?> accessorComponentType,
			final Class<PARAMETERS> parametersInterface);

}
