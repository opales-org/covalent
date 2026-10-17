/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
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

/**
 * Dedicated per-queue-key worker pools must not
 * accumulate forever (automatic idle-timeout and least-recently-used
 * reclaim, plus explicit release), and the pending-task-by-message-key
 * index must not grow one empty entry per message key that is only ever
 * used once.
 */
public class AsyncReclaimContainerizedTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	private boolean originalIdleReclaimEnabled;

	private boolean originalCapReclaimEnabled;

	private Duration originalIdleTimeout;

	private int originalMaxDedicatedWorkerPools;

	@Before
	public void setUp() {
		Covalent.main.rcs().registerComponent(this);
		AsyncParameters parameters = this.accessAsyncParameters();
		this.originalIdleReclaimEnabled = parameters.isDedicatedWorkerIdleReclaimEnabled();
		this.originalCapReclaimEnabled = parameters.isDedicatedWorkerPoolCapReclaimEnabled();
		this.originalIdleTimeout = parameters.getDedicatedWorkerIdleTimeout();
		this.originalMaxDedicatedWorkerPools = parameters.getMaxDedicatedWorkerPools();
	}

	@After
	public void tearDown() {
		AsyncParameters parameters = this.accessAsyncParameters();
		parameters.setDedicatedWorkerIdleReclaimEnabled(this.originalIdleReclaimEnabled);
		parameters.setDedicatedWorkerPoolCapReclaimEnabled(this.originalCapReclaimEnabled);
		parameters.setDedicatedWorkerIdleTimeout(this.originalIdleTimeout);
		parameters.setMaxDedicatedWorkerPools(this.originalMaxDedicatedWorkerPools);
	}

	@Test
	public void idleDedicatedPoolIsReclaimedAfterConfiguredTimeout() throws Exception {
		AsyncParameters parameters = this.accessAsyncParameters();
		parameters.setDedicatedWorkerIdleTimeout("2 s");
		parameters.setDedicatedWorkerIdleReclaimEnabled(true);
		AsyncServices asyncServices = this.useAsyncServices();
		String queueKey = "ASYNC_RECLAIM_IDLE_TIMEOUT";

		CountDownLatch executed = new CountDownLatch(1);
		asyncServices.submit(queueKey, (Runnable) executed::countDown).toCompletableFuture().get(1, TimeUnit.SECONDS);
		Assert.assertTrue(executed.await(1, TimeUnit.SECONDS));
		Assert.assertEquals(1, countDedicatedThreads(queueKey));

		awaitNoDedicatedThreads(queueKey, 5);
		Assert.assertEquals("dedicated pool must be reclaimed once idle past the configured timeout", 0,
				countDedicatedThreads(queueKey));
	}

	@Test
	public void activeDedicatedPoolIsNotReclaimedWhileWithinTimeout() throws Exception {
		AsyncParameters parameters = this.accessAsyncParameters();
		parameters.setDedicatedWorkerIdleTimeout("30 mn");
		parameters.setDedicatedWorkerIdleReclaimEnabled(true);
		AsyncServices asyncServices = this.useAsyncServices();
		String queueKey = "ASYNC_RECLAIM_NOT_YET_IDLE";

		try {
			asyncServices.submit(queueKey, (Runnable) () -> {
			}).toCompletableFuture().get(1, TimeUnit.SECONDS);
			Thread.sleep(1500); // longer than the reclaim tick, far shorter than the 30 mn timeout

			Assert.assertEquals("a recently-active dedicated pool must not be reclaimed before its idle timeout", 1,
					countDedicatedThreads(queueKey));
		} finally {
			asyncServices.releaseQueue(queueKey);
		}
	}

	@Test
	public void explicitReleaseStopsDedicatedPoolImmediately() throws Exception {
		AsyncServices asyncServices = this.useAsyncServices();
		String queueKey = "ASYNC_RELEASE_EXPLICIT";

		asyncServices.submit(queueKey, (Runnable) () -> {
		}).toCompletableFuture().get(1, TimeUnit.SECONDS);
		Assert.assertEquals(1, countDedicatedThreads(queueKey));

		asyncServices.releaseQueue(queueKey);

		awaitNoDedicatedThreads(queueKey, 2);
		Assert.assertEquals(0, countDedicatedThreads(queueKey));
	}

	@Test
	public void releaseQueueOnUnknownKeyIsANoOp() {
		this.useAsyncServices().releaseQueue("ASYNC_RELEASE_NEVER_USED");
	}

	@Test
	public void queueUsedAgainAfterReleaseGetsAFreshPool() throws Exception {
		AsyncServices asyncServices = this.useAsyncServices();
		String queueKey = "ASYNC_RELEASE_THEN_REUSE";

		try {
			asyncServices.submit(queueKey, (Runnable) () -> {
			}).toCompletableFuture().get(1, TimeUnit.SECONDS);
			asyncServices.releaseQueue(queueKey);
			awaitNoDedicatedThreads(queueKey, 2);

			CountDownLatch executed = new CountDownLatch(1);
			asyncServices.submit(queueKey, (Runnable) executed::countDown).toCompletableFuture().get(1,
					TimeUnit.SECONDS);
			Assert.assertTrue(executed.await(1, TimeUnit.SECONDS));
		} finally {
			asyncServices.releaseQueue(queueKey);
		}
	}

	@Test
	public void leastRecentlyUsedDedicatedPoolIsReclaimedOnceCapIsExceeded() throws Exception {
		AsyncParameters parameters = this.accessAsyncParameters();
		parameters.setMaxDedicatedWorkerPools(2);
		parameters.setDedicatedWorkerPoolCapReclaimEnabled(true);
		AsyncServices asyncServices = this.useAsyncServices();
		String oldestKey = "ASYNC_LRU_OLDEST";
		String middleKey = "ASYNC_LRU_MIDDLE";
		String newestKey = "ASYNC_LRU_NEWEST";

		try {
			asyncServices.submit(oldestKey, (Runnable) () -> {
			}).toCompletableFuture().get(1, TimeUnit.SECONDS);
			Thread.sleep(50);
			asyncServices.submit(middleKey, (Runnable) () -> {
			}).toCompletableFuture().get(1, TimeUnit.SECONDS);
			Assert.assertEquals(2, countDedicatedThreads(oldestKey) + countDedicatedThreads(middleKey));

			// a third distinct key pushes the live dedicated pool count to 3, past cap 2.
			asyncServices.submit(newestKey, (Runnable) () -> {
			}).toCompletableFuture().get(1, TimeUnit.SECONDS);

			awaitNoDedicatedThreads(oldestKey, 3);
			Assert.assertEquals("the least-recently-active pool must be reclaimed first", 0,
					countDedicatedThreads(oldestKey));
			Assert.assertEquals(1, countDedicatedThreads(middleKey));
			Assert.assertEquals(1, countDedicatedThreads(newestKey));
		} finally {
			asyncServices.releaseQueue(oldestKey);
			asyncServices.releaseQueue(middleKey);
			asyncServices.releaseQueue(newestKey);
		}
	}

	@Test
	public void busyDedicatedPoolIsNotEvictedToEnforceTheCap() throws Exception {
		AsyncParameters parameters = this.accessAsyncParameters();
		parameters.setMaxDedicatedWorkerPools(1);
		parameters.setDedicatedWorkerPoolCapReclaimEnabled(true);
		AsyncServices asyncServices = this.useAsyncServices();
		String busyKey = "ASYNC_LRU_BUSY_NOT_EVICTED";
		String otherKey = "ASYNC_LRU_OTHER";

		CountDownLatch busyStarted = new CountDownLatch(1);
		CountDownLatch releaseBusy = new CountDownLatch(1);
		try {
			asyncServices.submit(busyKey, this.blockingTask(busyStarted, releaseBusy));
			Assert.assertTrue(busyStarted.await(1, TimeUnit.SECONDS));

			asyncServices.submit(otherKey, (Runnable) () -> {
			}).toCompletableFuture().get(1, TimeUnit.SECONDS);
			Thread.sleep(1500); // let the reaper run a few ticks while busyKey is still executing

			Assert.assertEquals("a pool with a task still running must not be evicted", 1,
					countDedicatedThreads(busyKey));
		} finally {
			releaseBusy.countDown();
			asyncServices.releaseQueue(busyKey);
			asyncServices.releaseQueue(otherKey);
		}
	}

	@Test
	public void workerThreadsAreDaemonThreads() throws Exception {
		// Worker threads are daemon threads, as the reclaim reaper is. A caller that
		// starts a container and lets its own thread run to completion without ever
		// calling stop() must not see the JVM hang forever: normal JVM exit waits for
		// every non-daemon thread to terminate and nothing else ever terminates these.
		AsyncServices asyncServices = this.useAsyncServices();
		String queueKey = "ASYNC_DAEMON_CHECK";

		try {
			asyncServices.submit(queueKey, (Runnable) () -> {
			}).toCompletableFuture().get(1, TimeUnit.SECONDS);

			for (Thread worker : this.dedicatedPoolWorkers(queueKey)) {
				Assert.assertTrue("worker thread " + worker.getName() + " must be a daemon thread", worker.isDaemon());
			}
		} finally {
			asyncServices.releaseQueue(queueKey);
		}
	}

	@Test
	public void reclaimNeverInterruptsATaskAlreadyClaimedByAWorker() throws Exception {
		// Race between a worker dequeuing a task and recording it as active, versus
		// tryReclaimIfIdleSince()'s idle check: a reclaim landing in that window could
		// see an empty queue and zero active tasks - even though a worker had already
		// claimed the task and was about to run it - and interrupt the pool's worker
		// threads out from under it. Drives many submit()/reclaim-attempt pairs back to
		// back, as tightly as possible, to try to hit that window; since dequeue and
		// claim happen atomically under the pool's own monitor, no task may ever
		// observe itself started with its
		// interrupted flag already set, and every submitted task must actually
		// run to completion (none may be silently dropped by a spurious
		// reclaim).
		AsyncServices asyncServices = this.useAsyncServices();
		String queueKey = "ASYNC_RECLAIM_RACE";
		int attempts = 500;

		try {
			CopyOnWriteArrayList<Boolean> startedInterrupted = new CopyOnWriteArrayList<>();
			CountDownLatch allCompleted = new CountDownLatch(attempts);

			for (int i = 0; i < attempts; i++) {
				asyncServices.submit(queueKey, (Runnable) () -> {
					startedInterrupted.add(Thread.currentThread().isInterrupted());
					allCompleted.countDown();
				});
				// Mimic an aggressive reclaim policy (the least-recently-used cap
				// reclaim passes "now" as the cutoff, i.e. "idle right this
				// instant") racing the worker that just picked up the task above.
				this.tryReclaimIfIdleSince(queueKey, System.currentTimeMillis());
			}

			Assert.assertTrue("expected every submitted task to complete, none silently dropped by a reclaim",
					allCompleted.await(10, TimeUnit.SECONDS));
			Assert.assertFalse(
					"a task must never observe itself started with its interrupted flag already set "
							+ "(a reclaim interrupted the worker after it had already claimed the task)",
					startedInterrupted.contains(Boolean.TRUE));
		} finally {
			asyncServices.releaseQueue(queueKey);
		}
	}

	@Test
	public void pendingMessageKeyIndexDoesNotGrowUnboundedlyAsMessagesComplete() throws Exception {
		AsyncServices asyncServices = this.useAsyncServices();
		String queueKey = "ASYNC_PENDING_INDEX_CLEANUP";

		try {
			for (int i = 0; i < 50; i++) {
				asyncServices.submit(queueKey, "message-" + i, (Runnable) () -> {
				}, false).toCompletableFuture().get(1, TimeUnit.SECONDS);
			}

			Assert.assertEquals("completed message keys must not leave empty pending-task index entries behind", 0,
					this.pendingTasksByMessageKeyIndexSize(queueKey));
		} finally {
			asyncServices.releaseQueue(queueKey);
		}
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

	private AsyncManagerImpl asyncManagerImpl() throws Exception {
		Field field = org.opales.covalent.container.impl.CovalentContainerImpl.class
				.getDeclaredField("asyncManagerImpl");
		field.setAccessible(true);
		return (AsyncManagerImpl) field.get(this.cm);
	}

	private int pendingTasksByMessageKeyIndexSize(final String queueKey) throws Exception {
		Field dedicatedWorkerPoolsField = AsyncManagerImpl.class.getDeclaredField("dedicatedWorkerPools");
		dedicatedWorkerPoolsField.setAccessible(true);
		Map<?, ?> dedicatedWorkerPools = (Map<?, ?>) dedicatedWorkerPoolsField.get(this.asyncManagerImpl());
		Object pool = dedicatedWorkerPools.get(queueKey);
		Assert.assertNotNull("expected a live dedicated pool for " + queueKey, pool);
		Field pendingField = pool.getClass().getDeclaredField("pendingTasksByMessageKey");
		pendingField.setAccessible(true);
		return ((Map<?, ?>) pendingField.get(pool)).size();
	}

	@SuppressWarnings("unchecked")
	private java.util.List<Thread> dedicatedPoolWorkers(final String queueKey) throws Exception {
		Field dedicatedWorkerPoolsField = AsyncManagerImpl.class.getDeclaredField("dedicatedWorkerPools");
		dedicatedWorkerPoolsField.setAccessible(true);
		Map<?, ?> dedicatedWorkerPools = (Map<?, ?>) dedicatedWorkerPoolsField.get(this.asyncManagerImpl());
		Object pool = dedicatedWorkerPools.get(queueKey);
		Assert.assertNotNull("expected a live dedicated pool for " + queueKey, pool);
		Field workersField = pool.getClass().getDeclaredField("workers");
		workersField.setAccessible(true);
		return (java.util.List<Thread>) workersField.get(pool);
	}

	private void tryReclaimIfIdleSince(final String queueKey, final long cutoffMillis) throws Exception {
		Field dedicatedWorkerPoolsField = AsyncManagerImpl.class.getDeclaredField("dedicatedWorkerPools");
		dedicatedWorkerPoolsField.setAccessible(true);
		Map<?, ?> dedicatedWorkerPools = (Map<?, ?>) dedicatedWorkerPoolsField.get(this.asyncManagerImpl());
		Object pool = dedicatedWorkerPools.get(queueKey);
		if (pool == null) {
			// Already reclaimed by an earlier iteration of the caller's loop, or
			// not created yet - either way, nothing to try reclaiming right now.
			return;
		}
		Method reclaim = pool.getClass().getDeclaredMethod("tryReclaimIfIdleSince", long.class);
		reclaim.setAccessible(true);
		reclaim.invoke(pool, cutoffMillis);
	}

	private static long countDedicatedThreads(final String queueKey) {
		String prefix = "covalent-async-" + queueKey + "-";
		return Thread.getAllStackTraces().keySet().stream().filter(thread -> thread.getName().startsWith(prefix))
				.count();
	}

	private static void awaitNoDedicatedThreads(final String queueKey, final int timeoutSeconds)
			throws InterruptedException {
		long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
		while (countDedicatedThreads(queueKey) > 0 && System.nanoTime() < deadlineNanos) {
			Thread.sleep(20);
		}
	}

}
