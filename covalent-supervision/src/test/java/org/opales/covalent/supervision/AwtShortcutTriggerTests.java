/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.supervision.testsupport.CapturingLogHandler;

/**
 * {@link AwtShortcutTrigger}: the JVM-wide AWT key listener that summons the supervisor from a Swing/AWT application.
 * The key events are handed to the registered listeners directly, so no display is needed.
 */
public class AwtShortcutTriggerTests {

	private static final Component SOURCE = new Component() {
		private static final long serialVersionUID = 1L;
	};

	/** Hands a key press to the AWT key listeners of the JVM, as the AWT does once the focus owner is known. */
	private static void press(final int code, final int modifiers) {
		KeyEvent event = new KeyEvent(SOURCE, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers, code,
				KeyEvent.CHAR_UNDEFINED);
		for (AWTEventListener listener : Toolkit.getDefaultToolkit().getAWTEventListeners(AWTEvent.KEY_EVENT_MASK)) {
			listener.eventDispatched(event);
		}
	}

	private CapturingLogHandler awtShortcutTriggerLogs;

	@Before
	public void captureTheLogs() {
		this.awtShortcutTriggerLogs = CapturingLogHandler.install(AwtShortcutTrigger.class);
	}

	@After
	public void restoreTheLogs() {
		this.awtShortcutTriggerLogs.restore();
	}

	@Test
	public void theCombinationSummonsTheSupervisorAndOtherKeysDoNot() {
		AtomicInteger summons = new AtomicInteger();
		AwtShortcutTrigger.install(SupervisionShortcut.of("Ctrl+Shift+F9"), summons::incrementAndGet);

		press(KeyEvent.VK_F9, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK);
		Assert.assertEquals(1, summons.get());

		press(KeyEvent.VK_F9, KeyEvent.CTRL_DOWN_MASK);
		press(KeyEvent.VK_F8, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK);
		Assert.assertEquals(1, summons.get());
	}

	@Test
	public void aDisabledShortcutInstallsNothing() {
		AtomicInteger summons = new AtomicInteger();
		AwtShortcutTrigger.install(SupervisionShortcut.of("none"), summons::incrementAndGet);

		press(KeyEvent.VK_F12, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK);
		Assert.assertEquals(0, summons.get());
	}

	@Test
	public void aFailingSummonNeverReachesTheApplication() {
		AwtShortcutTrigger.install(SupervisionShortcut.of("Ctrl+Shift+F7"), () -> {
			throw new IllegalStateException("boom");
		});

		press(KeyEvent.VK_F7, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK);
		Assert.assertEquals(List.of("The shortcut could not summon the supervisor."), this.awtShortcutTriggerLogs.messages(Level.WARNING));
		Assert.assertTrue(this.awtShortcutTriggerLogs.failures(Level.WARNING).get(0) instanceof IllegalStateException);
	}
}
