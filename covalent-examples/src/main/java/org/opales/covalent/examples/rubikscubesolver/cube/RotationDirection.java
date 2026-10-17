/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.cube;

/**
 * How far and which way a {@link Face} turns for one {@link Move}: a
 * quarter turn in either direction, or a half turn (equivalent to two
 * quarter turns in the same direction, provided here as its own value so a
 * solver can express it as a single move rather than two).
 */
public enum RotationDirection {

	/** Quarter turn clockwise, as seen by a viewer looking at the turned face from outside the cube. */
	CLOCKWISE,

	/** Quarter turn counterclockwise, as seen from outside the cube. */
	COUNTERCLOCKWISE,

	/** Half turn (180 degrees), identical in either direction. */
	HALF_TURN;
}
