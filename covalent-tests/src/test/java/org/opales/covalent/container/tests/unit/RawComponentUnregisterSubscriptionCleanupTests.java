/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.raw.RawComponentsServices;

/**
 * {@code RawComponentsImpl.unregisterComponent} calls {@code unsubscribeAllFor},
 * like {@code onComponentFailed} and {@code stop()}.
 * <p>
 * This test locks in that {@code unregisterComponent} stops a raw component's
 * event subscriptions from receiving further events. It calls {@code unsubscribeAllFor} directly, exactly like the other two
 * cleanup paths, instead of relying only on the general lifecycle passivation
 * step to unsubscribe consumed-events ports recorded in the component's
 * metamodel.
 * </p>
 * <p>
 * This test uses its own dedicated producer and events interface rather than
 * the {@code ComponentA} fixture shared by many other test classes, so it does
 * not depend on that fixture's own subscribe-delegate behavior or on
 * assumptions about state shared across containers.
 * </p>
 */
public class RawComponentUnregisterSubscriptionCleanupTests {

	private CovalentContainer container;

	// No tearDown(): this test never calls start() on its fresh container
	// (exercising the pre-first-start bootstrap window), and stop()
	// requires having been started first; the throwaway container is simply
	// discarded after the test.

	@Test
	public void unregisterComponentStopsARawSubscriberFromReceivingFurtherEvents() {
		this.container = Covalent.container();
		// Event delivery is asynchronous by default; make it synchronous so the
		// assertions below do not race the delivery worker thread.
		this.container.getParameters().setSynchronousEvents(true);
		RawComponentsServices rcs = this.container.rcs();

		Producer producer = new Producer(rcs);
		rcs.registerComponent(producer);

		Subscriber subscriber = new Subscriber(rcs);
		rcs.registerComponent(subscriber);

		producer.events.ping("before-unregister");
		Assert.assertEquals(1, subscriber.receivedCount);

		rcs.unregisterComponent(Subscriber.class);

		// The producer is still registered and active; firing again must not
		// reach the unregistered subscriber.
		producer.events.ping("after-unregister");
		Assert.assertEquals(1, subscriber.receivedCount);
	}

	public interface UnregisterCleanupEvents {
		void ping(String data);
	}

	public static class Producer {

		private final RawComponentsServices rcs;

		UnregisterCleanupEvents events;

		public Producer(final RawComponentsServices rcs) {
			this.rcs = rcs;
		}

		@Activation
		public void activate() {
			this.events = this.rcs.produceEvents(Producer.class, UnregisterCleanupEvents.class);
		}

		@Passivation
		public void passivate() {
			this.rcs.unproduceEvents(Producer.class, UnregisterCleanupEvents.class);
		}

	}

	public static class Subscriber implements UnregisterCleanupEvents {

		private final RawComponentsServices rcs;

		int receivedCount;

		public Subscriber(final RawComponentsServices rcs) {
			this.rcs = rcs;
		}

		@Activation
		public void activate() {
			this.rcs.subscribe(Subscriber.class, UnregisterCleanupEvents.class, this);
		}

		@Override
		public void ping(final String data) {
			this.receivedCount++;
		}

	}

}
