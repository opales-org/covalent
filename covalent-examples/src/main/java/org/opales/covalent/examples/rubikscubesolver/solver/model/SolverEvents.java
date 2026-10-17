/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.solver.model;

import org.opales.covalent.examples.rubikscubesolver.cube.Move;
import org.opales.covalent.examples.rubikscubesolver.cube.SolveProgress;
import org.opales.covalent.examples.rubikscubesolver.solver.model.impl.SolverModelImpl;

import org.opales.covalent.container.component.annotations.Events;

/**
 * Solver progress and completion notifications, produced by
 * {@code SolverModel} and consumed by {@code SolverView}. Unlike
 * {@code CubeEvents}, this interface stays entirely internal to the
 * {@code Solver} function: it has no cross-function subscriber, so
 * {@link SolverModelImpl} implements no subscription delegation for it -
 * {@code SolverView} only ever needs the next update, not whatever solve
 * happened to be in progress before it activated (a solve is short-lived
 * and user-triggered, unlike the cube's own persistent state).
 */
@Events
public interface SolverEvents {

	/**
	 * Fired on every progress update from the currently running solve.
	 *
	 * @param progress the current progress
	 */
	void progressUpdated(SolveProgress progress);

	/**
	 * Fired once a solve completes successfully.
	 *
	 * @param moves the found solution, as a sequence of moves to apply in
	 *              order
	 */
	void solveCompleted(Move[] moves);
}
