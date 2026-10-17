/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.view.impl;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.container.workers.DurationHistogram;
import org.opales.covalent.container.workers.WorkerPoolKind;
import org.opales.covalent.supervision.threads.model.Contention;
import org.opales.covalent.supervision.threads.model.ThreadActivity;
import org.opales.covalent.supervision.threads.model.ThreadsSnapshot;

public class ThreadsViewImplTests {

	@Test
	public void framesAreShortenedToClassAndMethod() {
		Assert.assertEquals("AStarSolverImpl.search(AStarSolverImpl.java:120)", ThreadsViewImpl.shortFrame(
				"app//org.opales.covalent.examples.rubikscubesolver.solver.astar.impl.AStarSolverImpl.search(AStarSolverImpl.java:120)"));
		Assert.assertEquals("Object.wait0(Native Method)",
				ThreadsViewImpl.shortFrame("java.base@21.0.12/java.lang.Object.wait0(Native Method)"));
	}

	@Test
	public void durationsReadAtTheirScale() {
		Assert.assertEquals("850 µs", ThreadsFormat.duration(850_000));
		Assert.assertEquals("12 ms", ThreadsFormat.duration(12_400_000));
		Assert.assertEquals("1.5 s", ThreadsFormat.duration(1_500_000_000));
		Assert.assertEquals("00:02:03", ThreadsFormat.duration(123_000_000_000L));
		Assert.assertEquals("–", ThreadsFormat.duration(-1));
	}

	private static ThreadsSnapshot snapshot(final long time, final ThreadActivity activity, final long taskNanos) {
		ThreadsSnapshot.Worker worker = new ThreadsSnapshot.Worker(7, "worker-7", activity, Thread.State.RUNNABLE,
				taskNanos, "Task.run", null, null, 1_000, null, -1);
		ThreadsSnapshot.Pool pool = new ThreadsSnapshot.Pool("general", WorkerPoolKind.GENERAL, 1, 0, 10, 0, 0,
				DurationHistogram.EMPTY, DurationHistogram.EMPTY, activity.isBusy() ? 1 : 0, Contention.FLUID, 0,
				List.of(worker), null, 0, false, java.util.Map.of(), 0);
		return new ThreadsSnapshot(time, true, true, List.of(pool), 0, List.of(), List.of(),
				ThreadsSnapshot.Callers.NONE, java.util.Map.of());
	}

	private static ThreadsSnapshot withComponents(final ThreadsSnapshot snapshot, final Class<?>... components) {
		return new ThreadsSnapshot(snapshot.timeMillis(), true, true, snapshot.pools(), 0, List.of(), List.of(),
				ThreadsSnapshot.Callers.NONE, components.length == 0 ? java.util.Map.of()
						: java.util.Map.of(7L, List.of(components)));
	}

	@Test
	public void historyKeepsTheComponentsOfEachMomentAndTheLastOne() {
		ThreadsHistory history = new ThreadsHistory();
		history.add(withComponents(snapshot(1, ThreadActivity.IDLE, -1), String.class, Integer.class));
		history.add(withComponents(snapshot(2, ThreadActivity.IDLE, -1)));
		history.add(withComponents(snapshot(3, ThreadActivity.RUNS, 100), Long.class));
		history.add(withComponents(snapshot(4, ThreadActivity.IDLE, -1)));

		ThreadsHistory.Track track = history.track(7);
		Assert.assertEquals(java.util.Set.of(String.class, Integer.class), history.components(track, 0, 0));
		Assert.assertEquals(java.util.Set.of(), history.components(track, 1, 1));
		Assert.assertEquals(java.util.Set.of(Integer.class, String.class, Long.class), history.components(track, 0, 3));
		// The last one worked for stays, a moment with none after it.
		Assert.assertEquals(Long.class, track.lastComponent);
	}

	@Test
	public void aTaskWithoutLabelShowsItsComponent() {
		ThreadsSnapshot.Worker labeled = new ThreadsSnapshot.Worker(7, "w", ThreadActivity.RUNS, Thread.State.RUNNABLE,
				100, "Consumer.onEvent", null, null, 0, String.class, 0.5);
		ThreadsSnapshot.Worker submitted = new ThreadsSnapshot.Worker(7, "w", ThreadActivity.RUNS, Thread.State.RUNNABLE,
				100, null, null, null, 0, String.class, 0.5);
		ThreadsSnapshot.Worker unknown = new ThreadsSnapshot.Worker(7, "w", ThreadActivity.RUNS, Thread.State.RUNNABLE,
				100, null, null, null, 0, null, 0.5);
		Assert.assertEquals("Consumer.onEvent", PoolSection.taskText(labeled));
		Assert.assertEquals("String", PoolSection.taskText(submitted));
		Assert.assertEquals(ThreadsTexts.get("task"), PoolSection.taskText(unknown));
	}

