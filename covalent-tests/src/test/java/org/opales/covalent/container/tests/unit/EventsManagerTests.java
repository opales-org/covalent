/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.events.EventsManagerEvents;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.CovalentTests;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.FullyConnectedEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.FullyConnectedServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.impl.FullyConnectedImpl;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithdelegation.impl.ProducerWithSubscribeAndDelegationImpl;
import org.opales.covalent.container.tests.unit.events.AComponentEvents;
import org.opales.covalent.container.tests.unit.events.BComponentEvents;
import org.opales.covalent.container.tests.unit.events.WrongNamedEvents;

public class EventsManagerTests extends CovalentTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	@Before
	public void setup() {
		this.rcs.registerComponent(this);
		this.cm.getParameters().setSynchronousEvents(false);
	}

	@After
	public void tearDown() {
		this.cm.stop();
	}

	@Test
	public void testEventsManagerEvents() throws InterruptedException {
		// Internal (technical) components are only activated by the container's
		// own start() (outside the pre-first-start bootstrap window); subscribing
		// to a technical events interface below needs them activated.
		this.cm.start();
		Covalent.main.getParameters().setInstrumented(true);
		this.cm.getParameters().setSynchronousEvents(true);
		EventsManagerEventsConsumer eventsManagerEventsConsumer = new EventsManagerEventsConsumer();
		FullyConnectedEventsDummyMock dummyMock = new FullyConnectedEventsDummyMock();

		CountDownLatch latch = new CountDownLatch(1);
		eventsManagerEventsConsumer.setLatch(latch);

		this.rcs.subscribe(EventsManagerEvents.class, eventsManagerEventsConsumer);

		Assert.assertTrue(eventsManagerEventsConsumer.eventsConsumed);
		Assert.assertFalse(eventsManagerEventsConsumer.eventsProduced);
		Assert.assertEquals(EventsManagerEvents.class, eventsManagerEventsConsumer.consumedEvents);

		eventsManagerEventsConsumer.reset();
		latch = new CountDownLatch(2);
		eventsManagerEventsConsumer.setLatch(latch);
		this.cm.deployComponents(ProducerWithSubscribeAndDelegationImpl.class, FullyConnectedImpl.class);
		// The container is already started (see above); activate the two
		// components just deployed instead of calling start() again.
		this.cm.lcs().activate();
		Assert.assertTrue("Timed out waiting for the expected events.", latch.await(5, TimeUnit.SECONDS));
		// no automatic subscribe
		Assert.assertFalse(eventsManagerEventsConsumer.eventsConsumed);
		Assert.assertTrue(eventsManagerEventsConsumer.eventsProduced);
		// produced: ProducerWithSubscribeEvents, FullyConnectedEvents
		Assert.assertEquals(2, eventsManagerEventsConsumer.producedCount);
		Assert.assertEquals(FullyConnectedEvents.class, eventsManagerEventsConsumer.producedEvents);

		eventsManagerEventsConsumer.reset();
		latch = new CountDownLatch(1);
		eventsManagerEventsConsumer.setLatch(latch);
		// we must subscribe to FullyConnectedEvents in order to test
		this.rcs.subscribe(FullyConnectedEvents.class, dummyMock);
		Assert.assertTrue("Timed out waiting for the expected events.", latch.await(5, TimeUnit.SECONDS));
		Assert.assertEquals(0, eventsManagerEventsConsumer.producedCount);
		Assert.assertTrue(eventsManagerEventsConsumer.eventsConsumed);
		Assert.assertEquals(1, eventsManagerEventsConsumer.consumedCount);
		Assert.assertEquals(FullyConnectedEvents.class, eventsManagerEventsConsumer.consumedEvents);
		eventsManagerEventsConsumer.reset();

		latch = new CountDownLatch(1);
		eventsManagerEventsConsumer.setLatch(latch);
		FullyConnectedServices fullyConnectedServices = this.rcs.useServices(FullyConnectedServices.class);
		fullyConnectedServices.checkUsedSubscribe();
		Assert.assertTrue("Timed out waiting for the expected events.", latch.await(5, TimeUnit.SECONDS));
		Assert.assertTrue(eventsManagerEventsConsumer.eventsConsumed);
		Assert.assertEquals(1, eventsManagerEventsConsumer.consumedCount);
		Assert.assertEquals(ProducerWithSubscribeEvents.class, eventsManagerEventsConsumer.consumedEvents);

		eventsManagerEventsConsumer.reset();
		latch = new CountDownLatch(2);
		eventsManagerEventsConsumer.setLatch(latch);
		fullyConnectedServices.checkProducedEvents();
		Assert.assertTrue("Timed out waiting for the expected events.", latch.await(5, TimeUnit.SECONDS));
		Assert.assertTrue(eventsManagerEventsConsumer.eventsFired);
		Assert.assertTrue(eventsManagerEventsConsumer.eventNotificationFinished);
		Assert.assertEquals(FullyConnectedEvents.class, eventsManagerEventsConsumer.firedEvents);
		Assert.assertTrue(eventsManagerEventsConsumer.durationNanos >= 0);
		Assert.assertNull(eventsManagerEventsConsumer.failure);

		eventsManagerEventsConsumer.reset();
		latch = new CountDownLatch(1);
		eventsManagerEventsConsumer.setLatch(latch);
		this.rcs.unsubscribe(FullyConnectedEvents.class, dummyMock);
		Assert.assertTrue("Timed out waiting for the expected events.", latch.await(5, TimeUnit.SECONDS));
		Assert.assertFalse(eventsManagerEventsConsumer.eventsConsumed);
		Assert.assertTrue(eventsManagerEventsConsumer.eventsUnconsumed);

		eventsManagerEventsConsumer.reset();
		latch = new CountDownLatch(2);
		eventsManagerEventsConsumer.setLatch(latch);
		this.cm.stop();
		Assert.assertTrue("Timed out waiting for the expected events.", latch.await(5, TimeUnit.SECONDS));
		Assert.assertFalse(eventsManagerEventsConsumer.eventsProduced);
		Assert.assertTrue(eventsManagerEventsConsumer.eventsUnproduced);
		Assert.assertEquals(2, eventsManagerEventsConsumer.unProducedCount);
		Assert.assertNull(eventsManagerEventsConsumer.producedEvents);
		Assert.assertNull(eventsManagerEventsConsumer.consumedEvents);

		this.cm.getParameters().setSynchronousEvents(false);
		Covalent.main.getParameters().setInstrumented(false);
		// This test exercises stop() itself; restart the container so tearDown()'s
		// own, unconditional stop() below does not run on an already-stopped one.
		this.cm.start();
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

	class FullyConnectedEventsDummyMock implements FullyConnectedEvents {
		@Override
		public void onEvent(final String string) {
		}
	}
}
