/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.async.AsyncParameters;
import org.opales.covalent.container.async.AsyncServices;
import org.opales.covalent.container.async.AsyncWorkersServices;
import org.opales.covalent.container.async.impl.AsyncManagerImpl;
import org.opales.covalent.container.events.EventsManagerParameters;
import org.opales.covalent.container.events.EventsWorkersServices;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.CovalentContainerizedTests;
import org.opales.covalent.container.tests.unit.data.ADisplayData;
import org.opales.covalent.container.tests.unit.events.AComponentEvents;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;
import org.opales.covalent.container.workers.WorkerPoolKind;
import org.opales.covalent.container.workers.WorkerPoolState;
import org.opales.covalent.container.workers.WorkerState;

public class WorkerPoolsContainerizedTests extends CovalentContainerizedTests {

	private static final String QUEUE_KEY = "WORKER_POOLS_TEST";

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	private AsyncWorkersServices asyncWorkers;

	private EventsWorkersServices eventsWorkers;

	private AsyncParameters asyncParameters;

	private EventsManagerParameters eventsParameters;

	private int originalQueueCapacity;

	private CapturingLogHandler asyncManagerLogs;

	@Before
	public void setUp() {
		this.asyncManagerLogs = CapturingLogHandler.install(AsyncManagerImpl.class);
		this.rcs.registerComponent(WorkerPoolsContainerizedTests.class, this);
		this.asyncWorkers = this.rcs.useServices(AsyncWorkersServices.class);
		this.eventsWorkers = this.rcs.useServices(EventsWorkersServices.class);
		this.asyncParameters = this.rcs.accessParameters(AsyncParameters.class);
		this.eventsParameters = this.rcs.accessParameters(EventsManagerParameters.class);
		this.originalQueueCapacity = this.asyncParameters.getWorkerQueueCapacity();
	}

	@After
	public void tearDown() {
		this.asyncParameters.setTaskTracking(false);
		this.eventsParameters.setTaskTracking(false);
		this.rcs.accessParameters(AsyncParameters.class).setWorkerQueueCapacity(this.originalQueueCapacity);
		this.rcs.useServices(AsyncServices.class).releaseQueue(QUEUE_KEY);
		this.cm.getParameters().setSynchronousEvents(true);
		this.rcs.unregisterComponent(WorkerPoolsContainerizedTests.class);
		this.asyncManagerLogs.restore();
	}

	@Test
	public void trackingIsOffByDefault() {
		Assert.assertFalse(this.asyncParameters.isTaskTracking());
		Assert.assertFalse(this.eventsParameters.isTaskTracking());
	}

	@Test
	public void asyncTaskIsShownRunningThenCounted() throws Exception {
		this.asyncParameters.setTaskTracking(true);
		CountDownLatch release = new CountDownLatch(1);
		AsyncServices async = this.rcs.useServices(AsyncServices.class);
		var done = async.submit(QUEUE_KEY, (Runnable) () -> await(release)).toCompletableFuture();

		WorkerPoolState running = this.awaitAsyncPool(pool -> pool.workers().stream().anyMatch(WorkerState::inTask));
		Assert.assertEquals(WorkerPoolKind.DEDICATED, running.kind());
		Assert.assertTrue(running.taskTracking());
		Assert.assertEquals(1, running.workers().size());
		Assert.assertTrue(running.workers().get(0).threadName().startsWith("covalent-async-" + QUEUE_KEY));
		Assert.assertEquals(WorkerPoolsContainerizedTests.class, running.workers().get(0).taskComponent());
		Assert.assertTrue(running.componentBusyNanos().containsKey(WorkerPoolsContainerizedTests.class));

		release.countDown();
		done.get(2, TimeUnit.SECONDS);
		WorkerPoolState after = this.awaitAsyncPool(pool -> pool.completedTasks() == 1);
		Assert.assertTrue(after.workers().get(0).busyNanos() > 0);
		Assert.assertEquals(after.workers().get(0).busyNanos(),
				(long) after.componentBusyNanos().get(WorkerPoolsContainerizedTests.class));
		Assert.assertEquals(1, after.durations().total());
		Assert.assertEquals(1, after.queueWaits().total());
		Assert.assertFalse(after.workers().get(0).inTask());

		Assert.assertEquals(WorkerPoolKind.GENERAL, this.asyncWorkers.readWorkerPools().get(0).kind());
	}

