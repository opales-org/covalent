/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.deployment;

import java.util.List;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.metamodel.metamodel.ComponentMetamodel;

/**
 * Deploys and undeploys one component at a time, including its metamodel.
 * Traversal of composed components belongs to the lifecycle manager.
 */
@Services
public interface DeploymentServices {

	/**
	 * Creates or updates the metamodel and deploys one managed component.
	 *
	 * @param componentType  component type exposed to Covalent
	 * @param implementation implementation class deployed for the component type
	 * @return metamodel identifying the declared children, without deploying them
	 */
	ComponentMetamodel deploy(final Class<?> componentType, final Class<?> implementation);

	/**
	 * Deploys one raw component without requiring an implementation annotation.
	 *
	 * @param componentType raw component type
	 * @param implementation raw implementation class
	 * @return created or updated component metamodel
	 */
	ComponentMetamodel deployRaw(final Class<?> componentType, final Class<?> implementation);

	/**
	 * Undeploys the component represented by an implementation class.
	 *
	 * @param implementationClass implementation class to undeploy
	 */
	void undeploy(final Class<?> implementationClass);

	/**
	 * Returns all currently deployed component types, including composed children,
	 * in deployment order.
	 *
	 * @return deployed component types
	 */
	public List<Class<?>> getDeployedComponentTypes();

	/**
	 * Replaces the current deployment order with {@code order}, without
	 * adding, removing, or losing any currently deployed component type -
	 * {@code order} must contain exactly the same elements as
	 * {@link #getDeployedComponentTypes()}, just resequenced. Used to apply
	 * a computed dependency order after the fact, without undeploying or
	 * redeploying anything.
	 *
	 * @param order the deployed component types, in the desired order
	 * @throws IllegalArgumentException if {@code order} is not a permutation
	 *                                   of the currently deployed types
	 */
	public void reorderDeployedComponentTypes(final List<Class<?>> order);

	/**
	 * Reports whether a component type is deployed.
	 *
	 * @param componentType component type to inspect
	 * @return {@code true} when the component type is deployed
	 */
	public boolean isDeployed(final Class<?> componentType);
}
