/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalwatch.model.impl;

import org.opales.covalent.examples.solarsystem.orbitalsystem.model.CloseApproach;
import org.opales.covalent.examples.solarsystem.orbitalwatch.model.OrbitalWatchEvents;
import org.opales.covalent.examples.solarsystem.orbitalwatch.model.OrbitalWatchModel;
import org.opales.covalent.examples.solarsystem.orbitalwatch.model.OrbitalWatchModelServices;

import java.util.ArrayList;
import java.util.List;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;

/**
 * The watch's Model: holds the log of every close approach recorded so far,
 * and notifies {@link OrbitalWatchEvents} whenever it changes. Carries no
 * logic of its own.
 */
@Implementation
public class OrbitalWatchModelImpl implements OrbitalWatchModel, OrbitalWatchModelServices {

	@ProducedEvents
	private OrbitalWatchEvents produceOrbitalWatchEvents;

	private final List<CloseApproach> approaches = new ArrayList<>();

	@Override
	public synchronized void recordApproach(final CloseApproach approach) {
		this.approaches.add(approach);
		this.produceOrbitalWatchEvents.onApproachRecorded(approach);
	}

	@Override
	public synchronized void clearLog() {
		this.approaches.clear();
		this.produceOrbitalWatchEvents.onLogCleared();
	}

	@Override
	public synchronized CloseApproach[] getRecordedApproaches() {
		return this.approaches.toArray(new CloseApproach[0]);
	}
}
