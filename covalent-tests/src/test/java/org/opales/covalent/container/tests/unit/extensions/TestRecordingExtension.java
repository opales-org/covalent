/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.extensions;

import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.extension.CovalentExtension;

public final class TestRecordingExtension implements CovalentExtension {

	public static final String EXTENSION_ID = "test-recording-extension";

	private static int startCount = 0;

	private static CovalentContainer lastContainer;

	@Override
	public String getId() {
		return EXTENSION_ID;
	}

	@Override
	public void onContainerStarted(final CovalentContainer container) {
		startCount++;
		lastContainer = container;
	}

	public static int getStartCount() {
		return startCount;
	}

	public static CovalentContainer getLastContainer() {
		return lastContainer;
	}

	public static void reset() {
		startCount = 0;
		lastContainer = null;
	}

}
