/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.scene.input.KeyEvent;
import javafx.stage.Window;

/**
 * Summons the supervisor from the keyboard in a JavaFX application: a key filter on every window, those already open
 * and those opened later. Reading the list of windows does not start the JavaFX toolkit, so installing the trigger
 * never starts it on the application's behalf.
 */
final class JavaFxShortcutTrigger {

	private static final Logger log = Logger.getLogger(JavaFxShortcutTrigger.class.getName());

	private JavaFxShortcutTrigger() {
	}

	/**
	 * Puts the shortcut filter on every window of the application; does nothing when the shortcut is disabled. The
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
		EventHandler<KeyEvent> filter = event -> {
			if (shortcut.matches(event)) {
				event.consume();
				try {
					summon.run();
				} catch (RuntimeException e) {
					log.log(Level.WARNING, "The shortcut could not summon the supervisor.", e);
				}
			}
		};
		Window.getWindows().addListener((ListChangeListener<Window>) change -> {
			while (change.next()) {
				if (change.wasAdded()) {
					for (Window window : change.getAddedSubList()) {
						watch(window, filter);
					}
				}
			}
		});
		watchOpenWindows(filter);
	}

	/** Covers the windows already open: the list is read on the JavaFX thread, where it changes. */
	private static void watchOpenWindows(final EventHandler<KeyEvent> filter) {
		Runnable watchAll = () -> {
			for (Window window : List.copyOf(Window.getWindows())) {
				watch(window, filter);
			}
		};
		if (Platform.isFxApplicationThread()) {
			watchAll.run();
			return;
		}
		try {
			Platform.runLater(watchAll);
		} catch (IllegalStateException toolkitNotStarted) {
			// No toolkit, so no window is open yet: the listener on the list covers those to come.
		}
	}

	/** The filter is its own marker on the window, so that a window is never watched twice. */
	private static void watch(final Window window, final EventHandler<KeyEvent> filter) {
		if (window.getProperties().putIfAbsent(filter, Boolean.TRUE) == null) {
			window.addEventFilter(KeyEvent.KEY_PRESSED, filter);
		}
	}
}
