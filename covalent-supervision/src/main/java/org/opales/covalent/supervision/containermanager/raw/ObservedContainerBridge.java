/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.containermanager.raw;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.opales.covalent.supervision.SupervisionThreads;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.events.EventsManagerServices;
import org.opales.covalent.container.events.SubscribeParameters;
import org.opales.covalent.container.events.impl.ConsumerMetadata;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.workers.WorkerPoolKind;
import org.opales.covalent.container.workers.WorkerPoolState;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.lifecycle.DependencyGraph;
import org.opales.covalent.container.metamodel.metamodel.ReadOnlyComponentMetamodel;
import org.opales.covalent.container.rr.parameters.ParametersManagerServices;
import org.opales.covalent.container.rr.services.ServicesManagerServices;
import org.opales.covalent.supervision.observed.DataPrinter;
import org.opales.covalent.supervision.observed.ObservedCallingThread;
import org.opales.covalent.supervision.observed.ObservedContainerConnectionEvents;
import org.opales.covalent.supervision.observed.ObservedDependencyGraphServices;
import org.opales.covalent.supervision.observed.ObservedEventsManagerEvents;
import org.opales.covalent.supervision.observed.ObservedEventsManagerServices;
import org.opales.covalent.supervision.observed.ObservedLifecycleEvents;
import org.opales.covalent.supervision.observed.ObservedLifecycleControlServices;
import org.opales.covalent.supervision.observed.ObservedLifecycleReadServices;
import org.opales.covalent.supervision.observed.ObservedMetamodelReadServices;
import org.opales.covalent.supervision.observed.ObservedParameters;
import org.opales.covalent.supervision.observed.ObservedParametersManagerEvents;
import org.opales.covalent.supervision.observed.ObservedParametersManagerServices;
import org.opales.covalent.supervision.observed.ObservedPortActivityEvents;
import org.opales.covalent.supervision.observed.ObservedPortRateEvents;
import org.opales.covalent.supervision.observed.ObservedPortKind;
import org.opales.covalent.supervision.observed.ObservedRegistryEvents;
import org.opales.covalent.supervision.observed.ObservedRegistryReadServices;
import org.opales.covalent.supervision.observed.ObservedServicesManagerEvents;
import org.opales.covalent.supervision.observed.ObservedServicesManagerServices;
import org.opales.covalent.supervision.observed.ObservedWorkersServices;

/**
 * The one place in this module that knows about the observed container's
 * data, and the sole provider/producer of {@link ObservedRegistryReadServices}/
 * {@link ObservedMetamodelReadServices}/{@link ObservedServicesManagerServices}/
 * {@link ObservedParametersManagerServices}/{@link ObservedEventsManagerServices}/
 * {@link ObservedDependencyGraphServices}/{@link ObservedContainerControl}/
 * {@link ObservedParameters}/{@link ObservedLifecycleReadServices}/
 * {@link ObservedLifecycleControlServices}/{@link ObservedWorkersServices} and
 * {@link ObservedRegistryEvents}/{@link ObservedServicesManagerEvents}/
 * {@link ObservedParametersManagerEvents}/{@link ObservedEventsManagerEvents}/
 * {@link ObservedPortActivityEvents}/{@link ObservedLifecycleEvents}/
 * {@link ObservedContainerConnectionEvents}/{@link ObservedPortRateEvents}
 * in the supervision container. These are distinct types from Covalent's own native
 * {@code RegistryReadServices}/{@code RegistryEvents}/etc: every container
 * produces/provides the <em>originals</em> natively about itself from
 * bootstrap, so reusing those names here would collide with the supervision
 * container's own self-observation.
 * <p>
 * An ordinary {@code @Implementation} component, deployed like any other
 * supervision function - real, clean Covalent wiring throughout, no
 * reflection, no raw ports on this side. It never itself touches a
 * {@link CovalentContainer} reference: that stays confined to
 * {@link ObservedContainerDelegate}, the one disposable, non-Covalent
 * technical object this class owns in composition and forwards every
 * Observed* call to, method by method - {@code RawComponentsServices}
 * refuses to raw-register any class that implements a
 * {@code @Services}/{@code @Parameters}-annotated interface (see
 * {@code RawComponentsImpl.checkRawComponentContract}), so the object that
 * does the actual raw registration on the observed container cannot be this
 * one. Each of {@link ObservedContainerDelegate}'s real-Events callbacks
 * calls the matching plain method below, which republishes the identical
 * notification through this instance's own {@code @ProducedEvents} proxy -
 * this class implements none of the five real Events interfaces itself, so
 * it is never scanned by the metamodel as their consumer either, which rules
 * out self-notification.
 * Every such republish goes through {@code publish(Runnable)}, which drops
 * it silently once this component's own teardown has started ({@code
 * disposing}) instead of risking a call on one of these proxies after it
 * has been invalidated.
 * <p>
 * The per-call notifications are the one high-volume part of this traffic:
 * an observed application can make thousands of calls in a burst. They
 * are therefore coalesced here, on the observed calls' own threads, before
 * anything is queued in the supervision container: {@link PortActivityCoalescer}
 * reduces them to {@link ObservedPortActivityEvents}, and the per-call
 * notifications themselves are only republished while
 * {@link ObservedParameters#setCallStreamEnabled} has them enabled - stamped with the
 * call's own thread and time, which their asynchronous delivery would
 * otherwise lose.
 * <p>
 * Before {@link #connect(CovalentContainer)} has ever been called, every
 * read returns an empty/absent result rather than throwing, so a function
 * activating before {@code ContainerManager}'s own default auto-connect
 * sees "nothing observed yet", not a failure.
 */
