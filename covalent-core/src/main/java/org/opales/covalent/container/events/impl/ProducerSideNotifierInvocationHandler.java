/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.events.impl;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.ComponentAnnotations;
import org.opales.covalent.container.events.EventsManagerEvents;
import org.opales.covalent.container.events.GenericSubscribe;
import org.opales.covalent.container.events.SubscribeParameters;
import org.opales.covalent.container.exceptions.ValidationException;
import org.opales.covalent.container.metamodel.MetamodelPortReadServices;
import org.opales.covalent.container.metamodel.MetamodelReadServices;
import org.opales.covalent.container.metamodel.metamodel.EventsMetamodel;
import org.opales.covalent.container.util.CovalentDiagnostics;
import org.opales.covalent.container.util.Reflection;
import org.opales.covalent.container.util.RuntimeCheck;
import org.opales.covalent.container.validation.ValidationParameters;
import org.opales.covalent.container.validation.ValidationServices;
import org.opales.covalent.container.workers.impl.LabeledTask;

/**
 * <p>
 * Events invocation handler used on producer side. This invocation handler
 * handles both subscription from events consumers and events production.
 * </p>
 * 
 * <p>
 * This invocation handler implements the following QoS:
 * </p>
 * 
 * <ul>
 * <li>Delegation: If the producer requires subscribe delegation (by
 * implementing the subscribe interface), it will be notified each time a
 * consumer subscribes or unsubscribes to produced events.</li>
 * <li>Asynchronous firing: events firing may be synchronous or asynchronous
 * depending on events manager parametering, see
 * {@link org.opales.covalent.container.CovalentContainerParameters#setSynchronousEvents(boolean)}
 * <li>Clone: events interfaces, methods or attributes annotated with @Cloned
 * will be automatically cloned before being fired. Note that arguments are
 * cloned only once, so there can still be unpredictable side effects,
 * especially when events are asynchronous, if consumers mutate the events data.
 * The clone QoS may have a significant impact on performance and so should be
 * used with care.</li>
 * </ul>
 *
 * @param <EVENTS>    the events interface this producer notifies consumers
 *                    for
 * @param <SUBSCRIBE> the events subscribe interface consumers use to
 *                    (un)subscribe
 */
