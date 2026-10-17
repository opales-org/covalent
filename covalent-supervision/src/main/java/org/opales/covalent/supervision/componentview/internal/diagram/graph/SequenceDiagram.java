/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.opales.covalent.supervision.componentview.internal.diagram.cells.AbstractCell;
import org.opales.covalent.supervision.componentview.internal.diagram.edges.MessageEdgeImpl;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.geometry.Point2D;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

/**
 * A sequence diagram: lifelines and messages on the zoomable canvas, and the actors' cards in
 * a separate band above it ({@link #getHeader()}). The band does not scroll vertically: each
 * card follows its lifeline horizontally, and keeps its size when zooming in, shrinking only
 * when zooming out so that cards never overlap.
 */
public class SequenceDiagram extends Graph {

	/** Room left of the first lifeline for the messages' times. */
	private static final double TIME_COLUMN_WIDTH = 72;

	/** Space above and below the cards in the band. */
	private static final double HEADER_PADDING = 10;

	private final Pane header = new Pane();
	private final List<ActorCellImpl> headerActors = new ArrayList<>();

	// The names are used the other way round: verticalSpacing is the horizontal distance between two
	// lifelines (see layout()), horizontalSpacing the vertical distance between two messages.
	private double verticalSpacing = 290;
	private double horizontalSpacing = 64;
	
	private List<ActorCell> actors = new ArrayList<>();
	private List<MessageEdge> messages = new ArrayList<>();

	/** How a message is drawn on a sequence diagram. */
	public enum MessageStyle {
		/** A synchronous call: filled arrowhead. */
		CALL,
		/** An asynchronous call, its caller not waiting: half arrowhead. */
		ASYNC_CALL,
		/** The return of a synchronous call: dashed line. */
		RETURN
	}
	
	public SequenceDiagram() {
		this.header.getStyleClass().add("seq-header");
		Rectangle clip = new Rectangle();
		clip.widthProperty().bind(this.header.widthProperty());
		clip.heightProperty().bind(this.header.heightProperty());
		this.header.setClip(clip);
		// Never squeezed by the tall canvas under it.
		this.header.setMinHeight(Region.USE_PREF_SIZE);
		// Change listeners: an invalidation listener on a transform stays silent until the
		// transform is read again, which layoutHeader does not do.
		this.getCanvas().localToSceneTransformProperty().addListener((o, before, after) -> this.layoutHeader());
		this.header.localToSceneTransformProperty().addListener((o, before, after) -> this.layoutHeader());
		this.header.widthProperty().addListener(o -> this.layoutHeader());
	}

	/** @return the band holding the actors' cards, to be placed right above the canvas */
	public Region getHeader() {
		return this.header;
	}

	/** Puts every card above its lifeline, at the lifelines' zoom. */
	public void layoutHeader() {
		double scale = this.getCanvas().getScale();
		double tallest = 0;
		for (ActorCellImpl actor : this.headerActors) {
			Region anchor = this.getGraphic(actor);
			Region card = actor.card;
			if (anchor == null || card == null) {
				continue;
			}
			card.applyCss();
			card.autosize();
			double width = card.getWidth();
			double height = card.getHeight();
			tallest = Math.max(tallest, height);
			card.setScaleX(scale);
			card.setScaleY(scale);
			if (this.header.getScene() == null || this.getCanvas().getScene() == null) {
				continue;
			}
			Point2D center = this.header.sceneToLocal(
					this.getCanvas().localToScene(anchor.getLayoutX() + anchor.getWidth() / 2, 0));
			// Scaling pivots on the card's center: shifted so its top stays at the padding.
			card.relocate(center.getX() - width / 2, HEADER_PADDING - height * (1 - scale) / 2);
		}
		// The band follows the cards' height, so zooming out leaves no empty strip above the messages.
		double bandHeight = tallest == 0 ? 0 : tallest * scale + 2 * HEADER_PADDING;
		this.header.setPrefHeight(bandHeight);
	}

	public void addActor(final String actor, final double length) {
		this.addActor(new ActorCellImpl(actor, new SimpleDoubleProperty(length)));
	}
	
	public void addActor(final ActorCell actor) {
		this.actors.add(actor);
		this.getModel().addCell(actor);
		this.endUpdate();
	}

	public MessageEdge addMessage(final ActorCell source, final ActorCell target, final String name) {
		return this.addMessage(source, target, name, MessageStyle.CALL);
	}

	public MessageEdge addMessage(final ActorCell source, final ActorCell target, final String name,
			final MessageStyle style) {
		return this.addMessage(new MessageEdgeImpl(source, target, name, style));
	}
	
	public MessageEdge addMessage(final MessageEdge edge) {
		this.messages.add(edge);
		this.getModel().addEdge(edge);
		this.endUpdate();
		return edge;
	}
	
