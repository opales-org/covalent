/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.edges;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.opales.covalent.supervision.componentview.internal.diagram.cells.PortPalette;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.Graph;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.Highlighting;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.Cell;
import org.opales.covalent.supervision.componentview.internal.diagram.edges.OrthogonalRouter.Direction;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.QuadCurveTo;

/**
 * A directed link between two whole cells, drawn with the graph's routing
 * style. Each end sits in the middle of the side of its cell that faces the
 * other cell: left or right when the cells are side by side, top or bottom
 * when they overlap horizontally. An arrowhead marks the target end.
 */
public class DependencyEdge extends AbstractEdge {

	private static final double ARROW_LENGTH = 10;
	private static final double ARROW_HALF_WIDTH = 4.5;
	private static final double DIAMOND_RADIUS = 9;
	private static final double DIAMOND_HALF_WIDTH = 6;
	/** Wide invisible stroke a pointer can actually land on; the drawn line itself is much thinner. */
	private static final double HIT_STROKE_WIDTH = 12;
	private static final double HIGHLIGHT_STROKE_WIDTH = 3.0;

	private final BooleanProperty highlighted = new SimpleBooleanProperty(false);
	private PauseTransition highlightTransition;
	private Consumer<ContextMenuEvent> onContextMenuConsumer;

	public DependencyEdge(final Cell source, final Cell target) {
		super(source, target, true);
	}

	@Override
	public EdgeGraphic getGraphic(final Graph graph) {
		EdgeGraphic graphic = new EdgeGraphic(graph, this);
		graphic.setVisible(this.visible.get());
		this.visible.addListener((s, o, n) -> graphic.setVisible(n.booleanValue()));
		return graphic;
	}

	public ReadOnlyBooleanProperty highlightedProperty() {
		return this.highlighted;
	}

	/**
	 * The link's own right-click (or platform context-menu key) gesture. The
	 * consumer is responsible for showing and for consuming the event.
	 */
	public void onContextMenuRequested(final Consumer<ContextMenuEvent> onContextMenuConsumer) {
		this.onContextMenuConsumer = onContextMenuConsumer;
	}

	/**
	 * Click-to-highlight: briefly paints this link in {@link PortPalette#HIGHLIGHT}
	 * - the same color the component diagram's own link highlight uses -
	 * before automatically reverting to the color it had
	 * (a plain link, or a step of an upstream/downstream highlight).
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

	/**
	 * Graphic of a {@link DependencyEdge}: a routed line between the facing sides of two cells, an arrowhead at the
	 * target end - an open one when it is a composition, with a diamond at the source end - and an invisible wider
	 * band that takes the pointer gestures.
	 */
	public static class EdgeGraphic extends Pane {

		private final DependencyEdge edge;
		private final RoutedLine line;
		private final Polygon arrowHead = new Polygon();
		/** The composite's end of a composition link, hidden otherwise. */
		private final Polygon diamond = new Polygon();
		/** The child's end of a composition link, an open arrowhead (navigability), hidden otherwise. */
		private final Polyline openArrowHead = new Polyline();
		private Color baseColor = Color.BLACK;
		private boolean baseDashed;
		/** The ends of the link, from its source to its target. */
		private final DoubleBinding[] ends;

