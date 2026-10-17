/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.events.impl;

import org.opales.covalent.container.util.ShutdownResidues;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.opales.covalent.ManagedContainerRelay;
import org.opales.covalent.container.component.ComponentAnnotations;
import org.opales.covalent.container.component.annotations.AccessedParameters;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.directory.NamingServices;
import org.opales.covalent.container.events.EventsLifecycleServices;
import org.opales.covalent.container.events.EventsManager;
import org.opales.covalent.container.events.EventsManagerEvents;
import org.opales.covalent.container.events.EventsManagerParameters;
import org.opales.covalent.container.events.EventsManagerServices;
import org.opales.covalent.container.events.EventsWorkersServices;
import org.opales.covalent.container.events.SubscribeParameters;
import org.opales.covalent.container.events.impl.EventsProxyFactory.UsedSubscriberSurrogateProxyInvocationHandler;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Initialization;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.metamodel.MetamodelPortReadServices;
import org.opales.covalent.container.metamodel.MetamodelReadServices;
import org.opales.covalent.container.raw.impl.RawComponentsImpl;
import org.opales.covalent.container.registry.PortRegistryServices;
import org.opales.covalent.container.util.CovalentDiagnostics;
import org.opales.covalent.container.util.RuntimeCheck;
import org.opales.covalent.container.util.StackTraceCleaner;
import org.opales.covalent.container.validation.ValidationParameters;
import org.opales.covalent.container.validation.ValidationServices;
import org.opales.covalent.container.workers.WorkerPoolKind;
import org.opales.covalent.container.workers.WorkerPoolState;
import org.opales.covalent.container.workers.impl.LabeledTask;
import org.opales.covalent.container.workers.impl.WorkerPoolTracker;

/**
 * Runtime manager for Covalent event producers, consumers, and subscribe
 * proxies.
 * <p>
 * The manager keeps two complementary views of event subscriptions:
 * producer-side notifiers deliver events when a producer exists, while
 * {@code pendingSubscribers} keeps consumer-side proxies waiting for a producer
 * to become available. Pending subscribers may come either from a direct
 * subscribe call made before the first producer exists, or from an existing
 * producer that was deleted or invalidated and will be recreated later.
 * </p>
 */
