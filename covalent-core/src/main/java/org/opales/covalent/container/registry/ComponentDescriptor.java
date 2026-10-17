/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.registry;

import java.util.List;

/**
 * Read-only view of the runtime record of a whole component: extends
 * {@link ItemDescriptor} with the component's child items, its child
 * (composed) components, and the lookups that resolve a port to the item or
 * field that implements it (provider, consumer, offeror and subscribe
 * delegate, and the reverse used, produced and accessed field lookups).
 * <p>
 * Only one item is returned when searching for the item implementing a
 * subscribe, events or services interface, while each item may declare a used
 * subscriber, used services or produced events field.
 * </p>
 */
public interface ComponentDescriptor extends ItemDescriptor {

	/**
	 * Returns this component's child items.
	 *
	 * @return a copy of this component's child items
	 */
	List<ItemDescriptor> getItems();

	/**
	 * Returns this component's child (composed) components.
	 *
	 * @return a copy of this component's child (composed) components
	 */
	List<ComponentDescriptor> getComponents();

	/**
	 * Returns the item which implements the provided services interface.
	 *
	 * @param <SERVICES>        the services interface
	 * @param servicesInterface the service interface class
	 * @return the item implementing the services interface
	 */
	<SERVICES> SERVICES getServicesProvider(Class<SERVICES> servicesInterface);

	/**
	 * Returns the item which implements the consumed events interface.
	 *
	 * @param <EVENT>         the events interface
	 * @param eventsInterface the events interface class
	 * @return the item implementing the events interface
	 */
	<EVENT> EVENT getEventsConsumer(Class<EVENT> eventsInterface);

	/**
	 * Returns the item which offers the given parameters interface.
	 *
	 * @param <PARAMETERS>        the parameters interface
	 * @param parametersInterface the parameters interface class
	 * @return the item offering the parameters interface
	 */
	<PARAMETERS> PARAMETERS getParametersOfferor(Class<PARAMETERS> parametersInterface);

	/**
	 * Returns the item which implements the events subscribe interface.
	 *
	 * @param <SUBSCRIBE>        the events subscribe interface
	 * @param subscribeInterface the events subscribe interface class
	 * @return the item implementing the events subscribe interface
	 */
	<SUBSCRIBE> SUBSCRIBE getSubscribeDelegate(Class<SUBSCRIBE> subscribeInterface);

	/**
	 * Returns all the items or component impl which contain a usedServices field
	 * corresponding to given services interface, along with those fields.
	 *
	 * @param <SERVICES>        the services interface
	 * @param servicesInterface the services interface class
	 * @return a copy of the list of ItemAndField, each instance containing an
	 *         item or component impl and the associated usedServices field, or
	 *         an empty list if none is recorded
	 */
	<SERVICES> List<ItemAndField> getUsedServicesItemAndField(Class<SERVICES> servicesInterface);

	/**
	 * Returns all the items or component impl which contain a producedEvents field
	 * corresponding to given events interface, along with those fields.
	 *
	 * @param <EVENTS>        the events interface
	 * @param eventsInterface the events interface class
	 * @return a copy of the list of ItemAndField, each instance containing an
	 *         item or component impl and the associated producedEvents field,
	 *         or an empty list if none is recorded
	 */
	<EVENTS> List<ItemAndField> getProducedEventsItemAndField(Class<EVENTS> eventsInterface);

	/**
	 * Returns all the items or component impl which contain an
	 * accessedParameters field corresponding to given parameters interface,
	 * along with those fields.
	 *
	 * @param <PARAMETERS>        the parameters interface
	 * @param parametersInterface the parameters interface class
	 * @return a copy of the list of ItemAndField, each instance containing an
	 *         item or component impl and the associated accessedParameters
	 *         field, or an empty list if none is recorded
	 */
	<PARAMETERS> List<ItemAndField> getAccessedParametersItemAndField(Class<PARAMETERS> parametersInterface);

	/**
	 * Returns all the items or component impl which contain a usedSubscribe field
	 * corresponding to given subscribe interface, along with those fields.
	 *
	 * @param <SUBSCRIBE>        the events subscribe interface
	 * @param subscribeInterface the events subscribe interface class
	 * @return a copy of the list of ItemAndField, each instance containing an
	 *         item or component impl and the associated usedSubscribe field,
	 *         or an empty list if none is recorded
	 */
	<SUBSCRIBE> List<ItemAndField> getUsedSubscribeItemAndField(Class<SUBSCRIBE> subscribeInterface);

}
