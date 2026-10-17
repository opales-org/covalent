/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.edges;

import java.util.List;
import java.util.function.Supplier;

import org.opales.covalent.supervision.componentview.internal.diagram.cells.CovalentComponentCell;
import org.opales.covalent.supervision.componentview.internal.diagram.edges.OrthogonalRouter.Card;
import org.opales.covalent.supervision.componentview.internal.diagram.edges.OrthogonalRouter.Connection;
import org.opales.covalent.supervision.componentview.internal.diagram.edges.OrthogonalRouter.Direction;
import org.opales.covalent.supervision.componentview.internal.diagram.edges.OrthogonalRouter.Point;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.beans.value.ObservableValue;
import javafx.scene.layout.Region;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.QuadCurveTo;
import javafx.scene.shape.StrokeLineJoin;

/** Reactive endpoints and JavaFX rendering for a wire owned by one graph's routing pass. */
public final class RoutedLine extends Path {
	/** Visible card bounds, excluding the pane reserved for port symbols and labels. */
	public record CardBounds(ObservableDoubleValue left, ObservableDoubleValue top,
			ObservableDoubleValue width, ObservableDoubleValue height) {
		public static CardBounds of(Region graphic, CovalentComponentCell component) {
			return new CardBounds(graphic.layoutXProperty().add(component.getCardLeftX()),
					graphic.layoutYProperty().add(component.getCardTopY()),
					new SimpleDoubleProperty(component.getCardRightX() - component.getCardLeftX()),
					new SimpleDoubleProperty(component.getCardBottomY() - component.getCardTopY()));
		}

		/** The bounds of a whole graphic, following its position and size. */
		public static CardBounds of(Region graphic) {
			return new CardBounds(graphic.layoutXProperty(), graphic.layoutYProperty(),
					graphic.widthProperty(), graphic.heightProperty());
		}

		Card snapshot() {
			return new Card(left.get(), top.get(), left.get() + width.get(), top.get() + height.get());
		}
	}

	private final String routingKey;
	private final Region owner;
	private final ObservableDoubleValue startX;
	private final ObservableDoubleValue startY;
	private final ObservableDoubleValue endX;
	private final ObservableDoubleValue endY;
	private final Supplier<Direction> startDirection;
	private final Supplier<Direction> endDirection;
	private final CardBounds source;
	private final CardBounds target;
	private final ObservableValue<EdgeRoutingStyle> style;
	/** Relation-name label anchor: true midpoint-by-arc-length along the routed polyline. */
	private final DoubleProperty labelX = new SimpleDoubleProperty();
	private final DoubleProperty labelY = new SimpleDoubleProperty();

	public RoutedLine(EdgeRouting routing, String routingKey, Region owner,
			ObservableDoubleValue startX, ObservableDoubleValue startY,
			ObservableDoubleValue endX, ObservableDoubleValue endY,
			Supplier<Direction> startDirection, Supplier<Direction> endDirection,
			CardBounds source, CardBounds target, ObservableValue<EdgeRoutingStyle> style) {
		this.routingKey = routingKey;
		this.owner = owner;
		this.startX = startX;
		this.startY = startY;
		this.endX = endX;
		this.endY = endY;
		this.startDirection = startDirection;
		this.endDirection = endDirection;
		this.source = source;
		this.target = target;
		this.style = style;
		this.labelX.set((startX.get() + endX.get()) / 2);
		this.labelY.set((startY.get() + endY.get()) / 2);
		setFill(null);
		setStrokeLineJoin(StrokeLineJoin.ROUND);

		List<Observable> inputs = List.of(startX, startY, endX, endY, source.left, source.top,
				source.width, source.height, target.left, target.top, target.width, target.height,
				style, owner.visibleProperty());
		InvalidationListener changed = observable -> routing.requestLayout();
		// Graphs can rebuild or remove edge graphics. Only attached graphics
		// participate, and shared-property listeners are released on removal.
		owner.parentProperty().addListener((observable, oldParent, newParent) -> {
			if (oldParent != null) {
				inputs.forEach(input -> input.removeListener(changed));
				routing.remove(this);
			}
			if (newParent != null) {
				inputs.forEach(input -> input.addListener(changed));
				routing.add(this);
			}
		});
		if (owner.getParent() != null) {
			inputs.forEach(input -> input.addListener(changed));
			routing.add(this);
		}
	}

