/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.component;

import java.lang.reflect.Field;

import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.ComponentItem;
import org.opales.covalent.container.component.annotations.ComposedComponent;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsFilter;
import org.opales.covalent.container.component.annotations.EventsSubscribe;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.Parameters;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.component.annotations.AccessedParameters;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.component.internals.Internal;

/**
 * Utility methods testing whether a class or field carries one of the
 * component-model annotations (see the
 * {@link org.opales.covalent.container.component.annotations} package),
 * used internally by the container when scanning component types and their
 * members. Each {@code isXxx} method checks the class- or interface-level
 * annotation, and each {@code isXxxField} method checks the corresponding
 * field-level annotation(s).
 */
public final class ComponentAnnotations {

	/** Not meant to be instantiated; every member is static. */
	private ComponentAnnotations() {
	}

	/**
	 * Checks whether the given class is annotated with {@link Component}.
	 *
	 * @param aClass the class to check
	 * @return {@code true} if annotated with {@link Component}
	 */
	public static boolean isComponent(final Class<?> aClass) {
		return aClass.isAnnotationPresent(Component.class);
	}

	/**
	 * Checks whether the given class is annotated with {@link Implementation}.
	 *
	 * @param aClass the class to check
	 * @return {@code true} if annotated with {@link Implementation}
	 */
	public static boolean isImplementation(final Class<?> aClass) {
		return aClass.isAnnotationPresent(Implementation.class);
	}

	/**
	 * Checks whether the given field is annotated with {@link ComposedComponent}
	 * or {@link Implementation}.
	 *
	 * @param field the field to check
	 * @return {@code true} if annotated with {@link ComposedComponent} or
	 *         {@link Implementation}
	 */
	public static boolean isImplementationField(final Field field) {
		return field.isAnnotationPresent(ComposedComponent.class) || field.isAnnotationPresent(Implementation.class);
	}

	/**
	 * Checks whether the given class is annotated with {@link Item}. Item
	 * classes are never annotated with {@link ComponentItem}: that
	 * annotation only ever targets the aggregating field, see
	 * {@link #isItemField}.
	 *
	 * @param aClass the class to check
	 * @return {@code true} if annotated with {@link Item}
	 */
	public static boolean isItem(final Class<?> aClass) {
		return aClass.isAnnotationPresent(Item.class);
	}

	/**
	 * Checks whether the given field is annotated with {@link Item} or
	 * {@link ComponentItem}.
	 *
	 * @param field the field to check
	 * @return {@code true} if annotated with {@link Item} or
	 *         {@link ComponentItem}
	 */
	public static boolean isItemField(final Field field) {
		return field.isAnnotationPresent(Item.class) || field.isAnnotationPresent(ComponentItem.class);
	}

	/**
	 * Checks whether the given class is annotated with {@link Services}.
	 *
	 * @param aClass the class to check
	 * @return {@code true} if annotated with {@link Services}
	 */
	public static boolean isServices(final Class<?> aClass) {
		return aClass.isAnnotationPresent(Services.class);
	}

	/**
	 * Checks whether the given field is annotated with {@link UsedServices}
	 * or {@link Services}.
	 *
	 * @param field the field to check
	 * @return {@code true} if annotated with {@link UsedServices} or
	 *         {@link Services}
	 */
	public static boolean isServicesField(final Field field) {
		return field.isAnnotationPresent(UsedServices.class) || field.isAnnotationPresent(Services.class);
	}

	/**
	 * Checks whether the given class is annotated with {@link Parameters}.
	 *
	 * @param aClass the class to check
	 * @return {@code true} if annotated with {@link Parameters}
	 */
	public static boolean isParameters(final Class<?> aClass) {
		return aClass.isAnnotationPresent(Parameters.class);
	}

	/**
	 * Checks whether the given field is annotated with
	 * {@link AccessedParameters} or {@link Parameters}.
	 *
	 * @param field the field to check
	 * @return {@code true} if annotated with {@link AccessedParameters} or
	 *         {@link Parameters}
	 */
	public static boolean isParametersField(final Field field) {
		return field.isAnnotationPresent(AccessedParameters.class) || field.isAnnotationPresent(Parameters.class);
	}

	/**
	 * Checks whether the given class is annotated with {@link Events}.
	 *
	 * @param aClass the class to check
	 * @return {@code true} if annotated with {@link Events}
	 */
	public static boolean isEvents(final Class<?> aClass) {
		return aClass.isAnnotationPresent(Events.class);
	}

	/**
	 * Checks whether the given field is annotated with {@link ProducedEvents}
	 * or {@link Events}.
	 *
	 * @param field the field to check
	 * @return {@code true} if annotated with {@link ProducedEvents} or
	 *         {@link Events}
	 */
	public static boolean isEventsField(final Field field) {
		return field.isAnnotationPresent(ProducedEvents.class) || field.isAnnotationPresent(Events.class);
	}

	/**
	 * Checks whether the given class is annotated with {@link EventsFilter}.
	 *
	 * @param aClass the class to check
	 * @return {@code true} if annotated with {@link EventsFilter}
	 */
	public static boolean isEventsFilter(final Class<?> aClass) {
		return aClass.isAnnotationPresent(EventsFilter.class);
	}

	/**
	 * Checks whether the given class is annotated with {@link EventsSubscribe}.
	 *
	 * @param aClass the class to check
	 * @return {@code true} if annotated with {@link EventsSubscribe}
	 */
	public static boolean isSubscribe(final Class<?> aClass) {
		return aClass.isAnnotationPresent(EventsSubscribe.class);
	}

	/**
	 * Checks whether the given field is annotated with
	 * {@link UsedEventsSubscribe}.
	 *
	 * @param field the field to check
	 * @return {@code true} if annotated with {@link UsedEventsSubscribe}
	 */
	public static boolean isSubscribeField(final Field field) {
		return field.isAnnotationPresent(UsedEventsSubscribe.class);
	}

	/**
	 * Checks whether the given component type is annotated with
	 * {@link Internal}.
	 *
	 * @param componentType the component type to check
	 * @return {@code true} if annotated with {@link Internal}
	 */
	public static boolean isInternalComponent(final Class<?> componentType) {
		return componentType.isAnnotationPresent(Internal.class);
	}

}
