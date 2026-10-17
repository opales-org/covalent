/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.controller.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.opales.covalent.container.async.AsyncManager;
import org.opales.covalent.container.workers.DurationHistogram;
import org.opales.covalent.container.workers.WorkerPoolKind;
import org.opales.covalent.container.workers.WorkerPoolState;
import org.opales.covalent.container.workers.WorkerState;
import org.opales.covalent.supervision.observed.ObservedCallingThread;
import org.opales.covalent.supervision.observed.ObservedParameters;
import org.opales.covalent.supervision.observed.ObservedRegistryReadServices;
import org.opales.covalent.supervision.observed.ObservedWorkersServices;
import org.opales.covalent.supervision.supervisionhmi.SupervisorSettings;
import org.opales.covalent.supervision.threads.model.Phrase;
import org.opales.covalent.supervision.threads.model.ThreadActivity;
import org.opales.covalent.supervision.threads.model.ThreadStack;
import org.opales.covalent.supervision.threads.model.ThreadsModelServices;
import org.opales.covalent.supervision.threads.model.ThreadsSnapshot;

/**
 * Verifies the finer behavior of {@link ThreadsControllerImpl}'s sampling with hand-written fakes
 * of the observed container: rates between samples, the calling threads, long tasks that vanish or
 * are evicted from the log, deadlocks mixing monitors and locks, the sampler's resistance to
 * failures, and the tuning and tracking services.
 */
public class ThreadsControllerImplSamplingTests {

	private final ThreadsControllerImpl controller = new ThreadsControllerImpl();

	private final FakeWorkers workers = new FakeWorkers();

	private final FakeModel model = new FakeModel();

	private static final class FakeWorkers implements ObservedWorkersServices, ObservedParameters {
		List<WorkerPoolState> pools = List.of();
		boolean tracking;
		boolean instrumented;
		List<ObservedCallingThread> callers = List.of();
		RuntimeException failure;
		final List<String> tunings = new ArrayList<>();

		@Override
		public List<WorkerPoolState> readWorkerPools() {
			if (this.failure != null) {
				throw this.failure;
			}
			return this.pools;
		}

		@Override
		public void setTaskTracking(final boolean tracking) {
			this.tracking = tracking;
		}

		@Override
		public boolean isTaskTracking() {
			return this.tracking;
		}

		@Override
		public List<ObservedCallingThread> readCallingThreads() {
			return this.callers;
		}

		@Override
		public boolean isInstrumented() {
			return this.instrumented;
		}

		@Override
		public void setWorkerCount(final WorkerPoolKind kind, final int count) {
			this.tunings.add(kind + " workers " + count);
		}

		@Override
		public void setQueueCapacity(final WorkerPoolKind kind, final int capacity) {
			this.tunings.add(kind + " queue " + capacity);
		}

		@Override
		public void setCallStreamEnabled(final boolean enabled) {
			// Not used by the Thread Supervisor.
		}
	}

	private static final class FakeModel implements ThreadsModelServices {
		final List<ThreadsSnapshot> snapshots = new ArrayList<>();
		final List<ThreadStack> stacks = new ArrayList<>();

		@Override
		public synchronized void setSnapshot(final ThreadsSnapshot snapshot) {
			this.snapshots.add(snapshot);
		}

		@Override
		public void setStack(final ThreadStack stack) {
			this.stacks.add(stack);
		}
	}

	@Before
	public void setUp() throws Exception {
		this.set("useObservedWorkersServices", this.workers);
		this.set("accessObservedParameters", this.workers);
		this.set("useModelServices", this.model);
		this.set("connected", true);
	}

	private void set(final String name, final Object value) throws Exception {
		Field field = ThreadsControllerImpl.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(this.controller, value);
	}

