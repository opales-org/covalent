/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Method;
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
import org.opales.covalent.container.rr.services.ServicesManagerEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.FullyConnectedServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.impl.FullyConnectedImpl;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithdelegation.impl.ProducerWithSubscribeAndDelegationImpl;

public class ServicesManagerTests extends CovalentTests {
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
	public void testServicesEvents() throws InterruptedException {
		// Internal (technical) components are only activated by the container's
		// own start() (outside the pre-first-start bootstrap window); subscribing
		// to a technical events interface below needs them activated.
		this.cm.start();

		Covalent.main.getParameters().setInstrumented(true);
		ServicesEventsConsumer servicesEventsConsumer = new ServicesEventsConsumer();
		this.rcs.subscribe(ServicesManagerEvents.class, servicesEventsConsumer);

		CountDownLatch latch = new CountDownLatch(3);
		servicesEventsConsumer.setLatch(latch);
		this.cm.deployComponents(ProducerWithSubscribeAndDelegationImpl.class, FullyConnectedImpl.class);
		// The container is already started (see above); activate the two
		// components just deployed instead of calling start() again.
		this.cm.lcs().activate();
		Assert.assertTrue("Timed out waiting for the expected events.", latch.await(5, TimeUnit.SECONDS));

		// provided: ProducerServices, FullyConnectedServices
		// used: ProducerServices
		Assert.assertTrue(servicesEventsConsumer.servicesProvided);
		Assert.assertEquals(2, servicesEventsConsumer.providedCount);
		Assert.assertEquals(1, servicesEventsConsumer.usedCount);
		Assert.assertTrue(servicesEventsConsumer.servicesUsed);
		Assert.assertEquals(FullyConnectedServices.class, servicesEventsConsumer.providedServices);
		Assert.assertEquals(ProducerServices.class, servicesEventsConsumer.usedServices);
		servicesEventsConsumer.reset();

		latch = new CountDownLatch(1);
		servicesEventsConsumer.setLatch(latch);
		FullyConnectedServices fullyConnectedServices = this.rcs.useServices(FullyConnectedServices.class);
		boolean service = fullyConnectedServices.checkProvidedServices();
		Assert.assertTrue("Timed out waiting for the expected events.", latch.await(5, TimeUnit.SECONDS));
		// used: FullyConnectedServices
		Assert.assertEquals(1, servicesEventsConsumer.usedCount);
		Assert.assertTrue(service);
		Assert.assertTrue(servicesEventsConsumer.usedServiceCallStarted);
		Assert.assertTrue(servicesEventsConsumer.usedServiceCallFinished);
		Assert.assertTrue(servicesEventsConsumer.providedServiceCallStarted);
		Assert.assertTrue(servicesEventsConsumer.providedServiceCallFinished);
		Assert.assertEquals(FullyConnectedServices.class, servicesEventsConsumer.calledServices);
		Assert.assertTrue(servicesEventsConsumer.durationNanos >= 0);
		Assert.assertNull(servicesEventsConsumer.failure);

		servicesEventsConsumer.reset();
		latch = new CountDownLatch(3);
		servicesEventsConsumer.setLatch(latch);
		this.cm.stop();
		Assert.assertTrue("Timed out waiting for the expected events.", latch.await(5, TimeUnit.SECONDS));
		// unprovided: FullyConnectedServices, ProducerServices
		// unused: ProducerServices, FullyConnectedServices
		Assert.assertFalse(servicesEventsConsumer.servicesProvided);
		Assert.assertFalse(servicesEventsConsumer.servicesUsed);
		Assert.assertTrue(servicesEventsConsumer.servicesUnprovided);
		Assert.assertTrue(servicesEventsConsumer.servicesUnused);
		Assert.assertEquals(2, servicesEventsConsumer.unprovidedCount);
		Assert.assertEquals(2, servicesEventsConsumer.unusedCount);
		Assert.assertNull(servicesEventsConsumer.providedServices);
		Assert.assertNull(servicesEventsConsumer.usedServices);

		Covalent.main.getParameters().setInstrumented(false);
		// This test exercises stop() itself; restart so tearDown()'s own,
		// unconditional stop() below does not run on an already-stopped
		// container.
		this.cm.start();
	}

	class ServicesEventsConsumer implements ServicesManagerEvents {
		CountDownLatch latch;

		void setLatch(final CountDownLatch latch) {
			this.latch = latch;
		}

