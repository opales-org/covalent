/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.cockpit.model;

import java.util.Arrays;
import java.util.List;

/**
 * Something the controller says, for the view to word in the supervisor's language: the key of a
 * text in the view's messages, and what fills it. An argument is either a name, shown as it is,
 * or another phrase, worded the same way - a command, a state.
 *
 * @param key       the text's key
 * @param arguments what fills the text: {@link String}s and {@link Phrase}s
 */
public record Phrase(String key, List<Object> arguments) {

	/**
	 * Builds a phrase, keeping the phrases among the arguments and turning the others into text.
	 *
	 * @param key       the text's key
	 * @param arguments what fills it: phrases as they are, anything else as its {@code toString()}
	 * @return the phrase
	 */
	public static Phrase of(final String key, final Object... arguments) {
		return new Phrase(key, Arrays.stream(arguments)
				.map(argument -> argument instanceof Phrase ? argument : (Object) String.valueOf(argument)).toList());
	}

	/**
	 * Returns the phrase naming a lifecycle command, as the console shows it.
	 *
	 * @param command the command to name
	 * @return the phrase
	 */
	public static Phrase of(final LifecycleCommand command) {
		return of("command." + command.name());
	}
}
