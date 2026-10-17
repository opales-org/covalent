/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.graph;

import java.util.function.DoubleConsumer;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.EventHandler;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;

/**
 * Listeners for making the scene's viewport draggable and zoomable
 */
public class ViewportGestures {

	private final DoubleProperty zoomSpeedProperty = new SimpleDoubleProperty(1.2d);
	/** Zoom bounds every diagram shares: beyond them, a diagram is either unreadable or mostly blank. */
	public static final double DEFAULT_MIN_SCALE = 0.1;
	public static final double DEFAULT_MAX_SCALE = 2.0;

	/** How much of a content larger than the viewport may be pushed past its edges, in pixels, when it must fill it. */
	private static final double OVERSCROLL = 60;

	/** The part of the content that always stays in view, in pixels, along each axis, when it may be moved freely. */
	private static final double MIN_VISIBLE = 120;

	private final DoubleProperty maxScaleProperty = new SimpleDoubleProperty(DEFAULT_MAX_SCALE);
	private final DoubleProperty minScaleProperty = new SimpleDoubleProperty(DEFAULT_MIN_SCALE);

	/** Mouse position, in scene coordinates, and canvas translation when a pan started. */
	private double panGrabSceneX;
	private double panGrabSceneY;
	private double panStartTranslateX;
	private double panStartTranslateY;
	private final Graph graph;
	private final PannableCanvas canvas;
	private MouseButton panButton = MouseButton.PRIMARY;

	/** Whether the wheel scrolls, zooming only with Ctrl held - see {@link #setWheelScrolls}. */
	private boolean wheelScrolls = false;

	/** Whether the content must keep filling the viewport - see {@link #setContentFillsViewport}. */
	private boolean contentFillsViewport = false;

	/** Told of each wheel scroll, with the vertical distance, positive towards the top. */
	private DoubleConsumer onWheelScroll = distance -> {
	};


	public ViewportGestures(final Graph graph) {
		this.graph = graph;
		this.canvas = graph.getCanvas();
	}

	/**
	 * Chooses what the wheel does. By default it zooms, which suits a graph read like a map. A
	 * diagram read like a document, from top to bottom, rather scrolls with it: then the wheel
	 * scrolls vertically, Shift + wheel horizontally, and Ctrl + wheel zooms.
	 *
	 * @param wheelScrolls whether the wheel scrolls
	 */
	public void setWheelScrolls(final boolean wheelScrolls) {
		this.wheelScrolls = wheelScrolls;
	}

	/**
	 * Chooses how far the content may be moved. By default it moves freely, as long as a part of it
	 * stays in view: any edge of the content can be brought to any edge of the viewport. A diagram
	 * whose position means something, like a timeline, rather keeps filling the viewport: content
	 * larger than the viewport never leaves a gap beyond a small overscroll, and smaller content
	 * stays entirely inside it.
	 *
	 * @param contentFillsViewport whether the content keeps filling the viewport
	 */
	public void setContentFillsViewport(final boolean contentFillsViewport) {
		this.contentFillsViewport = contentFillsViewport;
	}

	/**
	 * @param onWheelScroll told of each wheel scroll, with the vertical distance, positive towards the top;
	 *                      {@code null} for no one
	 */
	public void setOnWheelScroll(final DoubleConsumer onWheelScroll) {
		this.onWheelScroll = onWheelScroll == null ? distance -> {
		} : onWheelScroll;
	}

	public MouseButton getPanButton() {
		return panButton;
	}

	public void setPanButton(final MouseButton panButton) {
		this.panButton = panButton;
	}

	public EventHandler<MouseEvent> getOnMousePressedEventHandler() {
		return onMousePressedEventHandler;
	}

	public EventHandler<MouseEvent> getOnMouseDraggedEventHandler() {
		return onMouseDraggedEventHandler;
	}

	public EventHandler<MouseEvent> getOnMouseReleasedEventHandler() {
		return onMouseReleasedEventHandler;
	}

