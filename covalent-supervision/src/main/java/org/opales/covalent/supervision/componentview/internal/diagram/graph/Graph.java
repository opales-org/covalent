/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.graph;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opales.covalent.supervision.componentview.internal.diagram.edges.EdgeRouting;
import org.opales.covalent.supervision.componentview.internal.diagram.edges.EdgeRoutingStyle;
import org.opales.covalent.supervision.componentview.internal.diagram.layout.GraphLayout;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;

/**
 * Interactive graph widget: a pannable, zoomable canvas that draws the cells and edges of a {@link GraphModel} as JavaFX
 * regions. It owns the gestures that move the nodes and the viewport, the edge routing and the display options
 * (port names, relation names, link visibility per kind, routing style), and applies a {@link GraphLayout} to place
 * the cells.
 */
public class Graph {

	private final EdgeRouting edgeRouting = new EdgeRouting();
	
	private final GraphModel model;
	
	private PannableCanvas pannableCanvas;
	
	private final Map<GraphNode, Region> graphics;
	
	private final NodeGestures nodeGestures;
	
	private ViewportGestures viewportGestures;
	
	private final BooleanProperty useNodeGestures;
	
	private BooleanProperty useViewportGestures;
	
	private final BooleanProperty showPortNames = new SimpleBooleanProperty(false);
	
	private final BooleanProperty showRelationNames = new SimpleBooleanProperty(false);
	
	// One toggle per port kind, each defaulting to visible. Scoped to links
	// only (AnchoredPortEdge + CovalentComponentCell's own navigable-port
	// badge) - the ports themselves (pad/stem/shape) are never affected.
	private final BooleanProperty showServiceLinks = new SimpleBooleanProperty(true);
	
	private final BooleanProperty showEventLinks = new SimpleBooleanProperty(true);
	
	private final BooleanProperty showParameterLinks = new SimpleBooleanProperty(true);
	
	// How edges are drawn: STRAIGHT as a single diagonal segment, CORNER (the default) as
	// an elbowed (orthogonal) route, ROUND as the same route with eased corners.
	private final ObjectProperty<EdgeRoutingStyle> edgeRoutingStyle = new SimpleObjectProperty<>(EdgeRoutingStyle.CORNER);
	
	public Graph() {
		this(new GraphModel());
	}
	
	public Graph(final GraphModel model) {
		this.model = model;
		
		this.nodeGestures = new NodeGestures(this);
		this.useNodeGestures = new SimpleBooleanProperty(true);
		this.useNodeGestures.addListener((obs, oldVal, newVal) -> {
			if (newVal) {
				model.getAllCells().forEach(cell -> this.nodeGestures.makeDraggable(this.getGraphic(cell)));
			} else {
				model.getAllCells().forEach(cell -> this.nodeGestures.makeUndraggable(this.getGraphic(cell)));
			}
		});
		
		this.pannableCanvas = new PannableCanvas();
		this.viewportGestures = new ViewportGestures(this);
		this.useViewportGestures = new SimpleBooleanProperty(true);
		this.useViewportGestures.addListener((obs, oldVal, newVal) -> {
			final Parent parent = this.pannableCanvas.parentProperty().get();
			if (parent == null) {
				return;
			}
			if (newVal) {
				parent.addEventHandler(MouseEvent.MOUSE_PRESSED, this.viewportGestures.getOnMousePressedEventHandler());
				parent.addEventHandler(MouseEvent.MOUSE_DRAGGED, this.viewportGestures.getOnMouseDraggedEventHandler());
				parent.addEventHandler(MouseEvent.MOUSE_RELEASED,
					this.viewportGestures.getOnMouseReleasedEventHandler());
				parent.addEventHandler(ScrollEvent.ANY, this.viewportGestures.getOnScrollEventHandler());
			} else {
				parent.removeEventHandler(MouseEvent.MOUSE_PRESSED,
					this.viewportGestures.getOnMousePressedEventHandler());
				parent.removeEventHandler(MouseEvent.MOUSE_DRAGGED,
					this.viewportGestures.getOnMouseDraggedEventHandler());
				parent.removeEventHandler(MouseEvent.MOUSE_RELEASED,
					this.viewportGestures.getOnMouseReleasedEventHandler());
				parent.removeEventHandler(ScrollEvent.ANY, this.viewportGestures.getOnScrollEventHandler());
			}
		});
		this.pannableCanvas.parentProperty().addListener((obs, oldVal, newVal) -> {
			if (oldVal != null) {
				oldVal.removeEventHandler(MouseEvent.MOUSE_PRESSED,
					this.viewportGestures.getOnMousePressedEventHandler());
				oldVal.removeEventHandler(MouseEvent.MOUSE_DRAGGED,
					this.viewportGestures.getOnMouseDraggedEventHandler());
				oldVal.removeEventHandler(MouseEvent.MOUSE_RELEASED,
					this.viewportGestures.getOnMouseReleasedEventHandler());
				oldVal.removeEventHandler(ScrollEvent.ANY, this.viewportGestures.getOnScrollEventHandler());
			}
			if (newVal != null) {
				newVal.addEventHandler(MouseEvent.MOUSE_PRESSED, this.viewportGestures.getOnMousePressedEventHandler());
				newVal.addEventHandler(MouseEvent.MOUSE_DRAGGED, this.viewportGestures.getOnMouseDraggedEventHandler());
				newVal.addEventHandler(MouseEvent.MOUSE_RELEASED,
					this.viewportGestures.getOnMouseReleasedEventHandler());
				newVal.addEventHandler(ScrollEvent.ANY, this.viewportGestures.getOnScrollEventHandler());
			}
		});
		
		this.graphics = new HashMap<>();
		
		this.addEdges(this.getModel().getAllEdges());
		this.addCells(this.getModel().getAllCells());
	}
	
