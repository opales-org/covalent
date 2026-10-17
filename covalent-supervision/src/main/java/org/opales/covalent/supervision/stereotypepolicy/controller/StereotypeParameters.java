/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.stereotypepolicy.controller;

import org.opales.covalent.container.component.annotations.Parameters;

/**
 * The stereotype a component shows - offered by {@code StereotypePolicyController}, accessed by
 * {@code ComponentDiagram} and {@code SequenceDiagram}, which then show the same stereotypes.
 */
@Parameters
public interface StereotypeParameters {

	/**
	 * Returns the stereotype a component type shows, from the end of its name.
	 *
	 * @param componentType the component type
	 * @return the stereotype of the table rule whose name ending is the longest among those the
	 *         type's simple name ends with, {@code null} when no rule matches: the caller then
	 *         falls back on what the metamodel says
	 */
	String stereotypeFor(Class<?> componentType);
}
