/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.solver.human.impl;

import org.opales.covalent.examples.rubikscubesolver.cube.CubeState;
import org.opales.covalent.examples.rubikscubesolver.cube.Move;
import org.opales.covalent.examples.rubikscubesolver.cube.ProgressReporter;
import org.opales.covalent.examples.rubikscubesolver.solver.human.HumanSolver;
import org.opales.covalent.examples.rubikscubesolver.solver.human.HumanSolvingServices;

import org.opales.covalent.container.component.annotations.Implementation;

/**
 * The layer-by-layer ("human") solving strategy: provides
 * {@link HumanSolvingServices}. The method itself lives in
 * {@link LayerByLayerSolver}; this class is the component wrapper.
 */
@Implementation
public class HumanSolverImpl implements HumanSolver, HumanSolvingServices {

	@Override
	public Move[] solve(final CubeState cubeState, final ProgressReporter progressReporter) {
		return new LayerByLayerSolver(progressReporter).solve(cubeState);
	}
}
