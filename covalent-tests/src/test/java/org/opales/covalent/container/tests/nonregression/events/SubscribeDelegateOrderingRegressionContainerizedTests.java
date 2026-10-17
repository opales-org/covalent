/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.nonregression.events;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.CovalentContainerizedTests;

public class SubscribeDelegateOrderingRegressionContainerizedTests extends CovalentContainerizedTests {

	private static final String INITIAL_STATE = "initial-state";

	private static final String NORMAL_EVENT = "normal-event";

	private static final String LATER_EVENT = "later-event";

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	@Before
	public void setUp() {
		this.cm.getParameters().setSynchronousEvents(true);
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
		// produceEvents below uses this test class as an explicit, otherwise-
		// unregistered componentType, purely to pair produceEvents/subscribe calls.
		// RawPortSupport.requireKnownComponentType requires componentType to
		// actually be registered before a producer-side port can be created for it.
		this.rcs.registerComponent(SubscribeDelegateOrderingRegressionContainerizedTests.class, this);
	}

	@After
	public void tearDown() {
		this.rcs.unregisterComponent(SubscribeDelegateOrderingRegressionContainerizedTests.class);
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.trace);

	}

	@Test
	public void subscribedConsumerShouldNotReceiveNormalEventsBeforeSubscribeDelegateCompletes()
			throws InterruptedException {
		CountDownLatch delegateStarted = new CountDownLatch(1);
		CountDownLatch allowDelegateToFinish = new CountDownLatch(1);
		CountDownLatch subscribeReturned = new CountDownLatch(1);

		OrderedEvents producer = this.rcs.produceEvents(SubscribeDelegateOrderingRegressionContainerizedTests.class,
				OrderedEvents.class, subscriber -> {
					delegateStarted.countDown();
					await(allowDelegateToFinish);
					subscriber.onEvent(INITIAL_STATE);
				});

		RecordingConsumer consumer = new RecordingConsumer();

		Thread subscribeThread = new Thread(() -> {
			this.rcs.subscribe(SubscribeDelegateOrderingRegressionContainerizedTests.class, OrderedEvents.class, consumer);
			subscribeReturned.countDown();
		}, "subscribe-delegate-ordering-regression");

		subscribeThread.start();

		boolean normalEventReceivedBeforeInitialState = false;
		try {
			Assert.assertTrue("Subscribe delegate should have started.",
					delegateStarted.await(1, TimeUnit.SECONDS));

			producer.onEvent(NORMAL_EVENT);
			normalEventReceivedBeforeInitialState = consumer.awaitNormalEventBeforeInitialState();
		} finally {
			allowDelegateToFinish.countDown();
		}

		Assert.assertTrue("Subscribe should return after the delegate is allowed to finish.",
				subscribeReturned.await(1, TimeUnit.SECONDS));
		Assert.assertTrue("Subscribe delegate should send the initial state.",
				consumer.hasInitialStateReceived());

		producer.onEvent(LATER_EVENT);
		Assert.assertTrue("Consumer should receive the buffered and later events.",
				consumer.awaitReceivedEvents());
		Assert.assertEquals(Arrays.asList(INITIAL_STATE, NORMAL_EVENT, LATER_EVENT), consumer.getReceivedMessages());

		Assert.assertFalse(
				"A consumer must not receive normal stream events before the subscribe delegate has sent the initial state.",
				normalEventReceivedBeforeInitialState);
	}

	private static void await(final CountDownLatch latch) {
		try {
			Assert.assertTrue("Timed out waiting for the expected events.", latch.await(5, TimeUnit.SECONDS));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}
	}

	@Events
	public interface OrderedEvents {
		void onEvent(String message);
	}

	private static class RecordingConsumer implements OrderedEvents {
		private final AtomicBoolean initialStateReceived = new AtomicBoolean(false);

		private final CountDownLatch normalEventBeforeInitialState = new CountDownLatch(1);

		private final CountDownLatch receivedEvents = new CountDownLatch(3);

		private final List<String> receivedMessages = new CopyOnWriteArrayList<>();

		@Override
		public void onEvent(final String message) {
			this.receivedMessages.add(message);
			this.receivedEvents.countDown();
			if (INITIAL_STATE.equals(message)) {
				this.initialStateReceived.set(true);
			}
			if (NORMAL_EVENT.equals(message) && !this.initialStateReceived.get()) {
				this.normalEventBeforeInitialState.countDown();
			}
		}

		boolean awaitNormalEventBeforeInitialState() throws InterruptedException {
			return this.normalEventBeforeInitialState.await(200, TimeUnit.MILLISECONDS);
		}

		boolean hasInitialStateReceived() {
			return this.initialStateReceived.get();
		}

		boolean awaitReceivedEvents() throws InterruptedException {
			return this.receivedEvents.await(1, TimeUnit.SECONDS);
		}

		List<String> getReceivedMessages() {
			return this.receivedMessages;
		}
	}
}
