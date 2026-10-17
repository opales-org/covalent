/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.events.EventsManagerServices;
import org.opales.covalent.container.events.SubscribeParameters;
import org.opales.covalent.container.events.impl.ConsumerMetadata;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.CovalentContainerizedTests;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents.ProducerWithSubscribeEventsFilter;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithclone.ProducerWithClonedAttributeEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithclone.ProducerWithClonedEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithclone.ProducerWithClonedMethodEvents;
import org.opales.covalent.container.tests.unit.data.mutable.MutableData;
import org.opales.covalent.container.events.impl.ProducerSideNotifierInvocationHandler;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.impl.ComponentA;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.ComponentAEvents;
import org.opales.covalent.container.tests.unit.rawcomponents.componentb.impl.ComponentB;
import org.opales.covalent.container.tests.unit.rawcomponents.componentc.ComponentCEvents;
import org.opales.covalent.container.tests.unit.rawcomponents.componentc.impl.ComponentC;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;

public class EventsContainerizedTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	private LifecycleServices lifecycleServices;

	@Before
	public void setUp() {
		this.rcs.registerComponent(this);
		// these tests are made with synchronous events to avoid implementing complex
		// thread synchronization
		this.cm.getParameters().setSynchronousEvents(true);
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
		// Looked up here, not as a field initializer: the base class's own
		// @Before (which runs first) is what starts the container, and this raw
		// lookup needs it already running.
		this.lifecycleServices = this.cm.rcs().useServices(LifecycleServices.class);
	}

	@After
	public void tearDown() {
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.trace);
	}

	@Test
	public void rawUnproduceEventsRemovesProducerEntryAndInvalidatesProducerProxy() {
		EventsManagerServices eventsManagerServices = this.rcs.useServices(EventsManagerServices.class);
		ComponentA producer = new ComponentA();

		this.rcs.registerComponent(producer);
		try {
			Assert.assertTrue(eventsManagerServices.getProducerToEvent().containsKey(ComponentA.class));

			this.rcs.unproduceEvents(ComponentA.class, ComponentAEvents.class);

			Assert.assertFalse(eventsManagerServices.getProducerToEvent().containsKey(ComponentA.class));
			Assert.assertThrows(IllegalStateException.class,
					() -> producer.getProduceComponentAEvents().onCreate("event"));
		} finally {
			if (eventsManagerServices.getProducerToEvent().containsKey(ComponentA.class)) {
				eventsManagerServices.deleteProducer(ComponentA.class, ComponentAEvents.class);
			}
			this.rcs.unregisterComponent(ComponentA.class);
		}
	}

	@Test
	public void failingSubscriberDoesNotPreventOtherSubscribers() {
		CapturingLogHandler logHandler = CapturingLogHandler.install(ProducerSideNotifierInvocationHandler.class);
		ComponentA producer = new ComponentA();
		FailingComponentAEventsConsumer failingConsumer = new FailingComponentAEventsConsumer();
		RecordingComponentAEventsConsumer recordingConsumer = new RecordingComponentAEventsConsumer();

		try {
			this.rcs.registerComponent(producer);
			this.rcs.registerComponent(new ComponentB());
			try {
				this.rcs.subscribe(EventsContainerizedTests.class, ComponentAEvents.class, failingConsumer);
				this.rcs.subscribe(ComponentB.class, ComponentAEvents.class, recordingConsumer);

				producer.getProduceComponentAEvents().onCreate("event");

				Assert.assertEquals(1, failingConsumer.receivedCount);
				Assert.assertEquals(1, recordingConsumer.receivedCount);
				Assert.assertTrue(logHandler.containsSevereMessageWithFailure("Event consumer callback failed.",
						"expected subscriber failure"));
			} finally {
				this.rcs.unsubscribe(EventsContainerizedTests.class, ComponentAEvents.class, failingConsumer);
				this.rcs.unsubscribe(ComponentB.class, ComponentAEvents.class, recordingConsumer);
				this.rcs.unregisterComponent(ComponentA.class);
				this.rcs.unregisterComponent(ComponentB.class);
			}
		} finally {
			logHandler.restore();
		}
	}

	@Test
	public void failingEventFilterDoesNotPreventOtherSubscribers() {
		CapturingLogHandler logHandler = CapturingLogHandler.install(ProducerSideNotifierInvocationHandler.class);
		ComponentA producer = new ComponentA();
		ThrowingComponentAEventsFilter throwingFilter = new ThrowingComponentAEventsFilter();
		RecordingComponentAEventsConsumer filteredConsumer = new RecordingComponentAEventsConsumer();
		RecordingComponentAEventsConsumer recordingConsumer = new RecordingComponentAEventsConsumer();
		SubscribeParameters subscribeParameters = this.cm.getParameters().newSubscribeParameters();
		subscribeParameters.setEventFilter(throwingFilter);

		try {
			this.rcs.registerComponent(producer);
			this.rcs.registerComponent(new ComponentB());
			try {
				this.rcs.subscribe(EventsContainerizedTests.class, ComponentAEvents.class, filteredConsumer, subscribeParameters);
				this.rcs.subscribe(ComponentB.class, ComponentAEvents.class, recordingConsumer);

				producer.getProduceComponentAEvents().onCreate("event");

				Assert.assertEquals(1, throwingFilter.receivedCount);
				Assert.assertEquals(0, filteredConsumer.receivedCount);
				Assert.assertEquals(1, recordingConsumer.receivedCount);
				Assert.assertTrue(logHandler.containsSevereMessageWithFailure("Event consumer callback failed.",
						"expected filter failure"));
			} finally {
				this.unsubscribeIfNeeded(EventsContainerizedTests.class, ComponentAEvents.class, filteredConsumer);
				this.unsubscribeIfNeeded(ComponentB.class, ComponentAEvents.class, recordingConsumer);
				this.rcs.unregisterComponent(ComponentA.class);
				this.rcs.unregisterComponent(ComponentB.class);
			}
		} finally {
			logHandler.restore();
		}
	}

	@Test
	public void subscriberCanUnsubscribeItselfDuringDispatch() {
		ComponentA producer = new ComponentA();
		SelfUnsubscribingComponentAEventsConsumer selfUnsubscribingConsumer = new SelfUnsubscribingComponentAEventsConsumer(
				this.rcs, EventsContainerizedTests.class);
		RecordingComponentAEventsConsumer recordingConsumer = new RecordingComponentAEventsConsumer();

		this.rcs.registerComponent(producer);
		this.rcs.registerComponent(new ComponentB());
		try {
			this.rcs.subscribe(EventsContainerizedTests.class, ComponentAEvents.class, selfUnsubscribingConsumer);
			this.rcs.subscribe(ComponentB.class, ComponentAEvents.class, recordingConsumer);

			producer.getProduceComponentAEvents().onCreate("first");
			producer.getProduceComponentAEvents().onCreate("second");

			Assert.assertEquals(1, selfUnsubscribingConsumer.receivedCount);
			Assert.assertEquals(2, recordingConsumer.receivedCount);
		} finally {
			this.unsubscribeIfNeeded(EventsContainerizedTests.class, ComponentAEvents.class, selfUnsubscribingConsumer);
			this.unsubscribeIfNeeded(ComponentB.class, ComponentAEvents.class, recordingConsumer);
			this.rcs.unregisterComponent(ComponentA.class);
			this.rcs.unregisterComponent(ComponentB.class);
		}
	}

	@Test
	public void subscriberUnsubscribingAnotherSubscriberDuringDispatchAffectsFollowingEventsOnly() {
		ComponentA producer = new ComponentA();
		RecordingComponentAEventsConsumer removedConsumer = new RecordingComponentAEventsConsumer();
		RecordingComponentAEventsConsumer stableConsumer = new RecordingComponentAEventsConsumer();
		MutatingComponentAEventsConsumer mutatingConsumer = new MutatingComponentAEventsConsumer(
				() -> this.rcs.unsubscribe(ComponentB.class, ComponentAEvents.class, removedConsumer));

		this.rcs.registerComponent(producer);
		this.rcs.registerComponent(new ComponentB());
		this.rcs.registerComponent(new ComponentC());
		try {
			this.rcs.subscribe(EventsContainerizedTests.class, ComponentAEvents.class, mutatingConsumer);
			this.rcs.subscribe(ComponentB.class, ComponentAEvents.class, removedConsumer);
			this.rcs.subscribe(ComponentC.class, ComponentAEvents.class, stableConsumer);

			producer.getProduceComponentAEvents().onCreate("first");
			producer.getProduceComponentAEvents().onCreate("second");

			Assert.assertEquals(2, mutatingConsumer.receivedCount);
			Assert.assertEquals(1, removedConsumer.receivedCount);
			Assert.assertEquals(2, stableConsumer.receivedCount);
		} finally {
			this.unsubscribeIfNeeded(EventsContainerizedTests.class, ComponentAEvents.class, mutatingConsumer);
			this.unsubscribeIfNeeded(ComponentB.class, ComponentAEvents.class, removedConsumer);
			this.unsubscribeIfNeeded(ComponentC.class, ComponentAEvents.class, stableConsumer);
			this.rcs.unregisterComponent(ComponentA.class);
			this.rcs.unregisterComponent(ComponentB.class);
			this.rcs.unregisterComponent(ComponentC.class);
		}
	}

	@Test
	public void subscriberAddingAnotherSubscriberDuringDispatchAffectsFollowingEventsOnly() {
		ComponentA producer = new ComponentA();
		RecordingComponentAEventsConsumer addedConsumer = new RecordingComponentAEventsConsumer();
		RecordingComponentAEventsConsumer stableConsumer = new RecordingComponentAEventsConsumer();
		MutatingComponentAEventsConsumer mutatingConsumer = new MutatingComponentAEventsConsumer(
				() -> this.rcs.subscribe(ComponentC.class, ComponentAEvents.class, addedConsumer));

		this.rcs.registerComponent(producer);
		this.rcs.registerComponent(new ComponentB());
		this.rcs.registerComponent(new ComponentC());
		try {
			this.rcs.subscribe(EventsContainerizedTests.class, ComponentAEvents.class, mutatingConsumer);
			this.rcs.subscribe(ComponentB.class, ComponentAEvents.class, stableConsumer);

			producer.getProduceComponentAEvents().onCreate("first");
			producer.getProduceComponentAEvents().onCreate("second");

			Assert.assertEquals(2, mutatingConsumer.receivedCount);
			Assert.assertEquals(2, stableConsumer.receivedCount);
			Assert.assertEquals(1, addedConsumer.receivedCount);
		} finally {
			this.unsubscribeIfNeeded(EventsContainerizedTests.class, ComponentAEvents.class, mutatingConsumer);
			this.unsubscribeIfNeeded(ComponentB.class, ComponentAEvents.class, stableConsumer);
			this.unsubscribeIfNeeded(ComponentC.class, ComponentAEvents.class, addedConsumer);
			this.rcs.unregisterComponent(ComponentA.class);
			this.rcs.unregisterComponent(ComponentB.class);
			this.rcs.unregisterComponent(ComponentC.class);
		}
	}

	@Test
	public void eventFilterCanBlockOneSubscriberWithoutBlockingOthers() {
		ComponentA producer = new ComponentA();
		RecordingComponentAEventsConsumer filteredConsumer = new RecordingComponentAEventsConsumer();
		RecordingComponentAEventsConsumer unfilteredConsumer = new RecordingComponentAEventsConsumer();
		BlockingComponentAEventsFilter filter = new BlockingComponentAEventsFilter("blocked");
		SubscribeParameters subscribeParameters = this.cm.getParameters().newSubscribeParameters();
		subscribeParameters.setEventFilter(filter);

		this.rcs.registerComponent(producer);
		this.rcs.registerComponent(new ComponentB());
		try {
			this.rcs.subscribe(EventsContainerizedTests.class, ComponentAEvents.class, filteredConsumer, subscribeParameters);
			this.rcs.subscribe(ComponentB.class, ComponentAEvents.class, unfilteredConsumer);

			producer.getProduceComponentAEvents().onCreate("allowed");
			producer.getProduceComponentAEvents().onCreate("blocked");

			Assert.assertEquals(2, filter.receivedCount);
			Assert.assertEquals(1, filteredConsumer.receivedCount);
			Assert.assertEquals(2, unfilteredConsumer.receivedCount);
		} finally {
			this.unsubscribeIfNeeded(EventsContainerizedTests.class, ComponentAEvents.class, filteredConsumer);
			this.unsubscribeIfNeeded(ComponentB.class, ComponentAEvents.class, unfilteredConsumer);
			this.rcs.unregisterComponent(ComponentA.class);
			this.rcs.unregisterComponent(ComponentB.class);
		}
	}

	@Test
	public void replacementProducerKeepsExistingSubscriberAfterOriginalProducerIsUnregistered() {
		ComponentA originalProducer = new ComponentA();
		RecordingComponentAEventsConsumer consumer = new RecordingComponentAEventsConsumer();

		this.rcs.registerComponent(originalProducer);
		this.rcs.registerComponent(new ComponentB());
		try {
			this.rcs.subscribe(ComponentB.class, ComponentAEvents.class, consumer);
			ComponentAEvents originalEvents = originalProducer.getProduceComponentAEvents();

			originalEvents.onCreate("first");
			this.rcs.unregisterComponent(ComponentA.class);

			Assert.assertThrows(IllegalStateException.class, () -> originalEvents.onCreate("ignored"));

			ComponentA replacementProducer = new ComponentA();
			this.rcs.registerComponent(replacementProducer);
			replacementProducer.getProduceComponentAEvents().onCreate("second");

			Assert.assertEquals(2, consumer.receivedCount);
		} finally {
			this.unsubscribeIfNeeded(ComponentB.class, ComponentAEvents.class, consumer);
			this.rcs.unregisterComponent(ComponentA.class);
			this.rcs.unregisterComponent(ComponentB.class);
		}
	}

	@Test
	public void eventsManagerReportsSubscribersFromProducerNotifiers() {
		EventsManagerServices eventsManagerServices = this.rcs.useServices(EventsManagerServices.class);
		ComponentA producer = new ComponentA();
		RecordingComponentAEventsConsumer consumer = new RecordingComponentAEventsConsumer();

		this.rcs.registerComponent(producer);
		this.rcs.registerComponent(new ComponentB());
		try {
			Assert.assertFalse(eventsManagerServices.isSubscribed(ComponentAEvents.class, consumer));

			this.rcs.subscribe(ComponentB.class, ComponentAEvents.class, consumer);

			Assert.assertTrue(eventsManagerServices.isSubscribed(ComponentAEvents.class, consumer));
			Map<Class<?>, Set<ConsumerMetadata<?>>> subscribers = eventsManagerServices.getSubscribers();
			Assert.assertTrue(subscribers.containsKey(ComponentAEvents.class));
			Assert.assertEquals(1, subscribers.get(ComponentAEvents.class).size());

			subscribers.clear();
			Assert.assertTrue(eventsManagerServices.getSubscribers().containsKey(ComponentAEvents.class));
		} finally {
			this.unsubscribeIfNeeded(ComponentB.class, ComponentAEvents.class, consumer);
			this.rcs.unregisterComponent(ComponentA.class);
			this.rcs.unregisterComponent(ComponentB.class);
		}
	}

	@Test
	public void deletingMissingProducerIsIgnoredByEventsManagerValidation() {
		EventsManagerServices eventsManagerServices = this.rcs.useServices(EventsManagerServices.class);
		ComponentA producer = new ComponentA();

		eventsManagerServices.deleteProducer(ComponentB.class, ComponentAEvents.class);

		this.rcs.registerComponent(producer);
		try {
			eventsManagerServices.deleteProducer(ComponentA.class, ComponentCEvents.class);

			Assert.assertTrue(eventsManagerServices.getProducerToEvent().containsKey(ComponentA.class));
			Assert.assertTrue(eventsManagerServices.getProducerToEvent().get(ComponentA.class)
					.contains(ComponentAEvents.class));
		} finally {
			this.rcs.unregisterComponent(ComponentA.class);
		}
	}

	@Test
	public void failingSubscribeDelegateCancelsSubscription() {
		// produceEvents below uses this test class as an explicit, otherwise-
		// unregistered componentType, purely to pair produceEvents/subscribe calls.
		// RawPortSupport.requireKnownComponentType requires componentType to
		// actually be registered before a producer-side port can be created for it.
		this.rcs.registerComponent(EventsContainerizedTests.class, this);
		this.rcs.registerComponent(new ComponentB());
		CapturingLogHandler logHandler = CapturingLogHandler.install(ProducerSideNotifierInvocationHandler.class);
		RecordingComponentAEventsConsumer consumer = new RecordingComponentAEventsConsumer();
		ComponentAEvents producer = this.rcs.produceEvents(EventsContainerizedTests.class, ComponentAEvents.class, eventConsumer -> {
			throw new IllegalStateException("expected subscribe delegate failure");
		});

		try {
			this.rcs.subscribe(ComponentB.class, ComponentAEvents.class, consumer);
			producer.onCreate("event");

			Assert.assertEquals(0, consumer.receivedCount);
			Assert.assertTrue(logHandler.containsSevereMessageWithFailure("Subscribe delegate failed.",
					"expected subscribe delegate failure"));
		} finally {
			this.unsubscribeIfNeeded(ComponentB.class, ComponentAEvents.class, consumer);
			this.rcs.unproduceEvents(EventsContainerizedTests.class, ComponentAEvents.class);
			this.rcs.unregisterComponent(EventsContainerizedTests.class);
			this.rcs.unregisterComponent(ComponentB.class);
			logHandler.restore();
		}
	}

	@Test
	public void testEventUnproduceThenProduceEvent() throws InterruptedException {
		ComponentA componentA = new ComponentA();
		this.rcs.registerComponent(componentA);

		ComponentB componentB = new ComponentB();
		this.rcs.registerComponent(componentB);

		ComponentC componentC = new ComponentC();
		this.rcs.registerComponent(componentC);

		// Subscribe to A and C
		componentB.consumeEventA(true);
		componentB.consumeEventC(true);

		// Unproduce event A
		componentA.produceEvents(false);

		// Notify A and C
		IllegalStateException e = Assert.assertThrows(IllegalStateException.class,
				() -> componentA.getProduceComponentAEvents().onCreate("data"));
		Assert.assertTrue(this.isInvalidatedException(e));
		componentC.getProduceComponentCEvents().onCEvent(1);

		// One time C event but no one for A event
		Assert.assertEquals(0, componentB.onCreate);
		Assert.assertEquals(1, componentB.onCEvent);

		// Re produce event A
		componentA.produceEvents(true);

		// Notify A and C
		componentA.getProduceComponentAEvents().onCreate("data");
		componentC.getProduceComponentCEvents().onCEvent(1);

		// One more event for both A and C
		Assert.assertEquals(1, componentB.onCreate);
		Assert.assertEquals(2, componentB.onCEvent);

		this.rcs.unregisterComponent(ComponentA.class);
		this.rcs.unregisterComponent(ComponentB.class);
		this.rcs.unregisterComponent(ComponentC.class);
	}

	@Test
	public void testStopEventProducerComponent() throws InterruptedException {
		ComponentA componentA = new ComponentA(true);
		this.rcs.registerComponent(componentA);

		ComponentB componentB = new ComponentB();
		this.rcs.registerComponent(componentB);

		// Subscribe to A
		componentB.consumeEventA(true);

		// Stop component A
		this.rcs.unregisterComponent(ComponentA.class);

		// Notify A
		IllegalStateException e = Assert.assertThrows(IllegalStateException.class,
				() -> componentA.getProduceComponentAEvents().onUpdate("data"));
		Assert.assertTrue(this.isInvalidatedException(e));
		// no event triggered
		Assert.assertEquals(0, componentB.onUpdate);

		// Re register component A2
		ComponentA componentA2 = new ComponentA(true);
		this.rcs.registerComponent(componentA2);

		// Notify A2
		componentA2.getProduceComponentAEvents().onUpdate("data");

		// Event shall be triggered
		Assert.assertEquals(1, componentB.onUpdate);

		// Notify A
		IllegalStateException laterFailure = Assert.assertThrows(IllegalStateException.class,
				() -> componentA.getProduceComponentAEvents().onUpdate("data"));
		Assert.assertTrue(this.isInvalidatedException(laterFailure));

		// Event shall not be triggered
		Assert.assertEquals(1, componentB.onUpdate);

		this.rcs.unregisterComponent(ComponentA.class);
		this.rcs.unregisterComponent(ComponentB.class);
	}

	@Test
	public void testTransferPendingSubscribersAfterProducingAgain() throws InterruptedException {
		// - start producing and consuming events.
		// - stop producing, then
		// start again. This will invalidate the producer side proxy, and backup
		// the producer in event manager.
		// - register the producer again. The pending
		// consumers should be transferred again to the proxy and receive notifications.
		ComponentA componentA = new ComponentA();
		this.rcs.registerComponent(componentA);
		componentA.produceEvents(true);

		ComponentB componentB = new ComponentB();
		this.rcs.registerComponent(componentB);

		componentB.consumeEventA(true);

		// produce a first event
		componentA.getProduceComponentAEvents().onCreate("data");

		// stop producing events
		componentA.produceEvents(false);

		try {
			// proxy should be invalidated
			componentA.getProduceComponentAEvents().onCreate("data");
			Assert.fail();
		} catch (IllegalStateException e) {
			Assert.assertTrue(this.isInvalidatedException(e));
		}

		// start producing events again
		componentA.produceEvents(true);

		// produce a second event
		componentA.getProduceComponentAEvents().onCreate("data");

		// consumer should have received two notifications
		Assert.assertEquals(2, componentB.onCreate);

		this.rcs.unregisterComponent(ComponentA.class);
	}

	@Test
	public void testEventSubscribeAndNotify() throws InterruptedException {
		ComponentA componentA = new ComponentA();
		this.rcs.registerComponent(componentA);

		ComponentB componentB = new ComponentB();
		this.rcs.registerComponent(componentB);

		ComponentC componentC = new ComponentC();
		this.rcs.registerComponent(componentC);

		componentA.getProduceComponentAEvents().onCreate("data");

		Assert.assertEquals(0, componentB.onCreate);
		Assert.assertEquals(0, componentB.onCEvent);
		Assert.assertEquals(0, componentB.onDelete);

		componentB.consumeEventA(true);
		componentB.consumeEventC(true);

		componentA.getProduceComponentAEvents().onCreate("data");

		Assert.assertEquals(1, componentB.onCreate);
		Assert.assertEquals(0, componentB.onCEvent);
		Assert.assertEquals(0, componentB.onDelete);

		componentC.getProduceComponentCEvents().onCEvent(1);

		Assert.assertEquals(1, componentB.onCreate);
		Assert.assertEquals(1, componentB.onCEvent);
		Assert.assertEquals(0, componentB.onDelete);

		componentA.getProduceComponentAEvents().onDelete("data");

		Assert.assertEquals(1, componentB.onCreate);
		Assert.assertEquals(1, componentB.onCEvent);
		Assert.assertEquals(1, componentB.onDelete);

		this.rcs.unregisterComponent(ComponentA.class);
		this.rcs.unregisterComponent(ComponentB.class);
		this.rcs.unregisterComponent(ComponentC.class);
	}

	@Test
	public void eventUnsubscribeAndNotifyTest() throws InterruptedException {
		ComponentA componentA = new ComponentA();
		this.rcs.registerComponent(componentA);

		ComponentB componentB = new ComponentB();
		this.rcs.registerComponent(componentB);

		ComponentC componentC = new ComponentC();
		this.rcs.registerComponent(componentC);

		// Subscribe to A and C
		componentB.consumeEventA(true);
		componentB.consumeEventC(true);

		// Notify A and C
		componentA.getProduceComponentAEvents().onCreate("data");
		componentC.getProduceComponentCEvents().onCEvent(1);

		// One time A event and one time C event
		Assert.assertEquals(1, componentB.onCreate);
		Assert.assertEquals(1, componentB.onCEvent);

		// Unsubscribe A
		componentB.consumeEventA(false);

		// Notify A and C
		componentA.getProduceComponentAEvents().onCreate("data");
		componentC.getProduceComponentCEvents().onCEvent(1);

		// Does not receive A but get one more C
		Assert.assertEquals(1, componentB.onCreate);
		Assert.assertEquals(2, componentB.onCEvent);

		// Unsubscribe C
		componentB.consumeEventC(false);

		// Notify A and C
		componentA.getProduceComponentAEvents().onDelete("data");
		componentC.getProduceComponentCEvents().onCEvent(1);

		// No changes
		Assert.assertEquals(1, componentB.onCreate);
		Assert.assertEquals(2, componentB.onCEvent);

		this.rcs.unregisterComponent(ComponentA.class);
		this.rcs.unregisterComponent(ComponentB.class);
		this.rcs.unregisterComponent(ComponentC.class);
	}

	@Test
	public void testConsumeEventBeforeProducerStart() throws InterruptedException {
		ComponentB componentB = new ComponentB();
		this.rcs.registerComponent(componentB);

		// Subscribe to A
		componentB.consumeEventA(true);

		// Start component A
		ComponentA componentA = new ComponentA();
		this.rcs.registerComponent(componentA);

		// Notify A
		componentA.getProduceComponentAEvents().onCreate("data");

		// Event shall be triggered
		Assert.assertEquals(1, componentB.onCreate);

		this.rcs.unregisterComponent(ComponentA.class);
		this.rcs.unregisterComponent(ComponentB.class);
	}

	@Test
	public void testInitialStateEvent() throws InterruptedException {
		ComponentA componentA = new ComponentA(true);
		this.rcs.registerComponent(componentA);

		ComponentB componentB = new ComponentB();
		this.rcs.registerComponent(componentB);

		componentB.consumeEventA(true);

		// On subscribe a create event shall be triggered
		Assert.assertEquals(1, componentB.onCreate);

		this.rcs.unregisterComponent(ComponentA.class);
		this.rcs.unregisterComponent(ComponentB.class);
	}

	private <EVENTS> void unsubscribeIfNeeded(final Class<?> consumerComponentType, final Class<EVENTS> eventsInterface,
			final EVENTS consumer) {
		EventsManagerServices eventsManagerServices = this.rcs.useServices(EventsManagerServices.class);
		if (eventsManagerServices.isSubscribed(eventsInterface, consumer)) {
			this.rcs.unsubscribe(consumerComponentType, eventsInterface, consumer);
		}
	}

	public static class MutatingComponentAEventsConsumer implements ComponentAEvents {
		private final Runnable mutation;

		private boolean mutationDone;

		int receivedCount;

		MutatingComponentAEventsConsumer(final Runnable mutation) {
			this.mutation = mutation;
		}

		@Override
		public void onCreate(final String data) {
			this.receivedCount++;
			if (!this.mutationDone) {
				this.mutationDone = true;
				this.mutation.run();
			}
		}

		@Override
		public void onUpdate(final String data) {
		}

		@Override
		public void onDelete(final String data) {
		}

		@Override
		public void onEmpty() {
		}
	}

	public static class BlockingComponentAEventsFilter {
		private final String blockedValue;

		int receivedCount;

		BlockingComponentAEventsFilter(final String blockedValue) {
			this.blockedValue = blockedValue;
		}

		public boolean filterCreate(final String data) {
			this.receivedCount++;
			return this.blockedValue.equals(data);
		}
	}

}
