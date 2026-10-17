/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.container.rr.common.ClientRRInvocationHandler;

/**
 * Verifies {@link ClientRRInvocationHandler}: forwarding to the current target, replacement of the target,
 * refusal of calls once invalidated, failures of the target rethrown unchanged, failures of the reflective
 * call wrapped with the name of the call, and instrumentation notifications.
 */
public class ClientRRInvocationHandlerTests {

	/** A services interface used as proxied interface. */
	public interface Greeter {
		String greet(String name);

		void fail() throws IOException;
	}

	private static final class RealGreeter implements Greeter {
		private final String prefix;

		RealGreeter(final String prefix) {
			this.prefix = prefix;
		}

		@Override
		public String greet(final String name) {
			return this.prefix + " " + name;
		}

		@Override
		public void fail() throws IOException {
			throw new IOException("boom");
		}
	}

	private static final class Client extends ClientRRInvocationHandler<Object> {
		final List<String> events = new ArrayList<>();

		Client(final Object target) {
			super(target);
		}

		@Override
		protected void notifyCallStarted(final Method method, final Object[] args) {
			this.events.add("start:" + method.getName());
		}

		@Override
		protected void notifyCallFinished(final Method method, final Object[] args, final long durationNanos,
				final Throwable failure, final Object returnValue) {
			this.events.add("end:" + method.getName() + ":" + (failure == null ? "ok" : failure.getClass().getSimpleName())
					+ ":" + returnValue + ":" + (durationNanos >= 0));
		}

		Greeter proxy() {
			return (Greeter) Proxy.newProxyInstance(Greeter.class.getClassLoader(), new Class<?>[] { Greeter.class },
					this);
		}
	}

	@Test
	public void callsAreForwardedToTheCurrentTarget() {
		Client client = new Client(new RealGreeter("hello"));
		assertEquals("hello world", client.proxy().greet("world"));
	}

	@Test
	public void replacingTheTargetRedirectsLaterCalls() {
		Client client = new Client(new RealGreeter("hello"));
		Greeter proxy = client.proxy();
		assertEquals("hello a", proxy.greet("a"));
		client.replaceClientRR(new RealGreeter("bye"));
		assertEquals("bye a", proxy.greet("a"));
	}

	@Test
	public void callsAreRefusedOnceInvalidated() {
		Client client = new Client(new RealGreeter("hello"));
		Greeter proxy = client.proxy();
		client.invalidate();
		IllegalStateException e = Assert.assertThrows(IllegalStateException.class, () -> proxy.greet("x"));
		assertEquals("Proxy[Client] is invalidated", e.getMessage());
		assertTrue(client.events.isEmpty());
	}

	@Test
	public void aFailureOfTheTargetIsRethrownUnchanged() {
		Client client = new Client(new RealGreeter("hello"));
		IOException e = Assert.assertThrows(IOException.class, () -> client.proxy().fail());
		assertEquals("boom", e.getMessage());
	}

	@Test
	public void aFailureOfTheReflectiveCallIsWrappedWithTheNameOfTheCall() {
		Client client = new Client("not a greeter");
		RuntimeException e = Assert.assertThrows(RuntimeException.class, () -> client.proxy().greet("x"));
		assertEquals("Exception while invoking greet from " + Greeter.class, e.getMessage());
		assertTrue(e.getCause() instanceof IllegalArgumentException);
	}

	@Test
	public void instrumentationIsNotifiedAroundSuccessfulAndFailedCalls() {
		Client client = new Client(new RealGreeter("hello"));
		AtomicBoolean enabled = new AtomicBoolean(true);
		client.setInstrumentationSupplier(enabled::get);
		Greeter proxy = client.proxy();
		proxy.greet("z");
		try {
			proxy.fail();
		} catch (IOException expected) {
			// expected
		}
		assertEquals(List.of("start:greet", "end:greet:ok:hello z:true", "start:fail", "end:fail:IOException:null:true"),
				client.events);
	}

	@Test
	public void instrumentationIsSilentWhenOffOrUnset() {
		Client client = new Client(new RealGreeter("hello"));
		client.proxy().greet("a");
		AtomicBoolean enabled = new AtomicBoolean(false);
		client.setInstrumentationSupplier(enabled::get);
		client.proxy().greet("b");
		assertTrue(client.events.isEmpty());
	}
}
