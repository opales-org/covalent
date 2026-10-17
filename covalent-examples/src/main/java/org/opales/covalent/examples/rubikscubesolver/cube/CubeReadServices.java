/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.cube;

import org.opales.covalent.examples.rubikscubesolver.cube.controller.impl.CubeControllerImpl;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Read-only access to the cube's current state, provided by
 * {@code CubeController} and used by any function that only needs to know
 * what the cube looks like right now (e.g. {@code UserConfiguration}'s
 * "solved" indicator).
 * <p>
 * {@link CubeServices} extends this interface (see
 * {@code doc/managed-components.md}'s Extending Services section) so a
 * read-only caller can depend on exactly the narrow surface it needs,
 * without visibility into, or accidental coupling to, the wider
 * write-capable {@link CubeServices} that {@code Solver} uses to drive the
 * cube. Both are provided by {@link CubeControllerImpl} from the same
 * underlying state.
 */
@Services
public interface CubeReadServices {

	/**
	 * Reads the cube's state at the moment of the call.
	 *
	 * @return the cube's current facelet state
	 */
	CubeState getState();

	/**
	 * Tells whether the cube is solved, that is whether every face shows a single color.
	 *
	 * @return {@code true} if the cube is currently solved
	 */
	boolean isSolved();
}
