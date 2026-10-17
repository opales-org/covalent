/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.lifecycle.LifecycleEvents;
import org.opales.covalent.container.lifecycle.LifecycleParameters;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.lifecycle.LifecycleServices.StartMode;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.tests.CovalentTests;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.ConsumerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.simpleconsumerofeventswithsubscriber.SimpleConsumerOfEventsWithSubscriber;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.simpleconsumerofeventswithsubscriber.impl.SimpleConsumerOfEventsWithSubscriberImpl;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumercomponent.ConsumerComponent;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumercomponent.ConsumerComponentServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumercomponent.impl.ConsumerComponentImpl;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithdelegation.ProducerWithSubscribeAndDelegation;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithdelegation.impl.ProducerWithSubscribeAndDelegationImpl;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.simpleproducer.SimpleProducer;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.simpleproducer.impl.SimpleProducerImpl;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent.ProducerComponent;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent.ProducerComponentServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent.impl.ProducerComponentImpl;

public class LifecycleTests extends CovalentTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	// Direct container facades, not raw useServices()/accessParameters()
	// proxies: they do not require the container to already be running, unlike
	// the generic lookups, which several tests below call before
	// this.cm.start().
	private final LifecycleServices lifecycleServices = Covalent.main.lcs();

	private final LifecycleParameters lifecycleParameters = (LifecycleParameters) Covalent.main.lcs();

	@Before
	public void setUp() {
		this.rcs.registerComponent(this);
		this.cm.getParameters().setSynchronousEvents(true);
	}

	@After
	public void tearDown() {
		this.cm.stop();
	}

	@Test
	public void testLifecycleMethodByNameConvention() {
		this.lifecycleParameters.setFindLifecycleMethodByNameConvention(true);
		this.cm.deployComponents(TestComponentWithNamingConventionLifecycleMethodImpl.class);
		this.cm.start();

		RegistryReadServices registryReadServices = this.rcs.useServices(RegistryReadServices.class);
		TestComponentWithNamingConventionLifecycleMethodImpl testComponentWithNamingConventionLifecycleMethodImpl = registryReadServices
				.getComponentInstance(TestComponentWithNamingConventionLifecycleMethod.class);
		this.lifecycleServices.passivateComponent(TestComponentWithNamingConventionLifecycleMethod.class);
		this.lifecycleServices.removeComponent(TestComponentWithNamingConventionLifecycleMethod.class);
		this.lifecycleParameters.setFindLifecycleMethodByNameConvention(false);

		Assert.assertTrue(testComponentWithNamingConventionLifecycleMethodImpl.initialized);
		Assert.assertTrue(testComponentWithNamingConventionLifecycleMethodImpl.activated);
		Assert.assertTrue(testComponentWithNamingConventionLifecycleMethodImpl.passivated);
		Assert.assertTrue(testComponentWithNamingConventionLifecycleMethodImpl.removed);
	}

	@Test
	public void lifecycleEventsAreNotifiedForEachTransition() throws InterruptedException {
		this.cm.start();
		LifecycleServices lifecycleServices = Covalent.main.rcs().useServices(LifecycleServices.class);

		LifecycleEventsConsumer lifecycleEventsConsumer = new LifecycleEventsConsumer();
		this.rcs.subscribe(LifecycleEvents.class, lifecycleEventsConsumer);

		this.cm.deployComponents(SimpleProducerImpl.class);
		Assert.assertFalse(lifecycleEventsConsumer.instantiated);

		lifecycleEventsConsumer
				.awaitEventDuring(() -> lifecycleServices.instantiateComponent(SimpleProducer.class));
		Assert.assertTrue(lifecycleEventsConsumer.instantiated);
		Assert.assertFalse(lifecycleEventsConsumer.initialized);

		lifecycleEventsConsumer
				.awaitEventDuring(() -> lifecycleServices.initializeComponent(SimpleProducer.class));
		Assert.assertFalse(lifecycleEventsConsumer.instantiated);
		Assert.assertTrue(lifecycleEventsConsumer.initialized);
		Assert.assertFalse(lifecycleEventsConsumer.activated);

		lifecycleEventsConsumer.awaitEventDuring(() -> lifecycleServices.activateComponent(SimpleProducer.class));
		Assert.assertFalse(lifecycleEventsConsumer.initialized);
		Assert.assertTrue(lifecycleEventsConsumer.activated);
		Assert.assertFalse(lifecycleEventsConsumer.passivated);

		lifecycleEventsConsumer.awaitEventDuring(() -> lifecycleServices.passivateComponent(SimpleProducer.class));
		Assert.assertFalse(lifecycleEventsConsumer.activated);
		Assert.assertTrue(lifecycleEventsConsumer.passivated);
		Assert.assertFalse(lifecycleEventsConsumer.removed);

		lifecycleEventsConsumer.awaitEventDuring(() -> lifecycleServices.removeComponent(SimpleProducer.class));
		Assert.assertFalse(lifecycleEventsConsumer.passivated);
		Assert.assertTrue(lifecycleEventsConsumer.removed);

		Assert.assertNull("Unexpected lifecycle failure notified.", lifecycleEventsConsumer.failure);
	}

	@Test
	public void testHorizontalStartMode() {
		this.lifecycleParameters.setStartMode(StartMode.horizontal);
		this.lifecycleServices.deployComponents(ProducerComponentImpl.class, ConsumerComponentImpl.class);
		this.cm.start();

		final ConsumerComponentServices consumerComponentServices = this.rcs.useServices(ConsumerComponent.class,
				ConsumerComponentServices.class);
		final ProducerComponentServices producerComponentServices = this.rcs.useServices(ProducerComponent.class,
				ProducerComponentServices.class);
		producerComponentServices.produceEvents();
		producerComponentServices.service();

		final List<String> itemAMessages = consumerComponentServices.getEventsReceivedByFirstItemA();
		// first item subscribed twice, once for each ID, and with a lambda observer
		// (hence the trailing "$")
		Assert.assertEquals(4, itemAMessages.size());
		Assert.assertEquals("subscribe_idA_ConsumerItemA$", itemAMessages.get(0));
		Assert.assertEquals("subscribe_idB_ConsumerItemA$", itemAMessages.get(1));
		Assert.assertEquals("idA", itemAMessages.get(2));
		Assert.assertEquals("idB", itemAMessages.get(3));

		final List<String> itemA2Messages = consumerComponentServices.getEventsReceivedBySecondItemA();
		// this item did not subscribe to any event
		Assert.assertEquals(0, itemA2Messages.size());

		final List<String> itemBObserverMessages = consumerComponentServices.getEventsReceivedByItemBObserver();
		Assert.assertEquals(2, itemBObserverMessages.size());
		Assert.assertEquals("subscribe_idB_ConsumerItemB$", itemBObserverMessages.get(0));
		Assert.assertEquals("idB", itemBObserverMessages.get(1));

		final List<String> itemBMessages = consumerComponentServices.getEventsReceivedByItemB();
		Assert.assertEquals(4, itemBMessages.size());
		Assert.assertEquals("subscribe_ConsumerItemB", itemBMessages.get(0));
		Assert.assertEquals("subscribe_ConsumerItemB#Global", itemBMessages.get(1));
		Assert.assertEquals("produceEvents", itemBMessages.get(2));
		Assert.assertEquals("service", itemBMessages.get(3));

		final List<String> implMessages = consumerComponentServices.getEventsReceivedByImpl();
		Assert.assertEquals(5, implMessages.size());
		Assert.assertEquals("subscribe_ConsumerComponentImpl$", implMessages.get(0));
		Assert.assertEquals("subscribe_ConsumerComponentImpl$#Global", implMessages.get(1));
		Assert.assertEquals("subscribe_ConsumerItemB#Global", implMessages.get(2));
		Assert.assertEquals("produceEvents", implMessages.get(3));
		Assert.assertEquals("service", implMessages.get(4));

		this.lifecycleParameters.setStartMode(StartMode.vertical);
	}

	@Test
	public void testHorizontalStartModeWithDependencies() {
		this.lifecycleParameters.setStartMode(StartMode.horizontal);
		this.lifecycleServices.deployComponents(ProducerWithSubscribeAndDelegationImpl.class,
				SimpleConsumerOfEventsWithSubscriberImpl.class);
		this.cm.start();
		ProducerServices producerServices = this.rcs.useServices(ProducerWithSubscribeAndDelegation.class,
				ProducerServices.class);

		ConsumerServices simpleConsumerServices = this.rcs.useServices(SimpleConsumerOfEventsWithSubscriber.class,
				ConsumerServices.class);

		producerServices.fireEvent("event");
		Assert.assertTrue(simpleConsumerServices.checkEventReceived());
		this.lifecycleParameters.setStartMode(StartMode.vertical);
	}

	class LifecycleEventsConsumer implements LifecycleEvents {
		CountDownLatch latch;

		volatile String failure;

		/**
		 * Resets the observed flags, runs the transition and waits for the event notified for it.
		 */
		void awaitEventDuring(final Runnable transition) throws InterruptedException {
			this.reset();
			this.latch = new CountDownLatch(1);
			transition.run();
			Assert.assertTrue("No lifecycle event received.", this.latch.await(5, TimeUnit.SECONDS));
		}

		@Override
		public void onComponentInstantiated(final Class<?> componentType) {
			this.instantiated = true;
			this.latch.countDown();
		}

		@Override
		public void onComponentInitialized(final Class<?> componentType) {
			this.initialized = true;
			this.latch.countDown();
		}

		@Override
		public void onComponentActivated(final Class<?> componentType) {
			this.activated = true;
			this.latch.countDown();
		}

		@Override
		public void onComponentPassivated(final Class<?> componentType) {
			this.passivated = true;
			this.latch.countDown();
		}

		@Override
		public void onComponentRemoved(final Class<?> componentType) {
			this.removed = true;
			this.latch.countDown();
		}

		@Override
		public void onComponentFailed(final Class<?> componentType) {
			this.failure = "Unexpected lifecycle failure: " + componentType;
		}

		void reset() {
			this.instantiated = false;
			this.initialized = false;
			this.activated = false;
			this.passivated = false;
			this.removed = false;
		}

		boolean instantiated;
		boolean initialized;
		boolean activated;
		boolean passivated;
		boolean removed;
	}

	@Component
	interface TestComponentWithNamingConventionLifecycleMethod {
	}

	@Implementation
	public static class TestComponentWithNamingConventionLifecycleMethodImpl
			implements TestComponentWithNamingConventionLifecycleMethod {
		public void initializeComponent() {
			this.initialized = true;
		}

		public void activateComponent() {
			this.activated = true;
		}

		public void passivateComponent() {
			this.passivated = true;
		}

		public void removeComponent() {
			this.removed = true;
		}

		boolean initialized;
		boolean activated;
		boolean passivated;
		boolean removed;
	}
}
