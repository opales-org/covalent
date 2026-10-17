/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.cube.interaction;

import org.opales.covalent.examples.rubikscubesolver.cube.interaction.impl.CubeInteractionImpl;

import org.opales.covalent.hmi.action.ActionKey;

/**
 * Action identifiers registered by {@code CubeInteraction} with the
 * HMI's {@code ActionManager}, and executed from the cube view's controls.
 * See {@code DatasetActionKey} in the Neural Network Trainer demo for what
 * an {@code ActionKey} enum constant is and why this indirection is used
 * instead of the View calling {@link CubeInteractionImpl} directly.
 */
public enum CubeActionKey implements ActionKey {

	/**
	 * A face was clicked directly on the unfolded cube net (carries a
	 * {@code Face} and a {@code RotationDirection}).
	 */
	RotateFace,

	/**
	 * The "Scramble" button (carries a {@code Long} random seed). There is
	 * no {@code Reset} constant here: that button, and its handling, both
	 * belong to {@code Solver} - see {@code SolverActionKey#Reset}'s
	 * javadoc.
	 */
	Scramble;
}
