/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.tests.CovalentContainerizedTests;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.exceptions.ValidationException;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.rr.services.ServicesManagerEvents;
import org.opales.covalent.container.rr.services.ServicesManagerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerParameters;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.ComponentAServices;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.impl.ComponentA;

public class ServicesManagerContainerizedTests extends CovalentContainerizedTests {
	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	@Before
	public void setUp() {
		this.rcs.registerComponent(this);
		this.cm.getParameters().setSynchronousEvents(true);
	}

	@Test
	public void duplicateProvidedServicesFailsWithoutReplacingProvider() {
		ComponentA provider = new ComponentA();
		this.rcs.registerComponent(provider);

		try {
			this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.strict);

			ValidationException exception = Assert.assertThrows(ValidationException.class,
					() -> this.rcs.provideServices(ComponentA.class, ComponentAServices.class));
			Assert.assertTrue(exception.getMessage().contains("already provided"));

			this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.trace);
			ComponentAServices services = this.rcs.useServices(ComponentA.class, ComponentAServices.class);
			Assert.assertEquals("A data", services.getData());
			this.rcs.unuseServices(ComponentAServices.class);
		} finally {
			this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.trace);
			this.rcs.unregisterComponent(ComponentA.class);
		}
	}

	@Test
	public void usedServicesRegistrationIsRemovedAfterUnuse() {
		ComponentA provider = new ComponentA();
		this.rcs.registerComponent(provider);

		try {
			ServicesManagerServices servicesManagerServices = this.rcs.useServices(ServicesManagerServices.class);
			ComponentAServices services = this.rcs.useServices(ComponentA.class, ComponentAServices.class);
			Assert.assertEquals("A data", services.getData());
			Assert.assertTrue(servicesManagerServices.getServiceToUsers().get(ComponentAServices.class)
					.contains(ServicesManagerContainerizedTests.class));

			this.rcs.unuseServices(ComponentAServices.class);

			Assert.assertNull(servicesManagerServices.getServiceToUsers().get(ComponentAServices.class));
		} finally {
			this.rcs.unregisterComponent(ComponentA.class);
		}
	}

	@Test
	public void usedServicesProxyIsInvalidatedAfterUnuse() {
		ComponentA provider = new ComponentA();
		this.rcs.registerComponent(provider);

		try {
			ComponentAServices services = this.rcs.useServices(ComponentA.class, ComponentAServices.class);
			Assert.assertEquals("A data", services.getData());

			this.rcs.unuseServices(ComponentAServices.class);

			Assert.assertThrows(IllegalStateException.class, services::getData);
		} finally {
			this.rcs.unregisterComponent(ComponentA.class);
		}
	}

	@Test
	public void duplicateUsedServicesFailsWithoutReplacingOriginalProxy() {
		ComponentA provider = new ComponentA();
		this.rcs.registerComponent(provider);

		try {
			ComponentAServices services = this.rcs.useServices(ComponentA.class, ComponentAServices.class);
			try {
				this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.strict);

				ValidationException exception = Assert.assertThrows(ValidationException.class,
						() -> this.rcs.useServices(ComponentA.class, ComponentAServices.class));
				Assert.assertTrue(exception.getMessage().contains("already used"));

				this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.trace);
				services.doSomething();
				Assert.assertTrue(provider.doSomething);
			} finally {
				this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.trace);
				this.rcs.unuseServices(ComponentAServices.class);
			}
		} finally {
			this.rcs.unregisterComponent(ComponentA.class);
		}
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void usedServicesRejectParametersInterface() {
		ServicesManagerServices servicesManagerServices = this.rcs.useServices(ServicesManagerServices.class);

		IllegalArgumentException exception = Assert.assertThrows(IllegalArgumentException.class,
				() -> servicesManagerServices.createUsedServices(ServicesManagerContainerizedTests.class,
						(Class) ProducerParameters.class));

		Assert.assertTrue(exception.getMessage().contains("parameters interface"));
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void usedServicesRejectEventsInterface() {
		ServicesManagerServices servicesManagerServices = this.rcs.useServices(ServicesManagerServices.class);

		IllegalArgumentException exception = Assert.assertThrows(IllegalArgumentException.class,
				() -> servicesManagerServices.createUsedServices(ServicesManagerContainerizedTests.class, (Class) ProducerEvents.class));

		Assert.assertTrue(exception.getMessage().contains("events interface"));
	}

	@Test
	public void serviceInstrumentationReportsFailuresAndDuration() throws InterruptedException {
		Covalent.main.getParameters().setInstrumented(true);
		this.cm.getParameters().setSynchronousEvents(true);

		ServicesEventsConsumer servicesEventsConsumer = new ServicesEventsConsumer();
		this.rcs.subscribe(ServicesManagerEvents.class, servicesEventsConsumer);
		FailingRawServicesProvider provider = new FailingRawServicesProvider();
		this.rcs.registerComponent(provider);
		try {
			this.rcs.provideServices(FailingRawServicesProvider.class, FailingRawServices.class);
			FailingRawServices services = this.rcs.useServices(ServicesManagerContainerizedTests.class, FailingRawServices.class);

			CountDownLatch latch = new CountDownLatch(4);
			servicesEventsConsumer.reset();
			servicesEventsConsumer.setLatch(latch);
			IllegalStateException failure = Assert.assertThrows(IllegalStateException.class, services::fail);
			Assert.assertTrue("Timed out waiting for the expected events.", latch.await(5, TimeUnit.SECONDS));

			Assert.assertTrue(servicesEventsConsumer.usedServiceCallStarted);
			Assert.assertTrue(servicesEventsConsumer.usedServiceCallFinished);
			Assert.assertTrue(servicesEventsConsumer.providedServiceCallStarted);
			Assert.assertTrue(servicesEventsConsumer.providedServiceCallFinished);
			Assert.assertEquals(FailingRawServices.class, servicesEventsConsumer.calledServices);
			Assert.assertEquals(ServicesManagerContainerizedTests.class, servicesEventsConsumer.servicesUser);
			Assert.assertEquals(FailingRawServicesProvider.class, servicesEventsConsumer.servicesProvider);
			Assert.assertSame(failure, servicesEventsConsumer.failure);
			Assert.assertTrue(servicesEventsConsumer.durationNanos >= 0);
		} finally {
			this.rcs.unuseServices(FailingRawServices.class);
			this.rcs.unprovideServices(FailingRawServicesProvider.class, FailingRawServices.class);
			this.rcs.unregisterComponent(FailingRawServicesProvider.class);
			Covalent.main.getParameters().setInstrumented(false);
			this.cm.getParameters().setSynchronousEvents(false);
		}
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

	public static interface FailingRawServices {
		void fail();
	}

	public static class FailingRawServicesProvider implements FailingRawServices {
		@Override
		public void fail() {
			throw new IllegalStateException("expected service failure");
		}
	}
}
