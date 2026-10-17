/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.raw.impl;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Logger;

import org.opales.covalent.container.component.ComponentAnnotations;
import org.opales.covalent.container.metamodel.MetamodelWriteServices;
import org.opales.covalent.container.registry.DescriptorRegistryWriteServices;
import org.opales.covalent.container.registry.PortRegistryServices;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.validation.ValidationServices;

/**
 * <p>
 * Small, stateless helpers shared by RawComponentsImpl's port-family
 * collaborators ({@link RawServicesAndParametersPort} and {@link RawEventsPort}).
 * </p>
 * <p>
 * These are generic, family-agnostic primitives, not a responsibility of
 * their own: they carry no state and depend only on their parameters, so
 * factoring them out here only avoids duplicating them once more than one
 * collaborator needs them.
 * </p>
 */
final class RawPortSupport {

	private RawPortSupport() {
	}

	static void trackRawPort(final Map<Class<?>, Set<Class<?>>> trackedPorts, final Class<?> componentType,
			final Class<?> portInterface) {
		if (componentType == null) {
			return;
		}
		trackedPorts.computeIfAbsent(componentType, key -> new CopyOnWriteArraySet<>()).add(portInterface);
	}

	static void untrackRawPort(final Map<Class<?>, Set<Class<?>>> trackedPorts, final Class<?> componentType,
			final Class<?> portInterface) {
		Set<Class<?>> portInterfaces = componentType == null ? null : trackedPorts.get(componentType);
		if (portInterfaces == null) {
			return;
		}
		portInterfaces.remove(portInterface);
		if (portInterfaces.isEmpty()) {
			trackedPorts.remove(componentType);
		}
	}

	static Map<Class<?>, Set<Class<?>>> copyTrackedPorts(final Map<Class<?>, Set<Class<?>>> trackedPorts) {
		Map<Class<?>, Set<Class<?>>> copy = new HashMap<>();
		for (Map.Entry<Class<?>, Set<Class<?>>> entry : trackedPorts.entrySet()) {
			copy.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
		}
		return copy;
	}

	/**
	 * Applies a metamodel update to the given component type, then refreshes the
	 * component's descriptor if it has one. A {@code null} or unknown component
	 * type is ignored: the update is not applied.
	 *
	 * @param log                              the logger of the calling collaborator
	 * @param metamodelWriteServices           used to tell whether the component type is known
	 * @param portRegistryServices             used to tell whether the component has a descriptor
	 * @param descriptorRegistryWriteServices  used to refresh the descriptor
	 * @param componentType                    the component type to update, possibly {@code null}
	 * @param metamodelUpdate                  the metamodel write to apply
	 */
	static void updateMetamodelOf(final Logger log, final MetamodelWriteServices metamodelWriteServices,
			final PortRegistryServices portRegistryServices,
			final DescriptorRegistryWriteServices descriptorRegistryWriteServices, final Class<?> componentType,
			final Runnable metamodelUpdate) {
		if (componentType == null) {
			return;
		}
		if (metamodelWriteServices.readMetamodel(componentType) == null) {
			log.fine("ComponentType is not known: " + componentType.getSimpleName());
			return;
		}
		metamodelUpdate.run();
		if (portRegistryServices.getComponentDescriptor(componentType) != null) {
			descriptorRegistryWriteServices.updateComponentDescriptor(componentType);
		}
	}

	static boolean isImplementation(final ValidationServices validationServices, final Class<?> componentType) {
		boolean result = false;
		if (ComponentAnnotations.isImplementation(componentType)) {
			// try getting the real component type
			String message = "componentType is indeed a component implementation: " + componentType;
			validationServices.handleStructuralVerification(message);
			result = true;
		}
		return result;
	}

	/**
	 * <p>
	 * Resolves the registered component type for a "manual" consumer-side raw
	 * port (a subscribed events consumer, a services user, or a parameters
	 * accessor) from its caller class.
	 * </p>
	 * <p>
	 * Unlike producer-side ports (produceEvents, provideServices,
	 * offerParameters), there is no metamodel lookup from the port interface to a
	 * single owner here: several different components can legitimately consume
	 * the same events/services/parameters interface. The caller's own class is
	 * instead resolved back to its registered component type, exactly like the
	 * fallback normalization already used on the producer side - so a manual
	 * consumer-side port is tracked under the same key that unregisterComponent
	 * and onComponentFailed use to clean it up, instead of always under the raw
	 * caller class.
	 * </p>
	 *
	 * @param metamodelWriteServices metamodel lookup used to resolve the caller's
	 *                               registered component type
	 * @param validationServices     used to report a structural-verification
	 *                               message when resolution falls back to the raw
	 *                               caller class
	 * @param caller                 the raw caller class, as returned by
	 *                               {@link org.opales.covalent.container.util.CallerFinder#getCaller()}
	 * @param action                 short description of what is being resolved,
	 *                               used only to build a clearer
	 *                               structural-verification message (for example
	 *                               "consuming events SomeEvents")
	 * @param methodName             the public method name to suggest calling
	 *                               with an explicit component type, when
	 *                               resolution falls back to the raw caller class
	 * @return the caller's registered component type, or the raw caller class
	 *         itself when no component type is registered for it yet
	 */
	static Class<?> resolveManualPortOwner(final MetamodelWriteServices metamodelWriteServices,
			final ValidationServices validationServices, final Class<?> caller, final String action,
			final String methodName) {
		Class<?> componentType = metamodelWriteServices.getType(caller);
		if (componentType != null) {
			return componentType;
		}
		String message = "Could not retrieve component type " + action
				+ ", this may cause problems with component view. Maybe you should call " + methodName
				+ " method with component type as parameter.";
		validationServices.handleStructuralVerification(message);
		return caller;
	}

