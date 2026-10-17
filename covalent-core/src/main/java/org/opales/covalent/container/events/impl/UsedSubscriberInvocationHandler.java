/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.events.impl;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opales.covalent.container.events.SubscribeParameters;
import org.opales.covalent.container.util.RuntimeCheck;
import org.opales.covalent.container.validation.ValidationServices;

/**
 * Proxy used on consumer side, when consumer wants to subscribe manually.
 *
 * @param <EVENTS>    the events interface the consumer subscribes to
 * @param <SUBSCRIBE> the producer's events subscribe interface used to
 *                    (un)subscribe
 */
public class UsedSubscriberInvocationHandler<EVENTS, SUBSCRIBE> extends AbstractSubscriberInvocationHandler<SUBSCRIBE> {

	/** Reports dynamic (structural) verification failures, e.g. a duplicate or unknown subscription. */
	private ValidationServices validationServices;

	/** Creates the consumer-side proxies substituted for the original consumer when subscribing. */
	private EventsProxyFactory eventsProxyFactory;

	/** The events interface this handler subscribes to. */
	private final Class<EVENTS> eventsInterface;

	/**
	 * The component owning the {@code @UsedEventsSubscribe} port this
	 * handler backs, or {@code null} when it backs no such port. Recorded on
	 * every consumer subscribed through it, so instrumentation can name that
	 * component even when the subscribed object is not the component itself.
	 */
	private Class<?> subscribingComponentType;

	/** The producer's current subscribe delegate instance. */
	private SUBSCRIBE providedSubscriber;

	/**
	 * For each original consumer, maps its remaining subscribe arguments
	 * (see {@link #getRemainingArgs}) to the consumer-side proxy registered
	 * with the producer for that particular subscription, so the matching
	 * proxy can be found back on unsubscribe.
	 */
	private final Map<EVENTS, Map<List<Object>, EVENTS>> consumerToProxyNew = new HashMap<>();

	/**
	 * Guards {@link #consumerToProxyNew}: raw ports are called from any thread.
	 * A consumer with no remaining subscription has no entry in the map.
	 */
	private final Object subscriptionsLock = new Object();

	/**
	 * Creates a new handler for manual subscription to the given events
	 * interface, through the given provider's subscribe interface.
	 *
	 * @param eventsInterface    the events interface to subscribe to
	 * @param subscribeInterface the producer's events subscribe interface
	 * @param providedSubscriber the producer's subscribe delegate instance;
	 *                           must not be {@code null}
	 */
	public UsedSubscriberInvocationHandler(final Class<EVENTS> eventsInterface,
			final Class<SUBSCRIBE> subscribeInterface, final SUBSCRIBE providedSubscriber) {
		super(subscribeInterface);
		this.eventsInterface = eventsInterface;
		RuntimeCheck.notNull(providedSubscriber, "providedSubscriber");
		this.providedSubscriber = providedSubscriber;
		this.introspectSubscribeInterface();
	}

	/**
	 * Sets the internal service used for dynamic verification of usage
	 * rules.
	 *
	 * @param validationServices the validation service
	 */
	public void setValidationServices(final ValidationServices validationServices) {
		this.validationServices = validationServices;
	}

	/**
	 * Sets the component owning the {@code @UsedEventsSubscribe} port this
	 * handler backs - see {@link #subscribingComponentType}.
	 *
	 * @param subscribingComponentType the owning component type
	 */
	public void setSubscribingComponentType(final Class<?> subscribingComponentType) {
		this.subscribingComponentType = subscribingComponentType;
	}

	/**
	 * Sets the collaborating proxy factory used to create consumer-side
	 * proxies.
	 *
	 * @param eventsProxyFactory the events proxy factory
	 */
	public void setEventsProxyFactory(final EventsProxyFactory eventsProxyFactory) {
		this.eventsProxyFactory = eventsProxyFactory;
	}

	/**
	 * Dispatches a call on the manual subscribe proxy: forwards a subscribe
	 * or unsubscribe method call to the producer's subscribe delegate,
	 * substituting the original consumer with the consumer-side proxy
	 * registered for it.
	 *
	 * @param proxy  the dynamic proxy to which this invocation handler is
	 *               attached
	 * @param method the called method
	 * @param args   the method arguments
	 * @return always {@code null}
	 */
	@Override
	public Object invokeMethod(final Object proxy, final Method method, final Object[] args) throws Throwable {
		if (this.subscribeMethods.contains(method)) {
			this.subscribe(method, args);
		} else if (this.unsubscribeMethods.contains(method)) {
			this.unsubscribe(method, args);
		}

		return null;
	}

