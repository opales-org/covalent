/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.containermanager.raw;

import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.async.AsyncParameters;
import org.opales.covalent.container.async.AsyncWorkersServices;
import org.opales.covalent.container.events.EventsManagerEvents;
import org.opales.covalent.container.events.EventsManagerParameters;
import org.opales.covalent.container.events.EventsManagerServices;
import org.opales.covalent.container.events.EventsWorkersServices;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.lifecycle.DependencyGraphServices;
import org.opales.covalent.container.lifecycle.LifecycleEvents;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.metamodel.MetamodelReadServices;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.registry.RegistryEvents;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.rr.parameters.ParametersManagerEvents;
import org.opales.covalent.container.rr.parameters.ParametersManagerServices;
import org.opales.covalent.container.rr.services.ServicesManagerEvents;
import org.opales.covalent.container.rr.services.ServicesManagerServices;
import java.lang.reflect.Method;

/**
 * The disposable, non-Covalent technical delegate that physically holds a
 * {@link CovalentContainer} reference and reaches across into it - the only
 * such object in this module (see {@link ObservedContainerBridge}'s own
 * class javadoc for the design this splits in two). Carries no Covalent annotation anywhere:
 * {@code RawComponentsServices.registerComponent(...)} rejects outright any
 * class that implements an interface annotated {@code @Services} or
 * {@code @Parameters} (see {@code RawComponentsImpl.checkRawComponentContract}),
 * so this class implements only the five <em>real</em> Events interfaces below,
 * purely as the raw-subscribe callback target on the observed container -
 * never a {@code @Services}/{@code @Parameters} port, and never
 * {@code @Implementation}/{@code @Item}.
 * <p>
 * Package-private by design: {@link ObservedContainerBridge} is its only
 * caller and its only collaborator, one per Bridge instance, constructed in
 * the Bridge's own field initializer.
 */
