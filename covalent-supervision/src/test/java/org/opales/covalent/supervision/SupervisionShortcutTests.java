/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.supervision.testsupport.CapturingLogHandler;

import javafx.scene.input.KeyCode;

/**
 * {@link SupervisionShortcut}: the key combination that summons the supervisor, Ctrl+Shift+F12 by default,
 * redefined by the {@code covalent.supervision.shortcut} system property and disabled by {@code none}.
 */
public class SupervisionShortcutTests {

	private static final Component SOURCE = new Component() {
		private static final long serialVersionUID = 1L;
	};

	private static javafx.scene.input.KeyEvent fxPressed(final KeyCode code, final boolean shift, final boolean control,
			final boolean alt) {
		return new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", code, shift, control,
				alt, false);
	}

	private static KeyEvent awtPressed(final int code, final int modifiers) {
		return new KeyEvent(SOURCE, KeyEvent.KEY_PRESSED, 0L, modifiers, code, KeyEvent.CHAR_UNDEFINED);
	}

	private CapturingLogHandler supervisionShortcutLogs;

	@Before
	public void captureTheLogs() {
		this.supervisionShortcutLogs = CapturingLogHandler.install(SupervisionShortcut.class);
	}

	@After
	public void restoreTheLogs() {
		this.supervisionShortcutLogs.restore();
	}

	@Test
	public void theDefaultCombinationIsControlShiftF12() {
		SupervisionShortcut shortcut = SupervisionShortcut.of(null);
		Assert.assertTrue(shortcut.isEnabled());
		Assert.assertTrue(shortcut.matches(fxPressed(KeyCode.F12, true, true, false)));
		Assert.assertFalse(shortcut.matches(fxPressed(KeyCode.F12, false, true, false)));
		Assert.assertFalse(shortcut.matches(fxPressed(KeyCode.F12, true, false, false)));
		Assert.assertFalse(shortcut.matches(fxPressed(KeyCode.F11, true, true, false)));
	}

	@Test
	public void theDefaultCombinationMatchesTheSameKeysOnAwtEvents() {
		SupervisionShortcut shortcut = SupervisionShortcut.of("");
		int ctrlShift = KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK;
		Assert.assertTrue(shortcut.matches(awtPressed(KeyEvent.VK_F12, ctrlShift)));
		Assert.assertFalse(shortcut.matches(awtPressed(KeyEvent.VK_F12, KeyEvent.CTRL_DOWN_MASK)));
		Assert.assertFalse(shortcut.matches(awtPressed(KeyEvent.VK_F12, ctrlShift | KeyEvent.ALT_DOWN_MASK)));
		Assert.assertFalse(shortcut.matches(awtPressed(KeyEvent.VK_F11, ctrlShift)));
	}

	@Test
	public void aReleasedAwtKeyDoesNotMatch() {
		SupervisionShortcut shortcut = SupervisionShortcut.of(null);
		KeyEvent released = new KeyEvent(SOURCE, KeyEvent.KEY_RELEASED, 0L,
				KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK, KeyEvent.VK_F12, KeyEvent.CHAR_UNDEFINED);
		Assert.assertFalse(shortcut.matches(released));
	}

	@Test
	public void thePropertyRedefinesTheCombination() {
		SupervisionShortcut shortcut = SupervisionShortcut.of("Ctrl+Shift+F11");
		Assert.assertTrue(shortcut.isEnabled());
		Assert.assertTrue(shortcut.matches(fxPressed(KeyCode.F11, true, true, false)));
		Assert.assertFalse(shortcut.matches(fxPressed(KeyCode.F12, true, true, false)));
		int ctrlShift = KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK;
		Assert.assertTrue(shortcut.matches(awtPressed(KeyEvent.VK_F11, ctrlShift)));
		Assert.assertFalse(shortcut.matches(awtPressed(KeyEvent.VK_F12, ctrlShift)));
	}

	@Test
	public void noneDisablesTheShortcut() {
		SupervisionShortcut shortcut = SupervisionShortcut.of("none");
		Assert.assertFalse(shortcut.isEnabled());
		Assert.assertFalse(shortcut.matches(fxPressed(KeyCode.F12, true, true, false)));
		Assert.assertFalse(shortcut.matches(awtPressed(KeyEvent.VK_F12,
				KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK)));
	}

	@Test
	public void anInvalidValueKeepsTheDefaultCombination() {
		SupervisionShortcut shortcut = SupervisionShortcut.of("Ctrl+Banana");
		Assert.assertTrue(shortcut.isEnabled());
		Assert.assertTrue(shortcut.matches(fxPressed(KeyCode.F12, true, true, false)));
		Assert.assertEquals(List.of("Invalid key combination 'Ctrl+Banana' for covalent.supervision.shortcut: using Ctrl+Shift+F12."), this.supervisionShortcutLogs.messages(Level.WARNING));
	}

	@Test
	public void aValueJavaFxCannotParseKeepsTheDefaultCombinationAndIsReported() {
		SupervisionShortcut shortcut = SupervisionShortcut.of("Ctrl+Ctrl+F9");

		Assert.assertTrue(shortcut.isEnabled());
		Assert.assertTrue(shortcut.matches(fxPressed(KeyCode.F12, true, true, false)));
		Assert.assertEquals(
				List.of("Invalid key combination 'Ctrl+Ctrl+F9' for covalent.supervision.shortcut: using Ctrl+Shift+F12."),
				this.supervisionShortcutLogs.messages(Level.WARNING));
	}

	@Test
	public void theShortcutModifierIsControlOrMetaOnAwtEventsAccordingToThePlatform() {
		SupervisionShortcut shortcut = SupervisionShortcut.of("Shortcut+Alt+F9");
		boolean macintosh = System.getProperty("os.name", "").toLowerCase().contains("mac");
		int platformKey = macintosh ? KeyEvent.META_DOWN_MASK : KeyEvent.CTRL_DOWN_MASK;
		int otherKey = macintosh ? KeyEvent.CTRL_DOWN_MASK : KeyEvent.META_DOWN_MASK;

		Assert.assertTrue(shortcut.matches(awtPressed(KeyEvent.VK_F9, platformKey | KeyEvent.ALT_DOWN_MASK)));
		Assert.assertFalse(shortcut.matches(awtPressed(KeyEvent.VK_F9, KeyEvent.ALT_DOWN_MASK)));
		Assert.assertFalse(shortcut.matches(awtPressed(KeyEvent.VK_F9, otherKey | KeyEvent.ALT_DOWN_MASK)));
		Assert.assertFalse(shortcut.matches(awtPressed(KeyEvent.VK_F9,
				platformKey | KeyEvent.ALT_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK)));
	}

	@Test
	public void everyModifierOfAnAwtCombinationMustBeDownAndNoOtherOne() {
		SupervisionShortcut meta = SupervisionShortcut.of("Meta+F9");
		SupervisionShortcut alt = SupervisionShortcut.of("Alt+F9");
		SupervisionShortcut bare = SupervisionShortcut.of("F9");

		Assert.assertTrue(meta.matches(awtPressed(KeyEvent.VK_F9, KeyEvent.META_DOWN_MASK)));
		Assert.assertFalse(meta.matches(awtPressed(KeyEvent.VK_F9, KeyEvent.CTRL_DOWN_MASK)));
		Assert.assertFalse(meta.matches(awtPressed(KeyEvent.VK_F9, 0)));
		Assert.assertTrue(alt.matches(awtPressed(KeyEvent.VK_F9, KeyEvent.ALT_DOWN_MASK)));
		Assert.assertFalse(alt.matches(awtPressed(KeyEvent.VK_F9, KeyEvent.ALT_DOWN_MASK | KeyEvent.META_DOWN_MASK)));
		Assert.assertTrue(bare.matches(awtPressed(KeyEvent.VK_F9, 0)));
		Assert.assertFalse(bare.matches(awtPressed(KeyEvent.VK_F9, KeyEvent.SHIFT_DOWN_MASK)));
	}

	@Test
	public void thePropertyNameIsTheDocumentedOne() {
		Assert.assertEquals("covalent.supervision.shortcut", SupervisionExtension.SHORTCUT_PROPERTY);
	}
}
