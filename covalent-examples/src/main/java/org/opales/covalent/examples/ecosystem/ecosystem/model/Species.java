/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */

package org.opales.covalent.examples.ecosystem.ecosystem.model;

/**
 * A species participating in the ecosystem: its name and trophic role.
 * @param name the species name, which also keys the population and rate maps
 * @param role the trophic role of the species
 */
public record Species(String name, Role role) {
}
