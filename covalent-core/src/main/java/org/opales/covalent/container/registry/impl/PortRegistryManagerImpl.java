/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.registry.impl;

import java.util.ArrayList;
import java.util.List;

import org.opales.covalent.container.component.annotations.ComposedComponent;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.metamodel.MetamodelReadServices;
import org.opales.covalent.container.metamodel.MetamodelPortReadServices;
import org.opales.covalent.container.registry.PortRegistry;
import org.opales.covalent.container.registry.ComponentDescriptor;
import org.opales.covalent.container.registry.ItemAndField;
import org.opales.covalent.container.registry.PortRegistryServices;
import org.opales.covalent.container.registry.ComponentInstantiationServices;
import org.opales.covalent.container.validation.ValidationServices;

/**
 * Default implementation of the port registry: composes a
 * {@link ComponentRegistryImpl} (which components/instances are currently
 * registered) with a {@link DescriptorRegistryImpl} (their
 * {@link ComponentDescriptor} bookkeeping), and resolves port lookups
 * ({@code SERVICES}, {@code EVENTS}, {@code PARAMETERS}, {@code SUBSCRIBE})
 * by first mapping the interface to its owning component type through the
 * metamodel, then delegating to that component's descriptor.
 */
@Implementation
public class PortRegistryManagerImpl implements PortRegistry, PortRegistryServices, ComponentInstantiationServices {

	@ComposedComponent
	private final ComponentRegistryImpl componentRegistryImpl = new ComponentRegistryImpl();

	@ComposedComponent
	private final DescriptorRegistryImpl descriptorRegistryImpl = new DescriptorRegistryImpl();

	@UsedServices
	private MetamodelPortReadServices useMetamodelPortReadServices;

	/**
	 * Creates a new port registry manager.
	 */
	public PortRegistryManagerImpl() {
	}

	/**
	 * Returns the composed component registry.
	 *
	 * @return the component registry
	 */
	public ComponentRegistryImpl getComponentRegistryImpl() {
		return this.componentRegistryImpl;
	}

	/**
	 * Returns the composed descriptor registry.
	 *
	 * @return the descriptor registry
	 */
	public DescriptorRegistryImpl getDescriptorRegistryImpl() {
		return this.descriptorRegistryImpl;
	}

	@Override
	public void instantiateComponent(final Class<?> componentType) {
		Object componentInstance = this.descriptorRegistryImpl.createComponentDescriptor(componentType);
		this.registerComponentInstance(componentType, componentInstance);
	}

	@Override
	public void componentify(final Class<?> componentType, final Object instance) {
		Object componentInstance = this.descriptorRegistryImpl.createComponentDescriptor(componentType, instance);
		this.registerComponentInstance(componentType, componentInstance);
	}

	@Override
	public void removeComponent(final Class<?> componentType) {
		if (this.componentRegistryImpl.isRegistered(componentType)) {
			this.componentRegistryImpl.unregisterComponent(componentType);
		}
		this.descriptorRegistryImpl.deleteComponentDescriptor(componentType);
	}

	/**
	 * Sets the internal service used to report validation issues,
	 * propagated to the descriptor registry.
	 *
	 * @param validationServices the validation service
	 */
	public void setValidationServices(final ValidationServices validationServices) {
		this.descriptorRegistryImpl.setValidationServices(validationServices);
	}

	/**
	 * Sets the internal service used to read component metamodels,
	 * propagated to the descriptor registry.
	 *
	 * @param metamodelReadServices the metamodel read service
	 */
	public void setMetamodelReadServices(final MetamodelReadServices metamodelReadServices) {
		this.descriptorRegistryImpl.setMetamodelReadServices(metamodelReadServices);
	}

	/**
	 * Sets the internal service used to resolve a port interface to its
	 * owning component type, propagated to the descriptor registry.
	 *
	 * @param metamodelPortReadServices the metamodel port read service
	 */
	public void setMetamodelPortReadServices(final MetamodelPortReadServices metamodelPortReadServices) {
		this.useMetamodelPortReadServices = metamodelPortReadServices;
		this.descriptorRegistryImpl.setMetamodelPortReadServices(metamodelPortReadServices);
	}

