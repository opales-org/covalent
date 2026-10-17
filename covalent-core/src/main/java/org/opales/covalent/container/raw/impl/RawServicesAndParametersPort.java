/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.raw.impl;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.opales.covalent.container.metamodel.MetamodelPortReadServices;
import org.opales.covalent.container.metamodel.MetamodelPortWriteServices;
import org.opales.covalent.container.metamodel.MetamodelWriteServices;
import org.opales.covalent.container.directory.NamingServices;
import org.opales.covalent.container.registry.DescriptorRegistryWriteServices;
import org.opales.covalent.container.registry.PortRegistryServices;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.rr.parameters.ParametersLifecycleServices;
import org.opales.covalent.container.rr.parameters.ParametersManagerServices;
import org.opales.covalent.container.rr.services.ServicesLifecycleServices;
import org.opales.covalent.container.rr.services.ServicesManagerServices;
import org.opales.covalent.container.util.CallerFinder;
import org.opales.covalent.container.util.RuntimeCheck;
import org.opales.covalent.container.validation.ValidationServices;

/**
 * <p>
 * Owns {@code RawComponentsImpl}'s services and parameters raw ports: their
 * tracking state, their creation/removal, and their bulk recovery cascades
 * used by {@code onComponentFailed}, {@code unregisterComponent}, and
 * {@code stop()}.
 * </p>
 * <p>
 * Grouped together, distinct from raw events, because their managers
 * ({@code ServicesManagerImpl}, {@code ParametersManagerImpl}) share a common
 * request/response implementation base ({@code RRManagerImpl}), unlike
 * events, which is a separate publish/subscribe mechanism
 * ({@code EventsManagerImpl}) with its own dispatch and worker-pool
 * concerns.
 * </p>
 * <p>
 * This is a plain internal collaborator, not a Covalent component: it is
 * constructed and wired by {@code RawComponentsImpl} once its own
 * {@code @UsedServices} fields are populated, from its {@code @Initialization} method.
 * </p>
 * <p>
 * The class is {@code public} only so that
 * {@code org.opales.covalent.container.util.CallerFinder}, in another package,
 * can name it among the internal frames to skip when resolving the real caller.
 * Its constructor and methods are package-private on purpose: it is not an
 * entry point, only {@code RawComponentsImpl} uses it.
 * </p>
 */
public final class RawServicesAndParametersPort {

	private static Logger log = Logger.getLogger(RawServicesAndParametersPort.class.getName());

	private final ValidationServices useValidationServices;
	private final MetamodelPortReadServices useMetamodelPortReadServices;
	private final MetamodelPortWriteServices useMetamodelPortWriteServices;
	private final MetamodelWriteServices useMetamodelWriteServices;
	private final PortRegistryServices usePortRegistryServices;
	private final DescriptorRegistryWriteServices useDescriptorRegistryWriteServices;
	private final NamingServices useNamingServices;
	private final ServicesManagerServices useServicesManagerServices;
	private final ServicesLifecycleServices useServicesLifecycleServices;
	private final ParametersManagerServices useParametersManagerServices;
	private final ParametersLifecycleServices useParametersLifecycleServices;
	private final RegistryReadServices useRegistryReadServices;

	private final Map<Class<?>, Set<Class<?>>> rawProviders = new ConcurrentHashMap<>();

	private final Map<Class<?>, Set<Class<?>>> rawUsers = new ConcurrentHashMap<>();

	private final Map<Class<?>, Set<Class<?>>> rawOfferors = new ConcurrentHashMap<>();

	private final Map<Class<?>, Set<Class<?>>> rawAccessors = new ConcurrentHashMap<>();