	public PannableCanvas getCanvas() {
		return this.pannableCanvas;
	}
	
	public GraphModel getModel() {
		return this.model;
	}

	public EdgeRouting getEdgeRouting() {
		return this.edgeRouting;
	}
	
	public void beginUpdate() {
		this.getCanvas().getChildren().clear();
	}
	
	public void endUpdate() {
		// add components to graph pane
		this.addEdges(this.model.getAddedEdges());
		this.addCells(this.model.getAddedCells());
		
		// remove components from graph pane
		this.removeEdges(this.model.getRemovedEdges());
		this.removeCells(this.model.getRemovedCells());
		
		// clean up the model
		this.getModel().endUpdate();
	}
	
	private void addEdges(final List<Edge> edges) {
		edges.forEach(edge -> {
			try {
				Region edgeGraphic = this.getGraphic(edge);
				this.getCanvas().getChildren().add(edgeGraphic);
				edge.onAddedToGraph(this, edgeGraphic);
			} catch (final Exception e) {
				throw new RuntimeException("failed to add " + edge, e);
			}
		});
	}
	
	private void removeEdges(final List<Edge> edges) {
		edges.forEach(edge -> {
			try {
				Region edgeGraphic = this.getGraphic(edge);
				this.getCanvas().getChildren().remove(edgeGraphic);
				edge.onRemovedFromGraph(this, edgeGraphic);
			} catch (final Exception e) {
				throw new RuntimeException("failed to remove " + edge, e);
			}
		});
	}
	
	private void addCells(final List<Cell> cells) {
		cells.forEach(cell -> {
			try {
				Region cellGraphic = this.getGraphic(cell);
				this.getCanvas().getChildren().add(cellGraphic);
				if (this.useNodeGestures.get()) {
					this.nodeGestures.makeDraggable(cellGraphic);
				}
				cell.onAddedToGraph(this, cellGraphic);
			} catch (final Exception e) {
				throw new RuntimeException("failed to add " + cell, e);
			}
		});
	}
	
	private void removeCells(final List<Cell> cells) {
		cells.forEach(cell -> {
			try {
				Region cellGraphic = this.getGraphic(cell);
				this.getCanvas().getChildren().remove(cellGraphic);
				cell.onRemovedFromGraph(this, cellGraphic);
			} catch (final Exception e) {
				throw new RuntimeException("failed to remove " + cell, e);
			}
		});
	}
	
	public Region getGraphic(final GraphNode node) {
		if (!this.graphics.containsKey(node)) {
			this.graphics.put(node, this.createGraphic(node));
		}
		return this.graphics.get(node);
	}
	
	public Region createGraphic(final GraphNode node) {
		return node.getGraphic(this);
	}
	
	public double getScale() {
		return this.getCanvas().getScale();
	}
	
	public void layout(final GraphLayout layout) {
		layout.apply(this);
	}
	
	public NodeGestures getNodeGestures() {
		return this.nodeGestures;
	}
	
	public BooleanProperty showPortNamesProperty() {
		return this.showPortNames;
	}
	
	public BooleanProperty showRelationNamesProperty() {
		return this.showRelationNames;
	}
	
	public BooleanProperty showServiceLinksProperty() {
		return this.showServiceLinks;
	}
	
	public BooleanProperty showEventLinksProperty() {
		return this.showEventLinks;
	}
	
	public BooleanProperty showParameterLinksProperty() {
		return this.showParameterLinks;
	}
	
	public ObjectProperty<EdgeRoutingStyle> edgeRoutingStyleProperty() {
		return this.edgeRoutingStyle;
	}
	
	/**
	 * The center of {@code node}'s on-screen bounds, in scene
	 * coordinates - used as the zoom pivot point for a node-relative zoom
	 * (e.g. "zoom around the selected component") since a zoom pivot is
	 * naturally expressed in scene coordinates, same as
	 * {@link ViewportGestures#zoomTo(double, double, double)}'s own mouse-
	 * event-derived pivot. Measured on the node's own layout area: a container's local bounds also
	 * cover the content panned past its edges, which would move the center with the content.
	 */
	public static Point2D sceneCenterOf(final Node node) {
		final Bounds bounds = node.localToScene(node.getLayoutBounds());
		return new Point2D((bounds.getMinX() + bounds.getMaxX()) / 2, (bounds.getMinY() + bounds.getMaxY()) / 2);
	}

	public ViewportGestures getViewportGestures() {
		return this.viewportGestures;
	}
}
