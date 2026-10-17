/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.container.util.RuntimeCheck;
import org.opales.covalent.container.util.StackTraceCleaner;

public class RuntimeUtilityBranchTests {

	@After
	public void tearDown() {
		StackTraceCleaner.setEnabled(false);
	}

	@Test
	public void runtimeChecksAcceptValidValuesAndRejectInvalidValues() {
		RuntimeCheck.notNull("value", "value");
		RuntimeCheck.isNull(null, "value");
		RuntimeCheck.isTrue(true, "condition");
		RuntimeCheck.isFalse(false, "condition");
		RuntimeCheck.equals(1.0f, 1.0f);
		RuntimeCheck.equals(1.0f, 1.0f + RuntimeCheck.EPSILON_FLOAT / 2.0f);
		RuntimeCheck.equals(1.0d, 1.0d);
		RuntimeCheck.equals(1.0d, 1.0d + RuntimeCheck.EPSILON_DOUBLE / 2.0d);

		assertRuntimeCheckFailure(() -> RuntimeCheck.notNull(null, "value"), "value must not be null");
		assertRuntimeCheckFailure(() -> RuntimeCheck.isNull("value", "value"), "value must be null");
		assertRuntimeCheckFailure(() -> RuntimeCheck.isTrue(false, "condition"), "condition must be true");
		assertRuntimeCheckFailure(() -> RuntimeCheck.isFalse(true, "condition"), "condition must be false");
		assertRuntimeCheckFailure(() -> RuntimeCheck.equals(1.0f, 2.0f), "expected float");
		assertRuntimeCheckFailure(() -> RuntimeCheck.equals(1.0d, 2.0d), "expected double");
	}

	@Test
	public void stackTraceCleanerRemovesTechnicalFramesWhenEnabled() {
		IllegalStateException failure = new IllegalStateException("failure");
		IllegalArgumentException shared = new IllegalArgumentException("shared");
		failure.addSuppressed(shared);
		failure.initCause(shared);
		failure.setStackTrace(new StackTraceElement[] {
				new StackTraceElement("java.lang.reflect.Method", "invoke", "Method.java", 1),
				new StackTraceElement("jdk.internal.reflect.NativeMethodAccessorImpl", "invoke0",
						"NativeMethodAccessorImpl.java", 2),
				new StackTraceElement("jdk.proxy1.$Proxy0", "call", null, -1),
				new StackTraceElement("com.sun.proxy.$Proxy1", "call", null, -1),
				new StackTraceElement("org.opales.covalent.container.invocation.CovalentInvocationHandler",
						"invoke", "CovalentInvocationHandler.java", 3),
				new StackTraceElement("org.opales.covalent.container.rr.common.ServerRRInvocationHandler",
						"invoke", "ServerRRInvocationHandler.java", 4),
				new StackTraceElement("org.opales.covalent.container.rr.services.impl.ServicesManagerImpl",
						"call", "ServicesManagerImpl.java", 5),
				new StackTraceElement("org.opales.covalent.container.rr.parameters.impl.ParametersManagerImpl",
						"call", "ParametersManagerImpl.java", 6),
				new StackTraceElement("org.opales.covalent.container.events.impl.EventsManagerImpl",
						"call", "EventsManagerImpl.java", 7),
				new StackTraceElement("org.opales.covalent.container.util.Reflection", "invokeMethod",
						"Reflection.java", 8),
				new StackTraceElement("org.opales.covalent.container.async.impl.AsyncManagerImpl$Worker",
						"run", "AsyncManagerImpl.java", 9),
				new StackTraceElement("example.BusinessService", "run", "BusinessService.java", 10) });
		shared.setStackTrace(new StackTraceElement[] {
				new StackTraceElement("org.opales.covalent.container.events.impl.EventsManagerImpl", "call",
						"EventsManagerImpl.java", 11),
				new StackTraceElement("example.SharedFailure", "run", "SharedFailure.java", 12) });

		Assert.assertSame(failure, StackTraceCleaner.cleanIfEnabled(failure));
		Assert.assertEquals(12, failure.getStackTrace().length);

		StackTraceCleaner.setEnabled(true);

		Assert.assertTrue(StackTraceCleaner.isEnabled());
		Assert.assertSame(failure, StackTraceCleaner.cleanIfEnabled(failure));
		Assert.assertEquals(1, failure.getStackTrace().length);
		Assert.assertEquals("example.BusinessService", failure.getStackTrace()[0].getClassName());
		Assert.assertEquals(1, shared.getStackTrace().length);
		Assert.assertEquals("example.SharedFailure", shared.getStackTrace()[0].getClassName());
		Assert.assertNull(StackTraceCleaner.clean((RuntimeException) null));
	}

	private static void assertRuntimeCheckFailure(final Runnable runnable, final String expectedMessageFragment) {
		IllegalArgumentException failure = Assert.assertThrows(IllegalArgumentException.class, runnable::run);
		Assert.assertTrue(failure.getMessage().contains(expectedMessageFragment));
	}
}
