/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.lifecycle;

import org.opales.covalent.container.component.annotations.Services;

/** Internal lifecycle entry point for externally supplied component instances. */
@Services
public interface RawComponentLifecycleServices {

	/**
	 * Registers an externally supplied instance as the given component type's
	 * instance, without running the normal instantiation logic.
	 *
	 * @param componentType     component type the instance is registered under
	 * @param componentInstance the externally supplied instance
	 */
	void pseudoInstantiateComponent(final Class<?> componentType, final Object componentInstance);
}
