/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.nonregression.diagnostics;

import java.util.Arrays;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.CovalentContainerizedTests;

public class CleanStackTraceRegressionContainerizedTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rawComponentsServices = Covalent.main.rcs();

	@Before
	public void setUp() {
		this.rawComponentsServices.registerComponent(this);
		this.cm.getParameters().setSynchronousEvents(true);
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
		this.cm.getParameters().setCleanStackTraces(true);
	}

	@After
	public void tearDown() {
		this.cm.getParameters().setCleanStackTraces(false);
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.trace);

	}

	@Test
	public void cleanedStackTraceShouldKeepUserFramesAndRemoveCovalentInvocationFrames() {
		IllegalStateException failure = this.catchFailureFromServiceProxy();

		Assert.assertTrue("The cleaned stack trace should keep the user failure frame.",
				this.containsFrame(failure, FailingServicesImpl.class, "failFromUserCode"));
		Assert.assertFalse("The cleaned stack trace should remove Covalent invocation handler frames.",
				this.containsClassName(failure, "CovalentInvocationHandler"));
		Assert.assertFalse("The cleaned stack trace should remove JDK reflection frames.",
				this.containsClassName(failure, "java.lang.reflect.Method"));
		Assert.assertFalse("The cleaned stack trace should remove JDK proxy frames.",
				this.containsClassName(failure, "jdk.proxy"));
	}

	@Test
	public void rawStackTraceShouldBePreservedWhenCleaningIsDisabled() {
		this.cm.getParameters().setCleanStackTraces(false);

		IllegalStateException failure = this.catchFailureFromServiceProxy();

		Assert.assertTrue("The raw stack trace should keep Covalent invocation handler frames.",
				this.containsClassName(failure, "CovalentInvocationHandler"));
		Assert.assertTrue("The raw stack trace should keep JDK reflection frames.",
				this.containsClassName(failure, "java.lang.reflect.Method"));
	}

	private IllegalStateException catchFailureFromServiceProxy() {
		FailingServices failingServices = new FailingServicesImpl();
		this.rawComponentsServices.registerComponent(FailingServicesImpl.class, failingServices);
		this.rawComponentsServices.provideServices(FailingServicesImpl.class, FailingServices.class);

		try {
			FailingServices usedFailingServices =
					this.rawComponentsServices.useServices(FailingServicesImpl.class, FailingServices.class);
			usedFailingServices.failFromUserCode();
			Assert.fail("The service should fail from user code.");
			return null;
		} catch (IllegalStateException e) {
			return e;
		} finally {
			this.rawComponentsServices.unuseServices(FailingServices.class);
			this.rawComponentsServices.unprovideServices(FailingServicesImpl.class, FailingServices.class);
			this.rawComponentsServices.unregisterComponent(FailingServicesImpl.class);
		}
	}

	private boolean containsFrame(final Throwable throwable, final Class<?> declaringClass, final String methodName) {
		return Arrays.stream(throwable.getStackTrace()).anyMatch(stackTraceElement ->
				stackTraceElement.getClassName().equals(declaringClass.getName())
						&& stackTraceElement.getMethodName().equals(methodName));
	}

	private boolean containsClassName(final Throwable throwable, final String classNamePart) {
		return Arrays.stream(throwable.getStackTrace())
				.anyMatch(stackTraceElement -> stackTraceElement.getClassName().contains(classNamePart));
	}

	public interface FailingServices {
		void failFromUserCode();
	}

	public static class FailingServicesImpl implements FailingServices {
		@Override
		public void failFromUserCode() {
			throw new IllegalStateException("user failure");
		}
	}
}
