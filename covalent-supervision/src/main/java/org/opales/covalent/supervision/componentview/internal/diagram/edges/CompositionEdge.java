/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.edges;

import org.opales.covalent.supervision.componentview.internal.diagram.cells.CovalentComponentCell;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.Graph;
import org.opales.covalent.supervision.componentview.internal.diagram.edges.OrthogonalRouter.Direction;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * Structural edge that represents Covalent component composition.
 */
public class CompositionEdge extends AbstractEdge {

	private static final Color STRUCTURAL_STROKE = Color.web("#475569");

	private static final Color STRUCTURAL_LABEL = Color.web("#334155");

	private static final double DIAMOND_RADIUS = 10;

	private static final double LINE_START_OFFSET = DIAMOND_RADIUS * 2;

	private final CovalentComponentCell parentComponent;

	private final CovalentComponentCell childComponent;

	private final StringProperty textProperty = new SimpleStringProperty();

	public CompositionEdge(final CovalentComponentCell parentComponent, final CovalentComponentCell childComponent,
			final String label) {
		// The underlying graph layout expects source nodes to be children of target
		// nodes. Composition is therefore registered as child -> parent, while the
		// rendered semantic remains parent owns child.
		super(childComponent, parentComponent, false);
		this.parentComponent = parentComponent;
		this.childComponent = childComponent;
		this.textProperty.set(label);
	}

	@Override
	public Region getGraphic(final Graph graph) {
		EdgeGraphic graphic = new EdgeGraphic(graph, this);
		graphic.setVisible(this.visible.get());
		this.visible.addListener((s, o, n) -> graphic.setVisible(n.booleanValue()));
		return graphic;
	}

	private DoubleBinding parentAnchorX(final Region parentGraphic, final Region childGraphic) {
		return Bindings.createDoubleBinding(() -> {
			double parentCenterX = cardCenterX(parentGraphic, this.parentComponent);
			double childCenterX = cardCenterX(childGraphic, this.childComponent);
			return parentGraphic.getLayoutX() + (this.horizontalDirection(parentCenterX, childCenterX) > 0
					? this.parentComponent.getCardRightX()
					: this.parentComponent.getCardLeftX());
		}, parentGraphic.layoutXProperty(), parentGraphic.layoutYProperty(), parentGraphic.widthProperty(),
				parentGraphic.heightProperty(), childGraphic.layoutXProperty(), childGraphic.layoutYProperty(),
				childGraphic.widthProperty(), childGraphic.heightProperty());
	}

	private DoubleBinding parentAnchorY(final Region parentGraphic, final Region childGraphic) {
		return Bindings.createDoubleBinding(() -> {
			return cardCenterY(parentGraphic, this.parentComponent);
		}, parentGraphic.layoutXProperty(), parentGraphic.layoutYProperty(), parentGraphic.widthProperty(),
				parentGraphic.heightProperty(), childGraphic.layoutXProperty(), childGraphic.layoutYProperty(),
				childGraphic.widthProperty(), childGraphic.heightProperty());
	}

	private DoubleBinding childAnchorX(final Region parentGraphic, final Region childGraphic) {
		return Bindings.createDoubleBinding(() -> {
			double parentCenterX = cardCenterX(parentGraphic, this.parentComponent);
			double childCenterX = cardCenterX(childGraphic, this.childComponent);
			return childGraphic.getLayoutX() + (this.horizontalDirection(parentCenterX, childCenterX) > 0
					? this.childComponent.getCardLeftX()
					: this.childComponent.getCardRightX());
		}, parentGraphic.layoutXProperty(), parentGraphic.layoutYProperty(), parentGraphic.widthProperty(),
				parentGraphic.heightProperty(), childGraphic.layoutXProperty(), childGraphic.layoutYProperty(),
				childGraphic.widthProperty(), childGraphic.heightProperty());
	}

	private DoubleBinding childAnchorY(final Region parentGraphic, final Region childGraphic) {
		return Bindings.createDoubleBinding(() -> {
			return cardCenterY(childGraphic, this.childComponent);
		}, parentGraphic.layoutXProperty(), parentGraphic.layoutYProperty(), parentGraphic.widthProperty(),
				parentGraphic.heightProperty(), childGraphic.layoutXProperty(), childGraphic.layoutYProperty(),
				childGraphic.widthProperty(), childGraphic.heightProperty());
	}

	private DoubleBinding lineStartX(final Region parentGraphic, final Region childGraphic, final DoubleBinding anchorX) {
		return Bindings.createDoubleBinding(() -> {
			double parentCenterX = cardCenterX(parentGraphic, this.parentComponent);
			double childCenterX = cardCenterX(childGraphic, this.childComponent);
			return anchorX.get() + this.horizontalDirection(parentCenterX, childCenterX) * LINE_START_OFFSET;
		}, anchorX, parentGraphic.layoutXProperty(), parentGraphic.layoutYProperty(), parentGraphic.widthProperty(),
				parentGraphic.heightProperty(), childGraphic.layoutXProperty(), childGraphic.layoutYProperty(),
				childGraphic.widthProperty(), childGraphic.heightProperty());
	}