		public EdgeGraphic(final Graph graph, final DependencyEdge edge) {
			this.edge = edge;
			this.setPickOnBounds(false);
			Region source = graph.getGraphic(edge.getSource());
			Region target = graph.getGraphic(edge.getTarget());

			Supplier<Direction> sourceSide = () -> facingSide(source, target);
			Supplier<Direction> targetSide = () -> facingSide(target, source);
			DoubleBinding startX = sideX(source, target, sourceSide);
			DoubleBinding startY = sideY(source, target, sourceSide);
			DoubleBinding endX = sideX(target, source, targetSide);
			DoubleBinding endY = sideY(target, source, targetSide);
			this.ends = new DoubleBinding[] { startX, startY, endX, endY };

			String routingKey = "dependency:" + System.identityHashCode(edge.getSource()) + "->"
					+ System.identityHashCode(edge.getTarget());
			this.line = new RoutedLine(graph.getEdgeRouting(), routingKey, this, startX, startY, endX, endY,
					sourceSide, targetSide, RoutedLine.CardBounds.of(source), RoutedLine.CardBounds.of(target),
					graph.edgeRoutingStyleProperty());
			this.line.setStrokeWidth(1.2);

			this.arrowHead.fillProperty().bind(this.line.strokeProperty());
			Runnable placeArrowHead = () -> {
				double[] end = routeEnd(this.line, false);
				double tipX = end != null ? end[0] : endX.get();
				double tipY = end != null ? end[1] : endY.get();
				// Pointing along the route's last segment, whatever the routing style.
				double towardX = end != null ? end[2] : -targetSide.get().dx;
				double towardY = end != null ? end[3] : -targetSide.get().dy;
				double baseX = tipX - towardX * ARROW_LENGTH;
				double baseY = tipY - towardY * ARROW_LENGTH;
				this.arrowHead.getPoints().setAll(tipX, tipY,
						baseX - towardY * ARROW_HALF_WIDTH, baseY + towardX * ARROW_HALF_WIDTH,
						baseX + towardY * ARROW_HALF_WIDTH, baseY - towardX * ARROW_HALF_WIDTH);
				this.openArrowHead.getPoints().setAll(
						baseX - towardY * ARROW_HALF_WIDTH, baseY + towardX * ARROW_HALF_WIDTH, tipX, tipY,
						baseX + towardY * ARROW_HALF_WIDTH, baseY - towardX * ARROW_HALF_WIDTH);
			};
			this.openArrowHead.strokeProperty().bind(this.line.strokeProperty());
			this.openArrowHead.strokeWidthProperty().bind(this.line.strokeWidthProperty());
			this.openArrowHead.setFill(null);
			this.openArrowHead.setVisible(false);
			endX.addListener(o -> placeArrowHead.run());
			endY.addListener(o -> placeArrowHead.run());
			placeArrowHead.run();

			this.diamond.fillProperty().bind(this.line.strokeProperty());
			this.diamond.setVisible(false);
			Runnable placeDiamond = () -> {
				double[] start = routeEnd(this.line, true);
				double tipX = start != null ? start[0] : startX.get();
				double tipY = start != null ? start[1] : startY.get();
				// Leaving along the route's first segment, whatever the routing style.
				double awayX = start != null ? start[2] : sourceSide.get().dx;
				double awayY = start != null ? start[3] : sourceSide.get().dy;
				double centerX = tipX + awayX * DIAMOND_RADIUS;
				double centerY = tipY + awayY * DIAMOND_RADIUS;
				this.diamond.getPoints().setAll(tipX, tipY,
						centerX - awayY * DIAMOND_HALF_WIDTH, centerY + awayX * DIAMOND_HALF_WIDTH,
						tipX + awayX * DIAMOND_RADIUS * 2, tipY + awayY * DIAMOND_RADIUS * 2,
						centerX + awayY * DIAMOND_HALF_WIDTH, centerY - awayX * DIAMOND_HALF_WIDTH);
			};
			startX.addListener(o -> placeDiamond.run());
			startY.addListener(o -> placeDiamond.run());
			placeDiamond.run();
			// The routing pass rewrites the path after the endpoints move, so the
			// ends are re-oriented from the route itself each time it changes.
			this.line.getElements().addListener((InvalidationListener) o -> {
				placeArrowHead.run();
				placeDiamond.run();
			});

			// The link itself is a thin stroke, hard to point at: an invisible,
			// wider copy of its path, under it, takes the pointer gestures for
			// it - same technique as the component diagram's own AnchoredPortEdge.
			Path hitArea = new Path();
			Bindings.bindContent(hitArea.getElements(), this.line.getElements());
			hitArea.setFill(null);
			hitArea.setStroke(Color.TRANSPARENT);
			hitArea.setStrokeWidth(HIT_STROKE_WIDTH);
			hitArea.setCursor(Cursor.HAND);
			for (Node pickable : List.of(hitArea, this.line)) {
				pickable.setOnContextMenuRequested(e -> {
					if (edge.onContextMenuConsumer != null) {
						edge.onContextMenuConsumer.accept(e);
					}
				});
				pickable.setOnMouseClicked(e -> {
					if (e.getButton() == MouseButton.PRIMARY) {
						edge.highlight();
						e.consume();
					}
				});
			}

			edge.highlighted.addListener((s, o, n) -> this.applyStroke());
			this.getChildren().addAll(hitArea, this.line, this.arrowHead, this.openArrowHead, this.diamond);
		}

		/**
		 * Draws the link as a composition, from the composite (the source) to
		 * its child: a solid line with a filled diamond at the composite's end
		 * and an open arrowhead at the child's, as a navigable UML composition.
		 *
		 * @param color the link's color
		 */
		public void setComposition(final Color color) {
			this.baseColor = color;
			this.baseDashed = false;
			this.arrowHead.setVisible(false);
			this.openArrowHead.setVisible(true);
			this.diamond.setVisible(true);
			this.applyStroke();
		}

		/**
		 * Draws the link aside from the straight line between its cells, to its right as it goes
		 * from its source to its target: a link and its reverse, both shifted so, run side by
		 * side instead of over each other, where their dashes would merge into one solid line.
		 *
		 * @param offset how far aside, in pixels
		 */
		public void setParallelOffset(final double offset) {
			DoubleBinding[] at = this.ends;
			this.translateXProperty().bind(Bindings.createDoubleBinding(
					() -> -unitY(at) * offset, at));
			this.translateYProperty().bind(Bindings.createDoubleBinding(
					() -> unitX(at) * offset, at));
		}