class ObservedContainerDelegate implements RegistryEvents, ServicesManagerEvents, ParametersManagerEvents,
		EventsManagerEvents, LifecycleEvents {

	private final ObservedContainerBridge owner;

	ObservedContainerDelegate(final ObservedContainerBridge owner) {
		this.owner = owner;
	}

	private volatile CovalentContainer observedContainer;
	volatile RegistryReadServices observedRegistryReadServices;
	volatile MetamodelReadServices observedMetamodelReadServices;
	volatile ServicesManagerServices observedServicesManagerServices;
	volatile ParametersManagerServices observedParametersManagerServices;
	volatile EventsManagerServices observedEventsManagerServices;
	volatile DependencyGraphServices observedDependencyGraphServices;
	volatile LifecycleServices observedLifecycleServices;
	volatile AsyncWorkersServices observedAsyncWorkersServices;
	volatile EventsWorkersServices observedEventsWorkersServices;
	volatile AsyncParameters observedAsyncParameters;
	volatile EventsManagerParameters observedEventsManagerParameters;

	void connect(final CovalentContainer target) {
		this.disconnect();
		RawComponentsServices rcs = target.rcs();
		rcs.registerComponent(this);
		this.observedRegistryReadServices = rcs.useServices(RegistryReadServices.class);
		this.observedMetamodelReadServices = rcs.useServices(MetamodelReadServices.class);
		this.observedServicesManagerServices = rcs.useServices(ServicesManagerServices.class);
		this.observedParametersManagerServices = rcs.useServices(ParametersManagerServices.class);
		this.observedEventsManagerServices = rcs.useServices(EventsManagerServices.class);
		this.observedDependencyGraphServices = rcs.useServices(DependencyGraphServices.class);
		this.observedLifecycleServices = rcs.useServices(LifecycleServices.class);
		this.observedAsyncWorkersServices = rcs.useServices(AsyncWorkersServices.class);
		this.observedEventsWorkersServices = rcs.useServices(EventsWorkersServices.class);
		this.observedAsyncParameters = rcs.accessParameters(AsyncParameters.class);
		this.observedEventsManagerParameters = rcs.accessParameters(EventsManagerParameters.class);
		rcs.subscribe(RegistryEvents.class, this);
		rcs.subscribe(ServicesManagerEvents.class, this);
		rcs.subscribe(ParametersManagerEvents.class, this);
		rcs.subscribe(EventsManagerEvents.class, this);
		rcs.subscribe(LifecycleEvents.class, this);
		this.observedContainer = target;
	}

	/** Whether the observed container is instrumented: its calls are then reported. */
	boolean isInstrumented() {
		CovalentContainer target = this.observedContainer;
		return target != null && target.getParameters().isInstrumented();
	}

	/** The observed container's name, {@code null} if none is observed. */
	String observedContainerName() {
		CovalentContainer target = this.observedContainer;
		return target == null ? null : target.getName();
	}

	void disconnect() {
		CovalentContainer target = this.observedContainer;
		if (target == null) {
			return;
		}
		// Only a managed container is observed, and it is left as soon as it is stopping or
		// being disposed (see ContainerManagerInteractionImpl): it still holds this
		// delegate here.
		this.tearDown(target.rcs());
		this.observedContainer = null;
		this.observedRegistryReadServices = null;
		this.observedMetamodelReadServices = null;
		this.observedServicesManagerServices = null;
		this.observedParametersManagerServices = null;
		this.observedEventsManagerServices = null;
		this.observedDependencyGraphServices = null;
		this.observedLifecycleServices = null;
		this.observedAsyncWorkersServices = null;
		this.observedEventsWorkersServices = null;
		this.observedAsyncParameters = null;
		this.observedEventsManagerParameters = null;
	}

	private void tearDown(final RawComponentsServices rcs) {
		// Leaves the observed pools as they were before: tracking costs them a little on every task.
		try {
			this.observedAsyncParameters.setTaskTracking(false);
			this.observedEventsManagerParameters.setTaskTracking(false);
		} catch (RuntimeException e) {
			// The observed container is already stopping: its pools stop with it.
		}
		rcs.unsubscribe(RegistryEvents.class, this);
		rcs.unsubscribe(ServicesManagerEvents.class, this);
		rcs.unsubscribe(ParametersManagerEvents.class, this);
		rcs.unsubscribe(EventsManagerEvents.class, this);
		rcs.unsubscribe(LifecycleEvents.class, this);
		rcs.unuseServices(RegistryReadServices.class);
		rcs.unuseServices(MetamodelReadServices.class);
		rcs.unuseServices(ServicesManagerServices.class);
		rcs.unuseServices(ParametersManagerServices.class);
		rcs.unuseServices(EventsManagerServices.class);
		rcs.unuseServices(DependencyGraphServices.class);
		rcs.unuseServices(LifecycleServices.class);
		rcs.unuseServices(AsyncWorkersServices.class);
		rcs.unuseServices(EventsWorkersServices.class);
		rcs.unaccessParameters(AsyncParameters.class);
		rcs.unaccessParameters(EventsManagerParameters.class);
		rcs.unregisterComponent(ObservedContainerDelegate.class);
	}

	// --- RegistryEvents/ServicesManagerEvents/ParametersManagerEvents/EventsManagerEvents/
	// LifecycleEvents: the real interfaces, forwarding each notification one method at a time
	// to a plain method on the owning Bridge - never through a shared Covalent
	// port, so the Bridge's own @ProducedEvents fields stay private and this
	// delegate stays entirely outside the managed component model. ---

	@Override
	public <CT> void onComponentRegistered(final Class<CT> componentType, final Object componentInstance) {
		this.owner.onComponentRegistered(componentType, componentInstance);
	}

	@Override
	public <CT> void onComponentUnregistered(final Class<CT> componentType, final Object componentInstance) {
		this.owner.onComponentUnregistered(componentType, componentInstance);
	}

	@Override
	public <CT, SERVICES> void onServicesProvided(final Class<CT> componentType, final Class<SERVICES> servicesInterface) {
		this.owner.onServicesProvided(componentType, servicesInterface);
	}

	@Override
	public <CT, SERVICES> void onServicesUnprovided(final Class<CT> componentType, final Class<SERVICES> servicesInterface) {
		this.owner.onServicesUnprovided(componentType, servicesInterface);
	}

	@Override
	public <CT, SERVICES> void onServicesUsed(final Class<CT> servicesUser, final Class<SERVICES> servicesInterface) {
		this.owner.onServicesUsed(servicesUser, servicesInterface);
	}

	@Override
	public <CT, SERVICES> void onServicesUnused(final Class<CT> servicesUser, final Class<SERVICES> servicesInterface) {
		this.owner.onServicesUnused(servicesUser, servicesInterface);
	}

	@Override
	public <SERVICES> void onUsedServiceCallStarted(final Class<SERVICES> servicesInterface,
			final Method method, final Class<?> servicesUser, final Object... inputData) {
		this.owner.onUsedServiceCallStarted(servicesInterface, method, servicesUser, inputData);
	}

	@Override
	public <SERVICES> void onUsedServiceCallFinished(final Class<SERVICES> servicesInterface,
			final Method method, final Class<?> servicesUser, final long durationNanos,
			final Throwable failure, final Object returnValue, final Object... inputData) {
		this.owner.onUsedServiceCallFinished(servicesInterface, method, servicesUser, durationNanos, failure, returnValue);
	}

	@Override
	public <SERVICES> void onProvidedServiceCallStarted(final Class<SERVICES> servicesInterface,
			final Method method, final Class<?> servicesProvider, final Object... inputData) {
		this.owner.onProvidedServiceCallStarted(servicesInterface, method, servicesProvider, inputData);
	}

	@Override
	public <SERVICES> void onProvidedServiceCallFinished(final Class<SERVICES> servicesInterface,
			final Method method, final Class<?> servicesProvider, final long durationNanos,
			final Throwable failure, final Object returnValue, final Object... inputData) {
		this.owner.onProvidedServiceCallFinished(servicesInterface, method, servicesProvider, durationNanos, failure, returnValue);
	}

	@Override
	public <PARAMETERS> void onParametersOffered(final Class<?> componentType, final Class<PARAMETERS> parametersInterface) {
		this.owner.onParametersOffered(componentType, parametersInterface);
	}

	@Override
	public <PARAMETERS> void onParametersUnoffered(final Class<?> componentType, final Class<PARAMETERS> parametersInterface) {
		this.owner.onParametersUnoffered(componentType, parametersInterface);
	}

	@Override
	public <PARAMETERS> void onParametersAccessed(final Class<?> parametersAccessor, final Class<PARAMETERS> parametersInterface) {
		this.owner.onParametersAccessed(parametersAccessor, parametersInterface);
	}

	@Override
	public <PARAMETERS> void onParametersUnaccessed(final Class<?> parametersAccessor, final Class<PARAMETERS> parametersInterface) {
		this.owner.onParametersUnaccessed(parametersAccessor, parametersInterface);
	}

	@Override
	public <PARAMETERS> void onAccessedParametersCallStarted(final Class<PARAMETERS> parametersInterface,
			final Method method, final Class<?> parametersAccessor, final Object... inputData) {
		this.owner.onAccessedParametersCallStarted(parametersInterface, method, parametersAccessor, inputData);
	}

	@Override
	public <PARAMETERS> void onAccessedParametersCallFinished(final Class<PARAMETERS> parametersInterface,
			final Method method, final Class<?> parametersAccessor, final long durationNanos,
			final Throwable failure, final Object returnValue, final Object... inputData) {
		this.owner.onAccessedParametersCallFinished(parametersInterface, method, parametersAccessor, durationNanos,
				failure, returnValue);
	}

	@Override
	public <PARAMETERS> void onOfferedParametersCallStarted(final Class<PARAMETERS> parametersInterface,
			final Method method, final Class<?> parametersOfferor, final Object... inputData) {
		this.owner.onOfferedParametersCallStarted(parametersInterface, method, parametersOfferor, inputData);
	}

	@Override
	public <PARAMETERS> void onOfferedParametersCallFinished(final Class<PARAMETERS> parametersInterface,
			final Method method, final Class<?> parametersOfferor, final long durationNanos,
			final Throwable failure, final Object returnValue, final Object... inputData) {
		this.owner.onOfferedParametersCallFinished(parametersInterface, method, parametersOfferor, durationNanos,
				failure, returnValue);
	}

	@Override
	public <EVENTS> void onEventsProduced(final Class<?> componentType, final Class<EVENTS> eventsInterface) {
		this.owner.onEventsProduced(componentType, eventsInterface);
	}

	@Override
	public <EVENTS> void onEventsUnproduced(final Class<?> componentType, final Class<EVENTS> eventsInterface) {
		this.owner.onEventsUnproduced(componentType, eventsInterface);
	}

	@Override
	public <EVENTS> void onEventsSubscribed(final Class<? extends EVENTS> eventsConsumer, final Class<EVENTS> eventsInterface) {
		this.owner.onEventsSubscribed(eventsConsumer, eventsInterface);
	}

	@Override
	public <EVENTS> void onEventsUnsubscribed(final Class<? extends EVENTS> eventsConsumer, final Class<EVENTS> eventsInterface) {
		this.owner.onEventsUnsubscribed(eventsConsumer, eventsInterface);
	}

	@Override
	public <EVENTS> void onEventNotificationStarted(final Class<EVENTS> eventsInterface,
			final Method method, final Class<?> eventsProducer, final Class<?> eventsConsumer,
			final Class<? extends EVENTS> subscriberType, final boolean asynchronous, final long queueWaitNanos,
			final Object... inputData) {
		if (isDeliveryToThisDelegate(eventsConsumer, subscriberType)) {
			return;
		}
		this.owner.onEventNotificationStarted(eventsInterface, method, eventsProducer, eventsConsumer, subscriberType,
				asynchronous, queueWaitNanos, inputData);
	}

	@Override
	public <EVENTS> void onEventNotificationFinished(final Class<EVENTS> eventsInterface,
			final Method method, final Class<?> eventsProducer, final Class<?> eventsConsumer,
			final Class<? extends EVENTS> subscriberType, final long durationNanos, final Throwable failure,
			final Object... inputData) {
		if (isDeliveryToThisDelegate(eventsConsumer, subscriberType)) {
			return;
		}
		this.owner.onEventNotificationFinished(eventsInterface, method, eventsProducer, eventsConsumer, subscriberType,
				durationNanos, failure);
	}

	/**
	 * The observed container's own notifications to this delegate are event deliveries too, which
	 * the instrumentation reports back here. They describe the supervision's plumbing, never the
	 * observed application, so they are dropped before reaching the bridge.
	 */
	private static boolean isDeliveryToThisDelegate(final Class<?> eventsConsumer, final Class<?> subscriberType) {
		return eventsConsumer == ObservedContainerDelegate.class || subscriberType == ObservedContainerDelegate.class;
	}

	@Override
	public void onComponentInstantiated(final Class<?> componentType) {
		this.owner.onComponentStateChanged(componentType, ComponentState.instantiated);
	}

	@Override
	public void onComponentInitialized(final Class<?> componentType) {
		this.owner.onComponentStateChanged(componentType, ComponentState.initialized);
	}

	@Override
	public void onComponentActivated(final Class<?> componentType) {
		this.owner.onComponentStateChanged(componentType, ComponentState.activated);
	}

	@Override
	public void onComponentPassivated(final Class<?> componentType) {
		this.owner.onComponentStateChanged(componentType, ComponentState.passivated);
	}

	@Override
	public void onComponentRemoved(final Class<?> componentType) {
		this.owner.onComponentStateChanged(componentType, ComponentState.removed);
	}

	@Override
	public void onComponentFailed(final Class<?> componentType) {
		this.owner.onComponentStateChanged(componentType, ComponentState.failed);
	}
}
