/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.view;

import java.util.Locale;
import java.util.ResourceBundle;

import org.opales.covalent.supervision.componentdiagram.model.PortColorKey;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisorMessages;

/**
 * The words for the component diagram's palette entries, as a settings panel shows them, in the
 * supervisor's language, from {@code messages.properties} beside this class. {@link
 * PortColorKey#getLabel()} and {@link PortColorKey#getCategory()} stay the English names, the
 * latter grouping the entries.
 */
public final class PortColorKeyTexts {

	private static final ResourceBundle BUNDLE = SupervisorMessages
			.bundle("org.opales.covalent.supervision.componentdiagram.view.messages");

	private PortColorKeyTexts() {
	}

	/**
	 * Returns the label of a palette entry in the supervisor's language.
	 *
	 * @param key the palette entry
	 * @return the entry's label, such as "Service provided"
	 */
	public static String label(final PortColorKey key) {
		return SupervisorMessages.text(BUNDLE, "color." + key.name());
	}

	/**
	 * Returns the section header of a palette entry in the supervisor's language.
	 *
	 * @param key the palette entry
	 * @return the entry's section header, such as "Ports &amp; wires"
	 */
	public static String category(final PortColorKey key) {
		return SupervisorMessages.text(BUNDLE, "category." + categoryKey(key.getCategory()));
	}

	/** @return the key of a section header, its English name in camel case: {@code portsAndWires}... */
	private static String categoryKey(final String category) {
		StringBuilder key = new StringBuilder();
		for (String word : category.replace("&", "and").split("[^A-Za-z]+")) {
			if (!word.isEmpty()) {
				key.append(key.length() == 0 ? word.toLowerCase(Locale.ROOT)
						: Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase(Locale.ROOT));
			}
		}
		return key.toString();
	}
}