	/**
	 * <p>
	 * Fails immediately when a producer-side raw port (provideServices,
	 * offerParameters) is about to be created for a {@code componentType} that
	 * has no metamodel entry at all - that is, a class that was never passed to
	 * {@code registerComponent} (raw) nor deployed as a managed component.
	 * </p>
	 * <p>
	 * Without this check, the port is still created successfully, but the
	 * metamodel's reverse index (which lets {@code useServices}/
	 * {@code accessParameters} find it again by interface) is never populated,
	 * since it is only populated once the componentType is known. The port then
	 * becomes a "ghost": unreachable through the normal consumer-side lookup,
	 * failing lazily and far from its actual cause the first time a client tries
	 * to invoke it (deep inside {@code ServerRRInvocationHandler.lookupInstance()}).
	 * Failing here instead, at creation time, turns that into an immediate,
	 * actionable error.
	 * </p>
	 * <p>
	 * This is unconditional (independent of the structural verification level):
	 * unlike the "could not retrieve component type" fallback message elsewhere
	 * in this package, which covers the legitimate case of a raw component
	 * resolving its own already-registered type through its caller class, there
	 * is no legitimate use case for creating one of these ports for a
	 * componentType that is not registered at all.
	 * </p>
	 * <p>
	 * This does not apply to {@code produceEvents}: event producers are looked
	 * up by consumers purely through their events interface
	 * ({@code EventsManagerImpl.producerSideSubscriberProxies}), never through
	 * the metamodel's reverse index, so an unregistered componentType cannot
	 * create the same "ghost port" there.
	 * </p>
	 *
	 * @param metamodelWriteServices metamodel lookup used to check whether
	 *                               {@code componentType} is registered
	 * @param componentType          the resolved producer-side componentType
	 *                               about to be used to create the port
	 * @param action                 short description of what is being created,
	 *                               used only to build a clearer exception
	 *                               message (for example "provide services
	 *                               SomeServices")
	 * @throws IllegalArgumentException if {@code componentType} has no
	 *                                  metamodel entry
	 */
	static void requireKnownComponentType(final MetamodelWriteServices metamodelWriteServices,
			final Class<?> componentType, final String action) {
		if (metamodelWriteServices.readMetamodel(componentType) == null) {
			throw new IllegalArgumentException("Cannot " + action + ": componentType " + componentType
					+ " has no metamodel entry. It must be registered first, either as a raw component (via "
					+ "registerComponent) or as a managed component (via deployComponents/start), before this "
					+ "port can be created for it.");
		}
	}

	/**
	 * <p>
	 * Fails immediately when a raw port - producer or consumer side - is about
	 * to be created for a {@code componentType} that has not been registered
	 * as an actual raw component instance yet, that is, a class for which
	 * {@code RawComponentsServices.registerComponent} (in any of its
	 * overloads) has not reached at least {@code ComponentState.instantiated}.
	 * </p>
	 * <p>
	 * This is a stricter precondition than {@link #requireKnownComponentType}:
	 * a componentType gets a metamodel entry as soon as it is deployed (the
	 * very first step of {@code registerComponent}), but only becomes
	 * registered in the instance registry - and therefore visible through
	 * {@link RegistryReadServices#isRegistered}, {@code getInstances()}, and
	 * the supervision tooling that relies on it - once registration has
	 * actually reached the {@code instantiated} state. Without this check, a
	 * raw component could freely open services/parameters/events ports while
	 * remaining permanently invisible to anything that queries the component
	 * registry, an identity gap with no legitimate use case.
	 * </p>
	 *
	 * @param registryReadServices registry lookup used to check whether
	 *                              {@code componentType} is registered
	 * @param componentType         the resolved componentType about to be used
	 *                              to create the port
	 * @param action                short description of what is being created,
	 *                              used only to build a clearer exception
	 *                              message (for example "produce events
	 *                              SomeEvents")
	 * @throws IllegalArgumentException if {@code componentType} is one of
	 *                                  Covalent's own (internal) components
	 * @throws IllegalStateException if {@code componentType} is not registered
	 */
	static void requireRegisteredComponentType(final RegistryReadServices registryReadServices,
			final Class<?> componentType, final String action) {
		// A raw port opened on behalf of one of Covalent's own components would
		// outlive every stop(), like them, and let a raw component pose as one.
		if (ComponentAnnotations.isInternalComponent(componentType)) {
			throw new IllegalArgumentException("Cannot " + action + ": componentType " + componentType
					+ " is one of Covalent's own components; a raw port can only be opened on behalf of a raw "
					+ "component.");
		}
		if (!registryReadServices.isRegistered(componentType)) {
			throw new IllegalStateException("Cannot " + action + ": componentType " + componentType
					+ " is not registered. Call RawComponentsServices.registerComponent(...) for this raw "
					+ "component before creating this port.");
		}
	}

}
