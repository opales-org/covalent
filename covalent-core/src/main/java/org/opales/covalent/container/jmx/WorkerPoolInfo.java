/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.jmx;

import java.beans.ConstructorProperties;

/**
 * The counters of one worker pool, as seen by a JMX client (an MXBean open
 * type: JConsole shows it as a table row). It is the part of a
 * {@code WorkerPoolState} that is useful to an operator: the histograms and
 * the per-worker details are left to the Supervisor.
 * <p>
 * Counters accumulate from the pool's creation: a monitoring tool takes the
 * difference between two reads. {@code rejectedTasks} and
 * {@code cancelledTasks} are counted whether task tracking is on or not;
 * {@code completedTasks} only counts while it is on, as {@code taskTracking}
 * says.
 */
public final class WorkerPoolInfo {

	private final String name;

	private final String kind;

	private final int targetWorkerCount;

	private final int queuedTasks;

	private final int queueCapacity;

	private final long rejectedTasks;

	private final long cancelledTasks;

	private final long completedTasks;

	private final boolean taskTracking;

	/**
	 * Creates the counters of a pool.
	 *
	 * @param name              the pool's name
	 * @param kind              the pool's kind: {@code EVENTS}, {@code GENERAL} or {@code DEDICATED}
	 * @param targetWorkerCount the number of worker threads the pool aims at
	 * @param queuedTasks       the tasks waiting in the queue now
	 * @param queueCapacity     the maximum number of waiting tasks
	 * @param rejectedTasks     the tasks refused because the queue was full
	 * @param cancelledTasks    the queued tasks replaced by a newer one of the same message key
	 * @param completedTasks    the tasks completed while task tracking was on
	 * @param taskTracking      whether task tracking is on
	 */
	@ConstructorProperties({ "name", "kind", "targetWorkerCount", "queuedTasks", "queueCapacity", "rejectedTasks",
			"cancelledTasks", "completedTasks", "taskTracking" })
	public WorkerPoolInfo(final String name, final String kind, final int targetWorkerCount, final int queuedTasks,
			final int queueCapacity, final long rejectedTasks, final long cancelledTasks, final long completedTasks,
			final boolean taskTracking) {
		this.name = name;
		this.kind = kind;
		this.targetWorkerCount = targetWorkerCount;
		this.queuedTasks = queuedTasks;
		this.queueCapacity = queueCapacity;
		this.rejectedTasks = rejectedTasks;
		this.cancelledTasks = cancelledTasks;
		this.completedTasks = completedTasks;
		this.taskTracking = taskTracking;
	}

	/**
	 * The pool's name.
	 *
	 * @return the pool's name
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * The pool's kind: {@code EVENTS}, {@code GENERAL} or {@code DEDICATED}.
	 *
	 * @return the pool's kind: {@code EVENTS}, {@code GENERAL} or {@code DEDICATED}
	 */
	public String getKind() {
		return this.kind;
	}

	/**
	 * The number of worker threads the pool aims at.
	 *
	 * @return the number of worker threads the pool aims at
	 */
	public int getTargetWorkerCount() {
		return this.targetWorkerCount;
	}

	/**
	 * The tasks waiting in the queue now.
	 *
	 * @return the tasks waiting in the queue now
	 */
	public int getQueuedTasks() {
		return this.queuedTasks;
	}

	/**
	 * The maximum number of waiting tasks.
	 *
	 * @return the maximum number of waiting tasks
	 */
	public int getQueueCapacity() {
		return this.queueCapacity;
	}

	/**
	 * The tasks refused because the queue was full, since the pool's creation.
	 *
	 * @return the tasks refused because the queue was full, since the pool's creation
	 */
	public long getRejectedTasks() {
		return this.rejectedTasks;
	}

	/**
	 * The queued tasks replaced by a newer one of the same message key, since the pool's creation.
	 *
	 * @return the queued tasks replaced by a newer one of the same message key, since the pool's creation
	 */
	public long getCancelledTasks() {
		return this.cancelledTasks;
	}

	/**
	 * The tasks completed while task tracking was on.
	 *
	 * @return the tasks completed while task tracking was on
	 */
	public long getCompletedTasks() {
		return this.completedTasks;
	}

	/**
	 * Whether task tracking is on.
	 *
	 * @return whether task tracking is on
	 */
	public boolean isTaskTracking() {
		return this.taskTracking;
	}

	@Override
	public String toString() {
		return "WorkerPoolInfo[" + this.name + ", " + this.kind + ", workers=" + this.targetWorkerCount + ", queued="
				+ this.queuedTasks + "/" + this.queueCapacity + ", rejected=" + this.rejectedTasks + ", cancelled="
				+ this.cancelledTasks + ", completed=" + this.completedTasks + ", tracking=" + this.taskTracking + "]";
	}
}
