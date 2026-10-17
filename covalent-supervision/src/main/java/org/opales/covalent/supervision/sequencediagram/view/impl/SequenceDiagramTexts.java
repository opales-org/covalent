/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.view.impl;

import java.util.ResourceBundle;

import org.opales.covalent.supervision.supervisionhmi.view.SupervisorMessages;

/** The sequence diagram's texts, from {@code messages.properties} beside it, in the supervisor's language. */
final class SequenceDiagramTexts {

	private static final ResourceBundle BUNDLE = SupervisorMessages
			.bundle("org.opales.covalent.supervision.sequencediagram.view.impl.messages");

	private SequenceDiagramTexts() {
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
	 * @param key         the text's key
	 * @param fallbackKey the key of the text used when there is no {@code key}
	 * @param arguments   what fills its {@code {0}}, {@code {1}}...
	 * @return the text
	 */
	static String getOr(final String key, final String fallbackKey, final Object... arguments) {
		return get(BUNDLE.containsKey(key) ? key : fallbackKey, arguments);
	}

	/**
	 * @param key   the key of the text for one
	 * @param count how many
	 * @param more  what fills its {@code {1}}, {@code {2}}...
	 * @return the text for one, or for several - {@code key.many}, its {@code {0}} the count
	 */
	static String count(final String key, final long count, final Object... more) {
		Object[] arguments = new Object[more.length + 1];
		arguments[0] = count;
		System.arraycopy(more, 0, arguments, 1, more.length);
		return get(count == 1 ? key + ".one" : key + ".many", arguments);
	}
}
