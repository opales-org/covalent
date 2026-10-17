/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalsystem;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the orbital system MVIC (Model/Controller/
 * Interaction/View). Owns every celestial body and the gravitational
 * computation as a single component, deliberately not split per body - the
 * same reasoning as the neural network demo's {@code NeuralNetworkModel}
 * not being split per layer.
 */
@Component
public interface OrbitalSystem {

}
