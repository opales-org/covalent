/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents.ProducerEventsSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents.ProducerWithSubscribeEventsFilter;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithsubscribe.ProducerWithSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithsubscribe.impl.ProducerWithSubscribeImpl;

/**
 * A consumer calling its producer's two-argument subscribe method with a
 * {@code null} filter is subscribed as if it had given no filter: it
 * receives every event. When the producer delegates subscriptions, it
 * receives the same two-argument call, {@code null} included, each time the
 * subscription is replayed after the producer was passivated and activated
 * again.
 */
public class SubscribeWithNullFilterTests {

	private CovalentContainer container;

	@Before
	public void setUp() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
	}

	@After
	public void tearDown() {
		this.container.dispose();
	}

	@Test
	public void aNullFilterSubscribesLikeNoFilter() {
		this.container.deployComponents(ProducerWithSubscribeImpl.class, NullFilterConsumerImpl.class);
		this.container.rcs().registerComponent(this);
		this.container.start();
		ProducerServices producer = this.container.rcs().useServices(ProducerWithSubscribe.class,
				ProducerServices.class);

		producer.fireEvent("first");
		producer.fireEvent("second");

		Assert.assertEquals(List.of("first", "second"), NullFilterConsumerImpl.received);
	}

	@Test
	public void aDelegatingProducerReceivesTheSameTwoArgumentCallWhenTheSubscriptionIsReplayed() {
		this.container.deployComponents(RecordingProducerImpl.class, NullFilterConsumerImpl.class);
		this.container.rcs().registerComponent(this);
		this.container.start();
		Assert.assertEquals(List.of("subscribe(observer, null)"), RecordingProducerImpl.calls);

		this.container.lcs().passivateComponent(RecordingProducer.class);
		RecordingProducerImpl.calls.clear();
		this.container.lcs().activateComponent(RecordingProducer.class);

		Assert.assertEquals(List.of("subscribe(observer, null)"), RecordingProducerImpl.calls);
	}

	@Component
	public interface RecordingProducer {
	}

	@Implementation
	public static class RecordingProducerImpl implements RecordingProducer, ProducerEventsSubscribe {

		static final List<String> calls = new ArrayList<>();

		@ProducedEvents
		private ProducerWithSubscribeEvents produceEvents;

		@Override
		public void subscribe(final ProducerWithSubscribeEvents observer) {
			calls.add("subscribe(observer)");
		}

		@Override
		public void subscribe(final ProducerWithSubscribeEvents observer,
				final ProducerWithSubscribeEventsFilter filter) {
			calls.add("subscribe(observer, " + filter + ")");
		}

		@Override
		public void unsubscribe(final ProducerWithSubscribeEvents observer) {
			calls.add("unsubscribe(observer)");
		}
	}

	@Component
	public interface NullFilterConsumer {
	}

	@Implementation
	public static class NullFilterConsumerImpl implements NullFilterConsumer, ProducerWithSubscribeEvents {

		static final List<String> received = new CopyOnWriteArrayList<>();

		@UsedEventsSubscribe
		private ProducerEventsSubscribe producerEventsSubscribe;

		@Activation
		public void activate() {
			received.clear();
			this.producerEventsSubscribe.subscribe(this, null);
		}

		@Override
		public void onEvent(final String message) {
			received.add(message);
		}
	}
}