		public void reset() {
			this.servicesProvided = false;
			this.servicesUnprovided = false;
			this.servicesUsed = false;
			this.servicesUnused = false;
			this.providedServices = null;
			this.usedServices = null;
			this.calledServices = null;
			this.providedCount = 0;
			this.unprovidedCount = 0;
			this.usedCount = 0;
			this.unusedCount = 0;
			this.usedServiceCallStarted = false;
			this.usedServiceCallFinished = false;
			this.providedServiceCallStarted = false;
			this.providedServiceCallFinished = false;
			this.servicesUser = null;
			this.servicesProvider = null;
			this.durationNanos = -1;
			this.failure = null;
		}

		boolean servicesProvided;
		boolean servicesUnprovided;
		boolean servicesUsed;
		boolean servicesUnused;
		boolean usedServiceCallStarted;
		boolean usedServiceCallFinished;
		boolean providedServiceCallStarted;
		boolean providedServiceCallFinished;

		Class<?> providedServices;
		Class<?> usedServices;
		Class<?> calledServices;
		Class<?> servicesUser;
		Class<?> servicesProvider;
		Throwable failure;
		long durationNanos = -1;

		int providedCount = 0;
		int unprovidedCount = 0;
		int usedCount = 0;
		int unusedCount = 0;

		@Override
		public <CT, SERVICES> void onServicesProvided(final Class<CT> componentType,
				final Class<SERVICES> servicesInterface) {
			this.servicesProvided = true;
			this.providedServices = servicesInterface;
			this.providedCount++;
			if (this.latch != null) {
				this.latch.countDown();
			}
		}

		@Override
		public <CT, SERVICES> void onServicesUnprovided(final Class<CT> componentType,
				final Class<SERVICES> servicesInterface) {
			this.servicesUnprovided = true;
			this.unprovidedCount++;
			if (this.latch != null) {
				this.latch.countDown();
			}
		}

		@Override
		public <CT, SERVICES> void onServicesUsed(final Class<CT> servicesUser,
				final Class<SERVICES> servicesInterface) {
			this.servicesUsed = true;
			this.usedServices = servicesInterface;
			this.usedCount++;
			if (this.latch != null) {
				this.latch.countDown();
			}
		}

		@Override
		public <CT, SERVICES> void onServicesUnused(final Class<CT> servicesUser,
				final Class<SERVICES> servicesInterface) {
			this.servicesUnused = true;
			this.unusedCount++;
			if (this.latch != null) {
				this.latch.countDown();
			}
		}

		@Override
		public <SERVICES> void onUsedServiceCallStarted(final Class<SERVICES> servicesInterface, final Method method,
				final Class<?> servicesUser, final Object... inputData) {
			this.usedServiceCallStarted = true;
			this.calledServices = servicesInterface;
			this.servicesUser = servicesUser;
			if (this.latch != null) {
				this.latch.countDown();
			}
		}

		@Override
		public <SERVICES> void onUsedServiceCallFinished(final Class<SERVICES> servicesInterface, final Method method,
				final Class<?> servicesUser, final long durationNanos,
				final Throwable failure, final Object returnValue, final Object... inputData) {
			this.usedServiceCallFinished = true;
			this.calledServices = servicesInterface;
			this.servicesUser = servicesUser;
			this.durationNanos = durationNanos;
			this.failure = failure;
			if (this.latch != null) {
				this.latch.countDown();
			}
		}

		@Override
		public <SERVICES> void onProvidedServiceCallStarted(final Class<SERVICES> servicesInterface,
				final Method method, final Class<?> servicesProvider, final Object... inputData) {
			this.providedServiceCallStarted = true;
			this.calledServices = servicesInterface;
			this.servicesProvider = servicesProvider;
			if (this.latch != null) {
				this.latch.countDown();
			}
		}

		@Override
		public <SERVICES> void onProvidedServiceCallFinished(final Class<SERVICES> servicesInterface,
				final Method method, final Class<?> servicesProvider, final long durationNanos,
				final Throwable failure, final Object returnValue, final Object... inputData) {
			this.providedServiceCallFinished = true;
			this.calledServices = servicesInterface;
			this.servicesProvider = servicesProvider;
			this.durationNanos = durationNanos;
			this.failure = failure;
			if (this.latch != null) {
				this.latch.countDown();
			}
		}

	}

}
