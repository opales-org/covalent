/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.metamodel.metamodel;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Describes the implementation, ports, subscribe mappings, and port fields of a
 * component item or monolithic component implementation.
 */
public interface ComponentItemMetamodel extends ReadOnlyComponentItemMetamodel {

	/**
	 * Sets the implementation class described by this item metamodel.
	 *
	 * @param implementationClass implementation class
	 */
	void setImplementation(Class<?> implementationClass);

	/**
	 * Returns the implementation class described by this item metamodel.
	 *
	 * @return implementation class
	 */
	Class<?> getImplementation();

	/**
	 * Sets the services interfaces provided by this item.
	 *
	 * @param providedServices provided services interfaces
	 */
	void setProvidedServices(List<Class<?>> providedServices);

	/**
	 * Returns the services interfaces provided by this item.
	 *
	 * @return provided services interfaces
	 */
	List<Class<?>> getProvidedServices();

	/**
	 * Sets the services interfaces used by this item.
	 *
	 * @param usedServices used services interfaces
	 */
	void setUsedServices(List<Class<?>> usedServices);

	/**
	 * Returns the services interfaces used by this item.
	 *
	 * @return used services interfaces
	 */
	List<Class<?>> getUsedServices();

	/**
	 * Sets the events interfaces produced by this item.
	 *
	 * @param producedEvents produced events interfaces
	 */
	void setProducedEvents(List<Class<?>> producedEvents);

	/**
	 * Returns the events interfaces produced by this item.
	 *
	 * @return produced events interfaces
	 */
	List<Class<?>> getProducedEvents();

	/**
	 * Sets the events interfaces consumed by this item.
	 *
	 * @param consumedEvents consumed events interfaces
	 */
	void setConsumedEvents(List<Class<?>> consumedEvents);

	/**
	 * Returns the events interfaces consumed by this item.
	 *
	 * @return consumed events interfaces
	 */
	List<Class<?>> getConsumedEvents();

	/**
	 * Sets the parameters interfaces offered by this item.
	 *
	 * @param offeredParameters offered parameters interfaces
	 */
	void setOfferedParameters(List<Class<?>> offeredParameters);

	/**
	 * Returns the parameters interfaces offered by this item.
	 *
	 * @return offered parameters interfaces
	 */
	List<Class<?>> getOfferedParameters();

	/**
	 * Sets the parameters interfaces accessed by this item.
	 *
	 * @param accessedParameters accessed parameters interfaces
	 */
	void setAccessedParameters(List<Class<?>> accessedParameters);

	/**
	 * Returns the parameters interfaces accessed by this item.
	 *
	 * @return accessed parameters interfaces
	 */
	List<Class<?>> getAccessedParameters();

	/**
	 * Sets the subscribe interfaces provided by this item.
	 *
	 * @param providedSubscribers provided subscribe interfaces
	 */
	void setProvidedSubscribers(List<Class<?>> providedSubscribers);

	/**
	 * Returns all the subscribers interface found for all events produced by this
	 * component.
	 * 
	 * @return provided subscribe interfaces
	 */
	List<Class<?>> getProvidedSubscribers();

	/**
	 * Sets the subscribe interfaces implemented by this item.
	 *
	 * @param implementedSubscribers implemented subscribe interfaces
	 */
	void setImplementedSubscribers(List<Class<?>> implementedSubscribers);

	/**
	 * <p>
	 * Returns all the subscribers interfaces implemented by this component item. By
	 * implementing a subscriber interface, the component declares to be a delegate
	 * for these events subscription. The subscribe method implementation will be
	 * called for each subscribing consumer.
	 * </p>
	 * 
	 * @return all the subscribers interfaces implemented by this component item
	 */
	List<Class<?>> getImplementedSubscribers();

	/**
	 * Sets the subscribe interfaces used by this item.
	 *
	 * @param usedEventsSubscribes used subscribe interfaces
	 */
	void setUsedSubscribers(List<Class<?>> usedEventsSubscribes);

	/**
	 * Returns the subscribe interfaces used by this item.
	 *
	 * @return used subscribe interfaces
	 */
	List<Class<?>> getUsedSubscribers();

	/**
	 * Maps a used subscribe interface to the consumed events interface it
	 * subscribes to.
	 *
	 * @param subscribeInterface used subscribe interface
	 * @param eventsInterface    consumed events interface
	 */
	void mapUsedSubscriberAndConsumedEvents(final Class<?> subscribeInterface, final Class<?> eventsInterface);

	/**
	 * Removes mappings for a consumed events interface.
	 *
	 * @param eventsInterface consumed events interface
	 */
	void removeUsedSubscriberAndConsumedEvents(final Class<?> eventsInterface);

	/**
	 * Maps a provided subscribe interface to the produced events interface it
	 * exposes.
	 *
	 * @param subscribeInterface provided subscribe interface
	 * @param eventsInterface    produced events interface
	 */
	void mapProvidedSubscriberAndProducedEvents(final Class<?> subscribeInterface,
			final Class<?> eventsInterface);

	/**
	 * Removes mappings for a produced events interface.
	 *
	 * @param eventsInterface produced events interface
	 */
	void removeProvidedSubscriberAndProducedEvents(final Class<?> eventsInterface);

	/**
	 * Records the field declaring a used services interface.
	 *
	 * @param servicesInterface used services interface
	 * @param field             field declaring the port
	 */
	void addUsedServicesField(Class<?> servicesInterface, Field field);

	/**
	 * Returns fields declaring used services.
	 *
	 * @return used services fields
	 */
	List<Field> getUsedServicesFields();

	/**
	 * Returns the field declaring a used services interface.
	 *
	 * @param servicesInterface used services interface
	 * @return field declaring the port
	 */
	Field getUsedServicesField(Class<?> servicesInterface);

	/**
	 * Records the field declaring a produced events interface.
	 *
	 * @param eventsInterface produced events interface
	 * @param field           field declaring the port
	 */
	void addProducedEventsField(Class<?> eventsInterface, Field field);

	/**
	 * Returns fields declaring produced events.
	 *
	 * @return produced events fields
	 */
	List<Field> getProducedEventsFields();

	/**
	 * Returns the field declaring a produced events interface.
	 *
	 * @param eventsInterface produced events interface
	 * @return field declaring the port
	 */
	Field getProducedEventsField(Class<?> eventsInterface);

	/**
	 * Records the field declaring a used subscribe interface.
	 *
	 * @param subscribeInterface used subscribe interface
	 * @param field              field declaring the port
	 */
	void addUsedSubscriberField(Class<?> subscribeInterface, Field field);

	/**
	 * Returns fields declaring used subscribe ports.
	 *
	 * @return used subscribe fields
	 */
	List<Field> getUsedSubscriberFields();

	/**
	 * Returns the field declaring a used subscribe interface.
	 *
	 * @param subscribeInterface used subscribe interface
	 * @return field declaring the port
	 */
	Field getUsedSubscriberField(Class<?> subscribeInterface);

	/**
	 * Records the field declaring an accessed parameters interface.
	 *
	 * @param parametersInterface accessed parameters interface
	 * @param field               field declaring the port
	 */
	void addAccessedParametersField(Class<?> parametersInterface, Field field);

	/**
	 * Returns fields declaring accessed parameters.
	 *
	 * @return accessed parameters fields
	 */
	List<Field> getAccessedParametersFields();

	/**
	 * Returns the field declaring an accessed parameters interface.
	 *
	 * @param parametersInterface accessed parameters interface
	 * @return field declaring the port
	 */
	Field getAccessedParametersField(Class<?> parametersInterface);

}
