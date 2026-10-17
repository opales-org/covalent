/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.events;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Applies lifecycle transitions to produced event ports.
 */
@Services
public interface EventsLifecycleServices {

	// PRODUCED EVENTS

	/**
	 * Activates produced events declared by a component type.
	 *
	 * @param <EVENTS>      events interface type
	 * @param componentType producer component type
	 */
	<EVENTS> void activateProducedEvents(Class<?> componentType);

	/**
	 * Passivates produced events declared by a component type.
	 *
	 * @param <EVENTS>      events interface type
	 * @param componentType producer component type
	 */
	<EVENTS> void passivateProducedEvents(Class<?> componentType);

	/**
	 * Publishes subscribe ports for produced events declared by a component type.
	 *
	 * @param <EVENTS>      events interface type
	 * @param <SUBSCRIBE>   subscribe interface type
	 * @param componentType producer component type
	 */
	<EVENTS, SUBSCRIBE> void publishProvidedSubscribers(final Class<?> componentType);

	/**
	 * Unpublishes subscribe ports for produced events declared by a component type.
	 *
	 * @param <EVENTS>      events interface type
	 * @param componentType producer component type
	 */
	<EVENTS> void unpublishProvidedSubscribers(final Class<?> componentType);

	// USED SUBSCRIBERS

	// CONSUMED EVENTS

}
