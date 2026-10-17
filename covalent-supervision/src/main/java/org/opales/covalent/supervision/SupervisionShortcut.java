/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision;

import java.awt.event.InputEvent;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyCombination.ModifierValue;
import javafx.scene.input.KeyEvent;

/**
 * The key combination that summons the supervisor, chosen by the {@code covalent.supervision.shortcut} system
 * property: Ctrl+Shift+F12 by default, any combination of modifiers and a key name in the syntax of
 * {@link KeyCombination#valueOf(String)}, or {@code none} for no shortcut at all. It recognizes the key events of JavaFX and of AWT.
 */
final class SupervisionShortcut {

	/** The combination used when the property is absent or invalid. */
	static final String DEFAULT_COMBINATION = "Ctrl+Shift+F12";

	private static final String NONE = "none";

	private static final Logger log = Logger.getLogger(SupervisionShortcut.class.getName());

	/** The combination, {@code null} when the shortcut is disabled. */
	private final KeyCombination combination;

	private SupervisionShortcut(final KeyCombination combination) {
		this.combination = combination;
	}

	/**
	 * Reads the shortcut named by a property value.
	 *
	 * @param value the property value; {@code null} or blank for the default combination, {@code none} to disable
	 *              the shortcut
	 * @return the shortcut; the default one when the value is not a valid combination
	 */
	static SupervisionShortcut of(final String value) {
		if (value == null || value.isBlank()) {
			return new SupervisionShortcut(KeyCombination.valueOf(DEFAULT_COMBINATION));
		}
		String text = value.trim();
		if (NONE.equalsIgnoreCase(text)) {
			return new SupervisionShortcut(null);
		}
		try {
			KeyCombination parsed = KeyCombination.valueOf(text);
			if (parsed instanceof KeyCodeCombination) {
				return new SupervisionShortcut(parsed);
			}
		} catch (RuntimeException e) {
			// Reported below, like a combination that is not made of modifiers and a key name.
		}
		log.log(Level.WARNING, "Invalid key combination ''{0}'' for {1}: using {2}.",
				new Object[] { text, SupervisionExtension.SHORTCUT_PROPERTY, DEFAULT_COMBINATION });
		return new SupervisionShortcut(KeyCombination.valueOf(DEFAULT_COMBINATION));
	}

	/**
	 * Tells whether a key combination is active.
	 *
	 * @return {@code false} when the shortcut is disabled
	 */
	boolean isEnabled() {
		return this.combination != null;
	}

	/**
	 * Tells whether a JavaFX key event is the combination.
	 *
	 * @param event a key event
	 * @return {@code true} for the key press of the combination
	 */
	boolean matches(final KeyEvent event) {
		return this.combination != null && event.getEventType() == KeyEvent.KEY_PRESSED
				&& this.combination.match(event);
	}

	/**
	 * Tells whether an AWT key event is the combination.
	 *
	 * @param event a key event
	 * @return {@code true} for the key press of the combination
	 */
	boolean matches(final java.awt.event.KeyEvent event) {
		if (!(this.combination instanceof KeyCodeCombination) || event.getID() != java.awt.event.KeyEvent.KEY_PRESSED) {
			return false;
		}
		KeyCodeCombination keyCombination = (KeyCodeCombination) this.combination;
		KeyCode code = keyCombination.getCode();
		int modifiers = event.getModifiersEx();
		boolean macintosh = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
		boolean shortcutDown = keyCombination.getShortcut() == ModifierValue.DOWN;
		// The platform shortcut key is Control, or Meta on a Macintosh.
		ModifierValue control = shortcutDown && !macintosh ? ModifierValue.DOWN : keyCombination.getControl();
		ModifierValue meta = shortcutDown && macintosh ? ModifierValue.DOWN : keyCombination.getMeta();
		return code.getCode() == event.getKeyCode()
				&& accepts(keyCombination.getShift(), (modifiers & InputEvent.SHIFT_DOWN_MASK) != 0)
				&& accepts(control, (modifiers & InputEvent.CTRL_DOWN_MASK) != 0)
				&& accepts(keyCombination.getAlt(), (modifiers & InputEvent.ALT_DOWN_MASK) != 0)
				&& accepts(meta, (modifiers & InputEvent.META_DOWN_MASK) != 0);
	}

	private static boolean accepts(final ModifierValue required, final boolean down) {
		return required == ModifierValue.ANY || (required == ModifierValue.DOWN) == down;
	}
}
