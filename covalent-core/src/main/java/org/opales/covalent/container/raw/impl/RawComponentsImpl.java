/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.raw.impl;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.opales.covalent.ManagedContainerRelay;
import org.opales.covalent.container.component.ComponentAnnotations;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.directory.NamingServices;
import org.opales.covalent.container.events.EventsManagerServices;
import org.opales.covalent.container.events.SubscribeParameters;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.lifecycle.Initialization;
import org.opales.covalent.container.lifecycle.LifecycleEvents;
import org.opales.covalent.container.lifecycle.LifecycleManager;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.lifecycle.RawComponentLifecycleServices;
import org.opales.covalent.container.lifecycle.impl.LifecycleManagerImpl;
import org.opales.covalent.container.metamodel.MetamodelPortReadServices;
import org.opales.covalent.container.metamodel.MetamodelPortWriteServices;
import org.opales.covalent.container.metamodel.MetamodelWriteServices;
import org.opales.covalent.container.raw.RawComponents;
import org.opales.covalent.container.raw.RawComponentsParameters;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.registry.DescriptorRegistryWriteServices;
import org.opales.covalent.container.registry.PortRegistryServices;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.rr.parameters.ParametersLifecycleServices;
import org.opales.covalent.container.rr.parameters.ParametersManagerServices;
import org.opales.covalent.container.rr.services.ServicesLifecycleServices;
import org.opales.covalent.container.rr.services.ServicesManagerServices;
import org.opales.covalent.container.util.CovalentDiagnostics;
import org.opales.covalent.container.util.RuntimeCheck;
import org.opales.covalent.container.validation.ValidationServices;

/**
 * Implements the raw (externally owned, non-Covalent) component ports:
 * registration and lifecycle advancement of externally owned instances
 * ({@link RawComponentsServices}), the services/parameters/events ports they
 * may use ({@link RawComponents}), and their default lifecycle state to
 * reach on registration ({@link RawComponentsParameters}). Delegates the
 * actual services/parameters and events port mechanics to the collaborating
 * {@link RawServicesAndParametersPort} and {@link RawEventsPort}, created
 * during {@link #initialize()}. Also implements {@link LifecycleEvents} to
 * clean up a failed component's raw port registrations.
 */