	/**
	 * Subscribes the given consumer to the producer's events, with optional
	 * subscribe parameters. Called from manual subscribe of raw components
	 * and from automatic subscription.
	 *
	 * @param consumer            the consumer to subscribe
	 * @param subscribeParameters the subscribe parameters to associate with
	 *                            the subscription, or {@code null} for a
	 *                            plain subscription
	 * @throws Throwable whatever the underlying subscribe delegate call throws
	 */
	public void subscribe(final EVENTS consumer, final SubscribeParameters subscribeParameters) throws Throwable {
		this.checkInvalidation();
		Object[] args = null;
		if (subscribeParameters == null) {
			args = new Object[] { consumer };
		} else {
			args = new Object[] { consumer, subscribeParameters };
		}
		final Method subscribeMethod = this.findSubscribeMethod(this.getArgTypes(args));
		final int consumerIndex = 0;
		this.subscribe(subscribeMethod, args, consumerIndex);
	}

	/**
	 * Subscribes to the producer's events on behalf of a managed component's
	 * proxy call, locating the consumer argument by its runtime type rather
	 * than by a fixed index.
	 *
	 * @param subscribeMethod the producer's subscribe delegate method being called
	 * @param args            the call's raw arguments
	 * @throws Throwable whatever the underlying subscribe delegate call throws
	 */
	private void subscribe(final Method subscribeMethod, final Object[] args) throws Throwable {
		// get original consumer
		final int consumerIndex = this.getConsumerIndex(args);
		this.subscribe(subscribeMethod, args, consumerIndex);
	}

	/**
	 * Subscribes the consumer at the given argument index: creates a
	 * consumer-side proxy for it (unless already subscribed with the same
	 * remaining arguments), registers it in {@link #consumerToProxyNew}, and
	 * invokes the given subscribe method with it substituted for the
	 * original consumer argument.
	 *
	 * @param subscribeMethod the producer's subscribe delegate method to call
	 * @param args            the call's raw arguments, mutated in place to
	 *                        substitute the consumer-side proxy
	 * @param consumerIndex   the index of the consumer argument
	 * @throws Throwable whatever the underlying subscribe delegate call throws
	 */
	private void subscribe(final Method subscribeMethod, final Object[] args, final int consumerIndex)
			throws Throwable {
		@SuppressWarnings("unchecked")
		final EVENTS consumer = (EVENTS) args[consumerIndex];

		// note: we must store, for each consumer, each subscribe it did, because
		// for subscribe with parameters, it is allowed for the same consumer to
		// subscribe several times.
		// For this purpose, there is a map associated with each consumer.
		// in this map, we store the list of parameters values (which may be an empty
		// list, for basic subscribe) as key, and the corresponding proxy as value.
		// that way, in unsubscribe method, we will be able to find back the proxy from
		// the consumer and the list of parameters.
		// this assumes that the parameters will be consistent regarding to equals.
		// (must be explained in used doc)

		final List<Object> remaining = this.getRemainingArgs(args, consumerIndex);
		final EVENTS consumerProxy;
		synchronized (this.subscriptionsLock) {
			final Map<List<Object>, EVENTS> argsToProxy = this.consumerToProxyNew.get(consumer);
			if (argsToProxy != null && argsToProxy.containsKey(remaining)) {
				final String message = "Consumer " + consumer + " has already subscribed to events:"
						+ this.eventsInterface + " with parameters " + remaining;
				this.validationServices.handleDynamicVerification(message);
				return;
			}

			consumerProxy = this.eventsProxyFactory.newConsumerProxy(this.eventsInterface, consumer);
			if (this.subscribingComponentType != null) {
				((ConsumerInvocationHandler<?>) Proxy.getInvocationHandler(consumerProxy))
						.setSubscribingComponentType(this.subscribingComponentType);
			}
			this.consumerToProxyNew.computeIfAbsent(consumer, key -> new HashMap<>()).put(remaining, consumerProxy);
		}

		args[consumerIndex] = consumerProxy;
		try {
			subscribeMethod.invoke(this.providedSubscriber, args);
		} catch (Throwable failure) {
			// The producer did not take the subscription: forget it, so that it can be tried again.
			this.forgetSubscription(consumer, remaining, consumerProxy);
			throw failure;
		}
	}

	/**
	 * Forgets the given proxy as the subscription of the consumer with the given remaining arguments, unless
	 * another one has replaced it.
	 */
	private void forgetSubscription(final EVENTS consumer, final List<Object> remaining, final EVENTS consumerProxy) {
		synchronized (this.subscriptionsLock) {
			final Map<List<Object>, EVENTS> argsToProxy = this.consumerToProxyNew.get(consumer);
			if (argsToProxy != null && argsToProxy.get(remaining) == consumerProxy) {
				argsToProxy.remove(remaining);
				if (argsToProxy.isEmpty()) {
					this.consumerToProxyNew.remove(consumer);
				}
			}
		}
	}

	/**
	 * Returns a copy of the given subscribe/unsubscribe call arguments with
	 * the consumer argument removed, used as the key distinguishing this
	 * consumer's several subscriptions (with different parameters) in
	 * {@link #consumerToProxyNew}.
	 *
	 * @param args          the call's raw arguments
	 * @param consumerIndex the index of the consumer argument to exclude
	 * @return the remaining arguments, in original order
	 */
	public List<Object> getRemainingArgs(final Object[] args, final int consumerIndex) {
		final List<Object> remainingArgs = new ArrayList<>();
		for (int i = 0; i < args.length; i++) {
			if (i != consumerIndex) {
				remainingArgs.add(args[i]);
			}
		}
		return remainingArgs;
	}

