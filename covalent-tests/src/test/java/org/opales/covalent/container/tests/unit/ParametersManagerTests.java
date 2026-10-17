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
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.rr.parameters.ParametersManagerEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.FullyConnectedParameters;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.impl.FullyConnectedImpl;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithdelegation.ProducerWithSubscribeAndDelegationParameters;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithdelegation.impl.ProducerWithSubscribeAndDelegationImpl;

public class ParametersManagerTests extends CovalentTests {

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
	public void testParametersEvents() throws InterruptedException {
		// Internal (technical) components are only activated by the container's
		// own start() (outside the pre-first-start bootstrap window); subscribing
		// to a technical events interface below needs them activated.
		this.cm.start();

		Covalent.main.getParameters().setInstrumented(true);
		ParametersEventsConsumer parametersEventsConsumer = new ParametersEventsConsumer();
		this.rcs.subscribe(ParametersManagerEvents.class, parametersEventsConsumer);

		CountDownLatch latch = new CountDownLatch(3);
		parametersEventsConsumer.setLatch(latch);
		this.cm.deployComponents(ProducerWithSubscribeAndDelegationImpl.class, FullyConnectedImpl.class);
		// The container is already started (see above); activate the two
		// components just deployed instead of calling start() again.
		this.cm.lcs().activate();
		Assert.assertTrue("Timed out waiting for the expected events.", latch.await(5, TimeUnit.SECONDS));

		// Offered: ProducerWithSubscribeAndDelegationParameters,
		// FullyConnectedParameters
		// Accessed: ProducerWithSubscribeAndDelegationParameters
		Assert.assertTrue(parametersEventsConsumer.parametersOffered);
		Assert.assertEquals(2, parametersEventsConsumer.offeredCount);
		Assert.assertTrue(parametersEventsConsumer.parametersAccessed);
		Assert.assertEquals(FullyConnectedParameters.class, parametersEventsConsumer.offeredParameters);
		Assert.assertEquals(ProducerWithSubscribeAndDelegationParameters.class,
				parametersEventsConsumer.accessedParameters);

		parametersEventsConsumer.reset();
		latch = new CountDownLatch(2);
		parametersEventsConsumer.setLatch(latch);
		FullyConnectedParameters accessParameters = this.rcs.accessParameters(FullyConnectedParameters.class);
		boolean parameter = accessParameters.getParameter();
		Assert.assertTrue("Timed out waiting for the expected events.", latch.await(5, TimeUnit.SECONDS));

		// Accessed: FullyConnectedParameters
		// Called: FullyConnectedParameters
		Assert.assertTrue(parametersEventsConsumer.parametersAccessed);
		Assert.assertTrue(parameter);
		Assert.assertTrue(parametersEventsConsumer.accessedParametersCallStarted);
		Assert.assertTrue(parametersEventsConsumer.accessedParametersCallFinished);
		Assert.assertTrue(parametersEventsConsumer.offeredParametersCallStarted);
		Assert.assertTrue(parametersEventsConsumer.offeredParametersCallFinished);
		Assert.assertEquals(FullyConnectedParameters.class, parametersEventsConsumer.calledParameters);
		Assert.assertTrue(parametersEventsConsumer.durationNanos >= 0);
		Assert.assertNull(parametersEventsConsumer.failure);

		parametersEventsConsumer.reset();
		latch = new CountDownLatch(3);
		parametersEventsConsumer.setLatch(latch);
		this.cm.stop();
		Assert.assertTrue("Timed out waiting for the expected events.", latch.await(5, TimeUnit.SECONDS));
		// Unaccessed: ProducerWithSubscribeAndDelegationParameters
		// Unoffered: FullyConnectedParameters,
		// ProducerWithSubscribeAndDelegationParameters
		Assert.assertFalse(parametersEventsConsumer.parametersOffered);
		Assert.assertFalse(parametersEventsConsumer.parametersAccessed);
		Assert.assertTrue(parametersEventsConsumer.parametersUnoffered);
		Assert.assertTrue(parametersEventsConsumer.parametersUnaccessed);
		Assert.assertEquals(2, parametersEventsConsumer.unofferedCount);
		Assert.assertNull(parametersEventsConsumer.offeredParameters);
		Assert.assertNull(parametersEventsConsumer.accessedParameters);

		Covalent.main.getParameters().setInstrumented(false);
		// This test exercises stop() itself; restart so tearDown()'s own,
		// unconditional stop() below does not run on an already-stopped
		// container.
		this.cm.start();
	}