		private static double unitX(final DoubleBinding[] at) {
			double length = Math.hypot(at[2].get() - at[0].get(), at[3].get() - at[1].get());
			return length < 1e-6 ? 0 : (at[2].get() - at[0].get()) / length;
		}

		private static double unitY(final DoubleBinding[] at) {
			double length = Math.hypot(at[2].get() - at[0].get(), at[3].get() - at[1].get());
			return length < 1e-6 ? 0 : (at[3].get() - at[1].get()) / length;
		}

		/** Colors the link; a dashed link keeps a solid arrowhead. */
		public void setStroke(final Color color, final boolean dashed) {
			this.baseColor = color;
			this.baseDashed = dashed;
			this.applyStroke();
		}

		/**
		 * Applies {@link #baseColor}/{@link #baseDashed} - or, while {@code
		 * edge.highlighted} is set, {@link PortPalette#HIGHLIGHT} instead - so
		 * a passing click-to-highlight flash never needs the caller to
		 * remember and restore the color the link was showing
		 * (a plain link, or a step of an upstream/downstream highlight).
		 */
		private void applyStroke() {
			if (this.edge.highlighted.get()) {
				this.line.setStroke(PortPalette.HIGHLIGHT);
				this.line.setStrokeWidth(HIGHLIGHT_STROKE_WIDTH);
				this.line.getStrokeDashArray().clear();
				return;
			}
			this.line.setStroke(this.baseColor);
			this.line.setStrokeWidth(1.2);
			if (this.baseDashed) {
				this.line.getStrokeDashArray().setAll(6.0, 4.0);
			} else {
				this.line.getStrokeDashArray().clear();
			}
		}

		/**
		 * One end of a route: its position and the unit direction of the end
		 * segment, pointing into the end when {@code start} is {@code false}
		 * and away from the start when it is {@code true}.
		 *
		 * @return {@code {x, y, dirX, dirY}}, or {@code null} when the route
		 *         has no segment yet
		 */
		private static double[] routeEnd(final RoutedLine route, final boolean start) {
			List<PathElement> elements = route.getElements();
			int count = elements.size();
			if (count < 2) {
				return null;
			}
			double[] end = elementPoint(elements.get(start ? 0 : count - 1));
			double[] neighbor = elementPoint(elements.get(start ? 1 : count - 2));
			if (end == null || neighbor == null) {
				return null;
			}
			double dx = start ? neighbor[0] - end[0] : end[0] - neighbor[0];
			double dy = start ? neighbor[1] - end[1] : end[1] - neighbor[1];
			double length = Math.hypot(dx, dy);
			if (length < 1e-6) {
				return null;
			}
			return new double[] { end[0], end[1], dx / length, dy / length };
		}

		private static double[] elementPoint(final PathElement element) {
			if (element instanceof MoveTo moveTo) {
				return new double[] { moveTo.getX(), moveTo.getY() };
			}
			if (element instanceof LineTo lineTo) {
				return new double[] { lineTo.getX(), lineTo.getY() };
			}
			if (element instanceof QuadCurveTo curveTo) {
				return new double[] { curveTo.getX(), curveTo.getY() };
			}
			return null;
		}

		private static Direction facingSide(final Region cell, final Region other) {
			double cellLeft = cell.getLayoutX();
			double cellRight = cellLeft + cell.getWidth();
			double otherLeft = other.getLayoutX();
			double otherRight = otherLeft + other.getWidth();
			if (otherLeft >= cellRight) {
				return Direction.RIGHT;
			}
			if (otherRight <= cellLeft) {
				return Direction.LEFT;
			}
			double cellCenterY = cell.getLayoutY() + cell.getHeight() / 2;
			double otherCenterY = other.getLayoutY() + other.getHeight() / 2;
			return otherCenterY < cellCenterY ? Direction.TOP : Direction.BOTTOM;
		}

		private static DoubleBinding sideX(final Region cell, final Region other, final Supplier<Direction> side) {
			return Bindings.createDoubleBinding(() -> switch (side.get()) {
			case LEFT -> cell.getLayoutX();
			case RIGHT -> cell.getLayoutX() + cell.getWidth();
			default -> cell.getLayoutX() + cell.getWidth() / 2;
			}, dependencies(cell, other));
		}

		private static DoubleBinding sideY(final Region cell, final Region other, final Supplier<Direction> side) {
			return Bindings.createDoubleBinding(() -> switch (side.get()) {
			case TOP -> cell.getLayoutY();
			case BOTTOM -> cell.getLayoutY() + cell.getHeight();
			default -> cell.getLayoutY() + cell.getHeight() / 2;
			}, dependencies(cell, other));
		}

		private static Observable[] dependencies(final Region cell, final Region other) {
			return new Observable[] { cell.layoutXProperty(), cell.layoutYProperty(),
					cell.widthProperty(), cell.heightProperty(), other.layoutXProperty(), other.layoutYProperty(),
					other.widthProperty(), other.heightProperty() };
		}
	}
}
