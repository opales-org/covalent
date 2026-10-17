/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.metamodel.metamodel;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Read-only view of a component item metamodel.
 */
public interface ReadOnlyComponentItemMetamodel {

	/**
	 * Returns the item's or component's implementation class.
	 *
	 * @return the implementation class
	 */
	Class<?> getImplementation();

	/**
	 * Returns the services interfaces this item or component provides.
	 *
	 * @return the provided services interfaces
	 */
	List<Class<?>> getProvidedServices();

	/**
	 * Returns the services interfaces this item or component uses.
	 *
	 * @return the used services interfaces
	 */
	List<Class<?>> getUsedServices();

	/**
	 * Returns the events interfaces this item or component produces.
	 *
	 * @return the produced events interfaces
	 */
	List<Class<?>> getProducedEvents();

	/**
	 * Returns the events interfaces this item or component consumes.
	 *
	 * @return the consumed events interfaces
	 */
	List<Class<?>> getConsumedEvents();

	/**
	 * Returns the parameters interfaces this item or component offers.
	 *
	 * @return the offered parameters interfaces
	 */
	List<Class<?>> getOfferedParameters();

	/**
	 * Returns the parameters interfaces this item or component accesses.
	 *
	 * @return the accessed parameters interfaces
	 */
	List<Class<?>> getAccessedParameters();

	/**
	 * Returns the events subscribe interfaces this item or component
	 * provides.
	 *
	 * @return the provided subscribe interfaces
	 */
	List<Class<?>> getProvidedSubscribers();

	/**
	 * Returns the events subscribe interfaces this item or component
	 * implements as a subscribe delegate.
	 *
	 * @return the implemented subscribe interfaces
	 */
	List<Class<?>> getImplementedSubscribers();

	/**
	 * Returns the events subscribe interfaces this item or component uses
	 * for manual subscription.
	 *
	 * @return the used subscribe interfaces
	 */
	List<Class<?>> getUsedSubscribers();

	/**
	 * Returns every {@code @UsedServices}-annotated field.
	 *
	 * @return the used-services fields
	 */
	List<Field> getUsedServicesFields();

	/**
	 * Returns the {@code @UsedServices}-annotated field for the given
	 * services interface.
	 *
	 * @param servicesInterface the services interface
	 * @return the matching field, or {@code null} if none exists
	 */
	Field getUsedServicesField(Class<?> servicesInterface);

	/**
	 * Returns every {@code @ProducedEvents}-annotated field.
	 *
	 * @return the produced-events fields
	 */
	List<Field> getProducedEventsFields();

	/**
	 * Returns the {@code @ProducedEvents}-annotated field for the given
	 * events interface.
	 *
	 * @param eventsInterface the events interface
	 * @return the matching field, or {@code null} if none exists
	 */
	Field getProducedEventsField(Class<?> eventsInterface);

	/**
	 * Returns every {@code @UsedEventsSubscribe}-annotated field.
	 *
	 * @return the used-subscriber fields
	 */
	List<Field> getUsedSubscriberFields();

	/**
	 * Returns the {@code @UsedEventsSubscribe}-annotated field for the given
	 * subscribe interface.
	 *
	 * @param subscribeInterface the events subscribe interface
	 * @return the matching field, or {@code null} if none exists
	 */
	Field getUsedSubscriberField(Class<?> subscribeInterface);

	/**
	 * Returns every {@code @AccessedParameters}-annotated field.
	 *
	 * @return the accessed-parameters fields
	 */
	List<Field> getAccessedParametersFields();

	/**
	 * Returns the {@code @AccessedParameters}-annotated field for the given
	 * parameters interface.
	 *
	 * @param parametersInterface the parameters interface
	 * @return the matching field, or {@code null} if none exists
	 */
	Field getAccessedParametersField(Class<?> parametersInterface);
}