	RawServicesAndParametersPort(final ValidationServices useValidationServices,
			final MetamodelPortReadServices useMetamodelPortReadServices,
			final MetamodelPortWriteServices useMetamodelPortWriteServices,
			final MetamodelWriteServices useMetamodelWriteServices, final PortRegistryServices usePortRegistryServices,
			final DescriptorRegistryWriteServices useDescriptorRegistryWriteServices,
			final NamingServices useNamingServices, final ServicesManagerServices useServicesManagerServices,
			final ServicesLifecycleServices useServicesLifecycleServices,
			final ParametersManagerServices useParametersManagerServices,
			final ParametersLifecycleServices useParametersLifecycleServices,
			final RegistryReadServices useRegistryReadServices) {
		this.useValidationServices = useValidationServices;
		this.useMetamodelPortReadServices = useMetamodelPortReadServices;
		this.useMetamodelPortWriteServices = useMetamodelPortWriteServices;
		this.useMetamodelWriteServices = useMetamodelWriteServices;
		this.usePortRegistryServices = usePortRegistryServices;
		this.useDescriptorRegistryWriteServices = useDescriptorRegistryWriteServices;
		this.useNamingServices = useNamingServices;
		this.useServicesManagerServices = useServicesManagerServices;
		this.useServicesLifecycleServices = useServicesLifecycleServices;
		this.useParametersManagerServices = useParametersManagerServices;
		this.useParametersLifecycleServices = useParametersLifecycleServices;
		this.useRegistryReadServices = useRegistryReadServices;
	}

	// ---- services ----

	<SERVICES> void provideServices(final Class<?> providerComponentType, final Class<SERVICES> servicesInterface) {
		RuntimeCheck.notNull(providerComponentType, "providerComponentType");
		if (RawPortSupport.isImplementation(this.useValidationServices, providerComponentType)) {
			this.provideServices(servicesInterface);
			return;
		}
		this.provideServicesInternal(providerComponentType, servicesInterface);
	}

	<SERVICES> void provideServices(final Class<SERVICES> servicesInterface) {
		Class<?> providerComponentType = this.tryToGetComponentTypeForProvidedServices(servicesInterface);
		this.provideServicesInternal(providerComponentType, servicesInterface);
	}

	private <SERVICES> void provideServicesInternal(final Class<?> providerComponentType,
			final Class<SERVICES> servicesInterface) {
		RawPortSupport.requireRegisteredComponentType(this.useRegistryReadServices, providerComponentType,
				"provide services " + servicesInterface);
		this.useValidationServices.checkServices(servicesInterface);
		// Ownership is checked, and the metamodel updated, BEFORE any of the RR-manager /
		// raw-port-tracking / activation side effects below: a declaration rejected by the
		// metamodel's global ownership check (a second, distinct component type already
		// provides this interface) must leave no trace anywhere - not in
		// RRManagerImpl.componentTypeToProvided, not in this port's own rawProviders
		// tracking, and the services must never be activated. Running the check first means
		// a rejection throws before any of that happens, instead of leaving it to be
		// (imperfectly) unwound later.
		Class<?> existingProvider = this.useMetamodelPortReadServices
				.getComponentTypeForProvidedServices(servicesInterface);
		boolean providedByAnotherComponent = existingProvider != null
				&& !existingProvider.equals(providerComponentType)
				&& this.useServicesManagerServices.isServicesProvided(servicesInterface);
		this.updateMetamodelWithProvidedServices(providerComponentType, servicesInterface);
		if (providedByAnotherComponent) {
			// first provider kept; this second declaration must leave no trace
			return;
		}
		this.useServicesManagerServices.createProvidedServices(providerComponentType, servicesInterface);
		RawPortSupport.trackRawPort(this.rawProviders, providerComponentType, servicesInterface);
		this.useServicesManagerServices.activateServices(servicesInterface);
		SERVICES servicesProvider = this.useServicesManagerServices.getServicesProvider(servicesInterface);
		this.useNamingServices.bindServices(servicesInterface, servicesProvider);

	}

	private <SERVICES> void updateMetamodelWithProvidedServices(final Class<?> providerComponentType,
			final Class<SERVICES> servicesInterface) {
		this.updateMetamodelOf(providerComponentType,
				() -> this.useMetamodelPortWriteServices.addProvidedServices(providerComponentType, servicesInterface));
	}

