/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram;

import java.util.ResourceBundle;

import org.opales.covalent.supervision.supervisionhmi.view.SupervisorMessages;

/**
 * The words of the frame of a text sequence log (its start and its end), from {@code messages.properties} beside this
 * class, in the supervisor's language. The CSV and JSON Lines logs are for machines and stay in English.
 */
final class SequenceLogTexts {

	private static final ResourceBundle BUNDLE = SupervisorMessages
			.bundle("org.opales.covalent.supervision.sequencediagram.messages");

	private SequenceLogTexts() {
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
	 * @param noun  {@code messages} or {@code failures}
	 * @param count how many
	 * @return the count with its noun, in the form the language gives to zero, one and many
	 */
	static String counted(final String noun, final int count) {
		return get("log." + noun + (count == 0 ? ".zero" : count == 1 ? ".one" : ".other"), count);
	}
}
