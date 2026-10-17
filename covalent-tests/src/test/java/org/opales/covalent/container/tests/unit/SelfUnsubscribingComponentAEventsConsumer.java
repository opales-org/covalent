/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.ComponentAEvents;

/**
 * Shared events test fixture: a {@code ComponentAEvents} consumer that
 * unsubscribes itself from within its own {@link #onCreate(String)}
 * callback, used to verify that unsubscribing during dispatch is safe.
 */
public class SelfUnsubscribingComponentAEventsConsumer implements ComponentAEvents {

	private final RawComponentsServices rawComponentsServices;

	private final Class<?> subscriberIdentity;

	int receivedCount;

	public SelfUnsubscribingComponentAEventsConsumer(final RawComponentsServices rawComponentsServices,
			final Class<?> subscriberIdentity) {
		this.rawComponentsServices = rawComponentsServices;
		this.subscriberIdentity = subscriberIdentity;
	}

	@Override
	public void onCreate(final String data) {
		this.receivedCount++;
		this.rawComponentsServices.unsubscribe(this.subscriberIdentity, ComponentAEvents.class, this);
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
