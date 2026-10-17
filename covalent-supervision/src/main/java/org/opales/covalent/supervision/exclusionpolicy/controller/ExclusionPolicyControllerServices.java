/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.exclusionpolicy.controller;

import java.util.List;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Mutators, provided by {@code ExclusionPolicyController} and used only by
 * this function's own {@code ExclusionPolicyInteraction} - not
 * exposed on {@code ExclusionParameters}, so
 * ComponentDiagram/SequenceDiagram/DependencyManagement, which
 * access that port, cannot silently reconfigure this function's own
 * business state.
 */
@Services
public interface ExclusionPolicyControllerServices {

	/**
	 * Replaces the excluded types with the given list.
	 *
	 * @param componentTypes the complete excluded-types list
	 */
	void setExcludedComponents(List<Class<?>> componentTypes);

	/**
	 * Adds one component type to the excluded types.
	 *
	 * @param componentType the component type to add to the excluded set
	 */
	void addExcludedComponent(Class<?> componentType);

	/**
	 * Switches the exclusion of framework-internal components on or off.
	 *
	 * @param exclude whether framework-internal components are excluded
	 */
	void setExcludeInternalComponents(boolean exclude);

	/** Has the Model publish its whole current state again, for a View that has just been shown. */
	void publishState();
}
