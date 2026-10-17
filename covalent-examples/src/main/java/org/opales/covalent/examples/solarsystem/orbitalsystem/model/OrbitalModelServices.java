/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalsystem.model;

import org.opales.covalent.examples.solarsystem.orbitalsystem.Body;
import org.opales.covalent.examples.solarsystem.orbitalsystem.controller.OrbitalController;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Records the orbital system's current body states, provided by
 * {@code OrbitalModel} and used by {@code OrbitalController}. The Model
 * carries no physics of its own: each method here just stores what it is
 * given and fires the matching {@link CloseApproachEvents} notification.
 */
@Services
public interface OrbitalModelServices {

	/**
	 * Replaces every body's state, and fires
	 * {@link CloseApproachEvents#onSystemAdvanced(Body[], double)}.
	 *
	 * @param bodies         every body's new state
	 * @param simulationTime the simulation time after this step
	 */
	void updateBodies(Body[] bodies, double simulationTime);

	/**
	 * Records a detected close approach, and fires
	 * {@link CloseApproachEvents#onCloseApproachDetected(CloseApproach)}.
	 *
	 * @param approach the detected close approach
	 */
	void recordCloseApproach(CloseApproach approach);

	/**
	 * Returns the bodies as last stored by {@link #updateBodies(Body[], double)}.
	 *
	 * @return every body's current state
	 */
	Body[] getBodies();

	/**
	 * Returns the simulation time as last stored by {@link #updateBodies(Body[], double)}.
	 *
	 * @return the current simulation time
	 */
	double getSimulationTime();
}
