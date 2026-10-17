/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.exclusionpolicy.model;

import java.util.List;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Provided by {@code ExclusionPolicyModel}, used by
 * {@code ExclusionPolicyController}. The policy's two fields, as plain
 * state - nothing else; {@code isExcluded(...)}'s own
 * filtering rule is business logic and lives on the Controller.
 */
@Services
public interface ExclusionPolicyModelServices {

	/**
	 * Replaces the excluded types and fires the matching event.
	 *
	 * @param componentTypes the complete excluded-types list
	 */
	void setExcludedComponents(List<Class<?>> componentTypes);

	/**
	 * Switches the exclusion of framework-internal components on or off and fires the matching event.
	 *
	 * @param exclude whether framework-internal components are excluded
	 */
	void setExcludeInternalComponents(boolean exclude);

	/** Fires both fields' current values again, for a consumer that has just started rendering them. */
	void publishState();
}
