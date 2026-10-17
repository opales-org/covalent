/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.edges;

import org.opales.covalent.supervision.componentview.internal.diagram.cells.ComponentPort;
import org.opales.covalent.supervision.componentview.internal.diagram.cells.ComponentPortKind;
import org.opales.covalent.supervision.componentview.internal.diagram.cells.CovalentComponentCell;
import org.opales.covalent.supervision.componentview.internal.diagram.cells.PortPalette;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.Highlighting;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.Graph;
import org.opales.covalent.supervision.componentview.internal.diagram.edges.OrthogonalRouter.Direction;

import java.util.List;
import java.util.function.Consumer;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * Component-to-component graph edge drawn between two visual component ports.
 */
public class AnchoredPortEdge extends AbstractEdge {

	// Every wire color comes from PortPalette, shared with CovalentComponentCell
	// so wires and port shapes always agree.


	/** Width of the invisible band around a wire that answers the pointer for it. */
	private static final double HIT_STROKE_WIDTH = 12;

	private final BooleanProperty highlighted = new SimpleBooleanProperty(false);

	private PauseTransition highlightTransition;

	/**
	 * This edge's single long-lived {@code EdgeGraphic}'s line, kept so
	 * {@link #refreshColor()} can re-bind its stroke after a Preferences-panel
	 * color change - the graph library builds one {@code EdgeGraphic} per edge
	 * and reuses it, it is not recreated on repaint.
	 */
	private RoutedLine line;

	private final CovalentComponentCell sourceComponent;

	private final CovalentComponentCell targetComponent;

	private final ComponentPort sourcePort;

	private final ComponentPort targetPort;

	private final StringProperty textProperty = new SimpleStringProperty();

	private final IntegerProperty offsetProperty = new SimpleIntegerProperty(0);

	public AnchoredPortEdge(final CovalentComponentCell sourceComponent, final ComponentPort sourcePort,
			final CovalentComponentCell targetComponent, final ComponentPort targetPort, final boolean isDirected) {
		super(sourceComponent, targetComponent, isDirected);
		this.sourceComponent = sourceComponent;
		this.sourcePort = sourcePort;
		this.targetComponent = targetComponent;
		this.targetPort = targetPort;
	}

	public CovalentComponentCell getSourceComponent() {
		return this.sourceComponent;
	}

	public CovalentComponentCell getTargetComponent() {
		return this.targetComponent;
	}

	public ComponentPort getSourcePort() {
		return this.sourcePort;
	}

	public ComponentPort getTargetPort() {
		return this.targetPort;
	}

	public StringProperty textProperty() {
		return this.textProperty;
	}

	public void setOffset(final Integer wiringCount) {
		this.offsetProperty.set(wiringCount);
	}

	public ReadOnlyBooleanProperty highlightedProperty() {
		return this.highlighted;
	}

	private Consumer<ContextMenuEvent> onContextMenuConsumer;

	/**
	 * The wire's own right-click (or platform context-menu key) gesture. The
	 * consumer is responsible for showing and for consuming the event.
	 */
	public void onContextMenuRequested(final Consumer<ContextMenuEvent> onContextMenuConsumer) {
		this.onContextMenuConsumer = onContextMenuConsumer;
	}

	/**
	 * Re-reads {@code PortPalette}'s current colors and re-binds this edge's
	 * line stroke from them - called by {@code ComponentDiagramViewImpl} right
	 * after a Preferences-panel color change, for every visible
	 * edge, since the bindings set up in {@code EdgeGraphic}'s constructor
	 * read the palette values once, when this edge is first drawn.
	 */
	public void refreshColor() {
		if (this.line == null) {
			return;
		}
		this.line.strokeProperty().bind(Bindings.when(this.highlighted)
				.then(PortPalette.HIGHLIGHT).otherwise(this.color()));
	}

