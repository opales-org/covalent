/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.supervisionhmi.view.impl;

import org.junit.Assert;
import org.junit.Test;

/** The steps of the status bar's Zoom out and Zoom in buttons. */
public class ZoomStepTests {

	private static final double DELTA = 1e-9;

	@Test
	public void zoomingInFromBetweenTwoStepsGoesToTheNextLargerOne() {
		Assert.assertEquals(0.25, SupervisionHmiViewImpl.zoomStep(0.23, true), DELTA);
	}

	@Test
	public void zoomingOutFromBetweenTwoStepsGoesToTheNextSmallerOne() {
		Assert.assertEquals(0.15, SupervisionHmiViewImpl.zoomStep(0.23, false), DELTA);
	}

	@Test
	public void fromAStepTheButtonsGoToItsNeighbors() {
		Assert.assertEquals(1.25, SupervisionHmiViewImpl.zoomStep(1.0, true), DELTA);
		Assert.assertEquals(0.75, SupervisionHmiViewImpl.zoomStep(1.0, false), DELTA);
	}

	@Test
	public void aScaleAHairOffAStepCountsAsThatStep() {
		Assert.assertEquals(1.25, SupervisionHmiViewImpl.zoomStep(1.0000001, true), DELTA);
		Assert.assertEquals(0.75, SupervisionHmiViewImpl.zoomStep(0.9999999, false), DELTA);
	}

	@Test
	public void theStepsStopAtTheZoomLimits() {
		Assert.assertEquals(2.0, SupervisionHmiViewImpl.zoomStep(2.0, true), DELTA);
		Assert.assertEquals(0.1, SupervisionHmiViewImpl.zoomStep(0.1, false), DELTA);
	}
}
