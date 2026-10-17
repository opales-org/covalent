/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.registry;

import org.opales.covalent.container.component.annotations.Services;

/** Coordinates technical component creation and removal. */
@Services
public interface ComponentInstantiationServices {

	/**
	 * Instantiates the given component type through its implementation
	 * class's default constructor.
	 *
	 * @param componentType the component type to instantiate
	 */
	void instantiateComponent(final Class<?> componentType);

	/**
	 * Registers the given already-existing instance as the given component
	 * type, without instantiating it - used for raw components, whose
	 * instance is supplied by application code rather than created by the
	 * container.
	 *
	 * @param componentType the component type to register the instance as
	 * @param instance      the existing instance
	 */
	void componentify(final Class<?> componentType, final Object instance);

	/**
	 * Removes the given component type's registration.
	 *
	 * @param componentType the component type to remove
	 */
	void removeComponent(final Class<?> componentType);
}
