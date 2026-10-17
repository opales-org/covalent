/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.container.rr.common.ClientRRInvocationHandler;
import org.opales.covalent.container.rr.common.ServerRRInvocationHandler;

/**
 * {@link ClientRRInvocationHandler} and {@link ServerRRInvocationHandler} both
 * expose {@code notifyCallStarted}/{@code notifyCallFinished} instrumentation
 * hooks that default to empty no-ops, overridden only by request/response
 * implementations that need to raise events. With instrumentation enabled and
 * the default hooks, a call goes through unchanged: it returns the target's
 * value or rethrows the target's failure.
 */
public class RRInvocationHandlerHooksTests {

	private static final IllegalStateException FAILURE = new IllegalStateException("target failure");

	private static final Method SUPPLIER_GET = method(Supplier.class, "get");


	@Test
	public void clientInvocationHandlerDefaultHooksLeaveTheCallUnchanged() throws Throwable {
		TestClientRRInvocationHandler<Runnable> handler = new TestClientRRInvocationHandler<>(() -> {
		});
		handler.setInstrumentationSupplier(() -> true);

		Assert.assertEquals("value", handler.call(valueSupplier(), SUPPLIER_GET, new Object[0]));
		Assert.assertSame(FAILURE, Assert.assertThrows(IllegalStateException.class,
				() -> handler.call(failingSupplier(), SUPPLIER_GET, new Object[0])));
	}

	@Test
	public void serverInvocationHandlerDefaultHooksLeaveTheCallUnchanged() throws Throwable {
		TestServerRRInvocationHandler handler = new TestServerRRInvocationHandler();
		handler.setInstrumentationSupplier(() -> true);

		Assert.assertEquals("value", handler.call(valueSupplier(), SUPPLIER_GET, new Object[0]));
		Assert.assertSame(FAILURE, Assert.assertThrows(IllegalStateException.class,
				() -> handler.call(failingSupplier(), SUPPLIER_GET, new Object[0])));
	}

	private static Method method(final Class<?> type, final String name, final Class<?>... parameterTypes) {
		try {
			return type.getMethod(name, parameterTypes);
		} catch (NoSuchMethodException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Supplier<String> valueSupplier() {
		return () -> "value";
	}

	private static Supplier<String> failingSupplier() {
		return () -> {
			throw FAILURE;
		};
	}

	private static final class TestClientRRInvocationHandler<RR> extends ClientRRInvocationHandler<RR> {

		TestClientRRInvocationHandler(final RR clientRR) {
			super(clientRR);
		}

		Object call(final Object target, final Method method, final Object[] args) throws Throwable {
			return this.invokeTarget(target, method, args);
		}
	}

	private static final class TestServerRRInvocationHandler extends ServerRRInvocationHandler<Runnable> {

		TestServerRRInvocationHandler() {
			super(Runnable.class, "test");
		}

		Object call(final Object target, final Method method, final Object[] args) throws Throwable {
			return this.invokeTarget(target, method, args);
		}

		@Override
		protected Class<?> getComponentTypeForProvidedInterface() {
			return null;
		}

		@Override
		protected Runnable getInstance(final Class<Runnable> anInterface) {
			return null;
		}

		@Override
		protected boolean checkInterface(final Class<Runnable> anInterface) {
			return false;
		}

		@Override
		protected Class<?> getRRAnnotation() {
			return Object.class;
		}
	}
}
