/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.graph;

import java.util.List;

import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.scene.layout.Region;

/**
 * A box of a graph, linked to other cells as parent or child. It can be hidden or selected, and exposes the
 * geometry of its graphic so that edges can attach to it.
 */
public interface Cell extends GraphNode {

	void addChild(Cell cell);

	List<Cell> getChildren();

	void addParent(Cell cell);

	List<Cell> getParents();

	void removeChild(Cell cell);

	/**
	 * @param graph the graph that displays this cell
	 * @return the abscissa of the center of this cell's graphic
	 */
	default DoubleBinding getXAnchor(final Graph graph) {
		final Region graphic = graphicIn(graph);
		return graphic.layoutXProperty().add(graphic.widthProperty().divide(2));
	}

	/**
	 * @param graph the graph that displays this cell
	 * @return the ordinate of the center of this cell's graphic
	 */
	default DoubleBinding getYAnchor(final Graph graph) {
		final Region graphic = graphicIn(graph);
		return graphic.layoutYProperty().add(graphic.heightProperty().divide(2));
	}

	default ReadOnlyDoubleProperty getWidth(final Graph graph) {
		return graphicIn(graph).widthProperty();
	}

	default ReadOnlyDoubleProperty getHeight(final Graph graph) {
		return graphicIn(graph).heightProperty();
	}

	private Region graphicIn(final Graph graph) {
		return graph.getGraphic(this);
	}

	void setVisible(boolean visible);

	BooleanProperty visibleProperty();

	void setSelected(boolean selected);

	BooleanProperty selectedProperty();

}
