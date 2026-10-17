/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal;

import java.util.ResourceBundle;

import org.opales.covalent.supervision.supervisionhmi.view.SupervisorMessages;

/** The shared component view widgets' texts, from {@code messages.properties} beside it, in the Supervisor's language. */
final class ComponentViewTexts {

	private static final ResourceBundle BUNDLE = SupervisorMessages.bundle("org.opales.covalent.supervision.componentview.internal.messages");

	private ComponentViewTexts() {
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
