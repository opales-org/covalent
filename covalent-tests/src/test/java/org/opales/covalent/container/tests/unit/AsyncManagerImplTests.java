/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.container.async.impl.AsyncManagerImpl;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;
import org.opales.covalent.container.workers.WorkerPoolKind;
import org.opales.covalent.container.workers.WorkerPoolState;

/**
 * Verifies {@link AsyncManagerImpl} used on its own: validation and conversion of its parameters, dedicated
 * queues (ordering keys, replacement of pending tasks, full queues), failure reporting of fire-and-forget
 * tasks, pool introspection, and the stop, passivation and reactivation sequences, including a worker that does
 * not honor interruption.
 */
public class AsyncManagerImplTests {

	private static final long TIMEOUT_SECONDS = 10;

	private AsyncManagerImpl manager;

	private CapturingLogHandler asyncManagerLogs;

	@Before
	public void setUp() {
		this.asyncManagerLogs = CapturingLogHandler.install(AsyncManagerImpl.class);
		this.manager = new AsyncManagerImpl();
		this.manager.initialize();
		this.manager.activate();
	}

	@After
	public void tearDown() {
		this.manager.passivate();
		this.asyncManagerLogs.restore();
	}

	private static void await(final CountDownLatch latch) throws InterruptedException {
		assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
	}

	private WorkerPoolState pool(final String name) {
		for (WorkerPoolState state : this.manager.readWorkerPools()) {
			if (state.name().equals(name)) {
				return state;
			}
		}
		throw new AssertionError("no pool named " + name + " in " + this.manager.readWorkerPools());
	}

