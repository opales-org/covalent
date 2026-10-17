/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.layout;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.elk.alg.layered.options.LayeredOptions;
import org.eclipse.elk.core.RecursiveGraphLayoutEngine;
import org.eclipse.elk.core.options.CoreOptions;
import org.eclipse.elk.core.options.Direction;
import org.eclipse.elk.core.util.BasicProgressMonitor;
import org.eclipse.elk.graph.ElkNode;
import org.eclipse.elk.graph.util.ElkGraphUtil;

import org.opales.covalent.supervision.componentview.internal.diagram.graph.Graph;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.Cell;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.Edge;

import javafx.scene.layout.Region;

/**
 * Layered placement delegated to the Eclipse Layout Kernel (ELK,
 * {@code org.eclipse.elk.alg.layered}).
 * <p>
 * Only node placement is delegated here: port side selection stays
 * {@link ComponentPortSideLayout}'s job, and edge routing stays
 * {@code AnchoredPortEdge}/{@code OrthogonalRouter}'s - both read
 * back the layoutX/layoutY this class writes onto each cell's graphic.
 * <p>
 * Cycle breaking and multi-component packing (several disconnected node
 * groups, such as a composite level with no internal wiring at all, placed
 * side by side rather than forced into one vertical column) are handled
 * internally by ELK's layered algorithm. {@link #apply} builds the whole
 * model graph (every cell and edge, not just the visible ones) so that
 * ranking sees the full structure: an invisible cell in the middle of a
 * chain still occupies a layer. Only the writing of the computed position
 * back is skipped for cells that are hidden.
 */
public class ElkLayout implements GraphLayout {

	private final double levelGap;
	private final double nodeGap;

	// ELK's default (1.6, golden-ratio-ish) leaves it free to stack disconnected
	// node groups in a single column: edge-less sibling nodes, same shape as a
	// composite level whose children share no service/event/parameter wiring,
	// stay in one vertical column at 1.6 and only wrap into a multi-column grid
	// once the target aspect ratio is widened. 2.0 is a mild widescreen bias.
	private static final double ASPECT_RATIO = 2.0;

	public ElkLayout(final double levelGap, final double nodeGap) {
		this.levelGap = levelGap;
		this.nodeGap = nodeGap;
	}

	@Override
	public void apply(final Graph graph) {
		final ElkNode root = ElkGraphUtil.createGraph();
		root.setProperty(CoreOptions.ALGORITHM, "org.eclipse.elk.layered");
		root.setProperty(CoreOptions.DIRECTION, Direction.RIGHT);
		root.setProperty(CoreOptions.SPACING_NODE_NODE, this.nodeGap);
		root.setProperty(LayeredOptions.SPACING_NODE_NODE_BETWEEN_LAYERS, this.levelGap);
		// Disconnected groups (no edge at all between them - e.g. a composite
		// level whose children share no service/event/parameter wiring) get
		// packed into a grid instead of forced into a single layer/column.
		// Both the "separate" flag and a wide-enough ASPECT_RATIO are needed:
		// the flag alone produces a single column.
		root.setProperty(LayeredOptions.SEPARATE_CONNECTED_COMPONENTS, true);
		root.setProperty(LayeredOptions.COMPACTION_CONNECTED_COMPONENTS, true);
		root.setProperty(LayeredOptions.ASPECT_RATIO, ASPECT_RATIO);

		final Map<Cell, ElkNode> nodes = new HashMap<>();
		for (final Cell cell : graph.getModel().getAllCells()) {
			final Region graphic = graph.getGraphic(cell);
			final ElkNode node = ElkGraphUtil.createNode(root);
			node.setWidth(this.readEffectiveWidth(graphic));
			node.setHeight(this.readEffectiveHeight(graphic));
			nodes.put(cell, node);
		}

		for (final Edge edge : graph.getModel().getAllEdges()) {
			final ElkNode source = nodes.get(edge.getSource());
			final ElkNode target = nodes.get(edge.getTarget());
			if (source == null || target == null) {
				continue;
			}
			// The underlying graph layout expects source cells to be children of
			// target cells (same convention CompositionEdge's own constructor
			// documents, and the one cell.getChildren() via
			// AbstractEdge.linkCells() implements: target ends up
			// shallower, source deeper). ELK's own edges flow source -> target from
			// shallow to deep, so the ELK edge has to run target -> source to match,
			// not source -> target.
			ElkGraphUtil.createSimpleEdge(target, source);
		}

		new RecursiveGraphLayoutEngine().layout(root, new BasicProgressMonitor());

		for (final Map.Entry<Cell, ElkNode> entry : nodes.entrySet()) {
			final Cell cell = entry.getKey();
			if (!cell.visibleProperty().get()) {
				continue;
			}
			final Region graphic = graph.getGraphic(cell);
			final ElkNode node = entry.getValue();
			graphic.setLayoutX(node.getX());
			graphic.setLayoutY(node.getY());
		}
	}

	private double readEffectiveWidth(final Region graphic) {
		return Math.max(graphic.getWidth(), Math.max(graphic.prefWidth(-1), graphic.minWidth(-1)));
	}

	private double readEffectiveHeight(final Region graphic) {
		return Math.max(graphic.getHeight(), Math.max(graphic.prefHeight(-1), graphic.minHeight(-1)));
	}

}
