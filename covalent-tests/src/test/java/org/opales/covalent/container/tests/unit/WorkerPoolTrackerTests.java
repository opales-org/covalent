/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.container.workers.DurationHistogram;
import org.opales.covalent.container.workers.WorkerState;
import org.opales.covalent.container.workers.impl.LabeledTask;
import org.opales.covalent.container.workers.impl.WorkerPoolTracker;

public class WorkerPoolTrackerTests {

	private final AtomicBoolean tracking = new AtomicBoolean();

	private final WorkerPoolTracker tracker = new WorkerPoolTracker(this.tracking::get);

	private final LabeledTask task = () -> "Consumer.onEvent";

	@Test
	public void histogramBucketsArePowersOfTwoMicroseconds() {
		Assert.assertEquals(0, DurationHistogram.bucketOf(500));
		Assert.assertEquals(1, DurationHistogram.bucketOf(1_000));
		Assert.assertEquals(2, DurationHistogram.bucketOf(3_000));
		Assert.assertEquals(11, DurationHistogram.bucketOf(1_500_000));
		Assert.assertEquals(DurationHistogram.BUCKETS - 1, DurationHistogram.bucketOf(Long.MAX_VALUE));
		Assert.assertEquals(2_048_000, DurationHistogram.upperBoundNanos(11));
	}

	@Test
	public void histogramPercentilesAndDifference() {
		long[] counts = new long[DurationHistogram.BUCKETS];
		counts[1] = 90;
		counts[10] = 10;
		DurationHistogram later = new DurationHistogram(counts);
		Assert.assertEquals(100, later.total());
		Assert.assertEquals(2_000, later.percentileNanos(50));
		Assert.assertEquals(1_024_000, later.percentileNanos(95));
		Assert.assertEquals(-1, DurationHistogram.EMPTY.percentileNanos(50));

		long[] before = new long[DurationHistogram.BUCKETS];
		before[1] = 40;
		Assert.assertEquals(60, later.since(new DurationHistogram(before)).total());
	}

	@Test
	public void withoutTrackingOnlyRejectionsAreCounted() {
		WorkerPoolTracker.Worker worker = this.tracker.join();
		try {
			Assert.assertEquals(WorkerPoolTracker.UNKNOWN, this.tracker.enqueueTime());
			this.tracker.taskStarted(worker, this.task, WorkerPoolTracker.UNKNOWN);
			WorkerState state = this.onlyWorker();
			Assert.assertFalse(state.inTask());
			Assert.assertEquals(-1, state.taskNanos());
			Assert.assertEquals(0, state.busyNanos());
			this.tracker.taskFinished(worker);
			this.tracker.taskRejected();

			Assert.assertEquals(0, this.tracker.completedTasks());
			Assert.assertEquals(1, this.tracker.rejectedTasks());
		} finally {
			this.tracker.leave(worker);
		}
	}

	@Test
	public void trackedTaskIsShownThenCounted() {
		this.tracking.set(true);
		WorkerPoolTracker.Worker worker = this.tracker.join();
		try {
			Assert.assertSame(worker, this.tracker.currentWorker());
			this.tracker.taskStarted(worker, this.task, System.nanoTime() - 5_000_000);
			WorkerState state = this.onlyWorker();
			Assert.assertTrue(state.inTask());
			Assert.assertTrue(state.taskNanos() >= 0);
			Assert.assertEquals("Consumer.onEvent", state.taskLabel());
			Assert.assertEquals(Thread.currentThread().getName(), state.threadName());
			Assert.assertEquals(state.taskNanos(), state.busyNanos(), 1_000_000);

			// Turned off meanwhile: the task started tracked still ends counted.
			this.tracking.set(false);
			this.tracker.taskFinished(worker);

			Assert.assertFalse(this.onlyWorker().inTask());
			Assert.assertTrue(this.onlyWorker().busyNanos() > 0);
			Assert.assertEquals(1, this.tracker.completedTasks());
			Assert.assertEquals(1, this.tracker.durations().total());
			Assert.assertEquals(1, this.tracker.queueWaits().total());
			Assert.assertTrue(this.tracker.queueWaits().percentileNanos(50) >= 5_000_000);
		} finally {
			this.tracker.leave(worker);
		}
		Assert.assertTrue(this.tracker.workers().isEmpty());
	}

