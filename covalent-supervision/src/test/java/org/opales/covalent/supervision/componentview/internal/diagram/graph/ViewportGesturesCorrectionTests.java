/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.graph;

import org.junit.Assert;
import org.junit.Test;

/** Verifies how far a diagram may be moved along one axis. */
public class ViewportGesturesCorrectionTests {

	private static final double VIEW_MIN = 0;
	private static final double VIEW_MAX = 600;

	private static double free(final double contentMin, final double contentMax) {
		return ViewportGestures.correction(contentMin, contentMax, VIEW_MIN, VIEW_MAX, false);
	}

	private static double filling(final double contentMin, final double contentMax) {
		return ViewportGestures.correction(contentMin, contentMax, VIEW_MIN, VIEW_MAX, true);
	}

	@Test
	public void freeContentInsideTheViewIsLeftAlone() {
		Assert.assertEquals(0, free(100, 400), 0);
	}

	@Test
	public void freeContentLargerThanTheViewCanBeMovedUntilItsEdgeReachesTheOtherSide() {
		// The bottom of a 2000 px content can come up to 120 px under the top of the view.
		Assert.assertEquals(0, free(-1880, 120), 0);
		Assert.assertEquals(0, free(-1500, 500), 0);
	}

	@Test
	public void freeContentCannotLeaveTheViewEntirely() {
		Assert.assertEquals(20, free(-1900, 100), 1e-9);
		Assert.assertEquals(-20, free(500, 2500), 1e-9);
	}

	@Test
	public void freeContentCanGoPastTheEdgesOfTheView() {
		Assert.assertEquals(0, free(-200, 300), 0);
		Assert.assertEquals(0, free(300, 800), 0);
	}

	@Test
	public void freeContentSmallerThanTheMinimumKeepsAllOfItself() {
		// 50 px wide: it stays entirely visible, so a part cut off by the left edge comes back.
		Assert.assertEquals(60, free(-60, -10), 1e-9);
		Assert.assertEquals(0, free(10, 60), 0);
	}

	@Test
	public void fillingContentLargerThanTheViewKeepsFillingIt() {
		Assert.assertEquals(0, filling(-1000, 1000), 0);
		Assert.assertEquals(-40, filling(100, 2000), 1e-9);
		Assert.assertEquals(40, filling(-1900, 500), 1e-9);
	}

	@Test
	public void fillingContentSmallerThanTheViewStaysInside() {
		Assert.assertEquals(50, filling(-50, 200), 1e-9);
		Assert.assertEquals(-100, filling(400, 700), 1e-9);
	}

	@Test
	public void anEmptyViewNeverMovesAnything() {
		Assert.assertEquals(0, ViewportGestures.correction(-500, -400, 0, 0, false), 0);
	}
}
