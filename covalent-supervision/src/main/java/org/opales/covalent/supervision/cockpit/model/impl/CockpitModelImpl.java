/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.cockpit.model.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.supervision.cockpit.model.CockpitEvents;
import org.opales.covalent.supervision.cockpit.model.CockpitModel;
import org.opales.covalent.supervision.cockpit.model.CockpitModelReadServices;
import org.opales.covalent.supervision.cockpit.model.CockpitModelServices;
import org.opales.covalent.supervision.cockpit.model.CockpitSnapshot;

/**
 * Holds the cockpit's latest snapshot and whether a container is connected, and notifies each change: the
 * controller rebuilds the figures every second.
 */
@Implementation
public class CockpitModelImpl implements CockpitModel, CockpitModelServices, CockpitModelReadServices {

	/** Creates the model of the cockpit function. */
	public CockpitModelImpl() {
		// Nothing to initialize.
	}

	@ProducedEvents
	private CockpitEvents produceCockpitEvents;

	private volatile CockpitSnapshot snapshot;

	private volatile boolean connected;

	@Override
	public void setSnapshot(final CockpitSnapshot snapshot) {
		this.snapshot = snapshot;
		this.produceCockpitEvents.onSnapshotChanged();
	}

	@Override
	public void setConnected(final boolean connected) {
		this.connected = connected;
		this.produceCockpitEvents.onConnectionChanged();
	}

	@Override
	public CockpitSnapshot getSnapshot() {
		return this.snapshot;
	}

	@Override
	public boolean isConnected() {
		return this.connected;
	}
}
