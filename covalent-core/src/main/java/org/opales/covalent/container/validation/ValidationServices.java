/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.validation;

import java.lang.reflect.Field;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Validates component model classes, interfaces, fields, and runtime checks.
 */
@Services
public interface ValidationServices {
	/**
	 * Checks that a class is a valid component implementation.
	 *
	 * @param implementation implementation class to check
	 */
	void checkImplementation(final Class<?> implementation);

	/**
	 * Checks that a component type is a tag interface: it declares no method,
	 * and serves only to declare and identify the type of the component.
	 * What a component offers is declared in a {@code Services} or
	 * {@code Parameters} interface that its implementation also implements.
	 * The {@code CovalentContainer} type is exempt: it has a single possible
	 * implementation, so its type is part of that implementation.
	 *
	 * @param componentType component type interface to check
	 */
	void checkComponentType(final Class<?> componentType);

	/**
	 * Checks that a class is a valid component item.
	 *
	 * @param itemClass item class to check
	 */
	void checkItem(final Class<?> itemClass);

	/**
	 * Checks that a class is a valid composed component.
	 *
	 * @param composedComponentClass composed component class to check
	 */
	void checkComposedComponent(final Class<?> composedComponentClass);

	// PARAMETERS

	/**
	 * Reports whether a class is a parameters interface.
	 *
	 * @param assumedParameters class to inspect
	 * @return {@code true} when the class is a parameters interface
	 */
	boolean isParameters(Class<?> assumedParameters);

	/**
	 * Checks that a class is a valid parameters interface.
	 *
	 * @param assumedParameters class to check
	 */
	void checkParameters(Class<?> assumedParameters);

	/**
	 * Checks an accessed parameters field declaration.
	 *
	 * @param accessedParametersField accessed parameters field to check
	 * @param parametersInterface     parameters interface declared on the field
	 * @param componentImplOrItem     component implementation or item declaring
	 *                                the field
	 */
	void checkAccessedParametersField(Field accessedParametersField, final Class<?> parametersInterface,
			final Class<?> componentImplOrItem);

	// SERVICES

	/**
	 * Reports whether a class is a services interface.
	 *
	 * @param assumedServices class to inspect
	 * @return {@code true} when the class is a services interface
	 */
	boolean isServices(final Class<?> assumedServices);

	/**
	 * Checks that a class is a valid services interface.
	 *
	 * @param assumedServices class to check
	 */
	void checkServices(final Class<?> assumedServices);

	/**
	 * Checks a used services field declaration.
	 *
	 * @param usedServicesField   used services field to check
	 * @param servicesInterface   services interface declared on the field
	 * @param componentImplOrItem component implementation or item declaring the
	 *                            field
	 */
	void checkUsedServicesField(final Field usedServicesField, final Class<?> servicesInterface,
			final Class<?> componentImplOrItem);

	// EVENTS

	/**
	 * Reports whether a class is an events interface.
	 *
	 * @param assumedEvents class to inspect
	 * @return {@code true} when the class is an events interface
	 */
	boolean isEvents(final Class<?> assumedEvents);

	/**
	 * Checks that a class is a valid events interface.
	 *
	 * @param assumedEvents class to check
	 */
	void checkEvents(final Class<?> assumedEvents);

	/**
	 * Checks a produced events field declaration.
	 *
	 * @param producedEventsField produced events field to check
	 * @param eventsInterface     events interface declared on the field
	 * @param componentImplOrItem component implementation or item declaring the
	 *                            field
	 */
	void checkProducedEventsField(final Field producedEventsField, final Class<?> eventsInterface,
			final Class<?> componentImplOrItem);

	/**
	 * Checks event method naming conventions on an events interface.
	 *
	 * @param eventsInterface events interface to check
	 */
	void checkEventMethodNamingConvention(final Class<?> eventsInterface);

	// EVENTS SUBSCRIBE

	/**
	 * Reports whether a class is an events subscribe interface.
	 *
	 * @param assumedSubscribe class to inspect
	 * @return {@code true} when the class is an events subscribe interface
	 */
	boolean isSubscribe(final Class<?> assumedSubscribe);

	/**
	 * Checks that a class is a valid events subscribe interface.
	 *
	 * @param assumedSubscribe class to check
	 */
	void checkSubscribe(final Class<?> assumedSubscribe);

	/**
	 * Checks a used subscribe field declaration.
	 *
	 * @param usedSubscribeField  used subscribe field to check
	 * @param subscribeInterface  subscribe interface declared on the field
	 * @param componentImplOrItem component implementation or item declaring the
	 *                            field
	 */
	void checkUsedSubscribeField(final Field usedSubscribeField, Class<?> subscribeInterface,
			Class<?> componentImplOrItem);

	// GENERIC METHODS

	/**
	 * Handles a structural validation message according to the current verification
	 * level.
	 *
	 * @param message validation message to handle
	 */
	void handleStructuralVerification(final String message);

	/**
	 * Handles a dynamic validation message according to the current verification
	 * level.
	 *
	 * @param message validation message to handle
	 */
	void handleDynamicVerification(final String message);

}
