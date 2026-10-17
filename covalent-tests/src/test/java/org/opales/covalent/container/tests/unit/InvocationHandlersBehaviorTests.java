/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.container.invocation.CovalentInvocationHandler;
import org.opales.covalent.container.rr.common.ServerRRInvocationHandler;

/**
 * Verifies {@link CovalentInvocationHandler} (handling of {@code Object} methods, unwrapping of
 * {@link InvocationTargetException}, invalidation and activation state) and
 * {@link ServerRRInvocationHandler} (guards for disposed and passivated providers, lazy lookup of the
 * provider instance and its failure messages, forwarding, failure translation and instrumentation hooks).
 */
public class InvocationHandlersBehaviorTests {

	/** A service used as provided interface. */
	public interface Greeter {
		String greet(String name);

		void fail() throws java.io.IOException;
	}

	private static final class RealGreeter implements Greeter {
		@Override
		public String greet(final String name) {
			return "hello " + name;
		}

		@Override
		public void fail() throws java.io.IOException {
			throw new java.io.IOException("boom");
		}
	}

	private static final class PlainHandler extends CovalentInvocationHandler {
		Throwable toThrow;

		PlainHandler() {
			super();
		}

		@Override
		public Object invokeMethod(final Object proxy, final Method method, final Object[] args) throws Throwable {
			if (this.toThrow != null) {
				throw this.toThrow;
			}
			return "invoked:" + method.getName();
		}

