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
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.rr.parameters.ParametersManagerEvents;
import org.opales.covalent.container.rr.parameters.ParametersManagerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerServices;

public class ParametersManagerContainerizedTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	@Before
	public void setUp() {
		this.rcs.registerComponent(this);
		this.cm.getParameters().setSynchronousEvents(true);
	}

	@Test
	public void duplicateOfferedParametersFailsWithoutReplacingOfferor() {
		ManualParametersComponent offeror = new ManualParametersComponent();
		this.rcs.registerComponent(offeror);

		try {
			this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.strict);

			ValidationException exception = Assert.assertThrows(ValidationException.class,
					() -> this.rcs.offerParameters(ManualParametersComponent.class, ManualParameters.class));
			Assert.assertTrue(exception.getMessage().contains("already provided"));

			this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.trace);
			ManualParameters parameters = this.rcs.accessParameters(ManualParametersComponent.class,
					ManualParameters.class);
			Assert.assertTrue(parameters.getParameter());
			this.rcs.unaccessParameters(ManualParameters.class);
		} finally {
			this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.trace);
			this.rcs.unregisterComponent(ManualParametersComponent.class);
		}
	}

	@Test
	public void accessedParametersRegistrationIsRemovedAfterUnaccess() {
		ManualParametersComponent offeror = new ManualParametersComponent();
		this.rcs.registerComponent(offeror);

		try {
			ParametersManagerServices parametersManagerServices = this.rcs.useServices(ParametersManagerServices.class);
			ManualParameters parameters = this.rcs.accessParameters(ManualParametersComponent.class,
					ManualParameters.class);
			Assert.assertTrue(parameters.getParameter());
			Assert.assertTrue(parametersManagerServices.getParametersToAccessor().get(ManualParameters.class)
					.contains(ParametersManagerContainerizedTests.class));

			this.rcs.unaccessParameters(ManualParameters.class);

			Assert.assertNull(parametersManagerServices.getParametersToAccessor().get(ManualParameters.class));
		} finally {
			this.rcs.unregisterComponent(ManualParametersComponent.class);
		}
	}

	@Test
	public void accessedParametersProxyIsInvalidatedAfterUnaccess() {
		ManualParametersComponent offeror = new ManualParametersComponent();
		this.rcs.registerComponent(offeror);

		try {
			ManualParameters parameters = this.rcs.accessParameters(ManualParametersComponent.class,
					ManualParameters.class);
			Assert.assertTrue(parameters.getParameter());

			this.rcs.unaccessParameters(ManualParameters.class);

			Assert.assertThrows(IllegalStateException.class, parameters::getParameter);
		} finally {
			this.rcs.unregisterComponent(ManualParametersComponent.class);
		}
	}

	@Test
	public void duplicateAccessedParametersFailsWithoutReplacingOriginalProxy() {
		ManualParametersComponent offeror = new ManualParametersComponent();
		this.rcs.registerComponent(offeror);

		try {
			ManualParameters parameters = this.rcs.accessParameters(ManualParametersComponent.class,
					ManualParameters.class);
			try {
				this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.strict);

				ValidationException exception = Assert.assertThrows(ValidationException.class,
						() -> this.rcs.accessParameters(ManualParametersComponent.class, ManualParameters.class));
				Assert.assertTrue(exception.getMessage().contains("already used"));

				this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.trace);
				Assert.assertTrue(parameters.getParameter());
			} finally {
				this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.trace);
				this.rcs.unaccessParameters(ManualParameters.class);
			}
		} finally {
			this.rcs.unregisterComponent(ManualParametersComponent.class);
		}
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void accessedParametersRejectServicesInterface() {
		ParametersManagerServices parametersManagerServices = this.rcs.useServices(ParametersManagerServices.class);

		IllegalArgumentException exception = Assert.assertThrows(IllegalArgumentException.class,
				() -> parametersManagerServices.createAccessedParameters(ParametersManagerContainerizedTests.class,
						(Class) ProducerServices.class));

		Assert.assertTrue(exception.getMessage().contains("services interface"));
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void accessedParametersRejectEventsInterface() {
		ParametersManagerServices parametersManagerServices = this.rcs.useServices(ParametersManagerServices.class);

		IllegalArgumentException exception = Assert.assertThrows(IllegalArgumentException.class,
				() -> parametersManagerServices.createAccessedParameters(ParametersManagerContainerizedTests.class,
						(Class) ProducerEvents.class));

		Assert.assertTrue(exception.getMessage().contains("events interface"));
	}

	@Test
	public void parametersInstrumentationReportsFailuresAndDuration() throws InterruptedException {
		Covalent.main.getParameters().setInstrumented(true);
		this.cm.getParameters().setSynchronousEvents(true);

		ParametersEventsConsumer parametersEventsConsumer = new ParametersEventsConsumer();
		this.rcs.subscribe(ParametersManagerEvents.class, parametersEventsConsumer);
		FailingRawParametersOfferor offeror = new FailingRawParametersOfferor();
		this.rcs.registerComponent(offeror);
		try {
			this.rcs.offerParameters(FailingRawParametersOfferor.class, FailingRawParameters.class);
			FailingRawParameters parameters = this.rcs.accessParameters(FailingRawParametersOfferor.class,
					FailingRawParameters.class);

			CountDownLatch latch = new CountDownLatch(4);
			parametersEventsConsumer.reset();
			parametersEventsConsumer.setLatch(latch);
			IllegalStateException failure = Assert.assertThrows(IllegalStateException.class, parameters::fail);
			Assert.assertTrue("Timed out waiting for the expected events.", latch.await(5, TimeUnit.SECONDS));

			Assert.assertTrue(parametersEventsConsumer.accessedParametersCallStarted);
			Assert.assertTrue(parametersEventsConsumer.accessedParametersCallFinished);
			Assert.assertTrue(parametersEventsConsumer.offeredParametersCallStarted);
			Assert.assertTrue(parametersEventsConsumer.offeredParametersCallFinished);
			Assert.assertEquals(FailingRawParameters.class, parametersEventsConsumer.calledParameters);
			Assert.assertEquals(ParametersManagerContainerizedTests.class, parametersEventsConsumer.parametersAccessor);
			Assert.assertEquals(FailingRawParametersOfferor.class, parametersEventsConsumer.parametersOfferor);
			Assert.assertSame(failure, parametersEventsConsumer.failure);
			Assert.assertTrue(parametersEventsConsumer.durationNanos >= 0);
		} finally {
			this.rcs.unaccessParameters(FailingRawParameters.class);
			this.rcs.unofferParameters(FailingRawParametersOfferor.class, FailingRawParameters.class);
			this.rcs.unregisterComponent(FailingRawParametersOfferor.class);
			Covalent.main.getParameters().setInstrumented(false);
			this.cm.getParameters().setSynchronousEvents(false);
		}
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

	public interface ManualParameters {
		boolean getParameter();
	}

	public static class ManualParametersComponent implements ManualParameters {
		private final RawComponentsServices rcs = Covalent.main.rcs();

		@Activation
		public void activate() {
			this.rcs.offerParameters(ManualParametersComponent.class, ManualParameters.class);
		}

		@Passivation
		public void passivate() {
			this.rcs.unofferParameters(ManualParametersComponent.class, ManualParameters.class);
		}

		@Override
		public boolean getParameter() {
			return true;
		}
	}

	public interface FailingRawParameters {
		boolean fail();
	}

	public static class FailingRawParametersOfferor implements FailingRawParameters {
		@Override
		public boolean fail() {
			throw new IllegalStateException("expected parameters failure");
		}
	}
}