	private DoubleBinding lineStartY(final Region parentGraphic, final Region childGraphic, final DoubleBinding anchorY) {
		return Bindings.createDoubleBinding(() -> {
			return anchorY.get();
		}, anchorY, parentGraphic.layoutXProperty(), parentGraphic.layoutYProperty(), parentGraphic.widthProperty(),
				parentGraphic.heightProperty(), childGraphic.layoutXProperty(), childGraphic.layoutYProperty(),
				childGraphic.widthProperty(), childGraphic.heightProperty());
	}

	private DoubleBinding diamondCenterX(final Region parentGraphic, final Region childGraphic,
			final DoubleBinding parentX) {
		return Bindings.createDoubleBinding(() -> {
			double parentCenterX = cardCenterX(parentGraphic, this.parentComponent);
			double childCenterX = cardCenterX(childGraphic, this.childComponent);
			return parentX.get() + this.horizontalDirection(parentCenterX, childCenterX) * DIAMOND_RADIUS;
		}, parentX, parentGraphic.layoutXProperty(), parentGraphic.layoutYProperty(), parentGraphic.widthProperty(),
				parentGraphic.heightProperty(), childGraphic.layoutXProperty(), childGraphic.layoutYProperty(),
				childGraphic.widthProperty(), childGraphic.heightProperty());
	}

	private DoubleBinding diamondCenterY(final Region parentGraphic, final Region childGraphic,
			final DoubleBinding parentY) {
		return Bindings.createDoubleBinding(() -> {
			return parentY.get();
		}, parentY, parentGraphic.layoutXProperty(), parentGraphic.layoutYProperty(), parentGraphic.widthProperty(),
				parentGraphic.heightProperty(), childGraphic.layoutXProperty(), childGraphic.layoutYProperty(),
				childGraphic.widthProperty(), childGraphic.heightProperty());
	}

	private double horizontalDirection(final double sourceCenterX, final double targetCenterX) {
		return targetCenterX >= sourceCenterX ? 1 : -1;
	}

	private static double cardCenterX(final Region graphic, final CovalentComponentCell component) {
		return graphic.getLayoutX() + component.getCardCenterX();
	}

	private static double cardCenterY(final Region graphic, final CovalentComponentCell component) {
		return graphic.getLayoutY() + component.getCardCenterY();
	}

	private static class EdgeGraphic extends Pane {

		private EdgeGraphic(final Graph graph, final CompositionEdge edge) {
			this.setPickOnBounds(false);
			this.setMouseTransparent(true);

			Region parentGraphic = graph.getGraphic(edge.parentComponent);
			Region childGraphic = graph.getGraphic(edge.childComponent);

			DoubleBinding parentX = edge.parentAnchorX(parentGraphic, childGraphic);
			DoubleBinding parentY = edge.parentAnchorY(parentGraphic, childGraphic);
			DoubleBinding childX = edge.childAnchorX(parentGraphic, childGraphic);
			DoubleBinding childY = edge.childAnchorY(parentGraphic, childGraphic);
			DoubleBinding diamondX = edge.diamondCenterX(parentGraphic, childGraphic, parentX);
			DoubleBinding diamondY = edge.diamondCenterY(parentGraphic, childGraphic, parentY);

			RoutedLine.CardBounds parentCard = RoutedLine.CardBounds.of(parentGraphic, edge.parentComponent);
			RoutedLine.CardBounds childCard = RoutedLine.CardBounds.of(childGraphic, edge.childComponent);
			String routingKey = "composition:" + edge.parentComponent.getComponentClass()
					+ "->" + edge.childComponent.getComponentClass();
			RoutedLine line = new RoutedLine(graph.getEdgeRouting(), routingKey, this,
					edge.lineStartX(parentGraphic, childGraphic, parentX),
					edge.lineStartY(parentGraphic, childGraphic, parentY), childX, childY,
					() -> cardCenterX(childGraphic, edge.childComponent) >= cardCenterX(parentGraphic, edge.parentComponent)
							? Direction.RIGHT : Direction.LEFT,
					() -> cardCenterX(childGraphic, edge.childComponent) >= cardCenterX(parentGraphic, edge.parentComponent)
							? Direction.LEFT : Direction.RIGHT,
					parentCard, childCard, graph.edgeRoutingStyleProperty());
			line.setStroke(STRUCTURAL_STROKE);
			line.setStrokeWidth(1.25);
			line.setStrokeLineCap(StrokeLineCap.ROUND);
			line.getStrokeDashArray().addAll(7.0, 5.0);

			Polygon diamond = new Polygon(-DIAMOND_RADIUS, 0, 0, -DIAMOND_RADIUS, DIAMOND_RADIUS, 0, 0,
					DIAMOND_RADIUS);
			diamond.layoutXProperty().bind(diamondX);
			diamond.layoutYProperty().bind(diamondY);
			diamond.setFill(STRUCTURAL_STROKE);
			diamond.setStroke(STRUCTURAL_STROKE);
			diamond.setStrokeWidth(1);

			Text text = new Text();
			text.textProperty().bind(edge.textProperty);
			text.getStyleClass().add("edge-text");
			text.setFill(STRUCTURAL_LABEL);
			text.setFont(Font.font("System", 24));
			text.visibleProperty().bind(graph.showRelationNamesProperty());
			text.xProperty().bind(parentX.add(childX).divide(2).add(6));
			text.yProperty().bind(parentY.add(childY).divide(2).subtract(6));

			this.getChildren().addAll(line, diamond, text);
		}
	}
}
