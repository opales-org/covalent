/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */

package org.opales.covalent.examples.ecosystem.ecosystem.model;

import java.util.List;
import java.util.Map;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Public, read-only services exposing the ecosystem's current population
 * statistics, for consumption outside the container (see StatsExporter).
 */
@Services
public interface EcosystemStatsServices {

    /**
     * Returns the species of the ecosystem.
     * @return the species, in display order
     */
    List<Species> getSpecies();

    /**
     * Returns the current populations.
     * @return population by species name
     */
    Map<String, Double> getPopulations();
}
