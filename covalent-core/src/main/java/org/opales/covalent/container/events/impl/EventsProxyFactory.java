/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.events.impl;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.opales.covalent.container.component.ComponentAnnotations;
import org.opales.covalent.container.directory.NamingServices;
import org.opales.covalent.container.events.EventsManagerEvents;
import org.opales.covalent.container.exceptions.NotBoundException;
import org.opales.covalent.container.invocation.CovalentInvocationHandler;
import org.opales.covalent.container.metamodel.MetamodelPortReadServices;
import org.opales.covalent.container.metamodel.MetamodelReadServices;
import org.opales.covalent.container.util.RuntimeCheck;
import org.opales.covalent.container.validation.ValidationParameters;
import org.opales.covalent.container.validation.ValidationServices;

/**
 * Builds and wires the dynamic proxies {@link EventsManagerImpl} uses to
 * represent producers, consumers and subscribe delegates: producer-side
 * notifier proxies (which both fire events and accept subscribe/unsubscribe
 * calls), consumer-side used-subscribe proxies, plain consumer wrapper
 * proxies for parked subscriptions, and surrogate subscribe proxies used
 * while no real provider is published yet. Collaborators (validation,
 * metamodel, dispatch workers, instrumentation) are injected once through
 * the {@code set*} methods below and then reused for every proxy this
 * factory creates.
 */
public class EventsProxyFactory {
	private static Logger log = Logger.getLogger(EventsProxyFactory.class.getName());

	private ValidationServices validationServices;

	private ValidationParameters validationParameters;

	private MetamodelPortReadServices MetamodelPortReadServices;

	private MetamodelReadServices metamodelReadServices;

	private Executor synchronousWorker;

	private Executor asynchronousWorker;

	private Supplier<EventsManagerEvents> eventsManagerEventsSupplier;

	private BooleanSupplier instrumentationSupplier;

	private IntSupplier subscriptionBufferCapacity;

	private Runnable bufferedEventDropped;

	private volatile boolean synchronous;

	/** Creates a new factory; collaborators are injected via the {@code set*} methods. */
	public EventsProxyFactory() {
	}

	/**
	 * Sets the service used to validate events interfaces and method
	 * naming conventions, propagated to every notifier and used-subscribe
	 * proxy this factory creates.
	 *
	 * @param validationServices the validation service
	 */
	public void setValidationServices(final ValidationServices validationServices) {
		this.validationServices = validationServices;
	}

	/**
	 * Sets the parameters controlling how strictly validation is applied,
	 * propagated to every notifier proxy this factory creates.
	 *
	 * @param validationParameters the validation parameters
	 */
	public void setValidationParameters(final ValidationParameters validationParameters) {
		this.validationParameters = validationParameters;
	}

	/**
	 * Sets the service used to read a component's declared ports from the
	 * metamodel, propagated to every notifier proxy this factory creates.
	 *
	 * @param MetamodelPortReadServices the metamodel port read service
	 */
	public void setMetamodelPortReadServices(final MetamodelPortReadServices MetamodelPortReadServices) {
		this.MetamodelPortReadServices = MetamodelPortReadServices;
	}

	/**
	 * Sets the service used to read metamodel information not scoped to a
	 * single port, propagated to every notifier proxy this factory creates.
	 *
	 * @param metamodelReadServices the metamodel read service
	 */
	public void setMetamodelReadServices(final MetamodelReadServices metamodelReadServices) {
		this.metamodelReadServices = metamodelReadServices;
	}

	/**
	 * Sets the executor used to dispatch notifications for internal
	 * components, and for every producer when synchronous dispatch is in
	 * effect. See {@link #getWorker(Class, Class)} for the full routing
	 * rule.
	 *
	 * @param worker the synchronous dispatch executor
	 */
	public void setSynchronousWorker(final Executor worker) {
		this.synchronousWorker = worker;
	}

	/**
	 * Sets the executor used to dispatch notifications for business and
	 * raw producers when synchronous dispatch is not in effect. See
	 * {@link #getWorker(Class, Class)} for the full routing rule.
	 *
	 * @param worker the asynchronous dispatch executor
	 */
	public void setAsynchronousWorker(final Executor worker) {
		this.asynchronousWorker = worker;
	}

