/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.cube.view;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Piloting commands for the cube view, provided by {@code CubeView} and
 * used by {@code CubeInteraction}. Deliberately minimal: the one operation the view needs commanded from
 * outside its own event-driven redraw is a busy/animating indicator, shown
 * while a scramble is running.
 */
@Services
public interface CubeViewServices {

	/**
	 * Indicates whether the cube is busy, which the view shows by dimming
	 * its faces. {@code CubeInteraction} sets it around a scramble.
	 *
	 * @param animating {@code true} while the cube is busy
	 */
	void setAnimating(boolean animating);
}
