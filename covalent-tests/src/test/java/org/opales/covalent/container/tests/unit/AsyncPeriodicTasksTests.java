/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.container.async.ScheduledTask;
import org.opales.covalent.container.async.impl.AsyncManagerImpl;
import org.opales.covalent.container.workers.impl.TaskSubmitter;

/**
 * Verifies the periodic tasks of {@link AsyncManagerImpl} used on its own: repetition, initial delay, fixed
 * delay without overlap, failures that do not stop the repetition, cancellation by the caller, by the owner's
 * passivation and by the manager stopping, a full queue skipping a run, and argument checks.
 */
public class AsyncPeriodicTasksTests {

	private static final long TIMEOUT_SECONDS = 10;

	private AsyncManagerImpl manager;

	@Before
	public void setUp() {
		this.manager = new AsyncManagerImpl();
		this.manager.initialize();
		this.manager.activate();
		this.manager.startWorkers();
	}

	@After
	public void tearDown() {
		this.manager.passivate();
	}

	private static Duration ms(final long millis) {
		return Duration.ofMillis(millis);
	}

	private static void awaitCount(final AtomicInteger counter, final int expected) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
		while (counter.get() < expected) {
			assertTrue("only " + counter.get() + " runs, expected " + expected, System.nanoTime() < deadline);
			Thread.sleep(5);
		}
	}

	@Test
	public void aTaskRunsAgainAndAgain() throws Exception {
		AtomicInteger runs = new AtomicInteger();

		ScheduledTask task = this.manager.scheduleWithFixedDelay(ms(0), ms(20), runs::incrementAndGet);

		awaitCount(runs, 5);
		assertFalse(task.isCancelled());
	}

	@Test
	public void theInitialDelayIsRespected() throws Exception {
		AtomicInteger runs = new AtomicInteger();

		this.manager.scheduleWithFixedDelay(ms(400), ms(20), runs::incrementAndGet);

		Thread.sleep(150);
		assertEquals(0, runs.get());
		awaitCount(runs, 1);
	}

	@Test
	public void theDelayIsCountedFromTheEndOfARun() throws Exception {
		List<Long> starts = new CopyOnWriteArrayList<>();

		this.manager.scheduleWithFixedDelay(ms(0), ms(200), () -> {
			starts.add(System.nanoTime());
			sleep(100);
		});

		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
		while (starts.size() < 3 && System.nanoTime() < deadline) {
			Thread.sleep(10);
		}
		assertTrue(starts.size() >= 3);
		for (int i = 1; i < 3; i++) {
			long gapMillis = TimeUnit.NANOSECONDS.toMillis(starts.get(i) - starts.get(i - 1));
			assertTrue("gap " + gapMillis + " ms is shorter than run + delay", gapMillis >= 280);
		}
	}

	@Test
	public void runsOfOneTaskNeverOverlap() throws Exception {
		AtomicInteger inside = new AtomicInteger();
		AtomicInteger maxInside = new AtomicInteger();
		AtomicInteger runs = new AtomicInteger();

		this.manager.scheduleWithFixedDelay(ms(0), ms(1), () -> {
			maxInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
			sleep(20);
			inside.decrementAndGet();
			runs.incrementAndGet();
		});

		awaitCount(runs, 5);
		assertEquals(1, maxInside.get());
	}

	@Test
	public void aFailureIsReportedAndTheRepetitionContinues() throws Exception {
		AtomicInteger runs = new AtomicInteger();
		AtomicInteger failures = new AtomicInteger();
		AtomicReference<String> message = new AtomicReference<>();
		this.manager.setUnhandledFailureHandler((text, failure) -> {
			message.set(text + ":" + failure.getMessage());
			failures.incrementAndGet();
		});

		ScheduledTask task = this.manager.scheduleWithFixedDelay(ms(0), ms(20), () -> {
			runs.incrementAndGet();
			throw new IllegalStateException("boom");
		});

		awaitCount(runs, 3);
		awaitCount(failures, 3);
		assertTrue(message.get().endsWith(":boom"));
		assertFalse(task.isCancelled());
	}

	@Test
	public void aDelayTooLongForNanosecondsIsSaturatedSoTheTaskNeverFiresAndCanStillBeCancelled() throws Exception {
		AtomicInteger runs = new AtomicInteger();
		Duration endless = Duration.ofSeconds(Long.MAX_VALUE);

		ScheduledTask task = this.manager.scheduleWithFixedDelay(endless, endless, runs::incrementAndGet);

		Thread.sleep(150);
		assertEquals(0, runs.get());
		assertFalse(task.isCancelled());
		task.cancel();
		assertTrue(task.isCancelled());
	}

	@Test
	public void cancelStopsFurtherRuns() throws Exception {
		AtomicInteger runs = new AtomicInteger();
		ScheduledTask task = this.manager.scheduleWithFixedDelay(ms(0), ms(10), runs::incrementAndGet);
		awaitCount(runs, 2);

		task.cancel();
		task.cancel();
		Thread.sleep(100);
		int afterCancel = runs.get();
		Thread.sleep(200);

		assertTrue(task.isCancelled());
		assertEquals(afterCancel, runs.get());
	}

	@Test
	public void aTaskMayCancelItselfDuringARun() throws Exception {
		AtomicReference<ScheduledTask> self = new AtomicReference<>();
		AtomicInteger runs = new AtomicInteger();
		CountDownLatch scheduled = new CountDownLatch(1);
		self.set(this.manager.scheduleWithFixedDelay(ms(50), ms(10), () -> {
			awaitLatch(scheduled);
			runs.incrementAndGet();
			self.get().cancel();
		}));
		scheduled.countDown();

		awaitCount(runs, 1);
		Thread.sleep(200);

		assertEquals(1, runs.get());
		assertTrue(self.get().isCancelled());
	}

	@Test
	public void aDedicatedQueueTaskRunsOnTheThreadOfItsQueue() throws Exception {
		AtomicReference<String> thread = new AtomicReference<>();
		AtomicInteger runs = new AtomicInteger();

		this.manager.scheduleWithFixedDelay("PERIODIC_QUEUE", ms(0), ms(20), () -> {
			thread.set(Thread.currentThread().getName());
			runs.incrementAndGet();
		});

		awaitCount(runs, 2);
		assertTrue(thread.get(), thread.get().startsWith("covalent-async-PERIODIC_QUEUE-"));
	}

	@Test
	public void cancellingAnOwnerStopsOnlyItsTasks() throws Exception {
		AtomicInteger firstRuns = new AtomicInteger();
		AtomicInteger secondRuns = new AtomicInteger();
		ScheduledTask first = this.scheduleAs(FirstOwner.class, firstRuns);
		ScheduledTask second = this.scheduleAs(SecondOwner.class, secondRuns);
		awaitCount(firstRuns, 2);
		awaitCount(secondRuns, 2);

		this.manager.cancelPeriodicTasks(FirstOwner.class);
		Thread.sleep(100);
		int firstAfterCancel = firstRuns.get();
		int secondBefore = secondRuns.get();
		Thread.sleep(200);

		assertTrue(first.isCancelled());
		assertFalse(second.isCancelled());
		assertEquals(firstAfterCancel, firstRuns.get());
		assertTrue(secondRuns.get() > secondBefore);
	}

	@Test
	public void aTaskScheduledOutsideAnyComponentHasNoOwner() throws Exception {
		AtomicInteger runs = new AtomicInteger();
		ScheduledTask task = this.manager.scheduleWithFixedDelay(ms(0), ms(10), runs::incrementAndGet);
		awaitCount(runs, 1);

		this.manager.cancelPeriodicTasks(FirstOwner.class);

		assertFalse(task.isCancelled());
	}

	@Test
	public void stoppingTheWorkersCancelsEveryTask() throws Exception {
		AtomicInteger runs = new AtomicInteger();
		ScheduledTask general = this.manager.scheduleWithFixedDelay(ms(0), ms(10), runs::incrementAndGet);
		ScheduledTask dedicated = this.manager.scheduleWithFixedDelay("STOPPED_QUEUE", ms(0), ms(10),
				runs::incrementAndGet);
		awaitCount(runs, 4);

		this.manager.stopWorkers();
		this.manager.startWorkers();
		Thread.sleep(100);
		int afterRestart = runs.get();
		Thread.sleep(200);

		assertTrue(general.isCancelled());
		assertTrue(dedicated.isCancelled());
		assertEquals(afterRestart, runs.get());
	}

	@Test
	public void passivationCancelsEveryTaskAndRefusesNewOnes() throws Exception {
		AtomicInteger runs = new AtomicInteger();
		ScheduledTask task = this.manager.scheduleWithFixedDelay(ms(0), ms(10), runs::incrementAndGet);
		awaitCount(runs, 2);

		this.manager.passivate();

		assertTrue(task.isCancelled());
		assertThrows(RejectedExecutionException.class,
				() -> this.manager.scheduleWithFixedDelay(ms(0), ms(10), runs::incrementAndGet));
		this.manager.activate();
	}

	@Test
	public void aFullQueueSkipsARunAndTheTaskGoesOn() throws Exception {
		this.manager.setGeneralWorkerCount(1);
		this.manager.setWorkerQueueCapacity(1);
		this.manager.stopWorkers();
		this.manager.startWorkers();
		CountDownLatch blocked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		this.manager.fireAndForget(() -> {
			blocked.countDown();
			awaitLatch(release);
		});
		assertTrue(blocked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
		this.manager.fireAndForget(() -> {
		});
		AtomicInteger runs = new AtomicInteger();

		ScheduledTask task = this.manager.scheduleWithFixedDelay(ms(0), ms(10), runs::incrementAndGet);
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
		while (!this.manager.hadRejectedTasks() && System.nanoTime() < deadline) {
			Thread.sleep(5);
		}
		assertTrue(this.manager.hadRejectedTasks());
		assertEquals(0, runs.get());

		release.countDown();
		awaitCount(runs, 2);
		assertFalse(task.isCancelled());
	}

	@Test
	public void argumentsAreChecked() {
		Runnable task = () -> {
		};
		assertThrows(IllegalArgumentException.class,
				() -> this.manager.scheduleWithFixedDelay(ms(-1), ms(10), task));
		assertThrows(IllegalArgumentException.class, () -> this.manager.scheduleWithFixedDelay(ms(0), ms(0), task));
		assertThrows(IllegalArgumentException.class, () -> this.manager.scheduleWithFixedDelay(ms(0), ms(-5), task));
		assertThrows(IllegalArgumentException.class, () -> this.manager.scheduleWithFixedDelay(null, ms(10), task));
		assertThrows(IllegalArgumentException.class, () -> this.manager.scheduleWithFixedDelay(ms(0), null, task));
		assertThrows(IllegalArgumentException.class, () -> this.manager.scheduleWithFixedDelay(ms(0), ms(10), null));
		assertThrows(IllegalArgumentException.class,
				() -> this.manager.scheduleWithFixedDelay((String) null, ms(0), ms(10), task));
	}

	private ScheduledTask scheduleAs(final Class<?> owner, final AtomicInteger runs) {
		Class<?> previous = TaskSubmitter.enter(owner);
		try {
			return this.manager.scheduleWithFixedDelay(ms(0), ms(10), runs::incrementAndGet);
		} finally {
			TaskSubmitter.exit(previous);
		}
	}

	private static void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static void awaitLatch(final CountDownLatch latch) {
		try {
			latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static final class FirstOwner {
	}

	private static final class SecondOwner {
	}
}
