/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.events.EventsManagerEvents;
import org.opales.covalent.container.events.EventsManagerParameters;
import org.opales.covalent.container.events.SubscribeParameters;
import org.opales.covalent.container.events.impl.ProducerSideNotifierInvocationHandler;
import org.opales.covalent.container.exceptions.NamingException;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.CovalentContainerizedTests;
import org.opales.covalent.container.tests.unit.data.AData;
import org.opales.covalent.container.tests.unit.data.ADisplayData;
import org.opales.covalent.container.tests.unit.events.AComponentEvents;
import org.opales.covalent.container.tests.unit.events.AComponentEvents.AComponentEventsFilter;
import org.opales.covalent.container.tests.unit.events.BComponentEvents;
import org.opales.covalent.container.tests.unit.events.WrongNamedEvents;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;

public class EventsManagerContainerizedTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	@Before
	public void setup() {
		this.cm.getParameters().setSynchronousEvents(false);
		// Several tests below produce events under this test class as an explicit,
		// otherwise-unregistered componentType - purely to pair produceEvents/
		// subscribe calls. RawPortSupport.requireKnownComponentType requires
		// componentType to actually be registered before a producer-side port can be
		// created for it (see RawComponentsPortManagedTypeRedirectTests), so this
		// class registers and unregisters itself as a raw component around every
		// test here rather than each test doing it individually.
		this.rcs.registerComponent(EventsManagerContainerizedTests.class, this);
	}

	@After
	public void tearDown() {
		this.rcs.unregisterComponent(EventsManagerContainerizedTests.class);
	}

	@Test
	public void wrongEventNamingIsAcceptedWithSilentRule() {
		this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.silent);
		this.cm.getParameters().setSynchronousEvents(true);
		WrongNamedEvents produce = Covalent.main.rcs().produceEvents(EventsManagerContainerizedTests.class,
				WrongNamedEvents.class);
		WrongNamedEventsMock consumer = new WrongNamedEventsMock();

		this.rcs.subscribe(EventsManagerContainerizedTests.class, WrongNamedEvents.class, consumer);

		produce.badNamedonNotification("event");

		Assert.assertTrue(consumer.received);
	}

	@Test
	public void wrongEventMethodNamingIsRejectedWithStrictRule() {
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.strict);
		this.cm.getParameters().setSynchronousEvents(true);
		Assert.assertThrows(NamingException.class, () -> Covalent.main.rcs()
				.produceEvents(EventsManagerContainerizedTests.class, WrongNamedEvents.class));
	}

	@Test
	public void subscribeParametersEventTest() throws InterruptedException {
		AComponentEvents produce = Covalent.main.rcs().produceEvents(EventsManagerContainerizedTests.class, AComponentEvents.class);

		EventConsumer consumer = new EventConsumer(1, 1);
		SubscribeParameters subscribeParameters = this.cm.getParameters().newSubscribeParameters();
		subscribeParameters.setEventFilter(new AComponentEventsFilter() {

			@Override
			public boolean filterNotification(final ADisplayData value) {
				// filter if value.visible is true;
				return value.isVisible() == false;
			}

			@Override
			public boolean filterNotification(final ADisplayData value, final boolean aValue) {
				// never filter this event
				return false;
			}
		});
		this.rcs.subscribe(EventsManagerContainerizedTests.class, AComponentEvents.class, consumer, subscribeParameters);

		ADisplayData d = new ADisplayData();
		d.setVisible(true);
		// onNotification and onNotification2 shall be observed
		produce.onNotification(d);
		produce.onNotification(d, true);

		consumer.await();
		Assert.assertEquals(d, consumer.received);
		Assert.assertEquals(d, consumer.received2);
		Assert.assertEquals(2, consumer.count.get());

		consumer.reset(0, 1);

		ADisplayData d2 = new ADisplayData();
		d2.setVisible(false);
		// only onNotification2 shall be observed
		produce.onNotification(d2);
		produce.onNotification(d2, true);

		consumer.await();
		Assert.assertNull(consumer.received);
		Assert.assertEquals(d2, consumer.received2);
		Assert.assertEquals(1, consumer.count.get());

		this.rcs.unsubscribe(EventsManagerContainerizedTests.class, AComponentEvents.class, consumer);
	}

	@Test
	public void nominalEventTest() throws InterruptedException {
		AComponentEvents produce = Covalent.main.rcs().produceEvents(EventsManagerContainerizedTests.class, AComponentEvents.class);
		EventConsumer consumer = new EventConsumer();
		this.rcs.subscribe(EventsManagerContainerizedTests.class, AComponentEvents.class, consumer);

		ADisplayData d = new ADisplayData();
		produce.onNotification(d);
		consumer.await();
		Assert.assertEquals(d, consumer.received);

		consumer.reset();

		ADisplayData d2 = new ADisplayData();
		produce.onNotification(d2);
		consumer.await();
		Assert.assertEquals(d2, consumer.received);

		this.rcs.unsubscribe(EventsManagerContainerizedTests.class, AComponentEvents.class, consumer);
	}

	@Test
	public void overloadEventTest() throws InterruptedException {
		int eventCount = 100_000;

		// This stress test intentionally produces far more events, in one
		// tight burst, than the default events dispatch queue capacity
		// (1024)
		// can hold - it exists to verify every subscriber still receives
		// every event under heavy concurrent production, not to exercise
		// the bounded-queue drop behavior (separately covered by
		// EventsResourceLimitsContainerizedTests), so it widens the queue
		// enough to never trigger a drop, restoring the original capacity
		// once done.
		EventsManagerParameters eventsParameters = Covalent.main.rcs().accessParameters(EventsManagerParameters.class);
		int originalWorkerQueueCapacity = eventsParameters.getWorkerQueueCapacity();
		eventsParameters.setWorkerQueueCapacity(eventCount + 1_000);
		this.cm.stop();
		this.cm.start();
		// A container restart tears down every raw component registration -
		// re-register so produceEvents/subscribe below have a valid
		// componentType again (setUp()'s own registration was just
		// invalidated by the restart above).
		this.rcs.registerComponent(EventsManagerContainerizedTests.class, this);
		try {
			AComponentEvents produce = Covalent.main.rcs().produceEvents(EventsManagerContainerizedTests.class, AComponentEvents.class);
			BComponentEvents produce2 = Covalent.main.rcs().produceEvents(EventsManagerContainerizedTests.class,
					BComponentEvents.class);

			List<EventConsumer> consumers = new ArrayList<>();
			List<EventConsumer2> consumers2 = new ArrayList<>();

			for (int i = 0; i < 1; i++) {
				EventConsumer consumer = new EventConsumer(eventCount, 0);
				this.rcs.subscribe(EventsManagerContainerizedTests.class, AComponentEvents.class, consumer);
				consumers.add(consumer);

				EventConsumer2 consumer2 = new EventConsumer2(eventCount);
				this.rcs.subscribe(EventsManagerContainerizedTests.class, BComponentEvents.class, consumer2);
				consumers2.add(consumer2);
			}

			List<Integer> notified = new ArrayList<>();
			for (int i = 0; i < eventCount; i++) {
				notified.add(i);
			}

			notified.parallelStream().forEach(i -> {
				produce.onNotification(this.newDisplayData());
				produce2.onNotificationB(this.newDisplayData());
			});

			for (EventConsumer consumer : consumers) {
				consumer.await();
				Assert.assertEquals(eventCount, consumer.count.get());
			}

			for (EventConsumer2 consumer : consumers2) {
				consumer.await();
				Assert.assertEquals(eventCount, consumer.count.get());
			}

			for (EventConsumer consumer : consumers) {
				this.rcs.unsubscribe(EventsManagerContainerizedTests.class, AComponentEvents.class, consumer);
			}
			for (EventConsumer2 consumer : consumers2) {
				this.rcs.unsubscribe(EventsManagerContainerizedTests.class, BComponentEvents.class, consumer);
			}

			Covalent.main.rcs().unproduceEvents(EventsManagerContainerizedTests.class, BComponentEvents.class);
			Covalent.main.rcs().unproduceEvents(EventsManagerContainerizedTests.class, AComponentEvents.class);
		} finally {
			// the eventsParameters proxy obtained before the restart above was
			// invalidated by it - resolve a fresh one before restoring the
			// original capacity (mirrors
			// AsyncResourceLimitsContainerizedTests.stopCancelsPendingGeneralTasks).
			Covalent.main.rcs().accessParameters(EventsManagerParameters.class)
					.setWorkerQueueCapacity(originalWorkerQueueCapacity);
		}
	}

	@Test
	public void eventInstrumentationReportsFailuresAndDuration() throws InterruptedException {
		Covalent.main.getParameters().setInstrumented(true);
		this.cm.getParameters().setSynchronousEvents(true);

		EventsManagerEventsConsumer eventsManagerEventsConsumer = new EventsManagerEventsConsumer();
		FailingEventConsumer failingConsumer = new FailingEventConsumer();
		this.rcs.subscribe(EventsManagerEvents.class, eventsManagerEventsConsumer);

		AComponentEvents produce = Covalent.main.rcs().produceEvents(EventsManagerContainerizedTests.class, AComponentEvents.class);
		this.rcs.subscribe(EventsManagerContainerizedTests.class, AComponentEvents.class, failingConsumer);
		CapturingLogHandler logHandler = CapturingLogHandler.install(ProducerSideNotifierInvocationHandler.class);
		try {
			CountDownLatch latch = new CountDownLatch(2);
			eventsManagerEventsConsumer.reset();
			eventsManagerEventsConsumer.setLatch(latch);

			produce.onNotification(this.newDisplayData());
			Assert.assertTrue("Timed out waiting for the expected events.", latch.await(5, TimeUnit.SECONDS));

			Assert.assertTrue(eventsManagerEventsConsumer.eventsFired);
			Assert.assertTrue(eventsManagerEventsConsumer.eventNotificationFinished);
			Assert.assertEquals(AComponentEvents.class, eventsManagerEventsConsumer.firedEvents);
			Assert.assertTrue(eventsManagerEventsConsumer.durationNanos >= 0);
			Assert.assertTrue(eventsManagerEventsConsumer.queueWaitNanos >= 0);
			Assert.assertSame(failingConsumer.failure, eventsManagerEventsConsumer.failure);
			Assert.assertTrue(logHandler.containsSevereMessageWithFailure("Event consumer callback failed.",
					"expected event failure"));
		} finally {
			logHandler.restore();
			this.rcs.unsubscribe(EventsManagerContainerizedTests.class, AComponentEvents.class, failingConsumer);
			Covalent.main.rcs().unproduceEvents(EventsManagerContainerizedTests.class, AComponentEvents.class);
			this.cm.getParameters().setSynchronousEvents(false);
			Covalent.main.getParameters().setInstrumented(false);
		}
	}

	private static class WrongNamedEventsMock implements WrongNamedEvents {
		public boolean received = false;

		@Override
		public void badNamedonNotification(final String value) {
			this.received = true;
		}
	}

	private ADisplayData newDisplayData() {
		ADisplayData d = new ADisplayData();
		d.setKey(UUID.randomUUID());
		d.setVisible(true);
		AData data = new AData();
		data.setLat(1);
		data.setLon(2);
		data.setName("TEST");
		for (int i = 0; i < 120; i++) {
			data.getDatas().add("A");
		}
		d.setData(data);
		return d;
	}

	class EventConsumer implements AComponentEvents {
		ADisplayData received = null;
		ADisplayData received2 = null;
		AtomicInteger count = new AtomicInteger(0);

		CountDownLatch eventTriggered;
		CountDownLatch event2Triggered;

		public EventConsumer() {
			this(1, 0);
		}

		public EventConsumer(final int reqNbEvents, final int reqNbEvents2) {
			this.eventTriggered = new CountDownLatch(reqNbEvents);
			this.event2Triggered = new CountDownLatch(reqNbEvents2);
		}

		public void reset() {
			this.reset(1, 0);
		}

		public void reset(final int reqNbEvents, final int reqNbEvents2) {
			this.received = null;
			this.received2 = null;
			this.eventTriggered = new CountDownLatch(reqNbEvents);
			this.event2Triggered = new CountDownLatch(reqNbEvents2);
			this.count.set(0);
		}

		@Override
		public void onNotification(final ADisplayData value) {
			this.received = value;
			this.count.incrementAndGet();
			this.eventTriggered.countDown();
		}

		public void await() throws InterruptedException {
			if (this.eventTriggered.getCount() != 0) {
				Assert.assertTrue("Timed out waiting for the expected events.", this.eventTriggered.await(5, TimeUnit.SECONDS));
			}
			if (this.event2Triggered.getCount() != 0) {
				Assert.assertTrue("Timed out waiting for the expected events.", this.event2Triggered.await(5, TimeUnit.SECONDS));
			}
		}

		@Override
		public void onNotification(final ADisplayData value, final boolean aValue) {
			this.received2 = value;
			this.count.incrementAndGet();
			this.event2Triggered.countDown();
		}

	}

	class EventConsumer2 implements BComponentEvents {
		ADisplayData received = null;
		AtomicInteger count = new AtomicInteger(0);

		CountDownLatch eventTriggered;

		public EventConsumer2() {
			this(1);
		}

		public EventConsumer2(final int reqNbEvents) {
			this.eventTriggered = new CountDownLatch(reqNbEvents);
		}

		public void reset() {
			this.reset(1);
		}

		public void reset(final int reqNbEvents) {
			this.eventTriggered = new CountDownLatch(reqNbEvents);
			this.count.set(0);
		}

		@Override
		public void onNotificationB(final ADisplayData value) {
			this.received = value;
			this.count.incrementAndGet();
			this.eventTriggered.countDown();
		}

		public void await() throws InterruptedException {
			Assert.assertTrue("Timed out waiting for the expected events.", this.eventTriggered.await(5, TimeUnit.SECONDS));
		}
	}

	class EventsManagerEventsConsumer implements EventsManagerEvents {
		CountDownLatch latch;

		void setLatch(final CountDownLatch latch) {
			this.latch = latch;
		}

		public void reset() {
			this.eventsProduced = false;
			this.eventsUnproduced = false;
			this.eventsConsumed = false;
			this.eventsUnconsumed = false;
			this.producedEvents = null;
			this.consumedEvents = null;
			this.firedEvents = null;
			this.producedCount = 0;
			this.unProducedCount = 0;
			this.consumedCount = 0;
			this.unconsumedCount = 0;
			this.eventsFired = false;
			this.eventNotificationFinished = false;
			this.durationNanos = -1;
			this.queueWaitNanos = -1;
			this.failure = null;
		}

		boolean eventsProduced;
		boolean eventsUnproduced;
		boolean eventsConsumed;
		boolean eventsUnconsumed;
		boolean eventsFired;
		boolean eventNotificationFinished;

		Class<?> producedEvents;
		Class<?> consumedEvents;
		Class<?> firedEvents;
		Throwable failure;
		long durationNanos = -1;
		long queueWaitNanos = -1;

		int producedCount = 0;
		int unProducedCount = 0;
		int consumedCount = 0;
		int unconsumedCount = 0;

		@Override
		public <EVENTS> void onEventsProduced(final Class<?> componentType, final Class<EVENTS> eventsInterface) {
			this.eventsProduced = true;
			this.producedEvents = eventsInterface;
			this.producedCount++;
			if (this.latch != null) {
				this.latch.countDown();
			}
		}

		@Override
		public <EVENTS> void onEventsUnproduced(final Class<?> componentType, final Class<EVENTS> eventsInterface) {
			this.eventsUnproduced = true;
			this.unProducedCount++;
			if (this.latch != null) {
				this.latch.countDown();
			}
		}

		@Override
		public <EVENTS> void onEventsSubscribed(final Class<? extends EVENTS> eventsConsumer,
				final Class<EVENTS> eventsInterface) {
			this.eventsConsumed = true;
			this.consumedEvents = eventsInterface;
			this.consumedCount++;
			if (this.latch != null) {
				this.latch.countDown();
			}
		}

		@Override
		public <EVENTS> void onEventsUnsubscribed(final Class<? extends EVENTS> eventsConsumer,
				final Class<EVENTS> eventsInterface) {
			this.eventsUnconsumed = true;
			this.unconsumedCount++;
			if (this.latch != null) {
				this.latch.countDown();
			}
		}

		@Override
		public <EVENTS> void onEventNotificationStarted(final Class<EVENTS> eventsInterface, final Method method,
				final Class<?> eventsProducer, final Class<?> eventsConsumer, final Class<? extends EVENTS> subscriberType,
				final boolean asynchronous, final long queueWaitNanos, final Object... inputData) {
			this.queueWaitNanos = queueWaitNanos;
			this.eventsFired = true;
			this.firedEvents = eventsInterface;
			if (this.latch != null) {
				this.latch.countDown();
			}
		}

		@Override
		public <EVENTS> void onEventNotificationFinished(final Class<EVENTS> eventsInterface, final Method method,
				final Class<?> eventsProducer, final Class<?> eventsConsumer, final Class<? extends EVENTS> subscriberType,
				final long durationNanos, final Throwable failure, final Object... inputData) {
			this.eventNotificationFinished = true;
			this.firedEvents = eventsInterface;
			this.durationNanos = durationNanos;
			this.failure = failure;
			if (this.latch != null) {
				this.latch.countDown();
			}
		}

	}

	class FailingEventConsumer implements AComponentEvents {
		final IllegalStateException failure = new IllegalStateException("expected event failure");

		@Override
		public void onNotification(final ADisplayData value) {
			throw this.failure;
		}

		@Override
		public void onNotification(final ADisplayData value, final boolean aValue) {
			throw this.failure;
		}
	}

}
