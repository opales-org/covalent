/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.cube;

import org.opales.covalent.examples.rubikscubesolver.cube.controller.impl.CubeControllerImpl;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Write access to the cube, provided by {@code CubeController} and used
 * externally by {@code SolverController} to apply a found solution's moves,
 * to scramble the cube before a demo run, and to reset it back to solved.
 * Wider than {@link CubeReadServices}, which it extends: see that
 * interface's javadoc for why the two are kept separate even though both
 * are implemented by {@link CubeControllerImpl} from the same state.
 * <p>
 * {@link #applyMove(Move)} is the public, programmatic entry point used by
 * {@code SolverController} to play out a solution one move at a time -
 * distinct from {@code CubeControllerServices#rotateFace(Face, RotationDirection)},
 * this function's own internal entry point for interactive, mouse-driven
 * rotation from {@code CubeInteraction}. Both ultimately apply a
 * {@link Move} to the same {@link CubeState}, through
 * {@link CubeControllerImpl}.
 * <p>
 * {@link #resetToSolved()} lives here rather than on
 * {@code CubeControllerServices} because abandoning a scramble or a solve
 * attempt to start over is {@code Solver}'s call, not the cube's own
 * business - see {@code SolverControllerServices#reset()}'s javadoc.
 */
@Services
public interface CubeServices extends CubeReadServices {

	/**
	 * Applies one move to the cube, firing {@link CubeEvents#stateChanged(CubeState)}.
	 *
	 * @param move the move to apply
	 */
	void applyMove(Move move);

	/**
	 * Scrambles the cube with a reproducible sequence of random moves.
	 *
	 * @param seed the random seed to scramble with
	 */
	void scramble(long seed);

	/**
	 * Resets the cube straight back to a freshly solved state.
	 */
	void resetToSolved();
}
