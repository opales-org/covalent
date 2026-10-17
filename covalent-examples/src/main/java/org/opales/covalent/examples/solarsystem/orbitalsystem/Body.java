/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalsystem;

import org.opales.covalent.examples.solarsystem.hazardalert.HazardAlert;
import org.opales.covalent.examples.solarsystem.orbitalsystem.controller.OrbitalController;

/**
 * A single celestial body's full physical state at one instant: mass and
 * radius (fixed for a body's lifetime), and position/velocity (updated
 * every simulation step). Immutable - {@code OrbitalController} replaces the
 * whole array wholesale on every step, mirroring how
 * {@code DatasetControllerImpl} replaces the whole sample array on every
 * regeneration.
 *
 * @param name   the body's display name, unique within one simulation
 * @param mass   the body's mass, in this demo's own toy unit system (not SI)
 * @param radius the body's radius, in the same toy unit system - also used
 *               as this body's "size" for {@code HazardAlert}'s filter
 * @param x      the body's X position
 * @param y      the body's Y position
 * @param vx     the body's X velocity
 * @param vy     the body's Y velocity
 */
public record Body(String name, double mass, double radius, double x, double y, double vx, double vy) {
}
