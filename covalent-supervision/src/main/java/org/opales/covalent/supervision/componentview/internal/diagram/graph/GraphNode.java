/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.graph;

import javafx.scene.layout.Region;

/**
 * Anything a {@link Graph} draws, cells and edges alike. A node builds the JavaFX region that represents it, and
 * can react once the graph has added that region or removed it again.
 */
public interface GraphNode {

	/**
	 * @param graph the graph that is about to display this node
	 * @return the region that draws this node, or {@code null} if it draws nothing
	 */
	Region getGraphic(Graph graph);

	/**
	 * Called once the graph has added the region of this node to its canvas.
	 *
	 * @param graph the graph that displays this node
	 * @param region the region added to the canvas
	 */
	default void onAddedToGraph(final Graph graph, final Region region) {
		// Nothing to do by default.
	}

	/**
	 * Called once the graph has taken the region of this node off its canvas.
	 *
	 * @param graph the graph that displayed this node
	 * @param region the region removed from the canvas
	 */
	default void onRemovedFromGraph(final Graph graph, final Region region) {
		// Nothing to do by default.
	}

}
