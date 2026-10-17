/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.observed;

import org.opales.covalent.container.component.annotations.Events;

/**
 * Mirror of {@link org.opales.covalent.container.registry.RegistryEvents},
 * rooted in the <em>observed</em> container - see {@link ObservedRegistryReadServices}
 * for why a distinct type is needed instead of reusing the original.
 */
@Events
public interface ObservedRegistryEvents {

	/**
	 * Notifies that an instance has been registered under a component type.
	 *
	 * @param <CT> component type marker
	 * @param componentType registered component type
	 * @param componentInstance registered instance
	 */
	<CT> void onComponentRegistered(Class<CT> componentType, Object componentInstance);

	/**
	 * Notifies that an instance has been unregistered from a component type.
	 *
	 * @param <CT> component type marker
	 * @param componentType unregistered component type
	 * @param componentInstance unregistered instance
	 */
	<CT> void onComponentUnregistered(Class<CT> componentType, Object componentInstance);

}
