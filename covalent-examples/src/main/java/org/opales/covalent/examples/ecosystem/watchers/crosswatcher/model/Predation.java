/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.crosswatcher.model;

/**
 * One logged predation: which species consumed which, and when.
 * @param predatorSpecies name of the consuming species
 * @param preySpecies name of the consumed species
 * @param simulationTime simulation time at which it happened
 */
public record Predation(String predatorSpecies, String preySpecies, double simulationTime) {
}
