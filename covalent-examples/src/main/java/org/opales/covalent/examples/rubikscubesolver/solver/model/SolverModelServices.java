/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.solver.model;

import org.opales.covalent.examples.rubikscubesolver.cube.Move;
import org.opales.covalent.examples.rubikscubesolver.cube.SolveProgress;
import org.opales.covalent.examples.rubikscubesolver.cube.SolvingAlgorithm;
import org.opales.covalent.examples.rubikscubesolver.solver.controller.SolverController;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Records the solver's current selected algorithm, last known progress and
 * last found solution, provided by {@code SolverModel} and used by
 * {@link SolverController}. Carries no logic of its own: every setter here
 * just stores the given value and fires the matching
 * {@code SolverEvents} notification (see {@code SolverModelImpl}).
 * <p>
 * {@link SolverController} turns every {@link org.opales.covalent.examples.rubikscubesolver.cube.ProgressReporter}
 * callback invocation from whichever strategy is currently running into a
 * {@link #setProgress(SolveProgress)} call here, keeping this Model the
 * single producer of {@code SolverEvents} (see
 * {@link org.opales.covalent.examples.rubikscubesolver.cube.ProgressReporter}'s
 * own javadoc for why that callback is a plain Java interface rather than a
 * shared Covalent events contract).
 */
@Services
public interface SolverModelServices {

	/**
	 * Records a progress update, and fires
	 * {@code SolverEvents#progressUpdated(SolveProgress)}.
	 *
	 * @param progress the current progress
	 */
	void setProgress(SolveProgress progress);

	/**
	 * Records a found solution, and fires
	 * {@code SolverEvents#solveCompleted(Move[])}.
	 *
	 * @param moves the solution, as a sequence of moves to apply in order
	 */
	void setSolution(Move[] moves);

	/**
	 * Reads the solution last recorded through {@link #setSolution(Move[])}.
	 *
	 * @return the last recorded solution, or an empty array if none yet
	 */
	Move[] getSolution();

	/**
	 * Reads the algorithm chosen for the next solve.
	 *
	 * @return the currently selected solving algorithm
	 */
	SolvingAlgorithm getSelectedAlgorithm();

	/**
	 * Records the algorithm to use for the next solve.
	 *
	 * @param algorithm the algorithm to select for the next solve
	 */
	void setSelectedAlgorithm(SolvingAlgorithm algorithm);
}
