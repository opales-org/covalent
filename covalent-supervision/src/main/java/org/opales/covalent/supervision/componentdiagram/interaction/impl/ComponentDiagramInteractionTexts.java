/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.interaction.impl;

import java.util.ResourceBundle;

import org.opales.covalent.supervision.supervisionhmi.view.SupervisorMessages;

/**
 * The texts the component diagram's interaction controller hands to its view (errors, confirmations,
 * the level of a view to save), from {@code messages.properties} beside this class, in the
 * supervisor's language.
 */
final class ComponentDiagramInteractionTexts {

	private static final ResourceBundle BUNDLE = SupervisorMessages
			.bundle("org.opales.covalent.supervision.componentdiagram.interaction.impl.messages");

	private ComponentDiagramInteractionTexts() {
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
