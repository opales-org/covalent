/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */

package org.opales.covalent.examples.ecosystem.ecosystem.model;

/**
 * Trophic role of a species within the ecosystem.
 */
public enum Role {
    /** Species that grows on its own and is consumed by prey. */
    RESOURCE,
    /** Species that feeds on a resource and is consumed by predators. */
    PREY,
    /** Species that feeds on prey. */
    PREDATOR
}
