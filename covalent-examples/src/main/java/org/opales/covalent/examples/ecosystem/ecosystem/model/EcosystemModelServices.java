/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */

package org.opales.covalent.examples.ecosystem.ecosystem.model;

import java.util.List;
import java.util.Map;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Pure state-store services for the ecosystem's populations: records the
 * outcome of a simulation step and exposes the current state. Holds no
 * simulation logic of its own; the tau-leaping step lives entirely in
 * EcosystemController.
 */
@Services
public interface EcosystemModelServices {

    /**
     * Stores the species taking part in the simulation.
     * @param species the species, in display order
     */
    void setSpecies(List<Species> species);

    /**
     * Records the populations reached after a step or a reset, with the simulation time, and
     * notifies
     * the change.
     * @param populations population by species name
     * @param simulationTime simulation time reached
     */
    void updatePopulations(Map<String, Double> populations, double simulationTime);

    /**
     * Returns a copy of the current populations.
     * @return population by species name
     */
    Map<String, Double> getPopulations();

    /**
     * Returns a copy of the species list.
     * @return the species, in display order
     */
    List<Species> getSpecies();

    /**
     * Returns the simulation time of the last recorded populations.
     * @return the simulation time, in simulation time units
     */
    double getSimulationTime();
}
