/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.concurrent.ExecutorService;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.tests.CovalentTests;
import org.opales.covalent.container.tests.unit.support.EventsAsyncWorkerPool;

/**
 * Coverage of the events manager's asynchronous worker pool, which is recycled by
 * {@code stop()} - the pre-stop pool must have actually terminated, per its
 * own {@code isTerminated()} - and a fresh one is usable right away, without
 * needing a separate restart.
 * <p>
 * Scoped to {@code Covalent.main}'s own worker pool via
 * {@link EventsAsyncWorkerPool}, not a JVM-wide thread name search:
 * {@code Covalent.main}'s internal (technical) components, including the
 * events manager, are active from construction and stay active across the
 * whole suite (only briefly replaced by {@code stop()} itself), and other
 * containers used elsewhere in the suite may have worker pools of their
 * own alive at the same time.
 */
public class EventsShutdownContainerizedTests extends CovalentTests {

	private final CovalentContainer cm = Covalent.main;

	@After
	public void stopEventsTestContainer() {
		if (this.cm.isStarted()) {
			this.cm.stop();
		}
	}

	@Test
	public void eventsAsyncWorkerPoolDoesNotSurviveStop() {
		this.cm.start();
		ExecutorService poolBeforeStop = EventsAsyncWorkerPool.currentFor(this.cm);
		Assert.assertFalse("expected the events async worker pool to be live while the container is running",
				poolBeforeStop.isShutdown());

		this.cm.stop();

		// passivate() already blocks (bounded) until the pool has actually
		// terminated, so isTerminated() is expected to be true as soon as
		// stop() returns - no polling needed here.
		Assert.assertTrue("expected the pre-stop events async worker pool to have actually terminated",
				poolBeforeStop.isTerminated());

		// This test exercises stop() itself; restart so tearDown()'s own
		// stop() does not run on an already-stopped container (stop() still
		// throws when the container is not started).
		this.cm.start();
	}

	@Test
	public void eventsAsyncWorkerAcceptsWorkAgainAfterContainerRestart() {
		this.cm.start();
		ExecutorService poolBeforeStop = EventsAsyncWorkerPool.currentFor(this.cm);

		this.cm.stop();

		// stop() already reactivates the internal tree with a fresh pool on
		// its own; start() on top of that must not break anything.
		this.cm.start();

		ExecutorService poolAfterRestart = EventsAsyncWorkerPool.currentFor(this.cm);
		Assert.assertNotSame("expected a genuinely new worker pool after restart, not the pre-stop one",
				poolBeforeStop, poolAfterRestart);
		Assert.assertFalse("expected the worker pool after restart to be usable", poolAfterRestart.isShutdown());
	}

}
