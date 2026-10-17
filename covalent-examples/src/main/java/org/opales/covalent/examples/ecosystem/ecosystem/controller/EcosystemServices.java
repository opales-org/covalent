/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */

package org.opales.covalent.examples.ecosystem.ecosystem.controller;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Public services driving the ecosystem simulation, called each tick by
 * SimulationRunner, exactly like OrbitalSystemServices in the Solar System
 * Simulation example.
 */
@Services
public interface EcosystemServices {

    /**
     * Advances the simulation by one stochastic step: draws the number of births or deaths of
     * each species, records the populations reached and raises the matching population
     * notifications.
     * @param dt duration of the step, in simulation time units
     */
    void advance(double dt);

    /** Restores the initial populations and sets the simulation time back to zero. */
    void reset();
}
