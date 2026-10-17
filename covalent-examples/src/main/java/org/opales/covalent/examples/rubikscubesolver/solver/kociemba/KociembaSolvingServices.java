/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.solver.kociemba;

import org.opales.covalent.examples.rubikscubesolver.cube.CubeState;
import org.opales.covalent.examples.rubikscubesolver.cube.Move;
import org.opales.covalent.examples.rubikscubesolver.cube.ProgressReporter;

import org.opales.covalent.container.component.annotations.Services;

/**
 * The Kociemba two-phase strategy's solving operation, provided by
 * {@code KociembaSolver} and used only by {@code SolverController}.
 * Internal to the {@code Solver} function: distinct from
 * its sibling strategy's own interface, since Covalent does not allow two
 * different components to provide the same {@code Services} interface.
 */
@Services
public interface KociembaSolvingServices {

	/**
	 * Searches for a sequence of moves that solves {@code cubeState},
	 * reporting progress as it goes.
	 *
	 * @param cubeState        the cube state to solve, not mutated
	 * @param progressReporter callback invoked with progress updates while
	 *                         the search runs
	 * @return the found solution, as a sequence of moves to apply in order
	 */
	Move[] solve(CubeState cubeState, ProgressReporter progressReporter);
}
