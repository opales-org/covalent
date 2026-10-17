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
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.events.GenericSubscribe;
import org.opales.covalent.container.events.SubscribeParameters;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents.ProducerEventsSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents.ProducerWithSubscribeEventsFilter;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.simpleproducer.SimpleProducer;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.simpleproducer.impl.SimpleProducerImpl;

/**
 * The arguments of a subscribe interface's methods: the consumer alone, the
 * consumer and an {@code @EventsFilter} filter, or, with the generic subscribe
 * interface, the consumer and {@link SubscribeParameters}. The producer's own
 * subscribe methods receive exactly the call the consumer made, and the
 * filter keeps applying when the subscription is replayed after the producer
 * was passivated and activated again.
 */
public class SubscribeInterfaceArgumentsTests {

	/** Messages starting with this prefix are blocked by the filters below. */
	private static final String HIDDEN = "hidden";

	private static final List<String> received = new CopyOnWriteArrayList<>();

	private static final List<String> producerCalls = new CopyOnWriteArrayList<>();

	private static final List<Object> producerFilters = new CopyOnWriteArrayList<>();

	private static volatile boolean subscribeWithFilter;

	private static volatile SubscribeParameters parametersToSubscribeWith;

	private CovalentContainer container;

	@Before
	public void setUp() {
		received.clear();
		producerCalls.clear();
		producerFilters.clear();
		subscribeWithFilter = false;
		parametersToSubscribeWith = null;
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		this.container.rcs().registerComponent(this);
	}

	@After
	public void tearDown() {
		this.container.dispose();
	}

	@Test
	public void aSubscribeWithTheConsumerAloneReachesTheProducerAndReceivesEveryEvent() {
		this.container.deployComponents(DelegatingProducerImpl.class, SubscriberImpl.class);
		this.container.start();

		this.fireOnDelegatingProducer("one");
		this.fireOnDelegatingProducer(HIDDEN + "-two");

		Assert.assertEquals(List.of("subscribe(observer)"), producerCalls);
		Assert.assertEquals(List.of("one", HIDDEN + "-two"), received);
	}

	@Test
	public void aSubscribeWithAFilterReachesTheProducerWithThatFilterAndBlocksTheFilteredEvents() {
		subscribeWithFilter = true;
		this.container.deployComponents(DelegatingProducerImpl.class, SubscriberImpl.class);
		this.container.start();

		this.fireOnDelegatingProducer(HIDDEN + "-one");
		this.fireOnDelegatingProducer("two");

		Assert.assertEquals(List.of("subscribe(observer, filter)"), producerCalls);
		Assert.assertEquals(1, producerFilters.size());
		Assert.assertTrue(producerFilters.get(0) instanceof ProducerWithSubscribeEventsFilter);
		Assert.assertEquals(List.of("two"), received);
	}

	@Test
	public void theFilterIsReplayedToTheProducerAndKeepsApplyingAfterItWasPassivatedAndActivatedAgain() {
		subscribeWithFilter = true;
		this.container.deployComponents(DelegatingProducerImpl.class, SubscriberImpl.class);
		this.container.start();
		Object firstFilter = producerFilters.get(0);

		this.container.lcs().passivateComponent(DelegatingProducer.class);
		producerCalls.clear();
		producerFilters.clear();
		this.container.lcs().activateComponent(DelegatingProducer.class);
		this.fireOnDelegatingProducer(HIDDEN + "-one");
		this.fireOnDelegatingProducer("two");

		Assert.assertEquals(List.of("subscribe(observer, filter)"), producerCalls);
		Assert.assertSame(firstFilter, producerFilters.get(0));
		Assert.assertEquals(List.of("two"), received);
	}

	@Test
	public void anUnsubscribeReachesTheProducerAndStopsTheEvents() {
		this.container.deployComponents(DelegatingProducerImpl.class, SubscriberImpl.class);
		this.container.start();

		this.container.rcs().useServices(Subscriber.class, SubscriberControls.class).unsubscribe();
		this.fireOnDelegatingProducer("after");

		Assert.assertEquals(List.of("subscribe(observer)", "unsubscribe(observer)"), producerCalls);
		Assert.assertTrue(received.isEmpty());
	}

