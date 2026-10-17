/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.solver.human;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the layer-by-layer ("human") solving strategy provider.
 * Not part of the Solver's MVIC quartet: a plain provider component, composed
 * into {@code SolverImpl} alongside the Model, View and Interaction, and used
 * only by {@code SolverController} through {@link HumanSolvingServices}.
 */
@Component
public interface HumanSolver {

}
