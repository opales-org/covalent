/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.solver.model;

import org.opales.covalent.container.component.annotations.Parameters;

/**
 * Tunable setting of the solution playback, offered by {@code SolverModel}:
 * how fast a found solution is played onto the cube, one move at a time.
 */
@Parameters
public interface SolverPlaybackParameters {

	/**
	 * Returns the playback speed.
	 *
	 * @return the number of moves played per second
	 */
	int getMovesPerSecond();

	/**
	 * Sets the playback speed, taking effect from the next move played.
	 *
	 * @param movesPerSecond the number of moves played per second, expected to be positive
	 */
	void setMovesPerSecond(int movesPerSecond);
}
