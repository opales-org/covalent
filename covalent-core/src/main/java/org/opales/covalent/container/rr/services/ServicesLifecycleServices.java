/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.rr.services;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Applies lifecycle transitions to provided services ports. Consumed
 * internally by the component lifecycle machinery to activate, publish,
 * unpublish, and passivate every services interface a component type
 * provides, in step with the component's own lifecycle transitions.
 */
@Services
public interface ServicesLifecycleServices {

	/**
	 * Activates every services interface declared as provided by the given
	 * component type that is not already activated.
	 *
	 * @param <SERVICES>    services interface type
	 * @param componentType provider component type
	 */
	<SERVICES> void activateProvidedServices(Class<?> componentType);

	/**
	 * Publishes (binds in the directory) every services interface declared
	 * as provided by the given component type that is not already bound.
	 *
	 * @param <SERVICES>    services interface type
	 * @param componentType provider component type
	 */
	<SERVICES> void publishProvidedServices(Class<?> componentType);

	/**
	 * Unpublishes (unbinds from the directory) every services interface
	 * declared as provided by the given component type that is currently
	 * bound.
	 *
	 * @param <SERVICES>    services interface type
	 * @param componentType provider component type
	 */
	<SERVICES> void unpublishProvidedServices(Class<?> componentType);

	/**
	 * Passivates every services interface declared as provided by the given
	 * component type that is currently provided and activated.
	 *
	 * @param <SERVICES>    services interface type
	 * @param componentType provider component type
	 */
	<SERVICES> void passivateProvidedServices(Class<?> componentType);

}
