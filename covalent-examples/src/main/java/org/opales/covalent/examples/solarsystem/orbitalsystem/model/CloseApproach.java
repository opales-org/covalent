/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalsystem.model;

import org.opales.covalent.examples.solarsystem.hazardalert.HazardAlert;
import org.opales.covalent.examples.solarsystem.orbitalsystem.OrbitalSystem;
import org.opales.covalent.examples.solarsystem.orbitalsystem.controller.OrbitalController;
import org.opales.covalent.examples.solarsystem.orbitalsystem.controller.impl.OrbitalControllerImpl;

/**
 * One detected close approach between two bodies: fired once per approach
 * (on the step where their distance first drops below
 * {@code OrbitalController}'s own baseline "notable" threshold), not once
 * per step for as long as they stay close - see
 * {@code OrbitalControllerImpl}'s pair-transition tracking.
 *
 * @param bodyAName       the first body's name
 * @param bodyBName       the second body's name
 * @param distance        the distance between the two bodies at detection time
 * @param bodyARadius     the first body's radius, carried in the payload so
 *                        consumers (e.g. {@code HazardAlert}'s filter) do not
 *                        need to query {@code OrbitalSystem} back
 * @param bodyBRadius     the second body's radius
 * @param simulationTime  the simulation time at which the approach was detected
 */
public record CloseApproach(
		String bodyAName, String bodyBName, double distance,
		double bodyARadius, double bodyBRadius, double simulationTime) {
}
