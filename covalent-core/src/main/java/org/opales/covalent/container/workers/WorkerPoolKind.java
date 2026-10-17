/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.workers;

/** The kinds of worker pool the container runs. */
public enum WorkerPoolKind {

	/** The pool delivering asynchronous events, when events are not synchronous. */
	EVENTS,

	/** The async manager's shared pool, for tasks submitted without a queue key. */
	GENERAL,

	/** An async manager pool dedicated to one queue key, with a single worker. */
	DEDICATED
}