	/**
	 * Sets whether every producer this factory creates after the call
	 * dispatches synchronously, unless it is an internal component (which
	 * is always synchronous regardless of this flag).
	 *
	 * @param synchronous {@code true} to dispatch synchronously by default
	 */
	public void setSynchronous(final boolean synchronous) {
		this.synchronous = synchronous;
	}

	/**
	 * Sets the supplier used to obtain the internal
	 * {@code EventsManagerEvents} producer, so notifier proxies can report
	 * produced/consumed events management notifications.
	 *
	 * @param supplier supplies the internal events manager events producer
	 */
	void setProducedEventsManagerEvents(final Supplier<EventsManagerEvents> supplier) {
		this.eventsManagerEventsSupplier = supplier;
	}

	/**
	 * Sets, for the notifier proxies this factory creates, how many events a
	 * subscription buffers at most while its subscribe delegate notification
	 * is pending, and how an event dropped because that buffer is full is
	 * reported.
	 *
	 * @param capacity             supplies the buffer capacity
	 * @param bufferedEventDropped reports an event dropped because a buffer was full
	 */
	void setSubscriptionBuffer(final IntSupplier capacity, final Runnable bufferedEventDropped) {
		this.subscriptionBufferCapacity = capacity;
		this.bufferedEventDropped = bufferedEventDropped;
	}

	/**
	 * Sets the supplier that tells notifier proxies, at dispatch time,
	 * whether instrumentation (before/after notification reporting) is
	 * currently enabled.
	 *
	 * @param instrumentationSupplier supplies whether instrumentation is
	 *                                currently enabled
	 */
	public void setInstrumentationSupplier(final BooleanSupplier instrumentationSupplier) {
		this.instrumentationSupplier = instrumentationSupplier;
	}

	/**
	 * Re-resolves and applies the dispatch worker of an existing notifier
	 * proxy, for example after a component that changes whether it is
	 * treated as internal is re-registered under the same events
	 * interface.
	 *
	 * @param <EVENT>         the events interface type
	 * @param <SUBSCRIBE>     the subscribe interface type associated with
	 *                        the events interface
	 * @param notifier        the existing notifier proxy to refresh
	 * @param componentType   the producer component type
	 * @param eventsInterface the events interface produced by the
	 *                        component
	 */
	<EVENT, SUBSCRIBE> void refreshNotifierWorker(final SUBSCRIBE notifier, final Class<?> componentType,
			final Class<EVENT> eventsInterface) {
		@SuppressWarnings("unchecked")
		ProducerSideNotifierInvocationHandler<EVENT, SUBSCRIBE> invocationHandler = (ProducerSideNotifierInvocationHandler<EVENT, SUBSCRIBE>) Proxy
				.getInvocationHandler(notifier);
		Executor worker = this.getWorker(componentType, eventsInterface);
		invocationHandler.setWorker(worker, worker != this.synchronousWorker);
	}

	/**
	 * Creates the consumer-side used-subscribe proxy: the object a consumer
	 * calls {@code subscribe}/{@code unsubscribe} on, which delegates to
	 * the given producer-side subscriber (either the real provider-side
	 * notifier, or a surrogate while none is published yet).
	 *
	 * @param <EVENTS>              the events interface type
	 * @param <SUBSCRIBE>           the subscribe interface type associated
	 *                              with the events interface
	 * @param eventsInterface       the events interface being subscribed to
	 * @param subscribeInterface    the subscribe interface implemented by
	 *                              the returned proxy
	 * @param producerSideSubscriber the provider-side (or surrogate)
	 *                              subscriber the proxy delegates to
	 * @return the consumer-side used-subscribe proxy
	 */
	public <EVENTS, SUBSCRIBE> SUBSCRIBE newUsedSubscriberProxy(final Class<EVENTS> eventsInterface,
			final Class<SUBSCRIBE> subscribeInterface, final SUBSCRIBE producerSideSubscriber) {
		RuntimeCheck.notNull(eventsInterface, "eventsInterface");
		RuntimeCheck.notNull(subscribeInterface, "subscribeInterface");
		UsedSubscriberInvocationHandler<EVENTS, SUBSCRIBE> consumerSideSubscriberInvocationHandler = new UsedSubscriberInvocationHandler<>(
				eventsInterface, subscribeInterface, producerSideSubscriber);

		consumerSideSubscriberInvocationHandler.setEventsProxyFactory(this);
		consumerSideSubscriberInvocationHandler.setValidationServices(this.validationServices);

		@SuppressWarnings("unchecked")
		SUBSCRIBE proxy = (SUBSCRIBE) Proxy.newProxyInstance(subscribeInterface.getClassLoader(),
				new Class[] { subscribeInterface }, consumerSideSubscriberInvocationHandler);

		return proxy;
	}

