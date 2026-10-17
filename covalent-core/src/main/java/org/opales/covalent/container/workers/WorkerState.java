/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.workers;

import org.opales.covalent.container.util.RuntimeCheck;

/**
 * One worker thread of a pool, at the moment it was read.
 *
 * @param threadId    the thread's id
 * @param threadName  the thread's name
 * @param inTask      whether it is running a task; always {@code false}
 *                    while the pool's task tracking is off, since it is then
 *                    not known
 * @param taskNanos   how long its current task has been running,
 *                    {@code -1} when it runs none or when it is not known
 * @param taskLabel   what its current task is, {@code null} when unknown
 * @param taskComponent the component its current task works for - the one
 *                    that submitted an async task, the consumer of an
 *                    event - {@code null} when unknown
 * @param busyNanos   the time it spent in tracked tasks since it started,
 *                    its current one included: the difference between two
 *                    reads gives how busy it was in between, however short
 *                    its tasks
 * @param lastTaskComponent the component the last task it finished worked
 *                    for, {@code null} when unknown or when it ran none: when
 *                    its busy time grew between two reads and it runs no task
 *                    now, that is what the tasks in between worked for
 */
public record WorkerState(long threadId, String threadName, boolean inTask, long taskNanos, String taskLabel,
		Class<?> taskComponent, long busyNanos, Class<?> lastTaskComponent) {

	/**
	 * Creates a worker's state, which needs a thread name.
	 */
	public WorkerState {
		RuntimeCheck.notNull(threadName, "threadName");
	}

	/**
	 * Creates a worker's state with no last task's component known.
	 *
	 * @param threadId      the thread's id
	 * @param threadName    the thread's name
	 * @param inTask        whether it is running a task
	 * @param taskNanos     how long its current task has been running, {@code -1} when none
	 * @param taskLabel     what its current task is, {@code null} when unknown
	 * @param taskComponent the component its current task works for, {@code null} when unknown
	 * @param busyNanos     the time it spent in tracked tasks since it started
	 */
	public WorkerState(final long threadId, final String threadName, final boolean inTask, final long taskNanos,
			final String taskLabel, final Class<?> taskComponent, final long busyNanos) {
		this(threadId, threadName, inTask, taskNanos, taskLabel, taskComponent, busyNanos, null);
	}
}
