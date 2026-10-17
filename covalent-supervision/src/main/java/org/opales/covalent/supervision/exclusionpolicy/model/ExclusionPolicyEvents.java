/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.exclusionpolicy.model;

import java.util.List;

import org.opales.covalent.container.component.annotations.Events;

/** Produced by {@code ExclusionPolicyModel}, consumed by the View and Interaction. */
@Events
public interface ExclusionPolicyEvents {

	/**
	 * Fired when the excluded types change, and when the state is published again.
	 *
	 * @param componentTypes the complete excluded-types list
	 */
	void onExcludedComponentsChanged(List<Class<?>> componentTypes);

	/**
	 * Fired when the exclusion of framework-internal components changes, and when the state is published
	 * again.
	 *
	 * @param exclude whether framework-internal components are excluded
	 */
	void onExcludeInternalComponentsChanged(boolean exclude);
}
