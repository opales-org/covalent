/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

/**
 * Shared events test fixture: an event filter that always fails, used to
 * verify that a failing filter does not prevent other subscribers from
 * receiving the event.
 */
public class ThrowingComponentAEventsFilter {

	int receivedCount;

	public boolean filterCreate(final String data) {
		this.receivedCount++;
		throw new IllegalStateException("expected filter failure");
	}

}
