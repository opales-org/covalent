/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.layout;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.opales.covalent.supervision.componentview.internal.diagram.cells.ComponentPort;
import org.opales.covalent.supervision.componentview.internal.diagram.cells.ComponentPortKind;
import org.opales.covalent.supervision.componentview.internal.diagram.cells.ComponentPortSide;
import org.opales.covalent.supervision.componentview.internal.diagram.cells.CovalentComponentCell;
import org.opales.covalent.supervision.componentview.internal.diagram.edges.AnchoredPortEdge;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.Graph;

import javafx.scene.layout.Region;

/**
 * Chooses the visual side of service and event ports after the component layout
 * has positioned the component cells.
 */
public class ComponentPortSideLayout {

	public void execute(final Graph graph, final Collection<AnchoredPortEdge> edges) {
		Map<ComponentPort, SideVotes> votesByPort = new HashMap<>();

		for (AnchoredPortEdge edge : edges) {
			if (!edge.visibleProperty().get()) {
				continue;
			}
			this.collectVote(graph, votesByPort, edge.getSourceComponent(), edge.getTargetComponent(),
					edge.getSourcePort());
			this.collectVote(graph, votesByPort, edge.getTargetComponent(), edge.getSourceComponent(),
					edge.getTargetPort());
		}

		Set<CovalentComponentCell> changedComponents = new HashSet<>();
		for (Map.Entry<ComponentPort, SideVotes> entry : votesByPort.entrySet()) {
			ComponentPort port = entry.getKey();
			ComponentPortSide preferredSide = entry.getValue().preferredSide(port.getSide());
			if (preferredSide != port.getSide()) {
				port.setSide(preferredSide);
				changedComponents.add(port.getComponentCell());
			}
		}

		for (CovalentComponentCell component : changedComponents) {
			component.refreshPorts();
		}
	}

	private void collectVote(final Graph graph, final Map<ComponentPort, SideVotes> votesByPort,
			final CovalentComponentCell owner, final CovalentComponentCell opposite, final ComponentPort port) {
		if (!this.isSideLayoutCandidate(port) || owner == opposite) {
			return;
		}

		ComponentPortSide side = this.sideFacingOppositeComponent(graph, owner, opposite);
		votesByPort.computeIfAbsent(port, ignored -> new SideVotes()).add(side);
	}

	private boolean isSideLayoutCandidate(final ComponentPort port) {
		return port.getKind() == ComponentPortKind.SERVICE || port.getKind() == ComponentPortKind.EVENT;
	}

	private ComponentPortSide sideFacingOppositeComponent(final Graph graph, final CovalentComponentCell owner,
			final CovalentComponentCell opposite) {
		double ownerCenterX = this.cardCenterX(graph, owner);
		double oppositeCenterX = this.cardCenterX(graph, opposite);
		return oppositeCenterX >= ownerCenterX ? ComponentPortSide.RIGHT : ComponentPortSide.LEFT;
	}

	private double cardCenterX(final Graph graph, final CovalentComponentCell component) {
		Region graphic = graph.getGraphic(component);
		return graphic.getLayoutX() + component.getCardCenterX();
	}

	private static class SideVotes {
		private int left;

		private int right;

		void add(final ComponentPortSide side) {
			if (side == ComponentPortSide.LEFT) {
				this.left++;
			} else if (side == ComponentPortSide.RIGHT) {
				this.right++;
			}
		}

		ComponentPortSide preferredSide(final ComponentPortSide currentSide) {
			if (this.left > this.right) {
				return ComponentPortSide.LEFT;
			}
			if (this.right > this.left) {
				return ComponentPortSide.RIGHT;
			}
			return currentSide;
		}
	}
}
