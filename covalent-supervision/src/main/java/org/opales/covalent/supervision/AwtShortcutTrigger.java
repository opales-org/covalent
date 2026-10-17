/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision;

import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Summons the supervisor from the keyboard in a Swing or AWT application: one AWT key listener for the whole JVM.
 */
final class AwtShortcutTrigger {

	private static final Logger log = Logger.getLogger(AwtShortcutTrigger.class.getName());

	private AwtShortcutTrigger() {
	}

	/**
	 * Listens for the shortcut on every AWT component of the JVM; does nothing when the shortcut is disabled. The
	 * key event of the shortcut is consumed, the others reach the application untouched.
	 *
	 * @param shortcut the key combination
	 * @param summon   what to do when the combination is pressed; a failure is logged and never reaches the
	 *                 application
	 */
	static void install(final SupervisionShortcut shortcut, final Runnable summon) {
		if (!shortcut.isEnabled()) {
			return;
		}
		Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
			if (event instanceof KeyEvent && shortcut.matches((KeyEvent) event)) {
				((KeyEvent) event).consume();
				try {
					summon.run();
				} catch (RuntimeException e) {
					log.log(Level.WARNING, "The shortcut could not summon the supervisor.", e);
				}
			}
		}, AWTEvent.KEY_EVENT_MASK);
	}
}
