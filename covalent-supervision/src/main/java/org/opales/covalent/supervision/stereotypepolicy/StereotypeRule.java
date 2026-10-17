/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.stereotypepolicy;

import java.util.Objects;

/**
 * One line of the stereotype table: a component whose simple name ends with {@code suffix} is
 * shown with the stereotype {@code label}.
 *
 * @param suffix  the end of a component's simple name
 * @param label   the stereotype shown; {@link StereotypeRules#check} accepts a new rule only if it has
 *                at most {@link StereotypeRules#MAX_LABEL_LENGTH} characters
 * @param builtIn whether the supervisor itself provides this rule: it is removed only once the
 *                user has allowed it
 */
public record StereotypeRule(String suffix, String label, boolean builtIn) {

	/**
	 * Creates a rule.
	 *
	 * @param suffix  the end of a component's simple name
	 * @param label   the stereotype shown
	 * @param builtIn whether the supervisor itself provides this rule
	 */
	public StereotypeRule {
		Objects.requireNonNull(suffix, "suffix");
		Objects.requireNonNull(label, "label");
	}
}