	private void updateMetamodelOf(final Class<?> componentType, final Runnable metamodelUpdate) {
		RawPortSupport.updateMetamodelOf(log, this.useMetamodelWriteServices, this.usePortRegistryServices,
				this.useDescriptorRegistryWriteServices, componentType, metamodelUpdate);
	}

	<SERVICES> void unprovideServices(final Class<?> providerComponentType, final Class<SERVICES> servicesInterface) {
		RuntimeCheck.notNull(providerComponentType, "providerComponentType");
		if (RawPortSupport.isImplementation(this.useValidationServices, providerComponentType)) {
			this.unprovideServices(servicesInterface);
			return;
		}
		this.unprovideServicesInternal(providerComponentType, servicesInterface);
	}

	<SERVICES> void unprovideServices(final Class<SERVICES> servicesInterface) {
		Class<?> providerComponentType = this.tryToGetComponentTypeForProvidedServices(servicesInterface);
		this.unprovideServicesInternal(providerComponentType, servicesInterface);
	}

	private Class<?> tryToGetComponentTypeForProvidedServices(final Class<?> servicesInterface) {
		log.fine("Trying to guess component type for provided services");
		Class<?> providerComponentType = this.useMetamodelPortReadServices
				.getComponentTypeForProvidedServices(servicesInterface);
		if (providerComponentType == null) {
			// assume the component is not managed and the method is called from the
			// provider
			providerComponentType = CallerFinder.getCaller();
			// try to get type from impl
			Class<?> componentType = this.useMetamodelWriteServices.getType(providerComponentType);
			if (componentType != null) {
				providerComponentType = componentType;
			} else {
				String message = "Could not retrieve component type providing services " + servicesInterface
						+ ", this may cause problems with component view. Maybe you should call provideServices method with component type as parameter.";
				this.useValidationServices.handleStructuralVerification(message);
			}
		}
		return providerComponentType;
	}

	private <SERVICES> void unprovideServicesInternal(final Class<?> providerComponentType,
			final Class<SERVICES> servicesInterface) {
		// The owner is checked BEFORE anything is torn down: a refused withdrawal must
		// leave the owner's services fully working.
		Class<?> existingProvider = this.useMetamodelPortReadServices
				.getComponentTypeForProvidedServices(servicesInterface);
		if (existingProvider != null && !existingProvider.equals(providerComponentType)) {
			throw new IllegalArgumentException("Component type " + providerComponentType
					+ " does not match services provider component type " + existingProvider);
		}
		boolean provided = this.useServicesManagerServices.isServicesProvided(servicesInterface);
		if (!provided && existingProvider == null) {
			RawPortSupport.untrackRawPort(this.rawProviders, providerComponentType, servicesInterface);
			this.useValidationServices.handleDynamicVerification(
					"Services " + servicesInterface + " not provided by component " + providerComponentType);
			return;
		}
		if (provided) {
			this.useServicesManagerServices.passivateServices(servicesInterface);
			this.useServicesManagerServices.deleteProvidedServices(servicesInterface);
			this.useServicesLifecycleServices.unpublishProvidedServices(providerComponentType);
		}
		this.updateMetamodelWithUnprovidedServices(providerComponentType, servicesInterface);

		RawPortSupport.untrackRawPort(this.rawProviders, providerComponentType, servicesInterface);
	}

	private <SERVICES> void updateMetamodelWithUnprovidedServices(final Class<?> providerComponentType,
			final Class<SERVICES> servicesInterface) {
		this.updateMetamodelOf(providerComponentType,
				() -> this.useMetamodelPortWriteServices.removeProvidedServices(providerComponentType, servicesInterface));
	}

	<SERVICES> SERVICES useServices(final Class<?> providerComponentType, final Class<SERVICES> servicesInterface) {
		RuntimeCheck.notNull(providerComponentType, "providerComponentType");
		if (RawPortSupport.isImplementation(this.useValidationServices, providerComponentType)) {
			return this.useServices(servicesInterface);
		}
		return this.useServicesInternal(servicesInterface);
	}

