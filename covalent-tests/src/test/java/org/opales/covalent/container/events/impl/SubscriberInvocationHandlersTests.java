/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.events.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.container.directory.NamingServices;
import org.opales.covalent.container.exceptions.NotBoundException;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;
import org.opales.covalent.container.validation.ValidationServices;

/**
 * Verifies the consumer-side subscribe machinery of the events: the classification of the methods of a subscribe
 * interface and the resolution of a delegate from argument types ({@link AbstractSubscriberInvocationHandler}), the
 * substitution of consumer-side proxies and the reporting of duplicate or unknown (un)subscriptions
 * ({@link UsedSubscriberInvocationHandler}), and the surrogate that stands in for a producer that is not yet
 * declared ({@link EventsProxyFactory}).
 */
public class SubscriberInvocationHandlersTests {

	/** Events used by the fixtures. */
	public interface Ping {
		void onPing(String message);
	}

	/** Subscribe interface with a plain, a parameterized and an unsubscribe method. */
	public interface PingSubscribe {
		void subscribe(Ping consumer);

		void subscribe(Ping consumer, String label);

		void unsubscribe(Ping consumer);
	}

	/** Subscribe interface with a method that is neither a subscribe nor an unsubscribe one. */
	public interface OddSubscribe {
		void subscribe(Ping consumer);

		void unsubscribe(Ping consumer);

		void somethingElse();
	}

	/** Subscribe interface that cannot receive a consumer. */
	public interface ConsumerlessSubscribe {
		void subscribe(String label);

		void unsubscribe(String label);
	}

	private static final class RecordingSubscribe implements PingSubscribe {
		final List<String> calls = new ArrayList<>();

		final List<Ping> subscribed = new ArrayList<>();

		@Override
		public void subscribe(final Ping consumer) {
			this.calls.add("subscribe");
			this.subscribed.add(consumer);
		}

		@Override
		public void subscribe(final Ping consumer, final String label) {
			this.calls.add("subscribe:" + label);
			this.subscribed.add(consumer);
		}

		@Override
		public void unsubscribe(final Ping consumer) {
			this.calls.add("unsubscribe");
			this.subscribed.add(consumer);
		}
	}

	private static final class RecordingPing implements Ping {
		final List<String> received = new ArrayList<>();

		@Override
		public void onPing(final String message) {
			this.received.add(message);
		}
	}

	private static final class Introspected<S> extends AbstractSubscriberInvocationHandler<S> {
		Introspected(final Class<S> subscribeInterface) {
			super(subscribeInterface);
			this.introspectSubscribeInterface();
		}

		@Override
		public Object invokeMethod(final Object proxy, final Method method, final Object[] args) {
			return null;
		}

		Method find(final List<Class<?>> argTypes, final boolean subscribe) {
			return this.findSubscriberMethod(argTypes, subscribe);
		}
	}

	private final List<String> dynamicViolations = new ArrayList<>();

	private EventsProxyFactory factory;

	private static ValidationServices validationRecording(final List<String> dynamicViolations) {
		return (ValidationServices) Proxy.newProxyInstance(ValidationServices.class.getClassLoader(),
				new Class<?>[] { ValidationServices.class }, (proxy, method, args) -> {
					if (method.getName().equals("handleDynamicVerification")) {
						dynamicViolations.add((String) args[0]);
					}
					return null;
				});
	}

	private CapturingLogHandler abstractSubscriberInvocationHandlerLogs;

	@Before
	public void setUp() {
		this.abstractSubscriberInvocationHandlerLogs = CapturingLogHandler.install(AbstractSubscriberInvocationHandler.class);
		this.factory = new EventsProxyFactory();
		this.factory.setValidationServices(validationRecording(this.dynamicViolations));
	}

	@After
	public void restoreTheLogs() {
		this.abstractSubscriberInvocationHandlerLogs.restore();
	}

	@SuppressWarnings("unchecked")
	private static <S> UsedSubscriberInvocationHandler<Ping, S> handlerOf(final S usedProxy) {
		return (UsedSubscriberInvocationHandler<Ping, S>) Proxy.getInvocationHandler(usedProxy);
	}

	// ---------------------------------------------------------------------------------------------
	// AbstractSubscriberInvocationHandler
	// ---------------------------------------------------------------------------------------------