	@Test
	public void noSubmitterIsKeptWithoutTracking() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch started = new CountDownLatch(1);
		AsyncServices async = this.rcs.useServices(AsyncServices.class);
		var done = async.submit(QUEUE_KEY, (Runnable) () -> {
			started.countDown();
			await(release);
		}).toCompletableFuture();
		Assert.assertTrue(started.await(2, TimeUnit.SECONDS));
		// Turned on once the task runs: the task itself was submitted untracked.
		this.asyncParameters.setTaskTracking(true);
		WorkerPoolState pool = this.awaitAsyncPool(state -> true);
		Assert.assertNull(pool.workers().get(0).taskComponent());
		Assert.assertTrue(pool.componentBusyNanos().isEmpty());
		release.countDown();
		done.get(2, TimeUnit.SECONDS);
	}

	@Test
	public void asyncRejectionsAreCountedWithoutTracking() throws Exception {
		this.rcs.accessParameters(AsyncParameters.class).setWorkerQueueCapacity(1);
		CountDownLatch release = new CountDownLatch(1);
		AsyncServices async = this.rcs.useServices(AsyncServices.class);
		CountDownLatch started = new CountDownLatch(1);
		var first = async.submit(QUEUE_KEY, (Runnable) () -> {
			started.countDown();
			await(release);
		}).toCompletableFuture();
		Assert.assertTrue(started.await(2, TimeUnit.SECONDS));
		var second = async.submit(QUEUE_KEY, (Runnable) () -> {
		}).toCompletableFuture();
		Assert.assertThrows(RejectedExecutionException.class, () -> async.submit(QUEUE_KEY, (Runnable) () -> {
		}));

		WorkerPoolState pool = this.awaitAsyncPool(state -> true);
		Assert.assertEquals(1, pool.rejectedTasks());
		Assert.assertEquals(1, pool.queuedTasks());
		Assert.assertEquals(1, pool.queueCapacity());
		Assert.assertFalse(pool.taskTracking());
		Assert.assertEquals(0, pool.completedTasks());

		release.countDown();
		first.get(2, TimeUnit.SECONDS);
		second.get(2, TimeUnit.SECONDS);
		Assert.assertTrue(this.asyncManagerLogs.containsWarning(
				"Async task refused: the worker pool queue is full (capacity 1, pool WORKER_POOLS_TEST). A producer is outpacing its consumers, or a task is stalled."));
	}

	@Test
	public void eventDeliveryIsShownWithItsConsumerMethod() throws Exception {
		this.cm.getParameters().setSynchronousEvents(false);
		this.eventsParameters.setTaskTracking(true);
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch delivered = new CountDownLatch(1);
		AComponentEvents consumer = new AComponentEvents() {

			@Override
			public void onNotification(final ADisplayData value) {
				await(release);
				delivered.countDown();
			}

			@Override
			public void onNotification(final ADisplayData value, final boolean aValue) {
			}
		};
		this.rcs.subscribe(WorkerPoolsContainerizedTests.class, AComponentEvents.class, consumer);
		try {
			AComponentEvents producer = this.rcs.produceEvents(WorkerPoolsContainerizedTests.class,
					AComponentEvents.class);
			producer.onNotification(new ADisplayData());

			WorkerState busy = this.awaitEventsWorker(worker -> worker.inTask() && worker.taskLabel() != null
					&& worker.taskLabel().endsWith("onNotification"));
			Assert.assertTrue(busy.threadName().startsWith("covalent-events-async-"));
			// A plain object subscribed through the raw port: no component to name.
			Assert.assertNull(busy.taskComponent());

			release.countDown();
			Assert.assertTrue(delivered.await(2, TimeUnit.SECONDS));
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
			while (this.eventsWorkers.readWorkerPools().get(0).completedTasks() == 0 && System.nanoTime() < deadline) {
				Thread.sleep(10);
			}
			WorkerPoolState pool = this.eventsWorkers.readWorkerPools().get(0);
			Assert.assertEquals(WorkerPoolKind.EVENTS, pool.kind());
			Assert.assertTrue(pool.completedTasks() >= 1);
			Assert.assertTrue(pool.queueWaits().total() >= 1);
		} finally {
			release.countDown();
			this.rcs.unsubscribe(WorkerPoolsContainerizedTests.class, AComponentEvents.class, consumer);
		}
	}

	@Test
	public void noEventsPoolWhenEventsAreSynchronous() {
		this.cm.getParameters().setSynchronousEvents(true);
		Assert.assertEquals(List.of(), this.eventsWorkers.readWorkerPools());
	}

	private WorkerPoolState awaitAsyncPool(final Predicate<WorkerPoolState> condition) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
		while (true) {
			for (WorkerPoolState pool : this.asyncWorkers.readWorkerPools()) {
				if (QUEUE_KEY.equals(pool.name()) && condition.test(pool)) {
					return pool;
				}
			}
			if (System.nanoTime() > deadline) {
				throw new AssertionError("Dedicated pool never matched: " + this.asyncWorkers.readWorkerPools());
			}
			Thread.sleep(10);
		}
	}

	private WorkerState awaitEventsWorker(final Predicate<WorkerState> condition) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
		while (true) {
			for (WorkerPoolState pool : this.eventsWorkers.readWorkerPools()) {
				for (WorkerState worker : pool.workers()) {
					if (condition.test(worker)) {
						return worker;
					}
				}
			}
			if (System.nanoTime() > deadline) {
				throw new AssertionError("No events worker matched: " + this.eventsWorkers.readWorkerPools());
			}
			Thread.sleep(10);
		}
	}

	private static void await(final CountDownLatch latch) {
		try {
			latch.await(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