	@Test
	public void poolHealthSaysWhyAPoolIsPressed() {
		ThreadsSnapshot.Worker worker = new ThreadsSnapshot.Worker(7, "worker-7", ThreadActivity.RUNS,
				Thread.State.RUNNABLE, 100, "Task.run", null, null, 1_000, null, 0.9);
		ThreadsSnapshot.Pool overwhelmed = new ThreadsSnapshot.Pool("general", WorkerPoolKind.GENERAL, 1, 0, 1024, 372,
				0, DurationHistogram.EMPTY, DurationHistogram.EMPTY, 0.9, Contention.SATURATED, 0, List.of(worker), null,
				372, true, java.util.Map.of(), 0);
		Assert.assertEquals(ThreadsTexts.get("health.overwhelmed.many", 372, ThreadsTexts.get("health.pool.general")),
				PoolHealth.sentence(overwhelmed, 0.81));
		Assert.assertEquals(ThreadsTexts.get("health.detail.rejected", 372) + " · " + ThreadsTexts.get("health.detail.calmer"),
				PoolHealth.detail(overwhelmed, 0.81));
		ThreadsSnapshot.Pool strained = new ThreadsSnapshot.Pool("async-events", WorkerPoolKind.EVENTS, 2, 3, 1024, 0,
				0, DurationHistogram.EMPTY, DurationHistogram.EMPTY, 0.75, Contention.UNDER_STRAIN, 0, List.of(worker),
				null, 0, false, java.util.Map.of(), 0);
		Assert.assertEquals(ThreadsTexts.get("health.strainQueueing", ThreadsTexts.get("health.pool.events"), "75 %"),
				PoolHealth.sentence(strained, 0.75));
		Assert.assertEquals("", PoolHealth.detail(strained, 0.75));
	}

	@Test
	public void historyKeepsEachThreadsActivitiesAndNotesItsTasks() {
		ThreadsHistory history = new ThreadsHistory();
		history.add(snapshot(1, ThreadActivity.IDLE, -1));
		history.add(snapshot(2, ThreadActivity.RUNS, 100));
		history.add(snapshot(3, ThreadActivity.RUNS, 200));
		history.add(snapshot(4, ThreadActivity.IDLE, -1));

		ThreadsHistory.Track track = history.track(7);
		Assert.assertEquals(ThreadActivity.RUNS, history.activity(track, 2));
		Assert.assertEquals(ThreadActivity.IDLE, history.activity(track, 3));
		Assert.assertEquals(1, history.runLength(track, 3));
		Assert.assertEquals(2, history.runLength(track, 2));
		List<String> recent = track.recent.stream().map(ThreadsHistory.Activity::text).toList();
		Assert.assertEquals(List.of(ThreadsTexts.get("activity.finished", "Task.run", "1 µs"),
				ThreadsTexts.get("activity.took", "Task.run"), ThreadsTexts.get("activity.started")), recent);
		Assert.assertEquals(1.0f, history.pool("general").occupancy[1], 0f);
	}

	private static ThreadsSnapshot.Worker worker(final long id, final ThreadActivity activity, final Class<?> component) {
		return new ThreadsSnapshot.Worker(id, "worker-" + id, activity, Thread.State.RUNNABLE,
				activity.isBusy() ? 10 : -1, null, null, null, -1, component, -1);
	}

	private static ThreadsSnapshot loadSample(final long time, final java.util.Map<Class<?>, Double> general,
			final double generalOther, final java.util.Map<Class<?>, Double> events) {
		ThreadsSnapshot.Pool generalPool = new ThreadsSnapshot.Pool("general", WorkerPoolKind.GENERAL, 4, 0, 10, 0, 0,
				DurationHistogram.EMPTY, DurationHistogram.EMPTY, 0.6, Contention.FLUID, 0, idleWorkers(4), null, 0, false,
				general, generalOther);
		ThreadsSnapshot.Pool eventsPool = new ThreadsSnapshot.Pool("events-async", WorkerPoolKind.EVENTS, 2, 0, 10, 0,
				0, DurationHistogram.EMPTY, DurationHistogram.EMPTY, 0.6, Contention.FLUID, 0, idleWorkers(2), null, 0,
				false, events, 0);
		return new ThreadsSnapshot(time, true, true, List.of(generalPool, eventsPool), 0, List.of(), List.of(),
				ThreadsSnapshot.Callers.NONE, java.util.Map.of());
	}

