/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opales.covalent.container.ContainerStatus;
import org.opales.covalent.container.ShutdownReport;
import org.opales.covalent.container.jmx.WorkerPoolInfo;
import org.opales.covalent.container.workers.DurationHistogram;
import org.opales.covalent.container.workers.WorkerPoolKind;
import org.opales.covalent.container.workers.WorkerPoolState;
import org.opales.covalent.container.workers.WorkerState;

/**
 * The value types that report on the container: the shorter constructors fill in what they leave out, and the
 * worker pool information has a readable text form.
 */
public class ReportValueTypesTests {

	@Test
	public void aWorkerStateBuiltWithoutALastTaskComponentHasNone() {
		WorkerState state = new WorkerState(7L, "worker-7", true, 1_500L, "label", String.class, 9_000L);

		assertEquals(7L, state.threadId());
		assertEquals("worker-7", state.threadName());
		assertTrue(state.inTask());
		assertEquals(1_500L, state.taskNanos());
		assertEquals("label", state.taskLabel());
		assertEquals(String.class, state.taskComponent());
		assertEquals(9_000L, state.busyNanos());
		assertNull(state.lastTaskComponent());
	}

	@Test
	public void aWorkerPoolStateBuiltWithoutCancelledTasksHasNone() {
		WorkerState worker = new WorkerState(1L, "w", false, -1L, null, null, 0L);

		WorkerPoolState state = new WorkerPoolState("pool", WorkerPoolKind.GENERAL, 4, 2, 100, 3L, true, 11L,
				DurationHistogram.EMPTY, DurationHistogram.EMPTY, Map.of(), List.of(worker));

		assertEquals("pool", state.name());
		assertEquals(WorkerPoolKind.GENERAL, state.kind());
		assertEquals(4, state.targetWorkerCount());
		assertEquals(2, state.queuedTasks());
		assertEquals(100, state.queueCapacity());
		assertEquals(3L, state.rejectedTasks());
		assertEquals(0L, state.cancelledTasks());
		assertTrue(state.taskTracking());
		assertEquals(11L, state.completedTasks());
		assertEquals(List.of(worker), state.workers());
	}

	@Test
	public void aShutdownReportBuiltWithoutResiduesHasNone() {
		ShutdownReport report = new ShutdownReport(ContainerStatus.healthy, 1.5f, Map.of(), List.of());

		assertEquals(ContainerStatus.healthy, report.getStatus());
		assertEquals(1.5f, report.getDurationSeconds(), 0f);
		assertEquals(List.of(), report.getFailedComponents());
		assertEquals(List.of(), report.getResidues());
	}

	@Test
	public void theWorkerPoolInformationIsReadableAsText() {
		WorkerPoolInfo info = new WorkerPoolInfo("general", "GENERAL", 4, 2, 100, 3L, 1L, 11L, true);

		assertEquals("WorkerPoolInfo[general, GENERAL, workers=4, queued=2/100, rejected=3, cancelled=1, "
				+ "completed=11, tracking=true]", info.toString());
	}

	@Test
	public void aHistogramCannotBeComparedWithNothing() {
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> DurationHistogram.EMPTY.since(null));

		assertTrue(failure.getMessage(), failure.getMessage().contains("earlier"));
	}

	@Test
	public void aWorkerStateNeedsAThreadName() {
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> new WorkerState(1L, null, false, -1L, null, null, 0L));

		assertTrue(failure.getMessage(), failure.getMessage().contains("threadName"));
	}

	@Test
	public void aWorkerPoolStateNeedsItsNameKindHistogramsComponentsAndWorkers() {
		DurationHistogram empty = DurationHistogram.EMPTY;
		Map<Class<?>, Long> busy = Map.of();
		List<WorkerState> workers = List.of();
		assertNamedNullRejected("name", () -> new WorkerPoolState(null, WorkerPoolKind.GENERAL, 1, 0, 1, 0L, false, 0L,
				empty, empty, busy, workers));
		assertNamedNullRejected("kind", () -> new WorkerPoolState("p", null, 1, 0, 1, 0L, false, 0L, empty, empty,
				busy, workers));
		assertNamedNullRejected("queueWaits", () -> new WorkerPoolState("p", WorkerPoolKind.GENERAL, 1, 0, 1, 0L,
				false, 0L, null, empty, busy, workers));
		assertNamedNullRejected("durations", () -> new WorkerPoolState("p", WorkerPoolKind.GENERAL, 1, 0, 1, 0L,
				false, 0L, empty, null, busy, workers));
		assertNamedNullRejected("componentBusyNanos", () -> new WorkerPoolState("p", WorkerPoolKind.GENERAL, 1, 0, 1,
				0L, false, 0L, empty, empty, null, workers));
		assertNamedNullRejected("workers", () -> new WorkerPoolState("p", WorkerPoolKind.GENERAL, 1, 0, 1, 0L, false,
				0L, empty, empty, busy, null));
	}

	private static void assertNamedNullRejected(final String name, final org.junit.function.ThrowingRunnable creation) {
		IllegalArgumentException failure = assertThrows(name, IllegalArgumentException.class, creation);
		assertTrue(failure.getMessage(), failure.getMessage().contains(name));
	}
}
