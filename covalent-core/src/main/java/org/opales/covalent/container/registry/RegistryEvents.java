/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.registry;

import org.opales.covalent.container.component.annotations.Events;

/**
 * Reports component registry changes.
 */
@Events
public interface RegistryEvents {

	/**
	 * Notifies that an instance has been registered under a component type.
	 *
	 * @param <CT>              component type marker
	 * @param componentType     registered component type
	 * @param componentInstance registered instance
	 */
	<CT> void onComponentRegistered(final Class<CT> componentType, final Object componentInstance);

	/**
	 * Notifies that an instance has been unregistered from a component type.
	 *
	 * @param <CT>              component type marker
	 * @param componentType     unregistered component type
	 * @param componentInstance unregistered instance
	 */
	<CT> void onComponentUnregistered(final Class<CT> componentType, final Object componentInstance);

}
