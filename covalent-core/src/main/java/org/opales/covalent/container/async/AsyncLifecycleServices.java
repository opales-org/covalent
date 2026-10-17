/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.async;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Applies lifecycle transitions to the asynchronous work a component owns.
 * Consumed internally by the component lifecycle machinery, in step with the
 * component's own lifecycle transitions, like
 * {@link org.opales.covalent.container.rr.services.ServicesLifecycleServices}.
 */
@Services
public interface AsyncLifecycleServices {

	/**
	 * Cancels every periodic task the given component type scheduled through
	 * {@link AsyncServices}, so that no run of them starts once this call
	 * returns. A run already in progress is not interrupted. A no-op if the
	 * component scheduled none.
	 *
	 * @param componentType the component type whose periodic tasks stop
	 */
	void cancelPeriodicTasks(Class<?> componentType);
}
