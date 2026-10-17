/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.async.AsyncParameters;
import org.opales.covalent.container.async.AsyncServices;
import org.opales.covalent.container.async.impl.AsyncManagerImpl;
import org.opales.covalent.container.tests.CovalentContainerizedTests;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;

/**
 * Worker pool queues must be bounded and reject
 * submissions once full, instead of growing without limit, and the general
 * worker thread count must be configurable.
 */
public class AsyncResourceLimitsContainerizedTests extends CovalentContainerizedTests {

	private CapturingLogHandler asyncManagerLogs;

	private final CovalentContainer cm = Covalent.main;

	private int originalGeneralWorkerCount;

	private int originalWorkerQueueCapacity;

	@Before
	public void setUp() {
		this.asyncManagerLogs = CapturingLogHandler.install(AsyncManagerImpl.class);
		Covalent.main.rcs().registerComponent(this);
		AsyncParameters parameters = this.accessAsyncParameters();
		this.originalGeneralWorkerCount = parameters.getGeneralWorkerCount();
		this.originalWorkerQueueCapacity = parameters.getWorkerQueueCapacity();
	}

	@After
	public void tearDown() {
		AsyncParameters parameters = this.accessAsyncParameters();
		parameters.setGeneralWorkerCount(this.originalGeneralWorkerCount);
		parameters.setWorkerQueueCapacity(this.originalWorkerQueueCapacity);
		this.cm.stop();
		this.cm.start();
		this.asyncManagerLogs.restore();
	}

	@Test
	public void generalPoolRejectsSubmissionsBeyondConfiguredQueueCapacity() throws Exception {
		AsyncParameters parameters = this.accessAsyncParameters();
		parameters.setGeneralWorkerCount(1);
		parameters.setWorkerQueueCapacity(2);
		this.cm.stop();
		this.cm.start();
		Covalent.main.rcs().registerComponent(this);
		AsyncServices asyncServices = this.useAsyncServices();

		CountDownLatch blockerStarted = new CountDownLatch(1);
		CountDownLatch releaseBlocker = new CountDownLatch(1);
		asyncServices.submit(this.blockingTask(blockerStarted, releaseBlocker));
		Assert.assertTrue(blockerStarted.await(1, TimeUnit.SECONDS));

		// the single worker is busy; the queue (capacity 2) can still hold 2 more.
		CompletableFuture<Void> queued1 = asyncServices.submit((Runnable) () -> {
		}).toCompletableFuture();
		CompletableFuture<Void> queued2 = asyncServices.submit((Runnable) () -> {
		}).toCompletableFuture();

		Assert.assertThrows(RejectedExecutionException.class,
				() -> asyncServices.submit((Runnable) () -> Assert.fail("Must not run: queue was full.")));

		releaseBlocker.countDown();
		queued1.get(2, TimeUnit.SECONDS);
		queued2.get(2, TimeUnit.SECONDS);
		Assert.assertTrue(this.asyncManagerLogs.containsWarning(
				"Async task refused: the worker pool queue is full (capacity 2, pool general). A producer is outpacing its consumers, or a task is stalled."));
	}

	@Test
	public void dedicatedPoolRejectsSubmissionsBeyondConfiguredQueueCapacity() throws Exception {
		AsyncParameters parameters = this.accessAsyncParameters();
		parameters.setWorkerQueueCapacity(2);
		// dedicated pools are created lazily and pick up the current capacity
		// immediately - no container restart needed for this one.
		AsyncServices asyncServices = this.useAsyncServices();
		String queueKey = "ASYNC_QUEUE_CAPACITY_DEDICATED";

		CountDownLatch blockerStarted = new CountDownLatch(1);
		CountDownLatch releaseBlocker = new CountDownLatch(1);
		asyncServices.submit(queueKey, this.blockingTask(blockerStarted, releaseBlocker));
		Assert.assertTrue(blockerStarted.await(1, TimeUnit.SECONDS));

		CompletableFuture<Void> queued1 = asyncServices.submit(queueKey, (Runnable) () -> {
		}).toCompletableFuture();
		CompletableFuture<Void> queued2 = asyncServices.submit(queueKey, (Runnable) () -> {
		}).toCompletableFuture();

		Assert.assertThrows(RejectedExecutionException.class,
				() -> asyncServices.submit(queueKey, (Runnable) () -> Assert.fail("Must not run: queue was full.")));

		releaseBlocker.countDown();
		queued1.get(2, TimeUnit.SECONDS);
		queued2.get(2, TimeUnit.SECONDS);
		asyncServices.releaseQueue(queueKey);
		Assert.assertTrue(this.asyncManagerLogs.containsWarning(
				"Async task refused: the worker pool queue is full (capacity 2, pool ASYNC_QUEUE_CAPACITY_DEDICATED). A producer is outpacing its consumers, or a task is stalled."));
	}

