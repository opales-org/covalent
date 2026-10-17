/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.impl.DatasetControllerImpl;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.view.impl.DatasetViewImpl;

/**
 * The toy classification problems the dataset can be generated as, ordered
 * here (deliberately, top to bottom) by roughly increasing difficulty for a
 * feed-forward network to learn: from {@link #TWO_MOONS} (a smooth,
 * single-curvature boundary) to {@link #CHECKERBOARD} (many small,
 * disconnected same-label regions - a generalized XOR, historically the
 * textbook example of a problem a network needs real hidden-layer capacity
 * for, and the best candidate of the six for actually surfacing visible
 * overfitting on a demo-sized network).
 * <p>
 * Each constant names one {@link DatasetGenerator} implementation (wired in
 * {@code DatasetControllerImpl}'s shape-to-generator mapping) and carries a
 * short display label used by {@code DatasetViewImpl}'s shape-selector
 * tooltips; the enum itself has no drawing or generation logic - see
 * {@link DatasetGenerator} for that.
 */
public enum DatasetShape {

	/** Two interleaved half-circle arcs, one per class. */
	TWO_MOONS("Two moons"),
	/** A filled inner disk surrounded by an outer annulus. */
	RING("Ring"),
	/** A "+"-shaped region against its complement. */
	CROSS("Cross"),
	/** A non-convex five-pointed star against its complement. */
	STAR("Star"),
	/** Two interleaved spiral arms, one per class. */
	SPIRAL("Spiral"),
	/** A grid of alternating square cells, the XOR-like hardest shape. */
	CHECKERBOARD("Checkerboard");

	private final String label;

	DatasetShape(final String label) {
		this.label = label;
	}

	/**
	 * Returns the short display label of this shape.
	 *
	 * @return a short, human-readable name for this shape (e.g. for a tooltip)
	 */
	public String getLabel() {
		return this.label;
	}
}
