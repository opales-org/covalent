/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport;

import java.util.ResourceBundle;

import org.opales.covalent.supervision.supervisionhmi.view.SupervisorMessages;

/**
 * The words of the frame of a text sequence log, in the supervisor's language, so that a test does not depend on the
 * language of the machine it runs on.
 */
public final class SequenceLogWords {

	private static final ResourceBundle BUNDLE = SupervisorMessages
			.bundle("org.opales.covalent.supervision.sequencediagram.messages");

	private SequenceLogWords() {
	}

	/**
	 * @param key       the text's key
	 * @param arguments what fills its {@code {0}}, {@code {1}}...
	 * @return the text
	 */
	public static String get(final String key, final Object... arguments) {
		return SupervisorMessages.text(BUNDLE, key, arguments);
	}

	/**
	 * @param reason {@code stopped}, {@code saved} or {@code closed}
	 * @return what the last line of a text log starts with for that reason, up to the time
	 */
	public static String endOfLog(final String reason) {
		String line = get("log.end", get("log.end." + reason), "@", "", "");
		return line.substring(0, line.indexOf('@'));
	}
}
