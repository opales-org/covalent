/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.graph;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.layout.Pane;

/**
 * Surface a graph draws on. Panning moves it by changing its translation. Zooming changes one scale value, which
 * is bound to both of its scale axes and can be shared with other components.
 */
public class PannableCanvas extends Pane {

	private final DoubleProperty scale;

	/** Creates a canvas with its own scale, initially 1. */
	public PannableCanvas() {
		this(new SimpleDoubleProperty(1.0));
	}

	/**
	 * Creates a canvas whose zoom follows the given property.
	 *
	 * @param scale the property that holds the zoom factor, bound to both axes
	 */
	public PannableCanvas(final DoubleProperty scale) {
		this.scale = scale;
		this.scaleXProperty().bind(scale);
		this.scaleYProperty().bind(scale);
	}

	public double getScale() {
		return this.scale.get();
	}

	public void setScale(final double value) {
		this.scale.set(value);
	}

	public DoubleProperty scaleProperty() {
		return this.scale;
	}

}