public class ProducerSideNotifierInvocationHandler<EVENTS, SUBSCRIBE>
		extends AbstractSubscriberInvocationHandler<SUBSCRIBE> {

	private static final Logger log = Logger.getLogger(ProducerSideNotifierInvocationHandler.class.getName());

	/** Reports dynamic (structural) verification failures, e.g. a failed argument clone. */
	private ValidationServices validationServices;

	/** Decides whether a failure while cloning an event argument should throw or merely be reported. */
	private ValidationParameters validationParameters;

	/** Resolves this producer's events metamodel. */
	private MetamodelPortReadServices MetamodelPortReadServices;

	/** Resolves a consumer proxy's original component type, e.g. for supervision-UI reporting. */
	private MetamodelReadServices metamodelReadServices;

	/** Executor events are fired and subscribe/unsubscribe delegate calls are dispatched on. */
	private Executor eventWorker = null;

	/** Whether {@link #eventWorker} delivers on other threads than the producer's. */
	private boolean asynchronous = false;

	/** Metadata of consumers parked while this producer was passivated, awaiting resubscription. */
	private final Set<ConsumerMetadata<?>> pendingSubscribers = ConcurrentHashMap.newKeySet();

	/** The events interface this producer notifies consumers for. */
	private final Class<EVENTS> eventsInterface;

	/** The subscribe delegate instance notified of subscribe/unsubscribe calls, when one is declared. */
	private SUBSCRIBE subscribeDelegate;

	/** Callback invoked when a consumer subscribes, when using consumer-based subscribe delegation. */
	private Consumer<EVENTS> subscribeConsumer;

	/** Callback invoked when a consumer unsubscribes, when using consumer-based unsubscribe delegation. */
	private Consumer<EVENTS> unsubscribeConsumer;

	/** Whether a subscribe delegate (consumer callback or delegate instance) must be notified of subscriptions. */
	private boolean subscriptionDelegation = false;

	/** Whether a subscribe delegate (consumer callback or delegate instance) must be notified of unsubscriptions. */
	private boolean unsubscriptionDelegation = false;

	/**
	 * Map used to retrieve the subscription state from the original consumer.
	 */
	private final Map<EVENTS, EventSubscription> consumerToSubscription = new HashMap<>();

	/** Metadata of every consumer currently subscribed to this producer. */
	private final Set<ConsumerMetadata<EVENTS>> consumersMetadata = ConcurrentHashMap.newKeySet();

	/** The component type producing the events, reported when instrumentation is enabled. */
	private Class<?> producerComponentType;

	/** Every method declared by {@link #eventsInterface}, used to tell an events call apart from a subscribe/unsubscribe call. */
	private final List<Method> eventMethods = new ArrayList<>();

	/** This producer's events metamodel, resolved once by {@link #init()}. */
	private EventsMetamodel<EVENTS> eventsMetamodel;

	/** Whether {@link #init()} has already run. */
	private boolean initialized = false;

	/** Supplies whether instrumentation (call start/finish notifications) is currently enabled. */
	private BooleanSupplier instrumentationSupplier;

	/** Supplies the events manager events sink notified of subscribe/unsubscribe and, when instrumented, call events. */
	private Supplier<EventsManagerEvents> eventsManagerEventsSupplier;

	/** Supplies how many events a subscription buffers at most while its subscribe delegate notification is pending. */
	private IntSupplier subscriptionBufferCapacity;

	/** Reports an event dropped because the buffer of a pending subscription was full. */
	private Runnable bufferedEventDropped;

	/**
	 * Creates a new handler for a producer that delegates subscription
	 * notifications through plain {@link Consumer} callbacks rather than a
	 * dedicated subscribe interface. The synthetic {@link GenericSubscribe}
	 * interface is used internally as the subscribe interface in this case.
	 *
	 * @param eventsInterface     the events interface this producer notifies
	 *                            consumers for
	 * @param subscribeConsumer   callback invoked when a consumer subscribes,
	 *                            or {@code null} if subscribe delegation is
	 *                            not required
	 * @param unsubscribeConsumer callback invoked when a consumer
	 *                            unsubscribes, or {@code null} if unsubscribe
	 *                            delegation is not required
	 */
	@SuppressWarnings("unchecked")
	public ProducerSideNotifierInvocationHandler(final Class<EVENTS> eventsInterface,
			final Consumer<EVENTS> subscribeConsumer, final Consumer<EVENTS> unsubscribeConsumer) {
		super(null);
		this.eventsInterface = eventsInterface;
		this.subscribeConsumer = subscribeConsumer;
		this.unsubscribeConsumer = unsubscribeConsumer;
		if (this.subscribeConsumer != null) {
			this.subscriptionDelegation = true;
		}
		if (this.unsubscribeConsumer != null) {
			this.unsubscriptionDelegation = true;
		}
		this.subscribeInterface = (Class<SUBSCRIBE>) GenericSubscribe.class;
		this.introspectSubscribeInterface();
	}

	/**
	 * Creates a new handler for a producer that declares an explicit
	 * subscribe interface, optionally delegating (un)subscription
	 * notifications to a subscribe delegate instance implementing that
	 * interface.
	 *
	 * @param eventsInterface    the events interface this producer notifies
	 *                           consumers for
	 * @param subscribeInterface the events subscribe interface consumers use
	 *                           to (un)subscribe
	 * @param subscribeDelegate  the delegate notified of subscribe/unsubscribe
	 *                           calls, or {@code null} if delegation is not
	 *                           required
	 */
	public ProducerSideNotifierInvocationHandler(final Class<EVENTS> eventsInterface,
			final Class<SUBSCRIBE> subscribeInterface, final SUBSCRIBE subscribeDelegate) {
		super(subscribeInterface);
		this.eventsInterface = eventsInterface;
		this.subscribeDelegate = subscribeDelegate;
		if (this.subscribeInterface != null) {
			this.introspectSubscribeInterface();
		}
		if (this.subscribeDelegate != null) {
			this.subscriptionDelegation = true;
			this.unsubscriptionDelegation = true;
		}
	}

	/**
	 * Populates {@link #eventMethods} with every method declared by
	 * {@link #eventsInterface}, so that {@link #invokeMethod} can later tell
	 * an events call apart from a subscribe/unsubscribe call by simple
	 * membership.
	 */
	private void introspectEventsInterface() {
		Method[] methods = this.eventsInterface.getMethods();
		for (Method method : methods) {
			this.eventMethods.add(method);
		}
	}

	/**
	 * Finishes wiring this handler once its collaborating services have been
	 * injected: resolves the events metamodel for {@link #eventsInterface}
	 * and introspects the events interface's methods. Must be called before
	 * this handler's proxy is used; {@link #invokeMethod} throws an
	 * {@link IllegalStateException} if it has not run.
	 */
	public void init() {
		this.initialized = true;
		this.eventsMetamodel = this.MetamodelPortReadServices.getEventsMetamodel(this.eventsInterface);
		if (this.eventsMetamodel == null) {
			throw new IllegalStateException("No events metamodel for " + this.eventsInterface.getName());
		}
		this.introspectEventsInterface();
	}

	/**
	 * Sets the service used to report dynamic (structural) verification
	 * failures, e.g. when a cloned argument's {@code clone()} call fails.
	 *
	 * @param validationServices the validation service
	 */
	public void setValidationServices(final ValidationServices validationServices) {
		this.validationServices = validationServices;
	}

	/**
	 * Sets the container-wide validation parameters, used to decide whether
	 * a failure while cloning an event argument should throw or merely be
	 * reported.
	 *
	 * @param validationParameters the validation parameters
	 */
	public void setValidationParameters(final ValidationParameters validationParameters) {
		this.validationParameters = validationParameters;
	}

	/**
	 * Sets the service used to resolve this producer's events metamodel.
	 *
	 * @param MetamodelPortReadServices the metamodel port read service
	 */
	public void setMetamodelPortReadServices(final MetamodelPortReadServices MetamodelPortReadServices) {
		this.MetamodelPortReadServices = MetamodelPortReadServices;
	}

	/**
	 * Sets the service used to resolve a consumer proxy's original component
	 * type, e.g. for supervision-UI reporting.
	 *
	 * @param metamodelReadServices the metamodel read service
	 */
	public void setMetamodelReadServices(final MetamodelReadServices metamodelReadServices) {
		this.metamodelReadServices = metamodelReadServices;
	}

	/**
	 * Sets the executor events are fired and subscribe/unsubscribe delegate
	 * calls are dispatched on.
	 *
	 * @param eventWorker the events worker executor
	 * @param asynchronous whether it delivers on other threads than the
	 *                     producer's, rather than in the producer's own call
	 */
	public void setWorker(final Executor eventWorker, final boolean asynchronous) {
		this.eventWorker = eventWorker;
		this.asynchronous = asynchronous;
	}

	/**
	 * Sets the component type producing the events, reported alongside the
	 * consumer component type when instrumentation is enabled.
	 *
	 * @param producerComponentType the producer component type
	 */
	public void setProducerComponentType(final Class<?> producerComponentType) {
		this.producerComponentType = producerComponentType;
	}

	/**
	 * Returns the metadata of every consumer currently subscribed to this
	 * producer, primarily for consumption by the supervision UI. The
	 * returned set is a defensive copy, so adding or removing entries from
	 * it does not affect this handler's own bookkeeping; the
	 * {@code ConsumerMetadata} entries themselves are still the live,
	 * mutable instances shared with internal storage.
	 *
	 * @return a snapshot of the subscribed consumers' metadata
	 */
	public Set<ConsumerMetadata<EVENTS>> getConsumersMetadata() {
		return new HashSet<>(this.consumersMetadata);
	}

	/**
	 * Sets the supplier this handler consults to decide, on each event
	 * delivery, whether instrumentation (start/finish notifications) is
	 * currently enabled.
	 *
	 * @param instrumentationSupplier supplies whether instrumentation is
	 *                                currently enabled
	 */
	public void setInstrumentationSupplier(final BooleanSupplier instrumentationSupplier) {
		this.instrumentationSupplier = instrumentationSupplier;
	}

	/**
	 * Returns the callback notified when a consumer subscribes, when this
	 * producer was configured with consumer-based subscribe delegation.
	 *
	 * @return the subscribe delegate consumer, or {@code null} if not
	 *         configured
	 */
	public Consumer<EVENTS> getSubscribeDelegateConsumer() {
		return this.subscribeConsumer;
	}

	/**
	 * Returns the callback notified when a consumer unsubscribes, when this
	 * producer was configured with consumer-based unsubscribe delegation.
	 *
	 * @return the unsubscribe delegate consumer, or {@code null} if not
	 *         configured
	 */
	public Consumer<EVENTS> getUnSubscribeDelegateConsumer() {
		return this.unsubscribeConsumer;
	}

	/**
	 * Sets the supplier used to report subscribe/unsubscribe and, when
	 * instrumentation is enabled, event call start/finish notifications to
	 * the events manager events port.
	 *
	 * @param supplier supplies the events manager events sink to report to
	 */
	void setProducedEventsManagerEvents(final Supplier<EventsManagerEvents> supplier) {
		this.eventsManagerEventsSupplier = supplier;
	}

	/**
	 * Sets how many events a subscription buffers at most while its subscribe
	 * delegate notification is pending, and how an event dropped because that
	 * buffer is full is reported.
	 *
	 * @param capacity             supplies the buffer capacity
	 * @param bufferedEventDropped reports an event dropped because a buffer was full
	 */
	void setSubscriptionBuffer(final IntSupplier capacity, final Runnable bufferedEventDropped) {
		this.subscriptionBufferCapacity = capacity;
		this.bufferedEventDropped = bufferedEventDropped;
	}

	/**
	 * Dispatches a call on the producer proxy: an events-interface method is
	 * fired to subscribers, while any other method is treated as a
	 * subscribe/unsubscribe call.
	 *
	 * @param proxy  the dynamic proxy to which this invocation handler is
	 *               attached
	 * @param method the called method
	 * @param args   the method arguments
	 * @return always {@code null}, since events and subscribe/unsubscribe
	 *         methods are declared {@code void}
	 */
	@Override
	public Object invokeMethod(final Object proxy, final Method method, final Object[] args) throws Throwable {
		if (!this.initialized) {
			throw new IllegalStateException("Events handler not initialized: " + this.eventsInterface.getName());
		}
		if (this.eventMethods.contains(method)) {
			return this.invokeEvents(proxy, method, args);
		} else {
			return this.invokeSubscribe(proxy, method, args);
		}
	}

	/**
	 * Fires an events-interface method to every currently subscribed
	 * consumer: clones {@code @Cloned} arguments once if needed, then builds
	 * and runs the per-subscription delivery plan (buffering the event
	 * instead of delivering it immediately for a subscription still
	 * completing its subscribe delegate). A no-op while this producer is
	 * passivated.
	 *
	 * @param proxy  the dynamic proxy to which this invocation handler is
	 *               attached
	 * @param method the called events method
	 * @param args   the method arguments
	 * @return always {@code null}
	 */
	private Object invokeEvents(final Object proxy, final Method method, final Object[] args) {
		this.checkInvalidation();
		if (this.isPassivated()) {
			return null;
		}

		// Note: this optimization test avoids looking for @Cloned each time an event is
		// fired.
		if (this.hasCloneableElements()) {
			this.replaceWithClones(method, args);
		}

		List<EventSubscription> subscriptions = this.getSubscriptionsSnapshot();
		if (subscriptions.isEmpty()) {
			// Not necessarily a bug (a producer may legitimately have no
			// subscriber yet, e.g. early in a composite's own activation),
			// but silently dropping an event otherwise leaves no trace at
			// all - this is the one place in the dispatch path that can
			// surface it without changing delivery semantics.
			CovalentDiagnostics.fine(log, "Events " + this.eventsInterface.getSimpleName() + " method "
					+ method.getName() + " fired with no subscriber.");
		}

		List<Runnable> deliveries = new ArrayList<>();
		for (EventSubscription subscription : subscriptions) {
			Runnable delivery = subscription.prepareDelivery(method, args);
			if (delivery != null) {
				deliveries.add(delivery);
			}
		}

		// The delivery plan must be complete before invoking user callbacks.
		// Subscription mutations performed by one consumer must affect later events,
		// not the event currently being dispatched.
		for (Runnable delivery : deliveries) {
			delivery.run();
		}
		return null;
	}

	/**
	 * Returns a point-in-time copy of the current subscriptions, so that an
	 * event's delivery plan is unaffected by a subscription mutation
	 * happening concurrently or triggered by delivering the event itself.
	 *
	 * @return a snapshot of the current subscriptions
	 */
	private List<EventSubscription> getSubscriptionsSnapshot() {
		synchronized (this.consumerToSubscription) {
			return new ArrayList<>(this.consumerToSubscription.values());
		}
	}

	/**
	 * Replaces, in place, every {@code @Cloned} argument of the given event
	 * method call with a clone of its original value.
	 *
	 * @param method the called events method
	 * @param args   the method arguments, mutated in place
	 */
	private void replaceWithClones(final Method method, final Object[] args) {
		if (args != null && args.length > 0) {
			for (int i = 0; i < args.length; i++) {
				Object arg = args[i];
				// check whether args must be cloned
				if (this.isCloned(method, i)) {
					if (arg instanceof Cloneable) {
						try {
							// getMethod("clone") never finds the clone operation of an array type
							Object clone = arg.getClass().isArray() ? cloneArray(arg)
									: arg.getClass().getMethod("clone").invoke(arg);
							args[i] = clone;
						} catch (NoSuchMethodException e) {
							String message = "The class " + arg.getClass()
									+ " is Cloneable but does not implement the clone() method";
							ProducerSideNotifierInvocationHandler.this.validationServices
									.handleDynamicVerification(message);
						} catch (Exception e) {
							String message = "While cloning a Cloneable instance of class " + arg.getClass()
									+ ", an exception was thrown: " + e;
							if (ProducerSideNotifierInvocationHandler.this.validationParameters
									.getDynamicRulesVerificationLevel().equals(VerificationLevel.strict)) {
								throw new ValidationException(message, e);
							} else {
								ProducerSideNotifierInvocationHandler.this.validationServices
										.handleDynamicVerification(message);
							}
						}
					} else {
						Parameter parameter = method.getParameters()[i];
						String message = "Method argument " + parameter.getName()
								+ " is cloned but does not implement Cloneable";
						ProducerSideNotifierInvocationHandler.this.validationServices
								.handleDynamicVerification(message);
					}
				} else {
					// no @Cloned annotation, nothing to do
				}
			}
		}
	}

	/**
	 * Returns a shallow copy of an array, of the same runtime type, like the
	 * {@code clone()} of an array type: the elements themselves are shared
	 * with the original array.
	 *
	 * @param array the array to copy, primitive or not
	 * @return the copy
	 */
	private static Object cloneArray(final Object array) {
		int length = Array.getLength(array);
		Object copy = Array.newInstance(array.getClass().getComponentType(), length);
		System.arraycopy(array, 0, copy, 0, length);
		return copy;
	}

	/**
	 * Reports whether {@link #eventsInterface} declares any {@code @Cloned}
	 * element at all (interface, method or parameter level), used as a fast
	 * short-circuit so {@link #invokeEvents} does not need to inspect
	 * {@code @Cloned} on every fired event when there is nothing to clone.
	 *
	 * @return {@code true} if at least one {@code @Cloned} element exists
	 */
	private boolean hasCloneableElements() {
		return this.eventsMetamodel.hasCloneableElements();
	}

	/**
	 * Reports whether the argument at the given index of the given events
	 * method must be cloned before delivery, i.e. whether {@code @Cloned}
	 * applies to the events interface itself, to this method, or to this
	 * parameter.
	 *
	 * @param method   the called events method
	 * @param argIndex the index of the argument to check
	 * @return {@code true} if the argument must be cloned
	 */
	private boolean isCloned(final Method method, final int argIndex) {
		if (this.eventsMetamodel.isCloneable()) {
			return true;
		}
		if (this.eventsMetamodel.isMethodCloneable(method)) {
			return true;
		}
		Parameter parameter = method.getParameters()[argIndex];
		if (this.eventsMetamodel.isMethodParameterClonable(parameter)) {
			return true;
		}
		return false;
	}

	/**
	 * <p>
	 * Invokes a subscribe method.
	 * </p>
	 *
	 * @param proxy  the dynamic proxy to which this invocation handler is attached
	 * @param method the called subscriber method
	 * @param args   the method arguments
	 * @return always {@code null}
	 */
	private Object invokeSubscribe(final Object proxy, final Method method, final Object[] args) {
		// subscribe and unsubscribe is allowed when component is passivated
		if (this.subscribeMethods.contains(method)) {
			this.checkInvalidation();
			this.subscribe(args);
		} else if (this.unsubscribeMethods.contains(method)) {
			// unsubscribing from invalidated producer is allowed
			this.unsubscribe(args);
		}

		return null;
	}

	/**
	 * Drops every current subscription to this producer, without notifying
	 * an unsubscribe delegate. Reserved for internal use by the events
	 * manager, e.g. when unproducing the events interface.
	 */
	public void unsubscribeAll() {
		this.checkInvalidation();
		this.clearAll();
	}

	/**
	 * Clears both {@link #consumersMetadata} and {@link #consumerToSubscription},
	 * dropping every current subscription's bookkeeping.
	 */
	private void clearAll() {
		this.consumersMetadata.clear();
		synchronized (this.consumerToSubscription) {
			this.consumerToSubscription.clear();
		}
	}

	/**
	 * Parks current subscribers before delegating to the invalidation
	 * performed by the base class, so that a component whose consumed events
	 * proxy is invalidated does not leak stale subscriptions.
	 */
	@Override
	public void invalidate() {
		this.parkSubscribers();
		super.invalidate();
	}

	/**
	 * Moves every current consumer's metadata into {@link #pendingSubscribers}
	 * and clears the active subscription bookkeeping, so that
	 * {@link #subscribePendingConsumers()} can subscribe them again once this
	 * producer is reactivated. Called both when the producer passivates and,
	 * as a first step, when it invalidates.
	 */
	public void parkSubscribers() {
		this.checkInvalidation();
		for (ConsumerMetadata<EVENTS> metadata : this.consumersMetadata) {
			this.pendingSubscribers.add(metadata);
		}
		// as pending subscribers will be subscribed again,
		// we must clear all the collections
		this.clearAll();
	}

	/**
	 * <p>
	 * Subcribe pending consumers (happens when producer was passivated and then
	 * activated again).
	 * </p>
	 */
	private void subscribePendingConsumers() {
		this.checkInvalidation();
		for (ConsumerMetadata<?> metadata : this.pendingSubscribers) {
			this.subscribeAgain(metadata);
		}
		this.pendingSubscribers.clear();
	}

	/**
	 * Returns the metadata of consumers parked while this producer was
	 * passivated, awaiting resubscription once it activates again. The
	 * returned set is a defensive copy, so adding or removing entries from
	 * it does not affect this handler's own bookkeeping; the
	 * {@code ConsumerMetadata} entries themselves are still the live,
	 * mutable instances shared with internal storage.
	 *
	 * @return a snapshot of the pending consumers' metadata
	 */
	public Set<ConsumerMetadata<?>> getPendingSubscribers() {
		this.checkInvalidation();
		return new HashSet<>(this.pendingSubscribers);
	}

	/**
	 * Subscribes a consumer-side proxy to this producer's events, as if
	 * through a call to the subscribe interface. Used both to resubscribe a
	 * previously parked consumer and, through
	 * {@link #callSubscribeDelegate}, indirectly during ordinary subscribe
	 * processing.
	 *
	 * @param consumer             the consumer-side events proxy to subscribe
	 * @param subscribeParameters  the subscribe parameters to associate with
	 *                             the subscription, or {@code null} for a
	 *                             plain subscription
	 */
	public void subscribe(final EVENTS consumer, final SubscribeParameters subscribeParameters) {
		this.checkInvalidation();
		Object[] args = null;
		if (subscribeParameters == null) {
			args = new Object[] { consumer };
		} else {
			args = new Object[] { consumer, subscribeParameters };
		}
		this.subscribe(args);
	}

	/**
	 * Subscribes again a consumer parked earlier, with the same arguments as its
	 * original subscribe call, so that a producer implementing the subscribe
	 * interface receives the same call as the first time.
	 *
	 * @param metadata the parked consumer's metadata
	 */
	public void subscribeAgain(final ConsumerMetadata<?> metadata) {
		this.checkInvalidation();
		Object[] originalArguments = metadata.getSubscribeArguments();
		if (originalArguments != null) {
			this.subscribe(originalArguments);
			return;
		}
		Object argument = metadata.getSubscribeArgument();
		if (argument == null) {
			this.subscribe(new Object[] { metadata.getInstance() });
		} else {
			this.subscribe(new Object[] { metadata.getInstance(), argument });
		}
	}

	/**
	 * Unsubscribes a consumer through the producer-side notifier.
	 * <p>
	 * This method is used by the events manager when it already owns the
	 * consumer-side proxy that was registered with this notifier. In that case the
	 * manager can remove the subscription directly instead of going through a used
	 * subscribe proxy.
	 * </p>
	 *
	 * @param consumer the consumer-side events proxy to remove from this notifier.
	 */
	public void unsubscribe(final EVENTS consumer) {
		this.unsubscribe(new Object[] { consumer });
	}

	/**
	 * Reports whether the given subscribe method argument is a
	 * {@link SubscribeParameters} instance.
	 *
	 * @param arg the argument to check
	 * @return {@code true} if {@code arg} is a {@link SubscribeParameters}
	 */
	private boolean isSubscribeParameters(final Object arg) {
		return arg instanceof SubscribeParameters;
	}

	/**
	 * Processes a subscribe call: resolves the consumer-side proxy and any
	 * subscribe parameters (or {@code @EventsFilter} argument) from the raw
	 * method arguments, parks the subscription if this producer is currently
	 * passivated, otherwise registers it and, if subscription delegation is
	 * configured, hands it off to {@link #callSubscribeDelegate}.
	 *
	 * @param args the subscribe method's raw arguments
	 */
	private void subscribe(final Object[] args) {
		// analyze subscribe method arguments
		ConsumerMetadata<EVENTS> consumerMetadata = new ConsumerMetadata<>();
		if (args.length == 2) {
			Object arg1 = args[1];
			SubscribeParameters subscribeParameters = null;
			if (this.isSubscribeParameters(arg1)) {
				subscribeParameters = (SubscribeParameters) arg1;
			}
			if (this.isFilter(arg1)) {
				subscribeParameters = new SubscribeParametersImpl();
				subscribeParameters.setEventFilter(arg1);
			}
			consumerMetadata.setSubscribeParameters(subscribeParameters);
			consumerMetadata.setSubscribeArgument(arg1);
		}

		EVENTS consumerSideProxy = this.getConsumer(args);
		if (consumerSideProxy == null) {
			throw new IllegalArgumentException("No consumer found");
		}
		consumerMetadata.setInstance(consumerSideProxy);
		consumerMetadata.setSubscribeArguments(args);

		// try to get componentType from consumerClass

		Class<?> subscriberClass = consumerSideProxy.getClass();
		if (Proxy.isProxyClass(subscriberClass)) {
			InvocationHandler invocationHandler = Proxy.getInvocationHandler(consumerSideProxy);
			if (invocationHandler instanceof ConsumerInvocationHandler) {
				ConsumerInvocationHandler<?> consumerInvocationHandler = (ConsumerInvocationHandler<?>) invocationHandler;
				Object origConsumer = consumerInvocationHandler.getConsumer();
				Class<?> consumerComponentType = this.metamodelReadServices.getType(origConsumer.getClass());
				if (consumerComponentType == null) {
					// Not a component itself: an object a component subscribed through
					// its own @UsedEventsSubscribe port, if any.
					consumerComponentType = consumerInvocationHandler.getSubscribingComponentType();
				}
				if (consumerComponentType != null) {
					// this is needed only for component viewer
					consumerMetadata.setConsumerComponentType(consumerComponentType);
				}
			}
		}

		if (this.isPassivated()) {
			this.pendingSubscribers.add(consumerMetadata);
			return;
		}

		EventSubscription subscription = new EventSubscription(new EventsConsumer(consumerMetadata),
				this.subscriptionDelegation);
		this.addSubscription(consumerSideProxy, subscription);
		this.consumersMetadata.add(consumerMetadata);

		@SuppressWarnings("unchecked")
		Class<? extends EVENTS> consumerClass = (Class<? extends EVENTS>) consumerMetadata.getInstance().getClass();

		if (this.subscriptionDelegation) {
			this.callSubscribeDelegate(consumerSideProxy, args, subscription, consumerMetadata, consumerClass);
		} else {
			this.eventsManagerEventsSupplier.get().onEventsSubscribed(consumerClass, this.eventsInterface);
		}
	}

	/**
	 * Registers a subscription for the given consumer-side proxy.
	 *
	 * @param consumer     the consumer-side events proxy
	 * @param subscription the subscription to associate with it
	 */
	private void addSubscription(final EVENTS consumer, final EventSubscription subscription) {
		synchronized (this.consumerToSubscription) {
			this.consumerToSubscription.put(consumer, subscription);
		}
	}

	/**
	 * Reports whether the given subscribe method argument is an
	 * {@code @EventsFilter}-annotated filter instance: its class, one of its
	 * superclasses, or any super-interface of the interfaces they implement,
	 * implements an {@code @EventsFilter}-annotated interface.
	 *
	 * @param arg the argument to check; a {@code null} argument is not a filter
	 * @return {@code true} if {@code arg} implements an
	 *         {@code @EventsFilter}-annotated interface
	 */
	private boolean isFilter(final Object arg) {
		if (arg == null) {
			return false;
		}
		// Note: filter should use @EventsFilter annotation, unless provided through
		// SubscribeParameters
		return implementsEventsFilter(arg.getClass());
	}

	/**
	 * Reports whether {@code type}, one of its superclasses, or any
	 * super-interface of the interfaces they implement is an
	 * {@code @EventsFilter}-annotated interface.
	 *
	 * @param type the class or interface to inspect
	 * @return {@code true} if an {@code @EventsFilter}-annotated interface is found
	 */
	private static boolean implementsEventsFilter(final Class<?> type) {
		for (Class<?> current = type; current != null; current = current.getSuperclass()) {
			for (Class<?> anInterface : current.getInterfaces()) {
				if (ComponentAnnotations.isEventsFilter(anInterface) || implementsEventsFilter(anInterface)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Processes an unsubscribe call: resolves the consumer-side proxy from
	 * the raw method arguments, drops its subscription and metadata, reports
	 * the unsubscription, and, if unsubscribe delegation is configured,
	 * hands it off to {@link #callUnsubscribeDelegate}. A no-op if this
	 * handler is already invalidated.
	 *
	 * @param args the unsubscribe method's raw arguments
	 */
	private void unsubscribe(final Object[] args) {
		if (this.isInvalidated()) {
			// Note: the consumers have been removed from internal map when calling
			// invalidate(), so there will no be unwanted object retention.
			// We can return immediately
			return;
		}

		EVENTS consumer = this.getConsumer(args);
		// Drops the subscription from the tracking map without interrupting a
		// subscribe delegate that may still be running for this consumer: once that
		// delegate completes, the events buffered meanwhile are still delivered, but
		// no later event reaches the consumer (see EventSubscription's states).
		this.removeSubscription(consumer);

		Optional<ConsumerMetadata<EVENTS>> optional = this.consumersMetadata.stream()
				.filter(sm -> consumer == sm.getInstance()).findFirst();
		if (optional.isPresent()) {
			ConsumerMetadata<EVENTS> subscriberMetadata = optional.get();
			this.consumersMetadata.remove(subscriberMetadata);
		}

		@SuppressWarnings("unchecked")
		Class<? extends EVENTS> consumerClass = (Class<? extends EVENTS>) consumer.getClass();

		this.eventsManagerEventsSupplier.get().onEventsUnsubscribed(consumerClass, this.eventsInterface);

		if (this.unsubscriptionDelegation) {
			this.callUnsubscribeDelegate(consumer, args);
		}
	}

	/**
	 * Unconditionally removes and returns the subscription registered for
	 * the given consumer, if any.
	 *
	 * @param consumer the consumer-side events proxy
	 * @return the removed subscription, or {@code null} if none was registered
	 */
	private EventSubscription removeSubscription(final EVENTS consumer) {
		synchronized (this.consumerToSubscription) {
			return this.consumerToSubscription.remove(consumer);
		}
	}

	/**
	 * Removes the given consumer's subscription only if it is still exactly
	 * the given subscription instance, avoiding a race where a newer
	 * subscription for the same consumer would otherwise be dropped.
	 *
	 * @param consumer     the consumer-side events proxy
	 * @param subscription the subscription expected to still be registered
	 */
	private void removeSubscription(final EVENTS consumer, final EventSubscription subscription) {
		synchronized (this.consumerToSubscription) {
			if (this.consumerToSubscription.get(consumer) == subscription) {
				this.consumerToSubscription.remove(consumer);
			}
		}
	}

	/**
	 * Finds the consumer-side events proxy among the given subscribe or
	 * unsubscribe method arguments, i.e. the argument that is a dynamic
	 * proxy backed by a {@link ConsumerInvocationHandler}.
	 *
	 * @param args the subscribe or unsubscribe method's raw arguments
	 * @return the consumer-side events proxy, or {@code null} if none is found
	 */
	private EVENTS getConsumer(final Object[] args) {
		EVENTS consumer = null;
		if (args.length >= 1) {
			for (Object arg : args) {
				if (arg != null && Proxy.isProxyClass(arg.getClass())) {
					InvocationHandler invocationHandler = Proxy.getInvocationHandler(arg);
					if (invocationHandler instanceof ConsumerInvocationHandler) {
						@SuppressWarnings("unchecked")
						EVENTS eventConsumer = (EVENTS) arg;
						consumer = eventConsumer;
						break;
					}
				}
			}
		}
		return consumer;
	}

	/**
	 * Notifies the subscribe delegate (consumer callback or delegate
	 * instance method) of a new subscription, on {@link #eventWorker},
	 * without blocking the caller: the subscription stays subscribing, and
	 * buffers the events fired meanwhile, until that notification completes
	 * it or, on failure, cancels it. Not waiting keeps a subscribe call made
	 * on an events worker thread from waiting for a task queued behind it on
	 * that same pool.
	 *
	 * @param consumer         the consumer-side events proxy that subscribed
	 * @param args             the original subscribe method arguments,
	 *                         forwarded to a delegate instance method
	 * @param subscription     the subscription to complete or cancel
	 * @param consumerMetadata the subscribed consumer's metadata, removed if
	 *                         the delegate call fails
	 * @param consumerClass    the consumer-side proxy's runtime class,
	 *                         reported once the subscription completes
	 */
	private void callSubscribeDelegate(final EVENTS consumer, final Object[] args,
			final EventSubscription subscription, final ConsumerMetadata<EVENTS> consumerMetadata,
			final Class<? extends EVENTS> consumerClass) {
		DelegateCall delegateCall;
		if (this.subscribeConsumer != null) {
			delegateCall = () -> this.subscribeConsumer.accept(consumer);
		} else {
			Method subscribeDelegateMethod = this.findSubscribeDelegateMethod(this.getArgTypes(args));
			delegateCall = () -> {
				try {
					subscribeDelegateMethod.invoke(this.subscribeDelegate, args);
				} catch (InvocationTargetException e) {
					throw this.getDelegateFailure(e);
				}
			};
		}
		SubscribeNotification notification = new SubscribeNotification(delegateCall, consumer, subscription,
				consumerMetadata, consumerClass);
		this.eventWorker.execute(notification);
	}

	/** The call of the subscribe delegate, which may fail with any throwable. */
	private interface DelegateCall {
		void run() throws Throwable;
	}

	/**
	 * The notification of the subscribe delegate of one subscription. Whatever
	 * happens to it - it succeeds, it fails, or the events worker drops it
	 * without running it (full queue, worker stopped) - the subscription ends up
	 * completed or cancelled, so it never stays subscribing.
	 */
	private final class SubscribeNotification implements Runnable, DroppableTask {

		private final DelegateCall delegateCall;

		private final EVENTS consumer;

		private final EventSubscription subscription;

		private final ConsumerMetadata<EVENTS> consumerMetadata;

		private final Class<? extends EVENTS> consumerClass;

		SubscribeNotification(final DelegateCall delegateCall, final EVENTS consumer,
				final EventSubscription subscription, final ConsumerMetadata<EVENTS> consumerMetadata,
				final Class<? extends EVENTS> consumerClass) {
			this.delegateCall = delegateCall;
			this.consumer = consumer;
			this.subscription = subscription;
			this.consumerMetadata = consumerMetadata;
			this.consumerClass = consumerClass;
		}

		@Override
		public void run() {
			try {
				this.delegateCall.run();
				ProducerSideNotifierInvocationHandler.this.completeSubscription(this.consumer, this.subscription,
						this.consumerClass);
			} catch (Throwable e) {
				ProducerSideNotifierInvocationHandler.this.cancelSubscription(this.consumer, this.subscription,
						this.consumerMetadata);
				CovalentDiagnostics.severe(log, "Subscribe delegate failed.", e);
			}
		}

		@Override
		public void dropped() {
			ProducerSideNotifierInvocationHandler.this.cancelSubscription(this.consumer, this.subscription,
					this.consumerMetadata);
			CovalentDiagnostics.severe(log, "Subscribe delegate not notified: the events worker dropped the "
					+ "notification, so the subscription was cancelled.");
		}
	}

	/**
	 * Completes a subscription whose subscribe delegate notification just
	 * succeeded: transitions it to active (draining any event buffered while
	 * the delegate was running) and reports the subscription, unless the
	 * subscription was concurrently cancelled, in which case it is dropped
	 * instead.
	 *
	 * @param consumer      the consumer-side events proxy that subscribed
	 * @param subscription  the subscription to complete
	 * @param consumerClass the consumer-side proxy's runtime class, reported
	 *                      alongside {@link #eventsInterface}
	 */
	private void completeSubscription(final EVENTS consumer, final EventSubscription subscription,
			final Class<? extends EVENTS> consumerClass) {
		if (subscription.completeSubscribing()) {
			this.eventsManagerEventsSupplier.get().onEventsSubscribed(consumerClass, this.eventsInterface);
		} else {
			this.removeSubscription(consumer, subscription);
		}
	}

	/**
	 * Cancels a subscription whose subscribe delegate notification just
	 * failed, dropping its buffered events and its bookkeeping.
	 *
	 * @param consumer         the consumer-side events proxy that failed to
	 *                         subscribe
	 * @param subscription     the subscription to cancel
	 * @param consumerMetadata the consumer's metadata to remove
	 */
	private void cancelSubscription(final EVENTS consumer, final EventSubscription subscription,
			final ConsumerMetadata<EVENTS> consumerMetadata) {
		subscription.cancel();
		this.removeSubscription(consumer, subscription);
		this.consumersMetadata.remove(consumerMetadata);
	}

	/**
	 * Unwraps a delegate-invocation failure to the underlying cause, when
	 * the given throwable is an {@link InvocationTargetException} with a
	 * target exception; otherwise returns it unchanged.
	 *
	 * @param throwable the failure to unwrap
	 * @return the underlying delegate failure
	 */
	private Throwable getDelegateFailure(final Throwable throwable) {
		if (throwable instanceof InvocationTargetException) {
			Throwable targetException = ((InvocationTargetException) throwable).getTargetException();
			if (targetException != null) {
				return targetException;
			}
		}
		return throwable;
	}

	/**
	 * Notifies the unsubscribe delegate (consumer callback or delegate
	 * instance method) of a dropped subscription, on {@link #eventWorker}.
	 * Like {@link #callSubscribeDelegate}, this does not block the caller;
	 * unlike it, this does not affect this handler's own bookkeeping, since
	 * the subscription has already been removed by the time this is called.
	 *
	 * @param consumer the consumer-side events proxy that unsubscribed
	 * @param args     the original unsubscribe method arguments, forwarded
	 *                 to a delegate instance method
	 */
	private void callUnsubscribeDelegate(final EVENTS consumer, final Object[] args) {
		if (this.unsubscribeConsumer != null) {
			this.eventWorker.execute(() -> {
				try {
					this.unsubscribeConsumer.accept(consumer);
				} catch (Throwable e) {
					CovalentDiagnostics.severe(log, "Unsubscribe delegate failed.", e);
				}
			});
		} else {
			Method unsubscribeDelegateMethod = this.findUnsubscribeDelegateMethod(this.getArgTypes(args));
			this.eventWorker.execute(() -> {
				try {
					unsubscribeDelegateMethod.invoke(this.subscribeDelegate, args);
				} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
					CovalentDiagnostics.severe(log, "Unsubscribe delegate failed.", this.getDelegateFailure(e));
				}
			});
		}
	}

	/**
	 * Resolves the subscribe delegate interface method matching the given
	 * argument types.
	 *
	 * @param argTypes the subscribe call's argument types
	 * @return the matching subscribe delegate method
	 */
	private Method findSubscribeDelegateMethod(final List<Class<?>> argTypes) {
		return this.findSubscriberMethod(argTypes, true);
	}

	/**
	 * Resolves the unsubscribe delegate interface method matching the given
	 * argument types.
	 *
	 * @param argTypes the unsubscribe call's argument types
	 * @return the matching unsubscribe delegate method
	 */
	private Method findUnsubscribeDelegateMethod(final List<Class<?>> argTypes) {
		return this.findSubscriberMethod(argTypes, false);
	}

	/**
	 * Resubscribes every parked consumer once this producer reactivates,
	 * after letting the base class complete its own activation.
	 */
	@Override
	public void activate() {
		super.activate();
		this.subscribePendingConsumers();
	}

	/**
	 * Parks current subscribers, before letting the base class complete its
	 * own passivation, so events fired once this producer is passivated
	 * cannot reach a subscriber whose delegate has not fully processed.
	 */
	@Override
	public void passivate() {
		this.parkSubscribers();
		super.passivate();
	}

	/**
	 * The lifecycle a single {@code EventSubscription} goes through, from
	 * registration to either steady-state delivery or cancellation.
	 */
	private enum SubscriptionState {
		/** Registered, but the subscribe delegate has not yet been notified/completed. */
		SUBSCRIBING,
		/** Subscribe delegate completed; buffered events are being delivered before activation. */
		DRAINING,
		/** Steady state: events are delivered immediately as they are fired. */
		ACTIVE,
		/** The subscription was dropped (unsubscribed or subscribe delegate failed); buffered events are discarded. */
		CANCELLED
	}

	/**
	 * Tracks one consumer's subscription state and the events fired for it
	 * while it is not yet {@link SubscriptionState#ACTIVE}, so that a
	 * subscribe delegate still running does not miss or reorder events
	 * produced meanwhile.
	 */
	private class EventSubscription {
		/** The subscribed consumer, wrapped with its filtering logic. */
		private final EventsConsumer eventsConsumer;

		/**
		 * Events fired while this subscription is not yet active, in firing order,
		 * at most {@link #subscriptionBufferCapacity} of them.
		 */
		private final Deque<BufferedEvent> bufferedEvents = new ArrayDeque<>();

		/** This subscription's current lifecycle state. */
		private SubscriptionState state;

		/**
		 * Creates a new subscription for the given consumer.
		 *
		 * @param eventsConsumer            the subscribed consumer
		 * @param waitForSubscribeDelegate  {@code true} to start in
		 *                                  {@link SubscriptionState#SUBSCRIBING} and
		 *                                  buffer events until the subscribe
		 *                                  delegate completes; {@code false} to
		 *                                  start directly {@link SubscriptionState#ACTIVE}
		 */
		EventSubscription(final EventsConsumer eventsConsumer, final boolean waitForSubscribeDelegate) {
			this.eventsConsumer = eventsConsumer;
			this.state = waitForSubscribeDelegate ? SubscriptionState.SUBSCRIBING : SubscriptionState.ACTIVE;
		}

		/**
		 * Prepares the delivery of a fired event to this subscription: if
		 * currently active, returns a task that delivers it on
		 * {@link #eventWorker}; otherwise buffers the event and returns
		 * {@code null} (already cancelled, or not yet active). When the
		 * buffer is full, the event is dropped and reported instead, the
		 * producer never being blocked nor failed.
		 *
		 * @param method the called events method
		 * @param args   the method arguments
		 * @return a delivery task to run, or {@code null} if the event was
		 *         buffered or dropped, or the subscription is cancelled
		 */
		Runnable prepareDelivery(final Method method, final Object[] args) {
			BufferedEvent event = new BufferedEvent(method, args);
			synchronized (this) {
				if (this.state == SubscriptionState.CANCELLED) {
					return null;
				}
				if (this.state == SubscriptionState.ACTIVE) {
					return () -> ProducerSideNotifierInvocationHandler.this.eventWorker.execute(new Delivery(event));
				}
				if (this.bufferedEvents.size() < ProducerSideNotifierInvocationHandler.this.subscriptionBufferCapacity
						.getAsInt()) {
					this.bufferedEvents.addLast(event);
					return null;
				}
			}
			// Outside this subscription's lock: reporting the drop logs it.
			ProducerSideNotifierInvocationHandler.this.bufferedEventDropped.run();
			return null;
		}

		/**
		 * Marks the subscribe delegate notification as completed and
		 * transitions this subscription toward active, draining any event
		 * buffered while it was subscribing.
		 *
		 * @return {@code true} if the subscription is now (or already)
		 *         active and should be reported to the consumer; {@code
		 *         false} if it was concurrently cancelled and should be
		 *         dropped instead
		 */
		boolean completeSubscribing() {
			synchronized (this) {
				if (this.state == SubscriptionState.CANCELLED) {
					this.bufferedEvents.clear();
					return false;
				}
				if (this.state != SubscriptionState.SUBSCRIBING) {
					return this.state == SubscriptionState.ACTIVE;
				}
				this.state = SubscriptionState.DRAINING;
			}
			this.drainBufferedEventsBeforeActivation();
			return true;
		}

		/**
		 * Cancels this subscription, discarding any buffered event.
		 */
		void cancel() {
			synchronized (this) {
				this.state = SubscriptionState.CANCELLED;
				this.bufferedEvents.clear();
			}
		}

		/**
		 * Delivers every buffered event, in order, before transitioning this
		 * subscription to {@link SubscriptionState#ACTIVE} - the buffer must
		 * be observed empty under the same lock that flips the state, so a
		 * concurrently delivered event can never overtake a buffered one.
		 */
		private void drainBufferedEventsBeforeActivation() {
			while (true) {
				BufferedEvent event = null;
				synchronized (this) {
					if (this.state == SubscriptionState.CANCELLED) {
						this.bufferedEvents.clear();
						return;
					}
					event = this.bufferedEvents.pollFirst();
					if (event == null) {
						// Activation must happen after the buffer is empty. Otherwise an executor
						// with several threads could deliver a fresh business event before one of
						// the events produced while the subscribe delegate was still running.
						this.state = SubscriptionState.ACTIVE;
						return;
					}
				}
				this.deliver(event);
			}
		}

		/**
		 * Delivers a single event to the wrapped consumer, logging (rather
		 * than propagating) any failure so that one misbehaving consumer
		 * cannot disrupt the delivery of other buffered or subsequent
		 * events.
		 *
		 * @param event the buffered event to deliver
		 */
		private void deliver(final BufferedEvent event) {
			try {
				this.eventsConsumer.notify(event.getMethod(), event.getArgs(), event.getFiredAtNanos());
			} catch (Throwable e) {
				CovalentDiagnostics.severe(log, "Event consumer callback failed.", e);
			}
		}

		/** The delivery of one event, which can tell the consumer method it calls. */
		private final class Delivery implements Runnable, LabeledTask {

			private final BufferedEvent event;

			private Delivery(final BufferedEvent event) {
				this.event = event;
			}

			@Override
			public void run() {
				EventSubscription.this.deliver(this.event);
			}

			@Override
			public String taskLabel() {
				return EventSubscription.this.eventsConsumer.taskLabel(this.event.getMethod());
			}

			@Override
			public Class<?> taskComponent() {
				return EventSubscription.this.eventsConsumer.consumerComponent();
			}
		}
	}

	/**
	 * An events method call captured for later delivery, used to buffer an
	 * event fired while a subscription is not yet
	 * {@link SubscriptionState#ACTIVE}.
	 */
	private class BufferedEvent {
		/** The called events method. */
		private final Method method;

		/** The method arguments, defensively cloned at capture time. */
		private final Object[] args;

		/** When the producer fired the event, as given by {@link System#nanoTime()}. */
		private final long firedAtNanos = System.nanoTime();

		/**
		 * Captures the given method call, cloning its argument array so
		 * later mutation of the original array (e.g. clone replacement for
		 * another firing) cannot affect this buffered copy.
		 *
		 * @param method the called events method
		 * @param args   the method arguments to capture, or {@code null}
		 */
		BufferedEvent(final Method method, final Object[] args) {
			this.method = method;
			this.args = args == null ? null : args.clone();
		}

		/**
		 * Returns when the producer fired the event.
		 *
		 * @return the {@link System#nanoTime()} at which the event was fired
		 */
		long getFiredAtNanos() {
			return this.firedAtNanos;
		}

		/**
		 * Returns the captured events method.
		 *
		 * @return the called events method
		 */
		Method getMethod() {
			return this.method;
		}

		/**
		 * Returns the captured method arguments.
		 *
		 * @return the method arguments, or {@code null}
		 */
		Object[] getArgs() {
			return this.args;
		}
	}

	/**
	 * Wraps a single subscribed consumer's original proxy and its optional
	 * event filter, dispatching an actual method call to it and reporting
	 * call start/finish when instrumentation is enabled.
	 */
	private class EventsConsumer {
		/** The subscribed consumer's metadata (instance, subscribe parameters, resolved component type). */
		private final ConsumerMetadata<EVENTS> consumerMetadata;

		/** This consumer's subscribe parameters, or {@code null} if it subscribed without any. */
		private final SubscribeParameters subscribeParameters;

		/**
		 * Creates a new wrapper for the given consumer, introspecting its
		 * optional event filter's methods.
		 *
		 * @param consumerMetadata the subscribed consumer's metadata
		 */
		public EventsConsumer(final ConsumerMetadata<EVENTS> consumerMetadata) {
			this.consumerMetadata = consumerMetadata;
			this.subscribeParameters = this.consumerMetadata.getSubscribeParameters();
			this.introspectEvents();
		}

		/**
		 * Populates {@link #eventToFilter} by matching each events method to
		 * a same-signature {@code filterXxx} method on the consumer's event
		 * filter (by replacing the {@code on} prefix with {@code filter}), a
		 * no-op if the consumer has no filter.
		 *
		 * @param <FILTER> the event filter's runtime type
		 */
		private <FILTER> void introspectEvents() {
			if (this.subscribeParameters == null) {
				return;
			}
			FILTER filter = this.subscribeParameters.getEventFilter();
			if (filter == null) {
				return;
			}
			@SuppressWarnings("unchecked")
			Class<FILTER> filterType = (Class<FILTER>) filter.getClass();
			for (Method eventMethod : ProducerSideNotifierInvocationHandler.this.eventsInterface.getMethods()) {
				String eventMethodName = eventMethod.getName();
				String filterMethodName = eventMethodName.replaceFirst("on", "filter");
				Method filterMethod = Reflection.lookupOptionalMethod(filterType, filterMethodName,
						eventMethod.getParameterTypes());
				if (filterMethod != null) {
					this.eventToFilter.put(eventMethod, filterMethod);
				}
			}
		}

		/** Events method to matching event-filter method, as resolved by {@link #introspectEvents()}. */
		private final Map<Method, Method> eventToFilter = new HashMap<>();

		/**
		 * Delivers an events method call to the wrapped consumer, unless the
		 * consumer's event filter rejects it, reporting call start/finish
		 * when instrumentation is enabled.
		 *
		 * @param method       the called events method
		 * @param args         the method arguments
		 * @param firedAtNanos when the producer fired the event, as given by
		 *                     {@link System#nanoTime()}
		 * @return always {@code null}
		 * @throws Throwable whatever the consumer's callback throws
		 */
		public Object notify(final Method method, final Object[] args, final long firedAtNanos) throws Throwable {
			if (!this.isFiltered(method, args)) {
				boolean instrumented = ProducerSideNotifierInvocationHandler.this.instrumentationSupplier.getAsBoolean()
						&& !ProducerSideNotifierInvocationHandler.this.eventsInterface.equals(EventsManagerEvents.class);
				Class<?> consumerComponentType = null;
				Class<? extends EVENTS> subscriberType = null;
				if (instrumented) {
					subscriberType = this.resolveSubscriberType();
					consumerComponentType = this.resolveConsumerComponentType(subscriberType);
					ProducerSideNotifierInvocationHandler.this.eventsManagerEventsSupplier.get()
							.onEventNotificationStarted(ProducerSideNotifierInvocationHandler.this.eventsInterface,
									method, ProducerSideNotifierInvocationHandler.this.producerComponentType,
									consumerComponentType, subscriberType,
									ProducerSideNotifierInvocationHandler.this.asynchronous,
									Math.max(0, System.nanoTime() - firedAtNanos), args);
				}
				long startedAt = System.nanoTime();
				Throwable failure = null;
				try {
					method.invoke(this.consumerMetadata.getInstance(), args);
				} catch (InvocationTargetException e) {
					failure = e.getCause();
					throw e;
				} catch (Throwable e) {
					failure = e;
					throw e;
				} finally {
					if (instrumented) {
						ProducerSideNotifierInvocationHandler.this.eventsManagerEventsSupplier.get()
								.onEventNotificationFinished(
										ProducerSideNotifierInvocationHandler.this.eventsInterface, method,
										ProducerSideNotifierInvocationHandler.this.producerComponentType,
										consumerComponentType, subscriberType, System.nanoTime() - startedAt, failure,
										args);
					}
				}
			}
			return null;
		}

		/**
		 * Resolves the class of the object actually receiving events: the
		 * original consumer's runtime class, unwrapped from its
		 * consumer-side proxy.
		 *
		 * @return the subscribed object's class
		 */
		@SuppressWarnings("unchecked")
		private Class<? extends EVENTS> resolveSubscriberType() {
			Class<? extends EVENTS> consumerClass = null;
			EVENTS consumer = this.consumerMetadata.getInstance();
			if (Proxy.isProxyClass(consumer.getClass())) {
				InvocationHandler invocationHandler = Proxy.getInvocationHandler(consumer);
				if (invocationHandler instanceof ConsumerInvocationHandler) {
					consumerClass = (Class<? extends EVENTS>) ((ConsumerInvocationHandler<?>) invocationHandler).getConsumer()
							.getClass();
				}
			}
			RuntimeCheck.notNull(consumerClass, "consumerClass");
			return consumerClass;
		}

		/**
		 * @param method an events method
		 * @return the consumer and the method, as {@code Consumer.method}
		 */
		private String taskLabel(final Method method) {
			Class<?> consumer = this.consumerComponent();
			return consumer == null ? method.getName() : consumer.getSimpleName() + "." + method.getName();
		}

		/**
		 * @return the consuming component, or the subscribed object's class when it is none;
		 *         {@code null} when that class is a proxy, which names nothing
		 */
		private Class<?> consumerComponent() {
			Class<?> consumer = this.resolveConsumerComponentType(this.consumerMetadata.getInstance().getClass());
			return Proxy.isProxyClass(consumer) ? null : consumer;
		}

		/**
		 * Resolves the component type to report for this consumer when
		 * instrumentation is enabled: the metadata's cached component type
		 * if known - the subscribed object's own component type, or else the
		 * component that subscribed it - otherwise the subscribed object's
		 * class.
		 *
		 * @param subscriberType the subscribed object's class
		 * @return the consumer component type to report
		 */
		private Class<?> resolveConsumerComponentType(final Class<?> subscriberType) {
			Class<?> consumerComponentType = this.consumerMetadata.getConsumerComponentType();
			return consumerComponentType != null ? consumerComponentType : subscriberType;
		}

		/**
		 * Reports whether the consumer's event filter rejects the given
		 * events method call, by invoking the matching {@code filterXxx}
		 * method (if any) resolved by {@link #introspectEvents()} and
		 * treating a {@code Boolean} {@code true} result as a rejection.
		 *
		 * @param method the called events method
		 * @param args   the method arguments
		 * @return {@code true} if the event must not be delivered to this consumer
		 * @throws Throwable whatever the filter method throws
		 */
		private boolean isFiltered(final Method method, final Object[] args) throws Throwable {
			boolean filtered = false;
			if (this.subscribeParameters == null) {
				return false;
			}
			Object eventFilter = this.subscribeParameters.getEventFilter();
			if (eventFilter == null) {
				return false;
			}
			Method filterMethod = this.eventToFilter.get(method);
			if (filterMethod != null) {
				Object filterResult = filterMethod.invoke(eventFilter, args);
				if (filterResult instanceof Boolean) {
					filtered = (boolean) filterResult;
				}
			}
			return filtered;
		}
	}

}
