/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opales.covalent.supervision.testsupport.CapturingLogHandler;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * {@link JavaFxShortcutTrigger}: the key filter it puts on every window of the application, those already open as
 * well as those opened later. Needs a JavaFX toolkit, so the tests are skipped where none can start.
 */
public class JavaFxShortcutTriggerTests {

	private static final long WAIT_SECONDS = 10;

	private CapturingLogHandler javaFxShortcutTriggerLogs;

	@Before
	public void captureTheLogs() {
		this.javaFxShortcutTriggerLogs = CapturingLogHandler.install(JavaFxShortcutTrigger.class);
	}

	@After
	public void restoreTheLogs() {
		this.javaFxShortcutTriggerLogs.restore();
	}

	@BeforeClass
	public static void startTheToolkit() {
		try {
			Platform.startup(() -> {
			});
		} catch (IllegalStateException alreadyStarted) {
			// The toolkit already runs in this JVM.
		} catch (UnsupportedOperationException | UnsatisfiedLinkError | NoClassDefFoundError noToolkit) {
			Assume.assumeNoException(noToolkit);
		}
		Platform.setImplicitExit(false);
	}

	private static void onFxThread(final Runnable action) throws InterruptedException {
		CountDownLatch done = new CountDownLatch(1);
		Platform.runLater(() -> {
			try {
				action.run();
			} finally {
				done.countDown();
			}
		});
		Assert.assertTrue(done.await(WAIT_SECONDS, TimeUnit.SECONDS));
	}

	private static Stage openWindow() {
		Stage stage = new Stage();
		stage.setScene(new Scene(new StackPane(), 100, 100));
		stage.show();
		return stage;
	}

	private static void press(final Stage stage, final KeyCode code, final boolean shift, final boolean control) {
		Event.fireEvent(stage.getScene().getRoot(),
				new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, control, false, false));
	}

	@Test
	public void aWindowOpenedAfterTheInstallationSummonsTheSupervisor() throws InterruptedException {
		AtomicInteger summons = new AtomicInteger();
		JavaFxShortcutTrigger.install(SupervisionShortcut.of("Ctrl+Shift+F6"), summons::incrementAndGet);

		onFxThread(() -> {
			Stage stage = openWindow();
			press(stage, KeyCode.F6, true, true);
			press(stage, KeyCode.F6, false, true);
			press(stage, KeyCode.F5, true, true);
			stage.close();
		});
		Assert.assertEquals(1, summons.get());
	}

	@Test
	public void aWindowAlreadyOpenWhenTheTriggerIsInstalledSummonsTheSupervisor() throws InterruptedException {
		AtomicInteger summons = new AtomicInteger();
		Stage[] stage = new Stage[1];
		onFxThread(() -> stage[0] = openWindow());

		onFxThread(() -> JavaFxShortcutTrigger.install(SupervisionShortcut.of("Ctrl+Shift+F4"),
				summons::incrementAndGet));
		onFxThread(() -> {
			press(stage[0], KeyCode.F4, true, true);
			stage[0].close();
		});
		Assert.assertEquals(1, summons.get());
	}

	@Test
	public void aSceneSetAfterTheWindowOpensIsCoveredToo() throws InterruptedException {
		AtomicInteger summons = new AtomicInteger();
		JavaFxShortcutTrigger.install(SupervisionShortcut.of("Ctrl+Shift+F3"), summons::incrementAndGet);

		onFxThread(() -> {
			Stage stage = openWindow();
			stage.setScene(new Scene(new StackPane(), 120, 120));
			press(stage, KeyCode.F3, true, true);
			stage.close();
		});
		Assert.assertEquals(1, summons.get());
	}

	@Test
	public void aDisabledShortcutInstallsNothing() throws InterruptedException {
		AtomicInteger summons = new AtomicInteger();
		JavaFxShortcutTrigger.install(SupervisionShortcut.of("none"), summons::incrementAndGet);

		onFxThread(() -> {
			Stage stage = openWindow();
			press(stage, KeyCode.F12, true, true);
			stage.close();
		});
		Assert.assertEquals(0, summons.get());
	}

	@Test
	public void aFailingSummonNeverReachesTheApplication() throws InterruptedException {
		JavaFxShortcutTrigger.install(SupervisionShortcut.of("Ctrl+Shift+F2"), () -> {
			throw new IllegalStateException("boom");
		});

		onFxThread(() -> {
			Stage stage = openWindow();
			press(stage, KeyCode.F2, true, true);
			stage.close();
		});
		Assert.assertEquals(List.of("The shortcut could not summon the supervisor."), this.javaFxShortcutTriggerLogs.messages(Level.WARNING));
		Assert.assertTrue(this.javaFxShortcutTriggerLogs.failures(Level.WARNING).get(0) instanceof IllegalStateException);
	}
}
