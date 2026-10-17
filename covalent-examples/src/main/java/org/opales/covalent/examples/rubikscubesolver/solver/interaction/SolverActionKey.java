/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.solver.interaction;

import org.opales.covalent.examples.rubikscubesolver.cube.SolvingAlgorithm;
import org.opales.covalent.examples.rubikscubesolver.solver.interaction.impl.SolverInteractionImpl;

import org.opales.covalent.hmi.action.ActionKey;

/**
 * Action identifiers registered by {@code SolverInteraction} with the
 * HMI's {@code ActionManager}, and executed from the solver view's
 * controls. See {@code DatasetActionKey} in the Neural Network Trainer demo
 * for what an {@code ActionKey} enum constant is and why this indirection is
 * used instead of the View calling {@link SolverInteractionImpl}
 * directly.
 */
public enum SolverActionKey implements ActionKey {

	/** The algorithm picker (carries a {@code SolvingAlgorithm}). */
	SelectAlgorithm,

	/** The "Solve" button. */
	StartSolve,

	/** The "Cancel" button. */
	CancelSolve,

	/**
	 * The move history panel's visibility toggle (carries a {@code Boolean}):
	 * see {@code SolverInteractionImpl} for why this is the one action
	 * in this whole demo that also drives manual event subscription.
	 */
	ToggleMoveHistory,

	/**
	 * The "Reset" button (no arguments): brings the cube straight back to
	 * solved, cancelling any solve currently running first. Built and
	 * registered entirely on Solver's side - {@code SolverViewImpl} owns
	 * the widget, {@code SolverInteractionImpl} handles it: whether
	 * to abandon the current attempt and start over is a call about the
	 * solving session, which belongs to {@code Solver} - the cube's player
	 * - and not to the cube itself, which has no notion of an "attempt" at
	 * all. Nothing about where a widget happens to render decides which
	 * function it belongs to; here the two even coincide, since
	 * {@code HmiBuilder} simply places each function's whole root as one
	 * block.
	 */
	Reset;
}
