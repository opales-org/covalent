/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.dependencymanagement.view.impl;

import java.util.ResourceBundle;

import org.opales.covalent.supervision.supervisionhmi.view.SupervisorMessages;

/** The dependency graph's texts, from {@code messages.properties} beside it, in the Supervisor's language. */
final class DependencyGraphTexts {

	private static final ResourceBundle BUNDLE = SupervisorMessages
			.bundle("org.opales.covalent.supervision.dependencymanagement.view.impl.messages");

	private DependencyGraphTexts() {
	}

	/**
	 * @param key       the text's key
	 * @param arguments what fills its {@code {0}}, {@code {1}}...
	 * @return the text
	 */
	static String get(final String key, final Object... arguments) {
		return SupervisorMessages.text(BUNDLE, key, arguments);
	}

	/**
	 * @param key   the key of the text for one
	 * @param count how many
	 * @param more  what fills its {@code {1}}, {@code {2}}...
	 * @return the text for one, or for several - {@code key.many}, its {@code {0}} the count
	 */
	static String count(final String key, final long count) {
		return get(count == 1 ? key + ".one" : key + ".many", count);
	}
}