	@Test
	public void generalWorkerCountOfOneSerializesGeneralPoolTasks() throws Exception {
		AsyncParameters parameters = this.accessAsyncParameters();
		parameters.setGeneralWorkerCount(1);
		this.cm.stop();
		this.cm.start();
		Covalent.main.rcs().registerComponent(this);
		AsyncServices asyncServices = this.useAsyncServices();

		CountDownLatch firstStarted = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		asyncServices.submit(this.blockingTask(firstStarted, releaseFirst));
		Assert.assertTrue(firstStarted.await(1, TimeUnit.SECONDS));

		CountDownLatch secondStarted = new CountDownLatch(1);
		asyncServices.submit((Runnable) secondStarted::countDown);

		Assert.assertFalse("With a single general worker, the second task must wait for the first.",
				secondStarted.await(200, TimeUnit.MILLISECONDS));

		releaseFirst.countDown();
		Assert.assertTrue(secondStarted.await(1, TimeUnit.SECONDS));
	}

	@Test
	public void generalWorkerCountOfThreeAllowsThreeConcurrentTasks() throws Exception {
		AsyncParameters parameters = this.accessAsyncParameters();
		parameters.setGeneralWorkerCount(3);
		this.cm.stop();
		this.cm.start();
		Covalent.main.rcs().registerComponent(this);
		AsyncServices asyncServices = this.useAsyncServices();

		CountDownLatch allStarted = new CountDownLatch(3);
		CountDownLatch release = new CountDownLatch(1);
		for (int i = 0; i < 3; i++) {
			asyncServices.submit(this.blockingTask(allStarted, release));
		}
		Assert.assertTrue("All 3 tasks should be able to run concurrently on 3 general workers.",
				allStarted.await(1, TimeUnit.SECONDS));
		release.countDown();
	}

	@Test
	public void generalPoolSettingsApplyWithoutRestart() throws Exception {
		AsyncParameters parameters = this.accessAsyncParameters();
		AsyncServices asyncServices = this.useAsyncServices();
		parameters.setGeneralWorkerCount(1);
		parameters.setWorkerQueueCapacity(2);

		CountDownLatch blockerStarted = new CountDownLatch(1);
		CountDownLatch releaseBlocker = new CountDownLatch(1);
		asyncServices.submit(this.blockingTask(blockerStarted, releaseBlocker));
		Assert.assertTrue(blockerStarted.await(1, TimeUnit.SECONDS));

		CountDownLatch secondStarted = new CountDownLatch(1);
		CompletableFuture<Void> queued1 = asyncServices.submit((Runnable) secondStarted::countDown)
				.toCompletableFuture();
		CompletableFuture<Void> queued2 = asyncServices.submit((Runnable) () -> {
		}).toCompletableFuture();
		Assert.assertFalse("With a single general worker, the second task must wait for the first.",
				secondStarted.await(200, TimeUnit.MILLISECONDS));
		Assert.assertThrows(RejectedExecutionException.class,
				() -> asyncServices.submit((Runnable) () -> Assert.fail("Must not run: queue was full.")));

		releaseBlocker.countDown();
		queued1.get(2, TimeUnit.SECONDS);
		queued2.get(2, TimeUnit.SECONDS);
		Assert.assertTrue(this.asyncManagerLogs.containsWarning(
				"Async task refused: the worker pool queue is full (capacity 2, pool general). A producer is outpacing its consumers, or a task is stalled."));
	}

