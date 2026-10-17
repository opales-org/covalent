/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.autowiring;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Creates and deletes automatically wired ports declared by component
 * metamodels.
 */
@Services
public interface AutoWiringServices {

	/**
	 * Creates provided services declared by a component type.
	 *
	 * @param componentType component type whose ports are created
	 */
	void createProvidedServices(final Class<?> componentType);

	/**
	 * Deletes provided services declared by a component type.
	 *
	 * @param componentType component type whose ports are deleted
	 */
	void deleteProvidedServices(Class<?> componentType);

	/**
	 * Creates used services declared by a component type.
	 *
	 * @param <SERVICES>    services interface type
	 * @param componentType component type whose ports are created
	 */
	<SERVICES> void createUsedServices(final Class<?> componentType);

	/**
	 * Deletes used services declared by a component type.
	 *
	 * @param <SERVICES>    services interface type
	 * @param componentType component type whose ports are deleted
	 */
	<SERVICES> void deleteUsedServices(final Class<?> componentType);

	/**
	 * Creates used subscribe ports declared by a component type.
	 *
	 * @param <SUBSCRIBE>   subscribe interface type
	 * @param componentType component type whose ports are created
	 */
	<SUBSCRIBE> void createUsedSubscribers(Class<?> componentType);

	/**
	 * Deletes used subscribe ports declared by a component type.
	 *
	 * @param <SUBSCRIBE>   subscribe interface type
	 * @param componentType component type whose ports are deleted
	 */
	<SUBSCRIBE> void deleteUsedSubscribers(final Class<?> componentType);

	/**
	 * Creates produced events declared by a component type.
	 *
	 * @param <EVENTS>      events interface type
	 * @param componentType component type whose ports are created
	 */
	<EVENTS> void createProducedEvents(Class<?> componentType);

	/**
	 * Deletes produced events declared by a component type.
	 *
	 * @param <EVENTS>      events interface type
	 * @param componentType component type whose ports are deleted
	 */
	<EVENTS> void deleteProducedEvents(final Class<?> componentType);

	/**
	 * Creates consumed events declared by a component type.
	 *
	 * @param componentType component type whose ports are created
	 */
	void createConsumedEvents(final Class<?> componentType);

	/**
	 * Deletes consumed events declared by a component type.
	 *
	 * @param componentType component type whose ports are deleted
	 */
	void deleteConsumedEvents(final Class<?> componentType);

	/**
	 * Creates offered parameters declared by a component type.
	 *
	 * @param componentType component type whose ports are created
	 */
	void createOfferedParameters(final Class<?> componentType);

	/**
	 * Deletes offered parameters declared by a component type.
	 *
	 * @param componentType component type whose ports are deleted
	 */
	void deleteOfferedParameters(Class<?> componentType);

	/**
	 * Creates accessed parameters declared by a component type.
	 *
	 * @param <PARAMETERS>  parameters interface type
	 * @param componentType component type whose ports are created
	 */
	<PARAMETERS> void createAccessedParameters(final Class<?> componentType);

	/**
	 * Deletes accessed parameters declared by a component type.
	 *
	 * @param <PARAMETERS>  parameters interface type
	 * @param componentType component type whose ports are deleted
	 */
	<PARAMETERS> void deleteAccessedParameters(final Class<?> componentType);

}
