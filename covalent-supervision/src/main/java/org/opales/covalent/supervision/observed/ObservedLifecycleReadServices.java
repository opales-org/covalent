/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.observed;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.lifecycle.ComponentState;

/**
 * Reads the current lifecycle state of the observed container's components -
 * see {@link ObservedRegistryReadServices} for why a distinct type is needed
 * instead of reusing {@link org.opales.covalent.container.lifecycle.LifecycleServices}.
 */
@Services
public interface ObservedLifecycleReadServices {

	/**
	 * Returns the current lifecycle state of a component type.
	 *
	 * @param componentType a component type of the observed container
	 * @return its current state, or {@code null} when nothing is observed or
	 *         the type is unknown
	 */
	ComponentState readComponentState(Class<?> componentType);

}