@Implementation
public class ObservedContainerBridge implements
		ObservedRegistryReadServices, ObservedMetamodelReadServices, ObservedServicesManagerServices,
		ObservedParametersManagerServices, ObservedEventsManagerServices, ObservedDependencyGraphServices,
		ObservedContainerControl, ObservedParameters, ObservedLifecycleReadServices,
		ObservedLifecycleControlServices, ObservedWorkersServices {

	/** Creates the bridge, not yet connected to any container: until a first connect, every read answers empty. */
	public ObservedContainerBridge() {
		// Nothing to initialize.
	}

	@ProducedEvents
	private ObservedRegistryEvents produceObservedRegistryEvents;

	@ProducedEvents
	private ObservedServicesManagerEvents produceObservedServicesManagerEvents;

	@ProducedEvents
	private ObservedParametersManagerEvents produceObservedParametersManagerEvents;

	@ProducedEvents
	private ObservedEventsManagerEvents produceObservedEventsManagerEvents;

	@ProducedEvents
	private ObservedContainerConnectionEvents produceObservedContainerConnectionEvents;

	@ProducedEvents
	private ObservedPortActivityEvents produceObservedPortActivityEvents;

	@ProducedEvents
	private ObservedLifecycleEvents produceObservedLifecycleEvents;

	@ProducedEvents
	private ObservedPortRateEvents produceObservedPortRateEvents;

	private final PortRateSampler portRates = new PortRateSampler(
			(sampleTimeMillis, periodMillis, rates) -> this.publish(() -> this.produceObservedPortRateEvents
					.onPortRatesSampled(sampleTimeMillis, periodMillis, rates)));

	/** Which threads run the observed components' calls, read by the Thread Supervisor. */
	private final CallingThreadsTracker callingThreads = new CallingThreadsTracker();

	private final PortActivityCoalescer portActivity = new PortActivityCoalescer(
			(componentType, portType, portKind, active) -> this.publish(() -> this.produceObservedPortActivityEvents
					.onPortActivityChanged(componentType, portType, portKind, active)));

	/** Whether the per-call notifications are republished - see {@link ObservedParameters#setCallStreamEnabled}. */
	private volatile boolean callStreamEnabled = false;

	/** Incremented each time the call stream is enabled, restarting every thread's order numbers. */
	private volatile long callStreamEpoch = 0;

	/** Per observed thread: the epoch its counter belongs to, then the counter itself. */
	private final ThreadLocal<long[]> callSequences = ThreadLocal.withInitial(() -> new long[] { -1, 0 });

	private final ObservedContainerDelegate delegate = new ObservedContainerDelegate(this);

	/**
	 * Set at the very start of this component's own {@code @Passivation} -
	 * before anything else can invalidate this instance's own
	 * {@code @ProducedEvents} proxies (see {@link #stopPortActivity()}).
	 * Once {@code true}, {@link #publish(Runnable)} drops every republish
	 * below instead of risking a call on one of those now-unreliable
	 * proxies: the observed container's own worker threads can still be
	 * delivering notifications to {@link ObservedContainerDelegate}
	 * concurrently with this component's own teardown.
	 */
	private volatile boolean disposing = false;

	// --- ObservedContainerControl ---

	@Override
	public void connect(final CovalentContainer target) {
		// Only a managed container reports its lifecycle (see ManagedContainerEvents): the
		// supervision leaves it as soon as it is stopping or being disposed.
		if (target.getName() == null) {
			throw new IllegalArgumentException("Only a managed container can be observed.");
		}
		this.portActivity.reset();
		this.callingThreads.reset();
		this.delegate.connect(target);
		this.portRates.start();
		this.publish(() -> this.produceObservedContainerConnectionEvents.onContainerConnected());
	}

	@Override
	public String getObservedContainerName() {
		return this.delegate.observedContainerName();
	}

	@Override
	public void disconnect() {
		this.delegate.disconnect();
		this.portActivity.reset();
		this.callingThreads.reset();
		this.portRates.stop();
		this.publish(() -> this.produceObservedContainerConnectionEvents.onContainerDisconnected());
	}

	/**
	 * Stops the bridge when the component is passivated: no further republication goes through the
	 * produced-events proxies, and the port activity coalescer and the rate sampler are shut down.
	 */
	@Passivation
	public void stopPortActivity() {
		this.disposing = true;
		this.portActivity.shutdown();
		this.portRates.stop();
	}

	// --- ObservedWorkersServices ---

	@Override
	public List<WorkerPoolState> readWorkerPools() {
		var events = this.delegate.observedEventsWorkersServices;
		var async = this.delegate.observedAsyncWorkersServices;
		if (events == null || async == null) {
			return List.of();
		}
		List<WorkerPoolState> pools = new ArrayList<>(this.safeRead(List.of(), events::readWorkerPools));
		pools.addAll(this.safeRead(List.of(), async::readWorkerPools));
		return pools;
	}

	@Override
	public boolean isInstrumented() {
		return this.delegate.isInstrumented();
	}

	@Override
	public List<ObservedCallingThread> readCallingThreads() {
		return this.delegate.observedContainerName() == null ? List.of() : this.callingThreads.read();
	}

	// --- ObservedParameters ---

	@Override
	public void setTaskTracking(final boolean tracking) {
		var events = this.delegate.observedEventsManagerParameters;
		var async = this.delegate.observedAsyncParameters;
		if (events != null && async != null) {
			this.safeRead(null, () -> {
				events.setTaskTracking(tracking);
				async.setTaskTracking(tracking);
				return null;
			});
		}
	}

	@Override
	public boolean isTaskTracking() {
		var async = this.delegate.observedAsyncParameters;
		return async != null && this.safeRead(false, async::isTaskTracking);
	}

	@Override
	public void setWorkerCount(final WorkerPoolKind kind, final int count) {
		switch (kind) {
			case EVENTS -> {
				var events = this.delegate.observedEventsManagerParameters;
				if (events != null) {
					this.safeRead(null, () -> {
						events.setWorkerCount(count);
						return null;
					});
				}
			}
			case GENERAL -> {
				var async = this.delegate.observedAsyncParameters;
				if (async != null) {
					this.safeRead(null, () -> {
						async.setGeneralWorkerCount(count);
						return null;
					});
				}
			}
			default -> throw new IllegalArgumentException("A " + kind + " pool keeps its workers: " + count);
		}
	}

	@Override
	public void setQueueCapacity(final WorkerPoolKind kind, final int capacity) {
		switch (kind) {
			case EVENTS -> {
				var events = this.delegate.observedEventsManagerParameters;
				if (events != null) {
					this.safeRead(null, () -> {
						events.setWorkerQueueCapacity(capacity);
						return null;
					});
				}
			}
			case GENERAL -> {
				var async = this.delegate.observedAsyncParameters;
				if (async != null) {
					this.safeRead(null, () -> {
						async.setWorkerQueueCapacity(capacity);
						return null;
					});
				}
			}
			default -> throw new IllegalArgumentException("A " + kind + " pool's capacity is the general one's: "
					+ capacity);
		}
	}

	// --- ObservedLifecycleReadServices ---

	@Override
	public ComponentState readComponentState(final Class<?> componentType) {
		var observed = this.delegate.observedLifecycleServices;
		return observed == null ? null : this.safeRead(null, () -> observed.readComponentState(componentType));
	}

	// --- ObservedLifecycleControlServices ---

	@Override
	public void instantiateComponent(final Class<?> componentType) {
		this.requireObservedLifecycleServices().instantiateComponent(componentType);
	}

	@Override
	public void initializeComponent(final Class<?> componentType) {
		this.requireObservedLifecycleServices().initializeComponent(componentType);
	}

	@Override
	public void activateComponent(final Class<?> componentType) {
		this.requireObservedLifecycleServices().activateComponent(componentType);
	}

	@Override
	public void passivateComponent(final Class<?> componentType) {
		this.requireObservedLifecycleServices().passivateComponent(componentType);
	}

	@Override
	public void removeComponent(final Class<?> componentType) {
		this.requireObservedLifecycleServices().removeComponent(componentType);
	}

	@Override
	public void resetFailedComponent(final Class<?> componentType) {
		this.requireObservedLifecycleServices().resetFailedComponent(componentType);
	}

	@Override
	public void undeployComponent(final Class<?> componentType) {
		this.requireObservedLifecycleServices().undeployComponent(this.implementationOf(componentType));
	}

	private LifecycleServices requireObservedLifecycleServices() {
		var observed = this.delegate.observedLifecycleServices;
		if (observed == null) {
			throw new IllegalStateException("No container observed");
		}
		return observed;
	}

	private Class<?> implementationOf(final Class<?> componentType) {
		var metamodels = this.delegate.observedMetamodelReadServices;
		if (metamodels == null) {
			throw new IllegalStateException("No container observed");
		}
		var metamodel = metamodels.readMetamodel(componentType);
		if (metamodel == null) {
			throw new IllegalArgumentException("Unknown component type: " + componentType.getName());
		}
		return metamodel.getImplementation();
	}

	void onComponentStateChanged(final Class<?> componentType, final ComponentState state) {
		this.publish(() -> this.produceObservedLifecycleEvents.onComponentStateChanged(componentType, state));
	}

	// --- ObservedParameters (call stream) ---

	@Override
	public void setCallStreamEnabled(final boolean enabled) {
		if (enabled) {
			this.callStreamEpoch++;
		}
		this.callStreamEnabled = enabled;
	}

	/**
	 * The next order number of the current thread's call notifications,
	 * from 1 since the call stream was last enabled - see the per-call
	 * methods of {@link ObservedServicesManagerEvents}.
	 */
	private long nextCallSequence() {
		long[] counter = this.callSequences.get();
		long epoch = this.callStreamEpoch;
		if (counter[0] != epoch) {
			counter[0] = epoch;
			counter[1] = 0;
		}
		return ++counter[1];
	}

	/**
	 * Runs a read against the observed container, treating an invalidated
	 * used-services proxy exactly like "nothing observed yet": connect()
	 * is mid-switch to a new target (disconnect() already tore down the
	 * old one's proxies, the new one isn't wired up yet) or the observed
	 * component genuinely went away. Either way there is nothing valid to
	 * report right now, and the next legitimate notification - or
	 * onContainerConnected() once a new target settles - produces a
	 * correct read.
	 *
	 * @param <T>         the read's result type.
	 * @param emptyResult the value to return if the proxy is invalidated.
	 * @param read        the read to attempt.
	 * @return the read's result, or {@code emptyResult} if its proxy is
	 *         invalidated.
	 */
	private <T> T safeRead(final T emptyResult, final Supplier<T> read) {
		try {
			return read.get();
		} catch (IllegalStateException e) {
			return emptyResult;
		}
	}

	/**
	 * Runs a republish against one of this instance's own
	 * {@code @ProducedEvents} proxies, dropping it silently instead of
	 * ever letting the failure reach the observed container's own
	 * delivery code: once {@link #disposing} is set (see
	 * {@link #stopPortActivity()}), every republish is skipped outright;
	 * the catch below is the backstop for the narrow window where a proxy
	 * is invalidated before that flag is observed.
	 *
	 * @param publish the republish to attempt.
	 */
	private void publish(final Runnable publish) {
		if (this.disposing) {
			return;
		}
		try {
			publish.run();
		} catch (IllegalStateException e) {
			// Lost the race: this component's own teardown invalidated the
			// producer proxy between the check above and this call.
		}
	}

	// --- ObservedRegistryReadServices: forwarded, empty/absent when nothing is observed. ---

	@Override
	public <CT, CI extends CT> CI getComponentInstance(final Class<CT> componentType) {
		var observed = this.delegate.observedRegistryReadServices;
		return observed == null ? null : this.safeRead(null, () -> observed.getComponentInstance(componentType));
	}

	@Override
	public Map<Class<?>, Object> getInstances() {
		var observed = this.delegate.observedRegistryReadServices;
		return observed == null ? Map.of() : this.safeRead(Map.of(), observed::getInstances);
	}

	@Override
	public <T> boolean isRegistered(final Class<T> componentType) {
		var observed = this.delegate.observedRegistryReadServices;
		return observed != null && this.safeRead(false, () -> observed.isRegistered(componentType));
	}

	// --- ObservedMetamodelReadServices: forwarded, absent when nothing is observed. ---

	@Override
	public ReadOnlyComponentMetamodel readMetamodel(final Class<?> componentType) {
		var observed = this.delegate.observedMetamodelReadServices;
		return observed == null ? null : this.safeRead(null, () -> observed.readMetamodel(componentType));
	}

	@Override
	public <CT, CI> Class<CT> getType(final Class<CI> implementation) {
		var observed = this.delegate.observedMetamodelReadServices;
		return observed == null ? null : this.safeRead(null, () -> observed.getType(implementation));
	}

	@Override
	public <CT, CI> Class<CI> getImplementation(final Class<CT> componentType) {
		var observed = this.delegate.observedMetamodelReadServices;
		return observed == null ? null : this.safeRead(null, () -> observed.getImplementation(componentType));
	}

	// --- ObservedServicesManagerServices: forwarded verbatim; no supervision function
	// calls the mutating methods, but they forward too rather than being
	// special-cased, so this bridge stays a faithful, uniform proxy. ---

	@Override
	public boolean isServicesUsed(final Class<?> componentType, final Class<?> servicesInterface) {
		var observed = this.delegate.observedServicesManagerServices;
		return observed != null && this.safeRead(false, () -> observed.isServicesUsed(componentType, servicesInterface));
	}

	@Override
	public <SERVICES> void createProvidedServices(final Class<SERVICES> servicesInterface) {
		this.requireObservedServicesManagerServices().createProvidedServices(servicesInterface);
	}

	@Override
	public <SERVICES> void createProvidedServices(final Class<?> componentType, final Class<SERVICES> servicesInterface) {
		this.requireObservedServicesManagerServices().createProvidedServices(componentType, servicesInterface);
	}

	@Override
	public <SERVICES> SERVICES getServicesProvider(final Class<SERVICES> servicesInterface) {
		var observed = this.delegate.observedServicesManagerServices;
		return observed == null ? null : this.safeRead(null, () -> observed.getServicesProvider(servicesInterface));
	}

	@Override
	public <SERVICES> SERVICES createUsedServices(final Class<?> servicesUserComponentType,
			final Class<SERVICES> servicesInterface) {
		return this.requireObservedServicesManagerServices().createUsedServices(servicesUserComponentType, servicesInterface);
	}

	@Override
	public <SERVICES> SERVICES reconnectUsedServices(final Class<?> servicesUserComponentType,
			final Class<SERVICES> servicesInterface) {
		return this.requireObservedServicesManagerServices().reconnectUsedServices(servicesUserComponentType, servicesInterface);
	}

	@Override
	public <SERVICES> void deleteUsedServices(final Class<?> userComponentType, final Class<SERVICES> servicesInterface) {
		this.requireObservedServicesManagerServices().deleteUsedServices(userComponentType, servicesInterface);
	}

	@Override
	public boolean isServicesProvided(final Class<?> servicesInterface) {
		var observed = this.delegate.observedServicesManagerServices;
		return observed != null && this.safeRead(false, () -> observed.isServicesProvided(servicesInterface));
	}

	@Override
	public <SERVICES> void deleteProvidedServices(final Class<SERVICES> servicesInterface) {
		this.requireObservedServicesManagerServices().deleteProvidedServices(servicesInterface);
	}

	@Override
	public Map<Class<?>, Set<Class<?>>> getProviderToServices() {
		var observed = this.delegate.observedServicesManagerServices;
		return observed == null ? Map.of() : this.safeRead(Map.of(), observed::getProviderToServices);
	}

	@Override
	public Map<Class<?>, Set<Class<?>>> getServiceToUsers() {
		var observed = this.delegate.observedServicesManagerServices;
		return observed == null ? Map.of() : this.safeRead(Map.of(), observed::getServiceToUsers);
	}

	@Override
	public <SERVICES> void activateServices(final Class<SERVICES> servicesInterface) {
		this.requireObservedServicesManagerServices().activateServices(servicesInterface);
	}

	@Override
	public <SERVICES> void passivateServices(final Class<SERVICES> servicesInterface) {
		this.requireObservedServicesManagerServices().passivateServices(servicesInterface);
	}

	@Override
	public <SERVICES> boolean isActivated(final Class<SERVICES> servicesInterface) {
		var observed = this.delegate.observedServicesManagerServices;
		return observed != null && this.safeRead(false, () -> observed.isActivated(servicesInterface));
	}

	private ServicesManagerServices requireObservedServicesManagerServices() {
		var observed = this.delegate.observedServicesManagerServices;
		if (observed == null) {
			throw new IllegalStateException("Not connected to an observed container.");
		}
		return observed;
	}

	// --- ObservedParametersManagerServices: forwarded verbatim, same rationale as above. ---

	@Override
	public boolean isParametersAccessed(final Class<?> componentType, final Class<?> parametersInterface) {
		var observed = this.delegate.observedParametersManagerServices;
		return observed != null
				&& this.safeRead(false, () -> observed.isParametersAccessed(componentType, parametersInterface));
	}

	@Override
	public <PARAMETERS> void createOfferedParameters(final Class<PARAMETERS> parametersInterface) {
		this.requireObservedParametersManagerServices().createOfferedParameters(parametersInterface);
	}

	@Override
	public <PARAMETERS> void createOfferedParameters(final Class<?> componentType, final Class<PARAMETERS> servicesInterface) {
		this.requireObservedParametersManagerServices().createOfferedParameters(componentType, servicesInterface);
	}

	@Override
	public <PARAMETERS> PARAMETERS getParametersOfferor(final Class<PARAMETERS> parametersInterface) {
		var observed = this.delegate.observedParametersManagerServices;
		return observed == null ? null : this.safeRead(null, () -> observed.getParametersOfferor(parametersInterface));
	}

	@Override
	public <PARAMETERS> PARAMETERS createAccessedParameters(final Class<?> parametersAccessorComponentType,
			final Class<PARAMETERS> parametersInterface) {
		return this.requireObservedParametersManagerServices().createAccessedParameters(parametersAccessorComponentType,
				parametersInterface);
	}

	@Override
	public <PARAMETERS> void deleteAccessedParameters(final Class<?> accessorComponentType,
			final Class<PARAMETERS> parametersInterface) {
		this.requireObservedParametersManagerServices().deleteAccessedParameters(accessorComponentType, parametersInterface);
	}

	@Override
	public boolean isParametersOffered(final Class<?> parametersInterface) {
		var observed = this.delegate.observedParametersManagerServices;
		return observed != null && this.safeRead(false, () -> observed.isParametersOffered(parametersInterface));
	}

	@Override
	public <PARAMETERS> void deleteOfferedParameters(final Class<PARAMETERS> parametersInterface) {
		this.requireObservedParametersManagerServices().deleteOfferedParameters(parametersInterface);
	}

	@Override
	public Map<Class<?>, Set<Class<?>>> getOfferorToParameters() {
		var observed = this.delegate.observedParametersManagerServices;
		return observed == null ? Map.of() : this.safeRead(Map.of(), observed::getOfferorToParameters);
	}

	@Override
	public Map<Class<?>, Set<Class<?>>> getParametersToAccessor() {
		var observed = this.delegate.observedParametersManagerServices;
		return observed == null ? Map.of() : this.safeRead(Map.of(), observed::getParametersToAccessor);
	}

	@Override
	public <PARAMETERS> void activateParameters(final Class<PARAMETERS> parametersInterface) {
		this.requireObservedParametersManagerServices().activateParameters(parametersInterface);
	}

	@Override
	public <PARAMETERS> void passivateParameters(final Class<PARAMETERS> parametersInterface) {
		this.requireObservedParametersManagerServices().passivateParameters(parametersInterface);
	}

	private ParametersManagerServices requireObservedParametersManagerServices() {
		var observed = this.delegate.observedParametersManagerServices;
		if (observed == null) {
			throw new IllegalStateException("Not connected to an observed container.");
		}
		return observed;
	}

	// --- ObservedEventsManagerServices: forwarded verbatim, same rationale as above. ---

	@Override
	public Map<Class<?>, Set<ConsumerMetadata<?>>> getSubscribers() {
		var observed = this.delegate.observedEventsManagerServices;
		return observed == null ? Map.of() : this.safeRead(Map.of(), observed::getSubscribers);
	}

	@Override
	public Map<Class<?>, Set<Class<?>>> getProducerToEvent() {
		var observed = this.delegate.observedEventsManagerServices;
		return observed == null ? Map.of() : this.safeRead(Map.of(), observed::getProducerToEvent);
	}

	@Override
	public <EVENTS> EVENTS createProducer(final Class<EVENTS> eventsInterface, final Class<?> componentType,
			final Consumer<EVENTS> subscribeDelegateConsumer, final Consumer<EVENTS> unsubscribeDelegateConsumer) {
		return this.requireObservedEventsManagerServices().createProducer(eventsInterface, componentType,
				subscribeDelegateConsumer, unsubscribeDelegateConsumer);
	}

	@Override
	public <EVENTS> void deleteProducer(final Class<?> componentType, final Class<EVENTS> eventsInterface) {
		this.requireObservedEventsManagerServices().deleteProducer(componentType, eventsInterface);
	}

	@Override
	public <EVENTS> void activateProducer(final Class<EVENTS> eventsInterface) {
		this.requireObservedEventsManagerServices().activateProducer(eventsInterface);
	}

	@Override
	public <EVENTS> void passivateProducer(final Class<EVENTS> eventsInterface) {
		this.requireObservedEventsManagerServices().passivateProducer(eventsInterface);
	}

	@Override
	public <EVENTS> boolean isEventsProduced(final Class<EVENTS> eventsInterface) {
		var observed = this.delegate.observedEventsManagerServices;
		return observed != null && this.safeRead(false, () -> observed.isEventsProduced(eventsInterface));
	}

	@Override
	public <EVENTS, SUBSCRIBE> SUBSCRIBE createUsedSubscriber(final Class<EVENTS> eventsInterface,
			final Class<SUBSCRIBE> subscribeInterface) {
		return this.requireObservedEventsManagerServices().createUsedSubscriber(eventsInterface, subscribeInterface);
	}

	@Override
	public <EVENTS> void subscribe(final Class<EVENTS> eventsInterface, final EVENTS consumer,
			final SubscribeParameters subscribeParameters) {
		this.requireObservedEventsManagerServices().subscribe(eventsInterface, consumer, subscribeParameters);
	}

	@Override
	public <EVENTS, SUBSCRIBE> SUBSCRIBE getProvidedSubscriber(final Class<EVENTS> eventsInterface) {
		var observed = this.delegate.observedEventsManagerServices;
		return observed == null ? null
				: this.safeRead(null, () -> observed.<EVENTS, SUBSCRIBE>getProvidedSubscriber(eventsInterface));
	}

	@Override
	public <EVENTS> void unsubscribe(final Class<EVENTS> eventsInterface, final EVENTS consumer) {
		this.requireObservedEventsManagerServices().unsubscribe(eventsInterface, consumer);
	}

	@Override
	public <EVENTS> boolean isSubscribed(final Class<EVENTS> eventsInterface, final EVENTS consumer) {
		var observed = this.delegate.observedEventsManagerServices;
		return observed != null && this.safeRead(false, () -> observed.isSubscribed(eventsInterface, consumer));
	}

	private EventsManagerServices requireObservedEventsManagerServices() {
		var observed = this.delegate.observedEventsManagerServices;
		if (observed == null) {
			throw new IllegalStateException("Not connected to an observed container.");
		}
		return observed;
	}

	// --- ObservedDependencyGraphServices: forwarded, "nothing observed yet" reported the
	// same way DependencyManagementControllerImpl treats "checking was
	// disabled" - hasDependencyGraph() false, getDependencyGraph() absent. ---

	@Override
	public DependencyGraph getDependencyGraph() {
		var observed = this.delegate.observedDependencyGraphServices;
		return observed == null ? null : this.safeRead(null, observed::getDependencyGraph);
	}

	@Override
	public boolean hasDependencyGraph() {
		var observed = this.delegate.observedDependencyGraphServices;
		return observed != null && this.safeRead(false, observed::hasDependencyGraph);
	}

	// --- Plain forwarding methods called by ObservedContainerDelegate, one per real
	// Events method - see class javadoc for why this class never implements those
	// interfaces itself. Each republishes through this instance's own
	// @ProducedEvents proxy; the per-call ones coalesce first - see class
	// javadoc. ---

	<CT> void onComponentRegistered(final Class<CT> componentType, final Object componentInstance) {
		this.publish(() -> this.produceObservedRegistryEvents.onComponentRegistered(componentType, componentInstance));
	}

	<CT> void onComponentUnregistered(final Class<CT> componentType, final Object componentInstance) {
		this.publish(() -> this.produceObservedRegistryEvents.onComponentUnregistered(componentType, componentInstance));
	}

	<CT, SERVICES> void onServicesProvided(final Class<CT> componentType, final Class<SERVICES> servicesInterface) {
		this.publish(() -> this.produceObservedServicesManagerEvents.onServicesProvided(componentType, servicesInterface));
	}

	<CT, SERVICES> void onServicesUnprovided(final Class<CT> componentType, final Class<SERVICES> servicesInterface) {
		this.publish(() -> this.produceObservedServicesManagerEvents.onServicesUnprovided(componentType, servicesInterface));
	}

	<CT, SERVICES> void onServicesUsed(final Class<CT> servicesUser, final Class<SERVICES> servicesInterface) {
		this.publish(() -> this.produceObservedServicesManagerEvents.onServicesUsed(servicesUser, servicesInterface));
	}

	<CT, SERVICES> void onServicesUnused(final Class<CT> servicesUser, final Class<SERVICES> servicesInterface) {
		this.publish(() -> this.produceObservedServicesManagerEvents.onServicesUnused(servicesUser, servicesInterface));
	}

	/**
	 * Whether the call runs on one of the Supervisor's own internal threads (its Cockpit command executor,
	 * live-state workers, samplers...). A call that physically runs on one of these - typically a component's own
	 * {@code @Activation}/{@code @Passivation} callback, invoked synchronously from a Cockpit lifecycle command - is
	 * administrative noise, not business activity: every observation channel below (calling threads, port
	 * activity/rates, the call stream) keeps it out, the same way it keeps out the container's own worker pools.
	 */
	private static boolean isSupervisionThread() {
		return SupervisionThreads.isCurrent();
	}

	<SERVICES> void onUsedServiceCallStarted(final Class<SERVICES> servicesInterface, final Method method,
			final Class<?> servicesUser, final Object... inputData) {
		if (isSupervisionThread()) {
			return;
		}
		this.portActivity.callStarted(servicesUser, servicesInterface, ObservedPortKind.SERVICE);
		this.portRates.count(servicesUser, servicesInterface, ObservedPortKind.SERVICE, false);
		if (this.callStreamEnabled) {
			this.publish(() -> this.produceObservedServicesManagerEvents.onUsedServiceCallStarted(Thread.currentThread(),
					System.nanoTime(), this.nextCallSequence(), servicesInterface,
					method, servicesUser, DataPrinter.describeArguments(inputData)));
		}
	}

	<SERVICES> void onUsedServiceCallFinished(final Class<SERVICES> servicesInterface, final Method method,
			final Class<?> servicesUser, final long durationNanos, final Throwable failure, final Object returnValue) {
		if (isSupervisionThread()) {
			return;
		}
		this.portActivity.callFinished(servicesUser, servicesInterface, ObservedPortKind.SERVICE);
		this.portRates.finished(servicesUser, servicesInterface, ObservedPortKind.SERVICE, false, durationNanos, failure,
				method.getName());
		if (this.callStreamEnabled) {
			this.publish(() -> this.produceObservedServicesManagerEvents.onUsedServiceCallFinished(Thread.currentThread(),
					System.nanoTime(), this.nextCallSequence(), servicesInterface,
					method, servicesUser, durationNanos, failure,
					DataPrinter.describeOutcome(method, returnValue, failure)));
		}
	}

	<SERVICES> void onProvidedServiceCallStarted(final Class<SERVICES> servicesInterface, final Method method,
			final Class<?> servicesProvider, final Object... inputData) {
		if (isSupervisionThread()) {
			return;
		}
		this.callingThreads.entered(servicesProvider, method.getName());
		this.portActivity.callStarted(servicesProvider, servicesInterface, ObservedPortKind.SERVICE);
		this.portRates.count(servicesProvider, servicesInterface, ObservedPortKind.SERVICE, true);
		if (this.callStreamEnabled) {
			this.publish(() -> this.produceObservedServicesManagerEvents.onProvidedServiceCallStarted(Thread.currentThread(),
					System.nanoTime(), this.nextCallSequence(), servicesInterface,
					method, servicesProvider, DataPrinter.describeArguments(inputData)));
		}
	}

	<SERVICES> void onProvidedServiceCallFinished(final Class<SERVICES> servicesInterface, final Method method,
			final Class<?> servicesProvider, final long durationNanos, final Throwable failure, final Object returnValue) {
		if (isSupervisionThread()) {
			return;
		}
		this.callingThreads.exited(servicesProvider);
		this.portActivity.callFinished(servicesProvider, servicesInterface, ObservedPortKind.SERVICE);
		this.portRates.finished(servicesProvider, servicesInterface, ObservedPortKind.SERVICE, true, durationNanos, failure,
				method.getName());
		if (this.callStreamEnabled) {
			this.publish(() -> this.produceObservedServicesManagerEvents.onProvidedServiceCallFinished(Thread.currentThread(),
					System.nanoTime(), this.nextCallSequence(), servicesInterface,
					method, servicesProvider, durationNanos, failure,
					DataPrinter.describeOutcome(method, returnValue, failure)));
		}
	}

	<PARAMETERS> void onParametersOffered(final Class<?> componentType, final Class<PARAMETERS> parametersInterface) {
		this.publish(() -> this.produceObservedParametersManagerEvents.onParametersOffered(componentType, parametersInterface));
	}

	<PARAMETERS> void onParametersUnoffered(final Class<?> componentType, final Class<PARAMETERS> parametersInterface) {
		this.publish(() -> this.produceObservedParametersManagerEvents.onParametersUnoffered(componentType, parametersInterface));
	}

	<PARAMETERS> void onParametersAccessed(final Class<?> parametersAccessor, final Class<PARAMETERS> parametersInterface) {
		this.publish(() -> this.produceObservedParametersManagerEvents.onParametersAccessed(parametersAccessor, parametersInterface));
	}

	<PARAMETERS> void onParametersUnaccessed(final Class<?> parametersAccessor, final Class<PARAMETERS> parametersInterface) {
		this.publish(() -> this.produceObservedParametersManagerEvents.onParametersUnaccessed(parametersAccessor, parametersInterface));
	}

	<PARAMETERS> void onAccessedParametersCallStarted(final Class<PARAMETERS> parametersInterface, final Method method,
			final Class<?> parametersAccessor, final Object... inputData) {
		if (isSupervisionThread()) {
			return;
		}
		this.portActivity.callStarted(parametersAccessor, parametersInterface, ObservedPortKind.PARAMETER);
		this.portRates.count(parametersAccessor, parametersInterface, ObservedPortKind.PARAMETER, false);
		if (this.callStreamEnabled) {
			this.publish(() -> this.produceObservedParametersManagerEvents.onAccessedParametersCallStarted(Thread.currentThread(), System.nanoTime(), this.nextCallSequence(),
					parametersInterface, method, parametersAccessor, DataPrinter.describeArguments(inputData)));
		}
	}

	<PARAMETERS> void onAccessedParametersCallFinished(final Class<PARAMETERS> parametersInterface, final Method method,
			final Class<?> parametersAccessor, final long durationNanos, final Throwable failure, final Object returnValue) {
		if (isSupervisionThread()) {
			return;
		}
		this.portActivity.callFinished(parametersAccessor, parametersInterface, ObservedPortKind.PARAMETER);
		this.portRates.finished(parametersAccessor, parametersInterface, ObservedPortKind.PARAMETER, false, durationNanos,
				failure, method.getName());
		if (this.callStreamEnabled) {
			this.publish(() -> this.produceObservedParametersManagerEvents.onAccessedParametersCallFinished(Thread.currentThread(), System.nanoTime(), this.nextCallSequence(),
					parametersInterface, method, parametersAccessor, durationNanos, failure,
					DataPrinter.describeOutcome(method, returnValue, failure)));
		}
	}

	<PARAMETERS> void onOfferedParametersCallStarted(final Class<PARAMETERS> parametersInterface, final Method method,
			final Class<?> parametersOfferor, final Object... inputData) {
		if (isSupervisionThread()) {
			return;
		}
		this.callingThreads.entered(parametersOfferor, method.getName());
		this.portActivity.callStarted(parametersOfferor, parametersInterface, ObservedPortKind.PARAMETER);
		this.portRates.count(parametersOfferor, parametersInterface, ObservedPortKind.PARAMETER, true);
		if (this.callStreamEnabled) {
			this.publish(() -> this.produceObservedParametersManagerEvents.onOfferedParametersCallStarted(Thread.currentThread(), System.nanoTime(), this.nextCallSequence(),
					parametersInterface, method, parametersOfferor, DataPrinter.describeArguments(inputData)));
		}
	}

	<PARAMETERS> void onOfferedParametersCallFinished(final Class<PARAMETERS> parametersInterface, final Method method,
			final Class<?> parametersOfferor, final long durationNanos, final Throwable failure, final Object returnValue) {
		if (isSupervisionThread()) {
			return;
		}
		this.callingThreads.exited(parametersOfferor);
		this.portActivity.callFinished(parametersOfferor, parametersInterface, ObservedPortKind.PARAMETER);
		this.portRates.finished(parametersOfferor, parametersInterface, ObservedPortKind.PARAMETER, true, durationNanos,
				failure, method.getName());
		if (this.callStreamEnabled) {
			this.publish(() -> this.produceObservedParametersManagerEvents.onOfferedParametersCallFinished(Thread.currentThread(), System.nanoTime(), this.nextCallSequence(),
					parametersInterface, method, parametersOfferor, durationNanos, failure,
					DataPrinter.describeOutcome(method, returnValue, failure)));
		}
	}

	<EVENTS> void onEventsProduced(final Class<?> componentType, final Class<EVENTS> eventsInterface) {
		this.publish(() -> this.produceObservedEventsManagerEvents.onEventsProduced(componentType, eventsInterface));
	}

	<EVENTS> void onEventsUnproduced(final Class<?> componentType, final Class<EVENTS> eventsInterface) {
		this.publish(() -> this.produceObservedEventsManagerEvents.onEventsUnproduced(componentType, eventsInterface));
	}

	// Both of these also fire for the Delegate's own (un)subscription to
	// Registry/Services/Parameters/EventsManagerEvents, done as part of our
	// own connect()/disconnect() - every producer reports (un)subscription
	// activity through this one shared EventsManagerEvents channel,
	// regardless of which interface is being (un)subscribed. An earlier
	// version of this method tried to filter that administrative echo out
	// by comparing eventsConsumer to ObservedContainerDelegate.class, but
	// EventsManagerImpl always reports the per-interface consumer-side JDK
	// proxy's class here (see EventsProxyFactory.newConsumerProxy), never
	// the real subscriber's own class - so the comparison could never
	// match, for this or any other consumer, and the filter was dead code.
	// Every (un)subscription is republished unfiltered; publish(), below,
	// is what now protects every republish in this class - including these
	// two - against this instance's own @ProducedEvents proxies going
	// invalid while this component's own teardown is in progress (see
	// disposing).
	<EVENTS> void onEventsSubscribed(final Class<? extends EVENTS> eventsConsumer, final Class<EVENTS> eventsInterface) {
		this.publish(() -> this.produceObservedEventsManagerEvents.onEventsSubscribed(eventsConsumer, eventsInterface));
	}

	<EVENTS> void onEventsUnsubscribed(final Class<? extends EVENTS> eventsConsumer, final Class<EVENTS> eventsInterface) {
		this.publish(() -> this.produceObservedEventsManagerEvents.onEventsUnsubscribed(eventsConsumer, eventsInterface));
	}

	<EVENTS> void onEventNotificationStarted(final Class<EVENTS> eventsInterface, final Method method,
			final Class<?> eventsProducer, final Class<?> eventsConsumer, final Class<? extends EVENTS> subscriberType,
			final boolean asynchronous, final long queueWaitNanos, final Object... inputData) {
		if (isSupervisionThread()) {
			return;
		}
		this.callingThreads.entered(eventsConsumer, method.getName());
		this.portActivity.callStarted(eventsProducer, eventsInterface, ObservedPortKind.EVENT);
		this.portActivity.callStarted(eventsConsumer, eventsInterface, ObservedPortKind.EVENT);
		this.portRates.count(eventsProducer, eventsInterface, ObservedPortKind.EVENT, false);
		this.portRates.count(eventsConsumer, eventsInterface, ObservedPortKind.EVENT, true);
		if (this.callStreamEnabled) {
			this.publish(() -> this.produceObservedEventsManagerEvents.onEventNotificationStarted(Thread.currentThread(),
					System.nanoTime(), this.nextCallSequence(), eventsInterface,
					method, eventsProducer, eventsConsumer, subscriberType, asynchronous, queueWaitNanos,
					DataPrinter.describeArguments(inputData)));
		}
	}

	<EVENTS> void onEventNotificationFinished(final Class<EVENTS> eventsInterface, final Method method,
			final Class<?> eventsProducer, final Class<?> eventsConsumer, final Class<? extends EVENTS> subscriberType,
			final long durationNanos, final Throwable failure) {
		if (isSupervisionThread()) {
			return;
		}
		this.callingThreads.exited(eventsConsumer);
		this.portActivity.callFinished(eventsProducer, eventsInterface, ObservedPortKind.EVENT);
		this.portActivity.callFinished(eventsConsumer, eventsInterface, ObservedPortKind.EVENT);
		this.portRates.finished(eventsProducer, eventsInterface, ObservedPortKind.EVENT, false, durationNanos, failure,
				method.getName());
		this.portRates.finished(eventsConsumer, eventsInterface, ObservedPortKind.EVENT, true, durationNanos, failure,
				method.getName());
		if (this.callStreamEnabled) {
			this.publish(() -> this.produceObservedEventsManagerEvents.onEventNotificationFinished(Thread.currentThread(),
					System.nanoTime(), this.nextCallSequence(), eventsInterface,
					method, eventsProducer, eventsConsumer, subscriberType, durationNanos, failure));
		}
	}
}
