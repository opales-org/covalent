/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalsystem.controller;

import org.opales.covalent.examples.solarsystem.orbitalsystem.Body;
import org.opales.covalent.examples.solarsystem.orbitalsystem.interaction.OrbitalActionKey;

/**
 * Derived, display-ready details about one body, computed by
 * {@code OrbitalController} for the interactive "select a body" preview
 * (see {@code OrbitalActionKey#SelectBody}) - distinct from {@link Body}
 * itself, which is the Model's raw stored state.
 *
 * @param name             the body's display name
 * @param mass             the body's mass
 * @param radius           the body's radius
 * @param distanceFromSun  the body's current distance from the system's
 *                         central body (name {@code "Sun"})
 * @param speed            the body's current speed (velocity magnitude)
 */
public record BodyDetails(String name, double mass, double radius, double distanceFromSun, double speed) {
}
