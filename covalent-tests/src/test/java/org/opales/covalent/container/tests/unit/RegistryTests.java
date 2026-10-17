/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.tests.CovalentTests;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.registry.DescriptorRegistryReadServices;
import org.opales.covalent.container.registry.PortRegistryServices;
import org.opales.covalent.container.registry.RegistryEvents;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.registry.ComponentDescriptor;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.simpleproducer.SimpleProducer;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.simpleproducer.impl.SimpleProducerImpl;

public class RegistryTests extends CovalentTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	@Before
	public void setUp() {
		this.cm.getParameters().setSynchronousEvents(true);
	}

	@After
	public void tearDown() {
		this.cm.stop();
	}

	@Test
	public void undeployRemovesRegistryInstanceAndPortDescriptor() {
		this.cm.deployComponents(SimpleProducerImpl.class);
		this.cm.start();
		this.rcs.registerComponent(this);

		// Technical services (used below to read the registry) are only usable
		// once the container is started.
		RegistryReadServices registryReadServices = this.rcs.useServices(RegistryReadServices.class);
		PortRegistryServices portRegistryServices = this.rcs.useServices(PortRegistryServices.class);

		Assert.assertTrue(registryReadServices.isRegistered(SimpleProducer.class));
		Assert.assertNotNull(portRegistryServices.getComponentDescriptor(SimpleProducer.class));

		Covalent.main.lcs().undeployComponent(SimpleProducerImpl.class);

		Assert.assertFalse(registryReadServices.isRegistered(SimpleProducer.class));
		Assert.assertNull(portRegistryServices.getComponentDescriptor(SimpleProducer.class));
	}

	@Test
	public void testRegistryEvents() throws InterruptedException {
		// Start first (empty): subscribing to a technical events interface needs
		// internal components activated, and we want to catch the registration
		// event as SimpleProducer is deployed/activated below, not miss it by
		// subscribing afterward.
		this.cm.start();
		RegistryEventsConsumer registryEventsConsumer = new RegistryEventsConsumer();
		// registered before SimpleProducer is deployed, so that the stop cascade,
		// which tears down in reverse order, removes this observer last
		this.rcs.registerComponent(RegistryEventsConsumer.class, registryEventsConsumer);
		this.rcs.subscribe(RegistryEventsConsumer.class, RegistryEvents.class, registryEventsConsumer);

		CountDownLatch latch = new CountDownLatch(1);
		registryEventsConsumer.setLatch(latch);
		this.cm.deployComponents(SimpleProducerImpl.class);
		this.cm.lcs().activate();
		Assert.assertTrue("Timed out waiting for the expected events.", latch.await(5, TimeUnit.SECONDS));

		Assert.assertTrue(registryEventsConsumer.registered);
		Assert.assertNotNull(registryEventsConsumer.registeredInstance);
		Assert.assertEquals(SimpleProducerImpl.class, registryEventsConsumer.registeredInstance.getClass());

		registryEventsConsumer.reset();

		latch = new CountDownLatch(1);
		registryEventsConsumer.setLatch(latch);
		this.cm.stop();
		Assert.assertTrue("Timed out waiting for the expected events.", latch.await(5, TimeUnit.SECONDS));
		Assert.assertTrue(registryEventsConsumer.unregistered);
		Assert.assertNotNull(registryEventsConsumer.unregisteredInstance);
		Assert.assertEquals(SimpleProducerImpl.class, registryEventsConsumer.unregisteredInstance.getClass());
		// This test exercises stop() itself; restart the container so tearDown()'s
		// own, unconditional stop() below does not run on an already-stopped one.
		this.cm.start();
	}

	@Test
	public void registryRegistrationEventSeesCompleteDescriptor() {
		// Same reasoning as testRegistryEvents(): start first, then subscribe,
		// then deploy/activate the component whose registration we want to catch.
		this.cm.start();
		CompleteDescriptorRegistryEventsConsumer registryEventsConsumer = new CompleteDescriptorRegistryEventsConsumer();
		this.rcs.registerComponent(CompleteDescriptorRegistryEventsConsumer.class, registryEventsConsumer);
		this.rcs.subscribe(CompleteDescriptorRegistryEventsConsumer.class, RegistryEvents.class,
				registryEventsConsumer);

		this.cm.deployComponents(SimpleProducerImpl.class);
		this.cm.lcs().activate();

		Assert.assertTrue(registryEventsConsumer.registered);
		Assert.assertTrue(registryEventsConsumer.completeDescriptorObserved);
	}

	class RegistryEventsConsumer implements RegistryEvents {
		CountDownLatch latch;

		void setLatch(final CountDownLatch latch) {
			this.latch = latch;
		}

		public void reset() {
			this.registered = false;
			this.unregistered = false;
			this.registeredInstance = null;
			this.unregisteredInstance = null;
		}

		boolean registered;
		boolean unregistered;

		Object registeredInstance;
		Object unregisteredInstance;

		@Override
		public <CT> void onComponentRegistered(final Class<CT> componentType, final Object componentInstance) {
			this.registered = true;
			this.registeredInstance = componentInstance;
			this.latch.countDown();
		}

		@Override
		public <CT> void onComponentUnregistered(final Class<CT> componentType, final Object componentInstance) {
			this.unregistered = true;
			this.unregisteredInstance = componentInstance;
			this.latch.countDown();
		}

	}

	class CompleteDescriptorRegistryEventsConsumer implements RegistryEvents {
		boolean registered;
		boolean completeDescriptorObserved;

		@Override
		public <CT> void onComponentRegistered(final Class<CT> componentType, final Object componentInstance) {
			if (!SimpleProducer.class.equals(componentType)) {
				return;
			}
			this.registered = true;
			DescriptorRegistryReadServices descriptorRegistryServices = RegistryTests.this.rcs
					.useServices(DescriptorRegistryReadServices.class);
			ComponentDescriptor descriptor = descriptorRegistryServices.readComponentDescriptor(SimpleProducer.class);
			this.completeDescriptorObserved = descriptor != null
					&& descriptor.getServicesProvider(ProducerServices.class) == componentInstance;
		}

		@Override
		public <CT> void onComponentUnregistered(final Class<CT> componentType, final Object componentInstance) {
		}
	}

}
