/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.registry;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Updates component instances in the component registry.
 *
 * <p>Same concurrency contract as {@link RegistryReadServices}: not
 * independently synchronized, relies on the caller going through
 * {@link org.opales.covalent.container.lifecycle.LifecycleServices}'s
 * per-container lock.
 */
@Services
public interface RegistryWriteServices extends RegistryReadServices {
	/**
	 * Registers an instance under a component type.
	 *
	 * @param componentType     component type used as the registry key
	 * @param componentInstance component instance to register
	 * @throws IllegalArgumentException if {@code componentInstance} is {@code null}
	 * @throws IllegalStateException    if an instance is already registered under
	 *                                  {@code componentType}
	 */
	void registerComponent(final Class<?> componentType, final Object componentInstance);

	/**
	 * Unregisters and returns the instance associated with a component type.
	 *
	 * @param <T>           component type marker
	 * @param componentType component type to unregister
	 * @return unregistered component instance
	 * @throws IllegalStateException if no instance is registered under
	 *                               {@code componentType}
	 */
	<T> T unregisterComponent(final Class<T> componentType);

}
