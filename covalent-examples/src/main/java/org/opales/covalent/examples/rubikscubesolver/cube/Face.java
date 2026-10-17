/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.cube;

/**
 * The six faces of a Rubik's cube, named after their fixed position in the
 * cube's own reference frame (not after the color that happens to sit on
 * them, which changes as the cube is scrambled and solved).
 */
public enum Face {

	/** The top face. */
	UP,

	/** The bottom face, opposite {@link #UP}. */
	DOWN,

	/** The left face. */
	LEFT,

	/** The right face, opposite {@link #LEFT}. */
	RIGHT,

	/** The front face, facing the viewer. */
	FRONT,

	/** The back face, opposite {@link #FRONT}. */
	BACK;
}
