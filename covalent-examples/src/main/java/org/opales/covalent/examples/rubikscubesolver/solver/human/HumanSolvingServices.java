/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.solver.human;

import org.opales.covalent.examples.rubikscubesolver.cube.CubeState;
import org.opales.covalent.examples.rubikscubesolver.cube.Move;
import org.opales.covalent.examples.rubikscubesolver.cube.ProgressReporter;

import org.opales.covalent.container.component.annotations.Services;

/**
 * The layer-by-layer strategy's solving operation, provided by
 * {@code HumanSolver} and used only by {@code SolverController}. Internal to
 * the {@code Solver} function, a sibling of the other strategies' own
 * interfaces.
 */
@Services
public interface HumanSolvingServices {

	/**
	 * Solves {@code cubeState} the way a person does with the beginner
	 * method: first-layer cross, first-layer corners, middle-layer edges,
	 * last-layer cross, last-layer edges, last-layer corner positions and
	 * last-layer corner orientations, reporting one progress update per step.
	 *
	 * @param cubeState        the cube state to solve, not mutated
	 * @param progressReporter callback invoked with progress updates while
	 *                         the solution is built
	 * @return the solution, as a sequence of moves to apply in order
	 */
	Move[] solve(CubeState cubeState, ProgressReporter progressReporter);
}
