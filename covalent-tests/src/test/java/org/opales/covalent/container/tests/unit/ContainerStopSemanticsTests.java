/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.async.AsyncServices;
import org.opales.covalent.container.tests.CovalentTests;
import org.opales.covalent.container.tests.unit.data.ADisplayData;
import org.opales.covalent.container.tests.unit.events.AComponentEvents;

/**
 * The infrastructure is always available to business components, started container or not: the
 * worker pools run threads on demand outside {@code start()}/{@code stop()}, {@code start()}
 * starts them ahead, and {@code stop()} leaves the container as a new one, without worker
 * threads.
 */
public class ContainerStopSemanticsTests extends CovalentTests {

	/** A raw business component. */
	public static class Business {
	}

	private final Set<Thread> threadsBefore = workerThreads();

	private final CovalentContainer container = Covalent.container();

	@After
	public void disposeContainer() {
		this.container.dispose();
	}

	@Test
	public void aNewContainerHasNoWorkerThread() {
		Assert.assertEquals(Set.of(), this.newWorkerThreads());
	}

	@Test
	public void aBusinessComponentActiveBeforeStartRunsAsyncTasks() throws Exception {
		this.container.rcs().registerComponent(Business.class, new Business());

		this.assertRunsAnAsyncTask();
	}

	@Test
	public void aBusinessComponentActiveBeforeStartReceivesAsyncEvents() throws Exception {
		this.container.getParameters().setSynchronousEvents(false);
		this.container.rcs().registerComponent(Business.class, new Business());

		this.assertDeliversAnAsyncNotification();
	}

	@Test
	public void aStoppedContainerRunsTheAsyncTasksOfANewBusinessComponent() throws Exception {
		this.container.start();
		this.container.stop();
		this.container.rcs().registerComponent(Business.class, new Business());

		this.assertRunsAnAsyncTask();
	}

	@Test
	public void aStoppedContainerDeliversTheAsyncEventsOfANewBusinessComponent() throws Exception {
		this.container.getParameters().setSynchronousEvents(false);
		this.container.start();
		this.container.stop();
		this.container.rcs().registerComponent(Business.class, new Business());

		this.assertDeliversAnAsyncNotification();
	}

	@Test
	public void startStartsTheWorkerThreadsAndStopStopsThem() {
		this.container.start();
		Assert.assertNotEquals(Set.of(), this.newWorkerThreads());

		this.container.stop();

		Assert.assertEquals(Set.of(), this.newWorkerThreads());
	}

	@Test
	public void stopStopsTheThreadsStartedOnDemand() throws Exception {
		this.container.rcs().registerComponent(Business.class, new Business());
		this.assertRunsAnAsyncTask();
		this.container.start();

		this.container.stop();

		Assert.assertEquals(Set.of(), this.newWorkerThreads());
	}

	private void assertRunsAnAsyncTask() throws Exception {
		// this test, registered as a raw component, is the user of the service
		this.container.rcs().registerComponent(this);
		AsyncServices asyncServices = this.container.rcs().useServices(AsyncServices.class);
		CountDownLatch ran = new CountDownLatch(1);
		asyncServices.submit((Runnable) ran::countDown);
		Assert.assertTrue("the task must run", ran.await(2, TimeUnit.SECONDS));
	}

	private void assertDeliversAnAsyncNotification() throws Exception {
		AComponentEvents producer = this.container.rcs().produceEvents(Business.class, AComponentEvents.class);
		CountDownLatch delivered = new CountDownLatch(1);
		this.container.rcs().subscribe(Business.class, AComponentEvents.class, new AComponentEvents() {
			@Override
			public void onNotification(final ADisplayData value) {
				delivered.countDown();
			}

			@Override
			public void onNotification(final ADisplayData value, final boolean aValue) {
			}
		});
		producer.onNotification(new ADisplayData());
		Assert.assertTrue("the notification must be delivered", delivered.await(2, TimeUnit.SECONDS));
	}

	private Set<Thread> newWorkerThreads() {
		Set<Thread> threads = workerThreads();
		threads.removeAll(this.threadsBefore);
		return threads;
	}

	private static Set<Thread> workerThreads() {
		return Thread.getAllStackTraces().keySet().stream().filter(Thread::isAlive)
				.filter(thread -> thread.getName().startsWith("covalent-events-async-")
						|| thread.getName().startsWith("covalent-async-"))
				.collect(Collectors.toSet());
	}
}
