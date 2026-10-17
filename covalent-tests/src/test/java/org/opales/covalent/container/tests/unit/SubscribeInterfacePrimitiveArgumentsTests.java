/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsSubscribe;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.lifecycle.Activation;

/**
 * Subscribe interfaces whose methods declare primitive parameters of every kind: the subscriber passes boxed
 * values (always seen as wrappers at runtime) and the producer implementing the subscribe interface receives
 * them unchanged, whichever primitive type is declared. The {@code int} case is covered by
 * {@link SubscribeInterfaceExtraArgumentsTests}.
 */
public class SubscribeInterfacePrimitiveArgumentsTests {

	private static final List<String> received = new CopyOnWriteArrayList<>();

	private static final List<String> producerCalls = new CopyOnWriteArrayList<>();

	private CovalentContainer container;

	@Before
	public void setUp() {
		received.clear();
		producerCalls.clear();
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		this.container.rcs().registerComponent(this);
	}

	@After
	public void tearDown() {
		this.container.dispose();
	}

	@Test
	public void everyPrimitiveParameterTypeIsMatchedByItsBoxedArgumentAndReachesTheProducerUnchanged() {
		this.container.deployComponents(PrimitiveProducerImpl.class, PrimitiveSubscriberImpl.class);
		this.container.start();

		this.container.rcs().useServices(PrimitiveProducer.class, PrimitiveServices.class).fire("one");

		Assert.assertEquals(List.of("long=5000000000", "boolean=true", "double=2.5", "float=1.5", "short=7",
				"byte=3", "char=x"), producerCalls);
		// one subscription per call: the number of deliveries is not what this test is about
		Assert.assertFalse(received.isEmpty());
		Assert.assertTrue(received.stream().allMatch("one"::equals));
	}

	// --- Fixtures ---

	@Events
	public interface PrimitiveEvents {
		void onEvent(String message);

		@EventsSubscribe
		interface PrimitiveSubscribe {
			void subscribe(PrimitiveEvents observer, long value);

			void subscribe(PrimitiveEvents observer, boolean value);

			void subscribe(PrimitiveEvents observer, double value);

			void subscribe(PrimitiveEvents observer, float value);

			void subscribe(PrimitiveEvents observer, short value);

			void subscribe(PrimitiveEvents observer, byte value);

			void subscribe(PrimitiveEvents observer, char value);

			void unsubscribe(PrimitiveEvents observer);
		}
	}

	@Component
	public interface PrimitiveProducer {
	}

	@Services
	public interface PrimitiveServices {
		void fire(String message);
	}

	@Implementation
	public static class PrimitiveProducerImpl
			implements PrimitiveProducer, PrimitiveServices, PrimitiveEvents.PrimitiveSubscribe {

		@ProducedEvents
		private PrimitiveEvents events;

		@Override
		public void fire(final String message) {
			this.events.onEvent(message);
		}

		@Override
		public void subscribe(final PrimitiveEvents observer, final long value) {
			producerCalls.add("long=" + value);
		}

		@Override
		public void subscribe(final PrimitiveEvents observer, final boolean value) {
			producerCalls.add("boolean=" + value);
		}

		@Override
		public void subscribe(final PrimitiveEvents observer, final double value) {
			producerCalls.add("double=" + value);
		}

		@Override
		public void subscribe(final PrimitiveEvents observer, final float value) {
			producerCalls.add("float=" + value);
		}

		@Override
		public void subscribe(final PrimitiveEvents observer, final short value) {
			producerCalls.add("short=" + value);
		}

		@Override
		public void subscribe(final PrimitiveEvents observer, final byte value) {
			producerCalls.add("byte=" + value);
		}

		@Override
		public void subscribe(final PrimitiveEvents observer, final char value) {
			producerCalls.add("char=" + value);
		}

		@Override
		public void unsubscribe(final PrimitiveEvents observer) {
			producerCalls.add("unsubscribe");
		}
	}

	@Component
	public interface PrimitiveSubscriber {
	}

	@Implementation
	public static class PrimitiveSubscriberImpl implements PrimitiveSubscriber, PrimitiveEvents {

		@UsedEventsSubscribe
		private PrimitiveEvents.PrimitiveSubscribe subscribe;

		@Activation
		public void activate() {
			this.subscribe.subscribe(this, 5_000_000_000L);
			this.subscribe.subscribe(this, true);
			this.subscribe.subscribe(this, 2.5d);
			this.subscribe.subscribe(this, 1.5f);
			this.subscribe.subscribe(this, (short) 7);
			this.subscribe.subscribe(this, (byte) 3);
			this.subscribe.subscribe(this, 'x');
		}

		@Override
		public void onEvent(final String message) {
			received.add(message);
		}
	}
}
