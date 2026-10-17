/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.controller.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.container.workers.DurationHistogram;
import org.opales.covalent.container.workers.WorkerPoolKind;
import org.opales.covalent.container.workers.WorkerPoolState;
import org.opales.covalent.container.workers.WorkerState;
import org.opales.covalent.container.async.AsyncManager;
import org.opales.covalent.supervision.containermanager.raw.ObservedContainerControl;
import org.opales.covalent.supervision.observed.ObservedCallingThread;
import org.opales.covalent.supervision.observed.ObservedParameters;
import org.opales.covalent.supervision.observed.ObservedRegistryReadServices;
import org.opales.covalent.supervision.sequencediagram.controller.SequenceDiagramServices;
import org.opales.covalent.supervision.supervisionhmi.SupervisorSettings;
import org.opales.covalent.supervision.observed.ObservedWorkersServices;
import org.opales.covalent.supervision.threads.model.Contention;
import org.opales.covalent.supervision.threads.model.Phrase;
import org.opales.covalent.supervision.threads.model.ThreadActivity;
import org.opales.covalent.supervision.threads.model.ThreadStack;
import org.opales.covalent.supervision.threads.model.ThreadsModelServices;
import org.opales.covalent.supervision.threads.model.ThreadsSnapshot;

public class ThreadsControllerImplTests {

	private final ThreadsControllerImpl controller = new ThreadsControllerImpl();

	private final FakeWorkers workers = new FakeWorkers();

	private final FakeModel model = new FakeModel();

	private static final class FakeWorkers implements ObservedWorkersServices, ObservedParameters {
		List<WorkerPoolState> pools = List.of();
		boolean tracking;

