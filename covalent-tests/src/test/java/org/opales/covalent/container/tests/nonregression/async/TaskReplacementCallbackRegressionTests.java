/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.nonregression.async;

import java.util.concurrent.CountDownLatch;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.container.async.impl.AsyncManagerImpl;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;

/**
 * A pending task replaced through {@code flushPending} is cancelled, which runs
 * the callbacks attached to its future. A callback calling back into the
 * manager while another thread reconfigures the pools must not deadlock: the
 * replacement must not complete the cancelled future while it holds the
 * pool's lock.
 */
public class TaskReplacementCallbackRegressionTests {

	private static final String QUEUE_KEY = "queue";

	private static final String MESSAGE_KEY = "replace";

	private AsyncManagerImpl manager;

	private CapturingLogHandler asyncManagerLogs;

	/** Set once every thread of the test has finished, so that passivation cannot wait on a stuck one. */
	private boolean settled;

	@Before
	public void setUp() {
		this.asyncManagerLogs = CapturingLogHandler.install(AsyncManagerImpl.class);
		this.manager = new AsyncManagerImpl();
		this.manager.initialize();
		this.manager.activate();
	}

	@After
	public void tearDown() {
		try {
			if (this.settled) {
				this.manager.passivate();
			}
		} finally {
			this.asyncManagerLogs.restore();
		}
	}

	@Test(timeout = 10000)
	public void cancellationCallbackCallingTheManagerDuringReconfigurationCompletes() throws InterruptedException {
		CountDownLatch workerBusy = new CountDownLatch(1);
		CountDownLatch releaseWorker = new CountDownLatch(1);
		this.manager.submit(QUEUE_KEY, () -> {
			workerBusy.countDown();
			awaitUninterruptibly(releaseWorker);
		});
		workerBusy.await();

		CountDownLatch inCallback = new CountDownLatch(1);
		CountDownLatch callbackMayProceed = new CountDownLatch(1);
		CountDownLatch callbackDone = new CountDownLatch(1);
		this.manager.submit(QUEUE_KEY, MESSAGE_KEY, () -> {
		}, false).whenComplete((result, failure) -> {
			inCallback.countDown();
			awaitUninterruptibly(callbackMayProceed);
			this.manager.readWorkerPools();
			callbackDone.countDown();
		});

		Thread replacing = daemon("task-replacement",
				() -> this.manager.submit(QUEUE_KEY, MESSAGE_KEY, () -> {
				}, true));
		replacing.start();
		inCallback.await();

		CountDownLatch reconfigured = new CountDownLatch(1);
		Thread reconfiguring = daemon("pool-reconfiguration", () -> {
			this.manager.setWorkerQueueCapacity(128);
			reconfigured.countDown();
		});
		reconfiguring.start();
		// Lets the reconfiguration either finish or reach the pool's lock before the callback calls back into
		// the manager: the interleaving that deadlocked when the callback ran under that lock.
		while (reconfigured.getCount() > 0 && reconfiguring.getState() != Thread.State.BLOCKED) {
			Thread.onSpinWait();
		}
		callbackMayProceed.countDown();

		callbackDone.await();
		reconfigured.await();
		replacing.join();
		releaseWorker.countDown();
		this.settled = true;
	}

	private static Thread daemon(final String name, final Runnable body) {
		Thread thread = new Thread(body, name);
		thread.setDaemon(true);
		return thread;
	}

	private static void awaitUninterruptibly(final CountDownLatch latch) {
		boolean interrupted = false;
		while (true) {
			try {
				latch.await();
				break;
			} catch (InterruptedException e) {
				interrupted = true;
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
	}
}
