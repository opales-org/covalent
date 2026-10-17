/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.model;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import org.opales.covalent.container.workers.WorkerPoolKind;
import org.opales.covalent.supervision.threads.model.ThreadsSnapshot.Pool;
import org.opales.covalent.supervision.threads.model.ThreadsSnapshot.Tuning;
import org.opales.covalent.supervision.threads.model.ThreadsSnapshot.Worker;

/**
 * Verifies the derived values of the threads snapshot records: a worker's occupancy, a pool's
 * title and which pools can be tuned live.
 */
public class ThreadsSnapshotTests {

	private static Worker worker(final ThreadActivity activity, final double busyShare) {
		return new Worker(1, "w", activity, Thread.State.RUNNABLE, -1, null, null, null, -1, null, busyShare);
	}

	private static Pool pool(final WorkerPoolKind kind, final String name) {
		return new Pool(name, kind, 1, 0, 16, 0, 0, null, null, 0, null, 0, List.of(), null, 0, false, Map.of(), 0);
	}

	@Test
	public void occupancyIsTheBusyShareWhenKnown() {
		Assert.assertEquals(0.25, worker(ThreadActivity.IDLE, 0.25).occupancy(), 0);
		Assert.assertEquals(0.25, worker(ThreadActivity.RUNS, 0.25).occupancy(), 0);
	}

	@Test
	public void blockedWorkerIsFullyOccupiedWhateverItsBusyShare() {
		Assert.assertEquals(1, worker(ThreadActivity.BLOCKED, 0.1).occupancy(), 0);
	}

	@Test
	public void occupancyFallsBackOnHoldingAWorkerWhenShareIsUnknown() {
		Assert.assertEquals(1, worker(ThreadActivity.RUNS, -1).occupancy(), 0);
		Assert.assertEquals(0, worker(ThreadActivity.IDLE, -1).occupancy(), 0);
	}

	@Test
	public void poolTitleNamesItsManager() {
		Assert.assertEquals("EventsManager · events-async", pool(WorkerPoolKind.EVENTS, "x").title());
		Assert.assertEquals("AsyncManager · general", pool(WorkerPoolKind.GENERAL, "x").title());
		Assert.assertEquals("AsyncManager · db", pool(WorkerPoolKind.DEDICATED, "db").title());
	}

	@Test
	public void onlyEventsAndGeneralPoolsAreTunable() {
		Assert.assertTrue(Tuning.tunable(WorkerPoolKind.EVENTS));
		Assert.assertTrue(Tuning.tunable(WorkerPoolKind.GENERAL));
		Assert.assertFalse(Tuning.tunable(WorkerPoolKind.DEDICATED));
	}

	@Test
	public void poolCopiesItsComponentLoad() {
		java.util.HashMap<Class<?>, Double> load = new java.util.HashMap<>();
		load.put(String.class, 0.5);
		Pool pool = new Pool("p", WorkerPoolKind.GENERAL, 1, 0, 16, 0, 0, null, null, 0, null, 0, List.of(), null, 0,
				false, load, 0);
		load.clear();
		Assert.assertEquals(Map.of(String.class, 0.5), pool.componentLoad());
	}
}
