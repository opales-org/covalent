/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalsystem.model.impl;

import org.opales.covalent.examples.solarsystem.orbitalsystem.Body;
import org.opales.covalent.examples.solarsystem.orbitalsystem.model.CloseApproach;
import org.opales.covalent.examples.solarsystem.orbitalsystem.model.CloseApproachEvents;
import org.opales.covalent.examples.solarsystem.orbitalsystem.model.OrbitalModel;
import org.opales.covalent.examples.solarsystem.orbitalsystem.model.OrbitalModelServices;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;

/**
 * The orbital system's Model: holds the current body states and simulation
 * time, and notifies {@link CloseApproachEvents} whenever they change.
 * Carries no physics of its own.
 * <p>
 * Does not implement {@link CloseApproachEvents.CloseApproachEventsSubscribe}:
 * the container performs every subscription itself, filtered ones included,
 * and this Model has nothing to do when a subscriber arrives.
 */
@Implementation
public class OrbitalModelImpl implements OrbitalModel, OrbitalModelServices {

	@ProducedEvents
	private CloseApproachEvents produceCloseApproachEvents;

	private volatile Body[] bodies = new Body[0];

	private volatile double simulationTime;

	@Override
	public void updateBodies(final Body[] bodies, final double simulationTime) {
		this.bodies = bodies;
		this.simulationTime = simulationTime;
		this.produceCloseApproachEvents.onSystemAdvanced(bodies, simulationTime);
	}

	@Override
	public void recordCloseApproach(final CloseApproach approach) {
		this.produceCloseApproachEvents.onCloseApproachDetected(approach);
	}

	@Override
	public Body[] getBodies() {
		return this.bodies;
	}

	@Override
	public double getSimulationTime() {
		return this.simulationTime;
	}
}