		@Override
		public List<WorkerPoolState> readWorkerPools() {
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

		List<ObservedCallingThread> callers = List.of();
		boolean instrumented;

		@Override
		public List<ObservedCallingThread> readCallingThreads() {
			return this.callers;
		}

		@Override
		public boolean isInstrumented() {
			return this.instrumented;
		}

		final List<String> tunings = new ArrayList<>();

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

		@Override
		public void setSnapshot(final ThreadsSnapshot snapshot) {
			this.snapshots.add(snapshot);
		}

		final List<ThreadStack> stacks = new ArrayList<>();

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

	private static WorkerPoolState pool(final int target, final int queued, final long rejected,
			final WorkerState... workers) {
		return new WorkerPoolState("general", WorkerPoolKind.GENERAL, target, queued, 100, rejected, true, 0,
				DurationHistogram.EMPTY, DurationHistogram.EMPTY, java.util.Map.of(), List.of(workers));
	}

	private static WorkerPoolState busyFor(final java.util.Map<Class<?>, Long> componentBusyNanos,
			final WorkerState... workers) {
		return new WorkerPoolState("general", WorkerPoolKind.GENERAL, 4, 0, 100, 0, true, 0, DurationHistogram.EMPTY,
				DurationHistogram.EMPTY, componentBusyNanos, List.of(workers));
	}

	@Test
	public void componentLoadIsTheTimeSpentForEachComponentOverTheInterval() {
		WorkerState worker = new WorkerState(1, "worker-1", false, -1, null, null, 0);
		WorkerPoolState before = busyFor(java.util.Map.of(String.class, 1_000L), worker, worker, worker, worker);
		WorkerPoolState after = busyFor(java.util.Map.of(String.class, 3_001_000L, Integer.class, 500_000L,
				org.opales.covalent.container.async.AsyncManager.class, 2_000_000L), worker, worker, worker, worker);

		// 3 ms for String and 0.5 ms for Integer over 2 ms: 1.5 and 0.25 workers; one of Covalent's
		// own components is left out.
		java.util.Map<Class<?>, Double> load = ThreadsControllerImpl.componentLoad(after, before, true, 2_000_000);
		Assert.assertEquals(java.util.Set.of(String.class, Integer.class), load.keySet());
		Assert.assertEquals(1.5, load.get(String.class), 1e-9);
		Assert.assertEquals(0.25, load.get(Integer.class), 1e-9);

		Assert.assertTrue(ThreadsControllerImpl.componentLoad(after, null, true, 2_000_000).isEmpty());
		Assert.assertTrue(ThreadsControllerImpl.componentLoad(after, before, false, 2_000_000).isEmpty());
	}

	@Test
	public void activityCombinesTheJvmStateWithThePoolsKnowledge() {
		Assert.assertEquals(ThreadActivity.BLOCKED, ThreadsControllerImpl.activity(Thread.State.BLOCKED, false, true));
		Assert.assertEquals(ThreadActivity.RUNS, ThreadsControllerImpl.activity(Thread.State.RUNNABLE, false, false));
		Assert.assertEquals(ThreadActivity.WAITS, ThreadsControllerImpl.activity(Thread.State.WAITING, true, true));
		Assert.assertEquals(ThreadActivity.IDLE, ThreadsControllerImpl.activity(Thread.State.TIMED_WAITING, false, true));
		Assert.assertEquals(ThreadActivity.UNTRACKED, ThreadsControllerImpl.activity(Thread.State.WAITING, true, false));
	}

	@Test
	public void contentionFromOccupancyQueueAndRejections() {
		Assert.assertEquals(Contention.FLUID, ThreadsControllerImpl.contention(0.5, false, false));
		Assert.assertEquals(Contention.UNDER_STRAIN, ThreadsControllerImpl.contention(0.75, false, false));
		Assert.assertEquals(Contention.UNDER_STRAIN, ThreadsControllerImpl.contention(0.2, true, false));
		Assert.assertEquals(Contention.SATURATED, ThreadsControllerImpl.contention(0.95, true, false));
		Assert.assertEquals(Contention.SATURATED, ThreadsControllerImpl.contention(0.1, false, true));
	}

	@Test
	public void sampleReadsTheThreadsAndLogsRejectionsAndResizes() {
		Thread current = Thread.currentThread();
		WorkerState worker = new WorkerState(current.threadId(), current.getName(), true, 1_000, "Consumer.onEvent",
				String.class, 0);
		this.workers.tracking = true;
		this.workers.pools = List.of(pool(2, 0, 0, worker));
		ThreadsSnapshot first = this.controller.sample();
		Assert.assertTrue(first.connected());
		Assert.assertTrue(first.taskTracking());
		ThreadsSnapshot.Pool general = first.pools().get(0);
		Assert.assertEquals(ThreadActivity.RUNS, general.workers().get(0).activity());
		Assert.assertEquals(1.0, general.occupancy(), 0.0);
		Assert.assertEquals("Consumer.onEvent", general.workers().get(0).taskLabel());
		Assert.assertEquals(String.class, general.workers().get(0).taskComponent());

		// 100 ms more spent in tasks since the first sample: a share of the interval.
		WorkerState later = new WorkerState(worker.threadId(), worker.threadName(), false, -1, null, null, 100_000_000L);
		this.workers.pools = List.of(pool(3, 5, 2, later));
		ThreadsSnapshot second = this.controller.sample();
		double share = second.pools().get(0).workers().get(0).busyShare();
		Assert.assertTrue(String.valueOf(share), share > 0 && share <= 1);
		Assert.assertEquals(Contention.SATURATED, second.pools().get(0).contention());
		List<Phrase> texts = second.log().stream().map(ThreadsSnapshot.LogEntry::text).toList();
		Assert.assertTrue(texts.toString(), texts.contains(Phrase.of("log.rejected.many", 2, 5, 100)));
		Assert.assertTrue(texts.toString(), texts.contains(Phrase.of("log.workers", 2, 3)));
		Assert.assertTrue(texts.toString(), texts.contains(Phrase.of("log.saturated")));
	}

	@Test
	public void poolsAreTunedWithinBoundsFromTheirValuesAtConnection() {
		this.workers.pools = List.of(pool(4, 0, 0));
		ThreadsSnapshot first = this.controller.sample();
		Assert.assertEquals(new ThreadsSnapshot.Tuning(4, 100), first.pools().get(0).tuning());

		this.controller.setWorkerCount(WorkerPoolKind.GENERAL, 6);
		this.controller.setWorkerCount(WorkerPoolKind.EVENTS, 0);
		this.controller.setQueueCapacity(WorkerPoolKind.GENERAL, 1_000_000);
		this.controller.setWorkerCount(WorkerPoolKind.DEDICATED, 3);
		Assert.assertEquals(List.of("GENERAL workers 6", "EVENTS workers 1", "GENERAL queue 65536"),
				this.workers.tunings);

		// The pool takes the change: its value at connection stays, the change is logged.
		this.workers.pools = List.of(new WorkerPoolState("general", WorkerPoolKind.GENERAL, 6, 0, 65_536, 0, true, 0,
				DurationHistogram.EMPTY, DurationHistogram.EMPTY, java.util.Map.of(), List.of()));
		ThreadsSnapshot second = this.controller.sample();
		Assert.assertEquals(new ThreadsSnapshot.Tuning(4, 100), second.pools().get(0).tuning());
		List<Phrase> texts = second.log().stream().map(ThreadsSnapshot.LogEntry::text).toList();
		Assert.assertTrue(texts.toString(), texts.contains(Phrase.of("log.workers", 4, 6)));
		Assert.assertTrue(texts.toString(), texts.contains(Phrase.of("log.queueCapacity", 100, 65536)));

		WorkerPoolState dedicated = new WorkerPoolState("orders", WorkerPoolKind.DEDICATED, 1, 0, 100, 0, true, 0,
				DurationHistogram.EMPTY, DurationHistogram.EMPTY, java.util.Map.of(), List.of());
		this.workers.pools = List.of(dedicated);
		Assert.assertNull(this.controller.sample().pools().get(0).tuning());
	}

	@Test
	public void aSaturationIsLoggedOnceUntilThePoolIsFluidAgain() {
		Thread current = Thread.currentThread();
		WorkerState busy = new WorkerState(current.threadId(), current.getName(), true, 1_000, "Task.run", null, 0);
		WorkerState idle = new WorkerState(current.threadId(), current.getName(), false, -1, null, null, 0);
		this.workers.tracking = true;
		// Saturated while it rejects tasks, its queue emptying now and then: one entry.
		for (int sample = 0; sample < 12; sample++) {
			this.workers.pools = List.of(pool(1, sample % 3 == 0 ? 0 : 5, sample, busy));
			this.controller.sample();
		}
		// Then idle for a minute without rejections: fluid again, one entry.
		ThreadsSnapshot last = null;
		for (int sample = 0; sample < 250; sample++) {
			this.workers.pools = List.of(pool(1, 0, 11, idle));
			last = this.controller.sample();
		}
		List<String> keys = last.log().stream().map(entry -> entry.text().key()).toList();
		Assert.assertEquals(keys.toString(), 1, keys.stream().filter("log.saturated"::equals).count());
		Assert.assertEquals(keys.toString(), 1, keys.stream().filter("log.fluidAgain"::equals).count());
		Assert.assertEquals("12 s", ThreadsControllerImpl.lasted(12_400));
		Assert.assertEquals("2 min 5 s", ThreadsControllerImpl.lasted(125_000));
	}

	@Test
	public void rejectionsWithinTheMinuteKeepThePoolSaturated() {
		this.workers.tracking = true;
		this.workers.pools = List.of(pool(1, 0, 0));
		this.controller.sample();
		this.workers.pools = List.of(pool(1, 0, 372));
		this.controller.sample();
		// Quiet seconds since: still saturated for the minute, and calmer now.
		ThreadsSnapshot.Pool pool = null;
		for (int sample = 0; sample < 30; sample++) {
			pool = this.controller.sample().pools().get(0);
		}
		Assert.assertEquals(Contention.SATURATED, pool.contention());
		Assert.assertEquals(372, pool.rejectedLastMinute());
		Assert.assertTrue(pool.calmerNow());
	}

	@Test
	public void trackingChangesAreLogged() {
		this.workers.pools = List.of();
		this.controller.sample();
		this.workers.tracking = true;
		ThreadsSnapshot snapshot = this.controller.sample();
		Assert.assertEquals(Phrase.of("log.trackingOn"), snapshot.log().get(0).text());
	}

	@Test
	public void noSampleOnceDisconnected() throws Exception {
		this.set("connected", false);
		Assert.assertNull(this.controller.sample());
	}

	@Test
	public void componentCallsAreFramesInTheComponentsClasses() {
		java.util.Map<String, Class<?>> components = java.util.Map.of("org.example.SolverImpl", Runnable.class);
		Assert.assertEquals("Runnable.search()", ThreadsControllerImpl.componentCall(
				new StackTraceElement("org.example.SolverImpl", "search", "SolverImpl.java", 12), components));
		Assert.assertEquals("Runnable.solve()", ThreadsControllerImpl.componentCall(
				new StackTraceElement("org.example.SolverImpl$Node", "lambda$solve$0", "SolverImpl.java", 30), components));
		Assert.assertNull(ThreadsControllerImpl.componentCall(
				new StackTraceElement("java.lang.Thread", "run", "Thread.java", 1), components));
	}

	@Test
	public void callingThreadsOutsideThePoolsAreSampledWhileDataFlowsAreOn() {
		Thread current = Thread.currentThread();
		WorkerState worker = new WorkerState(current.threadId(), current.getName(), false, -1, null, null, 0);
		ObservedCallingThread pooled = new ObservedCallingThread(current.threadId(), current.getName(), true, 10,
				"Solver.solve", String.class, 10, 1, String.class, java.util.Set.of(String.class));
		ObservedCallingThread caller = new ObservedCallingThread(987_654_321L, "simulation-runner", true, 5_000, "Runner.tick",
				Integer.class, 5_000, 3, Integer.class, java.util.Set.of(Long.class, Integer.class));
		this.workers.pools = List.of(pool(1, 0, 0, worker));
		this.workers.callers = List.of(pooled, caller);

		Assert.assertEquals(List.of(), this.controller.sample().callers().threads());

		this.workers.instrumented = true;
		ThreadsSnapshot snapshot = this.controller.sample();
		Assert.assertTrue(snapshot.callers().dataFlows());
		// The pool's own thread is left to its pool.
		Assert.assertEquals(1, snapshot.callers().threads().size());
		ThreadsSnapshot.Worker runner = snapshot.callers().threads().get(0);
		Assert.assertEquals("simulation-runner", runner.name());
		Assert.assertEquals("Runner.tick", runner.taskLabel());
		Assert.assertEquals(Integer.class, runner.taskComponent());
		// The components its calls entered since the previous sample, the latest last.
		List<Class<?>> entered = snapshot.components().get(987_654_321L);
		Assert.assertEquals(2, entered.size());
		Assert.assertEquals(Integer.class, entered.get(1));
	}

	// --- Connection, services and the rest of what the controller does for the thread views ---

	private static void await(final BooleanSupplier condition) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 5_000;
		while (!condition.getAsBoolean()) {
			Assert.assertTrue("Timed out waiting", System.currentTimeMillis() < deadline);
			Thread.sleep(5);
		}
	}

