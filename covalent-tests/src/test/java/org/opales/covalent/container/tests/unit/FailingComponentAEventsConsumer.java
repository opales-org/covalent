/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import org.opales.covalent.container.tests.unit.rawcomponents.componenta.ComponentAEvents;

/**
 * Shared events test fixture: a {@code ComponentAEvents} consumer that
 * always fails on {@link #onCreate(String)}, used to verify that a failing
 * subscriber does not prevent other subscribers from receiving the event.
 */
public class FailingComponentAEventsConsumer implements ComponentAEvents {

	int receivedCount;

	@Override
	public void onCreate(final String data) {
		this.receivedCount++;
		throw new IllegalStateException("expected subscriber failure");
	}

	@Override
	public void onUpdate(final String data) {
	}

	@Override
	public void onDelete(final String data) {
	}

	@Override
	public void onEmpty() {
	}

}