	/**
	 * Creates a plain consumer-side wrapper proxy for a real consumer,
	 * used to represent a subscription that is parked because no
	 * producer-side notifier exists yet for its events interface. The
	 * proxy simply forwards every call to the real consumer; it is stored
	 * as the pending subscriber's instance and attached to the real
	 * notifier once one is created.
	 *
	 * @param <EVENTS>        the events interface type
	 * @param eventsInterface the events interface the consumer implements
	 * @param consumer        the real consumer instance to wrap
	 * @return the wrapper proxy delegating to {@code consumer}
	 */
	public <EVENTS> EVENTS newConsumerProxy(final Class<EVENTS> eventsInterface, final EVENTS consumer) {
		RuntimeCheck.notNull(consumer, "consumer");
		ConsumerInvocationHandler<EVENTS> consumerInvocationHandler = new ConsumerInvocationHandler<>(consumer);
		@SuppressWarnings("unchecked")
		EVENTS proxy = (EVENTS) Proxy.newProxyInstance(eventsInterface.getClassLoader(),
				new Class[] { eventsInterface }, consumerInvocationHandler);

		return proxy;
	}

	/**
	 * <p>
	 * Creates a notifier proxy for the given events and subscribe interfaces. A
	 * notifier proxy implements both the subscribe and the events interface and is
	 * used on the producer side to handle:
	 * </p>
	 * <ul>
	 * <li>events notification</li>
	 * <li>subscribe and unsubscribe of consumers</li>
	 * <li>handle events filter provided in consumer's subscribe parameters</li>
	 * </ul>
	 *
	 * @param <EVENT>                     the events interface type
	 * @param <SUBSCRIBE>                 the subscribe interface type
	 *                                    associated with the events
	 *                                    interface
	 * @param eventsInterface             the events interface produced by
	 *                                    the component
	 * @param subscribeInterface          the subscribe interface
	 *                                    implemented by the returned proxy
	 * @param subscribeDelegate           an existing delegate to forward
	 *                                    subscribe/unsubscribe calls to, or
	 *                                    {@code null} when
	 *                                    {@code subscribeDelegateConsumer}
	 *                                    /{@code unsubscribeDelegateConsumer}
	 *                                    are used instead
	 * @param componentType               the producer component type, used
	 *                                    to pick the dispatch worker (see
	 *                                    {@link #getWorker(Class, Class)})
	 * @param subscribeDelegateConsumer   optional callback invoked when a
	 *                                    consumer subscribes; when
	 *                                    non-{@code null} it is used
	 *                                    instead of {@code subscribeDelegate}
	 * @param unsubscribeDelegateConsumer optional callback invoked when a
	 *                                    consumer unsubscribes; used
	 *                                    together with
	 *                                    {@code subscribeDelegateConsumer}
	 * @return the producer-side notifier proxy implementing both
	 *         {@code eventsInterface} and {@code subscribeInterface}
	 */
	public <EVENT, SUBSCRIBE> SUBSCRIBE newNotifierProxy(final Class<EVENT> eventsInterface,
			final Class<SUBSCRIBE> subscribeInterface, final SUBSCRIBE subscribeDelegate, final Class<?> componentType,
			final Consumer<EVENT> subscribeDelegateConsumer, final Consumer<EVENT> unsubscribeDelegateConsumer) {
		ProducerSideNotifierInvocationHandler<EVENT, SUBSCRIBE> invocationHandler = null;
		if (subscribeDelegateConsumer == null) {
			invocationHandler = new ProducerSideNotifierInvocationHandler<>(eventsInterface, subscribeInterface,
					subscribeDelegate);
		} else {
			invocationHandler = new ProducerSideNotifierInvocationHandler<>(eventsInterface, subscribeDelegateConsumer,
					unsubscribeDelegateConsumer);
		}
		Executor worker = this.getWorker(componentType, eventsInterface);
		invocationHandler.setWorker(worker, worker != this.synchronousWorker);
		invocationHandler.setProducerComponentType(componentType);
		invocationHandler.setInstrumentationSupplier(this.instrumentationSupplier);
		invocationHandler.setValidationServices(this.validationServices);
		invocationHandler.setValidationParameters(this.validationParameters);
		invocationHandler.setMetamodelPortReadServices(this.MetamodelPortReadServices);
		invocationHandler.setMetamodelReadServices(this.metamodelReadServices);
		invocationHandler.setProducedEventsManagerEvents(this.eventsManagerEventsSupplier);
		invocationHandler.setSubscriptionBuffer(this.subscriptionBufferCapacity, this.bufferedEventDropped);

		invocationHandler.init();

		@SuppressWarnings("unchecked")
		SUBSCRIBE proxy = (SUBSCRIBE) Proxy.newProxyInstance(eventsInterface.getClassLoader(),
				new Class[] { eventsInterface, subscribeInterface }, invocationHandler);

		return proxy;
	}

