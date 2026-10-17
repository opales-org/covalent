/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.graph;

import java.util.function.Consumer;

import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * Lets the user drag the nodes of a graph with a mouse button, whatever the zoom. Each drag step and the end of a
 * drag are reported to consumers. The gestures also remember whether the last press and release moved the node,
 * so that a click handler can tell a click from a drag.
 */
public class NodeGestures {

	/** Style class of the sequence diagram actors, which only move horizontally. */
	private static final String HORIZONTAL_ONLY_STYLE_CLASS = "actor-cell";

	private static final Consumer<Node> IGNORE = node -> {
	};

	private final Graph graph;
	private MouseButton dragButton = MouseButton.PRIMARY;
	private Node lastTransparentNode;

	/** Distance between the mouse and the node origin when the drag started, in screen units. */
	private double grabOffsetX;
	private double grabOffsetY;

	private boolean nodeDragged;

	/**
	 * Whether the last press and release on a node moved it. It is set when the button is released, before
	 * {@link #nodeDragged} is cleared: JavaFX sends the click event after the release even if the mouse moved, so
	 * a click handler reads this flag to skip selecting a node that was only dragged.
	 */
	private boolean lastGestureWasDrag;

	private Consumer<Node> nodeDraggedConsumer = IGNORE;
	private Consumer<Node> nodeDragFinishedConsumer = IGNORE;

	private final EventHandler<MouseEvent> onPressed = this::handlePressed;
	private final EventHandler<MouseEvent> onDragged = this::handleDragged;
	private final EventHandler<MouseEvent> onReleased = this::handleReleased;

	public NodeGestures(final Graph graph) {
		this.graph = graph;
	}

	public void setDragButton(final MouseButton dragButton) {
		this.dragButton = dragButton;
	}

	public MouseButton getDragButton() {
		return this.dragButton;
	}

	/**
	 * @return whether the last press and release on a node moved it, as opposed to a plain click
	 */
	public boolean wasLastGestureADrag() {
		return this.lastGestureWasDrag;
	}

	public void setOnNodeDragFinished(final Consumer<Node> consumer) {
		this.nodeDragFinishedConsumer = consumer == null ? IGNORE : consumer;
	}

	public void setOnNodeDragged(final Consumer<Node> consumer) {
		this.nodeDraggedConsumer = consumer == null ? IGNORE : consumer;
	}

	public void makeDraggable(final Node node) {
		node.setOnMousePressed(this.onPressed);
		node.setOnMouseDragged(this.onDragged);
		node.setOnMouseReleased(this.onReleased);
	}

	public void makeUndraggable(final Node node) {
		node.setOnMousePressed(null);
		node.setOnMouseDragged(null);
		node.setOnMouseReleased(null);
	}

	public void revertLastNodeTransparency() {
		this.setMouseTransparent(this.lastTransparentNode, false);
	}

	private void setMouseTransparent(final Node node, final boolean transparent) {
		if (node == null) {
			return;
		}
		node.setMouseTransparent(transparent);
		if (node instanceof Parent parent) {
			for (final Node child : parent.getChildrenUnmodifiable()) {
				this.setMouseTransparent(child, transparent);
			}
		}
	}

	private void handlePressed(final MouseEvent event) {
		final Node node = (Node) event.getSource();
		if (event.getButton() == this.dragButton) {
			this.nodeDragged = false;
			final double scale = this.graph.getScale();
			this.grabOffsetX = node.getBoundsInParent().getMinX() * scale - event.getScreenX();
			this.grabOffsetY = node.getBoundsInParent().getMinY() * scale - event.getScreenY();
			event.consume();
		} else if (event.getButton() == this.graph.getViewportGestures().getPanButton()) {
			// The pan button was pressed over a node: let the mouse events go through the node so that the
			// canvas can pan. The transparency is restored when the pan button is released.
			this.lastTransparentNode = node;
			this.setMouseTransparent(node, true);
		}
	}

	private void handleDragged(final MouseEvent event) {
		if (event.getButton() != this.dragButton) {
			// Not the drag button: leave the event to the parent.
			return;
		}
		final Node node = (Node) event.getSource();
		final double scale = this.graph.getScale();
		final double x = (event.getScreenX() + this.grabOffsetX) / scale;
		double y = (event.getScreenY() + this.grabOffsetY) / scale;
		if (node.getStyleClass().contains(HORIZONTAL_ONLY_STYLE_CLASS)) {
			y = 0;
		}
		node.relocate(x, y);
		this.nodeDragged = true;
		this.nodeDraggedConsumer.accept(node);
		event.consume();
	}

	private void handleReleased(final MouseEvent event) {
		this.lastGestureWasDrag = this.nodeDragged;
		if (this.nodeDragged) {
			this.nodeDragged = false;
			this.nodeDragFinishedConsumer.accept((Node) event.getSource());
			event.consume();
		}
	}

}
