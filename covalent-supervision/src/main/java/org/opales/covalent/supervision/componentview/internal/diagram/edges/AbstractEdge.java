/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.edges;

import java.util.Objects;

import org.opales.covalent.supervision.componentview.internal.diagram.graph.Cell;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.Edge;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * Starting point of the edges of a graph. It holds the source and target cells, whether the edge is directed, and
 * the visibility state. Creating an edge makes the target a parent of the source and the source a child of the
 * target.
 */
public abstract class AbstractEdge implements Edge {

	private final Cell source;
	private final Cell target;
	private final boolean directed;
	protected SimpleBooleanProperty visible = new SimpleBooleanProperty(true);

	protected AbstractEdge(final Cell source, final Cell target, final boolean directed) {
		this.source = Objects.requireNonNull(source, "Source cannot be null");
		this.target = Objects.requireNonNull(target, "Target cannot be null");
		this.directed = directed;
		this.linkCells();
	}

	protected void linkCells() {
		this.source.addParent(this.target);
		this.target.addChild(this.source);
	}

	@Override
	public Cell getSource() {
		return this.source;
	}

	@Override
	public Cell getTarget() {
		return this.target;
	}

	@Override
	public boolean isDirected() {
		return this.directed;
	}

	@Override
	public void setVisible(final boolean isVisible) {
		this.visible.set(isVisible);
	}

	@Override
	public BooleanProperty visibleProperty() {
		return this.visible;
	}

}
