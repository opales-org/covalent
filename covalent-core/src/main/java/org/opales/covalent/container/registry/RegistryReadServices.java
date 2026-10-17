/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.registry;

import java.util.Map;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Reads component instances from the component registry.
 *
 * <p>Operations here are not independently synchronized: safe concurrent
 * access relies entirely on the caller going through
 * {@link org.opales.covalent.container.lifecycle.LifecycleServices}, whose
 * per-container lock already serializes the lifecycle transitions
 * (instantiate/initialize/activate/passivate/remove) that read and write the
 * registry. A caller that reaches these methods without going through that
 * lock - for instance by holding a services proxy and calling it from a
 * thread outside any lifecycle operation, concurrently with one that is
 * mutating the registry - has no guarantee against concurrent modification.
 * This is unlike {@link org.opales.covalent.container.async.AsyncServices},
 * whose worker pools are independently thread-safe by design.
 */
@Services
public interface RegistryReadServices {
	/**
	 * Returns the instance associated with a component type.
	 *
	 * @param <CT>          component type marker
	 * @param <CI>          component instance type
	 * @param componentType component type to read
	 * @return registered component instance
	 * @throws IllegalStateException    if {@code componentType} is a component
	 *                                  implementation class, not a component type
	 * @throws IllegalArgumentException if no instance is registered under
	 *                                  {@code componentType}
	 */
	<CT, CI extends CT> CI getComponentInstance(final Class<CT> componentType);

	/**
	 * Returns all registered component instances.
	 *
	 * @return registered instances keyed by component type
	 */
	Map<Class<?>, Object> getInstances();

	/**
	 * Reports whether a component type has a registered instance.
	 *
	 * @param <T>           component type marker
	 * @param componentType component type to inspect
	 * @return {@code true} when an instance is registered
	 */
	<T> boolean isRegistered(final Class<T> componentType);

}