	private static List<ThreadsSnapshot.Worker> idleWorkers(final int count) {
		return java.util.stream.LongStream.range(0, count)
				.mapToObj(id -> new ThreadsSnapshot.Worker(id, "worker-" + id, ThreadActivity.IDLE,
						Thread.State.WAITING, 0, null, null, null, 0, null, 0))
				.toList();
	}

	@Test
	public void eventsShowSimpleClassNames() {
		Assert.assertEquals("blocked on CovalentWorkerPool@58fe6776, held by covalent-async-general-6",
				ThreadsViewImpl.shortClassNames("blocked on org.opales.covalent.container.async.impl."
						+ "AsyncManagerImpl$CovalentWorkerPool@58fe6776, held by covalent-async-general-6"));
		Assert.assertEquals("Consumer.onEvent took 2 s", ThreadsViewImpl.shortClassNames("Consumer.onEvent took 2 s"));
	}

	@Test
	public void aFewWorkersOnAverageStillShow() {
		Assert.assertEquals("0.0", ThreadsFormat.workers(0));
		Assert.assertEquals("<0.1", ThreadsFormat.workers(0.004));
		Assert.assertEquals("1.5", ThreadsFormat.workers(1.5));
	}

	@Test
	public void componentSharesAreItsWorkersOverEachKindsWorkers() {
		ThreadsHistory history = new ThreadsHistory();
		// A general pool of 4 workers, an events pool of 2.
		history.add(loadSample(1, java.util.Map.of(String.class, 1.5, Integer.class, 0.25), 0.5,
				java.util.Map.of(String.class, 1.0)));
		history.add(loadSample(2, java.util.Map.of(Integer.class, 2.0), 0, java.util.Map.of()));

		java.util.Map<String, java.util.Map<WorkerPoolKind, float[]>> shares = history.componentShares(0, 1);
		Assert.assertEquals(java.util.Set.of("Integer", "String", ThreadsHistory.UNKNOWN_COMPONENT), shares.keySet());
		Assert.assertEquals(java.util.Set.of(WorkerPoolKind.GENERAL, WorkerPoolKind.EVENTS),
				shares.get("String").keySet());
		Assert.assertArrayEquals(new float[] { 0.375f, 0 }, shares.get("String").get(WorkerPoolKind.GENERAL), 1e-6f);
		Assert.assertArrayEquals(new float[] { 0.5f, 0 }, shares.get("String").get(WorkerPoolKind.EVENTS), 1e-6f);
		Assert.assertEquals(java.util.Set.of(WorkerPoolKind.GENERAL), shares.get("Integer").keySet());
		Assert.assertArrayEquals(new float[] { 0.0625f, 0.5f }, shares.get("Integer").get(WorkerPoolKind.GENERAL),
				1e-6f);
		Assert.assertArrayEquals(new float[] { 0.125f, 0 },
				shares.get(ThreadsHistory.UNKNOWN_COMPONENT).get(WorkerPoolKind.GENERAL), 1e-6f);
		Assert.assertEquals(java.util.Map.of(WorkerPoolKind.EVENTS, 0.5, WorkerPoolKind.GENERAL, 0.375),
				history.componentSharesAt("String", 0));
		Assert.assertEquals(java.util.Map.of(), history.componentSharesAt("String", 1));
	}

	private static ThreadsSnapshot workSample(final long time, final double busy, final Class<?> component) {
		ThreadsSnapshot.Worker worker = new ThreadsSnapshot.Worker(7, "worker-7", ThreadActivity.IDLE,
				Thread.State.WAITING, -1, null, null, null, 0, null, busy);
		ThreadsSnapshot.Pool pool = new ThreadsSnapshot.Pool("general", WorkerPoolKind.GENERAL, 1, 0, 10, 0, 0,
				DurationHistogram.EMPTY, DurationHistogram.EMPTY, 0.6, Contention.FLUID, 0, List.of(worker), null, 0, false,
				java.util.Map.of(), 0);
		return new ThreadsSnapshot(time, true, true, List.of(pool), 0, List.of(), List.of(),
				ThreadsSnapshot.Callers.NONE,
				component == null ? java.util.Map.of() : java.util.Map.of(7L, List.<Class<?>>of(component)));
	}

