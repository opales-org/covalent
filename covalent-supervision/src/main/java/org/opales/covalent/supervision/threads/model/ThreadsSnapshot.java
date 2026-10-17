/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.model;

import java.util.List;
import java.util.Map;

import org.opales.covalent.container.workers.DurationHistogram;
import org.opales.covalent.container.workers.WorkerPoolKind;

/**
 * The observed worker pools at one sample.
 *
 * @param timeMillis       when it was taken
 * @param connected        whether a container is observed
 * @param taskTracking     whether the observed pools' tasks are tracked
 * @param pools            the pools: events first, then general, then dedicated
 * @param minuteOccupancy  the share of every worker busy over the last minute, from 0 to 1
 * @param deadlocks        the deadlocks involving a pool thread
 * @param log              what happened lately, newest first
 * @param callers          the other threads running the observed components' calls
 * @param components       per thread, the application's components its tasks or calls worked
 *                         for since the previous sample, the latest last: every one a calling
 *                         thread's calls entered, the sampled task's one for a pool's worker
 */
public record ThreadsSnapshot(long timeMillis, boolean connected, boolean taskTracking, List<Pool> pools,
		double minuteOccupancy, List<Deadlock> deadlocks, List<LogEntry> log, Callers callers,
		Map<Long, List<Class<?>>> components) {

	/** Nothing observed. */
	public static final ThreadsSnapshot EMPTY = new ThreadsSnapshot(0, false, false, List.of(), 0, List.of(), List.of(),
			Callers.NONE, Map.of());

	/**
	 * The threads outside the pools that ran calls of the observed components - the
	 * application's own threads, the JavaFX application thread - known only while the
	 * observed container is instrumented (data flows on). For each of them, a task is a call:
	 * its label the innermost call, its component that of the outermost one.
	 *
	 * @param dataFlows      whether the observed container is instrumented
	 * @param callsPerSecond the calls they ran per second over the last interval
	 * @param threads        those threads
	 */
	public record Callers(boolean dataFlows, double callsPerSecond, List<Worker> threads) {

		/** None known. */
		public static final Callers NONE = new Callers(false, 0, List.of());
	}

	/**
	 * A worker pool of the observed container as one sample sees it, with its figures and its worker threads.
	 *
	 * @param name            the pool's name
	 * @param kind            its kind
	 * @param targetWorkers   how many workers it aims at
	 * @param queuedTasks     the tasks in its queue
	 * @param queueCapacity   its queue's capacity
	 * @param rejectedTasks   the tasks refused since it exists
	 * @param completedTasks  the tracked tasks completed since it exists
	 * @param queueWaits      how long the tracked tasks waited, since it exists
	 * @param durations       how long the tracked tasks ran, since it exists
	 * @param occupancy       the share of its workers' time spent in tasks over the last interval
	 *                        while tasks are tracked, otherwise the share of its workers busy now,
	 *                        from 0 to 1
	 * @param contention      how hard it is pressed: saturated when it rejected tasks within the last
	 *                        minute, otherwise as its last seconds say
	 * @param tasksPerSecond  the tracked tasks completed per second over the last interval
	 * @param workers         its worker threads
	 * @param tuning          how it can be tuned live, {@code null} when it cannot: a dedicated pool
	 *                        keeps its single worker
	 * @param rejectedLastMinute the tasks it rejected within the last minute
	 * @param calmerNow       whether its last seconds are calmer than its verdict: saturated by
	 *                        rejections within the minute, none lately
	 * @param componentLoad   while tasks are tracked, how many of its workers worked for each
	 *                        component on average over the last interval, Covalent's own left out
	 * @param otherLoad       likewise, for the tasks of no component known
	 */
	public record Pool(String name, WorkerPoolKind kind, int targetWorkers, int queuedTasks, int queueCapacity,
			long rejectedTasks, long completedTasks, DurationHistogram queueWaits, DurationHistogram durations,
			double occupancy, Contention contention, double tasksPerSecond, List<Worker> workers, Tuning tuning,
			long rejectedLastMinute, boolean calmerNow, Map<Class<?>, Double> componentLoad, double otherLoad) {

		/**
		 * Creates a pool, copying its component load.
		 *
		 * @param componentLoad the load of each component, copied
		 */
		public Pool {
			componentLoad = Map.copyOf(componentLoad);
		}

		/**
		 * Returns how the pool reads: the manager that owns it and its name.
		 *
		 * @return how the pool reads, such as {@code EventsManager · events-async}
		 */
		public String title() {
			return switch (this.kind) {
				case EVENTS -> "EventsManager · events-async";
				case GENERAL -> "AsyncManager · general";
				case DEDICATED -> "AsyncManager · " + this.name;
			};
		}
	}

	/**
	 * A pool tuned live - its workers and queue capacity - until the observed container restarts,
	 * its configuration then applying again.
	 *
	 * @param workersAtConnection       its worker count when the supervisor connected
	 * @param queueCapacityAtConnection its queue capacity when the supervisor connected
	 */
	public record Tuning(int workersAtConnection, int queueCapacityAtConnection) {

		/** The fewest workers a pool is tuned to. */
		public static final int MIN_WORKERS = 1;

		/** The most workers a pool is tuned to. */
		public static final int MAX_WORKERS = 64;

		/** The smallest queue capacity a pool is tuned to. */
		public static final int MIN_QUEUE_CAPACITY = 16;

		/** The largest queue capacity a pool is tuned to. */
		public static final int MAX_QUEUE_CAPACITY = 65_536;

		/**
		 * Tells whether a pool can be tuned live: a dedicated one keeps its single worker.
		 *
		 * @param kind the pool's kind
		 * @return whether the pool can be tuned
		 */
		public static boolean tunable(final WorkerPoolKind kind) {
			return kind == WorkerPoolKind.EVENTS || kind == WorkerPoolKind.GENERAL;
		}
	}

	/**
	 * A worker thread of a pool, as one sample sees it.
	 *
	 * @param threadId      the thread's id
	 * @param name          its name
	 * @param activity      what it does
	 * @param jvmState      its state for the JVM
	 * @param taskNanos     how long its current task has run, {@code -1} when unknown
	 * @param taskLabel     what its current task is, {@code null} when unknown
	 * @param lockName      the monitor it is blocked on, or the object it waits on, {@code null} if none
	 * @param lockOwnerName the thread holding that lock, {@code null} if none or unknown
	 * @param cpuNanos      the CPU time it used since it started, {@code -1} when not measured
	 * @param taskComponent the component its current task works for - the one that submitted
	 *                      an async task, the consumer of an event - {@code null} when unknown
	 * @param busyShare     the share of the last interval it spent in tasks, from 0 to 1, short
	 *                      tasks included; {@code -1} when not known (task tracking off)
	 */
	public record Worker(long threadId, String name, ThreadActivity activity, Thread.State jvmState, long taskNanos,
			String taskLabel, String lockName, String lockOwnerName, long cpuNanos, Class<?> taskComponent,
			double busyShare) {

		/**
		 * Returns how busy the worker was.
		 *
		 * @return how busy it was: its share of the last interval in tasks when known, otherwise
		 *         whether it holds its worker now
		 */
		public double occupancy() {
			return this.busyShare >= 0 ? Math.max(this.busyShare, this.activity == ThreadActivity.BLOCKED ? 1 : 0)
					: this.activity.isBusy() ? 1 : 0;
		}
	}

	/**
	 * A set of threads each waiting for a lock another one holds, so that none can go on.
	 *
	 * @param threadNames the threads waiting on each other
	 * @param waits       how they wait on each other: per thread, the lock it waits for and the
	 *                    thread holding it
	 * @param threadIds   their ids
	 * @param report      a report to copy: for each thread its state, the lock it waits for, the
	 *                    monitors and synchronizers it holds, and its stack - in English
	 */
	public record Deadlock(List<String> threadNames, List<Phrase> waits, List<Long> threadIds, String report) {
	}

	/** How much a log entry matters. */
	public enum Severity {
		/** For information. */
		INFO,
		/** Worth a look. */
		WARNING,
		/** Something went wrong. */
		ERROR
	}

	/**
	 * An entry of the log of what happened to the pools and their threads.
	 *
	 * @param timeMillis when it happened
	 * @param subject    the pool or thread concerned
	 * @param text       what happened
	 * @param severity   how much it matters
	 */
	public record LogEntry(long timeMillis, Phrase subject, Phrase text, Severity severity) {
	}
}
