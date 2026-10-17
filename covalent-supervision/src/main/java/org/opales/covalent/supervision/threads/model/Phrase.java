/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.model;

import java.util.Arrays;
import java.util.List;

/**
 * Something the controller says, for the view to word in the supervisor's language: the key of a
 * text in the view's messages, and what fills it. A name - a thread's, a lock's - has no key: it
 * shows as it is.
 *
 * @param key       the text's key, {@code null} for a name
 * @param arguments what fills the text, or the name
 */
public record Phrase(String key, List<String> arguments) {

	/**
	 * Creates a phrase that words a text of the view's messages.
	 *
	 * @param key       the text's key
	 * @param arguments what fills it, as their {@code toString()}
	 * @return the phrase
	 */
	public static Phrase of(final String key, final Object... arguments) {
		return new Phrase(key, Arrays.stream(arguments).map(String::valueOf).toList());
	}

	/**
	 * Creates a phrase for a name.
	 *
	 * @param name the name, such as a thread's or a lock's
	 * @return a name, shown as it is
	 */
	public static Phrase name(final String name) {
		return new Phrase(null, List.of(name));
	}
}