	/**
	 * Picks the worker a producer's notifications are dispatched through.
	 * <p>
	 * Internal-component producers (see {@link ComponentAnnotations#isInternalComponent})
	 * are always routed to the synchronous worker, never to the asynchronous
	 * pool, regardless of {@link #setSynchronous}. This is a deliberate,
	 * supported contract:
	 * <ul>
	 * <li>Internal producers (e.g. {@code LifecycleEvents},
	 * {@code EventsManagerEvents}) are created and activated by
	 * {@code Bootstrapper.bootstrapInternalEvents()} very early during
	 * bootstrap, before {@code EventsManagerImpl.activate()} has even created
	 * the asynchronous worker pool.</li>
	 * <li>Internal events - {@code onComponentPassivated} in particular - are
	 * still fired while that same asynchronous pool is being torn down (on
	 * {@code EventsManagerImpl.passivate()}) or recreated (on
	 * {@code EventsManagerImpl.activate()}) across a container
	 * {@code stop()}/{@code start()} cycle. Routing internal events through
	 * the pool whose own lifecycle they can be reporting on would race that
	 * teardown/recreation.</li>
	 * </ul>
	 * Business and raw producers keep using the asynchronous pool by default,
	 * unless synchronous dispatch was requested for the whole container.
	 */
	private Executor getWorker(final Class<?> componentType, final Class<?> eventsInterface) {
		// Only the component type and the container's synchronous setting select the
		// worker; the events interface is not consulted.
		Executor worker = null;
		if (ComponentAnnotations.isInternalComponent(componentType) || (this.synchronous)) {
			worker = this.synchronousWorker;
		} else {
			worker = this.asynchronousWorker;
		}
		return worker;
	}

	/**
	 * Creates a surrogate subscribe proxy, used as a stand-in provider-side
	 * subscriber while no producer for {@code eventsInterface} is
	 * published in the directory yet. The first call made through it
	 * looks up the real provider, replaces itself in the associated
	 * used-subscribe proxy, and forwards that first call to the real
	 * provider.
	 *
	 * @param <EVENTS>           the events interface type
	 * @param <SUBSCRIBE>        the subscribe interface type associated
	 *                           with the events interface
	 * @param eventsInterface    the events interface to eventually look up
	 *                           a provider for
	 * @param subscribeInterface the subscribe interface implemented by the
	 *                           returned proxy
	 * @param namingServices     the directory service used to look up the
	 *                           real provider once one is published
	 * @return the surrogate subscribe proxy
	 */
	public <EVENTS, SUBSCRIBE> SUBSCRIBE newSurrogateProxy(final Class<EVENTS> eventsInterface,
			final Class<SUBSCRIBE> subscribeInterface, final NamingServices namingServices) {
		UsedSubscriberSurrogateProxyInvocationHandler<EVENTS, SUBSCRIBE> surrogateInvocationHandeler = new UsedSubscriberSurrogateProxyInvocationHandler<>(
				eventsInterface, namingServices);

		@SuppressWarnings("unchecked")
		SUBSCRIBE proxy = (SUBSCRIBE) Proxy.newProxyInstance(subscribeInterface.getClassLoader(),
				new Class[] { subscribeInterface }, surrogateInvocationHandeler);
		return proxy;
	}

