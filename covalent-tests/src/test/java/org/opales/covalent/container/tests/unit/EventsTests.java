/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.events.EventsManager;
import org.opales.covalent.container.events.EventsManagerServices;
import org.opales.covalent.container.events.impl.EventsManagerImpl;
import org.opales.covalent.container.exceptions.ValidationException;
import org.opales.covalent.container.lifecycle.LifecycleEvents;
import org.opales.covalent.container.lifecycle.LifecycleParameters;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.tests.CovalentTests;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.ConsumerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.consumerwithgenericsubscribe.impl.ConsumerWithGenericSubscribeImpl;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.consumerwithmanualsubscribe.ConsumerWithManualSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.consumerwithmanualsubscribe.impl.ConsumerWithManualSubscribeImpl;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.consumerwithfilteredsubscribe.ConsumerWithFilteredSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.consumerwithfilteredsubscribe.impl.ConsumerWithFilteredSubscribeImpl;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.consumerwithproducerdelegationandmanualsubscribe.ConsumerWithProducerDelegationAndManualSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.consumerwithproducerdelegationandmanualsubscribe.impl.ConsumerWithProducerDelegationAndManualSubscribeImpl;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.simpleconsumer.SimpleConsumer;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.simpleconsumer.impl.SimpleConsumerImpl;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.simpleconsumerofeventswithsubscriber.SimpleConsumerOfEventsWithSubscriber;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.simpleconsumerofeventswithsubscriber.impl.SimpleConsumerOfEventsWithSubscriberImpl;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents.ProducerEventsSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents.ProducerWithSubscribeEventsFilter;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithclone.ProducerWithCloneServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithclone.ProducerWithClonedAttributeEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithclone.ProducerWithClonedEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithclone.ProducerWithClonedMethodEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithclone.impl.ProducerWithCloneImpl;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithdelegation.ProducerWithSubscribeAndDelegation;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithdelegation.impl.ProducerWithSubscribeAndDelegationImpl;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithsubscribe.ProducerWithSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithsubscribe.impl.ProducerWithSubscribeImpl;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.simpleproducer.SimpleProducer;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.simpleproducer.impl.SimpleProducerImpl;
import org.opales.covalent.container.tests.unit.data.mutable.MutableData;
import org.opales.covalent.container.tests.unit.data.mutable.impl.MutableCloneableDataImpl;
import org.opales.covalent.container.tests.unit.data.mutable.impl.MutableNotCloneableDataImpl;
import org.opales.covalent.container.events.impl.ProducerSideNotifierInvocationHandler;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.impl.ComponentA;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.ComponentAEvents;
import org.opales.covalent.container.tests.unit.rawcomponents.componentb.impl.ComponentB;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;

