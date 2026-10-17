/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.registry.impl;

import java.util.List;
import java.util.ArrayList;
import org.opales.covalent.container.util.ShutdownResidues;
import java.util.LinkedHashMap;
import java.util.Map;

import org.opales.covalent.container.component.ComponentAnnotations;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.registry.ComponentRegistry;
import org.opales.covalent.container.registry.RegistryEvents;
import org.opales.covalent.container.registry.RegistryWriteServices;
import org.opales.covalent.container.util.RuntimeCheck;

/**
 * Default {@link ComponentRegistry} implementation: keeps the live map from
 * component type to instance and fires registration/unregistration events
 * for it. This is the container's single source of truth for "what is
 * currently instantiated" - the registry itself performs no lifecycle
 * logic, it only records instances handed to it and enforces that a
 * component type is registered at most once.
 */
@Implementation
public class ComponentRegistryImpl implements ComponentRegistry, RegistryWriteServices {

	/** Fires the registry's component-registered/unregistered events. */
	@ProducedEvents
	private RegistryEvents produceRegistryEvents;

	/** {@code <ComponentType, Instance>} */
	private final Map<Class<?>, Object> componentsRegistry = new LinkedHashMap<>();

	/**
	 * Creates a new, empty registry.
	 */
	public ComponentRegistryImpl() {
	}

	@Override
	public void registerComponent(final Class<?> componentType, final Object componentInstance) {
		RuntimeCheck.notNull(componentType, "componentType");
		RuntimeCheck.notNull(componentInstance, "componentInstance");
		if (this.componentsRegistry.containsKey(componentType)) {
			throw new IllegalStateException("A component instance already exists for type " + componentType);
		}
		this.componentsRegistry.put(componentType, componentInstance);

		this.produceRegistryEvents.onComponentRegistered(componentType, componentInstance);

	}

	@Override
	public <T> T unregisterComponent(final Class<T> componentType) {
		RuntimeCheck.notNull(componentType, "componentType");
		if (!this.componentsRegistry.containsKey(componentType)) {
			throw new IllegalStateException("No component instance found for type " + componentType);
		}

		@SuppressWarnings("unchecked")
		final T componentInstance = (T) this.componentsRegistry.get(componentType);
		this.componentsRegistry.remove(componentType);

		this.produceRegistryEvents.onComponentUnregistered(componentType, componentInstance);

		return componentInstance;
	}

	@Override
	public <T> boolean isRegistered(final Class<T> componentType) {
		return this.componentsRegistry.containsKey(componentType);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <CT, CI extends CT> CI getComponentInstance(final Class<CT> componentType) {
		final CI result = (CI) this.componentsRegistry.get(componentType);
		if (result == null) {

			// maybe this is a component impl ?
			if (ComponentAnnotations.isImplementation(componentType)) {
				throw new IllegalStateException("Trying to get instance of a component implementation: "
						+ componentType.getSimpleName() + ". Call the method with a component type.");
			} else {

				throw new IllegalArgumentException(
						"Component type: " + componentType.getSimpleName() + " is not registered");
			}

		}
		return result;
	}

	@Override
	public Map<Class<?>, Object> getInstances() {
		return new LinkedHashMap<>(this.componentsRegistry);
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
		for (Class<?> componentType : this.componentsRegistry.keySet()) {
			ShutdownResidues.addIfNotInternal(residues, "ComponentRegistry", "component still registered",
					componentType);
		}
		return residues;
	}


}