		boolean check() {
			return this.checkInvalidation();
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T unchecked(final Object value) {
		return (T) value;
	}

	private static Runnable proxyOf(final CovalentInvocationHandler handler) {
		return (Runnable) Proxy.newProxyInstance(InvocationHandlersBehaviorTests.class.getClassLoader(),
				new Class<?>[] { Runnable.class }, handler);
	}

	@Test
	public void objectMethodsAreHandledLocallyAndOtherMethodsAreDispatched() throws Throwable {
		PlainHandler handler = new PlainHandler();
		Method run = Runnable.class.getMethod("run");
		assertEquals("invoked:run", handler.invoke(null, run, null));
		assertEquals(System.identityHashCode(handler), handler.invoke(null, Object.class.getMethod("hashCode"), null));
		String text = (String) handler.invoke(null, Object.class.getMethod("toString"), null);
		assertEquals("Proxy[PlainHandler]" + System.identityHashCode(handler), text);
	}

	@Test
	public void equalsWithAProxyArgumentComparesHandlers() throws Throwable {
		Method equals = Object.class.getMethod("equals", Object.class);
		PlainHandler first = new PlainHandler();
		PlainHandler second = new PlainHandler();
		assertEquals(Boolean.TRUE, first.invoke(null, equals, new Object[] { proxyOf(first) }));
		assertEquals(Boolean.FALSE, first.invoke(null, equals, new Object[] { proxyOf(second) }));
	}

	@Test
	public void proxyIsEqualOnlyToItself() {
		Runnable proxy = proxyOf(new PlainHandler());
		Runnable other = proxyOf(new PlainHandler());
		Runnable plain = () -> {
		};
		assertTrue(proxy.equals(proxy));
		assertFalse(proxy.equals(null));
		assertFalse(proxy.equals(other));
		assertFalse(other.equals(proxy));
		assertFalse(proxy.equals(plain));
		assertFalse(plain.equals(proxy));
	}

	@Test
	public void proxyHashCodeIsTheHandlerIdentityHashCode() {
		PlainHandler handler = new PlainHandler();
		Runnable proxy = proxyOf(handler);
		assertEquals(System.identityHashCode(handler), proxy.hashCode());
		assertTrue(proxy.toString().startsWith("Proxy[PlainHandler]"));
	}

	@Test
	public void objectMethodsOtherThanEqualsHashCodeAndToStringAreDispatchedLikeAnyOtherMethod() throws Throwable {
		// a dynamic proxy only hands equals, hashCode and toString of Object to its handler
		PlainHandler handler = new PlainHandler();
		assertEquals("invoked:getClass", handler.invoke(null, Object.class.getMethod("getClass"), null));
		assertEquals("invoked:notify", handler.invoke(null, Object.class.getMethod("notify"), new Object[0]));
		assertEquals("invoked:wait",
				handler.invoke(null, Object.class.getMethod("wait", long.class), new Object[] { 1L }));
	}

	@Test
	public void invocationTargetExceptionIsUnwrappedToItsCause() throws Throwable {
		PlainHandler handler = new PlainHandler();
		IllegalStateException cause = new IllegalStateException("inner");
		handler.toThrow = new InvocationTargetException(cause);
		IllegalStateException e = Assert.assertThrows(IllegalStateException.class,
				() -> handler.invoke(null, Runnable.class.getMethod("run"), null));
		assertSame(cause, e);
	}

	@Test
	public void invocationTargetExceptionWithoutCauseIsRethrownAsIs() throws Throwable {
		PlainHandler handler = new PlainHandler();
		InvocationTargetException bare = new InvocationTargetException(null);
		handler.toThrow = bare;
		InvocationTargetException e = Assert.assertThrows(InvocationTargetException.class,
				() -> handler.invoke(null, Runnable.class.getMethod("run"), null));
		assertSame(bare, e);
	}

	@Test
	public void invalidationAndActivationStateAreTracked() {
		PlainHandler handler = new PlainHandler();
		assertTrue(handler.isPassivated());
		assertFalse(handler.isActivated());
		handler.activate();
		assertTrue(handler.isActivated());
		assertFalse(handler.isPassivated());
		handler.passivate();
		assertTrue(handler.isPassivated());

		assertFalse(handler.isInvalidated());
		assertTrue(handler.check());
		handler.invalidate();
		assertTrue(handler.isInvalidated());
		IllegalStateException e = Assert.assertThrows(IllegalStateException.class, () -> handler.check());
		assertTrue(e.getMessage().contains("invalidated"));
	}

	/* ------------------------------------------------------------------ */

	private static final class Server extends ServerRRInvocationHandler<Greeter> {
		Class<?> lookupType;
		Greeter lookupInstance;
		boolean interfaceChecked = true;
		int lookups;
		final List<String> events = new ArrayList<>();

		Server() {
			super(Greeter.class, "service");
		}

		@Override
		protected Class<?> getComponentTypeForProvidedInterface() {
			return this.lookupType;
		}

		@Override
		protected Greeter getInstance(final Class<Greeter> anInterface) {
			this.lookups++;
			return this.lookupInstance;
		}

		@Override
		protected boolean checkInterface(final Class<Greeter> anInterface) {
			return this.interfaceChecked;
		}

		@Override
		protected Class<?> getRRAnnotation() {
			return Deprecated.class;
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

		void provide(final Object instance) {
			this.providedInstance = InvocationHandlersBehaviorTests.<Greeter>unchecked(instance);
		}

		Greeter proxy() {
			return (Greeter) Proxy.newProxyInstance(Greeter.class.getClassLoader(), new Class<?>[] { Greeter.class },
					this);
		}
	}

	@Test
	public void serverRejectsANullInterface() {
		try {
			new ServerRRInvocationHandler<Greeter>(null, "service") {
				@Override
				protected Class<?> getComponentTypeForProvidedInterface() {
					return null;
				}

				@Override
				protected Greeter getInstance(final Class<Greeter> anInterface) {
					return null;
				}

				@Override
				protected boolean checkInterface(final Class<Greeter> anInterface) {
					return false;
				}

				@Override
				protected Class<?> getRRAnnotation() {
					return null;
				}
			};
			fail();
		} catch (RuntimeException expected) {
			assertTrue(expected.getMessage().contains("providedInterface"));
		}
	}

	@Test
	public void serverForwardsCallsToTheProviderOnceActivated() {
		Server server = new Server();
		server.provide(new RealGreeter());
		server.activate();
		assertEquals("hello bob", server.proxy().greet("bob"));
		assertEquals(0, server.lookups);
	}

	@Test
	public void serverRefusesCallsWhenPassivatedWithTheInterfaceName() {
		Server server = new Server();
		IllegalStateException e = Assert.assertThrows(IllegalStateException.class, () -> server.proxy().greet("x"));
		assertEquals("Cannot invoke service: Greeter. Component is passivated.", e.getMessage());
	}

	@Test
	public void serverDisposedMessageTakesPrecedenceOverPassivation() {
		Server server = new Server();
		AtomicBoolean disposed = new AtomicBoolean(true);
		server.setDisposedSupplier(disposed::get);
		IllegalStateException e = Assert.assertThrows(IllegalStateException.class, () -> server.proxy().greet("x"));
		assertEquals("Container is disposed and no longer available.", e.getMessage());
		disposed.set(false);
		IllegalStateException laterFailure = Assert.assertThrows(IllegalStateException.class,
				() -> server.proxy().greet("x"));
		assertTrue(laterFailure.getMessage().contains("passivated"));
	}

	@Test
	public void serverLooksUpTheProviderLazilyAndKeepsIt() {
		Server server = new Server();
		server.lookupType = String.class;
		server.lookupInstance = new RealGreeter();
		server.activate();
		Greeter proxy = server.proxy();
		assertEquals("hello a", proxy.greet("a"));
		assertEquals("hello b", proxy.greet("b"));
		assertEquals(1, server.lookups);
	}

	@Test
	public void serverProxyKeepsItsHashCodeWhenItsProviderIsLookedUpLazily() {
		Server server = new Server();
		server.lookupType = String.class;
		server.lookupInstance = new RealGreeter();
		server.activate();
		Greeter proxy = server.proxy();
		Set<Greeter> proxies = new HashSet<>();
		proxies.add(proxy);
		int hashBeforeLookup = proxy.hashCode();
		assertEquals("hello a", proxy.greet("a"));
		assertEquals(1, server.lookups);
		assertEquals(hashBeforeLookup, proxy.hashCode());
		assertTrue(proxies.contains(proxy));
		assertFalse(proxy.equals(server.lookupInstance));
	}

	@Test
	public void serverFailsWhenNoComponentTypeProvidesTheInterface() {
		Server server = new Server();
		server.activate();
		IllegalStateException e = Assert.assertThrows(IllegalStateException.class, () -> server.proxy().greet("x"));
		assertTrue(e.getMessage().startsWith("No component type found for service"));
		assertTrue(e.getMessage().contains(Greeter.class.getName()));
	}

	@Test
	public void serverFailsWhenNoProviderInstanceExists() {
		Server server = new Server();
		server.lookupType = String.class;
		server.activate();
		IllegalStateException e = Assert.assertThrows(IllegalStateException.class, () -> server.proxy().greet("x"));
		assertTrue(e.getMessage().startsWith("No provider found for service"));
		assertFalse(e.getMessage().contains("not annotated"));
	}

	@Test
	public void serverMentionsTheMissingAnnotationWhenTheInterfaceIsNotValid() {
		Server server = new Server();
		server.lookupType = String.class;
		server.interfaceChecked = false;
		server.activate();
		IllegalStateException e = Assert.assertThrows(IllegalStateException.class, () -> server.proxy().greet("x"));
		assertTrue(e.getMessage().contains("This interface is not annotated with @Deprecated."));
	}

	@Test
	public void serverRethrowsTheProviderExceptionUnchanged() {
		Server server = new Server();
		server.provide(new RealGreeter());
		server.activate();
		java.io.IOException e = Assert.assertThrows(java.io.IOException.class, () -> server.proxy().fail());
		assertEquals("boom", e.getMessage());
	}

	@Test
	public void serverWrapsReflectionFailuresWithTheKindAndInterface() {
		Server server = new Server();
		server.provide("not a greeter");
		server.activate();
		RuntimeException e = Assert.assertThrows(RuntimeException.class, () -> server.proxy().greet("x"));
		assertEquals("Exception while invoking service: greet from " + Greeter.class, e.getMessage());
		assertTrue(e.getCause() instanceof IllegalArgumentException);
	}

	@Test
	public void serverNotifiesInstrumentationAroundSuccessfulAndFailedCalls() {
		Server server = new Server();
		server.provide(new RealGreeter());
		server.activate();
		AtomicBoolean enabled = new AtomicBoolean(true);
		server.setInstrumentationSupplier(enabled::get);
		Greeter proxy = server.proxy();
		proxy.greet("z");
		try {
			proxy.fail();
		} catch (java.io.IOException expected) {
			// expected
		}
		assertEquals(List.of("start:greet", "end:greet:ok:hello z:true", "start:fail", "end:fail:IOException:null:true"),
				server.events);
	}

	@Test
	public void serverDoesNotNotifyWhenInstrumentationIsOffOrUnset() {
		Server server = new Server();
		server.provide(new RealGreeter());
		server.activate();
		server.proxy().greet("a");
		server.setInstrumentationSupplier(() -> false);
		server.proxy().greet("b");
		assertTrue(server.events.isEmpty());
	}
}
