/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.cube.model;

import org.opales.covalent.examples.rubikscubesolver.cube.CubeEvents;
import org.opales.covalent.examples.rubikscubesolver.cube.CubeState;
import org.opales.covalent.examples.rubikscubesolver.cube.controller.CubeController;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Records the cube's current state, provided by {@code CubeModel} and used
 * by {@link CubeController}. The Model carries no move logic of its own -
 * that lives in {@link CubeState#applyMove(org.opales.covalent.examples.rubikscubesolver.cube.Move)},
 * called by {@code CubeController} before it pushes the result here: this
 * interface just stores whatever state it is given and fires
 * {@link CubeEvents#stateChanged(CubeState)}.
 * <p>
 * See {@code DatasetModelServices} in the Neural Network Trainer demo for
 * the fuller rationale behind this Services/Events split: {@code setState}
 * is the synchronous, call-and-return contract used by the one component
 * that needs the write to have happened before it proceeds
 * ({@code CubeController}); {@code stateChanged} is the fire-and-forget
 * counterpart broadcast to any number of consumers.
 */
@Services
public interface CubeModelServices {

	/**
	 * Reads the state last stored through {@link #setState(CubeState)}.
	 *
	 * @return the cube's current state
	 */
	CubeState getState();

	/**
	 * Replaces the stored state, and fires
	 * {@link CubeEvents#stateChanged(CubeState)}.
	 *
	 * @param state the new state
	 */
	void setState(CubeState state);
}
