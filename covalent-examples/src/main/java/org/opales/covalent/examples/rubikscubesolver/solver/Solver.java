/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.solver;

import org.opales.covalent.examples.rubikscubesolver.solver.controller.SolverController;
import org.opales.covalent.examples.rubikscubesolver.solver.impl.SolverImpl;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the Solver MVIC (Model/Controller/Interaction/View),
 * plus its two solving-strategy provider components. See {@code DatasetExplorer}
 * in the Neural Network Trainer demo for what a bare {@code @Component} tag
 * interface means in general; as there, this type is never used to look up
 * a service or event contract, it exists only as the deployment root, see
 * {@link SolverImpl}.
 * <p>
 * Uses the external {@link org.opales.covalent.examples.rubikscubesolver.cube.CubeServices}
 * to drive the cube and {@link org.opales.covalent.examples.rubikscubesolver.cube.CubeEvents}
 * to trace its moves; see {@link SolverController} and
 * {@code SolverInteraction} for the details of each.
 */
@Component
public interface Solver {

}
