/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.async.AsyncServices;
import org.opales.covalent.container.tests.CovalentContainerizedTests;

public class AsyncServicesContainerizedTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	private AsyncServices asyncServices;

	@Before
	public void setUp() {
		this.cm.rcs().registerComponent(this);
		// These tests use synchronous events to avoid unrelated event synchronization.
		this.cm.getParameters().setSynchronousEvents(true);
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
		// Looked up here, not as a field initializer: the base class's own @Before
		// (which runs first) is what starts the container, and this raw lookup
		// needs it already running.
		this.asyncServices = this.cm.rcs().useServices(AsyncServices.class);
	}

	@After
	public void teardown() {
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.trace);

	}

	@Test
	public void submittedRunnableCompletesItsFuture() throws Exception {
		CountDownLatch executed = new CountDownLatch(1);

		CompletableFuture<Void> future = this.asyncServices.submit((Runnable) executed::countDown)
				.toCompletableFuture();

		Assert.assertTrue(executed.await(1, TimeUnit.SECONDS));
		future.get(1, TimeUnit.SECONDS);
	}

	@Test
	public void submittedSupplierCompletesItsFutureWithResult() throws Exception {
		CompletableFuture<Integer> future = this.asyncServices.submit(() -> 42).toCompletableFuture();

		Assert.assertEquals(Integer.valueOf(42), future.get(1, TimeUnit.SECONDS));
	}

	@Test
	public void generalWorkerPoolExecutesSeveralTasks() throws Exception {
		CountDownLatch executed = new CountDownLatch(5);
		AtomicInteger counter = new AtomicInteger();
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (int i = 0; i < 5; i++) {
			futures.add(this.asyncServices.submit((Runnable) () -> {
				counter.incrementAndGet();
				executed.countDown();
			}).toCompletableFuture());
		}

		Assert.assertTrue(executed.await(1, TimeUnit.SECONDS));
		for (CompletableFuture<Void> future : futures) {
			future.get(1, TimeUnit.SECONDS);
		}
		Assert.assertEquals(5, counter.get());
	}

	@Test
	public void dedicatedQueueExecutesTasksInSubmissionOrder() throws Exception {
		String queueKey = "DEDICATED_ORDER_QUEUE";
		CountDownLatch executed = new CountDownLatch(5);
		List<Integer> executionOrder = new ArrayList<>();
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (int i = 0; i < 5; i++) {
			final int index = i;
			futures.add(this.asyncServices.submit(queueKey, (Runnable) () -> {
				synchronized (executionOrder) {
					executionOrder.add(index);
				}
				executed.countDown();
			}).toCompletableFuture());
		}

		Assert.assertTrue(executed.await(1, TimeUnit.SECONDS));
		for (CompletableFuture<Void> future : futures) {
			future.get(1, TimeUnit.SECONDS);
		}
		Assert.assertEquals(List.of(0, 1, 2, 3, 4), executionOrder);
	}

	@Test
	public void dedicatedQueueFlushCancelsPendingTasksWithSameMessageKey() throws Exception {
		String queueKey = "DEDICATED_FLUSH_QUEUE";
		String messageKey = "REFRESH";
		CountDownLatch firstTaskStarted = new CountDownLatch(1);
		CountDownLatch releaseFirstTask = new CountDownLatch(1);
		CountDownLatch latestTaskExecuted = new CountDownLatch(1);
		AtomicInteger replacedTaskExecutions = new AtomicInteger();

		CompletableFuture<Void> firstFuture = this.asyncServices.submit(queueKey, (Runnable) () -> {
			firstTaskStarted.countDown();
			await(releaseFirstTask);
		}).toCompletableFuture();
		Assert.assertTrue(firstTaskStarted.await(1, TimeUnit.SECONDS));

		CompletableFuture<Integer> replacedFuture = this.asyncServices
				.submit(queueKey, messageKey, this.count(replacedTaskExecutions), false).toCompletableFuture();
		CompletableFuture<Void> latestFuture = this.asyncServices.submit(queueKey, messageKey, (Runnable) () -> {
			latestTaskExecuted.countDown();
		}, true).toCompletableFuture();

		assertCancelledWith(replacedFuture, "replaced by a newer pending task");

		releaseFirstTask.countDown();
		firstFuture.get(1, TimeUnit.SECONDS);
		latestFuture.get(1, TimeUnit.SECONDS);
		Assert.assertTrue(latestTaskExecuted.await(1, TimeUnit.SECONDS));
		Assert.assertEquals(0, replacedTaskExecutions.get());
	}

	@Test
	public void dedicatedQueueFlushKeepsOtherMessageKeysPending() throws Exception {
		String queueKey = "DEDICATED_TOPIC_QUEUE";
		CountDownLatch firstTaskStarted = new CountDownLatch(1);
		CountDownLatch releaseFirstTask = new CountDownLatch(1);
		AtomicInteger firstMessageCount = new AtomicInteger();
		AtomicInteger secondMessageCount = new AtomicInteger();

		this.asyncServices.submit(queueKey, (Runnable) () -> {
			firstTaskStarted.countDown();
			await(releaseFirstTask);
		});
		Assert.assertTrue(firstTaskStarted.await(1, TimeUnit.SECONDS));

		CompletableFuture<Integer> firstMessageFuture = this.asyncServices
				.submit(queueKey, "MESSAGE_A", this.count(firstMessageCount), false).toCompletableFuture();
		CompletableFuture<Integer> secondMessageFuture = this.asyncServices
				.submit(queueKey, "MESSAGE_B", this.count(secondMessageCount), true).toCompletableFuture();

		releaseFirstTask.countDown();
		Assert.assertEquals(Integer.valueOf(1), firstMessageFuture.get(1, TimeUnit.SECONDS));
		Assert.assertEquals(Integer.valueOf(1), secondMessageFuture.get(1, TimeUnit.SECONDS));
	}

	@Test
	public void blockedDedicatedQueueDoesNotBlockOtherDedicatedQueues() throws Exception {
		String blockedQueueKey = "DEDICATED_BLOCKED_QUEUE";
		String independentQueueKey = "DEDICATED_INDEPENDENT_QUEUE";
		CountDownLatch blockedTaskStarted = new CountDownLatch(1);
		CountDownLatch releaseBlockedTask = new CountDownLatch(1);
		CountDownLatch independentTaskExecuted = new CountDownLatch(1);
		CountDownLatch laterBlockedTaskExecuted = new CountDownLatch(1);

		CompletableFuture<Void> blockedFuture = this.asyncServices
				.submit(blockedQueueKey, this.blockingTask(blockedTaskStarted, releaseBlockedTask))
				.toCompletableFuture();
		Assert.assertTrue(blockedTaskStarted.await(1, TimeUnit.SECONDS));

		CompletableFuture<Void> laterBlockedFuture = this.asyncServices
				.submit(blockedQueueKey, (Runnable) laterBlockedTaskExecuted::countDown).toCompletableFuture();
		CompletableFuture<Void> independentFuture = this.asyncServices
				.submit(independentQueueKey, (Runnable) independentTaskExecuted::countDown).toCompletableFuture();

		Assert.assertTrue(independentTaskExecuted.await(1, TimeUnit.SECONDS));
		Assert.assertEquals(1, laterBlockedTaskExecuted.getCount());

		releaseBlockedTask.countDown();
		blockedFuture.get(1, TimeUnit.SECONDS);
		laterBlockedFuture.get(1, TimeUnit.SECONDS);
		independentFuture.get(1, TimeUnit.SECONDS);
		Assert.assertEquals(0, laterBlockedTaskExecuted.getCount());
	}

	@Test
	public void dedicatedQueueFlushDoesNotCancelSameMessageKeyOnAnotherQueue() throws Exception {
		String firstQueueKey = "DEDICATED_FLUSH_QUEUE_A";
		String secondQueueKey = "DEDICATED_FLUSH_QUEUE_B";
		String messageKey = "REFRESH";
		CountDownLatch firstQueueStarted = new CountDownLatch(1);
		CountDownLatch releaseFirstQueue = new CountDownLatch(1);
		CountDownLatch secondQueueStarted = new CountDownLatch(1);
		CountDownLatch releaseSecondQueue = new CountDownLatch(1);
		AtomicInteger replacedSameQueueCount = new AtomicInteger();
		AtomicInteger keptOtherQueueCount = new AtomicInteger();
		AtomicInteger latestSameQueueCount = new AtomicInteger();

		CompletableFuture<Void> firstQueueBlocker = this.asyncServices
				.submit(firstQueueKey, this.blockingTask(firstQueueStarted, releaseFirstQueue)).toCompletableFuture();
		CompletableFuture<Void> secondQueueBlocker = this.asyncServices
				.submit(secondQueueKey, this.blockingTask(secondQueueStarted, releaseSecondQueue)).toCompletableFuture();
		Assert.assertTrue(firstQueueStarted.await(1, TimeUnit.SECONDS));
		Assert.assertTrue(secondQueueStarted.await(1, TimeUnit.SECONDS));

		CompletableFuture<Integer> replacedSameQueueFuture = this.asyncServices
				.submit(firstQueueKey, messageKey, this.count(replacedSameQueueCount), false).toCompletableFuture();
		CompletableFuture<Integer> keptOtherQueueFuture = this.asyncServices
				.submit(secondQueueKey, messageKey, this.count(keptOtherQueueCount), false).toCompletableFuture();
		CompletableFuture<Integer> latestSameQueueFuture = this.asyncServices
				.submit(firstQueueKey, messageKey, this.count(latestSameQueueCount), true).toCompletableFuture();

		assertCancelledWith(replacedSameQueueFuture, "replaced by a newer pending task");

		releaseFirstQueue.countDown();
		releaseSecondQueue.countDown();
		firstQueueBlocker.get(1, TimeUnit.SECONDS);
		secondQueueBlocker.get(1, TimeUnit.SECONDS);
		Assert.assertEquals(Integer.valueOf(1), keptOtherQueueFuture.get(1, TimeUnit.SECONDS));
		Assert.assertEquals(Integer.valueOf(1), latestSameQueueFuture.get(1, TimeUnit.SECONDS));
		Assert.assertEquals(0, replacedSameQueueCount.get());
	}

	private Supplier<Integer> count(final AtomicInteger counter) {
		return () -> counter.incrementAndGet();
	}

	private Runnable blockingTask(final CountDownLatch started, final CountDownLatch release) {
		return () -> {
			started.countDown();
			await(release);
		};
	}

	private static void await(final CountDownLatch latch) {
		try {
			latch.await(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting in async test.", e);
		}
	}

	private static void assertCancelledWith(final CompletableFuture<?> future, final String expectedMessage) {
		try {
			future.join();
			Assert.fail("Future should have been cancelled.");
		} catch (java.util.concurrent.CancellationException e) {
			Assert.assertTrue(e.getMessage().contains(expectedMessage));
		} catch (java.util.concurrent.CompletionException e) {
			Assert.assertTrue(e.getCause() instanceof java.util.concurrent.CancellationException);
			Assert.assertTrue(e.getCause().getMessage().contains(expectedMessage));
		}
	}

}