@Implementation
public class RawComponentsImpl implements RawComponents, RawComponentsServices, RawComponentsParameters,
		LifecycleEvents {
	private static Logger log = Logger.getLogger(RawComponentsImpl.class.getName());

	/** Creates a new raw components service implementation. */
	public RawComponentsImpl() {
	}

	@UsedServices
	private ValidationServices useValidationServices;

	@UsedServices
	private MetamodelPortReadServices useMetamodelPortReadServices;

	@UsedServices
	private MetamodelPortWriteServices useMetamodelPortWriteServices;

	@UsedServices
	private MetamodelWriteServices useMetamodelWriteServices;

	@UsedServices
	private PortRegistryServices usePortRegistryServices;

	@UsedServices
	private DescriptorRegistryWriteServices useDescriptorRegistryWriteServices;

	@UsedServices
	private RawComponentLifecycleServices useRawComponentLifecycleServices;

	@UsedServices
	private RegistryReadServices useRegistryServices;

	@UsedServices
	private NamingServices useNamingServices;

	@UsedServices
	private ServicesManagerServices useServicesManagerServices;

	@UsedServices
	private ServicesLifecycleServices useServicesLifecycleServices;

	@UsedServices
	private EventsManagerServices useEventsManagerServices;

	@UsedServices
	private ParametersManagerServices useParametersManagerServices;

	@UsedServices
	private ParametersLifecycleServices useParametersLifecycleServices;

	@UsedServices
	private LifecycleServices useLifecycleServices;

	private LifecycleManagerImpl lifecycleManagerImpl;

	private RawServicesAndParametersPort rawServicesAndParametersPort;

	private RawEventsPort rawEventsPort;

	private ComponentState defaultStateToReach = ComponentState.activated;

	/**
	 * The component types this component deployed as raw components. Only these are cleaned up
	 * and undeployed when they fail: a managed component is reset instead.
	 */
	private final Set<Class<?>> rawComponentTypes = ConcurrentHashMap.newKeySet();

	/**
	 * Creates the collaborating {@link RawServicesAndParametersPort} and
	 * {@link RawEventsPort} that back this component's raw port methods.
	 * <p>
	 * Built during initialization, not activation: these port objects are
	 * essential wiring (not an optional resource to allocate lazily), and
	 * initialization - unlike activation for internal components - still runs
	 * once, unconditionally, at container construction time. This is what lets
	 * raw components be registered before the container's first start().
	 */
	@Initialization
	public void initialize() {
		this.lifecycleManagerImpl = this.useRegistryServices.getComponentInstance(LifecycleManager.class);
		this.rawServicesAndParametersPort = new RawServicesAndParametersPort(this.useValidationServices,
				this.useMetamodelPortReadServices, this.useMetamodelPortWriteServices, this.useMetamodelWriteServices,
				this.usePortRegistryServices, this.useDescriptorRegistryWriteServices, this.useNamingServices,
				this.useServicesManagerServices, this.useServicesLifecycleServices, this.useParametersManagerServices,
				this.useParametersLifecycleServices, this.useRegistryServices);
		this.rawEventsPort = new RawEventsPort(this.useValidationServices, this.useMetamodelPortReadServices,
				this.useMetamodelPortWriteServices, this.useMetamodelWriteServices, this.usePortRegistryServices,
				this.useDescriptorRegistryWriteServices, this.useNamingServices, this.useEventsManagerServices,
				this.useRegistryServices);
	}

	@Override
	public void onComponentFailed(final Class<?> componentType) {
		if (!this.rawComponentTypes.contains(componentType)) {
			return;
		}
		Class<?> implementation = this.useMetamodelWriteServices.getImplementation(componentType);
		try {
			this.closeRawPortsOf(componentType, implementation);
			this.useLifecycleServices.removeComponent(componentType);
			if (implementation != null) {
				this.useLifecycleServices.undeployComponent(implementation);
			}
			this.rawComponentTypes.remove(componentType);
		} catch (RuntimeException | Error cleanupFailure) {
			CovalentDiagnostics.severe(log, "RAW failure cleanup incomplete for " + componentType.getSimpleName()
					+ ".", cleanupFailure);
		}
	}

	@Override
	public void onComponentInstantiated(final Class<?> componentType) {
		// Nothing to do: raw ports are closed on removal and on failure.
	}

	@Override
	public void onComponentInitialized(final Class<?> componentType) {
		// Nothing to do: raw ports are closed on removal and on failure.
	}

	@Override
	public void onComponentActivated(final Class<?> componentType) {
		// Nothing to do: raw ports are closed on removal and on failure.
	}

	@Override
	public void onComponentPassivated(final Class<?> componentType) {
		// Nothing to do: raw ports are closed on removal and on failure.
	}

	/**
	 * Closes the raw ports of a raw component once it is removed - by the
	 * container's stop cascade in particular, as unregisterComponent() does -
	 * so that each raw component's ports follow its own lifecycle.
	 */
	@Override
	public void onComponentRemoved(final Class<?> componentType) {
		if (!this.rawComponentTypes.contains(componentType)) {
			return;
		}
		this.closeRawPortsOf(componentType, this.useMetamodelWriteServices.getImplementation(componentType));
	}

	/**
	 * Closes every raw port a raw component owns: first what it uses, accesses
	 * and subscribes to, then what it provides, offers and produces.
	 *
	 * @param componentType  the raw component type
	 * @param implementation the raw component's implementation, or {@code null}
	 *                       when none is registered any more
	 */
	private void closeRawPortsOf(final Class<?> componentType, final Class<?> implementation) {
		Set<Class<?>> owners = new LinkedHashSet<>();
		owners.add(componentType);
		// Caller-based manual ports are indexed by implementation, explicit ports by type.
		if (implementation != null) {
			owners.add(implementation);
		}
		for (Class<?> owner : owners) {
			this.rawServicesAndParametersPort.unuseAllServicesFor(owner);
			this.rawServicesAndParametersPort.unaccessAllParametersFor(owner);
			this.rawEventsPort.unsubscribeAllFor(owner);
		}
		for (Class<?> owner : owners) {
			this.rawEventsPort.unproduceAllEventsFor(owner);
			this.rawServicesAndParametersPort.unprovideAllServicesFor(owner);
			this.rawServicesAndParametersPort.unofferAllParametersFor(owner);
		}
	}

	@Override
	public void setDefaultStateToReach(final ComponentState componentStateToReach) {
		RuntimeCheck.notNull(componentStateToReach, "defaultStateToReach");
		this.defaultStateToReach = componentStateToReach;
	}

	@Override
	public ComponentState getDefaultStateToReach() {
		return this.defaultStateToReach;
	}

	@Override
	public <SERVICES> void provideServices(final Class<?> providerComponentType,
			final Class<SERVICES> servicesInterface) {
		this.rawServicesAndParametersPort.provideServices(providerComponentType, servicesInterface);
	}

	@Override
	public <SERVICES> void provideServices(final Class<SERVICES> servicesInterface) {
		this.rawServicesAndParametersPort.provideServices(servicesInterface);
	}

	@Override
	public <SERVICES> void unprovideServices(final Class<?> providerComponentType,
			final Class<SERVICES> servicesInterface) {
		this.rawServicesAndParametersPort.unprovideServices(providerComponentType, servicesInterface);
	}

	@Override
	public <SERVICES> void unprovideServices(final Class<SERVICES> servicesInterface) {
		this.rawServicesAndParametersPort.unprovideServices(servicesInterface);
	}

	@Override
	public <SERVICES> SERVICES useServices(final Class<?> providerComponentType,
			final Class<SERVICES> servicesInterface) {
		return this.rawServicesAndParametersPort.useServices(providerComponentType, servicesInterface);
	}

	@Override
	public <SERVICES> SERVICES useServices(final Class<SERVICES> servicesInterface) {
		return this.rawServicesAndParametersPort.useServices(servicesInterface);
	}

	@Override
	public <SERVICES> void unuseServices(final Class<SERVICES> servicesInterface) {
		this.rawServicesAndParametersPort.unuseServices(servicesInterface);
	}

	/*    														 */
	/* produce events methods with component type as argument */
	/*    														 */

	@Override
	public <EVENTS> EVENTS produceEvents(final Class<?> producerComponentType, final Class<EVENTS> eventsInterface) {
		return this.rawEventsPort.produceEvents(producerComponentType, eventsInterface);
	}

	@Override
	public <EVENTS> EVENTS produceEvents(final Class<?> producerComponentType, final Class<EVENTS> eventsInterface,
			final Consumer<EVENTS> subscribeDelegateConsumer) {
		return this.rawEventsPort.produceEvents(producerComponentType, eventsInterface, subscribeDelegateConsumer);
	}

	@Override
	public <EVENTS> EVENTS produceEvents(final Class<?> producerComponentType, final Class<EVENTS> eventsInterface,
			final Consumer<EVENTS> subscribeDelegateConsumer, final Consumer<EVENTS> unsubscribeDelegateConsumer) {
		return this.rawEventsPort.produceEvents(producerComponentType, eventsInterface, subscribeDelegateConsumer,
				unsubscribeDelegateConsumer);
	}

	/*    														    */
	/* produce events methods without component type as argument */
	/*    														    */

	@Override
	public <EVENTS> EVENTS produceEvents(final Class<EVENTS> eventsInterface) {
		return this.rawEventsPort.produceEvents(eventsInterface);
	}

	@Override
	public <EVENTS> EVENTS produceEvents(final Class<EVENTS> eventsInterface,
			final Consumer<EVENTS> subscribeDelegateConsumer) {
		return this.rawEventsPort.produceEvents(eventsInterface, subscribeDelegateConsumer);
	}

	@Override
	public <EVENTS> EVENTS produceEvents(final Class<EVENTS> eventsInterface,
			final Consumer<EVENTS> subscribeDelegateConsumer, final Consumer<EVENTS> unsubscribeDelegateConsumer) {
		return this.rawEventsPort.produceEvents(eventsInterface, subscribeDelegateConsumer, unsubscribeDelegateConsumer);
	}

	@Override
	public <EVENTS> void unproduceEvents(final Class<?> producerComponentType, final Class<EVENTS> eventsInterface) {
		this.rawEventsPort.unproduceEvents(producerComponentType, eventsInterface);
	}

	@Override
	public <EVENTS> void unproduceEvents(final Class<EVENTS> eventsInterface) {
		this.rawEventsPort.unproduceEvents(eventsInterface);
	}

	@Override
	public <EVENTS> void subscribe(final Class<?> consumerComponentType, final Class<EVENTS> eventsInterface,
			final EVENTS subscriber) {
		this.rawEventsPort.subscribe(consumerComponentType, eventsInterface, subscriber);
	}

	@Override
	public <EVENTS> void subscribe(final Class<?> consumerComponentType, final Class<EVENTS> eventsInterface,
			final EVENTS subscriber, final SubscribeParameters subscribeParameters) {
		this.rawEventsPort.subscribe(consumerComponentType, eventsInterface, subscriber, subscribeParameters);
	}

	@Override
	public <EVENTS> void subscribe(final Class<EVENTS> eventsInterface, final EVENTS subscriber) {
		this.rawEventsPort.subscribe(eventsInterface, subscriber);
	}

	@Override
	public <EVENTS> void subscribe(final Class<EVENTS> eventsInterface, final EVENTS subscriber,
			final SubscribeParameters subscribeParameters) {
		this.rawEventsPort.subscribe(eventsInterface, subscriber, subscribeParameters);
	}

	@Override
	public <EVENTS> void unsubscribe(final Class<?> consumerComponentType, final Class<EVENTS> eventsInterface,
			final EVENTS subscriber) {
		this.rawEventsPort.unsubscribe(consumerComponentType, eventsInterface, subscriber);
	}

	@Override
	public <EVENTS> void unsubscribe(final Class<EVENTS> eventsInterface, final EVENTS subscriber) {
		this.rawEventsPort.unsubscribe(eventsInterface, subscriber);
	}

	@Override
	public <PARAMETERS> void offerParameters(final Class<?> offerorComponentType,
			final Class<PARAMETERS> parametersInterface) {
		this.rawServicesAndParametersPort.offerParameters(offerorComponentType, parametersInterface);
	}

	@Override
	public <PARAMETERS> void offerParameters(final Class<PARAMETERS> parametersInterface) {
		this.rawServicesAndParametersPort.offerParameters(parametersInterface);
	}

	@Override
	public <PARAMETERS> void unofferParameters(final Class<?> offerorComponentType,
			final Class<PARAMETERS> parametersInterface) {
		this.rawServicesAndParametersPort.unofferParameters(offerorComponentType, parametersInterface);
	}

	@Override
	public <PARAMETERS> void unofferParameters(final Class<PARAMETERS> parametersInterface) {
		this.rawServicesAndParametersPort.unofferParameters(parametersInterface);
	}

	@Override
	public <PARAMETERS> PARAMETERS accessParameters(final Class<PARAMETERS> parametersInterface) {
		return this.rawServicesAndParametersPort.accessParameters(parametersInterface);
	}

	@Override
	public <PARAMETERS> PARAMETERS accessParameters(final Class<?> offerorComponentType,
			final Class<PARAMETERS> parametersInterface) {
		return this.rawServicesAndParametersPort.accessParameters(offerorComponentType, parametersInterface);
	}

	@Override
	public <PARAMETERS> void unaccessParameters(final Class<PARAMETERS> parametersInterface) {
		this.rawServicesAndParametersPort.unaccessParameters(parametersInterface);
	}

	/**
	 * Tears down every raw port registration held by this component on
	 * container shutdown.
	 * <p>
	 * User-side raw ports must be disconnected before provider-side raw ports.
	 * Otherwise a remaining client proxy may still reference a port that is being
	 * passivated or unpublished during the same stop sequence.
	 */
	@Passivation
	public void passivate() {
		this.rawServicesAndParametersPort.unuseAllServices();
		this.rawServicesAndParametersPort.unaccessAllParameters();
		this.rawEventsPort.unsubscribeAll();
		this.rawEventsPort.unproduceAllEvents();
		this.rawServicesAndParametersPort.unprovideAllServices();
		this.rawServicesAndParametersPort.unofferAllParameters();
	}

	/**
	 * <p>
	 * Registers a raw component with its implementation class as component type.
	 * </p>
	 * 
	 * <p>
	 * Managed Covalent components must be deployed through the managed lifecycle
	 * API, not registered through raw component services.
	 * </p>
	 * 
	 * @param componentInstance externally owned raw component instance
	 * @return the inferred raw component type
	 */
	@Override
	public Class<?> registerComponent(final Object componentInstance) {
		return this.registerComponent(componentInstance, this.defaultStateToReach);
	}

	@Override
	public Class<?> registerComponent(final Object componentInstance, final ComponentState stateToReach) {
		RuntimeCheck.notNull(componentInstance, "componentInstance");
		Class<?> componentType = componentInstance.getClass();
		this.registerComponent(componentType, componentInstance, stateToReach);
		return componentType;
	}

	@Override
	public void registerComponent(final Class<?> componentType, final Object componentInstance) {
		this.registerComponent(componentType, componentInstance, this.defaultStateToReach);
	}

	/**
	 * <p>
	 * Registers component and brings it up to provided component state to reach.
	 * Allowed states to reach are instantiated, initialized, activated and
	 * passivated.
	 * </p>
	 * <p>
	 * Safe to call again on an already-registered component: every step is a
	 * no-op once the component has already reached (or passed) it, so calling
	 * this method repeatedly - with the same or a different {@code stateToReach}
	 * - only ever advances the component, never re-runs a step or throws because
	 * a step already happened. This is what lets
	 * {@link #activateComponent(Class, Object)} bring an already-registered
	 * component further along by calling this method again internally.
	 * </p>
	 * 
	 * @param componentType     the raw component type to register the instance under
	 * @param componentInstance the externally owned raw component instance
	 * @param stateToReach      the lifecycle state to bring the component to;
	 *                          must be one of {@code instantiated},
	 *                          {@code initialized}, {@code activated} or
	 *                          {@code passivated}
	 */
	@Override
	public void registerComponent(final Class<?> componentType, final Object componentInstance,
			final ComponentState stateToReach) {
		RuntimeCheck.notNull(componentType, "componentType");
		RuntimeCheck.notNull(componentInstance, "componentInstance");
		RuntimeCheck.notNull(stateToReach, "stateToReach");

		switch (stateToReach) {
			case instantiated:
			case initialized:
			case activated:
			case passivated:
				break;
			case deployed:
			case removed:
			case failed:
				throw new IllegalArgumentException("State " + stateToReach + " not allowed as state to reach.");
			default:
				break;
		}
		// create metamodel
		final Class<?> implementation = componentInstance.getClass();
		this.checkRawComponentContract(componentType, implementation);
		if (!this.useLifecycleServices.isDeployed(componentType)) {
			this.deployRawComponent(componentType, implementation);
		}
		// create descriptor
		if (!this.useLifecycleServices.isInstantiated(componentType)) {
			this.useRawComponentLifecycleServices.pseudoInstantiateComponent(componentType, componentInstance);
		}

		// A componentType already occupied by a different, previously-registered
		// instance must be rejected outright, before any state-to-reach shortcut can
		// act on it. Without this check, a caller registering a second, distinct
		// instance under an already-instantiated componentType would silently fall
		// through: pseudoInstantiateComponent is skipped (the type is already
		// instantiated), the second instance is never stored anywhere, and any
		// subsequent initialize/activate/passivate step below acts on the FIRST
		// instance still on file - advancing it while discarding the second instance
		// with no error at all. This must be checked here, not only below, because a
		// caller asking only for ComponentState.instantiated to be reached returns
		// immediately after this point and would otherwise never hit the identity
		// check that follows.
		this.requireSameRegisteredInstance(componentType, componentInstance);
		if (ComponentState.instantiated == stateToReach) {
			return;
		}

		// From here on, a step already reached is skipped: the identity check above
		// guarantees that componentInstance is the instance already registered under
		// componentType - the legitimate progressive-adoption case of calling
		// registerComponent again on the same raw object.

		// initialize - isInitialized already means "at least initialized" (true for
		// initialized, activated, or passivated), matching the isDeployed/
		// isInstantiated guards above.
		if (!this.useLifecycleServices.isInitialized(componentType)) {
			this.useLifecycleServices.initializeComponent(componentType);
		}
		if (ComponentState.initialized == stateToReach) {
			return;
		}

		// activate - unlike isInitialized, isActivated is an exact match (true only
		// while activated, not once passivated), so "already past this step" also
		// has to check isPassivated explicitly.
		if (!(this.useLifecycleServices.isActivated(componentType)
				|| this.useLifecycleServices.isPassivated(componentType))) {
			this.useLifecycleServices.activateComponent(componentType);
		}
		if (ComponentState.activated == stateToReach) {
			return;
		}

		if (!this.useLifecycleServices.isPassivated(componentType)) {
			this.useLifecycleServices.passivateComponent(componentType);
		}
	}

	private void deployRawComponent(final Class<?> componentType, final Class<?> implementation) {
		this.lifecycleManagerImpl.deployRawComponent(componentType, implementation);
		this.rawComponentTypes.add(componentType);
	}

	private void checkRawComponentContract(final Class<?> componentType, final Class<?> implementation) {
		RuntimeCheck.notNull(componentType, "componentType");
		RuntimeCheck.notNull(implementation, "implementation");

		// @Internal is reserved to Covalent's own components: a raw component carrying it
		// would be kept across stop() like them. ManagedContainerRelay, registered by
		// Covalent on each managed container, is the only raw component allowed to.
		if ((ComponentAnnotations.isInternalComponent(componentType)
				|| ComponentAnnotations.isInternalComponent(implementation))
				&& componentType != ManagedContainerRelay.class) {
			throw new IllegalArgumentException("Raw component " + implementation.getSimpleName()
					+ " cannot be registered: @Internal is reserved to Covalent's own components.");
		}

		if (ComponentAnnotations.isComponent(componentType) || ComponentAnnotations.isImplementation(componentType)
				|| ComponentAnnotations.isItem(componentType) || ComponentAnnotations.isImplementation(implementation)
				|| ComponentAnnotations.isItem(implementation)
				|| this.usesManagedServicesOrParametersPort(componentType)
				|| this.usesManagedServicesOrParametersPort(implementation)) {
			throw new IllegalArgumentException(this.buildRawComponentContractMessage(implementation));
		}

		for (Field field : implementation.getDeclaredFields()) {
			if (ComponentAnnotations.isItemField(field) || ComponentAnnotations.isImplementationField(field)
					|| ComponentAnnotations.isServicesField(field) || ComponentAnnotations.isParametersField(field)) {
				throw new IllegalArgumentException(this.buildRawComponentContractMessage(implementation));
			}
		}
	}

	private boolean usesManagedServicesOrParametersPort(final Class<?> componentClass) {
		if (componentClass == null || Object.class.equals(componentClass)) {
			return false;
		}
		if (ComponentAnnotations.isServices(componentClass) || ComponentAnnotations.isParameters(componentClass)) {
			return true;
		}
		for (Class<?> componentInterface : componentClass.getInterfaces()) {
			if (this.usesManagedServicesOrParametersPort(componentInterface)) {
				return true;
			}
		}
		return this.usesManagedServicesOrParametersPort(componentClass.getSuperclass());
	}

	private String buildRawComponentContractMessage(final Class<?> implementation) {
		return "Raw component registration only accepts non-Covalent component classes. "
				+ "Covalent-annotated components must be fully managed by the container through the managed lifecycle API. "
				+ "RawComponentsServices is intended to interface with the non-Covalent world, so it cannot register "
				+ implementation.getSimpleName()
				+ " when the component type, implementation, item, services port, parameters port, or nested structure uses Covalent annotations.";
	}

	@Override
	public void unregisterComponent(final Class<?> componentType) throws RuntimeException {
		RuntimeCheck.notNull(componentType, "componentType");

		Class<?> resolvedComponentType = componentType;
		Class<?> implementation = this.useMetamodelWriteServices.getImplementation(resolvedComponentType);
		if (implementation == null) {
			// maybe the argument is a component implementation ?
			Class<?> type = this.useMetamodelWriteServices.getType(componentType);
			if (type != null) {
				String message = "unregisterComponent takes a component type as argument, but provided class '"
						+ componentType.getSimpleName() + "' is a component implementation.";
				this.useValidationServices.handleStructuralVerification(message);
				resolvedComponentType = type;
				implementation = componentType;
			}
		}
		RuntimeCheck.notNull(implementation, "implementation");
		if (ComponentAnnotations.isInternalComponent(resolvedComponentType)) {
			throw new IllegalArgumentException("Component " + resolvedComponentType.getSimpleName()
					+ " cannot be unregistered: it is one of Covalent's own components.");
		}

		if (this.useLifecycleServices.isInitialized(resolvedComponentType)
				&& !this.useLifecycleServices.isPassivated(resolvedComponentType)) {
			this.useLifecycleServices.passivateComponent(resolvedComponentType);
		}

		// Passivation callbacks should close raw ports. The unregister path still
		// guarantees cleanup before removal, so stale raw proxies cannot survive.
		// unsubscribeAllFor is included here for the same reason: onComponentFailed
		// and stop() both call it, so unregisterComponent should not be the only
		// cleanup path that skips event subscriptions, even though passivation's
		// own consumed-events auto-wiring teardown already covers the common case.
		this.rawServicesAndParametersPort.unuseAllServicesFor(resolvedComponentType);
		this.rawServicesAndParametersPort.unaccessAllParametersFor(resolvedComponentType);
		this.rawEventsPort.unsubscribeAllFor(resolvedComponentType);
		this.rawEventsPort.unproduceAllEventsFor(resolvedComponentType);
		this.rawServicesAndParametersPort.unprovideAllServicesFor(resolvedComponentType);
		this.rawServicesAndParametersPort.unofferAllParametersFor(resolvedComponentType);

		if (this.useLifecycleServices.isInstantiated(resolvedComponentType)
				&& !this.useLifecycleServices.isRemoved(resolvedComponentType)) {
			this.useLifecycleServices.removeComponent(resolvedComponentType);
		}

		this.useLifecycleServices.undeployComponent(implementation);
		this.rawComponentTypes.remove(resolvedComponentType);
	}

	/**
	 * Activates an externally owned raw component instance with an explicit
	 * component type.
	 * 
	 * @param componentType     raw component type exposed to Covalent
	 * @param componentInstance externally owned raw component instance
	 */
	@Override
	public void activateComponent(final Class<?> componentType, final Object componentInstance)
			throws RuntimeException {
		RuntimeCheck.notNull(componentType, "componentType");
		RuntimeCheck.notNull(componentInstance, "componentInstance");

		if (!this.useLifecycleServices.isInitialized(componentType)) {
			this.registerComponent(componentType, componentInstance, ComponentState.initialized);
		} else {
			this.requireSameRegisteredInstance(componentType, componentInstance);
		}
		this.useLifecycleServices.activateComponent(componentType);
	}

	/**
	 * Checks that {@code componentInstance} is the instance registered under
	 * {@code componentType}: a second, distinct instance under an already-occupied
	 * component type is not supported.
	 *
	 * @param componentType     the raw component type
	 * @param componentInstance the instance the caller refers to
	 * @throws IllegalStateException if another instance is registered under that type
	 */
	private void requireSameRegisteredInstance(final Class<?> componentType, final Object componentInstance) {
		Object currentlyRegisteredInstance = this.useRegistryServices.getComponentInstance(componentType);
		if (componentInstance != currentlyRegisteredInstance) {
			throw new IllegalStateException("Cannot register component instance " + componentInstance
					+ " for component type " + componentType.getName() + ": instance " + currentlyRegisteredInstance
					+ " is already registered under this component type. Registering a second, distinct instance "
					+ "under an already-occupied component type is not supported.");
		}
	}

}
