/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.ContainerStatus;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.ShutdownReport;
import org.opales.covalent.container.StartupReport;
import org.opales.covalent.container.async.AsyncParameters;
import org.opales.covalent.container.async.AsyncServices;
import org.opales.covalent.container.async.impl.AsyncManagerImpl;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.events.EventsManagerParameters;
import org.opales.covalent.container.events.impl.EventsManagerImpl;
import org.opales.covalent.container.impl.CovalentContainerImpl;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.lifecycle.impl.LifecycleManagerImpl;
import org.opales.covalent.container.tests.unit.data.ADisplayData;
import org.opales.covalent.container.tests.unit.events.AComponentEvents;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;

public class ContainerReportTests {

	private CovalentContainer container;

	private CapturingLogHandler lifecycleLogs;

	// EventsManagerImpl logs a rejected notification and a termination timeout
	// under its own class, and the async worker pool's own termination-timeout
	// warning logs under AsyncManagerImpl.class: both are exercised deliberately
	// by the tests below (a full dispatch queue, a stuck consumer callback), so
	// both need capturing here too, or their SEVERE-level output leaks to the
	// console despite being expected.
	private CapturingLogHandler containerLogs;

	private CapturingLogHandler eventsLogs;

	private CapturingLogHandler asyncLogs;

