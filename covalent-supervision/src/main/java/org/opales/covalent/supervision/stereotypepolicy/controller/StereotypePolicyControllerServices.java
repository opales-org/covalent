/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.stereotypepolicy.controller;

import java.util.List;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.supervision.stereotypepolicy.StereotypeRule;

/**
 * Mutators, provided by {@code StereotypePolicyController} and used only by this function's own
 * {@code StereotypePolicyInteraction} - not exposed on {@code StereotypeParameters}, so
 * ComponentDiagram and SequenceDiagram, which access that port, cannot reconfigure the table.
 */
@Services
public interface StereotypePolicyControllerServices {

	/**
	 * Replaces the whole stereotype table.
	 *
	 * @param rules the new whole stereotype table
	 */
	void setRules(List<StereotypeRule> rules);

	/** Has the Model publish its whole current state again, for a View that has just been shown. */
	void publishState();
}
