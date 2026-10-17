/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */

package org.opales.covalent.examples.ecosystem.ecosystem;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the central ecosystem composite: owns species
 * populations, growth rates and the interaction matrix, and runs the
 * stochastic tau-leaping step advancing them.
 */
@Component
public interface Ecosystem {
}
