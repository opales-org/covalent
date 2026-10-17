/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.deployment.impl;

import org.opales.covalent.container.util.ShutdownResidues;
import java.util.ArrayList;
import java.util.List;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.deployment.Deployer;
import org.opales.covalent.container.deployment.DeploymentEvents;
import org.opales.covalent.container.deployment.DeploymentServices;
import org.opales.covalent.container.metamodel.MetamodelWriteServices;
import org.opales.covalent.container.metamodel.metamodel.ComponentMetamodel;
import org.opales.covalent.container.util.RuntimeCheck;
import org.opales.covalent.container.validation.ValidationServices;

/**
 * Default implementation of the deployment service: turns a component
 * type/implementation pair into a {@link ComponentMetamodel} (creating or
 * updating it through {@link MetamodelWriteServices}), tracks the set of
 * currently deployed component types, and fires {@link DeploymentEvents}
 * notifications on deploy/undeploy.
 */
@Implementation
public class DeployerImpl implements Deployer, DeploymentServices {

	@UsedServices
	private ValidationServices useValidationServices;

	@UsedServices
	private MetamodelWriteServices useMetamodelWriteServices;

	@ProducedEvents
	private DeploymentEvents produceDeploymentEvents;

	/** The component types currently deployed. */
	private final List<Class<?>> types = new ArrayList<>();

	/**
	 * Creates a new, empty deployer.
	 */
	public DeployerImpl() {
	}

	@Override
	public ComponentMetamodel deploy(final Class<?> componentType, final Class<?> implementation) {
		return this.deploy(componentType, implementation, true);
	}

	@Override
	public ComponentMetamodel deployRaw(final Class<?> componentType, final Class<?> implementation) {
		return this.deploy(componentType, implementation, false);
	}

	/**
	 * Deploys the given component type/implementation pair: reuses and
	 * updates an existing metamodel if one is already registered (the case
	 * for internal components during bootstrap), otherwise creates a new
	 * one - either fully checked or, when
	 * {@code checkImplementationAnnotation} is {@code false}, without
	 * requiring the {@code @Implementation} annotation. Records the type as
	 * deployed and fires {@link DeploymentEvents#onComponentDeployed}.
	 *
	 * @param componentType                 the component type; must not
	 *                                       already be deployed
	 * @param implementation                the implementation class; must
	 *                                       not be {@code null}
	 * @param checkImplementationAnnotation {@code true} to require and
	 *                                       validate the {@code @Implementation}
	 *                                       annotation, {@code false} for a
	 *                                       raw (annotation-less) metamodel
	 * @return the resulting component metamodel
	 */
	private ComponentMetamodel deploy(final Class<?> componentType, final Class<?> implementation,
			final boolean checkImplementationAnnotation) {
		RuntimeCheck.notNull(componentType, "componentType");
		if (this.types.contains(componentType)) {
			throw new IllegalStateException("Component " + componentType.getSimpleName() + " is already deployed.");
		}
		RuntimeCheck.notNull(implementation, "implementation");
		ComponentMetamodel metamodel = this.useMetamodelWriteServices.readMetamodel(componentType);
		if (metamodel != null) {
			// Internal components may already have a metamodel during bootstrap.
			this.useMetamodelWriteServices.updateMetamodel(metamodel);
		} else if (checkImplementationAnnotation) {
			metamodel = this.useMetamodelWriteServices.createMetamodel(componentType, implementation);
		} else {
			metamodel = this.useMetamodelWriteServices.createRawMetamodel(componentType, implementation);
		}
		this.types.add(componentType);

		this.produceDeploymentEvents.onComponentDeployed(componentType, implementation);
		return metamodel;
	}

	/**
	 * Sets the internal service used to create, read, update and delete
	 * component metamodels.
	 *
	 * @param metamodelWriteServices the metamodel write service
	 */
	public void setMetamodelWriteServices(final MetamodelWriteServices metamodelWriteServices) {
		this.useMetamodelWriteServices = metamodelWriteServices;
	}

	@Override
	public void undeploy(final Class<?> implementation) {
		RuntimeCheck.notNull(implementation, "implementation");
		Class<?> componentType = this.useMetamodelWriteServices.getType(implementation);

		ComponentMetamodel metamodel = this.useMetamodelWriteServices.deleteMetamodel(componentType);
		this.types.remove(metamodel.getComponentType());

		this.produceDeploymentEvents.onComponentUndeployed(componentType, implementation);

	}

	@Override
	public boolean isDeployed(final Class<?> componentType) {
		return this.types.contains(componentType);
	}

	@Override
	public List<Class<?>> getDeployedComponentTypes() {
		return new ArrayList<>(this.types);
	}

	@Override
	public void reorderDeployedComponentTypes(final List<Class<?>> order) {
		RuntimeCheck.isTrue(order.size() == this.types.size() && order.containsAll(this.types),
				"order must be a permutation of the deployed types");
		this.types.clear();
		this.types.addAll(order);
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
		for (Class<?> componentType : this.types) {
			ShutdownResidues.addIfNotInternal(residues, "Deployer", "component still deployed", componentType);
		}
		return residues;
	}


}
