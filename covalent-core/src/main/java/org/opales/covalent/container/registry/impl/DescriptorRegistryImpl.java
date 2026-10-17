/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.registry.impl;

import java.util.List;
import java.util.ArrayList;
import org.opales.covalent.container.util.ShutdownResidues;
import java.util.HashMap;
import java.util.Map;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.metamodel.MetamodelReadServices;
import org.opales.covalent.container.metamodel.MetamodelPortReadServices;
import org.opales.covalent.container.registry.DescriptorRegistry;
import org.opales.covalent.container.registry.DescriptorRegistryWriteServices;
import org.opales.covalent.container.util.RuntimeCheck;
import org.opales.covalent.container.validation.ValidationServices;

/**
 * Registry-internal component holding the container's live
 * {@link ComponentDescriptorImpl} bookkeeping: it delegates descriptor
 * creation and completion to a {@link DescriptorFactory}, and keeps the
 * map of currently-registered component types to their descriptor. Not
 * part of any public API - other container internals see it through
 * {@link DescriptorRegistry} (read-only lookups) and
 * {@link DescriptorRegistryWriteServices} (creation/update/deletion).
 */
@Implementation
public class DescriptorRegistryImpl implements DescriptorRegistry, DescriptorRegistryWriteServices {

	@Item
	private final DescriptorFactory descriptorFactory = new DescriptorFactory();

	/** {@code <Component type, Component descriptor>} */
	private final Map<Class<?>, ComponentDescriptorImpl> descriptors = new HashMap<>();

	/**
	 * Creates a new, empty registry.
	 */
	public DescriptorRegistryImpl() {
	}

	/**
	 * Injects the validation services used by this registry's
	 * {@link DescriptorFactory} when creating and completing descriptors.
	 *
	 * @param validationServices the validation services
	 */
	public void setValidationServices(final ValidationServices validationServices) {
		this.descriptorFactory.setValidationServices(validationServices);
	}

	/**
	 * Injects the metamodel read services used by this registry's
	 * {@link DescriptorFactory} when creating and completing descriptors.
	 *
	 * @param metamodelReadServices the metamodel read services
	 */
	public void setMetamodelReadServices(final MetamodelReadServices metamodelReadServices) {
		this.descriptorFactory.setMetamodelReadServices(metamodelReadServices);
	}

	/**
	 * Injects the metamodel port read services used by this registry's
	 * {@link DescriptorFactory} when creating and completing descriptors.
	 *
	 * @param MetamodelPortReadServices the metamodel port read services
	 */
	public void setMetamodelPortReadServices(final MetamodelPortReadServices MetamodelPortReadServices) {
		this.descriptorFactory.setMetamodelPortReadServices(MetamodelPortReadServices);
	}

	@Override
	public ComponentDescriptorImpl readComponentDescriptor(final Class<?> componentType) {
		return this.descriptors.get(componentType);
	}

	@Override
	public boolean containsComponentDescriptor(final Class<?> componentType) {
		return this.descriptors.containsKey(componentType);
	}

	@Override
	public Object createComponentDescriptor(final Class<?> componentType) {
		ComponentDescriptorImpl componentDescriptor = this.descriptorFactory.createDescriptor(componentType,
				this.resolveParentDescriptor(componentType));
		Object componentInstance = this.descriptorFactory.instantiateOrAttachInstances(componentDescriptor);
		this.registerDescriptor(componentType, componentDescriptor);
		return componentInstance;
	}

	@Override
	public Object createComponentDescriptor(final Class<?> componentType, final Object instance) {
		ComponentDescriptorImpl componentDescriptor = this.descriptorFactory.createDescriptor(componentType,
				this.resolveParentDescriptor(componentType));
		Object componentInstance = this.descriptorFactory.instantiateOrAttachInstances(componentDescriptor, instance);
		this.registerDescriptor(componentType, componentDescriptor);
		return componentInstance;
	}

	@Override
	public void updateComponentDescriptor(final Class<?> componentType) {
		ComponentDescriptorImpl componentDescriptor = this.descriptors.get(componentType);
		RuntimeCheck.notNull(componentDescriptor, "componentDescriptor");
		this.descriptorFactory.completeDescriptor(componentDescriptor);
	}

	@Override
	public void deleteComponentDescriptor(final Class<?> componentType) {
		ComponentDescriptorImpl componentDescriptor = this.descriptors.remove(componentType);
		if (componentDescriptor == null) {
			return;
		}
		ComponentDescriptorImpl parent = componentDescriptor.getParent();
		if (parent != null) {
			parent.removeComponent(componentDescriptor);
		}
	}

	private ComponentDescriptorImpl resolveParentDescriptor(final Class<?> componentType) {
		ComponentDescriptorImpl parent = null;
		Class<?> parentComponentType = this.descriptorFactory.readParentComponentType(componentType);
		if (parentComponentType != null) {
			parent = this.descriptors.get(parentComponentType);
			RuntimeCheck.notNull(parent, "parentDescriptor");
		}
		return parent;
	}

	private void registerDescriptor(final Class<?> componentType, final ComponentDescriptorImpl componentDescriptor) {
		this.descriptorFactory.completeDescriptor(componentDescriptor);
		this.descriptors.put(componentType, componentDescriptor);
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
		for (Class<?> componentType : this.descriptors.keySet()) {
			ShutdownResidues.addIfNotInternal(residues, "DescriptorRegistry", "descriptor still registered",
					componentType);
		}
		return residues;
	}

}
