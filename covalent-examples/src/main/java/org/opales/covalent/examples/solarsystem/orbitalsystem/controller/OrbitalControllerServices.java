/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalsystem.controller;

import org.opales.covalent.examples.solarsystem.orbitalsystem.OrbitalSystem;
import org.opales.covalent.examples.solarsystem.orbitalsystem.interaction.OrbitalInteraction;
import org.opales.covalent.examples.solarsystem.simulationrunner.SimulationRunner;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Interactive preview operations, provided by {@code OrbitalController} and
 * used by {@code OrbitalInteraction}. Internal to the
 * {@code OrbitalSystem} function - distinct from the public
 * {@link OrbitalSystemServices}, used externally by
 * {@code SimulationRunner}.
 */
@Services
public interface OrbitalControllerServices {

	/**
	 * Computes derived, display-ready details for one body, for the
	 * interactive "select a body" preview - without affecting the
	 * simulation in any way.
	 *
	 * @param bodyName the selected body's name
	 * @return that body's current details, or {@code null} if no body with
	 *         that name currently exists
	 */
	BodyDetails getBodyDetails(String bodyName);

	/**
	 * Adds one comet to the simulation, on a randomly generated but
	 * strongly eccentric orbit crossing every planet's orbit - triggered on
	 * demand from the orrery view's "Add Comet" button, rather than left to
	 * accidental numerical drift.
	 */
	void addComet();
}
