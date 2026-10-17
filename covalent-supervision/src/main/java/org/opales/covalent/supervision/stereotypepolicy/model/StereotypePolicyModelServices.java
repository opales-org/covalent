/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.stereotypepolicy.model;

import java.util.List;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.supervision.stereotypepolicy.StereotypeRule;

/**
 * Provided by {@code StereotypePolicyModel}, used by {@code StereotypePolicyController}. The
 * stereotype table, as plain state - nothing else; the matching rule is business logic and lives
 * on the Controller.
 */
@Services
public interface StereotypePolicyModelServices {

	/**
	 * Replaces the whole stereotype table.
	 *
	 * @param rules the new whole stereotype table
	 */
	void setRules(List<StereotypeRule> rules);

	/** Fires the table's current value again, for a consumer that has just started rendering it. */
	void publishState();
}
