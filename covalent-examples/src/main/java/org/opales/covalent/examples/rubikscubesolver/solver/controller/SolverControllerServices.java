/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.solver.controller;

import org.opales.covalent.examples.rubikscubesolver.cube.SolvingAlgorithm;

import org.opales.covalent.container.component.annotations.Services;

/**
 * User-facing solve control, provided by {@code SolverController} and used
 * by {@code SolverInteraction}.
 */
@Services
public interface SolverControllerServices {

	/**
	 * Starts a solve with the given algorithm, off the UI thread (see
	 * {@code SolverControllerImpl} for how {@code AsyncServices} is used).
	 * A solve already in progress is not restarted; call
	 * {@link #cancelSolve()} first.
	 *
	 * @param algorithm the algorithm to solve with
	 */
	void startSolve(SolvingAlgorithm algorithm);

	/**
	 * Cancels the currently running solve, if any; a no-op otherwise.
	 */
	void cancelSolve();

	/**
	 * Abandons the current attempt and starts a fresh one: cancels any
	 * solve in progress, then resets the cube to solved. The single entry
	 * point for the "Reset" button - see {@code SolverActionKey#Reset}'s
	 * javadoc for why this lives here rather than on the cube's own
	 * controller.
	 */
	void reset();
}
