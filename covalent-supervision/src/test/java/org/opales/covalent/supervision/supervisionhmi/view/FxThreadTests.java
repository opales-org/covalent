/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.supervisionhmi.view;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import javafx.application.Platform;

/**
 * Work handed to the JavaFX thread runs there, at once when the caller already is that thread, and later otherwise.
 * It needs the JavaFX toolkit, so it is skipped on a machine that cannot start one.
 */
public class FxThreadTests {

	@BeforeClass
	public static void startToolkit() {
		CountDownLatch started = new CountDownLatch(1);
		try {
			Platform.startup(started::countDown);
		} catch (IllegalStateException alreadyStarted) {
			started.countDown();
		} catch (RuntimeException | Error unavailable) {
			Assume.assumeNoException("no JavaFX toolkit here", unavailable);
		}
		try {
			Assume.assumeTrue("no JavaFX toolkit here", started.await(10, TimeUnit.SECONDS));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			Assume.assumeNoException(e);
		}
	}

	@Test
	public void workFromAnotherThreadRunsOnTheJavaFxThread() throws Exception {
		CountDownLatch ran = new CountDownLatch(1);
		AtomicBoolean onFxThread = new AtomicBoolean();

		FxThread.run(() -> {
			onFxThread.set(Platform.isFxApplicationThread());
			ran.countDown();
		});

		Assert.assertTrue(ran.await(10, TimeUnit.SECONDS));
		Assert.assertTrue(onFxThread.get());
	}

	@Test
	public void workFromTheJavaFxThreadRunsAtOnceAndKeepsItsOrder() throws Exception {
		List<String> order = new ArrayList<>();
		CountDownLatch done = new CountDownLatch(1);

		Platform.runLater(() -> {
			FxThread.run(() -> order.add("first"));
			order.add("second");
			done.countDown();
		});

		Assert.assertTrue(done.await(10, TimeUnit.SECONDS));
		Assert.assertEquals(List.of("first", "second"), order);
	}
}