	<SERVICES> SERVICES useServices(final Class<SERVICES> servicesInterface) {
		return this.useServicesInternal(servicesInterface);
	}

	private <SERVICES> SERVICES useServicesInternal(final Class<SERVICES> servicesInterface) {
		Class<?> servicesUser = this.tryToGetComponentTypeForServicesUser(servicesInterface);
		RawPortSupport.requireRegisteredComponentType(this.useRegistryReadServices, servicesUser,
				"use services " + servicesInterface);
		SERVICES usedServices = this.useServicesManagerServices.createUsedServices(servicesUser, servicesInterface);
		RawPortSupport.trackRawPort(this.rawUsers, servicesUser, servicesInterface);
		this.updateMetamodelWithUsedServices(servicesUser, servicesInterface);

		return usedServices;
	}

	/**
	 * Records the used services in the user's metamodel, so the consumer side
	 * of a raw services port appears in the metamodel like a declared used
	 * services field, and the dependency graph built from the metamodels
	 * sees it.
	 */
	private <SERVICES> void updateMetamodelWithUsedServices(final Class<?> servicesUser,
			final Class<SERVICES> servicesInterface) {
		this.updateMetamodelOf(servicesUser,
				() -> this.useMetamodelPortWriteServices.addUsedServices(servicesUser, servicesInterface));
	}

	private <SERVICES> void updateMetamodelWithUnusedServices(final Class<?> servicesUser,
			final Class<SERVICES> servicesInterface) {
		this.updateMetamodelOf(servicesUser,
				() -> this.useMetamodelPortWriteServices.removeUsedServices(servicesUser, servicesInterface));
	}

	void unuseServices(final Class<?> servicesInterface) {
		Class<?> servicesUser = this.tryToGetComponentTypeForServicesUser(servicesInterface);
		this.unuseServicesInternal(servicesUser, servicesInterface);
	}

	private Class<?> tryToGetComponentTypeForServicesUser(final Class<?> servicesInterface) {
		// Same normalization as the events-consumer resolver in RawComponentsImpl,
		// for the services-user side of useServices/unuseServices.
		return RawPortSupport.resolveManualPortOwner(this.useMetamodelWriteServices, this.useValidationServices,
				CallerFinder.getCaller(), "using services " + servicesInterface, "useServices");
	}

	private void unuseServicesInternal(final Class<?> servicesUser, final Class<?> servicesInterface) {
		this.useServicesManagerServices.deleteUsedServices(servicesUser, servicesInterface);
		RawPortSupport.untrackRawPort(this.rawUsers, servicesUser, servicesInterface);
		this.updateMetamodelWithUnusedServices(servicesUser, servicesInterface);
	}

	// ---- parameters ----

	<PARAMETERS> void offerParameters(final Class<?> offerorComponentType, final Class<PARAMETERS> parametersInterface) {
		RuntimeCheck.notNull(offerorComponentType, "offerorComponentType");
		if (RawPortSupport.isImplementation(this.useValidationServices, offerorComponentType)) {
			this.offerParameters(parametersInterface);
			return;
		}
		this.offerParametersInternal(offerorComponentType, parametersInterface);
	}

	<PARAMETERS> void offerParameters(final Class<PARAMETERS> parametersInterface) {
		Class<?> offerorComponentType = this.tryToGetComponentTypeForOfferedParameters(parametersInterface);
		this.offerParametersInternal(offerorComponentType, parametersInterface);
	}

