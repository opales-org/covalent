/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.workers.impl;

/** A task that can tell what it is, read only when a pool's workers are read. */
public interface LabeledTask {

	/**
	 * Describes the task for display.
	 *
	 * @return what the task is, {@code null} when it cannot tell
	 */
	String taskLabel();

	/**
	 * Tells which component the task works for.
	 *
	 * @return the component the task works for - the one that submitted an async task, the
	 *         consumer of an event - {@code null} when unknown
	 */
	default Class<?> taskComponent() {
		return null;
	}
}
