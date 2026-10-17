/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.workers;

import java.util.List;
import java.util.Map;

import org.opales.covalent.container.util.RuntimeCheck;

/**
 * One worker pool, at the moment it was read. The counters and histograms
 * accumulate from the pool's creation: the difference between two reads
 * gives what happened in between.
 *
 * @param name              the pool's name: {@code events-async},
 *                          {@code general}, or the queue key of a dedicated
 *                          pool
 * @param kind              the pool's kind
 * @param targetWorkerCount how many workers the pool aims at
 * @param queuedTasks       the tasks waiting in its queue
 * @param queueCapacity     its queue's capacity
 * @param rejectedTasks     the tasks refused because its queue was full,
 *                          counted whether task tracking is on or not
 * @param cancelledTasks    the queued tasks cancelled before they ran because
 *                          a newer task of the same message key replaced them
 *                          (the asynchronous pools only; always 0 for the
 *                          events pool), counted whether task tracking is on
 *                          or not
 * @param taskTracking      whether its tasks are tracked: without it, the
 *                          workers' tasks, the completed tasks, the
 *                          histograms and the components' busy time are
 *                          not updated
 * @param completedTasks    the tracked tasks completed
 * @param queueWaits        how long the tracked tasks waited in the queue
 * @param durations         how long the tracked tasks ran
 * @param componentBusyNanos the time the tracked tasks spent working for
 *                           each component - the one that submitted an async
 *                           task, the consumer of an event - the running
 *                           tasks included; the tasks of no known component
 *                           are left out
 * @param workers           its worker threads, by thread id
 */
public record WorkerPoolState(String name, WorkerPoolKind kind, int targetWorkerCount, int queuedTasks,
		int queueCapacity, long rejectedTasks, long cancelledTasks, boolean taskTracking, long completedTasks,
		DurationHistogram queueWaits, DurationHistogram durations, Map<Class<?>, Long> componentBusyNanos,
		List<WorkerState> workers) {

	/**
	 * Creates a pool state that has no cancelled task.
	 *
	 * @param name               the pool's name
	 * @param kind               the pool's kind
	 * @param targetWorkerCount  how many workers the pool aims at
	 * @param queuedTasks        the tasks waiting in its queue
	 * @param queueCapacity      its queue's capacity
	 * @param rejectedTasks      the tasks refused because its queue was full
	 * @param taskTracking       whether its tasks are tracked
	 * @param completedTasks     the tracked tasks completed
	 * @param queueWaits         how long the tracked tasks waited in the queue
	 * @param durations          how long the tracked tasks ran
	 * @param componentBusyNanos the time the tracked tasks spent working for each component
	 * @param workers            its worker threads
	 */
	public WorkerPoolState(final String name, final WorkerPoolKind kind, final int targetWorkerCount,
			final int queuedTasks, final int queueCapacity, final long rejectedTasks, final boolean taskTracking,
			final long completedTasks, final DurationHistogram queueWaits, final DurationHistogram durations,
			final Map<Class<?>, Long> componentBusyNanos, final List<WorkerState> workers) {
		this(name, kind, targetWorkerCount, queuedTasks, queueCapacity, rejectedTasks, 0L, taskTracking,
				completedTasks, queueWaits, durations, componentBusyNanos, workers);
	}

	/**
	 * Creates a pool state holding copies of the per-component busy times and of the workers.
	 *
	 * @param componentBusyNanos the time spent working for each component, copied
	 * @param workers            its worker threads, copied
	 */
	public WorkerPoolState {
		RuntimeCheck.notNull(name, "name");
		RuntimeCheck.notNull(kind, "kind");
		RuntimeCheck.notNull(queueWaits, "queueWaits");
		RuntimeCheck.notNull(durations, "durations");
		RuntimeCheck.notNull(componentBusyNanos, "componentBusyNanos");
		RuntimeCheck.notNull(workers, "workers");
		componentBusyNanos = Map.copyOf(componentBusyNanos);
		workers = List.copyOf(workers);
	}
}
