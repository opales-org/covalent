/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.stereotypepolicy.model;

import java.util.List;

import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.supervision.stereotypepolicy.StereotypeRule;

/** Produced by {@code StereotypePolicyModel}, consumed by the View, the Interaction and the diagrams. */
@Events
public interface StereotypePolicyEvents {

	/**
	 * Tells that the stereotype table changed.
	 *
	 * @param rules the new whole stereotype table
	 */
	void onStereotypesChanged(List<StereotypeRule> rules);
}
