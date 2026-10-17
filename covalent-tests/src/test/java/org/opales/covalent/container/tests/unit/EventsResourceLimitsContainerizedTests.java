/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.events.EventsManagerParameters;
import org.opales.covalent.container.events.EventsWorkersServices;
import org.opales.covalent.container.impl.CovalentContainerImpl;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.CovalentContainerizedTests;
import org.opales.covalent.container.tests.unit.data.ADisplayData;
import org.opales.covalent.container.tests.unit.events.AComponentEvents;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;

/**
 * The events dispatch worker count and queue capacity must be
 * configurable, in the same spirit as {@code AsyncParameters}, and the
 * queue must be bounded - but unlike the async worker pool, a queue-full
 * condition must never propagate back to the producer: event delivery
 * stays strictly fire-and-forget even under sustained overload (the
 * notification is dropped and logged instead - see
 * {@code EventsManagerImpl.onEventRejected} and, for the resulting
 * degraded {@code ShutdownReport}, {@code ContainerReportTests}).
 */
public class EventsResourceLimitsContainerizedTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	private int originalWorkerCount;

	private int originalWorkerQueueCapacity;

	// EventsManagerImpl logs under CovalentContainerImpl.class (see its own
	// field), not its own class - this test deliberately overflows the
	// dispatch queue (workerCountOfOneSerializesEventDelivery... and the
	// capacity test below), which logs a SEVERE "Event notification
	// dropped" message by design; capture it so the console stays clean.
	private CapturingLogHandler eventsLogs;

	private boolean originalTaskTracking;

	@Before
	public void setUp() {
		this.rcs.registerComponent(this);
		this.eventsLogs = CapturingLogHandler.install(CovalentContainerImpl.class);
		this.cm.getParameters().setSynchronousEvents(false);
		EventsManagerParameters parameters = this.accessEventsManagerParameters();
		this.originalWorkerCount = parameters.getWorkerCount();
		this.originalWorkerQueueCapacity = parameters.getWorkerQueueCapacity();
		this.originalTaskTracking = parameters.isTaskTracking();
		// Several tests below produce events under this test class as an
		// explicit, otherwise-unregistered componentType - mirrors
		// EventsManagerContainerizedTests's own setup.
		this.rcs.registerComponent(EventsResourceLimitsContainerizedTests.class, this);
	}

	@After
	public void tearDown() {
		this.rcs.unregisterComponent(EventsResourceLimitsContainerizedTests.class);
		this.rcs.registerComponent(this);
		EventsManagerParameters parameters = this.accessEventsManagerParameters();
		parameters.setWorkerCount(this.originalWorkerCount);
		parameters.setWorkerQueueCapacity(this.originalWorkerQueueCapacity);
		parameters.setTaskTracking(this.originalTaskTracking);
		this.cm.stop();
		this.cm.start();
		this.eventsLogs.restore();
	}

	@Test
	public void workerCountOfOneSerializesEventDelivery() throws Exception {
		this.restartWith(1, this.originalWorkerQueueCapacity);
		AComponentEvents producer = this.produceEvents();

		AtomicInteger callIndex = new AtomicInteger();
		CountDownLatch firstStarted = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		CountDownLatch secondStarted = new CountDownLatch(1);
		this.subscribe(new AComponentEvents() {
			@Override
			public void onNotification(final ADisplayData value) {
				if (callIndex.getAndIncrement() == 0) {
					firstStarted.countDown();
					try {
						releaseFirst.await(5, TimeUnit.SECONDS);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				} else {
					secondStarted.countDown();
				}
			}

			@Override
			public void onNotification(final ADisplayData value, final boolean aValue) {
			}
		});

		producer.onNotification(new ADisplayData());
		Assert.assertTrue(firstStarted.await(1, TimeUnit.SECONDS));
		producer.onNotification(new ADisplayData());

		Assert.assertFalse("with a single events worker, the second delivery must wait for the first",
				secondStarted.await(200, TimeUnit.MILLISECONDS));

		releaseFirst.countDown();
		Assert.assertTrue(secondStarted.await(1, TimeUnit.SECONDS));
	}

	@Test
	public void workerCountOfThreeAllowsThreeConcurrentDeliveries() throws Exception {
		this.restartWith(3, this.originalWorkerQueueCapacity);
		AComponentEvents producer = this.produceEvents();

		CountDownLatch allStarted = new CountDownLatch(3);
		CountDownLatch release = new CountDownLatch(1);
		this.subscribe(new AComponentEvents() {
			@Override
			public void onNotification(final ADisplayData value) {
				allStarted.countDown();
				try {
					release.await(5, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}

			@Override
			public void onNotification(final ADisplayData value, final boolean aValue) {
			}
		});

		for (int i = 0; i < 3; i++) {
			producer.onNotification(new ADisplayData());
		}

		Assert.assertTrue("all 3 deliveries should be able to run concurrently on 3 events workers",
				allStarted.await(1, TimeUnit.SECONDS));
		release.countDown();
	}

	@Test
	public void eventDeliveryNeverThrowsToTheProducerWhenTheDispatchQueueIsFull() throws Exception {
		this.restartWith(1, 2);
		AComponentEvents producer = this.produceEvents();

		CountDownLatch blockerStarted = new CountDownLatch(1);
		CountDownLatch releaseBlocker = new CountDownLatch(1);
		CountDownLatch delivered = new CountDownLatch(3);
		AtomicInteger deliveredCount = new AtomicInteger();
		this.subscribe(new AComponentEvents() {
			private boolean first = true;

			@Override
			public synchronized void onNotification(final ADisplayData value) {
				deliveredCount.incrementAndGet();
				delivered.countDown();
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
		});

		// ties up the single worker thread
		producer.onNotification(new ADisplayData());
		Assert.assertTrue(blockerStarted.await(1, TimeUnit.SECONDS));

		// the queue (capacity 2) can still hold 2 more while the worker is busy
		producer.onNotification(new ADisplayData());
		producer.onNotification(new ADisplayData());

		// this one must be silently dropped, never thrown back here - fire-and-forget
		// stays intact even though the queue is now full.
		producer.onNotification(new ADisplayData());

		releaseBlocker.countDown();
		Assert.assertTrue("expected exactly the 3 deliveries the queue could actually hold", delivered.await(2,
				TimeUnit.SECONDS));
		// give a dropped 4th notification a chance to (wrongly) show up before asserting
		// it never does.
		Thread.sleep(300);
		Assert.assertEquals("the notification beyond capacity must have been dropped, not delivered", 3,
				deliveredCount.get());
	}

	@Test
	public void workerCountChangesApplyWithoutRestart() throws Exception {
		AComponentEvents producer = this.produceEvents();
		this.applyWithoutRestart(1, this.originalWorkerQueueCapacity);

		AtomicInteger callIndex = new AtomicInteger();
		CountDownLatch firstStarted = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		CountDownLatch secondStarted = new CountDownLatch(1);
		this.subscribe(this.blockingFirstConsumer(callIndex, firstStarted, releaseFirst, secondStarted));
		producer.onNotification(new ADisplayData());
		Assert.assertTrue(firstStarted.await(1, TimeUnit.SECONDS));
		producer.onNotification(new ADisplayData());
		Assert.assertFalse("with a single events worker, the second delivery must wait for the first",
				secondStarted.await(200, TimeUnit.MILLISECONDS));
		releaseFirst.countDown();
		Assert.assertTrue(secondStarted.await(1, TimeUnit.SECONDS));
	}

	@Test
	public void growingTheWorkerCountAllowsConcurrentDeliveriesWithoutRestart() throws Exception {
		AComponentEvents producer = this.produceEvents();
		this.applyWithoutRestart(1, this.originalWorkerQueueCapacity);
		this.applyWithoutRestart(3, this.originalWorkerQueueCapacity);

		CountDownLatch allStarted = new CountDownLatch(3);
		CountDownLatch release = new CountDownLatch(1);
		this.subscribe(this.blockingConsumer(allStarted, release, new CountDownLatch(3)));
		for (int i = 0; i < 3; i++) {
			producer.onNotification(new ADisplayData());
		}
		Assert.assertTrue("all 3 deliveries should run concurrently once grown to 3 events workers",
				allStarted.await(1, TimeUnit.SECONDS));
		release.countDown();
	}

	@Test
	public void shrinkingTheWorkerCountLetsRunningDeliveriesFinish() throws Exception {
		AComponentEvents producer = this.produceEvents();
		this.applyWithoutRestart(3, this.originalWorkerQueueCapacity);

		CountDownLatch allStarted = new CountDownLatch(3);
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch finished = new CountDownLatch(3);
		this.subscribe(this.blockingConsumer(allStarted, release, finished));
		for (int i = 0; i < 3; i++) {
			producer.onNotification(new ADisplayData());
		}
		Assert.assertTrue(allStarted.await(1, TimeUnit.SECONDS));

		this.applyWithoutRestart(1, this.originalWorkerQueueCapacity);
		release.countDown();
		Assert.assertTrue("deliveries running when the pool shrank must complete", finished.await(2, TimeUnit.SECONDS));
	}

	@Test
	public void queueCapacityChangeAppliesWithoutRestart() throws Exception {
		AComponentEvents producer = this.produceEvents();
		this.applyWithoutRestart(1, 2);

		CountDownLatch blockerStarted = new CountDownLatch(1);
		CountDownLatch releaseBlocker = new CountDownLatch(1);
		CountDownLatch delivered = new CountDownLatch(3);
		AtomicInteger deliveredCount = new AtomicInteger();
		this.subscribe(new AComponentEvents() {
			private boolean first = true;

			@Override
			public synchronized void onNotification(final ADisplayData value) {
				deliveredCount.incrementAndGet();
				delivered.countDown();
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
		});

		producer.onNotification(new ADisplayData());
		Assert.assertTrue(blockerStarted.await(1, TimeUnit.SECONDS));
		producer.onNotification(new ADisplayData());
		producer.onNotification(new ADisplayData());
		producer.onNotification(new ADisplayData());

		releaseBlocker.countDown();
		Assert.assertTrue(delivered.await(2, TimeUnit.SECONDS));
		Thread.sleep(300);
		Assert.assertEquals("the notification beyond capacity must have been dropped, not delivered", 3,
				deliveredCount.get());
	}

	@Test(timeout = 10000)
	public void aSubscriptionDroppedByAFullDispatchQueueDoesNotBlockTheSubscriber() throws Exception {
		this.restartWith(1, 1);
		// Task tracking counts the completed tasks of the events pool, see awaitCompletedEventTasks.
		this.accessEventsManagerParameters().setTaskTracking(true);
		EventsWorkersServices workers = this.rcs.useServices(EventsWorkersServices.class);
		long completedBeforeSubscribe = completedEventTasks(workers);
		AComponentEvents producer = this.rcs.produceEvents(EventsResourceLimitsContainerizedTests.class,
				AComponentEvents.class, delegateConsumer -> {
				});
		CountDownLatch blockerStarted = new CountDownLatch(1);
		CountDownLatch releaseBlocker = new CountDownLatch(1);
		this.subscribe(new AComponentEvents() {
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
		});
		// The subscription becomes active inside its subscribe delegate notification, run on the single worker.
		// Until then an event fired for it is buffered by the subscription rather than queued, and the queue
		// below would not fill up.
		awaitCompletedEventTasks(workers, completedBeforeSubscribe + 1);
		// ties up the single worker, then fills the queue (capacity 1)
		producer.onNotification(new ADisplayData());
		Assert.assertTrue(blockerStarted.await(1, TimeUnit.SECONDS));
		producer.onNotification(new ADisplayData());

		AtomicInteger lateDeliveries = new AtomicInteger();
		CountDownLatch subscribeReturned = new CountDownLatch(1);
		Thread subscriber = new Thread(() -> {
			this.subscribe(new AComponentEvents() {
				@Override
				public void onNotification(final ADisplayData value) {
					lateDeliveries.incrementAndGet();
				}

				@Override
				public void onNotification(final ADisplayData value, final boolean aValue) {
				}
			});
			subscribeReturned.countDown();
		}, "late-subscriber");
		subscriber.setDaemon(true);
		subscriber.start();

		boolean returned = subscribeReturned.await(2, TimeUnit.SECONDS);
		releaseBlocker.countDown();
		Assert.assertTrue("a subscribe whose delegate notification was dropped must not block its caller",
				returned);

		producer.onNotification(new ADisplayData());
		Thread.sleep(300);
		Assert.assertEquals("a subscription whose delegate notification was dropped must be cancelled", 0,
				lateDeliveries.get());
	}

	private AComponentEvents blockingFirstConsumer(final AtomicInteger callIndex, final CountDownLatch firstStarted,
			final CountDownLatch releaseFirst, final CountDownLatch secondStarted) {
		return new AComponentEvents() {
			@Override
			public void onNotification(final ADisplayData value) {
				if (callIndex.getAndIncrement() == 0) {
					firstStarted.countDown();
					try {
						releaseFirst.await(5, TimeUnit.SECONDS);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				} else {
					secondStarted.countDown();
				}
			}

			@Override
			public void onNotification(final ADisplayData value, final boolean aValue) {
			}
		};
	}

	private AComponentEvents blockingConsumer(final CountDownLatch started, final CountDownLatch release,
			final CountDownLatch finished) {
		return new AComponentEvents() {
			@Override
			public void onNotification(final ADisplayData value) {
				started.countDown();
				try {
					release.await(5, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				finished.countDown();
			}

			@Override
			public void onNotification(final ADisplayData value, final boolean aValue) {
			}
		};
	}

	private void applyWithoutRestart(final int workerCount, final int workerQueueCapacity) {
		EventsManagerParameters parameters = this.accessEventsManagerParameters();
		parameters.setWorkerCount(workerCount);
		parameters.setWorkerQueueCapacity(workerQueueCapacity);
	}

	private void restartWith(final int workerCount, final int workerQueueCapacity) {
		EventsManagerParameters parameters = this.accessEventsManagerParameters();
		parameters.setWorkerCount(workerCount);
		parameters.setWorkerQueueCapacity(workerQueueCapacity);
		this.cm.stop();
		this.cm.start();
		// A container restart tears down every raw component registration
		// along with everything else - re-register so produceEvents/subscribe
		// below have a valid componentType again.
		this.rcs.registerComponent(EventsResourceLimitsContainerizedTests.class, this);
	}

	private static long completedEventTasks(final EventsWorkersServices workers) {
		return workers.readWorkerPools().get(0).completedTasks();
	}

	private static void awaitCompletedEventTasks(final EventsWorkersServices workers, final long count) {
		while (completedEventTasks(workers) < count) {
			Thread.onSpinWait();
		}
	}

	private AComponentEvents produceEvents() {
		return this.rcs.produceEvents(EventsResourceLimitsContainerizedTests.class, AComponentEvents.class);
	}

	private void subscribe(final AComponentEvents consumer) {
		this.rcs.subscribe(EventsResourceLimitsContainerizedTests.class, AComponentEvents.class, consumer);
	}

	private EventsManagerParameters accessEventsManagerParameters() {
		return Covalent.main.rcs().accessParameters(EventsManagerParameters.class);
	}

}
