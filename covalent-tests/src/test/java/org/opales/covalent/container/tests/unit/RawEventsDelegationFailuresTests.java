/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.annotations.EventsFilter;
import org.opales.covalent.container.events.EventsManagerEvents;
import org.opales.covalent.container.events.SubscribeParameters;
import org.opales.covalent.container.events.impl.ProducerSideNotifierInvocationHandler;
import org.opales.covalent.container.events.impl.SubscribeParametersImpl;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;

/**
 * Verifies the producer-side notifier through the raw events API with subscribe and unsubscribe delegates:
 * a failing delegate leaves the consumer unsubscribed without failing the caller, events fired by the producer
 * while its subscribe delegate runs are delivered after it completes, a failing consumer callback does not
 * prevent later deliveries, event filters hide the events they match, and a failing consumer is reported
 * with its failure to the instrumentation events.
 */
public class RawEventsDelegationFailuresTests {

	private CapturingLogHandler producerSideNotifierInvocationHandlerLogs;

	/** Events produced by the fixtures. */
	public interface Probe {
		void onPing(String message);
	}

	/** Filter hiding the pings whose message starts with "hidden". */
	@EventsFilter
	public interface ProbeFilter {
		boolean filterPing(String message);
	}

	/** Records the pings it receives. */
	public static class Recorder implements Probe {
		final List<String> received = Collections.synchronizedList(new ArrayList<>());

		@Override
		public void onPing(final String message) {
			this.received.add(message);
		}
	}

	/** Fails on every ping, after recording it. */
	public static class FailingRecorder extends Recorder {
		@Override
		public void onPing(final String message) {
			super.onPing(message);
			throw new IllegalStateException("consumer failure " + message);
		}
	}

	/** Captures the failures reported for event notifications. */
	public static class NotificationsRecorder implements EventsManagerEvents {
		final List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

		final List<Boolean> started = Collections.synchronizedList(new ArrayList<>());

		@Override
		public <EVENTS> void onEventsProduced(final Class<?> componentType, final Class<EVENTS> eventsInterface) {
			// not observed
		}

		@Override
		public <EVENTS> void onEventsUnproduced(final Class<?> componentType, final Class<EVENTS> eventsInterface) {
			// not observed
		}

		@Override
		public <EVENTS> void onEventsSubscribed(final Class<? extends EVENTS> eventsConsumer,
				final Class<EVENTS> eventsInterface) {
			// not observed
		}

		@Override
		public <EVENTS> void onEventsUnsubscribed(final Class<? extends EVENTS> eventsConsumer,
				final Class<EVENTS> eventsInterface) {
			// not observed
		}

		@Override
		public <EVENTS> void onEventNotificationStarted(final Class<EVENTS> eventsInterface, final Method method,
				final Class<?> producerComponentType, final Class<?> consumerComponentType,
				final Class<? extends EVENTS> subscriberType, final boolean asynchronous, final long queueWaitNanos,
				final Object[] args) {
			if (eventsInterface == Probe.class) {
				this.started.add(asynchronous);
			}
		}

		@Override
		public <EVENTS> void onEventNotificationFinished(final Class<EVENTS> eventsInterface, final Method method,
				final Class<?> producerComponentType, final Class<?> consumerComponentType,
				final Class<? extends EVENTS> subscriberType, final long durationNanos, final Throwable failure,
				final Object[] args) {
			if (eventsInterface == Probe.class) {
				this.failures.add(failure);
			}
		}
	}

	private CovalentContainer container;

	private RawComponentsServices rcs;

	@Before
	public void setUp() {
		this.producerSideNotifierInvocationHandlerLogs = CapturingLogHandler.install(ProducerSideNotifierInvocationHandler.class);
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
		this.container.start();
		this.rcs = this.container.rcs();
		this.rcs.registerComponent(this);
	}

	@After
	public void tearDown() {
		this.container.dispose();
		this.producerSideNotifierInvocationHandlerLogs.restore();
	}

	@Test
	public void subscribeDelegateFailureLeavesTheConsumerUnsubscribedWithoutFailingTheCaller() {
		Consumer<Probe> failing = consumer -> {
			throw new IllegalStateException("delegate refuses");
		};
		Probe producer = this.rcs.produceEvents(Probe.class, failing, consumer -> {
		});
		Recorder recorder = new Recorder();

		this.rcs.subscribe(Probe.class, recorder);
		producer.onPing("after failure");

		assertTrue(recorder.received.isEmpty());
		assertEquals(List.of("Subscribe delegate failed."),
				this.producerSideNotifierInvocationHandlerLogs.messages(Level.SEVERE));
	}

	@Test
	public void eventsFiredWhileTheSubscribeDelegateRunsAreDeliveredOnceItCompletes() {
		AtomicReference<Probe> producerRef = new AtomicReference<>();
		List<String> order = Collections.synchronizedList(new ArrayList<>());
		Recorder recorder = new Recorder() {
			@Override
			public void onPing(final String message) {
				order.add(message);
				super.onPing(message);
			}
		};
		Consumer<Probe> subscribeDelegate = consumer -> {
			producerRef.get().onPing("buffered 1");
			producerRef.get().onPing("buffered 2");
			order.add("delegate done");
		};
		producerRef.set(this.rcs.produceEvents(Probe.class, subscribeDelegate, consumer -> {
		}));

		this.rcs.subscribe(Probe.class, recorder);
		producerRef.get().onPing("live");

		assertEquals(List.of("delegate done", "buffered 1", "buffered 2", "live"), order);
	}

