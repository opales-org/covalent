/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalsystem.controller;

import org.opales.covalent.examples.solarsystem.simulationrunner.SimulationRunner;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Simulation-driving operations on the orbital system (advance and reset), provided
 * by {@code OrbitalController} and used externally by
 * {@code SimulationRunner}. Public interface: distinct from
 * {@link OrbitalControllerServices}, used only by this function's own
 * Interaction Controller. Physics-internal detail (how gravity and close-approach
 * detection actually work) stays inside the composite and is intentionally
 * not part of this contract.
 */
@Services
public interface OrbitalSystemServices {

	/**
	 * Advances the simulation by one physics step: computes gravity between
	 * every pair of bodies, integrates positions and velocities, and detects
	 * any new close approach.
	 *
	 * @param dt the step's duration, in simulation time units
	 */
	void advance(double dt);

	/**
	 * Reinitializes the orbital system to its canonical starting
	 * configuration (fresh bodies, simulation time back to 0).
	 */
	void reset();
}
