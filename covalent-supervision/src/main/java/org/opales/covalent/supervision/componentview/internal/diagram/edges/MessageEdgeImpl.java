/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.edges;

import java.util.ArrayList;
import java.util.List;

import org.opales.covalent.supervision.componentview.internal.diagram.graph.Arrow;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.Graph;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.SequenceDiagram.ActorCell;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.SequenceDiagram.MessageEdge;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.SequenceDiagram.MessageStyle;

import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;

/**
 * One message of a sequence diagram: an arrow between two lifelines, its label - port in
 * gray and method in bold for a call - and its time at the diagram's left edge. It can be
 * marked failed or selected, styled through the {@code :failed} and {@code :selected}
 * pseudo-classes, and is selected by a click on its label or on its arrow - not by the end
 * of a drag that pans the diagram.
 */
public class MessageEdgeImpl extends AbstractEdge implements MessageEdge {

	private static final PseudoClass FAILED = PseudoClass.getPseudoClass("failed");
	private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

	/** How far, in pixels, the pointer may move between press and release for a click. */
	private static final double CLICK_TOLERANCE = 4;

	/** Width of the invisible band along the arrow that takes the pointer for it. */
	private static final double HIT_WIDTH = 12;

	/** Where the message's time is drawn, from the diagram's left edge. */
	private static final double TIME_X = 6;

	private final String name;
	private final MessageStyle style;
	private final DoubleProperty yOffsetProperty = new SimpleDoubleProperty();
	private Runnable onClicAction;
	private String timeText;
	private boolean failed;
	private boolean selected;
	private boolean dimmed;

	/** The message's graphic, once created. */
	private Region graphic;

	/** How visible a faded message is. */
	private static final double DIMMED_OPACITY = 0.22;

	/** The nodes the failed and selected states apply to, once the graphic exists. */
	private final List<Node> styledNodes = new ArrayList<>();

	public MessageEdgeImpl(final ActorCell source, final ActorCell target, final String name) {
		this(source, target, name, MessageStyle.CALL);
	}

	public MessageEdgeImpl(final ActorCell source, final ActorCell target, final String name,
			final MessageStyle style) {
		super(source, target, true);
		this.name = name;
		this.style = style;
	}

	@Override
	public DoubleProperty yOffsetProperty() {
		return this.yOffsetProperty;
	}

	@Override
	public Region getGraphic(final Graph graph) {
		EdgeGraphic g = new EdgeGraphic(graph, this);
		this.graphic = g;
		g.setOpacity(this.dimmed ? DIMMED_OPACITY : 1);
		g.setVisible(this.visible.get());
		this.visible.addListener((s, o, n) -> g.setVisible(n.booleanValue()));
		return g;
	}

	@Override
	public void onClick(final Runnable action) {
		this.onClicAction = action;
	}

	@Override
	public void setTimeText(final String text) {
		this.timeText = text;
	}

	@Override
	public void setFailed(final boolean failed) {
		this.failed = failed;
		this.styledNodes.forEach(node -> node.pseudoClassStateChanged(FAILED, failed));
	}

	@Override
	public void setSelected(final boolean selected) {
		this.selected = selected;
		this.styledNodes.forEach(node -> node.pseudoClassStateChanged(SELECTED, selected));
	}

	@Override
	public void setDimmed(final boolean dimmed) {
		this.dimmed = dimmed;
		if (this.graphic != null) {
			this.graphic.setOpacity(dimmed ? DIMMED_OPACITY : 1);
		}
	}

	private void styled(final Node node) {
		this.styledNodes.add(node);
		node.pseudoClassStateChanged(FAILED, this.failed);
		node.pseudoClassStateChanged(SELECTED, this.selected);
	}