	private static void await(final BooleanSupplier condition) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 5_000;
		while (!condition.getAsBoolean()) {
			Assert.assertTrue("Timed out waiting", System.currentTimeMillis() < deadline);
			Thread.sleep(5);
		}
	}

	private static WorkerPoolState pool(final int target, final int queued, final long rejected, final long completed,
			final Map<Class<?>, Long> componentBusy, final WorkerState... workers) {
		return new WorkerPoolState("general", WorkerPoolKind.GENERAL, target, queued, 100, rejected, true, completed,
				DurationHistogram.EMPTY, DurationHistogram.EMPTY, componentBusy, List.of(workers));
	}

	private static WorkerState task(final Thread thread, final long taskNanos, final String label) {
		return new WorkerState(thread.threadId(), thread.getName(), taskNanos >= 0, taskNanos, label, null, 0);
	}

	private static long threshold() {
		return TimeUnit.SECONDS.toNanos(Math.max(1,
				(long) SupervisorSettings.getDouble(SupervisorSettings.THREADS_LONG_TASK_SECONDS, 10)));
	}

	private static List<String> keys(final ThreadsSnapshot snapshot) {
		return snapshot.log().stream().map(entry -> entry.text().key()).toList();
	}

	// --- Services ---

	@Test
	public void dedicatedPoolsAreNeitherResizedNorGivenAQueueCapacity() {
		this.controller.setQueueCapacity(WorkerPoolKind.DEDICATED, 500);
		this.controller.setQueueCapacity(WorkerPoolKind.EVENTS, 1);
		this.controller.setQueueCapacity(WorkerPoolKind.GENERAL, 70_000);
		Assert.assertEquals(List.of("EVENTS queue 16", "GENERAL queue 65536"), this.workers.tunings);
	}

	@Test
	public void connectingEnablesTaskTrackingWhenTheSettingAsksForIt() throws Exception {
		boolean previous = SupervisorSettings.getBoolean(SupervisorSettings.THREADS_TRACKING_ON_CONNECT, false);
		this.set("connected", false);
		try {
			SupervisorSettings.putBoolean(SupervisorSettings.THREADS_TRACKING_ON_CONNECT, false);
			this.controller.onContainerConnected();
			Assert.assertFalse(this.workers.tracking);
			this.controller.onContainerDisconnected();

			SupervisorSettings.putBoolean(SupervisorSettings.THREADS_TRACKING_ON_CONNECT, true);
			this.controller.onContainerConnected();
			Assert.assertTrue(this.workers.tracking);
		} finally {
			this.controller.onContainerDisconnected();
			SupervisorSettings.putBoolean(SupervisorSettings.THREADS_TRACKING_ON_CONNECT, previous);
		}
	}

	@Test
	public void disablingTrackingIsLoggedAfterEnablingIt() {
		this.workers.tracking = true;
		this.controller.sample();
		this.workers.tracking = false;
		ThreadsSnapshot snapshot = this.controller.sample();
		Assert.assertEquals(Phrase.of("log.trackingOff"), snapshot.log().get(0).text());
		Assert.assertFalse(snapshot.taskTracking());
	}

	// --- Stack reading ---

	private void recurse(final int depth) {
		if (depth == 0) {
			this.controller.readStack(Thread.currentThread().threadId());
		} else {
			this.recurse(depth - 1);
		}
	}

	@Test
	public void consecutiveFramesOfTheSameComponentCallAreNamedOnce() throws Exception {
		this.set("useObservedRegistryReadServices", proxy(ObservedRegistryReadServices.class, (p, m, a) -> {
			Map<Class<?>, Object> instances = new HashMap<>();
			instances.put(Runnable.class, this);
			return instances;
		}));
		this.recurse(3);
		ThreadStack stack = this.model.stacks.get(0);
		Assert.assertEquals(1, stack.componentCalls().stream().filter("Runnable.recurse()"::equals).count());
	}

	@Test
	public void aLambdaFrameWithoutAnEnclosingMethodKeepsItsOwnName() {
		Map<String, Class<?>> components = Map.of("org.example.SolverImpl", Runnable.class);
		Assert.assertEquals("Runnable.lambda$()", ThreadsControllerImpl.componentCall(
				new StackTraceElement("org.example.SolverImpl", "lambda$", "SolverImpl.java", 1), components));
	}

	private static <T> T proxy(final Class<T> type, final java.lang.reflect.InvocationHandler handler) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler));
	}

	// --- The sampler ---

	private void sampleSafely() throws Exception {
		Method method = ThreadsControllerImpl.class.getDeclaredMethod("sampleSafely");
		method.setAccessible(true);
		method.invoke(this.controller);
	}

	@Test
	public void theSamplerPublishesASnapshotWhenConnected() throws Exception {
		this.sampleSafely();
		Assert.assertEquals(1, this.model.snapshots.size());
		Assert.assertTrue(this.model.snapshots.get(0).connected());
	}

	@Test
	public void theSamplerPublishesNothingOnceDisconnected() throws Exception {
		this.set("connected", false);
		this.sampleSafely();
		Assert.assertTrue(this.model.snapshots.isEmpty());
	}

	@Test
	public void aFailingSampleIsLostWithoutStoppingTheSampler() throws Exception {
		this.workers.failure = new IllegalStateException("container stopping");
		this.sampleSafely();
		Assert.assertTrue(this.model.snapshots.isEmpty());
		this.workers.failure = null;
		this.sampleSafely();
		Assert.assertEquals(1, this.model.snapshots.size());
	}

	// --- Figures between samples ---

	@Test
	public void tasksPerSecondFollowCompletedTasksBetweenSamples() throws Exception {
		this.workers.pools = List.of(pool(1, 0, 0, 10, Map.of()));
		Assert.assertEquals(0, this.controller.sample().pools().get(0).tasksPerSecond(), 0);
		Thread.sleep(5);
		this.workers.pools = List.of(pool(1, 0, 0, 110, Map.of()));
		Assert.assertTrue(this.controller.sample().pools().get(0).tasksPerSecond() > 0);
		Thread.sleep(5);
		this.workers.pools = List.of(pool(1, 0, 0, 5, Map.of()));
		Assert.assertEquals("a restarted counter is not a negative rate", 0,
				this.controller.sample().pools().get(0).tasksPerSecond(), 0);
	}

	@Test
	public void timeInTasksOfNoKnownComponentIsTheOtherLoad() throws Exception {
		Thread current = Thread.currentThread();
		WorkerState inTask = new WorkerState(current.threadId(), current.getName(), true, 1, "x", null, 0);
		this.workers.tracking = true;
		this.workers.pools = List.of(pool(1, 0, 0, 0, Map.of(), inTask));
		Assert.assertEquals(0, this.controller.sample().pools().get(0).otherLoad(), 0);
		Thread.sleep(5);
		WorkerState later = new WorkerState(current.threadId(), current.getName(), true, 1, "x", null, 1_000_000_000_000L);
		this.workers.pools = List.of(pool(1, 0, 0, 0, Map.of(), later));
		ThreadsSnapshot second = this.controller.sample();
		Assert.assertTrue(second.pools().get(0).componentLoad().isEmpty());
		Assert.assertEquals(1.0, second.pools().get(0).otherLoad(), 1e-9);
	}

	@Test
	public void callsPerSecondFollowTheCallingThreadsCallCount() throws Exception {
		this.workers.instrumented = true;
		this.workers.callers = List.of(new ObservedCallingThread(987_654_321L, "runner", false, -1, null, null, 0, 10,
				null, Set.of()));
		Assert.assertEquals(0, this.controller.sample().callers().callsPerSecond(), 0);
		Thread.sleep(5);
		this.workers.callers = List.of(new ObservedCallingThread(987_654_321L, "runner", false, -1, null, null, 0, 110,
				null, Set.of()));
		Assert.assertTrue(this.controller.sample().callers().callsPerSecond() > 0);
	}

	// --- Calling threads ---

	@Test
	public void aCallingThreadOutsideAnyCallIsIdleAndOneInACallKeepsItsTask() {
		Thread current = Thread.currentThread();
		this.workers.instrumented = true;
		this.workers.callers = List.of(
				new ObservedCallingThread(current.threadId(), current.getName(), false, -1, null, null, 0, 0, null, Set.of()));
		ThreadsSnapshot.Worker outside = this.controller.sample().callers().threads().get(0);
		Assert.assertEquals(ThreadActivity.IDLE, outside.activity());
		Assert.assertEquals(Thread.State.RUNNABLE, outside.jvmState());
		Assert.assertNull(outside.taskLabel());

		this.workers.callers = List.of(new ObservedCallingThread(current.threadId(), current.getName(), true, 50,
				"Runner.tick", Integer.class, 50, 1, Integer.class, Set.of(Integer.class)));
		ThreadsSnapshot.Worker inside = this.controller.sample().callers().threads().get(0);
		Assert.assertEquals(ThreadActivity.RUNS, inside.activity());
		Assert.assertEquals("Runner.tick", inside.taskLabel());
	}

	@Test
	public void componentsEnteredByACallingThreadAreKeptWhenTheLastOneIsNotAmongThem() {
		this.workers.instrumented = true;
		this.workers.callers = List.of(new ObservedCallingThread(987_654_321L, "runner", true, 50, "Runner.tick",
				Integer.class, 50, 1, String.class, Set.of(Integer.class)));
		Map<Long, List<Class<?>>> components = this.controller.sample().components();
		Assert.assertEquals(List.of(Integer.class), components.get(987_654_321L));
	}

	@Test
	public void aPoolThreadsTaskComponentIsListedUnlessItIsOneOfCovalentsOwn() {
		Thread current = Thread.currentThread();
		WorkerState own = new WorkerState(current.threadId(), current.getName(), true, 1, "t", AsyncManager.class, 0);
		this.workers.pools = List.of(pool(1, 0, 0, 0, Map.of(), own));
		Assert.assertTrue(this.controller.sample().components().isEmpty());

		WorkerState app = new WorkerState(current.threadId(), current.getName(), true, 1, "t", String.class, 0);
		this.workers.pools = List.of(pool(1, 0, 0, 0, Map.of(), app));
		Assert.assertEquals(List.of(String.class), this.controller.sample().components().get(current.threadId()));
	}

	@Test
	public void aPoolThreadThatFinishedShortTasksBetweenTwoSamplesIsListedWithTheirComponent() {
		Thread current = Thread.currentThread();
		this.workers.tracking = true;
		this.workers.pools = List.of(pool(1, 0, 0, 0, Map.of(),
				new WorkerState(current.threadId(), current.getName(), false, -1, null, null, 0, null)));
		Assert.assertTrue(this.controller.sample().components().isEmpty());

		// Busy time grew, no task now: one finished in between, for the component it left.
		this.workers.pools = List.of(pool(1, 0, 0, 1, Map.of(),
				new WorkerState(current.threadId(), current.getName(), false, -1, null, null, 500_000, String.class)));
		Assert.assertEquals(List.of(String.class), this.controller.sample().components().get(current.threadId()));

		// Nothing finished since: the same component is not listed again.
		this.workers.pools = List.of(pool(1, 0, 0, 1, Map.of(),
				new WorkerState(current.threadId(), current.getName(), false, -1, null, null, 500_000, String.class)));
		Assert.assertTrue(this.controller.sample().components().isEmpty());
	}

	@Test
	public void theComponentOfAFinishedTaskIsLeftOutWhenItIsCovalentsOwnOrTheThreadIsInATask() {
		Thread current = Thread.currentThread();
		this.workers.tracking = true;
		this.workers.pools = List.of(pool(1, 0, 0, 0, Map.of(),
				new WorkerState(current.threadId(), current.getName(), false, -1, null, null, 0, null)));
		this.controller.sample();

		this.workers.pools = List.of(pool(1, 0, 0, 1, Map.of(), new WorkerState(current.threadId(), current.getName(),
				false, -1, null, null, 500_000, AsyncManager.class)));
		Assert.assertTrue(this.controller.sample().components().isEmpty());

		// In a task now: its busy time grows by itself, and the last task finished is not the news.
		this.workers.pools = List.of(pool(1, 0, 0, 1, Map.of(), new WorkerState(current.threadId(), current.getName(),
				true, 1, "t", null, 900_000, String.class)));
		Assert.assertTrue(this.controller.sample().components().isEmpty());
	}

	@Test
	public void aTaskRunningNowIsCaughtBySampleNotItsPredecessor() {
		Thread current = Thread.currentThread();
		this.workers.tracking = true;
		this.workers.pools = List.of(pool(1, 0, 0, 0, Map.of(),
				new WorkerState(current.threadId(), current.getName(), false, -1, null, null, 0, null)));
		this.controller.sample();
		// Running now: the sample catches it itself.
		this.workers.pools = List.of(pool(1, 0, 0, 1, Map.of(), new WorkerState(current.threadId(), current.getName(),
				true, 1, "t", Integer.class, 900_000, String.class)));
		Assert.assertEquals(List.of(Integer.class), this.controller.sample().components().get(current.threadId()));
	}

	// --- Threads the JVM no longer knows or that wait on a monitor ---

	@Test
	public void aWorkerWhoseThreadHasEndedIsIdleWithoutLock() throws Exception {
		Thread ended = new Thread(() -> { });
		ended.start();
		ended.join();
		this.workers.tracking = true;
		this.workers.pools = List.of(pool(1, 0, 0, 0, Map.of(), task(ended, 5, "t")));
		ThreadsSnapshot.Worker worker = this.controller.sample().pools().get(0).workers().get(0);
		Assert.assertEquals(Thread.State.TERMINATED, worker.jvmState());
		Assert.assertEquals(ThreadActivity.IDLE, worker.activity());
		Assert.assertNull(worker.lockName());
		Assert.assertNull(worker.lockOwnerName());
	}

	@Test
	public void aWorkerWaitingOnAMonitorInATaskNamesTheLockWithoutOwner() throws Exception {
		Object lock = new Object();
		Thread waiting = new Thread(() -> {
			synchronized (lock) {
				try {
					lock.wait();
				} catch (InterruptedException e) {
					// Released by the test.
				}
			}
		}, "waits-on-a-monitor");
		waiting.setDaemon(true);
		waiting.start();
		try {
			await(() -> waiting.getState() == Thread.State.WAITING);
			this.workers.tracking = true;
			this.workers.pools = List.of(pool(1, 0, 0, 0, Map.of(), task(waiting, 5, "t")));
			ThreadsSnapshot.Worker worker = this.controller.sample().pools().get(0).workers().get(0);
			Assert.assertEquals(ThreadActivity.WAITS, worker.activity());
			Assert.assertNotNull(worker.lockName());
			Assert.assertNull(worker.lockOwnerName());
		} finally {
			waiting.interrupt();
			waiting.join(5_000);
		}
	}

	// --- Long tasks ---

	@Test
	public void aLongTaskIsClosedWhenItsThreadLeavesThePool() {
		Thread current = Thread.currentThread();
		this.workers.tracking = true;
		this.workers.pools = List.of(pool(1, 0, 0, 0, Map.of(), task(current, 1_000, "Job.run")));
		this.controller.sample();
		this.workers.pools = List.of(pool(1, 0, 0, 0, Map.of(), task(current, threshold() + 1_000_000_000L, "Job.run")));
		Assert.assertEquals("log.longTaskNamed", this.controller.sample().log().get(0).text().key());

		this.workers.pools = List.of(pool(1, 0, 0, 0, Map.of()));
		Assert.assertEquals("log.longTaskEndedNamed", this.controller.sample().log().get(0).text().key());
	}

	@Test
	public void aTaskAlreadyLongWhenFirstSeenAsIdleIsNotReportedAsStartingLong() {
		Thread current = Thread.currentThread();
		this.workers.tracking = true;
		this.workers.pools = List.of(pool(1, 0, 0, 0, Map.of(), task(current, -1, null)));
		this.controller.sample();
		this.workers.pools = List.of(pool(1, 0, 0, 0, Map.of(), task(current, threshold() + 1_000_000_000L, "Job.run")));
		Assert.assertFalse(keys(this.controller.sample()).contains("log.longTaskNamed"));
	}

	@Test
	public void aShorterTaskThanTheLongOneRunningMeansANewTaskAndEndsTheLongOne() {
		Thread current = Thread.currentThread();
		this.workers.tracking = true;
		this.workers.pools = List.of(pool(1, 0, 0, 0, Map.of(), task(current, 1_000, "Job.run")));
		this.controller.sample();
		this.workers.pools = List.of(pool(1, 0, 0, 0, Map.of(), task(current, threshold() + 5_000_000_000L, "Job.run")));
		this.controller.sample();
		this.workers.pools = List.of(pool(1, 0, 0, 0, Map.of(), task(current, threshold() + 1_000_000_000L, "Job.run")));
		ThreadsSnapshot snapshot = this.controller.sample();
		Assert.assertEquals("log.longTaskEndedNamed", snapshot.log().get(0).text().key());
		Assert.assertEquals("the start entry became the end entry", 1, snapshot.log().size());
	}

	@Test
	public void aLongTaskEntryPushedOutOfTheLogIsNotBroughtBack() {
		Thread current = Thread.currentThread();
		this.workers.tracking = true;
		this.workers.pools = List.of(pool(1, 0, 0, 0, Map.of(), task(current, 1_000, "Job.run")));
		this.controller.sample();
		long nanos = threshold() + 1_000_000_000L;
		this.workers.pools = List.of(pool(1, 0, 0, 0, Map.of(), task(current, nanos, "Job.run")));
		this.controller.sample();
		ThreadsSnapshot snapshot = null;
		for (int sample = 1; sample <= 70; sample++) {
			this.workers.pools = List.of(pool(1, 0, sample, 0, Map.of(), task(current, nanos + sample, "Job.run")));
			snapshot = this.controller.sample();
		}
		Assert.assertEquals(60, snapshot.log().size());
		Assert.assertTrue(keys(snapshot).stream().noneMatch(key -> key.startsWith("log.longTask")));
	}

	// --- Deadlocks ---

	@Test
	public void aDeadlockIsLoggedOnceWhileItStaysTheSameAndItsReportShowsMonitorsAndLocks() throws Exception {
		Object monitor = new Object();
		ReentrantLock lock = new ReentrantLock();
		CountDownLatch bothHold = new CountDownLatch(2);
		Thread holdsMonitor = new Thread(() -> {
			synchronized (monitor) {
				bothHold.countDown();
				try {
					bothHold.await();
					lock.lockInterruptibly();
					lock.unlock();
				} catch (InterruptedException e) {
					// Freed from the deadlock.
				}
			}
		}, "holds-the-monitor");
		Thread holdsLock = new Thread(() -> {
			lock.lock();
			try {
				bothHold.countDown();
				bothHold.await();
				synchronized (monitor) {
					// Taken once the other thread is freed.
				}
			} catch (InterruptedException e) {
				// Not expected.
			} finally {
				lock.unlock();
			}
		}, "holds-the-lock");
		holdsMonitor.setDaemon(true);
		holdsLock.setDaemon(true);
		try {
			holdsMonitor.start();
			holdsLock.start();
			await(() -> holdsMonitor.getState() == Thread.State.WAITING && holdsLock.getState() == Thread.State.BLOCKED
					&& java.lang.management.ManagementFactory.getThreadMXBean().findDeadlockedThreads() != null);
			this.workers.pools = List.of(pool(2, 0, 0, 0, Map.of(),
					new WorkerState(holdsMonitor.threadId(), holdsMonitor.getName(), false, -1, null, null, 0),
					new WorkerState(holdsLock.threadId(), holdsLock.getName(), false, -1, null, null, 0)));

			ThreadsSnapshot snapshot = null;
			for (int sample = 0; sample < 6; sample++) {
				snapshot = this.controller.sample();
			}

			Assert.assertEquals(1, keys(snapshot).stream().filter("log.deadlock"::equals).count());
			String report = snapshot.deadlocks().get(0).report();
			Assert.assertTrue(report, report.contains("holds monitor"));
			Assert.assertTrue(report, report.contains("waits for"));
			Assert.assertTrue(report, report.contains("held by \"holds-the-"));
			Assert.assertTrue(report, report.contains("    at "));
		} finally {
			holdsMonitor.interrupt();
			holdsMonitor.join(5_000);
			holdsLock.join(5_000);
		}
		Assert.assertFalse(holdsMonitor.isAlive());
		Assert.assertFalse(holdsLock.isAlive());
	}

	@Test
	public void deadlockedThreadsOutsideThePoolsAreNotReported() throws Exception {
		this.workers.pools = List.of();
		Assert.assertTrue(this.controller.sample().deadlocks().isEmpty());
	}
}
