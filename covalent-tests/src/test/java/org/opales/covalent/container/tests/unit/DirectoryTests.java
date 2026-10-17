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
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.directory.NamingEvents;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.CovalentTests;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerParameters;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.simpleproducer.impl.SimpleProducerImpl;

public class DirectoryTests extends CovalentTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

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
	public void testNamingEvents() throws InterruptedException {
		// Internal (technical) components are only activated by the container's
		// own start() (outside the pre-first-start bootstrap window); subscribing
		// to a technical events interface below needs them activated.
		this.cm.start();

		NamingEventsConsumer namingEventsConsumer = new NamingEventsConsumer();
		this.rcs.subscribe(NamingEvents.class, namingEventsConsumer);

		CountDownLatch latch = new CountDownLatch(3);
		namingEventsConsumer.setLatch(latch);
		this.cm.deployComponents(SimpleProducerImpl.class);
		// The container is already started (see above); activate the
		// component just deployed instead of calling start() again.
		this.cm.lcs().activate();
		Assert.assertTrue("Timed out waiting for the expected events.", latch.await(5, TimeUnit.SECONDS));

		Assert.assertTrue(namingEventsConsumer.servicesBound);
		Assert.assertTrue(namingEventsConsumer.subscriberBound);
		Assert.assertTrue(namingEventsConsumer.parametersBound);
		Assert.assertEquals(ProducerEvents.class, namingEventsConsumer.boundEventsForSubscribe);
		Assert.assertEquals(ProducerServices.class, namingEventsConsumer.boundServices);
		Assert.assertEquals(ProducerParameters.class, namingEventsConsumer.boundParameters);

		latch = new CountDownLatch(3);
		namingEventsConsumer.setLatch(latch);
		this.cm.stop();
		Assert.assertTrue("Timed out waiting for the expected events.", latch.await(5, TimeUnit.SECONDS));
		Assert.assertTrue(namingEventsConsumer.servicesUnbound);
		Assert.assertTrue(namingEventsConsumer.subscriberUnbound);
		Assert.assertTrue(namingEventsConsumer.parametersUnbound);
		Assert.assertEquals(ProducerServices.class, namingEventsConsumer.unboundServices);
		Assert.assertEquals(ProducerEvents.class, namingEventsConsumer.unboundEventsForSubscribe);
		Assert.assertEquals(ProducerParameters.class, namingEventsConsumer.unboundParameters);

		// This test exercises stop() itself; restart so tearDown()'s own,
		// unconditional stop() below does not run on an already-stopped
		// container.
		this.cm.start();
	}

	class NamingEventsConsumer implements NamingEvents {
		CountDownLatch latch;

		void setLatch(final CountDownLatch latch) {
			this.latch = latch;
		}

		@Override
		public <SERVICES> void onServicesBound(final Class<SERVICES> servicesInterface) {
			this.servicesBound = true;
			this.boundServices = servicesInterface;
			this.latch.countDown();
		}

		@Override
		public <SERVICES> void onServicesUnbound(final Class<SERVICES> servicesInterface) {
			this.servicesUnbound = true;
			this.unboundServices = servicesInterface;
			this.boundServices = null;
			this.latch.countDown();
		}

		@Override
		public <EVENTS> void onSubscriberBound(final Class<EVENTS> eventsInterface) {
			this.subscriberBound = true;
			this.boundEventsForSubscribe = eventsInterface;
			this.latch.countDown();
		}

		@Override
		public <EVENTS> void onSubscriberUnbound(final Class<EVENTS> eventsInterface) {
			this.subscriberUnbound = true;
			this.unboundEventsForSubscribe = eventsInterface;
			this.boundEventsForSubscribe = null;
			this.latch.countDown();
		}

		public void reset() {
			this.servicesBound = false;
			this.servicesUnbound = false;
			this.subscriberBound = false;
			this.subscriberUnbound = false;
			this.boundServices = null;
			this.boundEventsForSubscribe = null;
			this.unboundServices = null;
			this.unboundEventsForSubscribe = null;
			this.parametersBound = false;
			this.parametersUnbound = false;
			this.boundParameters = null;
			this.unboundParameters = null;
		}

		boolean servicesBound;
		boolean servicesUnbound;
		boolean subscriberBound;
		boolean subscriberUnbound;
		boolean parametersBound;
		boolean parametersUnbound;

		Class<?> boundServices;
		Class<?> boundEventsForSubscribe;
		Class<?> boundParameters;
		Class<?> unboundServices;
		Class<?> unboundEventsForSubscribe;
		Class<?> unboundParameters;

		@Override
		public <PARAMETERS> void onParametersBound(final Class<PARAMETERS> parametersInterface) {
			this.parametersBound = true;
			this.boundParameters = parametersInterface;
			this.latch.countDown();
		}

		@Override
		public <PARAMETERS> void onParametersUnbound(final Class<PARAMETERS> parametersInterface) {
			this.parametersUnbound = true;
			this.unboundParameters = parametersInterface;
			this.boundParameters = null;
			this.latch.countDown();
		}

	}
}