public class EventsTests extends CovalentTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	// A direct container facade, not a raw useServices() proxy: it does not
	// require the container to already be running, unlike
	// Covalent.main.rcs().useServices(LifecycleServices.class), which several
	// tests below call before this.cm.start().
	private final LifecycleServices lifecycleServices = Covalent.main.lcs();

	@Before
	public void setUp() {
		this.rcs.registerComponent(this);
		// these tests are made with synchronous events to avoid implementing complex
		// thread synchronization
		this.cm.getParameters().setSynchronousEvents(true);
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
	}

	@After
	public void tearDown() {
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.trace);
		this.cm.stop();
	}

	@Test
	public void failingManagedSubscribeDelegateCancelsSubscription() {
		CapturingLogHandler logHandler = CapturingLogHandler.install(ProducerSideNotifierInvocationHandler.class);
		RecordingProducerWithSubscribeEventsConsumer consumer = new RecordingProducerWithSubscribeEventsConsumer();

		try {
			this.cm.deployComponents(ThrowingSubscribeProducerImpl.class);
			this.cm.start();
			this.rcs.registerComponent(new ComponentB());
			this.rcs.subscribe(ComponentB.class, ProducerWithSubscribeEvents.class, consumer);

			ThrowingSubscribeProducerServices producerServices = this.rcs
					.useServices(ThrowingSubscribeProducerServices.class);
			producerServices.fire("event");

			Assert.assertEquals(0, consumer.receivedCount);
			Assert.assertTrue(logHandler.containsSevereMessageWithFailure("Subscribe delegate failed.",
					"expected managed subscribe delegate failure"));
		} finally {
			this.unsubscribeIfNeeded(ComponentB.class, ProducerWithSubscribeEvents.class, consumer);
			logHandler.restore();
		}
	}

	@Test
	public void failingManagedUnsubscribeDelegateIsLoggedNotThrown() {
		CapturingLogHandler logHandler = CapturingLogHandler.install(ProducerSideNotifierInvocationHandler.class);
		RecordingProducerWithSubscribeEventsConsumer consumer = new RecordingProducerWithSubscribeEventsConsumer();

		try {
			this.cm.deployComponents(ThrowingUnsubscribeProducerImpl.class);
			this.cm.start();
			this.rcs.registerComponent(new ComponentB());
			this.rcs.subscribe(ComponentB.class, ProducerWithSubscribeEvents.class, consumer);

			ThrowingSubscribeProducerServices producerServices = this.rcs
					.useServices(ThrowingSubscribeProducerServices.class);
			producerServices.fire("event");
			Assert.assertEquals(1, consumer.receivedCount);

			// the unsubscribe delegate itself throws (reflection dispatch path); the
			// caller must still see a clean unsubscribe, with the delegate failure only
			// reported through diagnostics.
			this.rcs.unsubscribe(ComponentB.class, ProducerWithSubscribeEvents.class, consumer);

			Assert.assertTrue(logHandler.containsSevereMessageWithFailure("Unsubscribe delegate failed.",
					"expected managed unsubscribe delegate failure"));
		} finally {
			this.unsubscribeIfNeeded(ComponentB.class, ProducerWithSubscribeEvents.class, consumer);
			logHandler.restore();
		}
	}

	@Test
	public void zeroArgumentEventIsDeliveredWithoutArguments() {
		this.cm.start();
		ComponentA componentA = new ComponentA(this.rcs, true);
		this.rcs.registerComponent(componentA);
		RecordingComponentAEventsConsumer consumer = new RecordingComponentAEventsConsumer();

		try {
			this.rcs.subscribe(EventsTests.class, ComponentAEvents.class, consumer);

			// onEmpty() takes no arguments: the dynamic proxy invocation passes a null
			// args array, which the delivery buffering path (BufferedEvent) must handle
			// without trying to clone/copy it.
			componentA.getProduceComponentAEvents().onEmpty();
			Assert.assertEquals(1, consumer.emptyReceivedCount);
		} finally {
			this.unsubscribeIfNeeded(EventsTests.class, ComponentAEvents.class, consumer);
			this.rcs.unregisterComponent(ComponentA.class);
		}
	}

	@Test
	public void unsubscribingWhileSubscribeDelegateIsStillRunningDoesNotCrash() throws Exception {
		// Unsubscribing while a subscribe delegate is still running does not necessarily
		// cancel the subscription: the consumer is recorded only once the delegate has
		// returned, so a concurrent unsubscribe finds no registration and does nothing
		// (logged at trace level, never thrown). Events are not delivered to a consumer
		// before its own subscribe delegate has completed (see
		// zeroArgumentEventIsDeliveredWithoutArguments and the other producer/consumer
		// tests below). This test only checks that the concurrent unsubscribe is
		// harmless: no exception, and the subscribing thread completes normally once the
		// delegate is released.
		this.cm.getParameters().setSynchronousEvents(false);
		this.cm.start();

		BlockingSubscribeDelegateProducer producer = new BlockingSubscribeDelegateProducer(this.rcs);
		RecordingComponentAEventsConsumer consumer = new RecordingComponentAEventsConsumer();

		Thread subscribing = new Thread(
				() -> this.rcs.subscribe(EventsTests.class, ComponentAEvents.class, consumer));
		try {
			subscribing.start();

			// wait until the subscribe delegate is running (and therefore blocked) on
			// the events worker thread.
			Assert.assertTrue("subscribe delegate did not start in time",
					producer.delegateStarted.await(5, TimeUnit.SECONDS));

			// unsubscribe concurrently, from this thread, while the delegate is still
			// blocked: must not throw, must not hang.
			this.rcs.unsubscribe(EventsTests.class, ComponentAEvents.class, consumer);

			producer.releaseDelegate.countDown();
			subscribing.join(5000);
			Assert.assertFalse("subscribing thread did not complete", subscribing.isAlive());
		} finally {
			producer.releaseDelegate.countDown();
			subscribing.join(5000);
			this.unsubscribeIfNeeded(EventsTests.class, ComponentAEvents.class, consumer);
			this.rcs.unregisterComponent(BlockingSubscribeDelegateProducer.class);
			this.cm.getParameters().setSynchronousEvents(true);
		}
	}

	@Test
	public void rawProducedEventsDoNotSurviveContainerRestart() throws Exception {
		// Same contract as for raw used services and accessed parameters, covered by
		// RawComponentsStopCleanupContainerizedTests (for instance
		// rawStopAllowsReprovidingServicesAfterRestart): container stop() tears down every
		// raw port, including produced events, and nothing re-establishes them at the next
		// start. A raw component registers again after a restart (a fresh instance, or a
		// fresh produceEvents() call); a proxy obtained before the stop() stays invalid.
		this.cm.start();
		this.cm.getParameters().setSynchronousEvents(false);

		RawAsyncEventsProducer producer = new RawAsyncEventsProducer(this.rcs);
		AwaitableComponentAEventsConsumer consumer = new AwaitableComponentAEventsConsumer();
		try {
			this.rcs.subscribe(EventsTests.class, ComponentAEvents.class, consumer);

			consumer.expect(1);
			producer.produceComponentAEvents.onCreate("before-restart");
			consumer.await();

			this.cm.stop();
			this.cm.start();
			this.rcs.registerComponent(this);

			// the pre-restart proxy is now permanently invalidated - firing
			// through it must fail loudly, not silently drop the event.
			IllegalStateException e = Assert.assertThrows("expected the pre-restart producer proxy to be invalidated",
					IllegalStateException.class,
					() -> producer.produceComponentAEvents.onCreate("after-restart-stale-proxy"));
			Assert.assertTrue(this.isExceptionIncludingText(e, "invalidated"));

			// the consumer's own raw subscription is torn down by the same
			// stop() cleanup, so it must resubscribe too - just like the
			// producer, nothing survives the restart.
			this.rcs.subscribe(EventsTests.class, ComponentAEvents.class, consumer);

			// re-registering after the restart re-establishes the port, exactly
			// like the services/parameters case.
			RawAsyncEventsProducer restartedProducer = new RawAsyncEventsProducer(this.rcs);
			try {
				consumer.expect(1);
				restartedProducer.produceComponentAEvents.onCreate("after-restart-fresh-producer");
				consumer.await();
				Assert.assertEquals(2, consumer.receivedCount);
			} finally {
				this.rcs.unproduceEvents(RawAsyncEventsProducer.class, ComponentAEvents.class);
				this.unsubscribeIfNeeded(EventsTests.class, ComponentAEvents.class, consumer);
			}
		} finally {
			this.cm.getParameters().setSynchronousEvents(true);
		}
	}

	/**
	 * A {@code ComponentAEvents} consumer that lets a test resynchronize with
	 * asynchronous dispatch: {@link #expect(int)} arms a latch for the next
	 * batch of events, and {@link #await()} blocks until they have actually
	 * been delivered, instead of polling {@code receivedCount} on a timer.
	 */
	private static class AwaitableComponentAEventsConsumer implements ComponentAEvents {
		int receivedCount;

		private volatile CountDownLatch latch;

		void expect(final int additionalEvents) {
			this.latch = new CountDownLatch(additionalEvents);
		}

		void await() throws InterruptedException {
			Assert.assertTrue("event(s) were not dispatched in time", this.latch.await(5, TimeUnit.SECONDS));
		}

		@Override
		public void onCreate(final String data) {
			this.receivedCount++;
			CountDownLatch currentLatch = this.latch;
			if (currentLatch != null) {
				currentLatch.countDown();
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

	@Test
	public void internalComponentEventsAreDispatchedSynchronously() {
		// EventsProxyFactory.getWorker(...) routes @Internal producers - here
		// LifecycleManagerImpl's LifecycleEvents - to the synchronous worker,
		// never to the asynchronous pool, regardless of the container's
		// async-events setting:
		// Bootstrapper.bootstrapInternalEvents() creates and activates every
		// internal producer very early in bootstrap, before
		// EventsManagerImpl.activate() has even created the asynchronous
		// worker pool, and internal events (onComponentPassivated in
		// particular) are still fired while that same pool is being torn
		// down/recreated across a container stop()/start(). Routing internal
		// events through it would race its own lifecycle.
		this.cm.getParameters().setSynchronousEvents(false);
		try {
			this.cm.start();

			EventsManagerImpl eventsManagerImpl = this.rcs.useServices(RegistryReadServices.class)
					.getComponentInstance(EventsManager.class);

			Thread testThread = Thread.currentThread();
			List<Thread> deliveryThreads = new ArrayList<>();
			LifecycleEvents recorder = new LifecycleEvents() {
				@Override
				public void onComponentInstantiated(final Class<?> componentType) {
					deliveryThreads.add(Thread.currentThread());
				}

				@Override
				public void onComponentInitialized(final Class<?> componentType) {
					deliveryThreads.add(Thread.currentThread());
				}

				@Override
				public void onComponentActivated(final Class<?> componentType) {
					deliveryThreads.add(Thread.currentThread());
				}

				@Override
				public void onComponentPassivated(final Class<?> componentType) {
					deliveryThreads.add(Thread.currentThread());
				}

				@Override
				public void onComponentRemoved(final Class<?> componentType) {
					deliveryThreads.add(Thread.currentThread());
				}

				@Override
				public void onComponentFailed(final Class<?> componentType) {
					deliveryThreads.add(Thread.currentThread());
				}
			};

			eventsManagerImpl.subscribe(LifecycleEvents.class, recorder, null);
			try {
				// registerComponent() synchronously drives this raw component
				// through instantiated/initialized/activated, each firing a
				// LifecycleEvents notification - while application events
				// (ComponentAEvents, produced below) go through the
				// asynchronous pool per this.cm.getParameters().
				this.rcs.registerComponent(new ComponentA(this.rcs));

				Assert.assertFalse("expected at least one internal LifecycleEvents notification",
						deliveryThreads.isEmpty());
				for (Thread deliveryThread : deliveryThreads) {
					Assert.assertSame(
							"internal component events must be delivered synchronously on the calling thread",
							testThread, deliveryThread);
				}
			} finally {
				eventsManagerImpl.unsubscribe(LifecycleEvents.class, recorder);
				this.rcs.unregisterComponent(ComponentA.class);
			}
		} finally {
			this.cm.getParameters().setSynchronousEvents(true);
		}
	}

	@Test
	public void testManualSubscribeWithAnnotations() {
		this.lifecycleServices.deployComponents(ProducerWithSubscribeImpl.class, ConsumerWithManualSubscribeImpl.class);
		this.cm.start();
		ConsumerServices consumerWithManualSubscribeCheckServices = this.rcs
				.useServices(ConsumerWithManualSubscribe.class, ConsumerServices.class);
		ProducerServices producerServices = this.rcs.useServices(ProducerWithSubscribe.class, ProducerServices.class);

		producerServices.fireEvent("1");
		Assert.assertTrue(consumerWithManualSubscribeCheckServices.checkEventReceived());
		Assert.assertTrue(consumerWithManualSubscribeCheckServices.checkCustomConsumerEventReceived());

		// try subscribe again with strict dynamic rules
		this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		ValidationException failure = Assert.assertThrows(ValidationException.class,
				() -> consumerWithManualSubscribeCheckServices.subscribe());
		Assert.assertTrue(this.isAlreadySubscribedException(failure));
	}

	@Test
	public void testAutomaticSubscribe() {
		this.lifecycleServices.deployComponents(SimpleProducerImpl.class, SimpleConsumerImpl.class);
		this.cm.start();
		ConsumerServices consumerWithAutoSubscribeCheckServices = this.rcs.useServices(SimpleConsumer.class,
				ConsumerServices.class);
		ProducerServices producerServices = this.rcs.useServices(ProducerServices.class);

		producerServices.fireEvent("event");
		Assert.assertTrue(consumerWithAutoSubscribeCheckServices.checkEventReceived());
	}

	@Test
	public void testAutomaticSubscribeToEventsWithSubscriber() {
		this.lifecycleServices.deployComponents(ProducerWithSubscribeImpl.class,
				SimpleConsumerOfEventsWithSubscriberImpl.class);
		this.cm.start();
		ConsumerServices consumerServices = this.rcs.useServices(SimpleConsumerOfEventsWithSubscriber.class,
				ConsumerServices.class);
		ProducerServices producerServices = this.rcs.useServices(ProducerWithSubscribe.class, ProducerServices.class);

		producerServices.fireEvent("event");
		Assert.assertTrue(consumerServices.checkEventReceived());
	}

	@Test
	public void testSubscriberDelegateManualSubscribeNoAnnotation() {
		this.lifecycleServices.deployComponents(ProducerWithSubscribeAndDelegationImpl.class);
		this.cm.start();
		ProducerServices producerWithDelegationServices = this.rcs.useServices(ProducerWithSubscribeAndDelegation.class,
				ProducerServices.class);

		ProducerWithDelegationEventsMock mock = new ProducerWithDelegationEventsMock();
		this.rcs.subscribe(EventsTests.class, ProducerWithSubscribeEvents.class, mock);

		producerWithDelegationServices.fireEvent("event");

		Assert.assertTrue(producerWithDelegationServices.checkDelegatedSubscribe());
		Assert.assertTrue(mock.received);
		Assert.assertEquals(2, mock.messages.size());
		Assert.assertEquals("subscribe", mock.messages.get(0));
		Assert.assertEquals("event", mock.messages.get(1));

		this.rcs.unsubscribe(EventsTests.class, ProducerWithSubscribeEvents.class, mock);

		Assert.assertTrue(producerWithDelegationServices.checkDelegatedUnsubscribe());
		Assert.assertEquals(3, mock.messages.size());
		Assert.assertEquals("unsubscribe", mock.messages.get(2));
	}

	@Test
	public void testSubscriberDelegateManualSubscribeWithAnnotation() {
		this.lifecycleServices.deployComponents(ProducerWithSubscribeAndDelegationImpl.class,
				ConsumerWithProducerDelegationAndManualSubscribeImpl.class);
		this.cm.start();
		ProducerServices producerWithDelegationServices = this.rcs.useServices(ProducerWithSubscribeAndDelegation.class,
				ProducerServices.class);

		ConsumerServices consumerWithProducerDelegationAndManualSubscribeServices = this.rcs
				.useServices(ConsumerWithProducerDelegationAndManualSubscribe.class, ConsumerServices.class);

		consumerWithProducerDelegationAndManualSubscribeServices.subscribe();
		Assert.assertTrue(producerWithDelegationServices.checkDelegatedSubscribe());
		Assert.assertTrue(consumerWithProducerDelegationAndManualSubscribeServices.checkEventReceived());

		consumerWithProducerDelegationAndManualSubscribeServices.unsubscribe();
		Assert.assertTrue(producerWithDelegationServices.checkDelegatedUnsubscribe());
	}

	@Test
	public void testSubscriberDelegateAutoSubscribe() {
		this.lifecycleServices.deployComponents(ProducerWithSubscribeAndDelegationImpl.class,
				SimpleConsumerOfEventsWithSubscriberImpl.class);
		this.cm.start();
		ProducerServices producerServices = this.rcs.useServices(ProducerWithSubscribeAndDelegation.class,
				ProducerServices.class);

		ConsumerServices simpleConsumerServices = this.rcs.useServices(SimpleConsumerOfEventsWithSubscriber.class,
				ConsumerServices.class);

		producerServices.fireEvent("event");
		Assert.assertTrue(simpleConsumerServices.checkEventReceived());
	}

	@Test
	public void testTransferPendingSubscribersAfterActivatingAgain() throws InterruptedException {
		// - start producing and consuming events.
		// - passivate the producer, then
		// activate it again. This will invalidate the producer side proxy, and backup
		// the producer in event manager.
		// - register the producer again. The pending
		// consumers should be transferred again to the proxy and receive notifications.

		this.lifecycleServices.deployComponents(SimpleProducerImpl.class, SimpleConsumerImpl.class);
		this.cm.start();

		RegistryReadServices registryReadServices = this.rcs.useServices(RegistryReadServices.class);
		ConsumerServices consumerServices = this.rcs.useServices(SimpleConsumer.class, ConsumerServices.class);

		SimpleProducerImpl instance = registryReadServices.getComponentInstance(SimpleProducer.class);

		instance.fireEvent("event");
		Assert.assertEquals(1, consumerServices.getEventsCount());

		this.lifecycleServices.passivateComponent(SimpleProducer.class);

		// passivation does not invalidate the producer, the events are just ignored
		instance.fireEvent("event");
		Assert.assertEquals(1, consumerServices.getEventsCount());

		this.lifecycleServices.activateComponent(SimpleProducer.class);

		instance.fireEvent("event");
		// consumer should have received 2 notifications not 3
		Assert.assertTrue(consumerServices.checkEventReceived());
		Assert.assertEquals(2, consumerServices.getEventsCount());
	}

	@Test
	public void testFilteredSubscriptionToDelegatingProducerSurvivesProducerReactivation() {
		// A consumer subscribed with a filter, through a subscribe interface the producer
		// implements, is parked while the producer is passivated, then subscribed again
		// with the same filter once the producer is activated again.
		this.lifecycleServices.deployComponents(ProducerWithSubscribeAndDelegationImpl.class,
				ConsumerWithFilteredSubscribeImpl.class);
		this.cm.start();
		ProducerServices producerServices = this.rcs.useServices(ProducerWithSubscribeAndDelegation.class,
				ProducerServices.class);
		ConsumerServices consumerServices = this.rcs.useServices(ConsumerWithFilteredSubscribe.class,
				ConsumerServices.class);

		consumerServices.subscribe();
		producerServices.fireEvent("event");
		producerServices.fireEvent("hidden event");
		Assert.assertEquals(List.of("event"), consumerServices.getEvents());

		this.lifecycleServices.passivateComponent(ProducerWithSubscribeAndDelegation.class);
		this.lifecycleServices.activateComponent(ProducerWithSubscribeAndDelegation.class);

		Assert.assertTrue(this.lifecycleServices.isActivated(ProducerWithSubscribeAndDelegation.class));
		producerServices.fireEvent("event");
		producerServices.fireEvent("hidden event");
		Assert.assertEquals(List.of("event", "event"), consumerServices.getEvents());
	}

	@Test
	public void testClone() {
		this.cm.deployComponents(ProducerWithCloneImpl.class);
		this.cm.start();
		ProducerWithCloneServices producerWithCloneServices = this.rcs.useServices(ProducerWithCloneServices.class);

		ProducerWithClonedEventsMock producerWithClonedEventsMock = new ProducerWithClonedEventsMock();
		ProducerWithClonedMethodEventsMock producerWithClonedMethodEventsMock = new ProducerWithClonedMethodEventsMock();
		ProducerWithClonedAttributeEventsMock producerWithClonedAttributeEventsMock = new ProducerWithClonedAttributeEventsMock();

		this.rcs.subscribe(ProducerWithClonedEvents.class, producerWithClonedEventsMock);
		this.rcs.subscribe(ProducerWithClonedMethodEvents.class, producerWithClonedMethodEventsMock);
		this.rcs.subscribe(ProducerWithClonedAttributeEvents.class, producerWithClonedAttributeEventsMock);

		producerWithCloneServices.fireProducerWithClonedEvents();

		Assert.assertTrue(producerWithClonedEventsMock.received);

		// 1) @Cloned on Events interface level:
		// ----------------------------------
		// cloneable attribute should be cloned and so, not be mutated
		Assert.assertFalse(producerWithCloneServices.isMutatedCloneableOnClonedEvents());
		// not cloneable attribute should be mutated
		Assert.assertTrue(producerWithCloneServices.isMutatedNotCloneableOnClonedEvents());

		// try again with strict dynamic verification rules
		this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		producerWithCloneServices.reset();
		ValidationException failure = Assert.assertThrows(ValidationException.class,
				() -> producerWithCloneServices.fireProducerWithClonedEvents());
		Assert.assertTrue(this.isExceptionIncludingText(failure, "arg1 is cloned but does not implement Cloneable"));
		this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.trace);

		producerWithCloneServices.fireProducerWithClonedMethodEvents();
		Assert.assertTrue(producerWithClonedMethodEventsMock.received);

		// 2) @Cloned on Event method level:
		// ------------------------------
		// cloneable attribute should be cloned and so, not be mutated
		Assert.assertFalse(producerWithCloneServices.isMutatedCloneableOnClonedMethodEvents());
		// not cloneable attribute should be mutated
		Assert.assertTrue(producerWithCloneServices.isMutatedNotCloneableOnClonedMethodEvents());

		// try again with strict dynamic verification rules
		this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		producerWithCloneServices.reset();
		ValidationException failure2 = Assert.assertThrows(ValidationException.class,
				() -> producerWithCloneServices.fireProducerWithClonedMethodEvents());
		Assert.assertTrue(this.isExceptionIncludingText(failure2, "arg1 is cloned but does not implement Cloneable"));
		this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.trace);

		producerWithCloneServices.fireProducerWithClonedAttributeEvents();
		Assert.assertTrue(producerWithClonedAttributeEventsMock.received);

		// 3) @Cloned on event attribute level:
		// ---------------------------------
		// cloneable attribute with @Cloned should not be mutated
		Assert.assertFalse(producerWithCloneServices.isMutatedCloneableOnClonedAttributeEvents());
		// not cloneable attribute with @Cloned should be mutated (it cannot be cloned)
		Assert.assertTrue(producerWithCloneServices.isMutatedNotCloneableOnClonedAttributeEvents());
		// cloneable attribute without @Cloned should be mutated (not cloned)
		Assert.assertTrue(producerWithCloneServices.isMutatedCloneableOnNotClonedAttributeEvents());
		// not cloneable attribute withou @Cloned should be mutated
		Assert.assertTrue(producerWithCloneServices.isMutatedNotCloneableOnNotClonedAttributeEvents());

		// try again with strict dynamic verification rules
		this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		producerWithCloneServices.reset();
		ValidationException failure3 = Assert.assertThrows(ValidationException.class,
				() -> producerWithCloneServices.fireProducerWithClonedAttributeEvents());
		Assert.assertTrue(this.isExceptionIncludingText(failure3, "arg1 is cloned but does not implement Cloneable"));
		this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.trace);

	}

	@Test
	public void testRawProducerClonesEventsAnnotatedAtInterfaceLevel() {
		// A raw (non-managed) events producer honors @Cloned: MetamodelManagerImpl.getEventsMetamodel
		// builds its EventsMetamodel for a raw producer with the same @Cloned
		// introspection that MetamodelFactory performs for a managed one. Reuses the
		// managed ProducerWithClonedEvents contract (@Events @Cloned at the interface
		// level) to check that a raw producer honors it exactly like a managed producer
		// does in testClone() above.
		this.cm.start();

		RawClonedEventsProducer rawProducer = new RawClonedEventsProducer();
		this.rcs.registerComponent(rawProducer);
		ProducerWithClonedEvents producedEvents = this.rcs.produceEvents(RawClonedEventsProducer.class,
				ProducerWithClonedEvents.class);

		ProducerWithClonedEventsMock consumer = new ProducerWithClonedEventsMock();
		this.rcs.subscribe(ProducerWithClonedEvents.class, consumer);

		MutableData cloneable = new MutableCloneableDataImpl("cloneable");
		MutableData notCloneable = new MutableNotCloneableDataImpl("cloneable");
		producedEvents.onEvents(cloneable, notCloneable);

		Assert.assertTrue(consumer.received);
		// The mock consumer mutates its own received copies (see
		// ProducerWithClonedEventsMock.onEvents above). The producer's own references
		// must NOT reflect that mutation for the cloneable argument - proving a clone,
		// not the original instance, was delivered - and must reflect it for the
		// argument that cannot be cloned, exactly like the managed case in testClone().
		Assert.assertFalse("a raw producer must clone a @Cloned, Cloneable argument like a managed producer does",
				cloneable.getState().equals("mutated"));
		Assert.assertTrue(notCloneable.getState().equals("mutated"));

		this.rcs.unregisterComponent(RawClonedEventsProducer.class);
	}

	private static class RawClonedEventsProducer {
	}

	@Test
	public void testCloneFailureIsReportedWithRealMessage() {
		this.cm.deployComponents(ProducerWithCloneImpl.class);
		this.cm.start();
		ProducerWithCloneServices producerWithCloneServices = this.rcs.useServices(ProducerWithCloneServices.class);

		// a Cloneable argument whose clone() method itself throws must be reported
		// with the real diagnostic message (target class and cause), not the
		// literal string "message"
		this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		ValidationException failure = Assert.assertThrows(ValidationException.class,
				() -> producerWithCloneServices.fireProducerWithClonedThrowingEvents());
		Assert.assertTrue(this.isExceptionIncludingText(failure, "While cloning a Cloneable instance of class"));
		Assert.assertTrue(this.isExceptionIncludingText(failure, "clone failure"));
	}

	@Test
	public void testCloneFailureIsReportedAsWarningWhenNotStrict() {
		this.cm.deployComponents(ProducerWithCloneImpl.class);
		this.cm.start();
		ProducerWithCloneServices producerWithCloneServices = this.rcs.useServices(ProducerWithCloneServices.class);

		// at any non-strict dynamic verification level, a Cloneable argument whose
		// clone() method itself throws must be reported through the ordinary dynamic
		// verification channel instead of raising a ValidationException, and the
		// event must still be delivered with the unmutated argument.
		this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.warn);
		CapturingLogHandler logHandler = CapturingLogHandler.install(
				org.opales.covalent.container.validation.impl.ComponentValidatorImpl.class);
		try {
			producerWithCloneServices.fireProducerWithClonedThrowingEvents();
			// Method.invoke() wraps the target's own IllegalStateException as an
			// InvocationTargetException, which is what replaceWithClones actually sees
			// and reports here - the underlying "clone failure" cause is preserved on
			// the exception chain but is not part of this particular log message.
			Assert.assertTrue(logHandler.containsWarning(
					"While cloning a Cloneable instance of class class org.opales.covalent.container.tests.unit.data.mutable.impl.MutableCloneableThrowingDataImpl, an exception was thrown: java.lang.reflect.InvocationTargetException"));
		} finally {
			logHandler.restore();
			this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.trace);
		}
	}

	@Test
	public void testCloneFailureWithNoAccessibleCloneMethodIsReportedAsWarning() {
		this.cm.deployComponents(ProducerWithCloneImpl.class);
		this.cm.start();
		ProducerWithCloneServices producerWithCloneServices = this.rcs.useServices(ProducerWithCloneServices.class);

		// a Cloneable argument with no public clone() method (Object.clone() is
		// protected) must be reported through the dynamic verification channel with
		// its own distinct message, not confused with a clone() method that is
		// reachable but fails when invoked (see testCloneFailureIsReportedAsWarningWhenNotStrict).
		this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.warn);
		CapturingLogHandler logHandler = CapturingLogHandler.install(
				org.opales.covalent.container.validation.impl.ComponentValidatorImpl.class);
		try {
			producerWithCloneServices.fireProducerWithClonedNoAccessibleCloneMethodEvents();
			Assert.assertTrue(logHandler.containsWarning(
					"The class class org.opales.covalent.container.tests.unit.data.mutable.impl.MutableCloneableWithoutPublicCloneDataImpl is Cloneable but does not implement the clone() method"));
		} finally {
			logHandler.restore();
			this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.trace);
		}
	}

	@Test
	public void testEventsFilter() {
		this.cm.deployComponents(ProducerWithSubscribeImpl.class, EventsConsumerWithFilterImpl.class);

		// A cast on the direct lcs() facade, not Covalent.main.rcs().accessParameters(...):
		// this runs before this.cm.start(), and only the direct facade works
		// without the container already running.
		LifecycleParameters lifecycleParameters = (LifecycleParameters) this.lifecycleServices;
		lifecycleParameters.setFindLifecycleMethodByNameConvention(true);
		this.cm.start();

		RegistryReadServices registryReadServices = this.rcs.useServices(RegistryReadServices.class);
		EventsConsumerWithFilterImpl eventsConsumerWithFilterImpl = registryReadServices
				.getComponentInstance(EventsConsumerWithFilter.class);

		ProducerServices producerServices = this.rcs.useServices(ProducerServices.class);

		eventsConsumerWithFilterImpl.subscribeNoFilter();
		producerServices.fireEvent("notFiltered");
		Assert.assertTrue(eventsConsumerWithFilterImpl.receivedEvent());
		eventsConsumerWithFilterImpl.unsubscribe();

		eventsConsumerWithFilterImpl.reset();

		eventsConsumerWithFilterImpl.subscribeWithFilter();
		producerServices.fireEvent("notFiltered");
		Assert.assertTrue(eventsConsumerWithFilterImpl.receivedEvent());

		eventsConsumerWithFilterImpl.reset();

		// this event should be filtered
		producerServices.fireEvent("filter");
		Assert.assertFalse(eventsConsumerWithFilterImpl.receivedEvent());

		eventsConsumerWithFilterImpl.unsubscribe();

	}

	@Test
	public void testGenericSubscribe() {
		this.cm.deployComponents(SimpleProducerImpl.class, ConsumerWithGenericSubscribeImpl.class);
		this.cm.start();

		ProducerServices producerServices = this.rcs.useServices(ProducerServices.class);
		ConsumerServices consumerServices = this.rcs.useServices(ConsumerServices.class);
		consumerServices.subscribe();
		producerServices.fireEvent("generic subscribe");
		Assert.assertTrue(consumerServices.checkEventReceived());
		Assert.assertEquals(1, consumerServices.getEventsCount());
	}

	private <EVENTS> void unsubscribeIfNeeded(final Class<?> consumerComponentType, final Class<EVENTS> eventsInterface,
			final EVENTS consumer) {
		EventsManagerServices eventsManagerServices = this.rcs.useServices(EventsManagerServices.class);
		if (eventsManagerServices.isSubscribed(eventsInterface, consumer)) {
			this.rcs.unsubscribe(consumerComponentType, eventsInterface, consumer);
		}
	}

	@Component
	public interface ThrowingSubscribeProducer {
	}

	@Services
	public interface ThrowingSubscribeProducerServices {
		void fire(String message);
	}

	@Implementation
	public static class ThrowingSubscribeProducerImpl implements ThrowingSubscribeProducer,
			ThrowingSubscribeProducerServices, ProducerEventsSubscribe {

		@ProducedEvents
		private ProducerWithSubscribeEvents produceProducerWithSubscribeEvents;

		@Override
		public void fire(final String message) {
			this.produceProducerWithSubscribeEvents.onEvent(message);
		}

		@Override
		public void subscribe(final ProducerWithSubscribeEvents observer) {
			throw new IllegalStateException("expected managed subscribe delegate failure");
		}

		@Override
		public void subscribe(final ProducerWithSubscribeEvents observer, final ProducerWithSubscribeEventsFilter filter) {
			throw new IllegalStateException("expected managed subscribe delegate failure");
		}

		@Override
		public void unsubscribe(final ProducerWithSubscribeEvents observer) {
		}
	}

	private static class RecordingProducerWithSubscribeEventsConsumer implements ProducerWithSubscribeEvents {
		private int receivedCount;

		@Override
		public void onEvent(final String message) {
			this.receivedCount++;
		}
	}

	@Implementation
	public static class ThrowingUnsubscribeProducerImpl implements ThrowingSubscribeProducer,
			ThrowingSubscribeProducerServices, ProducerEventsSubscribe {

		@ProducedEvents
		private ProducerWithSubscribeEvents produceProducerWithSubscribeEvents;

		@Override
		public void fire(final String message) {
			this.produceProducerWithSubscribeEvents.onEvent(message);
		}

		@Override
		public void subscribe(final ProducerWithSubscribeEvents observer) {
			// succeeds silently: this test targets the unsubscribe delegate failure
			// path, so the subscribe delegate must not itself deliver an event.
		}

		@Override
		public void subscribe(final ProducerWithSubscribeEvents observer, final ProducerWithSubscribeEventsFilter filter) {
			// not used
		}

		@Override
		public void unsubscribe(final ProducerWithSubscribeEvents observer) {
			throw new IllegalStateException("expected managed unsubscribe delegate failure");
		}
	}

	// A plain (non-deployed) raw producer: the subscribe delegate blocks on
	// releaseDelegate until released, letting a test deterministically
	// unsubscribe while the delegate is still in flight on the events worker
	// thread.
	private static class BlockingSubscribeDelegateProducer {
		final CountDownLatch delegateStarted = new CountDownLatch(1);

		final CountDownLatch releaseDelegate = new CountDownLatch(1);

		final ComponentAEvents produceComponentAEvents;

		BlockingSubscribeDelegateProducer(final RawComponentsServices rcs) {
			rcs.registerComponent(this);
			this.produceComponentAEvents = rcs.produceEvents(BlockingSubscribeDelegateProducer.class, ComponentAEvents.class,
					this::onSubscribe);
		}

		private void onSubscribe(final ComponentAEvents observer) {
			this.delegateStarted.countDown();
			try {
				this.releaseDelegate.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	// A raw producer that produces its events port exactly once, at
	// construction, and never again - unlike a managed component's
	// @Activation, which reruns on every start(). Used to probe whether its
	// captured eventWorker survives a container stop()/start() cycle.
	private static class RawAsyncEventsProducer {
		final ComponentAEvents produceComponentAEvents;

		RawAsyncEventsProducer(final RawComponentsServices rcs) {
			rcs.registerComponent(this);
			this.produceComponentAEvents = rcs.produceEvents(RawAsyncEventsProducer.class, ComponentAEvents.class);
		}
	}

	@Component
	interface EventsConsumerWithFilter {
	}

	@Implementation
	public static class EventsConsumerWithFilterImpl implements EventsConsumerWithFilter {
		@UsedEventsSubscribe
		private ProducerEventsSubscribe useProducerEventsSubscribe;

		private ConsumerMock consumerMock;

		private EventsFilter eventsFilter;

		public void activateComponent() {
			this.consumerMock = new ConsumerMock();
			this.eventsFilter = new EventsFilter();
		}

		public boolean receivedEvent() {
			return this.consumerMock.received;
		}

		public void subscribeWithFilter() {
			this.useProducerEventsSubscribe.subscribe(this.consumerMock, this.eventsFilter);
		}

		public void unsubscribe() {
			this.useProducerEventsSubscribe.unsubscribe(this.consumerMock);
		}

		public void reset() {
			this.consumerMock.reset();
		}

		public void subscribeNoFilter() {
			this.useProducerEventsSubscribe.subscribe(this.consumerMock);
		}

		public class ConsumerMock implements ProducerWithSubscribeEvents {
			boolean received;

			@Override
			public void onEvent(final String message) {
				this.received = true;
			}

			void reset() {
				this.received = false;
			}

		}

		public class EventsFilter implements ProducerWithSubscribeEventsFilter {
			@Override
			public boolean filterEvent(final String message) {
				if ("filter".equals(message)) {
					return true;
				}
				return false;
			}
		}

	}

	private static class ProducerWithClonedEventsMock implements ProducerWithClonedEvents {

		@Override
		public void onEvents(final MutableData cloneableData, final MutableData notCloneableData) {
			cloneableData.setState("mutated");
			notCloneableData.setState("mutated");
			this.received = true;
		}

		boolean received = false;
	}

	private static class ProducerWithClonedMethodEventsMock implements ProducerWithClonedMethodEvents {

		@Override
		public void onEvents(final MutableData cloneableData, final MutableData notCloneableData) {
			cloneableData.setState("mutated");
			notCloneableData.setState("mutated");
			this.received = true;
		}

		boolean received = false;
	}

	private static class ProducerWithClonedAttributeEventsMock implements ProducerWithClonedAttributeEvents {

		@Override
		public void onEvents(final MutableData cloneableData, final MutableData notCloneableData,
				final MutableData cloneableData2, final MutableData notCloneableData2) {
			cloneableData.setState("mutated");
			notCloneableData.setState("mutated");
			cloneableData2.setState("mutated");
			notCloneableData2.setState("mutated");
			this.received = true;
		}

		boolean received = false;
	}

	private static class ProducerWithDelegationEventsMock implements ProducerWithSubscribeEvents {

		@Override
		public void onEvent(final String message) {
			this.received = true;
			this.messages.add(message);
		}

		public List<String> messages = new ArrayList<>();

		boolean received = false;
	}
}
