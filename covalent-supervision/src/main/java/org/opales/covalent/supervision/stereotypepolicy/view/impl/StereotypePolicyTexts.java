/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.stereotypepolicy.view.impl;

import java.util.ResourceBundle;

import org.opales.covalent.supervision.supervisionhmi.view.SupervisorMessages;

/** The Stereotypes panel's texts, from {@code messages.properties} beside it, in the supervisor's language. */
final class StereotypePolicyTexts {

	private static final ResourceBundle BUNDLE = SupervisorMessages.bundle("org.opales.covalent.supervision.stereotypepolicy.view.impl.messages");

	private StereotypePolicyTexts() {
	}

	/**
	 * @param key       the text's key
	 * @param arguments what fills its {@code {0}}, {@code {1}}...
	 * @return the text
	 */
	static String get(final String key, final Object... arguments) {
		return SupervisorMessages.text(BUNDLE, key, arguments);
	}
}
