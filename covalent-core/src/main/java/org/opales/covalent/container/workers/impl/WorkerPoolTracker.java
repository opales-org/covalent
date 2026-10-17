/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.workers.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BooleanSupplier;

import org.opales.covalent.container.workers.DurationHistogram;
import org.opales.covalent.container.workers.WorkerState;

/**
 * The bookkeeping of one worker pool: its workers, what each one runs, and
 * how long its tasks wait and run. Each worker registers itself when it
 * starts and leaves when it ends. Task tracking can be turned off: a task
 * then costs one volatile read, and only the rejections are still counted.
 * Nothing here takes a lock on the tasks' path.
 */
public final class WorkerPoolTracker {

	/** An enqueue time not known: the task was queued while tracking was off. */
	public static final long UNKNOWN = Long.MIN_VALUE;

	private final BooleanSupplier tracking;

	private final Set<Worker> workers = ConcurrentHashMap.newKeySet();

	private final ThreadLocal<Worker> current = new ThreadLocal<>();

	private final AtomicLong rejected = new AtomicLong();

	private final AtomicLong cancelled = new AtomicLong();

	private final AtomicLong completed = new AtomicLong();

	private final AtomicLongArray queueWaits = new AtomicLongArray(DurationHistogram.BUCKETS);

	private final AtomicLongArray durations = new AtomicLongArray(DurationHistogram.BUCKETS);

	/** The time the tracked tasks finished spent working for each component, by component. */
	private final ConcurrentHashMap<Class<?>, LongAdder> componentBusy = new ConcurrentHashMap<>();

	/**
	 * Creates a tracker that measures the tasks only while tracking is on.
	 *
	 * @param tracking whether task tracking is on, read for every task
	 */
	public WorkerPoolTracker(final BooleanSupplier tracking) {
		this.tracking = tracking;
	}

	/** One worker thread and its current task. */
	public static final class Worker {

		private final Thread thread;

		private volatile Object task;

		private volatile long startNanos = UNKNOWN;

		/** The time spent in the tracked tasks finished, written by the worker itself only. */
		private volatile long busyNanos;

		/**
		 * The component the last tracked task finished worked for, {@code null} when unknown;
		 * written by the worker itself only, before {@link #busyNanos}.
		 */
		private volatile Class<?> lastComponent;

		private Worker(final Thread thread) {
			this.thread = thread;
		}
	}

	/**
	 * Tells whether task tracking is on.
	 *
	 * @return whether task tracking is on
	 */
	public boolean isTracking() {
		return this.tracking.getAsBoolean();
	}

	/**
	 * Registers the calling thread as one of the pool's workers, until
	 * {@link #leave(Worker)}.
	 *
	 * @return the worker, to pass to {@link #taskStarted} and {@link #taskFinished}
	 */
	public Worker join() {
		Worker worker = new Worker(Thread.currentThread());
		this.workers.add(worker);
		this.current.set(worker);
		return worker;
	}

	/**
	 * Forgets a worker that ends.
	 *
	 * @param worker a worker that ends, as {@link #join()} returned it
	 */
	public void leave(final Worker worker) {
		this.workers.remove(worker);
		this.current.remove();
	}

	/**
	 * Finds the worker the calling thread is.
	 *
	 * @return the calling thread as a worker, {@code null} when it did not join
	 */
	public Worker currentWorker() {
		return this.current.get();
	}

	/**
	 * Reads the clock for a task being queued.
	 *
	 * @return the time a task is queued, {@link #UNKNOWN} while tracking is off
	 */
	public long enqueueTime() {
		return this.isTracking() ? System.nanoTime() : UNKNOWN;
	}

	/**
	 * Records the start of a task on a worker.
	 *
	 * @param worker        the worker starting the task, {@code null} when unknown
	 * @param task          the task, labeled when it is a {@link LabeledTask}
	 * @param enqueuedNanos when it was queued, {@link #UNKNOWN} when not known
	 */
	public void taskStarted(final Worker worker, final Object task, final long enqueuedNanos) {
		if (worker == null || !this.isTracking()) {
			return;
		}
		long now = System.nanoTime();
		if (enqueuedNanos != UNKNOWN) {
			this.queueWaits.incrementAndGet(DurationHistogram.bucketOf(now - enqueuedNanos));
		}
		worker.task = task;
		worker.startNanos = now;
	}

