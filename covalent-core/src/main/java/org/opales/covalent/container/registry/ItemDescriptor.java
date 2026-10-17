/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.registry;

import java.lang.reflect.Field;
import java.util.List;

import org.opales.covalent.container.metamodel.metamodel.ReadOnlyComponentItemMetamodel;

/**
 * Read-only view of the runtime record of a single item (or, through its
 * sub-interface {@link ComponentDescriptor}, of a whole component): pairs the
 * item's live instance with its read-only metamodel, and offers convenience
 * lookups over that metamodel's field maps and interface lists.
 */
public interface ItemDescriptor {

	/**
	 * Returns the owning component's descriptor.
	 *
	 * @return the parent component descriptor, or {@code null} for a
	 *         top-level component's own descriptor
	 */
	ComponentDescriptor getParent();

	/**
	 * Returns this item's live instance.
	 *
	 * @return the instance, or {@code null} if not yet instantiated
	 */
	Object getInstance();

	/**
	 * Returns this item's metamodel, cast to the expected subtype.
	 *
	 * @param <T> the expected metamodel type
	 * @return this item's metamodel
	 */
	<T extends ReadOnlyComponentItemMetamodel> T getMetamodel();

	/**
	 * Returns this item's field annotated {@code @UsedEventsSubscribe} for the
	 * given interface.
	 *
	 * @param subscribeInterface the events subscribe interface
	 * @return the matching {@code @UsedEventsSubscribe} field, or
	 *         {@code null} if none exists
	 */
	Field getUsedSubscriberField(Class<?> subscribeInterface);

	/**
	 * Returns every {@code @UsedEventsSubscribe} field on this item.
	 *
	 * @return every {@code @UsedEventsSubscribe} field
	 */
	List<Field> getUsedSubscriberFields();

	/**
	 * Returns this item's field annotated {@code @UsedServices} for the
	 * given interface.
	 *
	 * @param servicesInterface the services interface
	 * @return the matching {@code @UsedServices} field, or {@code null} if
	 *         none exists
	 */
	Field getUsedServicesField(Class<?> servicesInterface);

	/**
	 * Returns every {@code @UsedServices} field on this item.
	 *
	 * @return every {@code @UsedServices} field
	 */
	List<Field> getUsedServicesFields();

	/**
	 * Returns this item's field annotated {@code @ProducedEvents} for the
	 * given interface.
	 *
	 * @param eventsInterface the events interface
	 * @return the matching {@code @ProducedEvents} field, or {@code null}
	 *         if none exists
	 */
	Field getProducedEventsField(Class<?> eventsInterface);

	/**
	 * Returns every {@code @ProducedEvents} field on this item.
	 *
	 * @return every {@code @ProducedEvents} field
	 */
	List<Field> getProducedEventsFields();

	/**
	 * Returns this item's field annotated {@code @AccessedParameters} for
	 * the given interface.
	 *
	 * @param parametersInterface the parameters interface
	 * @return the matching {@code @AccessedParameters} field, or
	 *         {@code null} if none exists
	 */
	Field getAccessedParametersField(Class<?> parametersInterface);

	/**
	 * Returns every {@code @AccessedParameters} field on this item.
	 *
	 * @return every {@code @AccessedParameters} field
	 */
	List<Field> getAccessedParametersFields();

	/**
	 * Checks whether this item implements the given events subscribe
	 * interface as a subscribe delegate.
	 *
	 * @param subscriber the events subscribe interface to check
	 * @return {@code true} if this item implements it as a subscribe
	 *         delegate
	 */
	boolean implementsSubscriber(Class<?> subscriber);

	/**
	 * Checks whether this item consumes the given events interface.
	 *
	 * @param eventsInterface the events interface to check
	 * @return {@code true} if this item consumes it
	 */
	boolean implementsConsumer(Class<?> eventsInterface);

	/**
	 * Checks whether this item provides the given services interface.
	 *
	 * @param servicesInterface the services interface to check
	 * @return {@code true} if this item provides it
	 */
	boolean implementsServices(Class<?> servicesInterface);

	/**
	 * Checks whether this item offers the given parameters interface.
	 *
	 * @param parametersInterface the parameters interface to check
	 * @return {@code true} if this item offers it
	 */
	boolean implementsParameters(Class<?> parametersInterface);

	/**
	 * Checks whether this item uses the given services interface.
	 *
	 * @param servicesInterface the services interface to check
	 * @return {@code true} if this item uses it
	 */
	boolean usesServices(Class<?> servicesInterface);

	/**
	 * Checks whether this item accesses the given parameters interface.
	 *
	 * @param parametersInterface the parameters interface to check
	 * @return {@code true} if this item accesses it
	 */
	boolean accessesParameters(Class<?> parametersInterface);

}
