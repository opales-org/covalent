/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import org.opales.covalent.container.tests.unit.rawcomponents.componenta.ComponentAEvents;

/**
 * Shared events test fixture: a {@code ComponentAEvents} consumer that only
 * counts what it receives, never fails. Used across several event tests
 * wherever a well-behaved consumer is needed alongside a failing or filtered
 * one.
 */
public class RecordingComponentAEventsConsumer implements ComponentAEvents {

	int receivedCount;

	int emptyReceivedCount;

	@Override
	public void onCreate(final String data) {
		this.receivedCount++;
	}

	@Override
	public void onUpdate(final String data) {
	}

	@Override
	public void onDelete(final String data) {
	}

	@Override
	public void onEmpty() {
		this.emptyReceivedCount++;
	}

}
