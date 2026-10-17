/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.containermanager.view.impl;

import java.util.ResourceBundle;

import org.opales.covalent.supervision.supervisionhmi.view.SupervisorMessages;

/** The container selector's texts, from {@code messages.properties} beside it, in the supervisor's language. */
final class ContainerManagerTexts {

	private static final ResourceBundle BUNDLE = SupervisorMessages.bundle("org.opales.covalent.supervision.containermanager.view.impl.messages");

	private ContainerManagerTexts() {
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
