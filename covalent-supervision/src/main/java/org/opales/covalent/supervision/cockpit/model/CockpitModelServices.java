/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.cockpit.model;

import org.opales.covalent.container.component.annotations.Services;

/** Publishes the cockpit's figures. */
@Services
public interface CockpitModelServices {

	/**
	 * Publishes the figures through {@link CockpitEvents#onSnapshotChanged()}.
	 *
	 * @param snapshot the figures
	 */
	void setSnapshot(CockpitSnapshot snapshot);

	/**
	 * Publishes whether a container is observed right now.
	 *
	 * @param connected {@code true} while connected to the observed container
	 */
	void setConnected(boolean connected);
}
