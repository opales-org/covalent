/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.graph;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link ViewportFit}: the content is fitted to the visible area with a margin, at most at 100 %, and its center
 * lands on the area's center, wherever it lies on the canvas. The canvas scales around its own center, as a
 * JavaFX node does.
 */
public class ViewportFitTests {

	private static final double MARGIN = 80;

	private static final double MIN_SCALE = 0.1;

	/** Where a content point lands in the area once the fit is applied to a canvas of this size. */
	private static double[] land(final ViewportFit fit, final double canvasWidth, final double canvasHeight,
			final double x, final double y) {
		double pivotX = canvasWidth / 2;
		double pivotY = canvasHeight / 2;
		return new double[] { pivotX + fit.scale() * (x - pivotX) + fit.translateX(),
				pivotY + fit.scale() * (y - pivotY) + fit.translateY() };
	}

	@Test
	public void theContentCenterLandsOnTheAreaCenter() {
		Bounds content = new BoundingBox(1200, 900, 600, 300);

		ViewportFit fit = ViewportFit.of(content, 913, 783, 913, 783, MARGIN, MIN_SCALE);

		double[] center = land(fit, 913, 783, content.getCenterX(), content.getCenterY());
		Assert.assertEquals(456.5, center[0], 0.01);
		Assert.assertEquals(391.5, center[1], 0.01);
	}

	@Test
	public void theScaleDependsOnTheContentSizeOnlyNotOnWhereItLies() {
		ViewportFit near = ViewportFit.of(new BoundingBox(10, 10, 1600, 400), 913, 783, 913, 783, MARGIN, MIN_SCALE);
		ViewportFit far = ViewportFit.of(new BoundingBox(3000, 2500, 1600, 400), 913, 783, 913, 783, MARGIN, MIN_SCALE);

		Assert.assertEquals(near.scale(), far.scale(), 1e-9);
		Assert.assertEquals((913 - MARGIN) / 1600, near.scale(), 1e-9);
	}

	@Test
	public void aSmallContentIsShownAtOneHundredPercentAtMost() {
		ViewportFit fit = ViewportFit.of(new BoundingBox(50, 60, 200, 100), 913, 783, 913, 783, MARGIN, MIN_SCALE);

		Assert.assertEquals(1.0, fit.scale(), 1e-9);
		double[] center = land(fit, 913, 783, 150, 110);
		Assert.assertEquals(456.5, center[0], 0.01);
		Assert.assertEquals(391.5, center[1], 0.01);
	}

	@Test
	public void aHugeContentIsShownAtTheMinimumScale() {
		ViewportFit fit = ViewportFit.of(new BoundingBox(0, 0, 100000, 100000), 913, 783, 913, 783, MARGIN, MIN_SCALE);

		Assert.assertEquals(MIN_SCALE, fit.scale(), 1e-9);
	}

	@Test
	public void aCanvasOfAnotherSizeThanTheAreaStillCentersTheContent() {
		Bounds content = new BoundingBox(400, 300, 500, 200);

		ViewportFit fit = ViewportFit.of(content, 1200, 900, 700, 500, MARGIN, MIN_SCALE);

		double[] center = land(fit, 1200, 900, content.getCenterX(), content.getCenterY());
		Assert.assertEquals(350, center[0], 0.01);
		Assert.assertEquals(250, center[1], 0.01);
	}
}