	String routingKey() { return routingKey; }
	boolean isWireVisible() { return owner.isVisible(); }
	boolean isOrthogonal() { return style.getValue() != EdgeRoutingStyle.STRAIGHT; }
	boolean isRounded() { return style.getValue() == EdgeRoutingStyle.ROUND; }

	/** Bindable X of the relation-name label, kept at the route's true midpoint-by-arc-length. */
	public DoubleProperty labelXProperty() { return labelX; }

	/** Bindable Y of the relation-name label, kept at the route's true midpoint-by-arc-length. */
	public DoubleProperty labelYProperty() { return labelY; }

	Connection connection() {
		return new Connection(new Point(startX.get(), startY.get()), startDirection.get(), source.snapshot(),
				new Point(endX.get(), endY.get()), endDirection.get(), target.snapshot());
	}

	// Corners are not softened by a fixed distance: each interior waypoint
	// becomes a quadratic Bezier
	// control point, ending at the midpoint to the next waypoint - a
	// classic polyline-smoothing technique where the curve's visible size
	// scales with the leg lengths themselves (short legs stay subtle, long
	// ones sweep visibly), rather than being capped at a few pixels
	// regardless of the wire's own geometry.
	void render(List<Point> points) {
		getElements().clear();
		if (points.isEmpty()) {
			return;
		}
		updateLabelPosition(points);
		getElements().add(new MoveTo(points.getFirst().x(), points.getFirst().y()));
		if (!isRounded() || points.size() < 3) {
			for (int i = 1; i < points.size(); i++) {
				getElements().add(new LineTo(points.get(i).x(), points.get(i).y()));
			}
			return;
		}
		for (int i = 1; i < points.size() - 1; i++) {
			Point corner = points.get(i);
			Point next = points.get(i + 1);
			getElements().add(new QuadCurveTo(corner.x(), corner.y(),
					(corner.x() + next.x()) / 2, (corner.y() + next.y()) / 2));
		}
		getElements().add(new LineTo(points.getLast().x(), points.getLast().y()));
	}

	/**
	 * Positions the relation-name label at the polyline's true midpoint by
	 * arc length (half of the total route length, walked segment by
	 * segment), rather than the naive straight-line midpoint between the
	 * two endpoints - on an elbowed route with two long arms either side of
	 * a single bend, the naive midpoint can land far from the visible path.
	 */
	private void updateLabelPosition(List<Point> points) {
		if (points.size() == 1) {
			labelX.set(points.get(0).x());
			labelY.set(points.get(0).y());
			return;
		}
		double totalLength = 0;
		double[] segmentLengths = new double[points.size() - 1];
		for (int i = 0; i < segmentLengths.length; i++) {
			Point a = points.get(i);
			Point b = points.get(i + 1);
			segmentLengths[i] = Math.hypot(b.x() - a.x(), b.y() - a.y());
			totalLength += segmentLengths[i];
		}
		double half = totalLength / 2;
		double covered = 0;
		for (int i = 0; i < segmentLengths.length; i++) {
			double length = segmentLengths[i];
			boolean last = i == segmentLengths.length - 1;
			if (last || covered + length >= half) {
				double t = length > 0 ? (half - covered) / length : 0;
				Point a = points.get(i);
				Point b = points.get(i + 1);
				labelX.set(a.x() + (b.x() - a.x()) * t);
				labelY.set(a.y() + (b.y() - a.y()) * t);
				return;
			}
			covered += length;
		}
	}
}