	class ParametersEventsConsumer implements ParametersManagerEvents {
		CountDownLatch latch;

		void setLatch(final CountDownLatch latch) {
			this.latch = latch;
		}

		private void countDownLatch() {
			if (this.latch != null) {
				this.latch.countDown();
			}
		}

		public void reset() {
			this.parametersOffered = false;
			this.parametersUnoffered = false;
			this.parametersAccessed = false;
			this.parametersUnaccessed = false;
			this.accessedParametersCallStarted = false;
			this.accessedParametersCallFinished = false;
			this.offeredParametersCallStarted = false;
			this.offeredParametersCallFinished = false;
			this.offeredParameters = null;
			this.accessedParameters = null;
			this.calledParameters = null;
			this.offeredCount = 0;
			this.unofferedCount = 0;
			this.parametersAccessor = null;
			this.parametersOfferor = null;
			this.durationNanos = -1;
			this.failure = null;
		}

		boolean parametersOffered;
		boolean parametersUnoffered;
		boolean parametersAccessed;
		boolean parametersUnaccessed;
		boolean accessedParametersCallStarted;
		boolean accessedParametersCallFinished;
		boolean offeredParametersCallStarted;
		boolean offeredParametersCallFinished;

		Class<?> offeredParameters;
		Class<?> accessedParameters;
		Class<?> calledParameters;
		Class<?> parametersAccessor;
		Class<?> parametersOfferor;
		Throwable failure;
		long durationNanos = -1;

		int offeredCount = 0;
		int unofferedCount = 0;

		@Override
		public <PARAMETERS> void onParametersOffered(final Class<?> componentType,
				final Class<PARAMETERS> parametersInterface) {
			this.parametersOffered = true;
			this.offeredParameters = parametersInterface;
			this.offeredCount++;
			this.countDownLatch();
		}

		@Override
		public <PARAMETERS> void onParametersUnoffered(final Class<?> componentType,
				final Class<PARAMETERS> parametersInterface) {
			this.parametersUnoffered = true;
			this.unofferedCount++;
			this.countDownLatch();
		}

		@Override
		public <PARAMETERS> void onParametersAccessed(final Class<?> parametersAccessor,
				final Class<PARAMETERS> parametersInterface) {
			this.parametersAccessed = true;
			this.accessedParameters = parametersInterface;
			this.countDownLatch();
		}

		@Override
		public <PARAMETERS> void onParametersUnaccessed(final Class<?> parametersAccessor,
				final Class<PARAMETERS> parametersInterface) {
			this.parametersUnaccessed = true;
			this.countDownLatch();
		}

		@Override
		public <PARAMETERS> void onAccessedParametersCallStarted(final Class<PARAMETERS> parametersInterface,
				final Method method, final Class<?> parametersAccessor, final Object... inputData) {
			this.accessedParametersCallStarted = true;
			this.calledParameters = parametersInterface;
			this.parametersAccessor = parametersAccessor;
			this.countDownLatch();
		}

		@Override
		public <PARAMETERS> void onAccessedParametersCallFinished(final Class<PARAMETERS> parametersInterface,
				final Method method, final Class<?> parametersAccessor, final long durationNanos,
				final Throwable failure, final Object returnValue, final Object... inputData) {
			this.accessedParametersCallFinished = true;
			this.calledParameters = parametersInterface;
			this.parametersAccessor = parametersAccessor;
			this.durationNanos = durationNanos;
			this.failure = failure;
			this.countDownLatch();
		}

		@Override
		public <PARAMETERS> void onOfferedParametersCallStarted(final Class<PARAMETERS> parametersInterface,
				final Method method, final Class<?> parametersOfferor, final Object... inputData) {
			this.offeredParametersCallStarted = true;
			this.calledParameters = parametersInterface;
			this.parametersOfferor = parametersOfferor;
			this.countDownLatch();
		}

		@Override
		public <PARAMETERS> void onOfferedParametersCallFinished(final Class<PARAMETERS> parametersInterface,
				final Method method, final Class<?> parametersOfferor, final long durationNanos,
				final Throwable failure, final Object returnValue, final Object... inputData) {
			this.offeredParametersCallFinished = true;
			this.calledParameters = parametersInterface;
			this.parametersOfferor = parametersOfferor;
			this.durationNanos = durationNanos;
			this.failure = failure;
			this.countDownLatch();
		}

	}

}