	@Test
	public void growingTheGeneralWorkerCountAddsWorkersWithoutRestart() throws Exception {
		AsyncParameters parameters = this.accessAsyncParameters();
		AsyncServices asyncServices = this.useAsyncServices();
		parameters.setGeneralWorkerCount(1);
		parameters.setGeneralWorkerCount(3);

		CountDownLatch allStarted = new CountDownLatch(3);
		CountDownLatch release = new CountDownLatch(1);
		for (int i = 0; i < 3; i++) {
			asyncServices.submit(this.blockingTask(allStarted, release));
		}
		Assert.assertTrue("All 3 tasks should run concurrently once the pool has grown to 3 workers.",
				allStarted.await(1, TimeUnit.SECONDS));
		release.countDown();
	}

	@Test
	public void shrinkingTheGeneralWorkerCountLetsRunningTasksFinish() throws Exception {
		AsyncParameters parameters = this.accessAsyncParameters();
		AsyncServices asyncServices = this.useAsyncServices();
		parameters.setGeneralWorkerCount(3);

		CountDownLatch allStarted = new CountDownLatch(3);
		CountDownLatch release = new CountDownLatch(1);
		CompletableFuture<?>[] running = new CompletableFuture<?>[3];
		for (int i = 0; i < 3; i++) {
			running[i] = asyncServices.submit(this.blockingTask(allStarted, release)).toCompletableFuture();
		}
		Assert.assertTrue(allStarted.await(1, TimeUnit.SECONDS));

		parameters.setGeneralWorkerCount(1);
		release.countDown();
		CompletableFuture.allOf(running).get(2, TimeUnit.SECONDS);

		CountDownLatch firstStarted = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		asyncServices.submit(this.blockingTask(firstStarted, releaseFirst));
		Assert.assertTrue(firstStarted.await(1, TimeUnit.SECONDS));
		CountDownLatch secondStarted = new CountDownLatch(1);
		asyncServices.submit((Runnable) secondStarted::countDown);
		Assert.assertFalse("Once shrunk to a single worker, the second task must wait for the first.",
				secondStarted.await(200, TimeUnit.MILLISECONDS));
		releaseFirst.countDown();
		Assert.assertTrue(secondStarted.await(1, TimeUnit.SECONDS));
	}

	@Test
	public void queueCapacityChangeAppliesToAnExistingDedicatedQueue() throws Exception {
		AsyncParameters parameters = this.accessAsyncParameters();
		AsyncServices asyncServices = this.useAsyncServices();
		String queueKey = "ASYNC_QUEUE_CAPACITY_EXISTING_DEDICATED";
		asyncServices.submit(queueKey, (Runnable) () -> {
		}).toCompletableFuture().get(1, TimeUnit.SECONDS);

		parameters.setWorkerQueueCapacity(2);

		CountDownLatch blockerStarted = new CountDownLatch(1);
		CountDownLatch releaseBlocker = new CountDownLatch(1);
		asyncServices.submit(queueKey, this.blockingTask(blockerStarted, releaseBlocker));
		Assert.assertTrue(blockerStarted.await(1, TimeUnit.SECONDS));
		CompletableFuture<Void> queued1 = asyncServices.submit(queueKey, (Runnable) () -> {
		}).toCompletableFuture();
		CompletableFuture<Void> queued2 = asyncServices.submit(queueKey, (Runnable) () -> {
		}).toCompletableFuture();
		Assert.assertThrows(RejectedExecutionException.class,
				() -> asyncServices.submit(queueKey, (Runnable) () -> Assert.fail("Must not run: queue was full.")));

		releaseBlocker.countDown();
		queued1.get(2, TimeUnit.SECONDS);
		queued2.get(2, TimeUnit.SECONDS);
		asyncServices.releaseQueue(queueKey);
		Assert.assertTrue(this.asyncManagerLogs.containsWarning(
				"Async task refused: the worker pool queue is full (capacity 2, pool ASYNC_QUEUE_CAPACITY_EXISTING_DEDICATED). A producer is outpacing its consumers, or a task is stalled."));
	}

	private Runnable blockingTask(final CountDownLatch started, final CountDownLatch release) {
		return () -> {
			started.countDown();
			try {
				release.await(5, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		};
	}

	private AsyncServices useAsyncServices() {
		return Covalent.main.rcs().useServices(AsyncServices.class);
	}

	private AsyncParameters accessAsyncParameters() {
		return Covalent.main.rcs().accessParameters(AsyncParameters.class);
	}

}