	public EventHandler<ScrollEvent> getOnScrollEventHandler() {
		return onScrollEventHandler;
	}

	private final EventHandler<MouseEvent> onMousePressedEventHandler = this::startPan;
	private final EventHandler<MouseEvent> onMouseDraggedEventHandler = this::continuePan;
	private final EventHandler<MouseEvent> onMouseReleasedEventHandler = this::endPan;

	private void startPan(final MouseEvent event) {
		if (event.getButton() == getPanButton()) {
			panGrabSceneX = event.getSceneX();
			panGrabSceneY = event.getSceneY();
			panStartTranslateX = canvas.getTranslateX();
			panStartTranslateY = canvas.getTranslateY();
			event.consume();
		}
	}

	private void continuePan(final MouseEvent event) {
		if (event.getButton() == getPanButton()) {
			canvas.setTranslateX(panStartTranslateX + event.getSceneX() - panGrabSceneX);
			canvas.setTranslateY(panStartTranslateY + event.getSceneY() - panGrabSceneY);
			keepContentInView();
			event.consume();
		}
	}

	private void endPan(final MouseEvent event) {
		if (event.getButton() == getPanButton()) {
			graph.getNodeGestures().revertLastNodeTransparency();
		}
	}

	/**
	 * Mouse wheel handler: zoom to pivot point, or scroll - see {@link #setWheelScrolls}.
	 */
	private final EventHandler<ScrollEvent> onScrollEventHandler = new EventHandler<ScrollEvent>() {

		@Override
		public void handle(final ScrollEvent event) {
			if (wheelScrolls && !event.isControlDown()) {
				scroll(event);
				return;
			}
			double scale = canvas.getScale(); // the scale is the same on both axes

			if (event.getDeltaY() < 0) {
				scale /= getZoomSpeed();
			} else if (event.getDeltaY() > 0) {
				scale *= getZoomSpeed();
			}

			zoomTo(scale, event.getSceneX(), event.getSceneY());

			event.consume();

		}

	};

	private void scroll(final ScrollEvent event) {
		double horizontal;
		double vertical;
		if (event.isShiftDown()) {
			// Some platforms already turn Shift + wheel into a horizontal delta.
			horizontal = event.getDeltaX() != 0 ? event.getDeltaX() : event.getDeltaY();
			vertical = 0;
		} else {
			horizontal = event.getDeltaX();
			vertical = event.getDeltaY();
		}
		this.canvas.setTranslateX(this.canvas.getTranslateX() + horizontal);
		this.canvas.setTranslateY(this.canvas.getTranslateY() + vertical);
		this.keepContentInView();
		if (vertical != 0) {
			this.onWheelScroll.accept(vertical);
		}
		event.consume();
	}

	/**
	 * Sets the canvas scale to {@code targetScale} (clamped to the configured
	 * zoom bounds), keeping the given scene point visually stationary. The
	 * mouse wheel handler above uses it for its pivot point, and any other
	 * zoom trigger (e.g. a toolbar slider) can zoom around an arbitrary
	 * point instead of only around the last scroll event's cursor position.
	 */
	public void zoomTo(final double targetScale, final double sceneX, final double sceneY) {
		final double scale = clamp(targetScale, minScaleProperty.get(), maxScaleProperty.get());
		// The canvas point under the pivot before the zoom is brought back under it after: measured
		// on the canvas's own transforms, whatever its size, position or overhang.
		final Point2D pivotLocal = canvas.sceneToLocal(sceneX, sceneY);
		canvas.setScale(scale);
		if (canvas.getParent() == null) {
			return;
		}
		final Point2D pivotInParent = canvas.getParent().sceneToLocal(sceneX, sceneY);
		final Point2D movedInParent = canvas.localToParent(pivotLocal);
		canvas.setTranslateX(canvas.getTranslateX() + pivotInParent.getX() - movedInParent.getX());
		canvas.setTranslateY(canvas.getTranslateY() + pivotInParent.getY() - movedInParent.getY());
		keepContentInView();
	}

