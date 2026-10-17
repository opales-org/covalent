/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.cockpit.view.impl;

import java.util.ResourceBundle;

import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.supervision.cockpit.model.LifecycleCommand;
import org.opales.covalent.supervision.cockpit.model.Phrase;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisorMessages;

/** The cockpit's texts, from {@code messages.properties} beside it, in the supervisor's language. */
final class CockpitTexts {

	private static final ResourceBundle BUNDLE = SupervisorMessages
			.bundle("org.opales.covalent.supervision.cockpit.view.impl.messages");

	private CockpitTexts() {
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
	static String count(final String key, final long count, final Object... more) {
		Object[] arguments = new Object[more.length + 1];
		arguments[0] = count;
		System.arraycopy(more, 0, arguments, 1, more.length);
		return get(count == 1 ? key + ".one" : key + ".many", arguments);
	}

	/** @return a phrase made by the controller, in words, its phrase arguments worded too */
	static String of(final Phrase phrase) {
		Object[] arguments = phrase.arguments().stream()
				.map(argument -> argument instanceof Phrase inner ? of(inner) : argument).toArray();
		return get(phrase.key(), arguments);
	}

	/** @return a command's name, as buttons and menus show it */
	static String command(final LifecycleCommand command) {
		return get("command." + command.name());
	}

	/** @return a state's name, {@code "unknown"} in words for {@code null} */
	static String state(final ComponentState state) {
		return get(state == null ? "state.unknown" : "state." + state.name());
	}
}
