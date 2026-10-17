/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.model;

/** What a worker thread is doing at one moment. */
public enum ThreadActivity {

	/** In a task, running: it uses CPU, or waits on blocking I/O. */
	RUNS,

	/** In a task, waiting: sleep, a future, a condition, a {@code java.util.concurrent} lock. */
	WAITS,

	/** Blocked on a monitor ({@code synchronized}), in a task or not. */
	BLOCKED,

	/** Without a task. */
	IDLE,

	/** Waiting, while task tracking is off: idle or waiting in a task, which cannot be told apart. */
	UNTRACKED;

	/**
	 * Tells whether the thread holds its worker.
	 *
	 * @return whether it holds its worker
	 */
	public boolean isBusy() {
		return this == RUNS || this == WAITS || this == BLOCKED;
	}
}
