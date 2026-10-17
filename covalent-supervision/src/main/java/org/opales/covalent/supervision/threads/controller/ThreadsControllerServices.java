/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.controller;

import java.util.Set;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.workers.WorkerPoolKind;

/** The Threads function's commands. */
@Services
public interface ThreadsControllerServices {

	/**
	 * Turns the observed pools' task tracking on or off.
	 *
	 * @param tracking whether to track the tasks
	 */
	void setTaskTracking(boolean tracking);

	/**
	 * Reads a thread's Java stack, published through the model.
	 *
	 * @param threadId the thread
	 */
	void readStack(long threadId);

	/**
	 * Interrupts threads - the deadlocked ones, say. It frees those waiting interruptibly, such as on
	 * {@code lockInterruptibly()} or a timed lock; one blocked on a monitor ({@code synchronized}) stays blocked.
	 *
	 * @param threadIds the threads' ids
	 */
	void interruptThreads(Set<Long> threadIds);

	/**
	 * The shared Record button, on the Threads panel: stops the recording in progress, whoever
	 * started it, or else starts a sequence recording of the given components.
	 *
	 * @param components the components to record, as the selection on the panel gives them
	 */
	void toggleRecording(Set<Class<?>> components);

	/**
	 * Tells whether a sequence recording runs.
	 *
	 * @return whether a sequence recording runs, whoever started it
	 */
	boolean isRecording();

	/**
	 * Tunes a pool's workers live, within {@code ThreadsSnapshot.Tuning}'s bounds, until the
	 * observed container restarts. Nothing happens for a dedicated pool.
	 *
	 * @param kind  the pool's kind
	 * @param count the worker count, brought within the bounds
	 */
	void setWorkerCount(WorkerPoolKind kind, int count);

	/**
	 * Tunes a pool's queue capacity live, within {@code ThreadsSnapshot.Tuning}'s bounds, until
	 * the observed container restarts; the general pool's applies to the dedicated pools too.
	 * Nothing happens for a dedicated pool.
	 *
	 * @param kind     the pool's kind
	 * @param capacity the queue capacity, brought within the bounds
	 */
	void setQueueCapacity(WorkerPoolKind kind, int capacity);
}
