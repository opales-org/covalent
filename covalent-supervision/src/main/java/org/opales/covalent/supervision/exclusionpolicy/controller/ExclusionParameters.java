/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.exclusionpolicy.controller;

import org.opales.covalent.container.component.annotations.Parameters;

/**
 * Whether a component type should be left out of the supervision screens -
 * offered by {@code ExclusionPolicyController}, accessed by
 * {@code ComponentDiagram}/{@code SequenceDiagram}/
 * {@code DependencyManagement}.
 */
@Parameters
public interface ExclusionParameters {

	/**
	 * Tells whether a component type is left out of the supervision screens: it is one of the excluded
	 * types, or it is a framework-internal type while internal components are excluded.
	 *
	 * @param componentType the component type to check
	 * @return {@code true} if this component type should be left out of the
	 *         supervision screens
	 */
	boolean isExcluded(Class<?> componentType);
}