	private static <T> T proxy(final Class<T> type, final java.lang.reflect.InvocationHandler handler) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler));
	}

	private static List<String> keys(final ThreadsSnapshot snapshot) {
		return snapshot.log().stream().map(entry -> entry.text().key()).toList();
	}

	@Test
	public void connectingStartsSamplingAndDisconnectingEmptiesTheModel() throws Exception {
		this.set("connected", false);
		this.controller.onContainerConnected();
		try {
			await(() -> !this.model.snapshots.isEmpty());
			Assert.assertTrue(this.model.snapshots.get(0).connected());
		} finally {
			this.controller.onContainerDisconnected();
		}

		Assert.assertSame(ThreadsSnapshot.EMPTY, this.model.snapshots.get(this.model.snapshots.size() - 1));
		Assert.assertNull("nothing is sampled once disconnected", this.controller.sample());
	}

	@Test
	public void aSampleStillBeingPublishedWhenTheContainerDisconnectsDoesNotOutliveTheEmptySnapshot() throws Exception {
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		List<ThreadsSnapshot> published = new CopyOnWriteArrayList<>();
		this.set("useModelServices", new ThreadsModelServices() {
			@Override
			public void setSnapshot(final ThreadsSnapshot snapshot) {
				if (entered.getCount() > 0) {
					entered.countDown();
					boolean waiting = true;
					while (waiting) {
						try {
							release.await();
							waiting = false;
						} catch (InterruptedException interrupted) {
							// The sampler is stopped while it publishes: it still finishes this publication.
						}
					}
				}
				published.add(snapshot);
			}

			@Override
			public void setStack(final ThreadStack stack) {
				// Not under test here.
			}
		});
		this.set("connected", false);
		this.controller.onContainerConnected();
		Assert.assertTrue("a first sample is being published", entered.await(5, TimeUnit.SECONDS));
		Thread releaser = new Thread(() -> {
			try {
				Thread.sleep(200);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
			}
			release.countDown();
		});
		releaser.start();

		this.controller.onContainerDisconnected();

		releaser.join();
		await(() -> published.size() >= 2);
		Assert.assertSame("the container is disconnected: the model ends empty", ThreadsSnapshot.EMPTY,
				published.get(published.size() - 1));
	}

	@Test
	public void activationSamplesAnAlreadyObservedContainerOnly() throws Exception {
		this.set("connected", false);
		this.set("useObservedContainerControl", proxy(ObservedContainerControl.class, (p, m, a) -> null));
		this.controller.start();
		Assert.assertTrue("no container is observed: nothing starts", this.model.snapshots.isEmpty());

		this.set("useObservedContainerControl", proxy(ObservedContainerControl.class,
				(p, m, a) -> "getObservedContainerName".equals(m.getName()) ? "observed" : null));
		this.controller.start();
		try {
			await(() -> !this.model.snapshots.isEmpty());
		} finally {
			this.controller.stop();
			this.controller.onContainerDisconnected();
		}
	}

	@Test
	public void taskTrackingIsForwardedToTheObservedContainer() {
		this.controller.setTaskTracking(true);
		Assert.assertTrue(this.workers.tracking);
		this.controller.setTaskTracking(false);
		Assert.assertFalse(this.workers.tracking);
	}

	@Test
	public void readingAStackNamesTheComponentCallsAmongItsFrames() throws Exception {
		this.set("useObservedRegistryReadServices", proxy(ObservedRegistryReadServices.class, (p, m, a) -> {
			if (!"getInstances".equals(m.getName())) {
				return null;
			}
			Map<Class<?>, Object> instances = new java.util.HashMap<>();
			instances.put(Runnable.class, this);
			instances.put(AsyncManager.class, new Object());
			instances.put(String.class, null);
			return instances;
		}));

		this.controller.readStack(Thread.currentThread().threadId());

		ThreadStack stack = this.model.stacks.get(0);
		Assert.assertEquals(Thread.currentThread().threadId(), stack.threadId());
		Assert.assertFalse(stack.frames().isEmpty());
		Assert.assertEquals(List.of("Runnable.readingAStackNamesTheComponentCallsAmongItsFrames()"),
				stack.componentCalls());
	}

	@Test
	public void readingTheStackOfAThreadThatIsGoneGivesAnEmptyStack() throws Exception {
		this.set("useObservedRegistryReadServices", proxy(ObservedRegistryReadServices.class, (p, m, a) -> {
			throw new IllegalStateException("nothing observed");
		}));

		Thread ended = new Thread(() -> { });
		ended.start();
		ended.join();

		this.controller.readStack(ended.threadId());

		ThreadStack stack = this.model.stacks.get(0);
		Assert.assertTrue(stack.frames().isEmpty());
		Assert.assertTrue(stack.componentCalls().isEmpty());
	}

	@Test
	public void theRecordingIsStartedOnTheComponentsOrStoppedIfOneRuns() throws Exception {
		boolean[] recording = { false };
		List<String> calls = new ArrayList<>();
		this.set("useSequenceDiagramServices", proxy(SequenceDiagramServices.class, (p, m, a) -> {
			switch (m.getName()) {
			case "isRecording":
				return recording[0];
			case "startRecording":
				recording[0] = true;
				calls.add("start " + a[0]);
				return null;
			case "stopRecording":
				recording[0] = false;
				calls.add("stop");
				return null;
			default:
				return null;
			}
		}));

		this.controller.toggleRecording(Set.of());
		Assert.assertEquals("nothing to record: nothing starts", List.of(), calls);
		Assert.assertFalse(this.controller.isRecording());

		this.controller.toggleRecording(Set.of(String.class));
		Assert.assertEquals(List.of("start [class java.lang.String]"), calls);
		Assert.assertTrue(this.controller.isRecording());

		this.controller.toggleRecording(Set.of(String.class));
		Assert.assertEquals(List.of("start [class java.lang.String]", "stop"), calls);
		Assert.assertFalse(this.controller.isRecording());
	}

	@Test
	public void interruptingThreadsInterruptsThemAndLogsIt() throws Exception {
		boolean[] interrupted = { false };
		Thread sleeper = new Thread(() -> {
			try {
				Thread.sleep(60_000);
			} catch (InterruptedException e) {
				interrupted[0] = true;
			}
		}, "sleeper-to-interrupt");
		sleeper.setDaemon(true);
		sleeper.start();

		this.controller.interruptThreads(Set.of(sleeper.threadId()));

		sleeper.join(5_000);
		Assert.assertTrue(interrupted[0]);
		this.workers.pools = List.of();
		Assert.assertTrue(keys(this.controller.sample()).contains("log.interrupted"));
	}

	@Test
	public void aDeadlockInvolvingAPoolThreadIsReportedWithItsThreads() throws Exception {
		ReentrantLock first = new ReentrantLock();
		ReentrantLock second = new ReentrantLock();
		CountDownLatch bothHold = new CountDownLatch(2);
		Thread one = deadlocked("deadlock-one", first, second, bothHold);
		Thread two = deadlocked("deadlock-two", second, first, bothHold);
		try {
			one.start();
			two.start();
			await(() -> one.getState() == Thread.State.WAITING && two.getState() == Thread.State.WAITING
					&& java.lang.management.ManagementFactory.getThreadMXBean().findDeadlockedThreads() != null);
			this.workers.pools = List.of(pool(2, 0, 0,
					new WorkerState(one.threadId(), one.getName(), false, -1, null, null, 0),
					new WorkerState(two.threadId(), two.getName(), false, -1, null, null, 0)));

			ThreadsSnapshot snapshot = this.controller.sample();

			Assert.assertEquals(1, snapshot.deadlocks().size());
			ThreadsSnapshot.Deadlock deadlock = snapshot.deadlocks().get(0);
			Assert.assertEquals(Set.of("deadlock-one", "deadlock-two"), Set.copyOf(deadlock.threadNames()));
			Assert.assertEquals(Set.of(one.threadId(), two.threadId()), Set.copyOf(deadlock.threadIds()));
			Assert.assertEquals(2, deadlock.waits().size());
			Assert.assertTrue(deadlock.report().startsWith("Covalent deadlock report"));
			Assert.assertTrue(deadlock.report().contains("deadlock-one"));
			Assert.assertTrue(keys(snapshot).contains("log.deadlock"));
			Assert.assertEquals(ThreadsSnapshot.Severity.ERROR, snapshot.log().get(0).severity());
		} finally {
			// Interrupting is how a deadlock is undone from the view: it frees these threads too.
			this.controller.interruptThreads(Set.of(one.threadId(), two.threadId()));
			one.join(5_000);
			two.join(5_000);
		}
		Assert.assertFalse(one.isAlive());
		Assert.assertFalse(two.isAlive());
	}

	private static Thread deadlocked(final String name, final ReentrantLock own, final ReentrantLock other,
			final CountDownLatch bothHold) {
		Thread thread = new Thread(() -> {
			try {
				own.lockInterruptibly();
				try {
					bothHold.countDown();
					bothHold.await();
					other.lockInterruptibly();
					other.unlock();
				} finally {
					own.unlock();
				}
			} catch (InterruptedException ended) {
				// Freed from the deadlock.
			}
		}, name);
		thread.setDaemon(true);
		return thread;
	}

	@Test
	public void aThreadThatBecomesBlockedOnAHeldLockIsLogged() throws Exception {
		Object lock = new Object();
		CountDownLatch go = new CountDownLatch(1);
		Thread blocked = new Thread(() -> {
			try {
				go.await();
			} catch (InterruptedException e) {
				return;
			}
			synchronized (lock) {
				// Taken once the test lets go of it.
			}
		}, "blocked-on-a-monitor");
		blocked.setDaemon(true);
		blocked.start();
		await(() -> blocked.getState() == Thread.State.WAITING);
		this.workers.tracking = true;
		this.workers.pools = List.of(pool(1, 0, 0,
				new WorkerState(blocked.threadId(), blocked.getName(), false, -1, null, null, 0)));
		this.controller.sample();

		ThreadsSnapshot snapshot;
		synchronized (lock) {
			go.countDown();
			await(() -> blocked.getState() == Thread.State.BLOCKED);
			snapshot = this.controller.sample();
		}

		Assert.assertEquals(ThreadActivity.BLOCKED, snapshot.pools().get(0).workers().get(0).activity());
		Assert.assertTrue(keys(snapshot).toString(), keys(snapshot).contains("log.blockedOnHeld"));
		blocked.join(5_000);
	}

	@Test
	public void aLongTaskIsLoggedWhileItLastsAndWhenItEnds() {
		long threshold = java.util.concurrent.TimeUnit.SECONDS.toNanos(Math.max(1,
				(long) SupervisorSettings.getDouble(SupervisorSettings.THREADS_LONG_TASK_SECONDS, 10)));
		Thread current = Thread.currentThread();
		this.workers.tracking = true;

		this.workers.pools = List.of(pool(1, 0, 0, task(current, 1_000, "Job.run")));
		this.controller.sample();

		this.workers.pools = List.of(pool(1, 0, 0, task(current, threshold + 1_000_000_000L, "Job.run")));
		ThreadsSnapshot started = this.controller.sample();
		Assert.assertEquals("log.longTaskNamed", started.log().get(0).text().key());
		Assert.assertEquals(ThreadsSnapshot.Severity.WARNING, started.log().get(0).severity());

		this.workers.pools = List.of(pool(1, 0, 0, task(current, threshold + 2_000_000_000L, "Job.run")));
		ThreadsSnapshot lasting = this.controller.sample();
		Assert.assertEquals("the entry follows the task instead of adding another", 1, lasting.log().size());
		Assert.assertEquals("log.longTaskLiveNamed", lasting.log().get(0).text().key());
		Assert.assertEquals(ThreadsSnapshot.Severity.WARNING, lasting.log().get(0).severity());

		this.workers.pools = List.of(pool(1, 0, 0, task(current, 10 * threshold + 1_000_000_000L, "Job.run")));
		ThreadsSnapshot far = this.controller.sample();
		Assert.assertEquals(ThreadsSnapshot.Severity.ERROR, far.log().get(0).severity());

		this.workers.pools = List.of(pool(1, 0, 0, task(current, -1, null)));
		ThreadsSnapshot ended = this.controller.sample();
		Assert.assertEquals("log.longTaskEndedNamed", ended.log().get(0).text().key());
		Assert.assertEquals(ThreadsSnapshot.Severity.INFO, ended.log().get(0).severity());
	}

	@Test
	public void aLongTaskWithoutALabelIsLoggedAnonymously() {
		long threshold = java.util.concurrent.TimeUnit.SECONDS.toNanos(Math.max(1,
				(long) SupervisorSettings.getDouble(SupervisorSettings.THREADS_LONG_TASK_SECONDS, 10)));
		Thread current = Thread.currentThread();
		this.workers.tracking = true;
		this.workers.pools = List.of(pool(1, 0, 0, task(current, 1_000, null)));
		this.controller.sample();

		this.workers.pools = List.of(pool(1, 0, 0, task(current, threshold + 1_000_000_000L, null)));
		Assert.assertEquals("log.longTask", this.controller.sample().log().get(0).text().key());
		this.workers.pools = List.of(pool(1, 0, 0, task(current, threshold + 2_000_000_000L, null)));
		Assert.assertEquals("log.longTaskLive", this.controller.sample().log().get(0).text().key());
		this.workers.pools = List.of(pool(1, 0, 0, task(current, -1, null)));
		Assert.assertEquals("log.longTaskEnded", this.controller.sample().log().get(0).text().key());
	}

	private static WorkerState task(final Thread thread, final long taskNanos, final String label) {
		return new WorkerState(thread.threadId(), thread.getName(), taskNanos >= 0, taskNanos, label, null, 0);
	}
}
