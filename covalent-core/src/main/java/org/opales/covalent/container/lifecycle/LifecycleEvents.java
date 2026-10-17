/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.lifecycle;

import org.opales.covalent.container.component.annotations.Events;

/**
 * Reports component lifecycle transitions.
 */
@Events
public interface LifecycleEvents {

	/**
	 * Notifies that a component type has been instantiated.
	 *
	 * @param componentType instantiated component type
	 */
	void onComponentInstantiated(Class<?> componentType);

	/**
	 * Notifies that a component type has been initialized.
	 *
	 * @param componentType initialized component type
	 */
	void onComponentInitialized(Class<?> componentType);

	/**
	 * Notifies that a component type has been activated.
	 *
	 * @param componentType activated component type
	 */
	void onComponentActivated(Class<?> componentType);

	/**
	 * Notifies that a component type has been passivated.
	 *
	 * @param componentType passivated component type
	 */
	void onComponentPassivated(Class<?> componentType);

	/**
	 * Notifies that a component type has been removed.
	 *
	 * @param componentType removed component type
	 */
	void onComponentRemoved(Class<?> componentType);

	/**
	 * Notifies that a component has failed, after technical cleanup was attempted
	 * and the entire owning composite was marked failed. Cleanup may be incomplete.
	 *
	 * @param componentType failed component type
	 */
	void onComponentFailed(Class<?> componentType);

}
