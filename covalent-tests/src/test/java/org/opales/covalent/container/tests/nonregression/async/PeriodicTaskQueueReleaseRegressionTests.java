/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.nonregression.async;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.container.async.ScheduledTask;
import org.opales.covalent.container.async.impl.AsyncManagerImpl;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;
import org.opales.covalent.container.workers.WorkerPoolState;

/**
 * Releasing a queue key cancels the periodic tasks scheduled under it, even
 * when one of their runs is queued on the released pool: that run is
 * cancelled without running, and the task must not be left uncancelled yet
 * never scheduled again.
 */
public class PeriodicTaskQueueReleaseRegressionTests {

	private static final String QUEUE_KEY = "queue";

	private AsyncManagerImpl manager;

	private CapturingLogHandler asyncManagerLogs;

	@Before
	public void setUp() {
		this.asyncManagerLogs = CapturingLogHandler.install(AsyncManagerImpl.class);
		this.manager = new AsyncManagerImpl();
		this.manager.initialize();
		this.manager.activate();
		this.manager.startWorkers();
	}

	@After
	public void tearDown() {
		try {
			this.manager.passivate();
		} finally {
			this.asyncManagerLogs.restore();
		}
	}

	@Test(timeout = 10000)
	public void releasingTheQueueOfAQueuedPeriodicRunCancelsThePeriodicTask() throws Exception {
		CountDownLatch workerBusy = new CountDownLatch(1);
		CountDownLatch releaseWorker = new CountDownLatch(1);
		this.manager.submit(QUEUE_KEY, () -> {
			workerBusy.countDown();
			try {
				releaseWorker.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		workerBusy.await();

		AtomicInteger runs = new AtomicInteger();
		ScheduledTask periodic = this.manager.scheduleWithFixedDelay(QUEUE_KEY, Duration.ZERO,
				Duration.ofMillis(20), runs::incrementAndGet);
		// The first run waits behind the busy worker.
		while (this.queuedTasks() == 0) {
			Thread.onSpinWait();
		}

		this.manager.releaseQueue(QUEUE_KEY);

		Assert.assertTrue("releasing the queue should cancel its periodic tasks", periodic.isCancelled());
		Assert.assertEquals("the queued run should have been cancelled without running", 0, runs.get());
		this.manager.submit(QUEUE_KEY, () -> {
		}).toCompletableFuture().join();
		Assert.assertEquals("a cancelled periodic task should not run on the queue's fresh pool", 0, runs.get());
		releaseWorker.countDown();
	}

	private int queuedTasks() {
		for (WorkerPoolState pool : this.manager.readWorkerPools()) {
			if (QUEUE_KEY.equals(pool.name())) {
				return pool.queuedTasks();
			}
		}
		return 0;
	}
}