	@Test
	public void unsubscribeDelegateFailureDoesNotFailTheCallerAndStopsDeliveries() {
		Consumer<Probe> failing = consumer -> {
			throw new IllegalStateException("unsubscribe delegate refuses");
		};
		Probe producer = this.rcs.produceEvents(Probe.class, consumer -> {
		}, failing);
		Recorder recorder = new Recorder();
		this.rcs.subscribe(Probe.class, recorder);
		producer.onPing("before");

		this.rcs.unsubscribe(Probe.class, recorder);
		producer.onPing("after");

		assertEquals(List.of("before"), recorder.received);
		assertEquals(List.of("Unsubscribe delegate failed."),
				this.producerSideNotifierInvocationHandlerLogs.messages(Level.SEVERE));
	}

	@Test
	public void unsubscribeDelegateReceivesTheConsumerSideProxyOfTheConsumer() {
		List<Probe> unsubscribed = Collections.synchronizedList(new ArrayList<>());
		Probe producer = this.rcs.produceEvents(Probe.class, consumer -> {
		}, unsubscribed::add);
		Recorder recorder = new Recorder();
		this.rcs.subscribe(Probe.class, recorder);

		this.rcs.unsubscribe(Probe.class, recorder);

		assertEquals(1, unsubscribed.size());
		unsubscribed.get(0).onPing("through the proxy");
		assertEquals(List.of("through the proxy"), recorder.received);
		producer.onPing("not delivered");
		assertEquals(1, recorder.received.size());
	}

	@Test
	public void aFailingConsumerDoesNotPreventLaterDeliveriesNorOtherConsumers() {
		Probe producer = this.rcs.produceEvents(Probe.class);
		FailingRecorder failing = new FailingRecorder();
		Recorder healthy = new Recorder();
		this.rcs.subscribe(Probe.class, failing);
		this.rcs.subscribe(Probe.class, healthy);

		producer.onPing("one");
		producer.onPing("two");

		assertEquals(List.of("one", "two"), failing.received);
		assertEquals(List.of("one", "two"), healthy.received);
		assertEquals(List.of("Event consumer callback failed.", "Event consumer callback failed."),
				this.producerSideNotifierInvocationHandlerLogs.messages(Level.SEVERE));
	}

	@Test
	public void instrumentationReportsTheFailureOfAFailingConsumer() {
		this.container.getParameters().setInstrumented(true);
		NotificationsRecorder notifications = new NotificationsRecorder();
		this.rcs.subscribe(EventsManagerEvents.class, notifications);
		Probe producer = this.rcs.produceEvents(Probe.class);
		this.rcs.subscribe(Probe.class, new FailingRecorder());
		this.rcs.subscribe(Probe.class, new Recorder());

		producer.onPing("boom");

		assertEquals(2, notifications.started.size());
		assertEquals(2, notifications.failures.size());
		long failed = notifications.failures.stream().filter(f -> f != null).count();
		assertEquals(1, failed);
		Throwable failure = notifications.failures.stream().filter(f -> f != null).findFirst().get();
		assertTrue(failure instanceof IllegalStateException);
		assertEquals("consumer failure boom", failure.getMessage());
		assertFalse(notifications.started.get(0));
		assertEquals(List.of("Event consumer callback failed."),
				this.producerSideNotifierInvocationHandlerLogs.messages(Level.SEVERE));
	}

	@Test
	public void filterReturningTrueHidesTheEventFromThatConsumerOnly() {
		Probe producer = this.rcs.produceEvents(Probe.class);
		Recorder filtered = new Recorder();
		Recorder unfiltered = new Recorder();
		SubscribeParameters parameters = new SubscribeParametersImpl();
		parameters.setEventFilter((ProbeFilter) message -> message.startsWith("hidden"));
		this.rcs.subscribe(Probe.class, filtered, parameters);
		this.rcs.subscribe(Probe.class, unfiltered);

		producer.onPing("visible");
		producer.onPing("hidden one");

		assertEquals(List.of("visible"), filtered.received);
		assertEquals(List.of("visible", "hidden one"), unfiltered.received);
	}

	@Test
	public void subscribeParametersWithoutFilterDeliverEverything() {
		Probe producer = this.rcs.produceEvents(Probe.class);
		Recorder recorder = new Recorder();
		this.rcs.subscribe(Probe.class, recorder, new SubscribeParametersImpl());

		producer.onPing("a");

		assertEquals(List.of("a"), recorder.received);
	}

	@Test
	public void subscribeParametersCarryTheirFilter() {
		SubscribeParameters parameters = new SubscribeParametersImpl();
		assertNull(parameters.getEventFilter());
		ProbeFilter filter = message -> false;
		parameters.setEventFilter(filter);
		assertNotNull(parameters.getEventFilter());
		assertEquals(filter, parameters.getEventFilter());
	}

	@Test
	public void subscribingTwiceWithTheSameConsumerIsReportedAsADynamicVerification() {
		this.container.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		this.rcs.produceEvents(Probe.class);
		Recorder recorder = new Recorder();
		this.rcs.subscribe(Probe.class, recorder);

		RuntimeException failure = assertThrows(RuntimeException.class,
				() -> this.rcs.subscribe(Probe.class, recorder));

		assertTrue(failure.getMessage(), failure.getMessage().contains("already subscribed"));
	}

	@Test
	public void aProducerWithDelegatesTakesOverEventsAlreadyProducedWithoutDelegates() {
		this.rcs.produceEvents(Probe.class);
		List<Probe> subscribed = Collections.synchronizedList(new ArrayList<>());
		Probe producer = this.rcs.produceEvents(RawEventsDelegationFailuresTests.class, Probe.class, subscribed::add,
				consumer -> {
				});
		Recorder recorder = new Recorder();

		this.rcs.subscribe(Probe.class, recorder);
		producer.onPing("delegated");

		assertEquals(1, subscribed.size());
		assertEquals(List.of("delegated"), recorder.received);
	}
}
