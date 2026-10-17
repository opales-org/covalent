/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.graph;

import javafx.beans.property.BooleanProperty;

/** A link a graph draws from a source cell to a target cell. It may be directed, and it can be hidden. */
public interface Edge extends GraphNode {

	Cell getSource();

	Cell getTarget();

	boolean isDirected();

	void setVisible(boolean visible);

	BooleanProperty visibleProperty();

}