	@Test
	public void methodsAreClassifiedByTheirSubscribeOrUnsubscribePrefix() throws Exception {
		Introspected<PingSubscribe> handler = new Introspected<>(PingSubscribe.class);

		assertEquals(2, handler.subscribeMethods.size());
		assertEquals(1, handler.unsubscribeMethods.size());
		assertTrue(handler.unsubscribeMethods.contains(PingSubscribe.class.getMethod("unsubscribe", Ping.class)));
	}

	@Test
	public void methodsMatchingNeitherPrefixAreIgnored() {
		Introspected<OddSubscribe> handler = new Introspected<>(OddSubscribe.class);

		assertEquals(1, handler.subscribeMethods.size());
		assertEquals(1, handler.unsubscribeMethods.size());
		assertEquals(List.of("Unrecognized method in subscribe interface: somethingElse"),
				this.abstractSubscriberInvocationHandlerLogs.messages(Level.WARNING));
	}

	@Test
	public void delegateIsResolvedFromArgumentTypes() throws Exception {
		Introspected<PingSubscribe> handler = new Introspected<>(PingSubscribe.class);

		assertEquals(PingSubscribe.class.getMethod("subscribe", Ping.class),
				handler.find(List.of(RecordingPing.class), true));
		assertEquals(PingSubscribe.class.getMethod("subscribe", Ping.class, String.class),
				handler.find(List.of(RecordingPing.class, String.class), true));
		assertEquals(PingSubscribe.class.getMethod("unsubscribe", Ping.class),
				handler.find(List.of(RecordingPing.class), false));
	}

	@Test
	public void aNullArgumentTypeMatchesAnyParameterType() throws Exception {
		Introspected<PingSubscribe> handler = new Introspected<>(PingSubscribe.class);
		List<Class<?>> argTypes = new ArrayList<>();
		argTypes.add(RecordingPing.class);
		argTypes.add(null);

		assertEquals(PingSubscribe.class.getMethod("subscribe", Ping.class, String.class),
				handler.find(argTypes, true));
	}