	/**
	 * Unsubscribes the given consumer from the producer's events.
	 *
	 * @param consumer the consumer to unsubscribe
	 * @throws Throwable whatever the underlying unsubscribe delegate call throws
	 */
	public void unsubscribe(final EVENTS consumer) throws Throwable {
		this.checkInvalidation();
		final Object[] args = new Object[] { consumer };
		final Method unsubscribeMethod = this.findUnsubscribeMethod(this.getArgTypes(args));
		final int consumerIndex = 0;
		this.unsubscribe(unsubscribeMethod, args, consumerIndex);
	}

	/**
	 * Unsubscribes on behalf of a managed component's proxy call, locating
	 * the consumer argument by its runtime type rather than by a fixed
	 * index.
	 *
	 * @param method the producer's unsubscribe delegate method being called
	 * @param args   the call's raw arguments
	 * @throws Throwable whatever the underlying unsubscribe delegate call throws
	 */
	private void unsubscribe(final Method method, final Object[] args) throws Throwable {
		final int consumerIndex = this.getConsumerIndex(args);
		this.unsubscribe(method, args, consumerIndex);
	}

	/**
	 * Unsubscribes the consumer at the given argument index, resolving the
	 * proxy previously registered for it in {@link #consumerToProxyNew} and
	 * invoking the given unsubscribe method with it substituted for the
	 * original consumer argument.
	 *
	 * @param unsubscribeMethod the producer's unsubscribe delegate method to call
	 * @param args              the call's raw arguments, mutated in place to
	 *                          substitute the consumer-side proxy
	 * @param consumerIndex     the index of the consumer argument
	 * @throws Throwable whatever the underlying unsubscribe delegate call throws
	 */
	public void unsubscribe(final Method unsubscribeMethod, final Object[] args, final int consumerIndex)
			throws Throwable {
		@SuppressWarnings("unchecked")
		final EVENTS consumer = (EVENTS) args[consumerIndex];

		final List<Object> remaining = this.getRemainingArgs(args, consumerIndex);
		final EVENTS consumerProxy;
		synchronized (this.subscriptionsLock) {
			final Map<List<Object>, EVENTS> argsToProxy = this.consumerToProxyNew.get(consumer);
			if (argsToProxy == null || !argsToProxy.containsKey(remaining)) {
				final String message = "Consumer " + consumer + " is not subscribed to events:" + this.eventsInterface
						+ " with parameters " + remaining;
				this.validationServices.handleDynamicVerification(message);
				return;
			}
			consumerProxy = argsToProxy.remove(remaining);
			if (argsToProxy.isEmpty()) {
				this.consumerToProxyNew.remove(consumer);
			}
		}
		args[consumerIndex] = consumerProxy;

		unsubscribeMethod.invoke(this.providedSubscriber, args);
	}

	/**
	 * Resolves the producer's subscribe delegate method matching the given
	 * argument types.
	 *
	 * @param argTypes the subscribe call's argument types
	 * @return the matching subscribe delegate method
	 */
	private Method findSubscribeMethod(final List<Class<?>> argTypes) {
		return this.findSubscriberMethod(argTypes, true);
	}

	/**
	 * Finds the index of the consumer argument among the given call
	 * arguments, i.e. the first non-null argument assignable to
	 * {@link #eventsInterface}.
	 *
	 * @param args the call's raw arguments
	 * @return the index of the consumer argument
	 * @throws IllegalArgumentException if no argument matches {@link #eventsInterface}
	 */
	private int getConsumerIndex(final Object[] args) {
		for (int i = 0; i < args.length; i++) {
			final Object arg = args[i];
			if (arg != null && this.eventsInterface.isAssignableFrom(arg.getClass())) {
				return i;
			}
		}
		throw new IllegalArgumentException("consumer not found");
	}

	/**
	 * Resolves the producer's unsubscribe delegate method matching the given
	 * argument types.
	 *
	 * @param argTypes the unsubscribe call's argument types
	 * @return the matching unsubscribe delegate method
	 */
	private Method findUnsubscribeMethod(final List<Class<?>> argTypes) {
		return this.findSubscriberMethod(argTypes, false);
	}

	/**
	 * Replaces the producer's subscribe delegate this handler forwards
	 * subscribe/unsubscribe calls to, e.g. when the producer's proxy is
	 * itself replaced.
	 *
	 * @param providedSubscriber the new subscribe delegate; must not be
	 *                           {@code null}
	 */
	public void replaceProvidedSubscriber(final SUBSCRIBE providedSubscriber) {
		RuntimeCheck.notNull(providedSubscriber, "providedSubscriber");
		this.providedSubscriber = providedSubscriber;
	}

}
