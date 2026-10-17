/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.tests.CovalentContainerizedTests;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.async.AsyncFailureHandler;
import org.opales.covalent.container.async.AsyncParameters;
import org.opales.covalent.container.component.annotations.Parameters;
import org.opales.covalent.container.introspection.IntrospectionServices;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.rr.services.ServicesManagerServices;
import org.opales.covalent.container.validation.ValidationParameters;

/**
 * <p>
 * A handful of container-level parameters and services expose simple
 * getter/setter pairs whose setter is exercised elsewhere in the suite (or by
 * the framework itself) while the matching getter is never read back
 * directly. These tests read them back explicitly, restoring the original
 * value afterwards so other tests keep running against the defaults.
 * </p>
 */
public class ConfigurationAccessorsContainerizedTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	@Before
	public void setUp() {
		this.rcs.registerComponent(this);
		this.cm.getParameters().setSynchronousEvents(true);
	}

	@Test
	public void containerParametersExposeCleanStackTracesAndVerificationLevels() {
		CovalentContainerParameters parameters = this.cm.getParameters();
		boolean originalCleanStackTraces = parameters.isCleanStackTraces();
		VerificationLevel originalStructural = parameters.getStructuralRulesChecking();
		VerificationLevel originalDynamic = parameters.getDynamicRulesChecking();
		try {
			parameters.setCleanStackTraces(true);
			Assert.assertTrue(parameters.isCleanStackTraces());
			parameters.setCleanStackTraces(false);
			Assert.assertFalse(parameters.isCleanStackTraces());

			parameters.setStructuralRulesChecking(VerificationLevel.strict);
			Assert.assertEquals(VerificationLevel.strict, parameters.getStructuralRulesChecking());

			parameters.setDynamicRulesChecking(VerificationLevel.warn);
			Assert.assertEquals(VerificationLevel.warn, parameters.getDynamicRulesChecking());
		} finally {
			parameters.setCleanStackTraces(originalCleanStackTraces);
			parameters.setStructuralRulesChecking(originalStructural);
			parameters.setDynamicRulesChecking(originalDynamic);
		}
	}

	@Test
	public void validationParametersExposeFieldNamingConventionToggles() {
		ValidationParameters validationParameters = this.rcs.accessParameters(ValidationParameters.class);
		boolean originalProduce = validationParameters.isCheckProduceEventsFieldNamingConvention();
		boolean originalSubscriber = validationParameters.isCheckUseSubscriberFieldNamingConvention();
		boolean originalServices = validationParameters.isCheckUseServicesFieldNamingConvention();
		boolean originalEvents = validationParameters.isCheckEventsMethodNamingConvention();
		try {
			validationParameters.setCheckProduceEventsFieldNamingConvention(false);
			Assert.assertFalse(validationParameters.isCheckProduceEventsFieldNamingConvention());
			validationParameters.setCheckProduceEventsFieldNamingConvention(true);
			Assert.assertTrue(validationParameters.isCheckProduceEventsFieldNamingConvention());

			validationParameters.setCheckUseSubscriberFieldNamingConvention(false);
			Assert.assertFalse(validationParameters.isCheckUseSubscriberFieldNamingConvention());
			validationParameters.setCheckUseSubscriberFieldNamingConvention(true);
			Assert.assertTrue(validationParameters.isCheckUseSubscriberFieldNamingConvention());

			validationParameters.setCheckUseServicesFieldNamingConvention(false);
			Assert.assertFalse(validationParameters.isCheckUseServicesFieldNamingConvention());
			validationParameters.setCheckUseServicesFieldNamingConvention(true);
			Assert.assertTrue(validationParameters.isCheckUseServicesFieldNamingConvention());

			validationParameters.setCheckEventsMethodNamingConvention(false);
			Assert.assertFalse(validationParameters.isCheckEventsMethodNamingConvention());
			validationParameters.setCheckEventsMethodNamingConvention(true);
			Assert.assertTrue(validationParameters.isCheckEventsMethodNamingConvention());
		} finally {
			validationParameters.setCheckProduceEventsFieldNamingConvention(originalProduce);
			validationParameters.setCheckUseSubscriberFieldNamingConvention(originalSubscriber);
			validationParameters.setCheckUseServicesFieldNamingConvention(originalServices);
			validationParameters.setCheckEventsMethodNamingConvention(originalEvents);
		}
	}

	@Test
	public void asyncParametersExposeUnhandledFailureHandler() {
		AsyncParameters asyncParameters = this.rcs.accessParameters(AsyncParameters.class);
		AsyncFailureHandler customHandler = (message, failure) -> {
		};
		try {
			asyncParameters.setUnhandledFailureHandler(customHandler);
			Assert.assertSame(customHandler, asyncParameters.getUnhandledFailureHandler());

			asyncParameters.setUnhandledFailureHandler(null);
			Assert.assertNotNull(asyncParameters.getUnhandledFailureHandler());
			Assert.assertNotSame(customHandler, asyncParameters.getUnhandledFailureHandler());
		} finally {
			asyncParameters.setUnhandledFailureHandler(null);
		}
	}

	@Test
	public void servicesManagerExposesProviderToServicesIndex() {
		ServicesManagerServices servicesManagerServices = this.rcs.useServices(ServicesManagerServices.class);
		Assert.assertNotNull(servicesManagerServices.getProviderToServices());
	}

	@Test
	public void introspectionServicesRecognizeParametersInterfaces() {
		IntrospectionServices introspectionServices = this.rcs.useServices(IntrospectionServices.class);
		Assert.assertTrue(introspectionServices.isComponentParameters(LocalParameters.class));
		Assert.assertFalse(introspectionServices.isComponentParameters(Runnable.class));
	}

	@Parameters
	public interface LocalParameters {
	}
}
