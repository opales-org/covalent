/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.interaction;

import org.opales.covalent.hmi.action.ActionKey;

/** The Threads function's actions. */
public enum ThreadsActionKey implements ActionKey {

	/** Opens or closes the Threads panel - no payload. */
	Toggle,

	/** Opens the Threads panel, or shows it when open - no payload. */
	Show,

	/** Turns the observed pools' task tracking on or off - payload {@code Boolean}. */
	SetTaskTracking,

	/** Reads a thread's Java stack - payload {@code Long}, the thread id. */
	ReadStack,

	/**
	 * The components the shared Record button would record, as the selection on the panel gives
	 * them - payload {@code Set<Class<?>>}, empty when there is nothing to record.
	 */
	SetRecordScope,

	/** The shared Record button, on the Threads panel - no payload. */
	RecordToggle,

	/** Interrupts threads - payload {@code Set<Long>}, their ids. */
	InterruptThreads,

	/** Tunes a pool's workers live - payload {@code WorkerPoolKind}, {@code Integer} the count. */
	SetPoolWorkers,

	/** Tunes a pool's queue capacity live - payload {@code WorkerPoolKind}, {@code Integer} the capacity. */
	SetPoolQueueCapacity
}