	/** Removes every message, keeping the actors. */
	public void clearMessages() {
		for (MessageEdge edge : this.messages) {
			this.getModel().removeEdge(edge);
		}
		this.messages.clear();
		this.endUpdate();
	}

	public void layout() {
		AtomicInteger counter = new AtomicInteger();
		this.actors.stream().map(actor -> this.getGraphic(actor)).forEach(actor -> {
			actor.setLayoutX(TIME_COLUMN_WIDTH + counter.getAndIncrement() * this.verticalSpacing);
			actor.setLayoutY(0);
		});
		
		counter.set(0);
		this.messages.forEach(edge -> {
			edge.yOffsetProperty().set(counter.incrementAndGet() * this.horizontalSpacing);
		});
		this.layoutHeader();
	}

	public double getHorizontalSpacing() {
		return this.horizontalSpacing;
	}
	
	public List<ActorCell> getActors() {
		return this.actors;
	}
	
	public List<MessageEdge> getMessages() {
		return this.messages;
	}

	/** Actor of a sequence diagram: a cell with a name, drawn as a card in the header band over a lifeline. */
	public interface ActorCell extends Cell {
		
		String getName();
		
	}

	/**
	 * Message of a sequence diagram: an edge between two actors that can be offset vertically, clicked, timed,
	 * marked failed and marked selected.
	 */
	public interface MessageEdge extends Edge {

		DoubleProperty yOffsetProperty();

		void onClick(Runnable action);

		/** Sets the time drawn beside the message, before its graphic is created. */
		void setTimeText(String text);

		/** Marks the message as part of a failed invocation. */
		void setFailed(boolean failed);

		/** Marks the message as the one selected. */
		void setSelected(boolean selected);

		/** Fades the message, as one between actors not selected while others are. */
		void setDimmed(boolean dimmed);

	}
	
	/**
	 * Actor of a sequence diagram: a lifeline of a given length on the canvas, with a card in the header band that shows
	 * its name and kind, and can be selected and marked as recorded.
	 */
	public static class ActorCellImpl extends AbstractCell implements ActorCell {


		/** Toggled on the actor's card while it is selected, styled in supervision.css. */
		private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

		/** Set on the card of an actor that is part of what is being recorded. */
		private static final PseudoClass RECORDED = PseudoClass.getPseudoClass("recorded");

		private boolean recorded = false;

		private final String name;
		private final DoubleProperty lifeLineLength;

		/** What the actor stands for, shown under its name. */
		private final StringProperty kind = new SimpleStringProperty(GraphTexts.get("actor.kind.default"));

		private Line lifeLineGraphic;

		private VBox card;

		private Consumer<MouseEvent> onClickConsumer;
		
		public ActorCellImpl(final String name, final Double lifeLineLength) {
			this(name, new SimpleDoubleProperty(lifeLineLength));
		}
		
		public ActorCellImpl(final String name, final DoubleProperty lifeLineLength) {
			this.name = name;
			this.lifeLineLength = lifeLineLength;
		}

		/**
		 * The lifeline's anchor on the canvas: an empty region as wide as the card, which
		 * itself goes into the diagram's header band.
		 */
		@Override
		public Region getGraphic(final Graph graph) {
			Label recordedBadge = new Label(GraphTexts.get("actor.recorded"));
			recordedBadge.getStyleClass().add("seq-actor-recorded-badge");
			recordedBadge.setVisible(this.recorded);
			recordedBadge.setTranslateY(-9);
			recordedBadge.setTranslateX(-10);
			StackPane headerBand = new StackPane(recordedBadge);
			StackPane.setAlignment(recordedBadge, Pos.TOP_RIGHT);
			headerBand.setPrefHeight(14);
			headerBand.setMinHeight(14);
			headerBand.setMaxHeight(14);
			headerBand.getStyleClass().add("seq-actor-header");
			Label label = new Label(this.name);
			label.setAlignment(Pos.CENTER);
			label.setWrapText(true);
			label.setMaxWidth(Double.MAX_VALUE);
			label.getStyleClass().add("seq-actor-name");
			Label kindLabel = new Label();
			// Between guillemets, as the component diagram shows a stereotype.
			kindLabel.textProperty().bind(Bindings.concat("\u00ab", this.kind, "\u00bb"));
			kindLabel.setAlignment(Pos.CENTER);
			kindLabel.setMaxWidth(Double.MAX_VALUE);
			kindLabel.getStyleClass().add("seq-actor-kind");
			VBox content = new VBox(6, label, kindLabel);
			content.setAlignment(Pos.CENTER);
			content.setStyle("-fx-padding: 14 18 14 18;");
			this.card = new VBox(headerBand, content);
			this.card.setAlignment(Pos.TOP_CENTER);
			this.card.setMinWidth(180);
			this.card.getStyleClass().add("seq-actor");
			this.card.pseudoClassStateChanged(RECORDED, this.recorded);
			this.card.getStyleClass().add("actor-cell");
			this.selected.addListener((s, o, n) -> this.updateSelectedState(n.booleanValue()));
			this.updateSelectedState(this.selected.get());

			Region anchor = new Region();
			anchor.setMouseTransparent(true);
			anchor.setMinHeight(0);
			anchor.setPrefHeight(0);
			anchor.setMaxHeight(0);
			anchor.prefWidthProperty().bind(this.card.widthProperty());
			anchor.minWidthProperty().bind(this.card.widthProperty());
			this.installCardGestures(graph, anchor);
			return anchor;
		}

