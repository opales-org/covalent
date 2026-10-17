/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.deployment;

import org.opales.covalent.container.component.annotations.Events;

/**
 * Reports component deployment changes.
 */
@Events
public interface DeploymentEvents {
	/**
	 * Notifies that an implementation has been deployed under a component type.
	 *
	 * @param componentType  deployed component type
	 * @param implementation deployed implementation class
	 */
	void onComponentDeployed(Class<?> componentType, Class<?> implementation);

	/**
	 * Notifies that an implementation has been undeployed from a component type.
	 *
	 * @param componentType  undeployed component type
	 * @param implementation undeployed implementation class
	 */
	void onComponentUndeployed(Class<?> componentType, Class<?> implementation);

}