	@Test
	public void noMatchingDelegateIsReportedWithTheArgumentTypes() {
		Introspected<PingSubscribe> handler = new Introspected<>(PingSubscribe.class);

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> handler.find(List.of(String.class), true));
		assertEquals("No subscribe method found in PingSubscribe with arg: " + String.class + " ", failure.getMessage());
		failure = assertThrows(IllegalStateException.class, () -> handler.find(List.of(String.class, String.class), false));
		assertTrue(failure.getMessage().startsWith("No unsubscribe method found in PingSubscribe"));
	}

	@Test
	public void argTypesAreTheRuntimeClassesOfTheArguments() {
		Introspected<PingSubscribe> handler = new Introspected<>(PingSubscribe.class);

		assertEquals(List.of(RecordingPing.class, String.class),
				handler.getArgTypes(new Object[] { new RecordingPing(), "label" }));
		assertTrue(handler.getArgTypes(new Object[0]).isEmpty());
	}

	@Test
	public void aNullArgumentIsKeptAsANullTypeEntry() {
		Introspected<PingSubscribe> handler = new Introspected<>(PingSubscribe.class);

		List<Class<?>> argTypes = handler.getArgTypes(new Object[] { new RecordingPing(), null });

		assertEquals(2, argTypes.size());
		assertEquals(RecordingPing.class, argTypes.get(0));
		assertNull(argTypes.get(1));
	}

	// ---------------------------------------------------------------------------------------------
	// UsedSubscriberInvocationHandler
	// ---------------------------------------------------------------------------------------------

	@Test
	public void subscribeHandsTheProducerAConsumerSideProxyOfTheConsumer() {
		RecordingSubscribe producer = new RecordingSubscribe();
		PingSubscribe used = this.factory.newUsedSubscriberProxy(Ping.class, PingSubscribe.class, producer);
		RecordingPing consumer = new RecordingPing();

		used.subscribe(consumer);

		assertEquals(List.of("subscribe"), producer.calls);
		Ping seenByProducer = producer.subscribed.get(0);
		assertNotSame(consumer, seenByProducer);
		assertTrue(Proxy.isProxyClass(seenByProducer.getClass()));
		seenByProducer.onPing("hello");
		assertEquals(List.of("hello"), consumer.received);
	}

	@Test
	public void subscribeKeepsTheOtherArgumentsAndIdentifiesTheConsumerByType() {
		RecordingSubscribe producer = new RecordingSubscribe();
		PingSubscribe used = this.factory.newUsedSubscriberProxy(Ping.class, PingSubscribe.class, producer);

		used.subscribe(new RecordingPing(), "label");

		assertEquals(List.of("subscribe:label"), producer.calls);
	}

	@Test
	public void subscribingTheSameConsumerTwiceIsReportedAndForwardedOnce() {
		RecordingSubscribe producer = new RecordingSubscribe();
		PingSubscribe used = this.factory.newUsedSubscriberProxy(Ping.class, PingSubscribe.class, producer);
		RecordingPing consumer = new RecordingPing();

		used.subscribe(consumer);
		used.subscribe(consumer);

		assertEquals(List.of("subscribe"), producer.calls);
		assertEquals(1, this.dynamicViolations.size());
		assertTrue(this.dynamicViolations.get(0).contains("has already subscribed to events:" + Ping.class));
	}

	@Test
	public void theSameConsumerMayRegisterSeveralTimesWithDifferentArguments() {
		RecordingSubscribe producer = new RecordingSubscribe();
		PingSubscribe used = this.factory.newUsedSubscriberProxy(Ping.class, PingSubscribe.class, producer);
		RecordingPing consumer = new RecordingPing();

		used.subscribe(consumer, "a");
		used.subscribe(consumer, "b");

		assertEquals(List.of("subscribe:a", "subscribe:b"), producer.calls);
		assertTrue(this.dynamicViolations.isEmpty());
		assertNotSame(producer.subscribed.get(0), producer.subscribed.get(1));
	}

	@Test
	public void unsubscribeHandsTheProducerTheProxyItWasGivenAtSubscribe() {
		RecordingSubscribe producer = new RecordingSubscribe();
		PingSubscribe used = this.factory.newUsedSubscriberProxy(Ping.class, PingSubscribe.class, producer);
		RecordingPing consumer = new RecordingPing();
		used.subscribe(consumer);

		used.unsubscribe(consumer);

		assertEquals(List.of("subscribe", "unsubscribe"), producer.calls);
		assertSame(producer.subscribed.get(0), producer.subscribed.get(1));
		assertTrue(this.dynamicViolations.isEmpty());
	}

	@Test
	public void unsubscribingAConsumerThatIsNotSubscribedIsReportedAndNotForwarded() {
		RecordingSubscribe producer = new RecordingSubscribe();
		PingSubscribe used = this.factory.newUsedSubscriberProxy(Ping.class, PingSubscribe.class, producer);
		RecordingPing consumer = new RecordingPing();

		used.unsubscribe(consumer);
		used.subscribe(consumer);
		used.unsubscribe(consumer);
		used.unsubscribe(consumer);

		assertEquals(List.of("subscribe", "unsubscribe"), producer.calls);
		assertEquals(2, this.dynamicViolations.size());
		assertTrue(this.dynamicViolations.get(0).contains("is not subscribed to events:" + Ping.class));
	}

	@Test
	public void anUnsubscribedConsumerIsNoLongerRetained() throws Exception {
		PingSubscribe producer = new PingSubscribe() {
			@Override
			public void subscribe(final Ping consumer) {
			}

			@Override
			public void subscribe(final Ping consumer, final String label) {
			}

			@Override
			public void unsubscribe(final Ping consumer) {
			}
		};
		PingSubscribe used = this.factory.newUsedSubscriberProxy(Ping.class, PingSubscribe.class, producer);

		WeakReference<Ping> consumer = subscribeThenUnsubscribeANewConsumer(used);

		for (int attempt = 0; attempt < 50 && consumer.get() != null; attempt++) {
			System.gc();
			Thread.sleep(20);
		}
		assertNull("the handler still retains an unsubscribed consumer", consumer.get());
	}

	private static WeakReference<Ping> subscribeThenUnsubscribeANewConsumer(final PingSubscribe used) {
		RecordingPing consumer = new RecordingPing();
		used.subscribe(consumer);
		used.unsubscribe(consumer);
		return new WeakReference<>(consumer);
	}

	@Test
	public void aSubscribeFailingAtTheProducerIsNotRememberedAndCanBeRetried() {
		List<String> calls = new ArrayList<>();
		PingSubscribe failingOnce = new PingSubscribe() {
			@Override
			public void subscribe(final Ping consumer) {
				calls.add("subscribe");
				if (calls.size() == 1) {
					throw new IllegalStateException("producer refuses");
				}
			}

			@Override
			public void subscribe(final Ping consumer, final String label) {
			}

			@Override
			public void unsubscribe(final Ping consumer) {
			}
		};
		PingSubscribe used = this.factory.newUsedSubscriberProxy(Ping.class, PingSubscribe.class, failingOnce);
		RecordingPing consumer = new RecordingPing();

		assertThrows(RuntimeException.class, () -> used.subscribe(consumer));
		used.subscribe(consumer);

		assertEquals(List.of("subscribe", "subscribe"), calls);
		assertTrue(this.dynamicViolations.toString(), this.dynamicViolations.isEmpty());
	}

	@Test
	public void directSubscribeAndUnsubscribeResolveTheDelegateFromTheArguments() throws Throwable {
		RecordingSubscribe producer = new RecordingSubscribe();
		PingSubscribe used = this.factory.newUsedSubscriberProxy(Ping.class, PingSubscribe.class, producer);
		UsedSubscriberInvocationHandler<Ping, PingSubscribe> handler = handlerOf(used);
		RecordingPing consumer = new RecordingPing();

		handler.subscribe(consumer, null);
		handler.unsubscribe(consumer);

		assertEquals(List.of("subscribe", "unsubscribe"), producer.calls);
	}

	@Test
	public void directCallsAreRefusedOnceTheHandlerIsInvalidated() {
		PingSubscribe used = this.factory.newUsedSubscriberProxy(Ping.class, PingSubscribe.class,
				new RecordingSubscribe());
		UsedSubscriberInvocationHandler<Ping, PingSubscribe> handler = handlerOf(used);
		handler.invalidate();

		assertThrows(IllegalStateException.class, () -> handler.subscribe(new RecordingPing(), null));
		assertThrows(IllegalStateException.class, () -> handler.unsubscribe(new RecordingPing()));
	}

	@Test
	public void theSubscribingComponentTypeIsRecordedOnTheConsumerSideProxy() {
		RecordingSubscribe producer = new RecordingSubscribe();
		PingSubscribe used = this.factory.newUsedSubscriberProxy(Ping.class, PingSubscribe.class, producer);
		handlerOf(used).setSubscribingComponentType(String.class);

		used.subscribe(new RecordingPing());

		ConsumerInvocationHandler<?> consumerHandler = (ConsumerInvocationHandler<?>) Proxy
				.getInvocationHandler(producer.subscribed.get(0));
		assertEquals(String.class, consumerHandler.getSubscribingComponentType());
	}

	@Test
	public void callsOnMethodsThatAreNeitherSubscribeNorUnsubscribeAreIgnored() {
		OddSubscribe odd = this.factory.newUsedSubscriberProxy(Ping.class, OddSubscribe.class, new OddSubscribe() {
			@Override
			public void subscribe(final Ping consumer) {
				throw new AssertionError("must not be called");
			}

			@Override
			public void unsubscribe(final Ping consumer) {
				throw new AssertionError("must not be called");
			}

			@Override
			public void somethingElse() {
				throw new AssertionError("must not be called");
			}
		});

		odd.somethingElse();
		assertEquals(List.of("Unrecognized method in subscribe interface: somethingElse"),
				this.abstractSubscriberInvocationHandlerLogs.messages(Level.WARNING));
	}

	@Test
	public void aSubscribeCallWithoutAConsumerArgumentIsRejected() {
		ConsumerlessSubscribe used = this.factory.newUsedSubscriberProxy(Ping.class, ConsumerlessSubscribe.class,
				new ConsumerlessSubscribe() {
					@Override
					public void subscribe(final String label) {
						throw new AssertionError("must not be called");
					}

					@Override
					public void unsubscribe(final String label) {
						throw new AssertionError("must not be called");
					}
				});

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> used.subscribe("x"));
		assertEquals("consumer not found", failure.getMessage());
		failure = assertThrows(IllegalArgumentException.class, () -> used.unsubscribe("x"));
		assertEquals("consumer not found", failure.getMessage());
	}

	@Test
	public void theProvidedSubscriberCanBeReplacedButNotByNull() {
		RecordingSubscribe first = new RecordingSubscribe();
		RecordingSubscribe second = new RecordingSubscribe();
		PingSubscribe used = this.factory.newUsedSubscriberProxy(Ping.class, PingSubscribe.class, first);
		UsedSubscriberInvocationHandler<Ping, PingSubscribe> handler = handlerOf(used);

		assertThrows(RuntimeException.class, () -> handler.replaceProvidedSubscriber(null));
		handler.replaceProvidedSubscriber(second);
		used.subscribe(new RecordingPing());

		assertTrue(first.calls.isEmpty());
		assertEquals(List.of("subscribe"), second.calls);
	}

	@Test
	public void aUsedSubscriberCannotBeCreatedWithoutAProvidedSubscriber() {
		assertThrows(RuntimeException.class,
				() -> this.factory.newUsedSubscriberProxy(Ping.class, PingSubscribe.class, null));
		assertThrows(RuntimeException.class,
				() -> this.factory.newUsedSubscriberProxy(null, PingSubscribe.class, new RecordingSubscribe()));
		assertThrows(RuntimeException.class,
				() -> this.factory.newUsedSubscriberProxy(Ping.class, null, new RecordingSubscribe()));
	}

	@Test
	public void consumerProxiesForwardEventsToTheConsumerAndRejectNullConsumers() {
		RecordingPing consumer = new RecordingPing();
		Ping proxy = this.factory.newConsumerProxy(Ping.class, consumer);

		proxy.onPing("a");

		assertEquals(List.of("a"), consumer.received);
		assertThrows(RuntimeException.class, () -> this.factory.newConsumerProxy(Ping.class, null));
	}

	// ---------------------------------------------------------------------------------------------
	// Surrogate
	// ---------------------------------------------------------------------------------------------

	private NamingServices namingReturning(final Object subscriber, final boolean bound, final int[] lookups) {
		return (NamingServices) Proxy.newProxyInstance(NamingServices.class.getClassLoader(),
				new Class<?>[] { NamingServices.class }, (proxy, method, args) -> {
					if (method.getName().equals("lookupSubscriber")) {
						lookups[0]++;
						if (!bound) {
							throw new NotBoundException("directory internal text");
						}
						return subscriber;
					}
					return null;
				});
	}

	@SuppressWarnings("unchecked")
	private EventsProxyFactory.UsedSubscriberSurrogateProxyInvocationHandler<Ping, PingSubscribe> surrogateHandler(
			final PingSubscribe surrogate) {
		return (EventsProxyFactory.UsedSubscriberSurrogateProxyInvocationHandler<Ping, PingSubscribe>) Proxy
				.getInvocationHandler(surrogate);
	}

	@Test
	public void surrogateSwapsInTheRealProducerOnFirstUseAndForwardsTheCall() {
		RecordingSubscribe real = new RecordingSubscribe();
		int[] lookups = new int[1];
		PingSubscribe surrogate = this.factory.newSurrogateProxy(Ping.class, PingSubscribe.class,
				this.namingReturning(real, true, lookups));
		PingSubscribe used = this.factory.newUsedSubscriberProxy(Ping.class, PingSubscribe.class, surrogate);
		this.surrogateHandler(surrogate).setUserSideInvocationHandler(handlerOf(used));
		RecordingPing consumer = new RecordingPing();

		used.subscribe(consumer);
		used.unsubscribe(consumer);

		assertEquals(1, lookups[0]);
		assertEquals(List.of("subscribe", "unsubscribe"), real.calls);
	}

	@Test
	public void surrogateExplainsThatNoProducerIsDeclaredYet() {
		int[] lookups = new int[1];
		PingSubscribe surrogate = this.factory.newSurrogateProxy(Ping.class, PingSubscribe.class,
				this.namingReturning(null, false, lookups));
		PingSubscribe used = this.factory.newUsedSubscriberProxy(Ping.class, PingSubscribe.class, surrogate);
		this.surrogateHandler(surrogate).setUserSideInvocationHandler(handlerOf(used));

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> used.subscribe(new RecordingPing()));

		assertEquals("No producer is currently declared for the events interface Ping.", failure.getMessage());
		assertTrue(failure.getCause() instanceof NotBoundException);
	}

	@Test
	public void surrogateRequiresTheUserSideHandlerToBeSet() {
		PingSubscribe surrogate = this.factory.newSurrogateProxy(Ping.class, PingSubscribe.class,
				this.namingReturning(new RecordingSubscribe(), true, new int[1]));

		assertThrows(RuntimeException.class, () -> surrogate.subscribe(new RecordingPing()));
	}
}
