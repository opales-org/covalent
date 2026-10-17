/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.registry.impl;

import java.lang.reflect.Field;
import java.util.List;

import org.opales.covalent.container.metamodel.metamodel.ReadOnlyComponentItemMetamodel;
import org.opales.covalent.container.registry.ItemDescriptor;
import org.opales.covalent.container.util.RuntimeCheck;

/**
 * Runtime record for a single item (or, through its subclass
 * {@link ComponentDescriptorImpl}, a whole component), implementing
 * {@link ItemDescriptor}: pairs the item's live instance with its read-only
 * metamodel, and offers convenience lookups over that metamodel's field maps
 * and interface lists.
 */
public class ItemDescriptorImpl implements ItemDescriptor {

	/** This item's metamodel (structural description of its ports). */
	ReadOnlyComponentItemMetamodel metamodel;

	/** This item's live instance, once instantiated. */
	Object instance;

	private final ComponentDescriptorImpl parent;

	/**
	 * Returns the owning component's descriptor.
	 *
	 * @return the parent component descriptor, or {@code null} for a
	 *         top-level component's own descriptor
	 */
	@Override
	public ComponentDescriptorImpl getParent() {
		return this.parent;
	}

	/**
	 * Creates a new descriptor for the given metamodel, under the given
	 * parent.
	 *
	 * @param parent    the owning component's descriptor, or {@code null}
	 *                  for a top-level component
	 * @param metamodel this item's metamodel
	 */
	public ItemDescriptorImpl(final ComponentDescriptorImpl parent, final ReadOnlyComponentItemMetamodel metamodel) {
		this.parent = parent;
		this.metamodel = metamodel;
	}

	/**
	 * Returns this item's live instance.
	 *
	 * @return the instance, or {@code null} if not yet instantiated
	 */
	@Override
	public Object getInstance() {
		return this.instance;
	}

	/**
	 * Sets this item's live instance.
	 *
	 * @param instance the instance; must not be {@code null}
	 */
	public void setInstance(final Object instance) {
		RuntimeCheck.notNull(instance, "instance");
		this.instance = instance;
	}

	/**
	 * Returns this item's metamodel, cast to the expected subtype.
	 *
	 * @param <T> the expected metamodel type
	 * @return this item's metamodel
	 */
	@Override
	public <T extends ReadOnlyComponentItemMetamodel> T getMetamodel() {
		@SuppressWarnings("unchecked")
		final T result = (T) this.metamodel;
		return result;
	}

	/**
	 * Returns this item's field annotated {@code @UsedEventsSubscribe} for the
	 * given interface.
	 *
	 * @param subscribeInterface the events subscribe interface
	 * @return the matching {@code @UsedEventsSubscribe} field, or
	 *         {@code null} if none exists
	 */
	@Override
	public Field getUsedSubscriberField(final Class<?> subscribeInterface) {
		return this.metamodel.getUsedSubscriberField(subscribeInterface);
	}

	/**
	 * Returns every {@code @UsedEventsSubscribe} field on this item.
	 *
	 * @return every {@code @UsedEventsSubscribe} field
	 */
	@Override
	public List<Field> getUsedSubscriberFields() {
		return this.metamodel.getUsedSubscriberFields();
	}

	/**
	 * Returns this item's field annotated {@code @UsedServices} for the
	 * given interface.
	 *
	 * @param servicesInterface the services interface
	 * @return the matching {@code @UsedServices} field, or {@code null} if
	 *         none exists
	 */
	@Override
	public Field getUsedServicesField(final Class<?> servicesInterface) {
		return this.metamodel.getUsedServicesField(servicesInterface);
	}

	/**
	 * Returns every {@code @UsedServices} field on this item.
	 *
	 * @return every {@code @UsedServices} field
	 */
	@Override
	public List<Field> getUsedServicesFields() {
		return this.metamodel.getUsedServicesFields();
	}

	/**
	 * Returns this item's field annotated {@code @ProducedEvents} for the
	 * given interface.
	 *
	 * @param eventsInterface the events interface
	 * @return the matching {@code @ProducedEvents} field, or {@code null}
	 *         if none exists
	 */
	@Override
	public Field getProducedEventsField(final Class<?> eventsInterface) {
		return this.metamodel.getProducedEventsField(eventsInterface);
	}

	/**
	 * Returns every {@code @ProducedEvents} field on this item.
	 *
	 * @return every {@code @ProducedEvents} field
	 */
	@Override
	public List<Field> getProducedEventsFields() {
		return this.metamodel.getProducedEventsFields();
	}

	/**
	 * Returns this item's field annotated {@code @AccessedParameters} for
	 * the given interface.
	 *
	 * @param parametersInterface the parameters interface
	 * @return the matching {@code @AccessedParameters} field, or
	 *         {@code null} if none exists
	 */
	@Override
	public Field getAccessedParametersField(final Class<?> parametersInterface) {
		return this.metamodel.getAccessedParametersField(parametersInterface);
	}

	/**
	 * Returns every {@code @AccessedParameters} field on this item.
	 *
	 * @return every {@code @AccessedParameters} field
	 */
	@Override
	public List<Field> getAccessedParametersFields() {
		return this.metamodel.getAccessedParametersFields();
	}

	/**
	 * Checks whether this item implements the given events subscribe
	 * interface as a subscribe delegate.
	 *
	 * @param subscriber the events subscribe interface to check
	 * @return {@code true} if this item implements it as a subscribe
	 *         delegate
	 */
	@Override
	public boolean implementsSubscriber(final Class<?> subscriber) {
		boolean result = false;
		if (this.getMetamodel().getImplementedSubscribers().contains(subscriber)) {
			result = true;
		}
		return result;
	}

	/**
	 * Checks whether this item consumes the given events interface.
	 *
	 * @param eventsInterface the events interface to check
	 * @return {@code true} if this item consumes it
	 */
	@Override
	public boolean implementsConsumer(final Class<?> eventsInterface) {
		boolean result = false;
		if (this.getMetamodel().getConsumedEvents().contains(eventsInterface)) {
			result = true;
		}
		return result;
	}

	/**
	 * Checks whether this item provides the given services interface.
	 *
	 * @param servicesInterface the services interface to check
	 * @return {@code true} if this item provides it
	 */
	@Override
	public boolean implementsServices(final Class<?> servicesInterface) {
		boolean result = false;
		if (this.getMetamodel().getProvidedServices().contains(servicesInterface)) {
			result = true;
		}
		return result;
	}

	/**
	 * Checks whether this item offers the given parameters interface.
	 *
	 * @param parametersInterface the parameters interface to check
	 * @return {@code true} if this item offers it
	 */
	@Override
	public boolean implementsParameters(final Class<?> parametersInterface) {
		boolean result = false;
		if (this.getMetamodel().getOfferedParameters().contains(parametersInterface)) {
			result = true;
		}
		return result;
	}

	/**
	 * Checks whether this item uses the given services interface.
	 *
	 * @param servicesInterface the services interface to check
	 * @return {@code true} if this item uses it
	 */
	@Override
	public boolean usesServices(final Class<?> servicesInterface) {
		boolean result = false;
		if (this.getMetamodel().getUsedServices().contains(servicesInterface)) {
			result = true;
		}
		return result;
	}

	/**
	 * Checks whether this item accesses the given parameters interface.
	 *
	 * @param parametersInterface the parameters interface to check
	 * @return {@code true} if this item accesses it
	 */
	@Override
	public boolean accessesParameters(final Class<?> parametersInterface) {
		boolean result = false;
		if (this.getMetamodel().getAccessedParameters().contains(parametersInterface)) {
			result = true;
		}
		return result;
	}

}
