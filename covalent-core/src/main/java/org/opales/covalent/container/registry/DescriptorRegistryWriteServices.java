/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.registry;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Creates, updates, and deletes component port descriptors.
 */
@Services
public interface DescriptorRegistryWriteServices extends DescriptorRegistryReadServices {
	/**
	 * Creates a descriptor for a component type.
	 *
	 * @param componentType component type represented by the descriptor
	 * @return created component instance
	 */
	Object createComponentDescriptor(final Class<?> componentType);

	/**
	 * Creates a descriptor for a component type and its already known instance.
	 *
	 * @param componentType component type represented by the descriptor
	 * @param instance      component instance associated with the descriptor
	 * @return attached component instance
	 */
	Object createComponentDescriptor(final Class<?> componentType, final Object instance);

	/**
	 * Rebuilds the descriptor associated with a component type from current
	 * registry and metamodel state. This update is local to the requested
	 * component type: it does not recursively update descriptors of composed
	 * components.
	 *
	 * @param componentType component type whose descriptor is updated
	 */
	void updateComponentDescriptor(final Class<?> componentType);

	/**
	 * Deletes the descriptor associated with a component type.
	 *
	 * @param componentType component type whose descriptor is deleted
	 */
	void deleteComponentDescriptor(final Class<?> componentType);

}
