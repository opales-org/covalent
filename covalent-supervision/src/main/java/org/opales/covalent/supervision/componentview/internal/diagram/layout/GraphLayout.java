/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.layout;

import org.opales.covalent.supervision.componentview.internal.diagram.graph.Graph;

/** A way of placing the cells of a graph, by moving their graphics. */
public interface GraphLayout {

	/**
	 * Places the cells of the graph.
	 *
	 * @param graph the graph whose cells are placed
	 */
	void apply(Graph graph);

}