	/** Runs the click action on a click that did not move: a drag that pans the diagram does not count. */
	private void installClick(final Node node) {
		double[] pressedAt = new double[2];
		node.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
			pressedAt[0] = event.getScreenX();
			pressedAt[1] = event.getScreenY();
		});
		node.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
			boolean still = Math.abs(event.getScreenX() - pressedAt[0]) <= CLICK_TOLERANCE
					&& Math.abs(event.getScreenY() - pressedAt[1]) <= CLICK_TOLERANCE;
			if (event.getButton() == MouseButton.PRIMARY && still && this.onClicAction != null) {
				this.onClicAction.run();
				event.consume();
			}
		});
		node.setCursor(Cursor.HAND);
	}

	private class EdgeGraphic extends Pane {
		private static final double LABEL_GAP = 8.0;

		private static final double CALL_LABEL_MIN_WIDTH = 250.0;

		private static final double RETURN_LABEL_MIN_WIDTH = 84.0;

		private static final double HEAD_LENGTH = 11.0;

		private static final double HEAD_HALF_WIDTH = 4.5;

		private final Group group = new Group();

		private final Arrow arrowEnd = new Arrow();

		private final Label text;

		public EdgeGraphic(final Graph graph, final MessageEdgeImpl edge) {
			this.setPickOnBounds(false);
			final DoubleBinding sourceX = edge.getSource().getXAnchor(graph);
			final DoubleBinding sourceY = edge.getSource().getYAnchor(graph).add(edge.yOffsetProperty);
			final DoubleBinding targetX = edge.getTarget().getXAnchor(graph);
			final DoubleBinding targetY = edge.getTarget().getYAnchor(graph).add(edge.yOffsetProperty);

			this.styleArrow(this.arrowEnd);

			this.arrowEnd.startXProperty().bind(sourceX);
			this.arrowEnd.startYProperty().bind(sourceY);

			this.arrowEnd.endXProperty().bind(targetX);
			this.arrowEnd.endYProperty().bind(targetY);

			// The arrow is a thin stroke, hard to point at: an invisible wider band
			// along it takes the pointer for it.
			Line hitBand = new Line();
			hitBand.startXProperty().bind(sourceX);
			hitBand.startYProperty().bind(sourceY);
			hitBand.endXProperty().bind(targetX);
			hitBand.endYProperty().bind(targetY);
			hitBand.setStroke(Color.TRANSPARENT);
			hitBand.setStrokeWidth(HIT_WIDTH);
			edge.installClick(hitBand);

			this.group.getChildren().addAll(hitBand, this.arrowEnd);

			final DoubleProperty textWidth = new SimpleDoubleProperty();

			this.text = new Label();
			int split = edge.name.indexOf('\n');
			if (split >= 0) {
				Label port = new Label(edge.name.substring(0, split) + ".");
				port.getStyleClass().add("seq-label-port");
				Label method = new Label(edge.name.substring(split + 1));
				method.getStyleClass().add("seq-label-method");
				this.text.setGraphic(new HBox(port, method));
			} else {
				this.text.setText(edge.name);
			}
			this.text.setWrapText(false);
			this.text.setAlignment(Pos.CENTER);
			this.text.setMinWidth(edge.style == MessageStyle.RETURN ? RETURN_LABEL_MIN_WIDTH : CALL_LABEL_MIN_WIDTH);
			this.text.getStyleClass().addAll("seq-message-label",
					edge.style == MessageStyle.RETURN ? "seq-return" : "seq-call");
			this.text.layoutXProperty().bind(sourceX.add(targetX).divide(2).subtract(textWidth.divide(2)));
			this.text.layoutYProperty().bind(sourceY.add(LABEL_GAP));
			this.text.widthProperty().addListener((obs, oldVal, newVal) -> textWidth.set(newVal.doubleValue()));
			this.group.getChildren().add(this.text);
			this.text.setPickOnBounds(true);
			edge.installClick(this.text);
			edge.styled(this.text);

			if (edge.timeText != null) {
				Label time = new Label(edge.timeText);
				time.getStyleClass().add("seq-time");
				time.setLayoutX(TIME_X);
				time.layoutYProperty().bind(sourceY.subtract(8));
				time.setMouseTransparent(true);
				this.group.getChildren().add(time);
				edge.styled(time);
			}

			this.getChildren().add(this.group);
			this.getStyleClass().add("message-edge");
		}

		/**
		 * UML arrowheads: filled for a synchronous call, half open for an asynchronous one,
		 * open on a dashed line for a return.
		 */
		private void styleArrow(final Arrow arrow) {
			MessageStyle messageStyle = MessageEdgeImpl.this.style;
			List<Node> parts = arrow.getChildren();
			for (int index = 0; index < parts.size(); index++) {
				if (parts.get(index) instanceof Line line) {
					boolean shaft = index == 0;
					if (messageStyle == MessageStyle.RETURN) {
						line.setStrokeWidth(1.2);
						if (shaft) {
							line.getStrokeDashArray().setAll(6d, 6d);
						}
						line.getStyleClass().add("seq-return-line");
					} else {
						line.setStrokeWidth(1.4);
						line.getStyleClass().add("seq-call-line");
					}
					// Barbs: index 1 and 2. A synchronous call replaces both with a filled
					// head; an asynchronous one keeps only the upper one.
					if (!shaft && (messageStyle == MessageStyle.CALL
							|| messageStyle == MessageStyle.ASYNC_CALL && index == 2)) {
						line.setVisible(false);
					}
					MessageEdgeImpl.this.styled(line);
				}
			}
			if (messageStyle == MessageStyle.CALL) {
				Polygon head = new Polygon();
				head.getStyleClass().add("seq-call-head");
				Runnable placeHead = () -> {
					double endX = arrow.getEndX();
					double endY = arrow.getEndY();
					double length = Math.hypot(arrow.getStartX() - endX, arrow.getStartY() - endY);
					if (length == 0) {
						head.getPoints().clear();
						return;
					}
					double ux = (arrow.getStartX() - endX) / length;
					double uy = (arrow.getStartY() - endY) / length;
					double baseX = endX + ux * HEAD_LENGTH;
					double baseY = endY + uy * HEAD_LENGTH;
					head.getPoints().setAll(endX, endY, baseX - uy * HEAD_HALF_WIDTH, baseY + ux * HEAD_HALF_WIDTH,
							baseX + uy * HEAD_HALF_WIDTH, baseY - ux * HEAD_HALF_WIDTH);
				};
				arrow.startXProperty().addListener(o -> placeHead.run());
				arrow.startYProperty().addListener(o -> placeHead.run());
				arrow.endXProperty().addListener(o -> placeHead.run());
				arrow.endYProperty().addListener(o -> placeHead.run());
				placeHead.run();
				this.group.getChildren().add(head);
				MessageEdgeImpl.this.styled(head);
			}
		}
	}
}
