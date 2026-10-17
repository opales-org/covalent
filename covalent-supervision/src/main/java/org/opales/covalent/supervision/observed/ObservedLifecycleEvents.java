/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.observed;

import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.lifecycle.ComponentState;

/**
 * Lifecycle transitions of the observed container's components - the
 * observed-container counterpart of
 * {@link org.opales.covalent.container.lifecycle.LifecycleEvents}, folded
 * into one notification carrying the state reached.
 */
@Events
public interface ObservedLifecycleEvents {

	/**
	 * Notifies that a component of the observed container reached a lifecycle state.
	 *
	 * @param componentType the component whose state changed
	 * @param state         the state it just reached
	 */
	void onComponentStateChanged(Class<?> componentType, ComponentState state);

}