	/**
	 * Moves the canvas so its content does not leave the viewport. By default a part of the content,
	 * at most {@value #MIN_VISIBLE} pixels along each axis, always stays in view, which leaves
	 * the rest free to go past any edge. With {@link #setContentFillsViewport}, content smaller than
	 * the viewport stays entirely inside it and larger content always fills it, give or take a small
	 * overscroll.
	 */
	public void keepContentInView() {
		if (canvas.getParent() == null) {
			return;
		}
		Bounds content = contentBoundsInParent();
		if (content == null) {
			return;
		}
		Bounds view = canvas.getParent().getLayoutBounds();
		canvas.setTranslateX(canvas.getTranslateX()
				+ correction(content.getMinX(), content.getMaxX(), view.getMinX(), view.getMaxX(),
						this.contentFillsViewport));
		canvas.setTranslateY(canvas.getTranslateY()
				+ correction(content.getMinY(), content.getMaxY(), view.getMinY(), view.getMaxY(),
						this.contentFillsViewport));
	}

	/**
	 * The shift that brings the content back into the allowed range along one axis; {@code 0} when
	 * it is in that range.
	 */
	static double correction(final double contentMin, final double contentMax, final double viewMin,
			final double viewMax, final boolean fillsViewport) {
		if (viewMax <= viewMin) {
			return 0;
		}
		if (!fillsViewport) {
			double visible = Math.min(MIN_VISIBLE, Math.min(contentMax - contentMin, viewMax - viewMin));
			if (contentMax < viewMin + visible) {
				return viewMin + visible - contentMax;
			}
			if (contentMin > viewMax - visible) {
				return viewMax - visible - contentMin;
			}
			return 0;
		}
		if (contentMax - contentMin <= viewMax - viewMin) {
			if (contentMin < viewMin) {
				return viewMin - contentMin;
			}
			if (contentMax > viewMax) {
				return viewMax - contentMax;
			}
			return 0;
		}
		if (contentMin > viewMin + OVERSCROLL) {
			return viewMin + OVERSCROLL - contentMin;
		}
		if (contentMax < viewMax - OVERSCROLL) {
			return viewMax - OVERSCROLL - contentMax;
		}
		return 0;
	}

	/** The visible children's bounds, in the canvas's parent's coordinates; {@code null} when none. */
	private Bounds contentBoundsInParent() {
		double minX = Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		for (Node child : canvas.getChildren()) {
			if (!child.isVisible()) {
				continue;
			}
			Bounds bounds = child.getBoundsInParent();
			if (bounds.isEmpty()) {
				continue;
			}
			minX = Math.min(minX, bounds.getMinX());
			minY = Math.min(minY, bounds.getMinY());
			maxX = Math.max(maxX, bounds.getMaxX());
			maxY = Math.max(maxY, bounds.getMaxY());
		}
		if (minX > maxX) {
			return null;
		}
		return canvas.localToParent(new BoundingBox(minX, minY, maxX - minX, maxY - minY));
	}

	public static double clamp(final double value, final double min, final double max) {
		if (Double.compare(value, min) < 0) {
			return min;
		}

		if (Double.compare(value, max) > 0) {
			return max;
		}

		return value;
	}

	public double getMinScale() {
		return minScaleProperty.get();
	}

	public DoubleProperty minScaleProperty() {
		return minScaleProperty;
	}

	public double getMaxScale() {
		return maxScaleProperty.get();
	}

	public DoubleProperty maxScaleProperty() {
		return maxScaleProperty;
	}

	public double getZoomSpeed() {
		return zoomSpeedProperty.get();
	}

	public DoubleProperty zoomSpeedProperty() {
		return zoomSpeedProperty;
	}
}