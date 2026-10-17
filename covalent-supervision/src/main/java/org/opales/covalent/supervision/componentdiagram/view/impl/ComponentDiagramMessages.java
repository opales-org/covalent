/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.view.impl;

import java.util.ResourceBundle;

import org.opales.covalent.supervision.componentdiagram.model.ComponentPortRole;
import org.opales.covalent.supervision.componentdiagram.model.ComponentStatus;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisorMessages;

/**
 * The component diagram's texts (its tab, toolbox, inspector and views library dialogs), from
 * {@code messages.properties} beside this class, in the supervisor's language.
 */
final class ComponentDiagramMessages {

	private static final ResourceBundle BUNDLE = SupervisorMessages
			.bundle("org.opales.covalent.supervision.componentdiagram.view.impl.messages");

	private ComponentDiagramMessages() {
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
	 * @return the text for one ({@code key.one}) or for several ({@code key.many}), its {@code {0}}
	 *         the count
	 */
	static String count(final String key, final long count, final Object... more) {
		Object[] arguments = new Object[more.length + 1];
		arguments[0] = count;
		System.arraycopy(more, 0, arguments, 1, more.length);
		return get(count == 1 ? key + ".one" : key + ".many", arguments);
	}

	/** @return a lifecycle status in words, lower case */
	static String status(final ComponentStatus status) {
		return get("status." + status.name());
	}

	/** @return a port role in words, lower case: provided, used... */
	static String role(final ComponentPortRole role) {
		return get("role." + role.name());
	}
}
