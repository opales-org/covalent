/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.async.AsyncServices;
import org.opales.covalent.container.async.ScheduledTask;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.LifecycleServices;

/**
 * The periodic tasks of a managed component stop with the component: passivation cancels them without any
 * cleanup code in the component, and a reactivated component schedules them again.
 */
public class AsyncPeriodicTasksOwnershipTests {

	private static final AtomicInteger RUNS = new AtomicInteger();

	private static final AtomicReference<ScheduledTask> LAST_TASK = new AtomicReference<>();

	private CovalentContainer container;

	private LifecycleServices lifecycle;

	@Before
	public void setUp() {
		RUNS.set(0);
		LAST_TASK.set(null);
		this.container = Covalent.container();
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
		this.container.start();
		this.container.rcs().registerComponent(this);
		this.lifecycle = this.container.rcs().useServices(LifecycleServices.class);
	}

	@After
	public void tearDown() {
		this.container.dispose();
	}

	private static void awaitRunsAbove(final int count) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (RUNS.get() <= count) {
			Assert.assertTrue("periodic task did not run", System.nanoTime() < deadline);
			Thread.sleep(5);
		}
	}

	@Test
	public void passivatingTheOwnerCancelsItsTasksAndReactivationSchedulesThemAgain() throws Exception {
		this.lifecycle.deployComponent(PeriodicOwnerImpl.class);
		this.lifecycle.activateComponent(PeriodicOwner.class);
		awaitRunsAbove(1);
		ScheduledTask firstTask = LAST_TASK.get();
		Assert.assertFalse(firstTask.isCancelled());

		this.lifecycle.passivateComponent(PeriodicOwner.class);

		Assert.assertTrue(firstTask.isCancelled());
		// Runs already queued may finish; once the dust settles, nothing runs.
		Thread.sleep(100);
		int afterPassivation = RUNS.get();
		Thread.sleep(200);
		Assert.assertEquals(afterPassivation, RUNS.get());

		this.lifecycle.activateComponent(PeriodicOwner.class);
		awaitRunsAbove(afterPassivation);
		Assert.assertNotSame(firstTask, LAST_TASK.get());
		Assert.assertFalse(LAST_TASK.get().isCancelled());
	}

	@Test
	public void removingTheOwnerCancelsItsTasks() throws Exception {
		this.lifecycle.deployComponent(PeriodicOwnerImpl.class);
		this.lifecycle.activateComponent(PeriodicOwner.class);
		awaitRunsAbove(1);
		ScheduledTask task = LAST_TASK.get();

		this.lifecycle.passivateComponent(PeriodicOwner.class);
		this.lifecycle.removeComponent(PeriodicOwner.class);

		Assert.assertTrue(task.isCancelled());
	}

	@Test
	public void stoppingTheContainerCancelsTheTasks() throws Exception {
		this.lifecycle.deployComponent(PeriodicOwnerImpl.class);
		this.lifecycle.activateComponent(PeriodicOwner.class);
		awaitRunsAbove(1);
		ScheduledTask task = LAST_TASK.get();

		this.container.stop();

		Assert.assertTrue(task.isCancelled());
		this.container.start();
	}

	@Component
	public interface PeriodicOwner {
	}

	@Implementation
	public static class PeriodicOwnerImpl implements PeriodicOwner {

		@UsedServices
		private AsyncServices asyncServices;

		@Activation
		public void activate() {
			LAST_TASK.set(this.asyncServices.scheduleWithFixedDelay(Duration.ZERO, Duration.ofMillis(10),
					RUNS::incrementAndGet));
		}
	}
}
