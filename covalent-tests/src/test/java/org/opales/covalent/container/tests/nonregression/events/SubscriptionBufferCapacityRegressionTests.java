/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.nonregression.events;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.events.EventsManagerParameters;
import org.opales.covalent.container.events.EventsWorkersServices;
import org.opales.covalent.container.events.impl.EventsManagerImpl;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;

/**
 * While its subscribe delegate notification is pending, a subscription buffers
 * the events fired for it. That buffer holds at most the dispatch queue
 * capacity: beyond it, events are dropped and reported like a full dispatch
 * queue, and the producer is never blocked.
 */
public class SubscriptionBufferCapacityRegressionTests {

	private static final int QUEUE_CAPACITY = 2;

	private static final int FIRED_WHILE_PENDING = 10;

	private static final String DROP_MESSAGE = "Event notification dropped: a subscription still waiting";

	private CovalentContainer container;

	private RawComponentsServices rcs;

	private CapturingLogHandler eventsLogs;

	@Before
	public void setUp() {
		this.eventsLogs = CapturingLogHandler.install(EventsManagerImpl.class);
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(false);
		this.container.start();
		this.rcs = this.container.rcs();
		this.rcs.registerComponent(SubscriptionBufferCapacityRegressionTests.class, this);
		EventsManagerParameters parameters = this.rcs.accessParameters(EventsManagerParameters.class);
		parameters.setWorkerCount(1);
		parameters.setWorkerQueueCapacity(QUEUE_CAPACITY);
		// The worker count applies to the pool created at start; a restart also drops raw registrations.
		this.container.stop();
		this.container.start();
		this.rcs.registerComponent(SubscriptionBufferCapacityRegressionTests.class, this);
	}

	@After
	public void tearDown() {
		try {
			this.container.dispose();
		} finally {
			this.eventsLogs.restore();
		}
	}

	@Test(timeout = 10000)
	public void eventsBeyondTheBufferCapacityOfAPendingSubscriptionAreDroppedAndReported()
			throws InterruptedException {
		CountDownLatch delegateEntered = new CountDownLatch(1);
		CountDownLatch releaseDelegate = new CountDownLatch(1);
		MessageEvents producer = this.rcs.produceEvents(SubscriptionBufferCapacityRegressionTests.class,
				MessageEvents.class, subscriber -> {
					delegateEntered.countDown();
					awaitUninterruptibly(releaseDelegate);
				});
		RecordingConsumer consumer = new RecordingConsumer();
		this.rcs.subscribe(SubscriptionBufferCapacityRegressionTests.class, MessageEvents.class, consumer);
		// The subscribe delegate now holds the single events worker: the subscription stays pending.
		delegateEntered.await();
		EventsWorkersServices workers = this.rcs.useServices(EventsWorkersServices.class);
		long rejectedBefore = workers.readWorkerPools().get(0).rejectedTasks();

		for (int i = 0; i < FIRED_WHILE_PENDING; i++) {
			producer.onMessage("pending-" + i);
		}

		int dropped = FIRED_WHILE_PENDING - QUEUE_CAPACITY;
		Assert.assertEquals("every event beyond the buffer capacity should be counted as rejected", dropped,
				workers.readWorkerPools().get(0).rejectedTasks() - rejectedBefore);
		Assert.assertEquals("every dropped event should be reported", dropped,
				this.eventsLogs.messagesStartingWith(DROP_MESSAGE).size());

		releaseDelegate.countDown();
		// The buffered events are delivered once the delegate returns; an event fired before that would find the
		// buffer still full.
		consumer.bufferedReceived.await();
		producer.onMessage("after");
		consumer.allReceived.await();
		Assert.assertEquals(Arrays.asList("pending-0", "pending-1", "after"), consumer.getMessages());
	}

	@Events
	public interface MessageEvents {
		void onMessage(String message);
	}

	private static class RecordingConsumer implements MessageEvents {

		private final List<String> messages = new CopyOnWriteArrayList<>();

		private final CountDownLatch bufferedReceived = new CountDownLatch(QUEUE_CAPACITY);

		private final CountDownLatch allReceived = new CountDownLatch(QUEUE_CAPACITY + 1);

		@Override
		public void onMessage(final String message) {
			this.messages.add(message);
			this.bufferedReceived.countDown();
			this.allReceived.countDown();
		}

		List<String> getMessages() {
			return this.messages;
		}
	}

	private static void awaitUninterruptibly(final CountDownLatch latch) {
		boolean interrupted = false;
		while (true) {
			try {
				latch.await();
				break;
			} catch (InterruptedException e) {
				interrupted = true;
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
	}
}