	private <PARAMETERS> void offerParametersInternal(final Class<?> offerorComponentType,
			final Class<PARAMETERS> parametersInterface) {
		RawPortSupport.requireRegisteredComponentType(this.useRegistryReadServices, offerorComponentType,
				"offer parameters " + parametersInterface);
		this.useValidationServices.checkParameters(parametersInterface);
		// Ownership is checked, and the metamodel updated, BEFORE any of the RR-manager /
		// raw-port-tracking / activation side effects below - see the identical reasoning in
		// provideServicesInternal above.
		Class<?> existingOfferor = this.useMetamodelPortReadServices
				.getComponentTypeForOfferedParameters(parametersInterface);
		boolean offeredByAnotherComponent = existingOfferor != null && !existingOfferor.equals(offerorComponentType)
				&& this.useParametersManagerServices.isParametersOffered(parametersInterface);
		this.updateMetamodelWithOfferedParameters(offerorComponentType, parametersInterface);
		if (offeredByAnotherComponent) {
			// first offeror kept; this second declaration must leave no trace
			return;
		}
		this.useParametersManagerServices.createOfferedParameters(offerorComponentType, parametersInterface);
		RawPortSupport.trackRawPort(this.rawOfferors, offerorComponentType, parametersInterface);
		this.useParametersManagerServices.activateParameters(parametersInterface);
		PARAMETERS parametersOfferor = this.useParametersManagerServices.getParametersOfferor(parametersInterface);
		this.useNamingServices.bindParameters(parametersInterface, parametersOfferor);

	}

	private <PARAMETERS> void updateMetamodelWithOfferedParameters(final Class<?> offerorComponentType,
			final Class<PARAMETERS> parametersInterface) {
		this.updateMetamodelOf(offerorComponentType,
				() -> this.useMetamodelPortWriteServices.addOfferedParameters(offerorComponentType, parametersInterface));
	}

	<PARAMETERS> void unofferParameters(final Class<?> offerorComponentType,
			final Class<PARAMETERS> parametersInterface) {
		RuntimeCheck.notNull(offerorComponentType, "offerorComponentType");
		if (RawPortSupport.isImplementation(this.useValidationServices, offerorComponentType)) {
			this.unofferParameters(parametersInterface);
			return;
		}
		this.unofferParametersInternal(offerorComponentType, parametersInterface);
	}

	<PARAMETERS> void unofferParameters(final Class<PARAMETERS> parametersInterface) {
		Class<?> providerComponentType = this.tryToGetComponentTypeForOfferedParameters(parametersInterface);
		this.unofferParametersInternal(providerComponentType, parametersInterface);
	}

	private Class<?> tryToGetComponentTypeForOfferedParameters(final Class<?> parametersInterface) {
		log.fine("Trying to guess component type for offered parameters");
		Class<?> offerorComponentType = this.useMetamodelPortReadServices
				.getComponentTypeForOfferedParameters(parametersInterface);
		if (offerorComponentType == null) {
			// assume the component is not managed and the method is called from the
			// offeror
			offerorComponentType = CallerFinder.getCaller();
			// try to get type from impl
			Class<?> componentType = this.useMetamodelWriteServices.getType(offerorComponentType);
			if (componentType != null) {
				offerorComponentType = componentType;
			} else {
				String message = "Could not retrieve component type offering parameters " + parametersInterface
						+ ", this may cause problems with component view. Maybe you should call offerParameters method with component type as parameter.";
				this.useValidationServices.handleStructuralVerification(message);
			}
		}
		return offerorComponentType;
	}

	private <PARAMETERS> void unofferParametersInternal(final Class<?> offerorComponentType,
			final Class<PARAMETERS> parametersInterface) {
		// The owner is checked BEFORE anything is torn down: a refused withdrawal must
		// leave the owner's parameters fully working.
		Class<?> existingOfferor = this.useMetamodelPortReadServices
				.getComponentTypeForOfferedParameters(parametersInterface);
		if (existingOfferor != null && !existingOfferor.equals(offerorComponentType)) {
			throw new IllegalArgumentException("Component type " + offerorComponentType
					+ " does not match parameters offering component type " + existingOfferor);
		}
		boolean offered = this.useParametersManagerServices.isParametersOffered(parametersInterface);
		if (!offered && existingOfferor == null) {
			RawPortSupport.untrackRawPort(this.rawOfferors, offerorComponentType, parametersInterface);
			this.useValidationServices.handleDynamicVerification(
					"Parameters " + parametersInterface + " not offered by component " + offerorComponentType);
			return;
		}
		if (offered) {
			this.useParametersManagerServices.passivateParameters(parametersInterface);
			this.useParametersManagerServices.deleteOfferedParameters(parametersInterface);
			this.useParametersLifecycleServices.unpublishOfferedParameters(offerorComponentType);
		}
		this.updateMetamodelWithUnofferedParameters(offerorComponentType, parametersInterface);

		RawPortSupport.untrackRawPort(this.rawOfferors, offerorComponentType, parametersInterface);
	}