	@Test
	public void theDominantWorkIsTheComponentCaughtMostOften() {
		ThreadsHistory history = new ThreadsHistory();
		history.add(workSample(1, 0.5, String.class));
		history.add(workSample(2, 0.2, Integer.class));
		history.add(workSample(3, 0.1, Integer.class));
		history.add(workSample(4, 0, null));
		ThreadsHistory.Track track = history.track(7);

		// Caught twice out of three: two thirds of the time in tasks, 0.8 over 4 samples.
		ThreadsHistory.Work dominant = history.dominantWork(track, 3, 4);
		Assert.assertEquals(Integer.class, dominant.component());
		Assert.assertEquals(0.8 / 4 * 2 / 3, dominant.share(), 1e-6);
		Assert.assertEquals(2, dominant.sample());
		Assert.assertEquals(String.class, history.dominantWork(track, 0, 1).component());
		Assert.assertEquals(0.5, history.dominantWork(track, 0, 1).share(), 1e-6);
		Assert.assertNull(history.dominantWork(track, 3, 1));

		ThreadsHistory.Work last = history.lastWork(track, 3);
		Assert.assertEquals(Integer.class, last.component());
		Assert.assertEquals(2, last.sample());
		Assert.assertEquals(String.class, history.lastWork(track, 0).component());
	}

	@Test
	public void theComponentShownIsKeptUnlessAnotherIsCaughtClearlyMoreOften() {
		ThreadsHistory history = new ThreadsHistory();
		for (int sample = 0; sample < 4; sample++) {
			history.add(workSample(sample, 0.5, String.class));
		}
		for (int sample = 4; sample < 9; sample++) {
			history.add(workSample(sample, 0.5, Integer.class));
		}
		ThreadsHistory.Track track = history.track(7);

		// Caught five times against four: close, so the one shown stays.
		Assert.assertEquals(Integer.class, history.dominantWork(track, 8, 9).component());
		Assert.assertEquals(Integer.class, history.dominantWork(track, 8, 9, null).component());
		Assert.assertEquals(String.class, history.dominantWork(track, 8, 9, String.class).component());
		Assert.assertEquals(Integer.class, history.dominantWork(track, 8, 9, Integer.class).component());
		// A component not caught lately is not kept.
		Assert.assertEquals(Integer.class, history.dominantWork(track, 8, 9, Long.class).component());

		// Six against four: clear, so the other takes over.
		history.add(workSample(9, 0.5, Integer.class));
		Assert.assertEquals(Integer.class, history.dominantWork(history.track(7), 9, 10, String.class).component());
	}

	@Test
	public void aThreadThatWorkedLatelyReadsGreenEvenIdleAtTheSample() {
		ThreadsHistory history = new ThreadsHistory();
		history.add(workSample(1, 0.4, String.class));
		history.add(workSample(2, 0, null));
		Assert.assertEquals(0.2, history.recentBusy(7, 2), 1e-6);
		Assert.assertEquals(0, history.recentBusy(7, 1), 1e-6);
		Assert.assertEquals(0, history.recentBusy(99, 2), 1e-6);

		ThreadsSnapshot.Worker idle = new ThreadsSnapshot.Worker(7, "worker-7", ThreadActivity.IDLE,
				Thread.State.WAITING, -1, null, null, null, 0, null, 0);
		Assert.assertEquals(ThreadDot.State.OK, ThreadDot.of(idle, false, 1_000_000_000L, 0.2).state());
		Assert.assertEquals(ThreadDot.State.IDLE, ThreadDot.of(idle, false, 1_000_000_000L, 0).state());
		ThreadsSnapshot.Worker waits = new ThreadsSnapshot.Worker(7, "worker-7", ThreadActivity.WAITS,
				Thread.State.WAITING, 1000, null, null, null, 0, null, 0);
		Assert.assertEquals(ThreadDot.State.WAITS, ThreadDot.of(waits, false, 1_000_000_000L, 0.9).state());
		Assert.assertEquals(1, ThreadDots.intensity(ThreadDot.of(idle, false, 1_000_000_000L, 0.8)), 1e-6);
		Assert.assertTrue(ThreadDots.intensity(ThreadDot.of(idle, false, 1_000_000_000L, 0.05)) < 0.5);
	}

	@Test
	public void historyForgetsWhatIsOlderThanItsCapacity() {
		ThreadsHistory history = new ThreadsHistory();
		for (int sample = 0; sample < ThreadsHistory.CAPACITY + 10; sample++) {
			history.add(snapshot(sample, ThreadActivity.RUNS, sample));
		}
		Assert.assertEquals(10, history.oldest());
		Assert.assertEquals(ThreadsHistory.CAPACITY + 9L, history.timeOf(history.count() - 1));
	}
}
