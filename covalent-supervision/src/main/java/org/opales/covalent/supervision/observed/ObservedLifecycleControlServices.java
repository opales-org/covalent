/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.observed;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Drives the lifecycle of the observed container's components, one component type at a time. A
 * composite carries its whole tree along, in the order the core gives. Each call runs
 * synchronously on the caller's thread and throws what the observed container throws, or an
 * {@link IllegalStateException} when nothing is observed.
 */
@Services
public interface ObservedLifecycleControlServices {

	/**
	 * Instantiates a component type with its dependencies.
	 *
	 * @param componentType a deployed component type, instantiated with its dependencies
	 */
	void instantiateComponent(Class<?> componentType);

	/**
	 * Initializes a component type, instantiating it first when needed.
	 *
	 * @param componentType a deployed component type, instantiated first when needed
	 */
	void initializeComponent(Class<?> componentType);

	/**
	 * Activates a component type, instantiating and initializing it first when needed.
	 *
	 * @param componentType a deployed component type, instantiated and initialized first when needed
	 */
	void activateComponent(Class<?> componentType);

	/**
	 * Passivates an activated component type.
	 *
	 * @param componentType an activated component type
	 */
	void passivateComponent(Class<?> componentType);

	/**
	 * Takes a component type out of the managed lifecycle.
	 *
	 * @param componentType a component type taken out of the managed lifecycle
	 */
	void removeComponent(Class<?> componentType);

	/**
	 * Returns a failed component type and its whole tree to the deployed state.
	 *
	 * @param componentType a failed component type, its whole tree returned to deployed
	 */
	void resetFailedComponent(Class<?> componentType);

	/**
	 * Undeploys the implementation of a component type.
	 *
	 * @param componentType a component type whose implementation is undeployed
	 */
	void undeployComponent(Class<?> componentType);

}