	/**
	 * Ends the worker's current task, if it was tracked - even when tracking
	 * was turned off meanwhile.
	 *
	 * @param worker the worker, {@code null} when unknown
	 */
	public void taskFinished(final Worker worker) {
		if (worker == null) {
			return;
		}
		long start = worker.startNanos;
		if (start == UNKNOWN) {
			return;
		}
		Object task = worker.task;
		long duration = System.nanoTime() - start;
		Class<?> component = componentOf(task);
		// The component before the busy time, so that whoever sees the time grow sees its component
		// - then the end of the task, see workers() for why.
		worker.lastComponent = component;
		worker.busyNanos += duration;
		worker.startNanos = UNKNOWN;
		worker.task = null;
		// The component's time once the task no longer shows as running - see componentBusyNanos().
		if (component != null) {
			LongAdder busy = this.componentBusy.get(component);
			if (busy == null) {
				busy = this.componentBusy.computeIfAbsent(component, key -> new LongAdder());
			}
			busy.add(duration);
		}
		this.durations.incrementAndGet(DurationHistogram.bucketOf(duration));
		this.completed.incrementAndGet();
	}

	/** Counts a task refused because the queue was full. */
	public void taskRejected() {
		this.rejected.incrementAndGet();
	}

	/**
	 * Counts the tasks refused because the queue was full.
	 *
	 * @return the tasks refused because the queue was full
	 */
	public long rejectedTasks() {
		return this.rejected.get();
	}

	/** Counts a queued task cancelled before it ran, because a newer task replaced it. */
	public void taskCancelled() {
		this.cancelled.incrementAndGet();
	}

	/**
	 * Counts the queued tasks cancelled before they ran because a newer task of the same message key replaced
	 * them. The tasks cancelled when the pool stops are not counted: the pool is discarded with its counters.
	 *
	 * @return the queued tasks replaced by a newer one
	 */
	public long cancelledTasks() {
		return this.cancelled.get();
	}

	/**
	 * Counts the tracked tasks that completed.
	 *
	 * @return the tracked tasks completed
	 */
	public long completedTasks() {
		return this.completed.get();
	}

	/**
	 * Returns the distribution of the time the tracked tasks waited in the queue.
	 *
	 * @return how long the tracked tasks waited in the queue
	 */
	public DurationHistogram queueWaits() {
		return histogram(this.queueWaits);
	}

	/**
	 * Returns the distribution of the time the tracked tasks ran.
	 *
	 * @return how long the tracked tasks ran
	 */
	public DurationHistogram durations() {
		return histogram(this.durations);
	}

	/**
	 * Measures the time the tracked tasks spent working for each component.
	 *
	 * @return the time the tracked tasks spent working for each component - the one that
	 *         submitted an async task, the consumer of an event - the running tasks included: the
	 *         difference between two reads, divided by the time in between, gives how many workers
	 *         worked for it on average, however short its tasks; the tasks of no known component
	 *         are left out
	 */
	public Map<Class<?>, Long> componentBusyNanos() {
		// The finished tasks first, then the running ones: a task ending meanwhile adds its time
		// once it no longer shows as running, so it is at worst left out of this read, never
		// counted twice. A running task is read before its start, which is set after it.
		Map<Class<?>, Long> busy = new HashMap<>();
		this.componentBusy.forEach((component, time) -> busy.put(component, time.sum()));
		long now = System.nanoTime();
		for (Worker worker : this.workers) {
			Object task = worker.task;
			long start = worker.startNanos;
			Class<?> component = start == UNKNOWN ? null : componentOf(task);
			if (component != null) {
				busy.merge(component, Math.max(0, now - start), Long::sum);
			}
		}
		return Map.copyOf(busy);
	}

	/** @return the component a task works for, {@code null} when unknown */
	private static Class<?> componentOf(final Object task) {
		return task instanceof LabeledTask labeled ? labeled.taskComponent() : null;
	}

	/**
	 * Lists the workers still alive.
	 *
	 * @return the workers still alive, by thread id
	 */
	public List<WorkerState> workers() {
		long now = System.nanoTime();
		List<WorkerState> states = new ArrayList<>();
		for (Worker worker : this.workers) {
			if (!worker.thread.isAlive()) {
				this.workers.remove(worker);
				continue;
			}
			// Busy time before start: the worker adds a finished task's time before clearing its
			// start, so a task ending meanwhile is at worst left out of this read, never counted twice.
			long busy = worker.busyNanos;
			Class<?> lastComponent = worker.lastComponent;
			long start = worker.startNanos;
			Object task = worker.task;
			boolean inTask = start != UNKNOWN;
			LabeledTask labeled = inTask && task instanceof LabeledTask candidate ? candidate : null;
			states.add(new WorkerState(worker.thread.threadId(), worker.thread.getName(), inTask,
					inTask ? Math.max(0, now - start) : -1, labeled == null ? null : labeled.taskLabel(),
					labeled == null ? null : labeled.taskComponent(), busy + (inTask ? Math.max(0, now - start) : 0),
					lastComponent));
		}
		states.sort(Comparator.comparingLong(WorkerState::threadId));
		return states;
	}

	private static DurationHistogram histogram(final AtomicLongArray counts) {
		long[] copy = new long[DurationHistogram.BUCKETS];
		for (int bucket = 0; bucket < copy.length; bucket++) {
			copy[bucket] = counts.get(bucket);
		}
		return new DurationHistogram(copy);
	}
}
