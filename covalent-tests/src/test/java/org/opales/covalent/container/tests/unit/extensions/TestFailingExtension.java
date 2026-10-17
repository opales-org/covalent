/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.extensions;

import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.extension.CovalentExtension;

/**
 * An extension failing when the container is started: an unexpected failure
 * once the business components are started.
 */
public final class TestFailingExtension implements CovalentExtension {

	public static final String EXTENSION_ID = "test-failing-extension";

	public static final String MESSAGE = "test failing extension";

	@Override
	public String getId() {
		return EXTENSION_ID;
	}

	@Override
	public void onContainerStarted(final CovalentContainer container) {
		throw new IllegalStateException(MESSAGE);
	}
}
