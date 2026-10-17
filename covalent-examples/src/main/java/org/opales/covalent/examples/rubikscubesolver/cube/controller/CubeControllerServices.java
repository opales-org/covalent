/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.cube.controller;

import org.opales.covalent.examples.rubikscubesolver.cube.CubeServices;
import org.opales.covalent.examples.rubikscubesolver.cube.Face;
import org.opales.covalent.examples.rubikscubesolver.cube.RotationDirection;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Interactive, mouse-driven rotation, provided by {@code CubeController}
 * and used only by {@code CubeInteraction} in response to a click or
 * drag on the cube view. Internal to the {@code Cube} function, distinct
 * from the public {@link CubeServices#applyMove(org.opales.covalent.examples.rubikscubesolver.cube.Move)},
 * which {@code SolverController} uses to play out a found solution
 * programmatically - see that interface's javadoc for why the two entry
 * points are kept separate even though both ultimately apply the same kind
 * of {@code Move} to the same state, through the same implementation class,
 * {@code CubeControllerImpl}.
 */
@Services
public interface CubeControllerServices {

	/**
	 * Rotates one face, exactly like {@link CubeServices#applyMove}, but as
	 * this function's own internal, interactive entry point.
	 *
	 * @param face      the face to rotate
	 * @param direction which way to rotate it
	 */
	void rotateFace(Face face, RotationDirection direction);
}
