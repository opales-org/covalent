/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.solver.view;

import org.opales.covalent.examples.rubikscubesolver.cube.Move;
import org.opales.covalent.examples.rubikscubesolver.cube.SolveProgress;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Piloting commands for the solver view, provided by {@code SolverView} and
 * used by {@code SolverInteraction} to toggle the move history panel and
 * feed it. The view shows a solve's progress and outcome itself, from the
 * solver's own events.
 */
@Services
public interface SolverViewServices {

	/**
	 * Shows the progress of a running solve in the view's progress label.
	 * Called by the view itself when it receives the solver's progress event.
	 *
	 * @param progress the current progress of a running solve, to display
	 */
	void displayProgress(SolveProgress progress);

	/**
	 * Shows that a solution was found, with its length.
	 * Called by the view itself when it receives the solver's completion event.
	 *
	 * @param moves the found solution to display, once a solve completes
	 */
	void displaySolution(Move[] moves);

	/**
	 * Shows or hides the move history panel. Also drives whether
	 * {@code SolverInteraction} is subscribed to the cube's own move
	 * events, see its javadoc.
	 *
	 * @param visible {@code true} to show the panel
	 */
	void setMoveHistoryVisible(boolean visible);

	/**
	 * Appends one entry to the move history panel's live trace; it is the call
	 * {@code SolverInteraction} uses to feed that trace from the cube's own state-change events
	 * while the panel is open.
	 * <p>
	 * Takes a pre-formatted {@code String}, not a {@link Move}: {@code CubeEvents#stateChanged} only
	 * carries the resulting {@code CubeState} snapshot, not the move that produced it, so
	 * {@code SolverInteraction} can only trace that a change happened, not decode which move caused
	 * it.
	 *
	 * @param entry a short, human-readable description of the change
	 */
	void appendMoveToHistory(String entry);
}