	@Before
	public void setUp() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		this.lifecycleLogs = CapturingLogHandler.install(LifecycleManagerImpl.class);
		this.containerLogs = CapturingLogHandler.install(CovalentContainerImpl.class);
		this.eventsLogs = CapturingLogHandler.install(EventsManagerImpl.class);
		this.asyncLogs = CapturingLogHandler.install(AsyncManagerImpl.class);
	}

	@After
	public void tearDown() {
		try {
			this.container.dispose();
		} finally {
			this.asyncLogs.restore();
			this.eventsLogs.restore();
			this.containerLogs.restore();
			this.lifecycleLogs.restore();
		}
	}

	@Test
	public void startupReportIsHealthyWhenApplicationStarts() {
		this.container.deployComponents(ApplicationComponentImpl.class);

		StartupReport report = this.container.start();

		Assert.assertEquals(ContainerStatus.healthy, report.getStatus());
		Assert.assertEquals(1, report.getApplicationComponentCount());
		Assert.assertEquals(Integer.valueOf(1), report.getComponentCounts().get(ComponentState.activated));
		Assert.assertTrue(report.getFailedComponents().isEmpty());
		Assert.assertTrue(report.getDurationSeconds() >= 0f);
	}

	@Test
	public void startupReportIsDegradedWhenApplicationComponentFails() {
		this.container.deployComponents(FailingApplicationComponentImpl.class);

		StartupReport report = this.container.start();

		Assert.assertEquals(ContainerStatus.degraded, report.getStatus());
		Assert.assertEquals(1, report.getApplicationComponentCount());
		Assert.assertEquals(Integer.valueOf(1), report.getComponentCounts().get(ComponentState.failed));
		Assert.assertEquals(1, report.getFailedComponents().size());
		Assert.assertEquals(FailingApplicationComponent.class, report.getFailedComponents().get(0));
	}

	@Test
	public void shutdownReportReflectsCleanShutdown() {
		this.container.deployComponents(ApplicationComponentImpl.class);
		this.container.start();

		ShutdownReport report = this.container.stop();

		Assert.assertEquals(ContainerStatus.healthy, report.getStatus());
		Assert.assertEquals(0, report.getApplicationComponentCount());
		Assert.assertTrue(report.getFailedComponents().isEmpty());
		Assert.assertTrue(report.getDurationSeconds() >= 0f);

		// This test exercises stop() itself; restart so tearDown()'s own,
		// unconditional stop() below does not run on an already-stopped
		// container.
		this.container.start();
	}

	@Test
	public void shutdownReportIsDegradedWhenAnAsyncTaskDoesNotHonorInterruption() throws Exception {
		// AsyncManagerImpl.passivate() blocks (bounded)
		// until its worker threads have actually terminated, but a task that
		// never checks Thread.interrupted() (or otherwise swallows
		// InterruptedException and keeps going) can still exceed that bound.
		// That must not be reported as an internal component failure - the
		// caller's task is at fault, not Covalent's own internal state - but
		// it does mean shutdown was not fully clean, so ShutdownReport must
		// be degraded rather than healthy.
		this.container.start();
		this.container.rcs().registerComponent(this);
		AsyncServices asyncServices = this.container.rcs().useServices(AsyncServices.class);
		String queueKey = "ASYNC_STUBBORN_TASK_NEVER_HONORS_INTERRUPTION";
		CountDownLatch taskStarted = new CountDownLatch(1);
		// far longer than the bounded wait of stop(), short enough not to outlive the test run
		long giveUpAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);

		asyncServices.submit(queueKey, (Runnable) () -> {
			taskStarted.countDown();
			while (System.nanoTime() < giveUpAt) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException deliberatelyIgnored) {
					// Simulates a task that does not honor interruption -
					// never lets the worker thread running it actually stop.
				}
			}
		});
		Assert.assertTrue(taskStarted.await(1, TimeUnit.SECONDS));

		ShutdownReport report = this.container.stop();

		Assert.assertEquals(ContainerStatus.degraded, report.getStatus());

		// The stubborn task's worker thread keeps running in the background until its own
		// deadline (it is a daemon thread, so it cannot keep the JVM alive): a task that
		// does not honor interruption cannot be stopped, which is the case under test.
		// Restart so tearDown()'s own, unconditional stop() below does not
		// run on an already-stopped container.
		this.container.start();
	}

	@Test
	public void shutdownReportIsDegradedWhenAnEventConsumerCallbackDoesNotHonorInterruption() throws Exception {
		// EventsManagerImpl.passivate() blocks (bounded) until its worker pool has actually
		// terminated, but a consumer callback that never checks
		// Thread.interrupted() (or otherwise swallows InterruptedException
		// and keeps going) can still exceed that bound. This must not be
		// reported as an internal component failure - the callback is at
		// fault, not Covalent's own internal state - but it does mean
		// shutdown was not fully clean, exactly like the equivalent async
		// case above.
		this.container.getParameters().setSynchronousEvents(false);
		this.container.start();
		this.container.rcs().registerComponent(ContainerReportTests.class, this);
		CountDownLatch callbackStarted = new CountDownLatch(1);
		try {
			AComponentEvents producer = this.container.rcs().produceEvents(ContainerReportTests.class,
					AComponentEvents.class);
			AComponentEvents consumer = new AComponentEvents() {
				@Override
				public void onNotification(final ADisplayData value) {
					callbackStarted.countDown();
					// Ignores interruption for longer than
					// EventsManagerImpl.WORKER_TERMINATION_TIMEOUT_MILLIS
					// (2s) - long enough to reliably exceed the bound
					// passivate() waits for and so exercise the degraded
					// path below, but bounded (unlike a truly endless
					// refusal) so this worker thread does not linger
					// forever afterwards: unlike a dedicated async worker
					// (named after its own queueKey), an events worker
					// thread's name carries no per-test qualifier, so an
					// endlessly stuck one would go on tripping other
					// tests' own "no leftover event worker threads" checks
					// for the rest of the suite.
					long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
					while (System.nanoTime() < deadlineNanos) {
						try {
							Thread.sleep(50);
						} catch (InterruptedException deliberatelyIgnored) {
							// Simulates a callback that does not honor
							// interruption right away.
						}
					}
				}

				@Override
				public void onNotification(final ADisplayData value, final boolean aValue) {
				}
			};
			this.container.rcs().subscribe(ContainerReportTests.class, AComponentEvents.class, consumer);

			producer.onNotification(new ADisplayData());
			Assert.assertTrue(callbackStarted.await(1, TimeUnit.SECONDS));

			ShutdownReport report = this.container.stop();

			Assert.assertEquals(ContainerStatus.degraded, report.getStatus());
		} finally {
			// The stubborn callback's worker thread self-terminates about 3s
			// after being interrupted (see above) - it is also a daemon
			// thread regardless, so it could never have kept the JVM alive
			// either way. Restart so tearDown()'s own, unconditional stop()
			// below does not run on an already-stopped container.
			this.container.start();
		}
	}

	@Test
	public void shutdownReportIsDegradedWhenTheEventsQueueOverflows() throws Exception {
		// An events dispatch queue
		// rejection must never impact the producer (fire-and-forget stays
		// intact - see EventsResourceLimitsContainerizedTests for that
		// guarantee), but in the absence of any throttling/backpressure QoS
		// on event production today, it is always a sign of a producer
		// outpacing its consumers - an infrastructure malfunction that must
		// still surface as ShutdownReport.degraded, exactly like the
		// worker-callback case above.
		this.container.start();
		this.container.rcs().registerComponent(this);
		EventsManagerParameters eventsParameters = this.container.rcs()
				.accessParameters(EventsManagerParameters.class);
		eventsParameters.setWorkerCount(1);
		eventsParameters.setWorkerQueueCapacity(1);
		this.container.getParameters().setSynchronousEvents(false);
		this.container.stop();
		this.container.start();
		this.container.rcs().registerComponent(ContainerReportTests.class, this);
		try {
			AComponentEvents producer = this.container.rcs().produceEvents(ContainerReportTests.class,
					AComponentEvents.class);

			CountDownLatch blockerStarted = new CountDownLatch(1);
			CountDownLatch releaseBlocker = new CountDownLatch(1);
			AComponentEvents consumer = new AComponentEvents() {
				private boolean first = true;

				@Override
				public synchronized void onNotification(final ADisplayData value) {
					if (this.first) {
						this.first = false;
						blockerStarted.countDown();
						try {
							releaseBlocker.await(5, TimeUnit.SECONDS);
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
					}
				}

				@Override
				public void onNotification(final ADisplayData value, final boolean aValue) {
				}
			};
			this.container.rcs().subscribe(ContainerReportTests.class, AComponentEvents.class, consumer);

			producer.onNotification(new ADisplayData()); // ties up the single worker
			Assert.assertTrue(blockerStarted.await(1, TimeUnit.SECONDS));
			producer.onNotification(new ADisplayData()); // fills the queue (capacity 1)
			producer.onNotification(new ADisplayData()); // must be dropped, not thrown

			releaseBlocker.countDown();

			ShutdownReport report = this.container.stop();

			Assert.assertEquals(ContainerStatus.degraded, report.getStatus());
		} finally {
			this.container.start();
		}
	}

	@Test
	public void shutdownReportIsDegradedWhenAnAsyncQueueOverflows() throws Exception {
		// Same rule as for the events queue above: a task refused by a full async queue is told to its caller by
		// an exception, which a fire-and-forget caller may ignore, so the container also reports the loss.
		this.container.start();
		this.container.rcs().registerComponent(ContainerReportTests.class, this);
		this.container.rcs().accessParameters(AsyncParameters.class).setWorkerQueueCapacity(1);
		AsyncServices async = this.container.rcs().useServices(AsyncServices.class);
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try {
			async.fireAndForget("report-overflow", () -> {
				started.countDown();
				try {
					release.await(5, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			});
			Assert.assertTrue(started.await(1, TimeUnit.SECONDS));
			async.fireAndForget("report-overflow", () -> {
			}); // fills the queue (capacity 1)
			Assert.assertThrows(java.util.concurrent.RejectedExecutionException.class,
					() -> async.fireAndForget("report-overflow", () -> {
					}));
			release.countDown();

			ShutdownReport report = this.container.stop();

			Assert.assertEquals(ContainerStatus.degraded, report.getStatus());
		} finally {
			release.countDown();
			this.container.start();
		}
	}

	@Component
	public interface ApplicationComponent {
	}

	@Implementation
	public static class ApplicationComponentImpl implements ApplicationComponent {
	}

	@Component
	public interface FailingApplicationComponent {
	}

	@Implementation
	public static class FailingApplicationComponentImpl implements FailingApplicationComponent {

		@Activation
		public void activate() {
			throw new IllegalStateException("expected application failure");
		}
	}

}