	private <PARAMETERS> void updateMetamodelWithUnofferedParameters(final Class<?> offerorComponentType,
			final Class<PARAMETERS> parametersInterface) {
		this.updateMetamodelOf(offerorComponentType,
				() -> this.useMetamodelPortWriteServices.removeOfferedParameters(offerorComponentType, parametersInterface));
	}

	<PARAMETERS> PARAMETERS accessParameters(final Class<PARAMETERS> parametersInterface) {
		return this.accessParametersInternal(parametersInterface);
	}

	<PARAMETERS> PARAMETERS accessParameters(final Class<?> offerorComponentType,
			final Class<PARAMETERS> parametersInterface) {
		RuntimeCheck.notNull(offerorComponentType, "offerorComponentType");
		if (RawPortSupport.isImplementation(this.useValidationServices, offerorComponentType)) {
			return this.accessParameters(parametersInterface);
		}
		return this.accessParametersInternal(parametersInterface);
	}

	private <PARAMETERS> PARAMETERS accessParametersInternal(final Class<PARAMETERS> parametersInterface) {
		Class<?> parametersAccessor = this.tryToGetComponentTypeForParametersAccessor(parametersInterface);
		RawPortSupport.requireRegisteredComponentType(this.useRegistryReadServices, parametersAccessor,
				"access parameters " + parametersInterface);
		PARAMETERS accessedParameters = this.useParametersManagerServices.createAccessedParameters(parametersAccessor,
				parametersInterface);
		RawPortSupport.trackRawPort(this.rawAccessors, parametersAccessor, parametersInterface);
		this.updateMetamodelWithAccessedParameters(parametersAccessor, parametersInterface);

		return accessedParameters;
	}

	/**
	 * Records the accessed parameters in the accessor's metamodel - see
	 * {@link #updateMetamodelWithUsedServices(Class, Class)}.
	 */
	private <PARAMETERS> void updateMetamodelWithAccessedParameters(final Class<?> parametersAccessor,
			final Class<PARAMETERS> parametersInterface) {
		this.updateMetamodelOf(parametersAccessor,
				() -> this.useMetamodelPortWriteServices.addAccessedParameters(parametersAccessor, parametersInterface));
	}

	private <PARAMETERS> void updateMetamodelWithUnaccessedParameters(final Class<?> parametersAccessor,
			final Class<PARAMETERS> parametersInterface) {
		this.updateMetamodelOf(parametersAccessor,
				() -> this.useMetamodelPortWriteServices.removeAccessedParameters(parametersAccessor, parametersInterface));
	}

	void unaccessParameters(final Class<?> parametersInterface) {
		Class<?> parametersAccessor = this.tryToGetComponentTypeForParametersAccessor(parametersInterface);
		this.unaccessParametersInternal(parametersAccessor, parametersInterface);
	}

	private Class<?> tryToGetComponentTypeForParametersAccessor(final Class<?> parametersInterface) {
		// Same normalization as the events-consumer resolver in RawComponentsImpl,
		// for the parameters-accessor side of accessParameters/unaccessParameters.
		return RawPortSupport.resolveManualPortOwner(this.useMetamodelWriteServices, this.useValidationServices,
				CallerFinder.getCaller(), "accessing parameters " + parametersInterface, "accessParameters");
	}

