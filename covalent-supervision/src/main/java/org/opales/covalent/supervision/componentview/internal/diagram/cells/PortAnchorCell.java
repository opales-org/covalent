/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.cells;

import org.opales.covalent.supervision.componentview.internal.diagram.graph.Graph;

import javafx.beans.binding.DoubleBinding;
import javafx.scene.layout.Region;

/**
 * Graph anchor backed by a visual port. It is used by edges for coordinate
 * binding, but it is never added as an independent graph cell.
 */
public class PortAnchorCell extends AbstractCell {

	private final ComponentPort port;

	PortAnchorCell(final ComponentPort port) {
		this.port = port;
	}

	public ComponentPort getPort() {
		return this.port;
	}

	@Override
	public DoubleBinding getXAnchor(final Graph graph) {
		return graph.getGraphic(this.port.getComponentCell()).layoutXProperty().add(this.port.anchorXProperty());
	}

	@Override
	public DoubleBinding getYAnchor(final Graph graph) {
		return graph.getGraphic(this.port.getComponentCell()).layoutYProperty().add(this.port.anchorYProperty());
	}

	@Override
	public Region getGraphic(final Graph graph) {
		return null;
	}
}