	@Override
	public <SERVICES> SERVICES getServicesProvider(final Class<SERVICES> servicesInterface) {
		final ComponentDescriptor descriptor = this.descriptorOf(
				this.useMetamodelPortReadServices.getComponentTypeForProvidedServices(servicesInterface));
		return descriptor == null ? null : descriptor.getServicesProvider(servicesInterface);
	}

	@Override
	public <SERVICES> List<ItemAndField> getUsedServicesItemAndField(final Class<?> componentType,
			final Class<SERVICES> servicesInterface) {
		final ComponentDescriptor descriptor = this.descriptorOf(componentType);
		return descriptor == null ? new ArrayList<>() : descriptor.getUsedServicesItemAndField(servicesInterface);
	}

	@Override
	public <EVENT> EVENT getEventsConsumer(final Class<?> componentType, final Class<EVENT> eventsInterface) {
		final ComponentDescriptor descriptor = this.descriptorOf(componentType);
		return descriptor == null ? null : descriptor.getEventsConsumer(eventsInterface);
	}

	@Override
	public <EVENTS> List<ItemAndField> getProducedEventsItemAndField(final Class<?> componentType,
			final Class<EVENTS> eventsInterface) {
		final ComponentDescriptor descriptor = this.descriptorOf(componentType);
		return descriptor == null ? new ArrayList<>() : descriptor.getProducedEventsItemAndField(eventsInterface);
	}

	@Override
	public <PARAMETERS> PARAMETERS getParametersOfferor(final Class<PARAMETERS> parametersInterface) {
		final ComponentDescriptor descriptor = this.descriptorOf(
				this.useMetamodelPortReadServices.getComponentTypeForOfferedParameters(parametersInterface));
		return descriptor == null ? null : descriptor.getParametersOfferor(parametersInterface);
	}

	@Override
	public <PARAMETERS> List<ItemAndField> getAccessedParametersItemAndField(final Class<?> componentType,
			final Class<PARAMETERS> parametersInterface) {
		final ComponentDescriptor descriptor = this.descriptorOf(componentType);
		return descriptor == null ? new ArrayList<>() : descriptor.getAccessedParametersItemAndField(parametersInterface);
	}

	@Override
	public <EVENT, SUBSCRIBE> SUBSCRIBE getSubscribeDelegate(final Class<EVENT> eventsInterface) {
		final Class<SUBSCRIBE> subscribeInterface = this.useMetamodelPortReadServices.getSubscribeInterface(eventsInterface);
		final Class<?> componentType = this.useMetamodelPortReadServices.getComponentTypeForProducedEvents(eventsInterface);
		SUBSCRIBE subscribeDelegate = null;
		final ComponentDescriptor descriptor = this.descriptorOf(componentType);
		if (descriptor != null) {
			subscribeDelegate = descriptor.getSubscribeDelegate(subscribeInterface);
		}
		return subscribeDelegate;
	}

	@Override
	public <SUBSCRIBE> List<ItemAndField> getUsedSubscribeItemAndField(final Class<?> componentType,
			final Class<SUBSCRIBE> subscribeInterface) {
		final ComponentDescriptor descriptor = this.descriptorOf(componentType);
		return descriptor == null ? new ArrayList<>() : descriptor.getUsedSubscribeItemAndField(subscribeInterface);
	}

	/** The descriptor of the given component type, or {@code null} when the type is {@code null} or unknown. */
	private ComponentDescriptor descriptorOf(final Class<?> componentType) {
		return componentType == null ? null : this.descriptorRegistryImpl.readComponentDescriptor(componentType);
	}

	@Override
	public ComponentDescriptor getComponentDescriptor(final Class<?> componentType) {
		return this.descriptorRegistryImpl.readComponentDescriptor(componentType);
	}

	/**
	 * Registers the given component instance in the component registry,
	 * unless it is already registered (internal components may be
	 * pre-registered during bootstrap).
	 *
	 * @param componentType     the component type
	 * @param componentInstance the component's instance to register
	 */
	private void registerComponentInstance(final Class<?> componentType, final Object componentInstance) {
		if (!this.componentRegistryImpl.isRegistered(componentType)) {
			// may be already registered for internal components
			this.componentRegistryImpl.registerComponent(componentType, componentInstance);
		}
	}

}
