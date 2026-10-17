/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalwatch.controller.impl;

import org.opales.covalent.examples.solarsystem.orbitalsystem.Body;
import org.opales.covalent.examples.solarsystem.orbitalsystem.OrbitalSystem;
import org.opales.covalent.examples.solarsystem.orbitalsystem.model.CloseApproach;
import org.opales.covalent.examples.solarsystem.orbitalsystem.model.CloseApproachEvents;
import org.opales.covalent.examples.solarsystem.orbitalwatch.controller.OrbitalWatchController;
import org.opales.covalent.examples.solarsystem.orbitalwatch.controller.OrbitalWatchControllerServices;
import org.opales.covalent.examples.solarsystem.orbitalwatch.model.OrbitalWatchModelServices;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;

/**
 * The watch's Controller: subscribes to {@link CloseApproachEvents} with no
 * filter (managed autowiring - implementing the interface is enough, no
 * manual subscription needed) and records every detected approach. Ignores
 * {@link #onSystemAdvanced(Body[], double)}: continuous positions are
 * {@code OrbitalSystem}'s own View's concern, not this function's.
 */
@Implementation
public class OrbitalWatchControllerImpl implements OrbitalWatchController, OrbitalWatchControllerServices,
		CloseApproachEvents {

	@UsedServices
	private OrbitalWatchModelServices useOrbitalWatchModelServices;

	@Override
	public void onSystemAdvanced(final Body[] bodies, final double simulationTime) {
		// Not this function's concern - see class javadoc.
	}

	@Override
	public void onCloseApproachDetected(final CloseApproach approach) {
		this.useOrbitalWatchModelServices.recordApproach(approach);
	}

	@Override
	public void clear() {
		this.useOrbitalWatchModelServices.clearLog();
	}
}