@Implementation
public class EventsManagerImpl
		implements EventsManager, EventsManagerServices, EventsManagerParameters, EventsWorkersServices {
	private static Logger log = Logger.getLogger(EventsManagerImpl.class.getName());

	@UsedServices
	private ValidationServices useValidationServices;

	@AccessedParameters
	private ValidationParameters accessValidationParameters;

	@UsedServices
	private MetamodelPortReadServices useMetamodelPortReadServices;

	@UsedServices
	private MetamodelReadServices useMetamodelReadServices;

	@UsedServices
	private PortRegistryServices usePortRegistryServices;

	@UsedServices
	private NamingServices useNamingServices;

	@ProducedEvents
	private EventsManagerEvents produceEventsManagerEvents;

	@Item
	private final EventsLifecycleManagerImpl eventsLifecycleManagerImpl;

	private volatile boolean synchronous = false;

	private final Map<Class<?>, Object> producerSideSubscriberProxies = new ConcurrentHashMap<>();

	private final Map<Class<?>, Set<ConsumerMetadata<?>>> pendingSubscribers = new HashMap<>();

	private final Map<Object, Map<Class<?>, Object>> realToProxyUsedSubscriber = new HashMap<>();

	/**
	 * Used for component view. {@code <component type or impl, event interface>}
	 */
	private final Map<Class<?>, Set<Class<?>>> producerToEvent = new ConcurrentHashMap<>();

	private static final int DEFAULT_QUEUE_CAPACITY = 1024;

	/** Bound on how long passivate() waits for the asynchronous worker
	 * pool to actually terminate after being asked to stop - same order
	 * of magnitude as AsyncManagerImpl's own worker termination bound
	 * (CovalentWorkerPool.WORKER_TERMINATION_TIMEOUT_MILLIS). */
	private static final long WORKER_TERMINATION_TIMEOUT_MILLIS = 2000L;

	private final Object lifecycleLock = new Object();

	private ThreadPoolExecutor asynchronousWorker;

	/** Bound on how long shrinking the pool waits for its surplus idle threads to terminate. */
	private static final long SHRINK_WAIT_MILLIS = 1000L;

	/** Pause between two checks while waiting for the surplus idle threads of a shrunk pool. */
	private static final long SHRINK_POLL_NANOS = TimeUnit.MICROSECONDS.toNanos(50);

	/** Whether {@link #startWorkers()} started the threads ahead; otherwise they start on demand. */
	private boolean workersStarted;

	/** The dispatch queue of {@link #asynchronousWorker}, whose capacity changes live. */
	private ResizableQueue asynchronousQueue;

	/**
	 * How many threads back {@link #asynchronousWorker}. A change applies to
	 * the live pool at once, like {@code AsyncManagerImpl.generalWorkerCount}:
	 * new threads start, surplus ones finish their current delivery, then
	 * terminate. Defaults to the host's core count, capped at 4 like the
	 * general pool: 16 threads for event dispatch alone on a 16-core box is
	 * far more than a typical container needs live at once.
	 */
	private volatile int workerCount = Math.min(Runtime.getRuntime().availableProcessors(), 4);

	/**
	 * Capacity of {@link #asynchronousWorker}'s dispatch queue, applied to the
	 * live queue at once, like {@link #workerCount}. Matches
	 * {@code AsyncParameters.setWorkerQueueCapacity}'s own default.
	 */
	private volatile int workerQueueCapacity = DEFAULT_QUEUE_CAPACITY;

	/**
	 * Whether the most recent {@link #passivate()} left the asynchronous
	 * worker pool alive past {@link #WORKER_TERMINATION_TIMEOUT_MILLIS}.
	 * Mirrors {@code AsyncManagerImpl.lastPassivationLeftThreadsAlive};
	 * queried by {@code ContainerReportBuilder} to fold an incomplete
	 * events shutdown into {@code ShutdownReport} as degraded.
	 */
	private volatile boolean lastPassivationLeftThreadsAlive = false;

	/**
	 * Whether at least one event notification has been dropped since the
	 * last {@link #startWorkers()} because the asynchronous dispatch queue was
	 * full. Reset on every {@link #startWorkers()} (unlike
	 * {@link #lastPassivationLeftThreadsAlive}, this can be set at any
	 * point during a run, not only while stopping, so it needs an explicit
	 * per-run reset). As there is no throttling/backpressure QoS on event production, a drop is always a sign of a producer
	 * outpacing its consumers (or a stalled consumer) - an
	 * infrastructure-level malfunction signal, not a business-rule
	 * violation, so it is tracked here rather than through the validation
	 * policy, and folded into {@code ShutdownReport} as degraded by
	 * {@code ContainerReportBuilder}, exactly like
	 * {@link #lastPassivationLeftThreadsAlive} above.
	 */
	private volatile boolean hadRejectedEvents = false;

	/** Whether the deliveries of the asynchronous pool are tracked - see {@link EventsWorkersServices}. */
	private volatile boolean taskTracking = false;

	/** The asynchronous pool's bookkeeping, kept across the pools {@link #installAsynchronousWorker()} creates. */
	private final WorkerPoolTracker asynchronousTracker = new WorkerPoolTracker(() -> this.taskTracking);

	private final Executor synchronousWorker = new SynchronousExecutor();

	private final EventsProxyFactory eventsProxyFactory;

	/**
	 * Creates the manager, along with its {@link EventsProxyFactory} and
	 * {@link EventsLifecycleManagerImpl} collaborators. The proxy factory is
	 * pre-wired with the synchronous worker and the current synchronous
	 * flag so internal-component proxies can be created before
	 * {@link #initialize()} has created the asynchronous worker pool; the
	 * lifecycle manager is linked back to this instance.
	 */
	public EventsManagerImpl() {
		this.eventsProxyFactory = new EventsProxyFactory();
		this.eventsProxyFactory.setSynchronousWorker(this.synchronousWorker);
		this.eventsProxyFactory.setSynchronous(this.synchronous);
		this.eventsProxyFactory.setProducedEventsManagerEvents(() -> this.produceEventsManagerEvents);
		this.eventsProxyFactory.setSubscriptionBuffer(() -> this.workerQueueCapacity, this::onBufferedEventDropped);

		this.eventsLifecycleManagerImpl = new EventsLifecycleManagerImpl();
		this.eventsLifecycleManagerImpl.setEventsManagerImpl(this);
	}

	/**
	 * Returns the lifecycle manager driving activate/passivate and
	 * publish/unpublish transitions for this manager's produced events.
	 *
	 * @return the events lifecycle manager implementation
	 */
	public EventsLifecycleManagerImpl getEventsLifecycleManagerImpl() {
		return this.eventsLifecycleManagerImpl;
	}

	/**
	 * Returns the lifecycle manager as the {@link EventsLifecycleServices}
	 * port it implements, for components that only need that service view.
	 *
	 * @return the events lifecycle services
	 */
	public EventsLifecycleServices getEventsLifecycleServices() {
		return this.eventsLifecycleManagerImpl;
	}

	/**
	 * Sets the service used to validate events interfaces and method
	 * naming conventions, and propagates it to the proxy factory.
	 *
	 * @param validationServices the validation service
	 */
	public void setValidationServices(final ValidationServices validationServices) {
		this.useValidationServices = validationServices;
		this.eventsProxyFactory.setValidationServices(validationServices);
	}

	/**
	 * Sets the parameters controlling how strictly validation is applied,
	 * and propagates them to the proxy factory.
	 *
	 * @param validationParameters the validation parameters
	 */
	public void setValidationParameters(final ValidationParameters validationParameters) {
		this.accessValidationParameters = validationParameters;
		this.eventsProxyFactory.setValidationParameters(validationParameters);
	}

	/**
	 * Sets the service used to read a component's declared ports from the
	 * metamodel, and propagates it to the proxy factory and the lifecycle
	 * manager.
	 *
	 * @param metamodelPortReadServices the metamodel port read service
	 */
	public void setMetamodelPortReadServices(final MetamodelPortReadServices metamodelPortReadServices) {
		this.useMetamodelPortReadServices = metamodelPortReadServices;
		this.eventsProxyFactory.setMetamodelPortReadServices(metamodelPortReadServices);
		this.eventsLifecycleManagerImpl.setMetamodelPortReadServices(metamodelPortReadServices);
	}

	/**
	 * Sets the service used to read metamodel information not scoped to a
	 * single port, and propagates it to the proxy factory.
	 *
	 * @param metamodelReadServices the metamodel read service
	 */
	public void setMetamodelReadServices(final MetamodelReadServices metamodelReadServices) {
		this.useMetamodelReadServices = metamodelReadServices;
		this.eventsProxyFactory.setMetamodelReadServices(metamodelReadServices);
	}

	/**
	 * Sets the service used to look up a producer's subscribe delegate in
	 * the port registry.
	 *
	 * @param portRegistryServices the port registry service
	 */
	public void setPortRegistryServices(final PortRegistryServices portRegistryServices) {
		this.usePortRegistryServices = portRegistryServices;
	}

	/**
	 * Sets the directory service used to publish/unpublish subscribe ports
	 * and look up provided subscribers, and propagates it to the lifecycle
	 * manager.
	 *
	 * @param namingServices the naming service
	 */
	public void setNamingServices(final NamingServices namingServices) {
		this.useNamingServices = namingServices;
		this.eventsLifecycleManagerImpl.setNamingServices(namingServices);
	}

	/**
	 * Sets the supplier that tells notifier proxies, at dispatch time,
	 * whether instrumentation (before/after notification reporting) is
	 * currently enabled, and propagates it to the proxy factory.
	 *
	 * @param instrumentationSupplier supplies whether instrumentation is
	 *                                currently enabled
	 */
	public void setInstrumentationSupplier(final BooleanSupplier instrumentationSupplier) {
		this.eventsProxyFactory.setInstrumentationSupplier(instrumentationSupplier);
	}

	@Override
	public Map<Class<?>, Set<ConsumerMetadata<?>>> getSubscribers() {
		return this.buildObserversSnapshot();
	}

	@Override
	public Map<Class<?>, Set<Class<?>>> getProducerToEvent() {
		Map<Class<?>, Set<Class<?>>> copy = new HashMap<>();
		for (Map.Entry<Class<?>, Set<Class<?>>> entry : this.producerToEvent.entrySet()) {
			copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
		}
		return copy;
	}

	private <EVENTS> Map<Class<?>, Set<ConsumerMetadata<?>>> buildObserversSnapshot() {
		Map<Class<?>, Set<ConsumerMetadata<?>>> snapshot = new HashMap<>();
		for (Map.Entry<Class<?>, Object> entry : this.producerSideSubscriberProxies.entrySet()) {
			@SuppressWarnings("unchecked")
			Class<EVENTS> eventsInterface = (Class<EVENTS>) entry.getKey();
			ProducerSideNotifierInvocationHandler<EVENTS, ?> invocationHandler = this
					.getProducerNotifier(eventsInterface);
			Set<ConsumerMetadata<EVENTS>> consumersMetadata = invocationHandler.getConsumersMetadata();
			snapshot.put(eventsInterface, new HashSet<>(consumersMetadata));
		}
		return snapshot;
	}

	@Override
	public <EVENTS> boolean isSubscribed(final Class<EVENTS> eventsInterface, final EVENTS consumer) {
		Map<Class<?>, Object> eventsToSubscriber = this.realToProxyUsedSubscriber.getOrDefault(consumer,
				new HashMap<>());
		return eventsToSubscriber.containsKey(eventsInterface);
	}

	private <EVENTS, SUBSCRIBE> ProducerSideNotifierInvocationHandler<EVENTS, SUBSCRIBE> getProducerNotifier(
			final Class<EVENTS> eventsInterface) {
		Object proxy = this.producerSideSubscriberProxies.get(eventsInterface);
		RuntimeCheck.notNull(proxy, "proxy");
		@SuppressWarnings("unchecked")
		ProducerSideNotifierInvocationHandler<EVENTS, SUBSCRIBE> invocationHandler = (ProducerSideNotifierInvocationHandler<EVENTS, SUBSCRIBE>) Proxy
				.getInvocationHandler(proxy);
		return invocationHandler;
	}

	@Override
	public <EVENT, SUBSCRIBE> EVENT createProducer(final Class<EVENT> eventsInterface, final Class<?> componentType,
			final Consumer<EVENT> subscribeDelegateConsumer, final Consumer<EVENT> unsubscribeDelegateConsumer) {

		EVENT proxyInstance = this.createProducerNoEvents(eventsInterface, componentType, subscribeDelegateConsumer,
				unsubscribeDelegateConsumer);

		this.produceEventsManagerEvents.onEventsProduced(componentType, eventsInterface);

		return proxyInstance;
	}

	/**
	 * Creates or reuses the producer-side event proxy without emitting the
	 * {@link EventsManagerEvents#onEventsProduced(Class, Class)} management event.
	 * <p>
	 * After the producer-side proxy is available, every subscriber parked for this
	 * events interface is attached to the new notifier. This covers both subscribers
	 * that subscribed before the first producer existed and subscribers parked when a
	 * previous producer was deleted or invalidated.
	 * </p>
	 *
	 * @param <EVENT>                     the produced events interface type.
	 * @param <SUBSCRIBE>                 the subscribe interface type associated
	 *                                    with the events interface.
	 * @param eventsInterface             the events interface produced by the
	 *                                    component.
	 * @param componentType               the component type that produces the
	 *                                    events.
	 * @param subscribeDelegateConsumer   optional delegate called when a consumer
	 *                                    subscribes.
	 * @param unsubscribeDelegateConsumer optional delegate called when a consumer
	 *                                    unsubscribes.
	 * @return the producer-side proxy implementing the events interface.
	 */
	public <EVENT, SUBSCRIBE> EVENT createProducerNoEvents(final Class<EVENT> eventsInterface,
			final Class<?> componentType, final Consumer<EVENT> subscribeDelegateConsumer,
			final Consumer<EVENT> unsubscribeDelegateConsumer) {
		if (this.accessValidationParameters.isCheckEventsMethodNamingConvention()) {
			this.useValidationServices.checkEventMethodNamingConvention(eventsInterface);
		}

		Set<Class<?>> set = this.producerToEvent.getOrDefault(componentType, ConcurrentHashMap.newKeySet());
		if (set.contains(eventsInterface)) {
			String message = "Events " + eventsInterface + " already produced for component " + componentType;
			this.useValidationServices.handleDynamicVerification(message);
		}
		set.add(eventsInterface);
		this.producerToEvent.put(componentType, set);

		@SuppressWarnings("unchecked")
		EVENT proxyInstance = (EVENT) this.getOrCreateProducerSideSubscriberProxy(componentType, eventsInterface,
				subscribeDelegateConsumer, unsubscribeDelegateConsumer);

		ProducerSideNotifierInvocationHandler<EVENT, SUBSCRIBE> invocationHandler = this
				.getProducerNotifier(eventsInterface);

		// Subscribers can be pending because they subscribed before the first producer
		// existed, or because a previous producer was deleted or invalidated.
		Set<ConsumerMetadata<?>> previousConsumers = this.pendingSubscribers.remove(eventsInterface);
		if (previousConsumers != null) {
			for (ConsumerMetadata<?> previousConsumer : previousConsumers) {
				invocationHandler.subscribeAgain(previousConsumer);
			}
		}

		return proxyInstance;
	}

	@Override
	public <EVENTS, SUBSCRIBE> SUBSCRIBE getProvidedSubscriber(final Class<EVENTS> eventsInterface) {
		RuntimeCheck.notNull(eventsInterface, "eventsInterface");
		RuntimeCheck.isTrue(this.producerSideSubscriberProxies.containsKey(eventsInterface), "produced events exists");
		@SuppressWarnings("unchecked")
		SUBSCRIBE providedSubscribe = (SUBSCRIBE) this.producerSideSubscriberProxies.get(eventsInterface);
		return providedSubscribe;
	}

	@Override
	public <EVENTS> boolean isEventsProduced(final Class<EVENTS> eventsInterface) {
		return this.producerSideSubscriberProxies.containsKey(eventsInterface);
	}

	/**
	 * creates the producer side proxy of both subscriber interface and events
	 * producer interface (if not already existing). This subscriber will be called
	 * either manually (with explicit subscribe from the consumer) or automatically
	 * (when the consumer does not use a subscriber).
	 * 
	 * The proxy will also handle subscribe and unsubscribe delegation to producers
	 * which need to be aware when a consumer is subscribing or unsubscribing.
	 * 
	 * @param <EVENT>
	 * @param <SUBSCRIBE>
	 * @param componentType
	 * @param eventsInterface
	 * @param subscribeDelegateConsumer
	 * @param unsubscribeDelegateConsumer
	 * @return
	 */
	private <EVENT, SUBSCRIBE> SUBSCRIBE getOrCreateProducerSideSubscriberProxy(final Class<?> componentType,
			final Class<EVENT> eventsInterface, final Consumer<EVENT> subscribeDelegateConsumer,
			final Consumer<EVENT> unsubscribeDelegateConsumer) {

		SUBSCRIBE subscribeDelegate = this.usePortRegistryServices.getSubscribeDelegate(eventsInterface);

		@SuppressWarnings("unchecked")
		SUBSCRIBE proxyInstance = (SUBSCRIBE) this.producerSideSubscriberProxies.get(eventsInterface);

		if (proxyInstance == null) {

			proxyInstance = this.createProxy(componentType, eventsInterface, subscribeDelegate,
					subscribeDelegateConsumer, unsubscribeDelegateConsumer);

			this.producerSideSubscriberProxies.put(eventsInterface, proxyInstance);
		} else if (subscribeDelegateConsumer != null || unsubscribeDelegateConsumer != null) {
			this.eventsProxyFactory.refreshNotifierWorker(proxyInstance, componentType, eventsInterface);
			// if a notifier was automatically created and the component implementation
			// wants
			// to use a specific notifier with subscribe delegate consumer, we must replace
			// the default
			// instance
			log.fine("Replacing previous notifier because it needs a subscribe or unsubscribe consumer");
			@SuppressWarnings("unchecked")
			ProducerSideNotifierInvocationHandler<EVENT, SUBSCRIBE> previousIH = (ProducerSideNotifierInvocationHandler<EVENT, SUBSCRIBE>) Proxy
					.getInvocationHandler(proxyInstance);
			if ((previousIH.getSubscribeDelegateConsumer() == null && subscribeDelegateConsumer != null)
					|| (previousIH.getUnSubscribeDelegateConsumer() == null && unsubscribeDelegateConsumer != null))
				proxyInstance = this.createProxy(componentType, eventsInterface, subscribeDelegate,
						subscribeDelegateConsumer, unsubscribeDelegateConsumer);
		} else {
			this.eventsProxyFactory.refreshNotifierWorker(proxyInstance, componentType, eventsInterface);
		}

		return proxyInstance;
	}

	private <EVENT, SUBSCRIBE> SUBSCRIBE createProxy(final Class<?> componentType, final Class<EVENT> eventsInterface,
			final SUBSCRIBE subscribeDelegate, final Consumer<EVENT> subscribeDelegateConsumer,
			final Consumer<EVENT> unsubscribeDelegateConsumer) {
		SUBSCRIBE proxyInstance = null;
		Class<SUBSCRIBE> subscribeInterface = this.useMetamodelPortReadServices.getSubscribeInterface(eventsInterface);
		proxyInstance = this.eventsProxyFactory.newNotifierProxy(eventsInterface, subscribeInterface, subscribeDelegate,
				componentType, subscribeDelegateConsumer, unsubscribeDelegateConsumer);
		this.producerSideSubscriberProxies.put(eventsInterface, proxyInstance);
		return proxyInstance;
	}

	@Override
	public <EVENT> void activateProducer(final Class<EVENT> eventsInterface) {
		this.getProducerNotifier(eventsInterface).activate();
	}

	@Override
	public <EVENT> void passivateProducer(final Class<EVENT> eventsInterface) {
		this.getProducerNotifier(eventsInterface).passivate();
	}

	@Override
	public <EVENT, SUBSCRIBE> void deleteProducer(final Class<?> componentType, final Class<EVENT> eventsInterface) {

		Set<Class<?>> set = this.producerToEvent.get(componentType);
		if (set == null) {
			String message = "Trying to delete producer " + componentType + ", but it does not produce events.";
			this.useValidationServices.handleDynamicVerification(message);
			return;
		}
		if (!set.contains(eventsInterface)) {
			String message = "Trying to delete producer " + componentType + " but it does not produce "
					+ eventsInterface;
			this.useValidationServices.handleDynamicVerification(message);
			return;
		}
		set.remove(eventsInterface);
		if (set.size() == 0) {
			this.producerToEvent.remove(componentType);
		}

		ProducerSideNotifierInvocationHandler<EVENT, SUBSCRIBE> invocationHandler = this
				.getProducerNotifier(eventsInterface);

		// store current subscribers into pending subscribers
		// there can remains consumers to park if a raw component calls unproduceEvents
		// before its passivation
		invocationHandler.parkSubscribers();
		invocationHandler.unsubscribeAll();

		Set<ConsumerMetadata<?>> pendingSubscribers = invocationHandler.getPendingSubscribers();
		this.pendingSubscribers.put(eventsInterface, pendingSubscribers);
		invocationHandler.invalidate();

		this.produceEventsManagerEvents.onEventsUnproduced(componentType, eventsInterface);

		this.producerSideSubscriberProxies.remove(eventsInterface);
	}

	@Override
	public <EVENTS, SUBSCRIBE> SUBSCRIBE createUsedSubscriber(final Class<EVENTS> eventsInterface,
			final Class<SUBSCRIBE> subscribeInterface, final Class<?> subscribingComponentType) {
		SUBSCRIBE usedSubscriber = this.createUsedSubscriber(eventsInterface, subscribeInterface);
		@SuppressWarnings("unchecked")
		UsedSubscriberInvocationHandler<EVENTS, SUBSCRIBE> invocationHandler = (UsedSubscriberInvocationHandler<EVENTS, SUBSCRIBE>) Proxy
				.getInvocationHandler(usedSubscriber);
		invocationHandler.setSubscribingComponentType(subscribingComponentType);
		return usedSubscriber;
	}

	@Override
	public <EVENTS, SUBSCRIBE> SUBSCRIBE createUsedSubscriber(final Class<EVENTS> eventsInterface,
			final Class<SUBSCRIBE> subscribeInterface) {
		SUBSCRIBE providedSubscribe = null;
		boolean useSurrogate = false;
		if (this.useNamingServices.isSubscriberBound(eventsInterface)) {
			// lookup provider-side proxy
			providedSubscribe = this.useNamingServices.lookupSubscriber(eventsInterface);
		} else {
			// temporarily replace missing provider-side proxy with surrogate proxy
			log.fine("Subscriber not published in directory. Using surrogate instead");
			providedSubscribe = this.eventsProxyFactory.newSurrogateProxy(eventsInterface, subscribeInterface,
					this.useNamingServices);
			useSurrogate = true;
		}

		// create user-side proxy
		SUBSCRIBE usedSubscriber = this.eventsProxyFactory.newUsedSubscriberProxy(eventsInterface, subscribeInterface,
				providedSubscribe);

		if (useSurrogate) {
			@SuppressWarnings("unchecked")
			UsedSubscriberSurrogateProxyInvocationHandler<EVENTS, SUBSCRIBE> surrogateInvocationHandler = (UsedSubscriberSurrogateProxyInvocationHandler<EVENTS, SUBSCRIBE>) Proxy
					.getInvocationHandler(providedSubscribe);
			@SuppressWarnings("unchecked")
			UsedSubscriberInvocationHandler<EVENTS, SUBSCRIBE> userSideInvocationHandler = (UsedSubscriberInvocationHandler<EVENTS, SUBSCRIBE>) Proxy
					.getInvocationHandler(usedSubscriber);
			surrogateInvocationHandler.setUserSideInvocationHandler(userSideInvocationHandler);
		}
		return usedSubscriber;
	}

	/**
	 * Subscribes a real consumer to an events interface.
	 * <p>
	 * If the producer-side notifier already exists, the subscription goes through
	 * the standard used-subscribe proxy so filters, subscribe delegates, and
	 * validation stay on the normal path. If no producer exists yet, the method
	 * creates the consumer-side events proxy immediately and parks it in
	 * {@code pendingSubscribers}; it will be attached when the producer is created.
	 * </p>
	 *
	 * @param <EVENTS>            the events interface type.
	 * @param <SUBSCRIBE>         the subscribe interface type associated with the
	 *                            events interface.
	 * @param eventsInterface     the events interface to subscribe to.
	 * @param consumer            the real consumer instance.
	 * @param subscribeParameters optional subscribe parameters such as an event
	 *                            filter.
	 */
	@Override
	public <EVENTS, SUBSCRIBE> void subscribe(final Class<EVENTS> eventsInterface, final EVENTS consumer,
			final SubscribeParameters subscribeParameters) {
		Map<Class<?>, Object> eventsToSubscriber = this.realToProxyUsedSubscriber.getOrDefault(consumer,
				new HashMap<>());

		if (eventsToSubscriber.get(eventsInterface) != null) {
			String message = "Consumer " + consumer + " has already subscribed to events: " + eventsInterface;
			this.useValidationServices.handleDynamicVerification(message);
			return;
		}

		if (!this.producerSideSubscriberProxies.containsKey(eventsInterface)) {
			ConsumerMetadata<EVENTS> pendingSubscriber = this.addPendingSubscriber(eventsInterface, consumer,
					subscribeParameters);
			eventsToSubscriber.put(eventsInterface, pendingSubscriber);
			this.realToProxyUsedSubscriber.put(consumer, eventsToSubscriber);
			return;
		}

		Class<SUBSCRIBE> subscribeInterface = this.useMetamodelPortReadServices.getSubscribeInterface(eventsInterface);
		SUBSCRIBE usedSubscriber = this.createUsedSubscriber(eventsInterface, subscribeInterface);

		// extract invocation handler
		@SuppressWarnings("unchecked")
		UsedSubscriberInvocationHandler<EVENTS, SUBSCRIBE> invocationHandler = (UsedSubscriberInvocationHandler<EVENTS, SUBSCRIBE>) Proxy
				.getInvocationHandler(usedSubscriber);
		try {
			// manually subscribe to consumer side invocation handler
			invocationHandler.subscribe(consumer, subscribeParameters);
		} catch (Throwable t) {
			Throwable cause;
			if (t instanceof InvocationTargetException) {
				cause = t.getCause();
			} else {
				cause = t;
			}
			throw StackTraceCleaner.cleanIfEnabled(new RuntimeException("Exception while subscribing",
					StackTraceCleaner.cleanIfEnabled(cause)));
		}

		eventsToSubscriber.put(eventsInterface, usedSubscriber);
		this.realToProxyUsedSubscriber.put(consumer, eventsToSubscriber);

	}

	/**
	 * Creates a pending subscriber entry for a direct subscribe call made before a
	 * producer-side notifier exists.
	 * <p>
	 * The stored instance is the consumer-side events proxy, not the real consumer.
	 * This is the same proxy shape used by normal subscriptions, so attaching the
	 * pending subscriber later can call the producer-side notifier directly.
	 * </p>
	 *
	 * @param <EVENTS>            the events interface type.
	 * @param eventsInterface     the events interface to subscribe to.
	 * @param consumer            the real consumer instance.
	 * @param subscribeParameters optional subscribe parameters such as an event
	 *                            filter.
	 * @return the metadata stored in {@code pendingSubscribers} and in the
	 *         real-consumer subscription index.
	 */
	private <EVENTS> ConsumerMetadata<EVENTS> addPendingSubscriber(final Class<EVENTS> eventsInterface,
			final EVENTS consumer, final SubscribeParameters subscribeParameters) {
		EVENTS consumerProxy = this.eventsProxyFactory.newConsumerProxy(eventsInterface, consumer);
		ConsumerMetadata<EVENTS> metadata = new ConsumerMetadata<>();
		metadata.setInstance(consumerProxy);
		metadata.setSubscribeParameters(subscribeParameters);

		Set<ConsumerMetadata<?>> pending = this.pendingSubscribers.getOrDefault(eventsInterface, new HashSet<>());
		pending.add(metadata);
		this.pendingSubscribers.put(eventsInterface, pending);
		return metadata;
	}

	/**
	 * Removes a metadata entry from the pending subscriber list of an events
	 * interface.
	 *
	 * @param eventsInterface the events interface whose pending subscriber list must
	 *                        be updated.
	 * @param metadata        the pending subscriber metadata to remove.
	 * @return {@code true} when the metadata was still pending and has been removed.
	 */
	private boolean removePendingSubscriber(final Class<?> eventsInterface, final ConsumerMetadata<?> metadata) {
		Set<ConsumerMetadata<?>> pending = this.pendingSubscribers.get(eventsInterface);
		if (pending == null) {
			return false;
		}

		boolean removed = pending.remove(metadata);
		if (pending.isEmpty()) {
			this.pendingSubscribers.remove(eventsInterface);
		}
		return removed;
	}

	/**
	 * Unsubscribes a consumer whose registration was originally created while no
	 * producer existed.
	 * <p>
	 * If the subscriber is still pending, removing it from {@code pendingSubscribers}
	 * is enough. If a producer has since been created, the pending metadata has
	 * already been consumed and attached to the producer-side notifier, so the same
	 * consumer-side proxy must be removed from that notifier.
	 * </p>
	 *
	 * @param <EVENTS>        the events interface type.
	 * @param <SUBSCRIBE>     the subscribe interface type associated with the
	 *                        events interface.
	 * @param eventsInterface the events interface to unsubscribe from.
	 * @param metadata        the metadata originally stored for the pending
	 *                        subscription.
	 */
	private <EVENTS, SUBSCRIBE> void unsubscribePendingSubscriber(final Class<EVENTS> eventsInterface,
			final ConsumerMetadata<?> metadata) {
		if (this.removePendingSubscriber(eventsInterface, metadata)) {
			return;
		}

		if (this.producerSideSubscriberProxies.containsKey(eventsInterface)) {
			ProducerSideNotifierInvocationHandler<EVENTS, SUBSCRIBE> invocationHandler = this
					.getProducerNotifier(eventsInterface);
			@SuppressWarnings("unchecked")
			EVENTS consumerProxy = (EVENTS) metadata.getInstance();
			invocationHandler.unsubscribe(consumerProxy);
		}
	}

	/**
	 * Unsubscribes a real consumer from an events interface.
	 * <p>
	 * Normal subscriptions are removed through the used-subscribe proxy that created
	 * them. Subscriptions that were first parked before a producer existed are
	 * removed either from the pending list or, if the producer has appeared since
	 * then, directly from the producer-side notifier.
	 * </p>
	 *
	 * @param <EVENTS>        the events interface type.
	 * @param <SUBSCRIBE>     the subscribe interface type associated with the
	 *                        events interface.
	 * @param eventsInterface the events interface to unsubscribe from.
	 * @param consumer        the real consumer instance.
	 */
	@Override
	public <EVENTS, SUBSCRIBE> void unsubscribe(final Class<EVENTS> eventsInterface, final EVENTS consumer) {
		Map<Class<?>, Object> eventsToSubscriber = this.realToProxyUsedSubscriber.get(consumer);

		Object subscriberRegistration = null;
		if (eventsToSubscriber != null) {
			subscriberRegistration = eventsToSubscriber.remove(eventsInterface);
			if (eventsToSubscriber.size() == 0) {
				this.realToProxyUsedSubscriber.remove(consumer);
			}
		}
		if (subscriberRegistration == null) {
			String message = "Trying to unsubscribe " + consumer + " from events " + eventsInterface.getSimpleName()
					+ ", but it never subscribed.";
			this.useValidationServices.handleDynamicVerification(message);
			return;
		}

		if (subscriberRegistration instanceof ConsumerMetadata<?>) {
			this.unsubscribePendingSubscriber(eventsInterface, (ConsumerMetadata<?>) subscriberRegistration);
			return;
		}

		@SuppressWarnings("unchecked")
		SUBSCRIBE usedSubscriber = (SUBSCRIBE) subscriberRegistration;
		RuntimeCheck.notNull(usedSubscriber, "usedSubscriber");
		// extract invocation handler
		@SuppressWarnings("unchecked")
		UsedSubscriberInvocationHandler<EVENTS, SUBSCRIBE> invocationHandler = (UsedSubscriberInvocationHandler<EVENTS, SUBSCRIBE>) Proxy
				.getInvocationHandler(usedSubscriber);
		try {
			// manually unsubscribe from consumer side invocation handler
			invocationHandler.unsubscribe(consumer);
		} catch (Throwable t) {
			Throwable cause;
			if (t instanceof InvocationTargetException) {
				cause = t.getCause();
			} else {
				cause = t;
			}
			throw StackTraceCleaner.cleanIfEnabled(new RuntimeException("Exception while unsubscribing",
					StackTraceCleaner.cleanIfEnabled(cause)));
		}
	}

	@Override
	public SubscribeParameters newSubscribeParameters() {
		return new SubscribeParametersImpl();
	}

	/**
	 * By default, event manager creates asynchronous publishers, but in some cases
	 * it is not required. This method allows to fire synchronous event.
	 *
	 * @param synchronous {@code true} to dispatch every subsequently
	 *                    created producer's notifications synchronously
	 */
	@Override
	public void setSynchronous(final boolean synchronous) {
		this.synchronous = synchronous;
		this.eventsProxyFactory.setSynchronous(synchronous);
	}

	@Override
	public boolean isSynchronous() {
		return this.synchronous;
	}

	@Override
	public void setWorkerCount(final int workerCount) {
		if (workerCount < 1) {
			throw new IllegalArgumentException("workerCount must be at least 1.");
		}
		synchronized (this.lifecycleLock) {
			this.workerCount = workerCount;
			ThreadPoolExecutor worker = this.asynchronousWorker;
			if (worker != null && !worker.isShutdown()) {
				// the maximum never goes below the core size: raised first when
				// growing, lowered last when shrinking
				if (workerCount > worker.getMaximumPoolSize()) {
					worker.setMaximumPoolSize(workerCount);
					worker.setCorePoolSize(workerCount);
					if (this.workersStarted) {
						worker.prestartAllCoreThreads();
					}
				} else {
					worker.setCorePoolSize(workerCount);
					worker.setMaximumPoolSize(workerCount);
					awaitIdleSurplusTerminated(worker, workerCount);
				}
			}
		}
	}

	@Override
	public int getWorkerCount() {
		return this.workerCount;
	}

	@Override
	public void setWorkerQueueCapacity(final int queueCapacity) {
		if (queueCapacity < 1) {
			throw new IllegalArgumentException("queueCapacity must be at least 1.");
		}
		synchronized (this.lifecycleLock) {
			this.workerQueueCapacity = queueCapacity;
			if (this.asynchronousQueue != null) {
				this.asynchronousQueue.setCapacity(queueCapacity);
			}
		}
	}

	@Override
	public int getWorkerQueueCapacity() {
		return this.workerQueueCapacity;
	}

	/**
	 * Container passivation hook: clears pending subscribers and shuts down
	 * the asynchronous worker pool.
	 * <p>
	 * Business and raw components are expected to have already been torn down
	 * by the time events management is passivated: only the internal
	 * components' produced events, {@code RawComponentsImpl}'s subscription to
	 * {@code LifecycleEvents} and the managed container relay's subscription
	 * (which survives a container restart) remain. This method does not check
	 * it.
	 * </p>
	 */
	@Passivation
	public void passivate() {
		// remove pending subscribers
		this.pendingSubscribers.clear();

		ExecutorService workerToStop;
		synchronized (this.lifecycleLock) {
			workerToStop = this.asynchronousWorker;
			this.asynchronousWorker = null;
		}
		// A container is never truly passivated while its event dispatch
		// worker threads are still alive - mirrors AsyncManagerImpl.passivate(),
		// which blocks (bounded) the same way for its own worker pool. This flag
		// records whether that fully succeeded, for ContainerReportBuilder.
		this.lastPassivationLeftThreadsAlive = !this.stopWorker(workerToStop);
	}

	/**
	 * Lists what is left behind once the business components are torn down: every
	 * entry describes something that is not an internal component. Run by the
	 * container's {@code stop()}, which reports the result in the shutdown report.
	 *
	 * @return the residues found, empty when only internal components remain
	 */
	public List<String> listNonInternalResidues() {
		List<String> residues = new ArrayList<>();
		List<Class<?>> internalEvents = new ArrayList<>();
		for (Map.Entry<Class<?>, Set<Class<?>>> entry : this.producerToEvent.entrySet()) {
			ShutdownResidues.addIfNotInternal(residues, "EventsManager", "events still produced by", entry.getKey());
			internalEvents.addAll(entry.getValue());
		}
		for (Class<?> eventsInterface : this.producerSideSubscriberProxies.keySet()) {
			if (!internalEvents.contains(eventsInterface)) {
				ShutdownResidues.add(residues, "EventsManager",
						"producer-side proxy still present for non-internal events: " + eventsInterface.getName());
			}
		}
		// Only Covalent's own subscribers remain, for the container's whole life:
		// RawComponentsImpl to LifecycleEvents and, on a managed container, its
		// ManagedContainerRelay to ContainerLifecycleEvents.
		int rawComponentsSubscribers = 0;
		int relaySubscribers = 0;
		for (Object subscriber : this.realToProxyUsedSubscriber.keySet()) {
			if (subscriber instanceof RawComponentsImpl) {
				rawComponentsSubscribers++;
			} else if (subscriber instanceof ManagedContainerRelay) {
				relaySubscribers++;
			} else {
				ShutdownResidues.add(residues, "EventsManager",
						"subscriber still present that is not one of Covalent's own: " + subscriber);
			}
		}
		if (rawComponentsSubscribers != 1 || relaySubscribers > 1) {
			ShutdownResidues.add(residues, "EventsManager", "unexpected internal subscribers ("
					+ rawComponentsSubscribers + " raw components, " + relaySubscribers + " relays)");
		}
		return residues;
	}


	/**
	 * Stops a worker pool: queued notifications discarded, running deliveries
	 * interrupted, and a bounded wait for its threads.
	 *
	 * @return {@code true} if every thread terminated within the bound
	 */
	private boolean stopWorker(final ExecutorService workerToStop) {
		if (workerToStop == null) {
			return true;
		}
		for (Runnable neverRun : workerToStop.shutdownNow()) {
			dropIfDroppable(neverRun);
		}
		return this.awaitWorkerTerminated(workerToStop);
	}

	/**
	 * Blocks (bounded) until the asynchronous worker pool has actually
	 * terminated, instead of only requesting interruption
	 * ({@code shutdownNow()}) and returning immediately - so a container is
	 * never observed as stopped while its event worker threads are still
	 * alive. A worker between tasks - or running a task that honors
	 * interruption - terminates within microseconds; exceeding the bound
	 * is a sign of a consumer callback that does not honor interruption,
	 * logged rather than thrown so a single misbehaving callback cannot
	 * hang container shutdown forever.
	 *
	 * @param worker the asynchronous worker pool to await termination of
	 * @return {@code true} if the pool terminated within the bound
	 */
	private boolean awaitWorkerTerminated(final ExecutorService worker) {
		boolean terminated;
		try {
			terminated = worker.awaitTermination(WORKER_TERMINATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			// Preserve the calling thread's own interrupted status rather
			// than swallowing it; treat an interrupted wait as inconclusive,
			// i.e. not confirmed terminated.
			Thread.currentThread().interrupt();
			terminated = false;
		}
		if (!terminated) {
			CovalentDiagnostics.severe(log, "Events asynchronous worker pool did not terminate within "
					+ WORKER_TERMINATION_TIMEOUT_MILLIS
					+ "ms of being stopped; it is likely running a consumer callback that does not honor interruption.",
					null);
		}
		return terminated;
	}

	/**
	 * Not part of {@link EventsManagerServices}/{@link EventsManagerParameters}:
	 * an implementation-detail query used only by {@code CovalentContainerImpl}
	 * (a composed component, in a different package, holding a direct
	 * reference to this concrete class) to fold an incomplete events
	 * shutdown into {@code ShutdownReport}. Mirrors
	 * {@code AsyncManagerImpl.hadIncompleteShutdown()}. Public only for that
	 * cross-package access, not meant as public API.
	 *
	 * @see #lastPassivationLeftThreadsAlive
	 * @return {@code true} if the most recent {@link #passivate()} left the
	 *         asynchronous worker pool alive past its termination bound
	 */
	public boolean hadIncompleteShutdown() {
		return this.lastPassivationLeftThreadsAlive;
	}

	/**
	 * Not part of {@link EventsManagerServices}/{@link EventsManagerParameters}:
	 * same cross-package implementation-detail query as
	 * {@link #hadIncompleteShutdown()}, but for the asynchronous dispatch
	 * queue having rejected at least one event notification since the last
	 * {@link #startWorkers()} because it was full.
	 *
	 * @see #hadRejectedEvents
	 * @return {@code true} if at least one event notification has been
	 *         dropped since the last {@link #startWorkers()} because the queue
	 *         was full
	 */
	public boolean hadRejectedEvents() {
		return this.hadRejectedEvents;
	}

	/**
	 * Creates the asynchronous worker pool, without any thread yet, and hands
	 * it to the proxy factory: its threads are started by
	 * {@link #startWorkers()} or on demand, at the first asynchronous
	 * notification.
	 */
	@Initialization
	public void initialize() {
		synchronized (this.lifecycleLock) {
			this.installAsynchronousWorker();
		}
	}

	/**
	 * Recreates the asynchronous worker pool if a previous passivation
	 * released it. Idempotent.
	 */
	@Activation
	public void activate() {
		synchronized (this.lifecycleLock) {
			if (this.asynchronousWorker == null || this.asynchronousWorker.isShutdown()) {
				this.installAsynchronousWorker();
			}
		}
	}

	/**
	 * Starts the pool's threads ahead of the business components' activity:
	 * called by the container's {@code start()}, which also starts a new
	 * period for {@link #hadRejectedEvents()}. Threads already started on
	 * demand are kept.
	 */
	public void startWorkers() {
		synchronized (this.lifecycleLock) {
			this.hadRejectedEvents = false;
			this.workersStarted = true;
			this.asynchronousWorker.prestartAllCoreThreads();
		}
	}

	/**
	 * Empties the pool of the business components' notifications, at the
	 * container's {@code stop()}: queued ones discarded, running deliveries
	 * interrupted, every thread stopped with a bounded wait. The pool is
	 * replaced by a new one without threads, and the subscriptions still
	 * waiting for a producer are forgotten. Records whether every thread
	 * terminated, read by {@link #hadIncompleteShutdown()}.
	 */
	public void stopWorkers() {
		ExecutorService workerToStop;
		synchronized (this.lifecycleLock) {
			this.pendingSubscribers.clear();
			workerToStop = this.asynchronousWorker;
			this.workersStarted = false;
			this.installAsynchronousWorker();
		}
		this.lastPassivationLeftThreadsAlive = !this.stopWorker(workerToStop);
	}

	private void installAsynchronousWorker() {
		this.asynchronousWorker = this.createAsynchronousWorker();
		this.eventsProxyFactory.setAsynchronousWorker(this.asynchronousWorker);
	}

	/**
	 * Waits (bounded) for the surplus idle threads of a shrunk pool to
	 * terminate: the pool interrupts them, but one of them could otherwise
	 * still take a notification right after the setter returns. Threads busy
	 * with a delivery finish it first and are not waited for.
	 */
	private static void awaitIdleSurplusTerminated(final ThreadPoolExecutor worker, final int workerCount) {
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SHRINK_WAIT_MILLIS);
		while (worker.getPoolSize() > Math.max(workerCount, worker.getActiveCount())
				&& System.nanoTime() < deadline) {
			LockSupport.parkNanos(SHRINK_POLL_NANOS);
		}
	}

	private ThreadPoolExecutor createAsynchronousWorker() {
		AtomicInteger workerIndex = new AtomicInteger();
		ThreadFactory namedWorkerThreadFactory = runnable -> {
			Thread worker = new Thread(() -> {
				WorkerPoolTracker.Worker tracked = this.asynchronousTracker.join();
				try {
					runnable.run();
				} finally {
					this.asynchronousTracker.leave(tracked);
				}
			}, "covalent-events-async-" + workerIndex.getAndIncrement());
			// Daemon, like AsyncManagerImpl's own worker pool: a caller that
			// starts a container and lets its own thread run to completion
			// without ever calling stop() must not have the JVM hang forever
			// waiting on these worker threads.
			worker.setDaemon(true);
			return worker;
		};
		int currentWorkerCount = this.workerCount;
		this.asynchronousQueue = new ResizableQueue(this.workerQueueCapacity);
		return new TrackedExecutor(currentWorkerCount, this.asynchronousQueue, namedWorkerThreadFactory,
				this::onEventRejected, this.asynchronousTracker);
	}

	/**
	 * Invoked by the asynchronous worker pool when its bounded queue is
	 * full: logs the drop and records it (see {@link #hadRejectedEvents}),
	 * but never throws - event delivery stays strictly fire-and-forget for
	 * the producer even under sustained overload, so the producer's own
	 * call (which only ever goes through {@code Executor.execute()}, with
	 * nothing here to catch) is never impacted. As there is no throttling/backpressure QoS on event production, this is
	 * always a sign of a producer outpacing its consumers (or a stalled
	 * consumer), not an expected condition - hence the fixed
	 * {@code SEVERE} severity, independent of the validation policy, which
	 * governs business/structural rules, not infrastructure malfunctions
	 * like this one.
	 *
	 * @param task     the event delivery task that could not be queued
	 * @param executor the asynchronous worker pool that rejected it
	 */
	private void onEventRejected(final Runnable task, final ThreadPoolExecutor executor) {
		this.hadRejectedEvents = true;
		this.asynchronousTracker.taskRejected();
		CovalentDiagnostics.severe(log, "Event notification dropped: the asynchronous dispatch queue is full "
				+ "(capacity " + this.workerQueueCapacity + "). A producer is outpacing its consumers, "
				+ "or a consumer callback is stalled.");
		dropIfDroppable(task);
	}

	/**
	 * Invoked when an event is dropped because the buffer of a subscription
	 * whose subscribe delegate notification is still pending is full - that
	 * buffer holds at most {@code workerQueueCapacity} events. Records and
	 * logs the drop exactly like {@link #onEventRejected}, and never throws to
	 * the producer.
	 */
	private void onBufferedEventDropped() {
		this.hadRejectedEvents = true;
		this.asynchronousTracker.taskRejected();
		CovalentDiagnostics.severe(log, "Event notification dropped: a subscription still waiting for its subscribe "
				+ "delegate has buffered " + this.workerQueueCapacity + " events (the dispatch queue capacity). "
				+ "The subscribe delegate is stalled, or a producer is outpacing it.");
	}

	/** Tells the submitter of a dropped task, if it waits for it, that it will never run. */
	private static void dropIfDroppable(final Runnable task) {
		Runnable submitted = task instanceof QueuedTask queued ? queued.task() : task;
		if (submitted instanceof DroppableTask droppable) {
			droppable.dropped();
		}
	}

	@Override
	public List<WorkerPoolState> readWorkerPools() {
		ResizableQueue queue;
		synchronized (this.lifecycleLock) {
			if (this.synchronous || this.asynchronousWorker == null || this.asynchronousWorker.isShutdown()) {
				return List.of();
			}
			queue = this.asynchronousQueue;
		}
		WorkerPoolTracker tracker = this.asynchronousTracker;
		return List.of(new WorkerPoolState("events-async", WorkerPoolKind.EVENTS, this.workerCount, queue.size(),
				queue.capacity, tracker.rejectedTasks(), 0L, this.taskTracking, tracker.completedTasks(),
				tracker.queueWaits(), tracker.durations(), tracker.componentBusyNanos(), tracker.workers()));
	}

	@Override
	public void setTaskTracking(final boolean tracking) {
		this.taskTracking = tracking;
	}

	@Override
	public boolean isTaskTracking() {
		return this.taskTracking;
	}

	/**
	 * The asynchronous pool, telling its tracker when a delivery is queued,
	 * starts and ends. While tracking is off, a delivery is queued as is.
	 */
	private static final class TrackedExecutor extends ThreadPoolExecutor {

		private final WorkerPoolTracker tracker;

		private TrackedExecutor(final int workerCount, final ResizableQueue queue, final ThreadFactory threadFactory,
				final RejectedExecutionHandler rejectionHandler, final WorkerPoolTracker tracker) {
			super(workerCount, workerCount, 0L, TimeUnit.MILLISECONDS, queue, threadFactory, rejectionHandler);
			this.tracker = tracker;
		}

		@Override
		public void execute(final Runnable task) {
			super.execute(this.tracker.isTracking() ? new QueuedTask(task, System.nanoTime()) : task);
		}

		@Override
		protected void beforeExecute(final Thread thread, final Runnable task) {
			super.beforeExecute(thread, task);
			long enqueued = task instanceof QueuedTask queued ? queued.enqueuedNanos : WorkerPoolTracker.UNKNOWN;
			this.tracker.taskStarted(this.tracker.currentWorker(), task, enqueued);
		}

		@Override
		protected void afterExecute(final Runnable task, final Throwable failure) {
			this.tracker.taskFinished(this.tracker.currentWorker());
			super.afterExecute(task, failure);
		}
	}

	/** A delivery queued while tracking was on, with the time it was queued. */
	private record QueuedTask(Runnable task, long enqueuedNanos) implements Runnable, LabeledTask {

		@Override
		public void run() {
			this.task.run();
		}

		@Override
		public String taskLabel() {
			return this.task instanceof LabeledTask labeled ? labeled.taskLabel() : null;
		}

		@Override
		public Class<?> taskComponent() {
			return this.task instanceof LabeledTask labeled ? labeled.taskComponent() : null;
		}
	}

	static class SynchronousExecutor implements Executor {
		@Override
		public void execute(final Runnable command) {
			command.run();
		}
	}

	/**
	 * A dispatch queue whose capacity can change while the pool runs. The
	 * worker pool only ever adds through {@link #offer(Runnable)}, which
	 * refuses a notification once the queue holds {@code capacity} of them -
	 * the pool then rejects it, see {@link #onEventRejected}. Notifications
	 * already queued are kept even beyond a reduced capacity.
	 */
	private static final class ResizableQueue extends LinkedBlockingQueue<Runnable> {

		private static final long serialVersionUID = 1L;

		private volatile int capacity;

		private ResizableQueue(final int capacity) {
			this.capacity = capacity;
		}

		private void setCapacity(final int capacity) {
			this.capacity = capacity;
		}

		@Override
		public synchronized boolean offer(final Runnable notification) {
			return this.size() < this.capacity && super.offer(notification);
		}
	}

}