		/** Dragging a card moves its lifeline sideways; a click that did not drag selects it. */
		private void installCardGestures(final Graph graph, final Region anchor) {
			double[] pressed = new double[2];
			boolean[] dragged = new boolean[1];
			this.card.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
				pressed[0] = event.getSceneX();
				pressed[1] = anchor.getLayoutX();
				dragged[0] = false;
				event.consume();
			});
			this.card.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
				double delta = event.getSceneX() - pressed[0];
				if (Math.abs(delta) > 4) {
					dragged[0] = true;
				}
				if (dragged[0]) {
					anchor.setLayoutX(pressed[1] + delta / graph.getCanvas().getScale());
					if (graph instanceof SequenceDiagram diagram) {
						diagram.layoutHeader();
					}
				}
				event.consume();
			});
			this.card.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
				if (!dragged[0] && this.onClickConsumer != null) {
					this.onClickConsumer.accept(event);
				}
			});
		}

		/** @param recorded whether the actor is part of what is being recorded, before its graphic is created */
		public void setRecorded(final boolean recorded) {
			this.recorded = recorded;
		}

		/** @param kind what the actor stands for, e.g. "Model" or "Composite" */
		public void setKind(final String kind) {
			this.kind.set(kind);
		}

		public void onClick(final Consumer<MouseEvent> onClickConsumer) {
			this.onClickConsumer = onClickConsumer;
		}

		private void updateSelectedState(final boolean selected) {
			if (this.card == null) {
				return;
			}
			// Outlined by the stylesheet (.seq-actor:selected), so it follows the theme.
			this.card.pseudoClassStateChanged(SELECTED, selected);
		}

		@Override
		public void onAddedToGraph(final Graph graph, final Region graphic) {
			if (graph instanceof SequenceDiagram diagram && this.card != null) {
				diagram.header.getChildren().add(this.card);
				diagram.headerActors.add(this);
				this.card.widthProperty().addListener(o -> diagram.layoutHeader());
				this.card.heightProperty().addListener(o -> diagram.layoutHeader());
				graphic.layoutXProperty().addListener(o -> diagram.layoutHeader());
				diagram.layoutHeader();
			}
			if (this.lifeLineGraphic != null) {
				graph.getCanvas().getChildren().remove(this.lifeLineGraphic);
			}
			this.lifeLineGraphic = new Line();
			this.lifeLineGraphic.startXProperty()
					.bind(graphic.layoutXProperty().add(graphic.widthProperty().divide(2)));
			this.lifeLineGraphic.startYProperty()
					.bind(graphic.layoutYProperty().add(graphic.heightProperty()));
			this.lifeLineGraphic.endXProperty()
					.bind(graphic.layoutXProperty().add(graphic.widthProperty().divide(2)));
			this.lifeLineGraphic.endYProperty()
					.bind(graphic.layoutYProperty().add(graphic.heightProperty()).add(this.lifeLineLength));
			this.lifeLineGraphic.setStroke(Color.web("#94a3b8"));
			this.lifeLineGraphic.setStrokeWidth(1.2);
			this.lifeLineGraphic.getStrokeDashArray().setAll(6d, 6d);
			this.lifeLineGraphic.getStyleClass().add("seq-lifeline");
			this.lifeLineGraphic.setMouseTransparent(true);
			graph.getCanvas().getChildren().add(0, this.lifeLineGraphic);
		}

		@Override
		public void onRemovedFromGraph(final Graph graph, final Region graphic) {
			if (graph instanceof SequenceDiagram diagram && this.card != null) {
				diagram.header.getChildren().remove(this.card);
				diagram.headerActors.remove(this);
			}
			if (this.lifeLineGraphic != null) {
				graph.getCanvas().getChildren().remove(this.lifeLineGraphic);
				this.lifeLineGraphic = null;
			}
		}
		
		@Override
		public DoubleBinding getXAnchor(final Graph graph) {
			final Region graphic = graph.getGraphic(this);
			return graphic.layoutXProperty().add(graphic.widthProperty().divide(2));
		}

		@Override
		public DoubleBinding getYAnchor(final Graph graph) {
			final Region graphic = graph.getGraphic(this);
			return graphic.layoutYProperty().add(graphic.heightProperty());
		}
		
		@Override
		public String getName() {
			return this.name;
		}
	}
}
