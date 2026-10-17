/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.cockpit.model;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Reads the current state of the cockpit model. The notifications of
 * {@link CockpitEvents} carry no state: a consumer reads it here when it
 * handles them, so it always renders the latest state, whatever order the
 * notifications arrive in.
 */
@Services
public interface CockpitModelReadServices {

	/**
	 * Returns the latest snapshot.
	 *
	 * @return the latest snapshot, {@code null} before the first one
	 */
	CockpitSnapshot getSnapshot();

	/**
	 * Returns whether a container is observed right now.
	 *
	 * @return {@code true} while connected to the observed container
	 */
	boolean isConnected();
}