	/**
	 * Invocation handler backing the surrogate subscribe proxy returned by
	 * {@link #newSurrogateProxy}. Every call made through it, before a real
	 * provider exists, looks up the provider in the directory, replaces
	 * itself with it in the associated {@link UsedSubscriberInvocationHandler},
	 * and forwards the call to the real provider.
	 *
	 * @param <EVENTS>    the events interface type
	 * @param <SUBSCRIBE> the subscribe interface type associated with the
	 *                    events interface
	 */
	class UsedSubscriberSurrogateProxyInvocationHandler<EVENTS, SUBSCRIBE> extends CovalentInvocationHandler {
		/**
		 * Creates a new surrogate handler for the given events interface.
		 *
		 * @param eventsInterface the events interface to eventually look up
		 *                        a provider for
		 * @param namingServices  the directory service used to look up the
		 *                        real provider once one is published
		 */
		public UsedSubscriberSurrogateProxyInvocationHandler(final Class<EVENTS> eventsInterface,
				final NamingServices namingServices) {
			this.eventsInterface = eventsInterface;
			this.namingServices = namingServices;
		}

		/**
		 * Looks up the real provider-side subscriber, replaces this
		 * surrogate with it in the associated used-subscribe invocation
		 * handler, and forwards this call to the real provider.
		 *
		 * @param proxy  the surrogate proxy instance
		 * @param method the subscribe/unsubscribe method being called
		 * @param args   the call's arguments
		 * @return the real provider's return value for this call
		 * @throws Throwable whatever the real provider's invocation throws,
		 *                   or {@link IllegalStateException} if no provider
		 *                   is currently bound
		 */
		@Override
		public Object invokeMethod(final Object proxy, final Method method, final Object[] args) throws Throwable {
			RuntimeCheck.notNull(this.usedSubscriberInvocationHandler, "usedSubscriberInvocationHandler");
			// try lookup
			SUBSCRIBE providedSubscriber;
			try {
				providedSubscriber = this.namingServices.lookupSubscriber(this.eventsInterface);
			} catch (NotBoundException e) {
				// NotBoundException's own message is directory-internal jargon;
				// this is the exception a caller actually sees when invoking a
				// used subscriber method too early, so it must be
				// understandable without knowing what "bound in directory" means.
				throw new IllegalStateException("No producer is currently declared for the events interface "
						+ this.eventsInterface.getSimpleName() + ".", e);
			}
			log.fine("Replacing surrogate with real services: " + this.eventsInterface.getSimpleName());
			this.usedSubscriberInvocationHandler.replaceProvidedSubscriber(providedSubscriber);
			log.fine("invoking service on genuine proxy...");
			return method.invoke(providedSubscriber, args);
		}

		/**
		 * Sets the consumer-side used-subscribe invocation handler this
		 * surrogate replaces itself into once the real provider is found.
		 *
		 * @param usedSubscriberInvocationHandler the used-subscribe
		 *                                        invocation handler to
		 *                                        update
		 */
		public void setUserSideInvocationHandler(
				final UsedSubscriberInvocationHandler<EVENTS, SUBSCRIBE> usedSubscriberInvocationHandler) {
			this.usedSubscriberInvocationHandler = usedSubscriberInvocationHandler;
		}

		/** The used-subscribe invocation handler this surrogate replaces itself into. */
		public UsedSubscriberInvocationHandler<EVENTS, SUBSCRIBE> usedSubscriberInvocationHandler;

		/** The directory service used to look up the real provider. */
		private final NamingServices namingServices;

		/** The events interface to eventually look up a provider for. */
		private final Class<EVENTS> eventsInterface;

	}

}