	@Test
	public void componentBusyTimeCountsTheRunningAndFinishedTasksOfEachComponent() {
		this.tracking.set(true);
		WorkerPoolTracker.Worker worker = this.tracker.join();
		try {
			this.tracker.taskStarted(worker, new ComponentTask(String.class), WorkerPoolTracker.UNKNOWN);
			sleepMillis(2);
			long running = this.tracker.componentBusyNanos().get(String.class);
			Assert.assertTrue(running >= 2_000_000);
			this.tracker.taskFinished(worker);
			long finished = this.tracker.componentBusyNanos().get(String.class);
			Assert.assertTrue(finished >= running);

			this.tracker.taskStarted(worker, new ComponentTask(Integer.class), WorkerPoolTracker.UNKNOWN);
			this.tracker.taskFinished(worker);
			this.tracker.taskStarted(worker, this.task, WorkerPoolTracker.UNKNOWN);
			this.tracker.taskFinished(worker);

			// A task of no known component is left out; the others keep their time.
			Assert.assertEquals(java.util.Set.of(String.class, Integer.class),
					this.tracker.componentBusyNanos().keySet());
			Assert.assertEquals(finished, (long) this.tracker.componentBusyNanos().get(String.class));
		} finally {
			this.tracker.leave(worker);
		}
	}

	@Test
	public void withoutTrackingNoComponentBusyTimeIsCounted() {
		WorkerPoolTracker.Worker worker = this.tracker.join();
		try {
			this.tracker.taskStarted(worker, new ComponentTask(String.class), WorkerPoolTracker.UNKNOWN);
			Assert.assertTrue(this.tracker.componentBusyNanos().isEmpty());
			this.tracker.taskFinished(worker);
			Assert.assertTrue(this.tracker.componentBusyNanos().isEmpty());
		} finally {
			this.tracker.leave(worker);
		}
	}

	@Test
	public void aFinishedTaskLeavesTheComponentItWorkedFor() {
		this.tracking.set(true);
		WorkerPoolTracker.Worker worker = this.tracker.join();
		try {
			Assert.assertNull(this.onlyWorker().lastTaskComponent());
			this.tracker.taskStarted(worker, new ComponentTask(String.class), WorkerPoolTracker.UNKNOWN);
			this.tracker.taskFinished(worker);
			WorkerState state = this.onlyWorker();
			Assert.assertFalse(state.inTask());
			Assert.assertTrue(state.busyNanos() > 0);
			Assert.assertEquals(String.class, state.lastTaskComponent());

			this.tracker.taskStarted(worker, new ComponentTask(Integer.class), WorkerPoolTracker.UNKNOWN);
			this.tracker.taskFinished(worker);
			Assert.assertEquals(Integer.class, this.onlyWorker().lastTaskComponent());

			// A task of no known component leaves none, not the previous one.
			this.tracker.taskStarted(worker, this.task, WorkerPoolTracker.UNKNOWN);
			this.tracker.taskFinished(worker);
			Assert.assertNull(this.onlyWorker().lastTaskComponent());
		} finally {
			this.tracker.leave(worker);
		}
	}

	/** A task working for a component. */
	private record ComponentTask(Class<?> taskComponent) implements LabeledTask {

		@Override
		public String taskLabel() {
			return null;
		}
	}

	private static void sleepMillis(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private WorkerState onlyWorker() {
		List<WorkerState> workers = this.tracker.workers();
		Assert.assertEquals(1, workers.size());
		return workers.get(0);
	}
}
