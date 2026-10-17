/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.supervisionhmi.view;

import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import org.opales.covalent.supervision.supervisionhmi.SupervisorSettings;

/**
 * The supervisor's texts in its language. Each part of the supervisor keeps its texts in a
 * {@code messages.properties} bundle beside its classes, English, and their French in {@code
 * messages_fr.properties}. The language is read once, at start, from {@link
 * SupervisorSettings#LANGUAGE}: the system's when it says {@code system} or nothing, as long as
 * the supervisor speaks it, English otherwise.
 */
public final class SupervisorMessages {

	/** The languages the supervisor speaks, as {@link SupervisorSettings#LANGUAGE} names them. */
	public static final List<String> LANGUAGES = List.of("en", "fr");

	private static final Locale LOCALE = resolve(SupervisorSettings.getString(SupervisorSettings.LANGUAGE));

	/** Bundles never fall back on the system's locale: English, the base bundle, is the fallback. */
	private static final ResourceBundle.Control NO_SYSTEM_FALLBACK = ResourceBundle.Control
			.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

	private SupervisorMessages() {
	}

	/**
	 * @param language the language setting, {@code null} or {@code "system"} to follow the system
	 * @return the locale of that language, English when it is not one the supervisor speaks
	 */
	static Locale resolve(final String language) {
		String chosen = language == null || language.equals("system") ? Locale.getDefault().getLanguage() : language;
		return LANGUAGES.contains(chosen) ? Locale.forLanguageTag(chosen) : Locale.ENGLISH;
	}

	/**
	 * Returns the supervisor's language for this run.
	 *
	 * @return the locale, English when the language is not one the supervisor speaks
	 */
	public static Locale locale() {
		return LOCALE;
	}

	/**
	 * Loads a resource bundle in the supervisor's language, English when a text is not translated.
	 *
	 * @param baseName a bundle's base name, such as {@code org.opales...threads.view.impl.messages}
	 * @return that bundle in the supervisor's language
	 */
	public static ResourceBundle bundle(final String baseName) {
		return ResourceBundle.getBundle(baseName, LOCALE, NO_SYSTEM_FALLBACK);
	}

	/**
	 * Looks a text up in a bundle and fills its numbered placeholders.
	 *
	 * @param bundle    the texts
	 * @param key       the text's key
	 * @param arguments what fills its {@code {0}}, {@code {1}}..., as their {@code toString()}: an
	 *                  apostrophe in a text needs no escaping, as it would with {@code MessageFormat}
	 * @return the text; the key itself when missing, so that a gap shows rather than breaks
	 */
	public static String text(final ResourceBundle bundle, final String key, final Object... arguments) {
		String pattern;
		try {
			pattern = bundle.getString(key);
		} catch (MissingResourceException e) {
			return key;
		}
		StringBuilder text = new StringBuilder(pattern.length() + 16);
		int from = 0;
		while (from < pattern.length()) {
			int open = pattern.indexOf('{', from);
			int close = open < 0 ? -1 : pattern.indexOf('}', open);
			if (close < 0) {
				break;
			}
			int index = placeholderIndex(pattern, open + 1, close);
			if (index >= 0 && index < arguments.length) {
				text.append(pattern, from, open).append(arguments[index]);
				from = close + 1;
			} else {
				text.append(pattern, from, open + 1);
				from = open + 1;
			}
		}
		return text.append(pattern, from, pattern.length()).toString();
	}

	/** The number a placeholder holds between its braces, or -1 when it is not a plain number. */
	private static int placeholderIndex(final String pattern, final int start, final int end) {
		if (start >= end || end - start > 4) {
			return -1;
		}
		int index = 0;
		for (int position = start; position < end; position++) {
			char digit = pattern.charAt(position);
			if (digit < '0' || digit > '9') {
				return -1;
			}
			index = index * 10 + (digit - '0');
		}
		return index;
	}
}
