/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.registry;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Reads component port descriptors.
 */
@Services
public interface DescriptorRegistryReadServices {
	/**
	 * Reads the descriptor associated with a component type.
	 *
	 * @param componentType component type to read
	 * @return component descriptor, or {@code null} when no descriptor exists
	 */
	ComponentDescriptor readComponentDescriptor(final Class<?> componentType);

	/**
	 * Reports whether a descriptor exists for a component type.
	 *
	 * @param componentType component type to inspect
	 * @return {@code true} when a descriptor exists
	 */
	boolean containsComponentDescriptor(final Class<?> componentType);

}
