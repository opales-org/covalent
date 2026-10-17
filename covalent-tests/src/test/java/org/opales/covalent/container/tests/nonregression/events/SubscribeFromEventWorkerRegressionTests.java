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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.events.EventsManagerParameters;
import org.opales.covalent.container.raw.RawComponentsServices;

/**
 * A consumer that subscribes, from one of its event callbacks, to a producer
 * with a subscribe delegate: with asynchronous events and a single events
 * worker, the subscribe call runs on that worker, and the delegate
 * notification is queued on the same worker. The subscribe call must return,
 * and the delegate must run afterwards and complete the subscription.
 */
public class SubscribeFromEventWorkerRegressionTests {

	private static final String INITIAL_STATE = "initial-state";

	private static final String LATER_EVENT = "later-event";

	private static final long TIMEOUT_SECONDS = 5;

	private CovalentContainer container;

	private RawComponentsServices rcs;

	@Before
	public void setUp() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(false);
		this.container.start();
		this.rcs = this.container.rcs();
		this.rcs.registerComponent(SubscribeFromEventWorkerRegressionTests.class, this);
		this.rcs.accessParameters(EventsManagerParameters.class).setWorkerCount(1);
	}

	@After
	public void tearDown() {
		this.container.dispose();
	}

	@Test
	public void subscribeFromAnEventCallbackOnTheOnlyEventsWorkerReturnsAndNotifiesTheDelegate()
			throws InterruptedException {
		TriggerEvents trigger = this.rcs.produceEvents(SubscribeFromEventWorkerRegressionTests.class,
				TriggerEvents.class);
		CountDownLatch delegateCalled = new CountDownLatch(1);
		StreamEvents stream = this.rcs.produceEvents(SubscribeFromEventWorkerRegressionTests.class,
				StreamEvents.class, subscriber -> {
					subscriber.onMessage(INITIAL_STATE);
					delegateCalled.countDown();
				});
		RecordingConsumer consumer = new RecordingConsumer(2);
		CountDownLatch subscribeReturned = new CountDownLatch(1);
		this.rcs.subscribe(SubscribeFromEventWorkerRegressionTests.class, TriggerEvents.class, () -> {
			this.rcs.subscribe(SubscribeFromEventWorkerRegressionTests.class, StreamEvents.class, consumer);
			subscribeReturned.countDown();
		});

		trigger.onTrigger();

		Assert.assertTrue("A subscribe call made on the only events worker should return.",
				subscribeReturned.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
		Assert.assertTrue("The subscribe delegate should be notified once the events worker is free.",
				delegateCalled.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
		stream.onMessage(LATER_EVENT);
		Assert.assertTrue("The consumer should receive the initial state and the later event.",
				consumer.awaitMessages());
		Assert.assertEquals(Arrays.asList(INITIAL_STATE, LATER_EVENT), consumer.getMessages());
	}

	@Events
	public interface TriggerEvents {
		void onTrigger();
	}

	@Events
	public interface StreamEvents {
		void onMessage(String message);
	}

	private static class RecordingConsumer implements StreamEvents {

		private final List<String> messages = new CopyOnWriteArrayList<>();

		private final CountDownLatch expectedMessages;

		RecordingConsumer(final int expectedCount) {
			this.expectedMessages = new CountDownLatch(expectedCount);
		}

		@Override
		public void onMessage(final String message) {
			this.messages.add(message);
			this.expectedMessages.countDown();
		}

		boolean awaitMessages() throws InterruptedException {
			return this.expectedMessages.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		}

		List<String> getMessages() {
			return this.messages;
		}
	}
}