	/**
	 * Click-to-highlight, driven by ComponentDiagramViewImpl when a port's pad
	 * is clicked: briefly paints this edge's own wire before automatically
	 * reverting. Every EdgeGraphic of this edge follows the highlighted
	 * binding, so this method does not need to locate the live Line node.
	 */
	public void highlight() {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(this::highlight);
			return;
		}
		if (this.highlightTransition != null) {
			this.highlightTransition.stop();
		}
		this.highlighted.set(true);
		this.highlightTransition = new PauseTransition(Highlighting.DURATION);
		this.highlightTransition.setOnFinished(e -> this.highlighted.set(false));
		this.highlightTransition.playFromStart();
	}

	@Override
	public Region getGraphic(final Graph graph) {
		EdgeGraphic graphic = new EdgeGraphic(graph, this, this.textProperty, this.offsetProperty);
		graphic.setVisible(this.visible.get());
		this.visible.addListener((s, o, n) -> graphic.setVisible(n.booleanValue()));
		return graphic;
	}

	/**
	 * A wire takes the color of its supplying end - the provided service, the
	 * produced events or the offered parameters - whichever end it starts
	 * from, so a link reads the same way for every port kind.
	 */
	private Color color() {
		ComponentPortKind kind = this.sourcePort.getKind();
		if (kind == ComponentPortKind.SERVICE) {
			return PortPalette.SERVICE_PROVIDED;
		}
		if (kind == ComponentPortKind.EVENT) {
			return PortPalette.EVENT_PRODUCED;
		}
		return PortPalette.PARAMETER;
	}

	/**
	 * Graphic of an {@link AnchoredPortEdge}: a routed wire between two port anchors, drawn over an invisible wider
	 * band that takes the pointer gestures, with an optional relation name at the middle of the wire.
	 */
	public static class EdgeGraphic extends Pane {

		public EdgeGraphic(final Graph graph, final AnchoredPortEdge edge, final StringProperty textProperty,
				final IntegerProperty offsetProperty) {
			this.setPickOnBounds(false);

			DoubleBinding sourceX = edge.sourcePort.getAnchorCell().getXAnchor(graph);
			DoubleBinding sourceY = edge.sourcePort.getAnchorCell().getYAnchor(graph);
			DoubleBinding targetX = edge.targetPort.getAnchorCell().getXAnchor(graph);
			DoubleBinding targetY = edge.targetPort.getAnchorCell().getYAnchor(graph);

			Region sourceGraphic = graph.getGraphic(edge.sourceComponent);
			Region targetGraphic = graph.getGraphic(edge.targetComponent);
			RoutedLine.CardBounds sourceCard = RoutedLine.CardBounds.of(sourceGraphic, edge.sourceComponent);
			RoutedLine.CardBounds targetCard = RoutedLine.CardBounds.of(targetGraphic, edge.targetComponent);

			String routingKey = "port:" + edge.sourceComponent.getComponentClass() + ":"
					+ edge.sourcePort.getKind() + ":" + edge.sourcePort.getRole() + ":" + edge.sourcePort.getLabel()
					+ "->" + edge.targetComponent.getComponentClass() + ":"
					+ edge.targetPort.getKind() + ":" + edge.targetPort.getRole() + ":" + edge.targetPort.getLabel();
			RoutedLine line = new RoutedLine(graph.getEdgeRouting(), routingKey, this,
					sourceX, sourceY, targetX, targetY,
					() -> Direction.valueOf(edge.sourcePort.getSide().name()),
					() -> Direction.valueOf(edge.targetPort.getSide().name()),
					sourceCard, targetCard, graph.edgeRoutingStyleProperty());
			edge.line = line;
			line.strokeProperty().bind(Bindings.when(edge.highlighted)
					.then(PortPalette.HIGHLIGHT).otherwise(edge.color()));
			line.strokeWidthProperty().bind(Bindings.when(edge.highlighted)
					.then(PortPalette.WIRE_HIGHLIGHT_WIDTH).otherwise(PortPalette.WIRE_WIDTH));
			// The wire itself is a thin stroke, hard to point at: an invisible,
			// wider copy of its path, under it, takes the pointer gestures for it.
			Path hitArea = new Path();
			Bindings.bindContent(hitArea.getElements(), line.getElements());
			hitArea.setFill(null);
			hitArea.setStroke(Color.TRANSPARENT);
			hitArea.setStrokeWidth(HIT_STROKE_WIDTH);
			hitArea.setCursor(Cursor.HAND);
			for (Node target : List.of(hitArea, line)) {
				target.setOnContextMenuRequested(event -> {
					if (edge.onContextMenuConsumer != null) {
						edge.onContextMenuConsumer.accept(event);
					}
				});
				target.setOnMouseClicked(event -> {
					if (event.getButton() == MouseButton.PRIMARY) {
						// Clicking the wire itself highlights the same group a pad click
						// does - the wire plus both pads it connects - also when the
						// click starts on the link rather than on one of its ends.
						edge.highlight();
						edge.sourcePort.highlight();
						edge.targetPort.highlight();
						event.consume();
					}
				});
			}

			Text text = new Text();
			text.textProperty().bind(textProperty);
			text.getStyleClass().add("edge-text");
			text.setFont(Font.font("System", 24));
			text.visibleProperty().bind(graph.showRelationNamesProperty());
			// Anchored to the routed polyline's true midpoint-by-arc-length (see
			// RoutedLine.updateLabelPosition) rather than the naive straight-line
			// midpoint between the two endpoints, so the label stays on an
			// elbowed route instead of drifting off it when both arms are long.
			text.xProperty().bind(line.labelXProperty());
			text.yProperty().bind(line.labelYProperty());
			offsetProperty.addListener((s, o, n) -> text.setTranslateY(n.intValue() * 14));
			text.setTranslateY(offsetProperty.get() * 14);

			this.getChildren().addAll(hitArea, line, text);
		}
	}
}
