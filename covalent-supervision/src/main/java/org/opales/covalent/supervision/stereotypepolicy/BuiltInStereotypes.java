/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.stereotypepolicy;

import java.util.List;

/**
 * The rules every stereotype table starts with: the roles of the MVIC pattern and the usual
 * managers and stores, each shown as its own name. The stereotype table removes them only once
 * the user has allowed it, and can always bring them back.
 */
public final class BuiltInStereotypes {

	private static final List<StereotypeRule> RULES = List.of(rule("Controller"), rule("Interaction"),
			rule("Manager"), rule("Model"), rule("Store"), rule("View"));

	private BuiltInStereotypes() {
	}

	/**
	 * Returns the built-in rules.
	 *
	 * @return the built-in rules
	 */
	public static List<StereotypeRule> rules() {
		return RULES;
	}

	private static StereotypeRule rule(final String name) {
		return new StereotypeRule(name, name, true);
	}
}
