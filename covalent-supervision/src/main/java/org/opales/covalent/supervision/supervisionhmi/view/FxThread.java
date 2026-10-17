/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.supervisionhmi.view;

import javafx.application.Platform;

/**
 * Runs work on the JavaFX application thread, from whichever thread asks. The services of the views use it so that
 * they can be called from any thread.
 */
public final class FxThread {

	private FxThread() {
		// Not meant to be instantiated.
	}

	/**
	 * Runs the work at once when the calling thread is the JavaFX application thread, and later on that thread
	 * otherwise, so that calls made from the application thread keep their order.
	 *
	 * @param work what to run on the JavaFX application thread
	 */
	public static void run(final Runnable work) {
		if (Platform.isFxApplicationThread()) {
			work.run();
		} else {
			Platform.runLater(work);
		}
	}
}
