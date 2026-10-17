/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.cockpit.model;

import org.opales.covalent.container.component.annotations.Events;

/**
 * The cockpit's state changed. The notifications carry no state: it is read
 * through {@link CockpitModelReadServices} when they are handled.
 */
@Events
public interface CockpitEvents {

	/** Notifies that the cockpit's figures changed. */
	void onSnapshotChanged();

	/** Notifies that the connection to the observed container changed. */
	void onConnectionChanged();
}
