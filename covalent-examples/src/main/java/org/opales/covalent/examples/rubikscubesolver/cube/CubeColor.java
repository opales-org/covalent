/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.cube;

/**
 * The six sticker colors of a standard Rubik's cube, in the conventional
 * Western color scheme (white opposite yellow, green opposite blue, red
 * opposite orange). {@link CubeState} maps each of its 54 facelets to one
 * of these.
 */
public enum CubeColor {

	/** White; opposite {@link #YELLOW}. */
	WHITE,

	/** Yellow; opposite {@link #WHITE}. */
	YELLOW,

	/** Green; opposite {@link #BLUE}. */
	GREEN,

	/** Blue; opposite {@link #GREEN}. */
	BLUE,

	/** Red; opposite {@link #ORANGE}. */
	RED,

	/** Orange; opposite {@link #RED}. */
	ORANGE;
}