	/** Occupies the single worker of a dedicated queue until the returned latch is released. */
	private CountDownLatch blockQueue(final String queueKey) throws InterruptedException {
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		this.manager.fireAndForget(queueKey, () -> {
			started.countDown();
			try {
				release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		await(started);
		return release;
	}

	@Test
	public void parametersHaveDocumentedDefaults() {
		assertFalse(this.manager.isDedicatedWorkerIdleReclaimEnabled());
		assertFalse(this.manager.isDedicatedWorkerPoolCapReclaimEnabled());
		assertEquals(Duration.ofMinutes(30), this.manager.getDedicatedWorkerIdleTimeout());
		assertEquals(256, this.manager.getMaxDedicatedWorkerPools());
		assertEquals(1024, this.manager.getWorkerQueueCapacity());
		assertTrue(this.manager.getGeneralWorkerCount() >= 1);
		assertFalse(this.manager.isTaskTracking());
		assertFalse(this.manager.hadIncompleteShutdown());
	}

	@Test
	public void parametersAreStoredAndReadBack() {
		this.manager.setDedicatedWorkerIdleReclaimEnabled(true);
		this.manager.setDedicatedWorkerPoolCapReclaimEnabled(true);
		this.manager.setMaxDedicatedWorkerPools(3);
		this.manager.setDedicatedWorkerIdleTimeout(Duration.ofSeconds(5));
		this.manager.setTaskTracking(true);
		assertTrue(this.manager.isDedicatedWorkerIdleReclaimEnabled());
		assertTrue(this.manager.isDedicatedWorkerPoolCapReclaimEnabled());
		assertEquals(3, this.manager.getMaxDedicatedWorkerPools());
		assertEquals(Duration.ofSeconds(5), this.manager.getDedicatedWorkerIdleTimeout());
		assertTrue(this.manager.isTaskTracking());
	}

	@Test
	public void invalidNumericParametersAreRejectedWithoutChangingTheCurrentValue() {
		int workers = this.manager.getGeneralWorkerCount();
		IllegalArgumentException workerFailure = assertThrows(IllegalArgumentException.class,
				() -> this.manager.setGeneralWorkerCount(0));
		assertEquals("workerCount must be at least 1.", workerFailure.getMessage());
		IllegalArgumentException queueFailure = assertThrows(IllegalArgumentException.class,
				() -> this.manager.setWorkerQueueCapacity(0));
		assertEquals("queueCapacity must be at least 1.", queueFailure.getMessage());
		IllegalArgumentException maxFailure = assertThrows(IllegalArgumentException.class,
				() -> this.manager.setMaxDedicatedWorkerPools(0));
		assertEquals("maxDedicatedWorkerPools must be at least 1.", maxFailure.getMessage());
		IllegalArgumentException timeoutFailure = assertThrows(IllegalArgumentException.class,
				() -> this.manager.setDedicatedWorkerIdleTimeout(Duration.ofSeconds(-1)));
		assertEquals("idleTimeout must not be negative.", timeoutFailure.getMessage());
		assertEquals(workers, this.manager.getGeneralWorkerCount());
		assertEquals(1024, this.manager.getWorkerQueueCapacity());
		assertEquals(256, this.manager.getMaxDedicatedWorkerPools());
		assertEquals(Duration.ofMinutes(30), this.manager.getDedicatedWorkerIdleTimeout());
	}

	@Test
	public void nullIdleTimeoutIsRejected() {
		assertThrows(RuntimeException.class, () -> this.manager.setDedicatedWorkerIdleTimeout((Duration) null));
		assertThrows(RuntimeException.class, () -> this.manager.setDedicatedWorkerIdleTimeout((String) null));
	}

	@Test
	public void idleTimeoutTextIsParsedAsASumOfAmountAndUnitTokens() {
		this.manager.setDedicatedWorkerIdleTimeout("30 mn");
		assertEquals(Duration.ofMinutes(30), this.manager.getDedicatedWorkerIdleTimeout());
		this.manager.setDedicatedWorkerIdleTimeout("3w 1d 6h");
		assertEquals(Duration.ofDays(22).plusHours(6), this.manager.getDedicatedWorkerIdleTimeout());
		this.manager.setDedicatedWorkerIdleTimeout("  2h5mn 7s  ");
		assertEquals(Duration.ofHours(2).plusMinutes(5).plusSeconds(7), this.manager.getDedicatedWorkerIdleTimeout());
		this.manager.setDedicatedWorkerIdleTimeout("1d");
		assertEquals(Duration.ofDays(1), this.manager.getDedicatedWorkerIdleTimeout());
	}

	@Test
	public void malformedIdleTimeoutTextIsRejectedWithAnExplanation() {
		for (String text : List.of("", "   ", "abc", "5", "5 x", "5 mn junk", "mn 5", "-3s")) {
			IllegalArgumentException failure = assertThrows(text, IllegalArgumentException.class,
					() -> this.manager.setDedicatedWorkerIdleTimeout(text));
			assertTrue(failure.getMessage(), failure.getMessage().contains("Invalid duration text \"" + text + "\""));
		}
		assertEquals(Duration.ofMinutes(30), this.manager.getDedicatedWorkerIdleTimeout());
	}

	@Test
	public void submittedSupplierOnADedicatedQueueReturnsItsValue() throws Exception {
		CompletableFuture<String> future = this.manager.submit("queue", () -> "value").toCompletableFuture();
		assertEquals("value", future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
	}

	@Test
	public void submittedRunnableOnADedicatedQueueCompletes() throws Exception {
		CountDownLatch ran = new CountDownLatch(1);
		this.manager.submit("queue", (Runnable) ran::countDown).toCompletableFuture().get(TIMEOUT_SECONDS,
				TimeUnit.SECONDS);
		await(ran);
	}

	@Test
	public void submitWithMessageKeyReturnsValueOnADedicatedQueue() throws Exception {
		CompletableFuture<Integer> future = this.manager.submit("queue", "key", () -> 7, false).toCompletableFuture();
		assertEquals(Integer.valueOf(7), future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
		this.manager.submit("queue", "key", () -> {
		}, false).toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}

	@Test
	public void failureOfASubmittedTaskCompletesTheStageExceptionally() throws Exception {
		IllegalStateException failure = new IllegalStateException("task failed");
		CompletableFuture<Object> future = this.manager.submit("queue", (java.util.function.Supplier<Object>) () -> {
			throw failure;
		}).toCompletableFuture();
		ExecutionException thrown = assertThrows(ExecutionException.class,
				() -> future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
		assertSame(failure, thrown.getCause());
	}

	@Test
	public void tasksOfADedicatedQueueRunOneAtATimeInSubmissionOrder() throws Exception {
		CountDownLatch release = this.blockQueue("ordered");
		StringBuffer order = new StringBuffer();
		CompletableFuture<Void> first = this.manager.submit("ordered", (Runnable) () -> order.append('a'))
				.toCompletableFuture();
		CompletableFuture<Void> second = this.manager.submit("ordered", (Runnable) () -> order.append('b'))
				.toCompletableFuture();
		release.countDown();
		first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		assertEquals("ab", order.toString());
	}

	@Test
	public void flushingPendingTasksCancelsTheOnesWithTheSameMessageKeyOnly() throws Exception {
		CountDownLatch release = this.blockQueue("flush");
		CompletableFuture<Void> replaced = this.manager.submit("flush", "k", () -> {
		}, false).toCompletableFuture();
		CompletableFuture<Void> otherKey = this.manager.submit("flush", "other", () -> {
		}, false).toCompletableFuture();
		CompletableFuture<Void> replacement = this.manager.submit("flush", "k", () -> {
		}, true).toCompletableFuture();
		ExecutionException thrown = assertThrows(ExecutionException.class,
				() -> replaced.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
		assertTrue(thrown.getCause() instanceof CancellationException);
		assertEquals("Async task was replaced by a newer pending task with the same message key.",
				thrown.getCause().getMessage());
		assertEquals(1, this.pool("flush").cancelledTasks());
		release.countDown();
		replacement.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		otherKey.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}

	@Test
	public void fireAndForgetWithKeyReplacesPendingTasksWhenFlushing() throws Exception {
		CountDownLatch release = this.blockQueue("faf");
		AtomicBoolean firstRan = new AtomicBoolean();
		CountDownLatch secondRan = new CountDownLatch(1);
		this.manager.fireAndForget("faf", "k", () -> firstRan.set(true), false);
		this.manager.fireAndForget("faf", "k", secondRan::countDown, true);
		release.countDown();
		await(secondRan);
		assertFalse(firstRan.get());
	}

	@Test
	public void aFullQueueRejectsNewTasksAndCountsThem() throws Exception {
		this.manager.setWorkerQueueCapacity(1);
		CountDownLatch release = this.blockQueue("full");
		this.manager.fireAndForget("full", () -> {
		});
		RejectedExecutionException failure = assertThrows(RejectedExecutionException.class,
				() -> this.manager.fireAndForget("full", () -> {
				}));
		assertEquals("Async worker pool queue is full (capacity 1).", failure.getMessage());
		assertEquals(1, this.pool("full").rejectedTasks());
		assertEquals(1, this.pool("full").queuedTasks());
		release.countDown();
		assertTrue(this.asyncManagerLogs.containsWarning(
				"Async task refused: the worker pool queue is full (capacity 1, pool full). A producer is outpacing "
						+ "its consumers, or a task is stalled."));
	}

	@Test
	public void aRefusedTaskIsLoggedAsAWarningAndRecordedUntilTheNextStart() throws Exception {
		assertFalse(this.manager.hadRejectedTasks());
		this.manager.setWorkerQueueCapacity(1);
		CountDownLatch release = this.blockQueue("refused");
		this.manager.fireAndForget("refused", () -> {
		});
		assertThrows(RejectedExecutionException.class, () -> this.manager.fireAndForget("refused", () -> {
		}));
		assertTrue(this.manager.hadRejectedTasks());
		assertTrue(this.asyncManagerLogs.containsWarning(
				"Async task refused: the worker pool queue is full (capacity 1, pool refused). A producer is outpacing "
						+ "its consumers, or a task is stalled."));
		assertEquals(0, this.pool("refused").cancelledTasks());
		release.countDown();

		this.manager.startWorkers();
		assertFalse(this.manager.hadRejectedTasks());
	}

	@Test
	public void stoppingAPoolLogsTheNumberOfPendingTasksItCancels() throws Exception {
		CountDownLatch release = this.blockQueue("doomed");
		this.manager.fireAndForget("doomed", () -> {
		});
		this.manager.fireAndForget("doomed", () -> {
		});
		this.manager.stopWorkers();
		release.countDown();
		assertTrue(this.asyncManagerLogs
				.containsWarning("2 pending async task(s) cancelled: the doomed worker pool is stopping."));
	}

	@Test
	public void nullArgumentsAreRejected() {
		assertThrows(RuntimeException.class, () -> this.manager.submit((Runnable) null));
		assertThrows(RuntimeException.class, () -> this.manager.submit("q", (Runnable) null));
		assertThrows(RuntimeException.class, () -> this.manager.submit(null, () -> 1));
		assertThrows(RuntimeException.class, () -> this.manager.submit("q", null, () -> 1, false));
		assertThrows(RuntimeException.class, () -> this.manager.fireAndForget(null, () -> {
		}));
		assertThrows(RuntimeException.class, () -> this.manager.releaseQueue(null));
	}

	@Test
	public void unhandledFailureHandlerReceivesTheFailureOfFireAndForgetTasks() throws Exception {
		AtomicReference<String> message = new AtomicReference<>();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		CountDownLatch handled = new CountDownLatch(1);
		this.manager.setUnhandledFailureHandler((m, f) -> {
			message.set(m);
			failure.set(f);
			handled.countDown();
		});
		IllegalStateException boom = new IllegalStateException("boom");
		this.manager.fireAndForget(() -> {
			throw boom;
		});
		await(handled);
		assertEquals("Async task failed.", message.get());
		assertSame(boom, failure.get());
	}

	@Test
	public void unhandledFailureHandlerIsNotCalledForSubmittedTasks() throws Exception {
		AtomicBoolean called = new AtomicBoolean();
		this.manager.setUnhandledFailureHandler((m, f) -> called.set(true));
		CompletableFuture<Void> future = this.manager.submit((Runnable) () -> {
			throw new IllegalStateException("reported through the stage");
		}).toCompletableFuture();
		assertThrows(ExecutionException.class, () -> future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
		assertFalse(called.get());
	}

	@Test
	public void aFailingUnhandledFailureHandlerDoesNotBreakTheWorker() throws Exception {
		CountDownLatch handlerCalled = new CountDownLatch(1);
		this.manager.setUnhandledFailureHandler((m, f) -> {
			handlerCalled.countDown();
			throw new IllegalArgumentException("handler failure");
		});
		this.manager.fireAndForget("handler", () -> {
			throw new IllegalStateException("task failure");
		});
		await(handlerCalled);
		assertEquals("alive", this.manager.submit("handler", () -> "alive").toCompletableFuture().get(TIMEOUT_SECONDS,
				TimeUnit.SECONDS));
		assertEquals(List.of("Unhandled async failure handler failed.", "Async task failed."),
				this.asyncManagerLogs.messages(Level.SEVERE));
		List<Throwable> failures = this.asyncManagerLogs.failures(Level.SEVERE);
		assertTrue(failures.get(0) instanceof IllegalArgumentException);
		assertTrue(failures.get(1) instanceof IllegalStateException);
	}

	@Test
	public void unhandledFailureHandlerIsReadBack() {
		AtomicBoolean unused = new AtomicBoolean();
		org.opales.covalent.container.async.AsyncFailureHandler handler = (m, f) -> unused.set(true);
		this.manager.setUnhandledFailureHandler(handler);
		assertSame(handler, this.manager.getUnhandledFailureHandler());
	}

	@Test
	public void nullUnhandledFailureHandlerRestoresTheDefaultLoggingHandler() throws Exception {
		AtomicBoolean called = new AtomicBoolean();
		this.manager.setUnhandledFailureHandler((m, f) -> called.set(true));
		this.manager.setUnhandledFailureHandler(null);
		assertFalse(this.manager.getUnhandledFailureHandler() == null);
		this.manager.fireAndForget("restore", () -> {
			throw new IllegalStateException("logged only");
		});
		this.manager.submit("restore", () -> 1).toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		assertFalse(called.get());
		assertEquals(List.of("Async task failed."), this.asyncManagerLogs.messages(Level.SEVERE));
	}

	@Test
	public void readWorkerPoolsListsTheGeneralPoolThenTheDedicatedOnesSortedByName() throws Exception {
		this.manager.submit("zeta", () -> 1).toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		this.manager.submit("alpha", () -> 1).toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		List<WorkerPoolState> pools = this.manager.readWorkerPools();
		assertEquals(3, pools.size());
		assertEquals(WorkerPoolKind.GENERAL, pools.get(0).kind());
		assertEquals("general", pools.get(0).name());
		assertEquals("alpha", pools.get(1).name());
		assertEquals(WorkerPoolKind.DEDICATED, pools.get(1).kind());
		assertEquals(1, pools.get(1).targetWorkerCount());
		assertEquals("zeta", pools.get(2).name());
	}

	@Test
	public void releasingAQueueRemovesItsPoolAndAllowsRecreation() throws Exception {
		this.manager.submit("temp", () -> 1).toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		this.manager.releaseQueue("temp");
		assertEquals(1, this.manager.readWorkerPools().size());
		this.manager.releaseQueue("never-created");
		assertEquals(Integer.valueOf(2),
				this.manager.submit("temp", () -> 2).toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
		assertEquals(2, this.manager.readWorkerPools().size());
	}

	@Test
	public void workerCountChangeIsAppliedToTheExistingGeneralPool() throws Exception {
		this.manager.startWorkers();
		this.manager.setGeneralWorkerCount(3);
		assertEquals(3, this.manager.getGeneralWorkerCount());
		assertEquals(3, this.pool("general").targetWorkerCount());
		this.manager.setGeneralWorkerCount(1);
		assertEquals(1, this.pool("general").targetWorkerCount());
		assertEquals("still works",
				this.manager.submit(() -> "still works").toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
	}

	@Test
	public void queueCapacityChangeIsAppliedToGeneralAndDedicatedPools() throws Exception {
		this.manager.submit("sized", () -> 1).toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		this.manager.setWorkerQueueCapacity(17);
		assertEquals(17, this.manager.getWorkerQueueCapacity());
		assertEquals(17, this.pool("general").queueCapacity());
		assertEquals(17, this.pool("sized").queueCapacity());
	}

	@Test
	public void taskTrackingIsReflectedInPoolStates() throws Exception {
		assertFalse(this.pool("general").taskTracking());
		this.manager.setTaskTracking(true);
		CountDownLatch done = new CountDownLatch(1);
		this.manager.submit("tracked", "label", () -> {
			done.countDown();
			return 1;
		}, false).toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		await(done);
		assertTrue(this.pool("tracked").taskTracking());
	}

	@Test
	public void passivationRejectsNewTasksUntilReactivation() throws Exception {
		this.manager.passivate();
		assertThrows(RejectedExecutionException.class, () -> this.manager.fireAndForget(() -> {
		}));
		assertThrows(RejectedExecutionException.class, () -> this.manager.submit("queue", () -> 1));
		assertTrue(this.manager.readWorkerPools().isEmpty());
		this.manager.setGeneralWorkerCount(2);
		this.manager.setWorkerQueueCapacity(5);
		this.manager.activate();
		assertEquals(Integer.valueOf(5),
				this.manager.submit(() -> 5).toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
		assertFalse(this.manager.hadIncompleteShutdown());
	}

	@Test
	public void stopWorkersCancelsPendingTasksAndLeavesAFreshGeneralPool() throws Exception {
		this.manager.setGeneralWorkerCount(1);
		this.manager.startWorkers();
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CompletableFuture<Void> running = this.manager.submit((Runnable) () -> {
			started.countDown();
			try {
				release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}).toCompletableFuture();
		await(started);
		CompletableFuture<Void> pending = this.manager.submit((Runnable) () -> {
		}).toCompletableFuture();
		this.manager.submit("dedicated", () -> 1).toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

		this.manager.stopWorkers();

		ExecutionException thrown = assertThrows(ExecutionException.class,
				() -> pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
		assertTrue(thrown.getCause() instanceof CancellationException);
		assertEquals("Async worker pool stopped before task execution.", thrown.getCause().getMessage());
		running.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		assertFalse(this.manager.hadIncompleteShutdown());
		assertEquals(Integer.valueOf(3),
				this.manager.submit(() -> 3).toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
		assertEquals(1, this.manager.readWorkerPools().size());
		assertTrue(this.asyncManagerLogs
				.containsWarning("1 pending async task(s) cancelled: the general worker pool is stopping."));
	}

	@Test
	public void aWorkerThatIgnoresInterruptionMakesTheShutdownIncomplete() throws Exception {
		CountDownLatch started = new CountDownLatch(1);
		AtomicBoolean finish = new AtomicBoolean();
		this.manager.fireAndForget("stubborn", () -> {
			started.countDown();
			while (!finish.get()) {
				Thread.onSpinWait();
			}
		});
		await(started);
		try {
			this.manager.passivate();
			assertTrue(this.manager.hadIncompleteShutdown());
		} finally {
			finish.set(true);
		}
		this.manager.activate();
		this.manager.passivate();
		assertFalse(this.manager.hadIncompleteShutdown());
		List<String> severe = this.asyncManagerLogs.messages(Level.SEVERE);
		assertEquals(1, severe.size());
		assertTrue(severe.get(0), severe.get(0).startsWith(
				"Async worker covalent-async-stubborn-0 did not terminate within 2000ms of stop(); it is likely running a task"));
	}

	@Test
	public void idleTimeoutTextTooLargeForADurationIsRejectedWithAnExplanation() {
		for (String text : List.of("999999999999999999d", "9999999999999999w", "9223372036854775807w",
				"9223372036854775807s 9223372036854775807s")) {
			IllegalArgumentException failure = assertThrows(text, IllegalArgumentException.class,
					() -> this.manager.setDedicatedWorkerIdleTimeout(text));
			assertTrue(failure.getMessage(), failure.getMessage().contains("\"" + text + "\""));
			assertTrue(failure.getMessage(), failure.getMessage().contains("too large"));
		}
		assertEquals(Duration.ofMinutes(30), this.manager.getDedicatedWorkerIdleTimeout());
	}

	@Test
	public void anEnormousIdleTimeoutDoesNotStopTheReclaimReaperNorThePeriodicTasks() throws Exception {
		this.manager.startWorkers();
		this.manager.setDedicatedWorkerIdleReclaimEnabled(true);
		this.manager.setDedicatedWorkerIdleTimeout(Duration.ofSeconds(Long.MAX_VALUE));
		CountDownLatch created = new CountDownLatch(1);
		this.manager.fireAndForget("enormous-timeout", created::countDown);
		await(created);
		// Let the reaper run at least one reclaim pass (its tick is one second).
		Thread.sleep(2500);

		CountDownLatch ran = new CountDownLatch(1);
		this.manager.scheduleWithFixedDelay(Duration.ZERO, Duration.ofMillis(20), ran::countDown);

		await(ran);
		assertEquals(0, this.asyncManagerLogs.messages(Level.SEVERE).size());
	}
}
