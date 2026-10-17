/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.events.impl;

/**
 * A task of the events worker whose submitter waits for its outcome, and must
 * therefore be told when the worker drops it without running it (queue full,
 * worker stopped).
 */
interface DroppableTask {

	/** Called instead of {@code run()} when the events worker drops the task. */
	void dropped();
}