	@Test
	public void subscribeParametersCarryingAFilterBlockTheFilteredEventsAndAreReplayed() {
		SubscribeParameters parameters = this.container.getParameters().newSubscribeParameters();
		parameters.setEventFilter(new HiddenMessagesFilter());
		parametersToSubscribeWith = parameters;
		this.container.deployComponents(SimpleProducerImpl.class, GenericSubscriberImpl.class);
		this.container.start();
		ProducerServices producer = this.container.rcs().useServices(SimpleProducer.class, ProducerServices.class);

		producer.fireEvent(HIDDEN + "-one");
		producer.fireEvent("two");
		this.container.lcs().passivateComponent(SimpleProducer.class);
		this.container.lcs().activateComponent(SimpleProducer.class);
		producer = this.container.rcs().useServices(SimpleProducer.class, ProducerServices.class);
		producer.fireEvent(HIDDEN + "-three");
		producer.fireEvent("four");

		Assert.assertEquals(List.of("two", "four"), received);
	}

	private void fireOnDelegatingProducer(final String message) {
		this.container.rcs().useServices(DelegatingProducer.class, ProducerServices.class).fireEvent(message);
	}

	// --- Fixtures ---

	/** Blocks the messages starting with {@link #HIDDEN} (a filter returns true to block). */
	public static class HiddenMessagesFilter {
		public boolean filterEvent(final String message) {
			return message.startsWith(HIDDEN);
		}
	}

	@Component
	public interface DelegatingProducer {
	}

	/** A producer that implements its own subscribe interface, and records the calls it receives. */
	@Implementation
	public static class DelegatingProducerImpl
			implements DelegatingProducer, ProducerServices, ProducerEventsSubscribe {

		@ProducedEvents
		private ProducerWithSubscribeEvents produceEvents;

		@Override
		public void fireEvent(final String message) {
			this.produceEvents.onEvent(message);
		}

		@Override
		public boolean checkDelegatedSubscribe() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean checkDelegatedUnsubscribe() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void subscribe(final ProducerWithSubscribeEvents observer) {
			producerCalls.add("subscribe(observer)");
		}

		@Override
		public void subscribe(final ProducerWithSubscribeEvents observer,
				final ProducerWithSubscribeEventsFilter filter) {
			producerCalls.add("subscribe(observer, filter)");
			producerFilters.add(filter);
		}

		@Override
		public void unsubscribe(final ProducerWithSubscribeEvents observer) {
			producerCalls.add("unsubscribe(observer)");
		}
	}

	@Component
	public interface Subscriber {
	}

	@Services
	public interface SubscriberControls {
		void unsubscribe();
	}

	@Implementation
	public static class SubscriberImpl implements Subscriber, SubscriberControls, ProducerWithSubscribeEvents {

		@UsedEventsSubscribe
		private ProducerEventsSubscribe producerEventsSubscribe;

		@Activation
		public void activate() {
			if (subscribeWithFilter) {
				this.producerEventsSubscribe.subscribe(this,
						message -> message.startsWith(HIDDEN));
			} else {
				this.producerEventsSubscribe.subscribe(this);
			}
		}

		@Override
		public void unsubscribe() {
			this.producerEventsSubscribe.unsubscribe(this);
		}

		@Override
		public void onEvent(final String message) {
			received.add(message);
		}
	}

	@Component
	public interface GenericSubscriber {
	}

	@Implementation
	public static class GenericSubscriberImpl implements GenericSubscriber, ProducerEvents {

		@UsedEventsSubscribe
		private GenericSubscribe genericSubscribe;

		@Activation
		public void activate() {
			this.genericSubscribe.subscribe(this, parametersToSubscribeWith);
		}

		@Override
		public void onEvent(final String message) {
			received.add(message);
		}
	}
}
