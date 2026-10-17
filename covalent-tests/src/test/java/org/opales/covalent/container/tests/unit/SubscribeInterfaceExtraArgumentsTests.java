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
 * Subscribe interfaces whose methods take more than the observer and an
 * optional filter: a second argument that is neither a filter nor
 * {@code SubscribeParameters} (here a topic), and more than two arguments.
 * The producer implementing the subscribe interface receives every argument
 * unchanged, the events are delivered, and the very same call is replayed
 * after the producer was passivated and activated again. A filter or
 * {@code SubscribeParameters} is only recognized as the second argument of a
 * two-argument call.
 */
public class SubscribeInterfaceExtraArgumentsTests {

	private static final List<String> received = new CopyOnWriteArrayList<>();

	private static final List<String> producerCalls = new CopyOnWriteArrayList<>();

	/** Which subscribe method the subscriber calls on activation. */
	private static volatile String mode;

	private CovalentContainer container;

	@Before
	public void setUp() {
		received.clear();
		producerCalls.clear();
		mode = null;
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		this.container.rcs().registerComponent(this);
	}

	@After
	public void tearDown() {
		this.container.dispose();
	}

	@Test
	public void aSecondArgumentThatIsNeitherAFilterNorSubscribeParametersReachesTheProducer() {
		this.start("topic");

		this.fire("one");
		this.fire("two");

		Assert.assertEquals(List.of("subscribe(topic=news)"), producerCalls);
		Assert.assertEquals(List.of("one", "two"), received);
	}

	@Test
	public void aSecondArgumentThatIsNeitherAFilterNorSubscribeParametersIsReplayedAfterPassivation() {
		this.start("topic");

		this.container.lcs().passivateComponent(TopicProducer.class);
		producerCalls.clear();
		this.container.lcs().activateComponent(TopicProducer.class);
		this.fire("one");

		Assert.assertEquals(List.of("subscribe(topic=news)"), producerCalls);
		Assert.assertEquals(List.of("one"), received);
	}

	@Test
	public void moreThanTwoArgumentsReachTheProducerInOrderAndEventsAreDelivered() {
		this.start("priority");

		this.fire("one");

		Assert.assertEquals(List.of("subscribe(priority=3, topic=news)"), producerCalls);
		Assert.assertEquals(List.of("one"), received);
	}

	@Test
	public void moreThanTwoArgumentsAreReplayedAfterPassivation() {
		this.start("priority");

		this.container.lcs().passivateComponent(TopicProducer.class);
		producerCalls.clear();
		this.container.lcs().activateComponent(TopicProducer.class);
		this.fire("one");

		Assert.assertEquals(List.of("subscribe(priority=3, topic=news)"), producerCalls);
		Assert.assertEquals(List.of("one"), received);
	}

	private void start(final String subscribeMode) {
		mode = subscribeMode;
		this.container.deployComponents(TopicProducerImpl.class, TopicSubscriberImpl.class);
		this.container.start();
	}

	private void fire(final String message) {
		this.container.rcs().useServices(TopicProducer.class, TopicServices.class).fire(message);
	}

	// --- Fixtures ---

	@Events
	public interface TopicEvents {
		void onEvent(String message);

		@EventsSubscribe
		interface TopicSubscribe {
			void subscribe(TopicEvents observer, String topic);

			void subscribe(TopicEvents observer, int priority, String topic);

			void unsubscribe(TopicEvents observer);
		}

	}

	@Component
	public interface TopicProducer {
	}

	@Services
	public interface TopicServices {
		void fire(String message);
	}

	@Implementation
	public static class TopicProducerImpl
			implements TopicProducer, TopicServices, TopicEvents.TopicSubscribe {

		@ProducedEvents
		private TopicEvents events;

		@Override
		public void fire(final String message) {
			this.events.onEvent(message);
		}

		@Override
		public void subscribe(final TopicEvents observer, final String topic) {
			producerCalls.add("subscribe(topic=" + topic + ")");
		}

		@Override
		public void subscribe(final TopicEvents observer, final int priority, final String topic) {
			producerCalls.add("subscribe(priority=" + priority + ", topic=" + topic + ")");
		}

		@Override
		public void unsubscribe(final TopicEvents observer) {
			producerCalls.add("unsubscribe");
		}
	}

	@Component
	public interface TopicSubscriber {
	}

	@Implementation
	public static class TopicSubscriberImpl implements TopicSubscriber, TopicEvents {

		@UsedEventsSubscribe
		private TopicEvents.TopicSubscribe subscribe;

		@Activation
		public void activate() {
			switch (mode) {
			case "topic":
				this.subscribe.subscribe(this, "news");
				break;
			default:
				this.subscribe.subscribe(this, 3, "news");
				break;
			}
		}

		@Override
		public void onEvent(final String message) {
			received.add(message);
		}
	}
}
