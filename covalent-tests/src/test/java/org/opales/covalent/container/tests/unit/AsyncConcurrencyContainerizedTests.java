/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.async.AsyncServices;
import org.opales.covalent.container.tests.CovalentContainerizedTests;

/**
 * Focused concurrency tests for the async worker pools, pinning
 * down their actual (independently thread-safe, non-serialized) concurrency
 * contract - see the class Javadoc on {@link AsyncServices} - rather than
 * mirroring {@code LifecycleConcurrencyTests}' serialization assertions,
 * which do not apply here.
 */
public class AsyncConcurrencyContainerizedTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	@Before
	public void registerTestInstanceAsRawComponent() {
		Covalent.main.rcs().registerComponent(this);
	}

	@Test
	public void tasksUnderTheSameQueueKeyNeverRunConcurrently() throws Exception {
		AsyncServices asyncServices = this.useAsyncServices();
		String queueKey = "ASYNC_CONCURRENCY_SAME_KEY";
		int taskCount = 50;
		AtomicBoolean currentlyRunning = new AtomicBoolean(false);
		AtomicInteger violations = new AtomicInteger();
		CountDownLatch allSubmitted = new CountDownLatch(taskCount);
		CountDownLatch allCompleted = new CountDownLatch(taskCount);
		ExecutorService submitters = Executors.newFixedThreadPool(8);
		try {
			for (int i = 0; i < taskCount; i++) {
				submitters.submit(() -> {
					asyncServices.submit(queueKey, (Runnable) () -> {
						if (!currentlyRunning.compareAndSet(false, true)) {
							violations.incrementAndGet();
						}
						LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(2));
						if (!currentlyRunning.compareAndSet(true, false)) {
							violations.incrementAndGet();
						}
						allCompleted.countDown();
					});
					allSubmitted.countDown();
				});
			}
			Assert.assertTrue(allSubmitted.await(5, TimeUnit.SECONDS));
			Assert.assertTrue(allCompleted.await(5, TimeUnit.SECONDS));
			Assert.assertEquals("no two tasks under the same queue key may run concurrently", 0, violations.get());
		} finally {
			submitters.shutdownNow();
			asyncServices.releaseQueue(queueKey);
		}
	}

	@Test
	public void concurrentFirstUseOfANewQueueKeyCreatesExactlyOneDedicatedThread() throws Exception {
		AsyncServices asyncServices = this.useAsyncServices();
		String queueKey = "ASYNC_CONCURRENCY_CREATION_RACE";
		int racerCount = 30;
		CountDownLatch ready = new CountDownLatch(racerCount);
		CountDownLatch go = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(racerCount);
		List<CompletableFuture<Void>> submissions = Collections.synchronizedList(new ArrayList<>());
		ExecutorService racers = Executors.newFixedThreadPool(racerCount);
		try {
			for (int i = 0; i < racerCount; i++) {
				racers.submit(() -> {
					ready.countDown();
					try {
						go.await(5, TimeUnit.SECONDS);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					submissions.add(asyncServices.submit(queueKey, (Runnable) () -> {
					}).toCompletableFuture());
					done.countDown();
				});
			}
			Assert.assertTrue(ready.await(2, TimeUnit.SECONDS));
			go.countDown();
			Assert.assertTrue(done.await(5, TimeUnit.SECONDS));

			// Each submission's own future only completes once its task has
			// actually run on the dedicated worker thread, so waiting on all of
			// them is a genuine resynchronization with thread creation - no
			// need to poll countDedicatedThreads() on a timer.
			for (CompletableFuture<Void> submission : submissions) {
				submission.get(5, TimeUnit.SECONDS);
			}
			Assert.assertEquals("exactly one dedicated thread must be created for a racing first use", 1,
					countDedicatedThreads(queueKey));
		} finally {
			racers.shutdownNow();
			asyncServices.releaseQueue(queueKey);
		}
	}

	@Test
	public void stopDuringConcurrentSubmissionLeavesNoTaskInLimbo() throws Exception {
		AsyncServices asyncServices = this.useAsyncServices();
		int submitterCount = 8;
		int perSubmitter = 20;
		List<CompletableFuture<Void>> allFutures = Collections.synchronizedList(new ArrayList<>());
		ExecutorService submitters = Executors.newFixedThreadPool(submitterCount);
		try {
			List<Future<?>> submitterFutures = new ArrayList<>();
			for (int i = 0; i < submitterCount; i++) {
				submitterFutures.add(submitters.submit(() -> {
					for (int j = 0; j < perSubmitter; j++) {
						try {
							allFutures.add(asyncServices.submit((Runnable) () -> {
							}).toCompletableFuture());
						} catch (RejectedExecutionException | IllegalStateException expectedOnceStopped) {
							// acceptable once the manager has stopped mid-loop
						}
					}
				}));
			}
			Thread.sleep(10);
			this.cm.stop();
			for (Future<?> submitterFuture : submitterFutures) {
				submitterFuture.get(5, TimeUnit.SECONDS);
			}
			this.cm.start();

			for (CompletableFuture<Void> future : allFutures) {
				try {
					future.get(2, TimeUnit.SECONDS);
				} catch (CancellationException | ExecutionException expectedTerminalState) {
					// a task cancelled by stop() (or, unexpectedly, failed) still reaches a
					// terminal state - that is what this test actually verifies: no hang.
				}
			}
		} finally {
			submitters.shutdownNow();
		}
	}

	private AsyncServices useAsyncServices() {
		return Covalent.main.rcs().useServices(AsyncServices.class);
	}

	private static long countDedicatedThreads(final String queueKey) {
		String prefix = "covalent-async-" + queueKey + "-";
		return Thread.getAllStackTraces().keySet().stream().filter(thread -> thread.getName().startsWith(prefix))
				.count();
	}

}
