/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.async.impl.AsyncManagerImpl;
import org.opales.covalent.container.tests.CovalentContainerizedTests;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.async.AsyncParameters;
import org.opales.covalent.container.async.AsyncServices;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;

public class AsyncShutdownContainerizedTests extends CovalentContainerizedTests {

	private CapturingLogHandler asyncManagerLogs;

	private final CovalentContainer cm = Covalent.main;

	@Before
	public void setUp() {
		this.asyncManagerLogs = CapturingLogHandler.install(AsyncManagerImpl.class);
		Covalent.main.rcs().registerComponent(this);
		this.accessAsyncParameters().setUnhandledFailureHandler(null);
		this.cm.getParameters().setSynchronousEvents(true);
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
	}

	@After
	public void tearDown() {
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.trace);

		this.asyncManagerLogs.restore();
	}

	@Test
	public void stoppedContainerInvalidatesAsyncServicesProxy() {
		AsyncServices asyncServices = this.useAsyncServices();

		this.cm.stop();

		Assert.assertThrows(IllegalStateException.class,
				() -> asyncServices.submit((Runnable) () -> Assert.fail("Task must not run.")));

		// This test exercises stop() itself; restart so the base class's own,
		// unconditional stop() below does not run on an already-stopped
		// container.
		this.cm.start();
	}

	@Test
	public void asyncManagerAcceptsTasksAgainAfterContainerRestart() throws Exception {
		AsyncServices stoppedAsyncServices = this.useAsyncServices();

		this.cm.stop();
		Assert.assertThrows(IllegalStateException.class,
				() -> stoppedAsyncServices.submit((Runnable) () -> Assert.fail("Task must not run.")));

		this.cm.start();
		Covalent.main.rcs().registerComponent(this);

		AsyncServices asyncServices = this.useAsyncServices();
		CountDownLatch executed = new CountDownLatch(1);
		CompletableFuture<Void> future = asyncServices.submit((Runnable) executed::countDown).toCompletableFuture();

		Assert.assertTrue(executed.await(1, TimeUnit.SECONDS));
		future.get(1, TimeUnit.SECONDS);
	}

	@Test
	public void dedicatedWorkerContinuesAfterTaskFailure() throws Exception {
		AsyncServices asyncServices = this.useAsyncServices();
		String workerKey = "ASYNC_FAILURE_RECOVERY";
		CountDownLatch laterTaskExecuted = new CountDownLatch(1);

		CompletableFuture<Void> failingFuture = asyncServices.submit(workerKey, (Runnable) () -> {
			throw new IllegalStateException("expected async failure");
		}).toCompletableFuture();
		CompletableFuture<Void> laterFuture = asyncServices.submit(workerKey, (Runnable) laterTaskExecuted::countDown)
				.toCompletableFuture();

		Assert.assertTrue(laterTaskExecuted.await(1, TimeUnit.SECONDS));
		laterFuture.get(1, TimeUnit.SECONDS);
		assertFailedWith(failingFuture, IllegalStateException.class, "expected async failure");
	}

	@Test
	public void fireAndForgetFailureIsReportedToGlobalHandler() throws InterruptedException {
		AsyncServices asyncServices = this.useAsyncServices();
		AsyncParameters asyncParameters = this.accessAsyncParameters();
		CountDownLatch failureReported = new CountDownLatch(1);
		AtomicInteger failureCount = new AtomicInteger();

		asyncParameters.setUnhandledFailureHandler((message, failure) -> {
			Assert.assertTrue(message.contains("Async task failed."));
			Assert.assertTrue(failure.getMessage().contains("expected unhandled failure"));
			failureCount.incrementAndGet();
			failureReported.countDown();
		});

		asyncServices.fireAndForget(() -> {
			throw new IllegalStateException("expected unhandled failure");
		});

		Assert.assertTrue(failureReported.await(1, TimeUnit.SECONDS));
		Assert.assertEquals(1, failureCount.get());
	}

	@Test
	public void dedicatedFireAndForgetFailureIsReportedAndWorkerContinues() throws Exception {
		AsyncServices asyncServices = this.useAsyncServices();
		AsyncParameters asyncParameters = this.accessAsyncParameters();
		String queueKey = "ASYNC_DEDICATED_FIRE_AND_FORGET_FAILURE";
		CountDownLatch failureReported = new CountDownLatch(1);
		CountDownLatch laterTaskExecuted = new CountDownLatch(1);
		AtomicInteger failureCount = new AtomicInteger();

		asyncParameters.setUnhandledFailureHandler((message, failure) -> {
			Assert.assertTrue(message.contains("Async task failed."));
			Assert.assertTrue(failure.getMessage().contains("expected dedicated unhandled failure"));
			failureCount.incrementAndGet();
			failureReported.countDown();
		});

		asyncServices.fireAndForget(queueKey, () -> {
			throw new IllegalStateException("expected dedicated unhandled failure");
		});
		CompletableFuture<Void> laterFuture = asyncServices.submit(queueKey, (Runnable) laterTaskExecuted::countDown)
				.toCompletableFuture();

		Assert.assertTrue(failureReported.await(1, TimeUnit.SECONDS));
		Assert.assertTrue(laterTaskExecuted.await(1, TimeUnit.SECONDS));
		laterFuture.get(1, TimeUnit.SECONDS);
		Assert.assertEquals(1, failureCount.get());
	}

	@Test
	public void submittedTaskFailureDoesNotInvokeGlobalHandler() {
		AsyncServices asyncServices = this.useAsyncServices();
		AsyncParameters asyncParameters = this.accessAsyncParameters();
		AtomicInteger globalFailureCount = new AtomicInteger();

		asyncParameters.setUnhandledFailureHandler((message, failure) -> globalFailureCount.incrementAndGet());

		CompletableFuture<Void> future = asyncServices.submit((Runnable) () -> {
			throw new IllegalStateException("expected submitted failure");
		}).toCompletableFuture();

		assertFailedWith(future, IllegalStateException.class, "expected submitted failure");
		Assert.assertEquals(0, globalFailureCount.get());
	}

	@Test
	public void supplierFailureCompletesFutureExceptionally() {
		AsyncServices asyncServices = this.useAsyncServices();

		CompletableFuture<Integer> future = asyncServices.submit((Supplier<Integer>) () -> {
			throw new IllegalStateException("expected supplier failure");
		}).toCompletableFuture();

		assertFailedWith(future, IllegalStateException.class, "expected supplier failure");
	}

	@Test
	public void stopBlocksUntilAsyncWorkerThreadsHaveActuallyTerminated() throws Exception {
		// stop() blocks, bounded, until every worker thread has actually terminated
		// (see AsyncManagerImpl.CovalentWorkerPool.awaitWorkersTerminated()), instead of
		// only requesting interruption (Thread.interrupt()) and returning at once: a
		// caller must not observe stop() as "done" while a worker thread is still alive
		// for a few more milliseconds. So this assertion needs no poll at all:
		// the threads must already be gone the instant stop() returns.
		//
		// The task below deliberately does a small amount of work (a short
		// sleep) AFTER being interrupted, instead of exiting immediately: a
		// task with nothing left to do post-interruption dies fast enough on
		// its own that this assertion can pass "by luck" even without the
		// join fix, since interrupt() to actual thread death is normally
		// only microseconds. Widening that window is what makes this test
		// actually exercise the guarantee instead of getting lucky with
		// scheduling.
		AsyncServices asyncServices = this.useAsyncServices();
		String queueKey = "ASYNC_STOP_BLOCKS_UNTIL_TERMINATED";
		String threadPrefix = "covalent-async-" + queueKey + "-";
		CountDownLatch taskStarted = new CountDownLatch(1);
		long postInterruptWorkMillis = 300L;

		asyncServices.submit(queueKey, (Runnable) () -> {
			taskStarted.countDown();
			try {
				Thread.sleep(10_000);
			} catch (InterruptedException interrupted) {
				try {
					Thread.sleep(postInterruptWorkMillis);
				} catch (InterruptedException ignored) {
					Thread.currentThread().interrupt();
				}
			}
		});
		Assert.assertTrue(taskStarted.await(1, TimeUnit.SECONDS));
		Assert.assertEquals(1, countThreadsWithPrefix(threadPrefix));

		this.cm.stop();

		Assert.assertEquals("worker thread must already be gone the instant stop() returns, "
				+ "even though its task took " + postInterruptWorkMillis + "ms to actually exit after interruption",
				0, countThreadsWithPrefix(threadPrefix));

		this.cm.start();
	}

	@Test
	public void stopCancelsPendingGeneralTasks() throws Exception {
		// The general pool's thread count is configurable, and its default depends on the running machine's core count, so this test pins it to
		// a known value
		// instead of assuming a fixed number of worker threads.
		int generalWorkerCount = 3;
		AsyncParameters asyncParameters = this.accessAsyncParameters();
		int originalGeneralWorkerCount = asyncParameters.getGeneralWorkerCount();
		asyncParameters.setGeneralWorkerCount(generalWorkerCount);
		this.cm.stop();
		this.cm.start();
		Covalent.main.rcs().registerComponent(this);
		try {
			AsyncServices asyncServices = this.useAsyncServices();
			CountDownLatch runningTasksStarted = new CountDownLatch(generalWorkerCount);
			CountDownLatch releaseRunningTasks = new CountDownLatch(1);

			for (int i = 0; i < generalWorkerCount; i++) {
				asyncServices.submit(this.blockingTask(runningTasksStarted, releaseRunningTasks));
			}
			Assert.assertTrue(runningTasksStarted.await(1, TimeUnit.SECONDS));

			CompletableFuture<Void> pendingFuture = asyncServices.submit((Runnable) () -> {
				Assert.fail("Pending task must be cancelled on stop.");
			}).toCompletableFuture();

			this.cm.stop();
			releaseRunningTasks.countDown();

			assertCancelledWith(pendingFuture, "stopped before task execution");
			Assert.assertTrue(this.asyncManagerLogs
					.containsWarning("1 pending async task(s) cancelled: the general worker pool is stopping."));
		} finally {
			// the container was stopped inside the try block, which invalidates the
			// AsyncParameters proxy obtained before it - restart first, then resolve a
			// fresh proxy before restoring the original worker count.
			this.cm.start();
			Covalent.main.rcs().registerComponent(this);
			this.accessAsyncParameters().setGeneralWorkerCount(originalGeneralWorkerCount);
		}
	}

	@Test
	public void stopCancelsPendingDedicatedTasks() throws Exception {
		AsyncServices asyncServices = this.useAsyncServices();
		String queueKey = "DEDICATED_STOP_QUEUE";
		CountDownLatch firstTaskStarted = new CountDownLatch(1);
		CountDownLatch releaseFirstTask = new CountDownLatch(1);

		asyncServices.submit(queueKey, this.blockingTask(firstTaskStarted, releaseFirstTask));
		Assert.assertTrue(firstTaskStarted.await(1, TimeUnit.SECONDS));

		CompletableFuture<Void> pendingFuture = asyncServices.submit(queueKey, (Runnable) () -> {
			Assert.fail("Pending dedicated task must be cancelled on stop.");
		}).toCompletableFuture();

		this.cm.stop();
		releaseFirstTask.countDown();

		assertCancelledWith(pendingFuture, "stopped before task execution");
		Assert.assertTrue(this.asyncManagerLogs
				.containsWarning("1 pending async task(s) cancelled: the DEDICATED_STOP_QUEUE worker pool is stopping."));

		// This test exercises stop() itself; restart so the base class's own,
		// unconditional stop() below does not run on an already-stopped
		// container.
		this.cm.start();
	}

	private Runnable blockingTask(final CountDownLatch started, final CountDownLatch release) {
		return () -> {
			started.countDown();
			try {
				release.await(5, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new CancellationException("Async task was interrupted.");
			}
		};
	}

	private AsyncServices useAsyncServices() {
		return Covalent.main.rcs().useServices(AsyncServices.class);
	}

	private AsyncParameters accessAsyncParameters() {
		return Covalent.main.rcs().accessParameters(AsyncParameters.class);
	}

	private static void assertFailedWith(final CompletableFuture<?> future, final Class<?> expectedType,
			final String expectedMessage) {
		CompletionException e = Assert.assertThrows("Future should have failed.", CompletionException.class,
				() -> future.join());
		Assert.assertTrue(expectedType.isInstance(e.getCause()));
		Assert.assertTrue(e.getCause().getMessage().contains(expectedMessage));
	}

	private static long countThreadsWithPrefix(final String prefix) {
		return Thread.getAllStackTraces().keySet().stream().filter(thread -> thread.getName().startsWith(prefix))
				.count();
	}

	private static void assertCancelledWith(final CompletableFuture<?> future, final String expectedMessage) {
		try {
			future.join();
			Assert.fail("Future should have been cancelled.");
		} catch (CancellationException e) {
			Assert.assertTrue(e.getMessage().contains(expectedMessage));
		} catch (CompletionException e) {
			Assert.assertTrue(e.getCause() instanceof CancellationException);
			Assert.assertTrue(e.getCause().getMessage().contains(expectedMessage));
		}
	}

}
