/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */

package org.opales.covalent.examples.ecosystem.ecosystem.controller;

import java.util.Map;

import org.opales.covalent.container.component.annotations.Parameters;

/**
 * Editable growth rates and interaction matrix of the generalized
 * Lotka-Volterra model, accessed live from UserConfiguration.
 */
@Parameters
public interface EcosystemParameters {

    /**
     * Returns a copy of the baseline growth rate of each species.
     * @return growth rate by species name; positive for the resource, negative for consumers
     */
    Map<String, Double> getGrowthRates();

    /**
     * Replaces the growth rates, applied from the next simulation step. The map is copied.
     * @param growthRates growth rate by species name
     */
    void setGrowthRates(Map<String, Double> growthRates);

    /**
     * Returns a deep copy of the interaction matrix. The row of a species holds the
     * coefficients that,
     * multiplied by the population of each other species, are added to its growth rate.
     * @return interaction coefficient by affected species name, then by acting species name
     */
    Map<String, Map<String, Double>> getInteractionMatrix();

    /**
     * Replaces the interaction matrix, applied from the next simulation step.
     * @param interactionMatrix interaction coefficient by affected species name, then by
     *     acting species name
     */
    void setInteractionMatrix(Map<String, Map<String, Double>> interactionMatrix);
}