	private void unaccessParametersInternal(final Class<?> parametersAccessor, final Class<?> parametersInterface) {
		this.useParametersManagerServices.deleteAccessedParameters(parametersAccessor, parametersInterface);
		RawPortSupport.untrackRawPort(this.rawAccessors, parametersAccessor, parametersInterface);
		this.updateMetamodelWithUnaccessedParameters(parametersAccessor, parametersInterface);
	}

	// ---- bulk recovery, used by RawComponentsImpl.onComponentFailed/unregisterComponent/stop() ----

	void unprovideAllServices() {
		Map<Class<?>, Set<Class<?>>> copy = RawPortSupport.copyTrackedPorts(this.rawProviders);
		for (Map.Entry<Class<?>, Set<Class<?>>> entry : copy.entrySet()) {
			Class<?> providerComponentType = entry.getKey();
			this.unprovideAllServicesFor(providerComponentType);
		}
	}

	void unprovideAllServicesFor(final Class<?> providerComponentType) {
		Set<Class<?>> trackedInterfaces = this.rawProviders.get(providerComponentType);
		if (trackedInterfaces == null) {
			return;
		}
		for (Class<?> trackedInterface : new LinkedHashSet<>(trackedInterfaces)) {
			this.unprovideServicesInternal(providerComponentType, trackedInterface);
		}
	}

	void unuseAllServices() {
		Map<Class<?>, Set<Class<?>>> copy = RawPortSupport.copyTrackedPorts(this.rawUsers);
		for (Map.Entry<Class<?>, Set<Class<?>>> entry : copy.entrySet()) {
			Class<?> servicesUser = entry.getKey();
			this.unuseAllServicesFor(servicesUser);
		}
	}

	void unuseAllServicesFor(final Class<?> servicesUser) {
		Set<Class<?>> trackedInterfaces = this.rawUsers.get(servicesUser);
		if (trackedInterfaces == null) {
			return;
		}
		for (Class<?> trackedInterface : new LinkedHashSet<>(trackedInterfaces)) {
			if (this.useServicesManagerServices.isServicesUsed(servicesUser, trackedInterface)) {
				this.unuseServicesInternal(servicesUser, trackedInterface);
			} else {
				RawPortSupport.untrackRawPort(this.rawUsers, servicesUser, trackedInterface);
			}
		}
	}

	void unofferAllParameters() {
		Map<Class<?>, Set<Class<?>>> copy = RawPortSupport.copyTrackedPorts(this.rawOfferors);
		for (Map.Entry<Class<?>, Set<Class<?>>> entry : copy.entrySet()) {
			Class<?> offerorComponentType = entry.getKey();
			this.unofferAllParametersFor(offerorComponentType);
		}
	}

	void unofferAllParametersFor(final Class<?> offerorComponentType) {
		Set<Class<?>> trackedInterfaces = this.rawOfferors.get(offerorComponentType);
		if (trackedInterfaces == null) {
			return;
		}
		for (Class<?> trackedInterface : new LinkedHashSet<>(trackedInterfaces)) {
			this.unofferParametersInternal(offerorComponentType, trackedInterface);
		}
	}

	void unaccessAllParameters() {
		Map<Class<?>, Set<Class<?>>> copy = RawPortSupport.copyTrackedPorts(this.rawAccessors);
		for (Map.Entry<Class<?>, Set<Class<?>>> entry : copy.entrySet()) {
			Class<?> parametersAccessor = entry.getKey();
			this.unaccessAllParametersFor(parametersAccessor);
		}
	}

	void unaccessAllParametersFor(final Class<?> parametersAccessor) {
		Set<Class<?>> trackedInterfaces = this.rawAccessors.get(parametersAccessor);
		if (trackedInterfaces == null) {
			return;
		}
		for (Class<?> trackedInterface : new LinkedHashSet<>(trackedInterfaces)) {
			if (this.useParametersManagerServices.isParametersAccessed(parametersAccessor, trackedInterface)) {
				this.unaccessParametersInternal(parametersAccessor, trackedInterface);
			} else {
				RawPortSupport.untrackRawPort(this.rawAccessors, parametersAccessor, trackedInterface);
			}
		}
	}

}
