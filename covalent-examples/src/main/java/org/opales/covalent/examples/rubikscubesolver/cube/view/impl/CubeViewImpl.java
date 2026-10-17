/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.cube.view.impl;

import org.opales.covalent.examples.rubikscubesolver.cube.CubeColor;
import org.opales.covalent.examples.rubikscubesolver.cube.CubeEvents;
import org.opales.covalent.examples.rubikscubesolver.cube.CubeState;
import org.opales.covalent.examples.rubikscubesolver.cube.Face;
import org.opales.covalent.examples.rubikscubesolver.cube.RotationDirection;
import org.opales.covalent.examples.rubikscubesolver.cube.interaction.CubeActionKey;
import org.opales.covalent.examples.rubikscubesolver.cube.view.CubeView;
import org.opales.covalent.examples.rubikscubesolver.cube.view.CubeViewParameters;
import org.opales.covalent.examples.rubikscubesolver.cube.view.CubeViewServices;

import java.util.EnumMap;
import java.util.Map;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionConsumerServices;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * The cube's View: renders the current {@link CubeState} as an unfolded cube net - six 3x3 grids of
 * colored squares, one {@link Canvas} per face, laid out as a cross (UP above FRONT, LEFT and RIGHT on
 * its sides, DOWN below, BACK after RIGHT) - plus a "Scramble" button. The canvases are redrawn on
 * {@link #stateChanged(CubeState)}; the rendering is 2D and not interactive beyond clicks.
 * <p>
 * Clicking any facelet square fires {@link CubeActionKey#RotateFace} for that square's face, always as
 * a clockwise quarter turn. There is no "Reset" button here: starting over belongs to {@code Solver},
 * so that widget is built and owned by {@code SolverView}, even though it resets the cube - see
 * {@code SolverActionKey#Reset}'s javadoc for why ownership of a widget has nothing to do with where
 * {@code HmiBuilder} places it on screen.
 * <p>
 * Consumes {@link CubeEvents} directly: implementing that interface is enough for Covalent to
 * auto-subscribe this View, and {@code CubeModelImpl}'s subscription delegation (see its javadoc)
 * catches this View up with the state that already exists when the subscription is registered, exactly
 * as {@code DatasetViewImpl} is for {@code DatasetEvents} in the Neural Network Trainer demo.
 * <p>
 * Builds its JavaFX root synchronously at {@code @Activation}, not wrapped in
 * {@code Platform.runLater}: {@code HmiBuilder} reads {@link #getRoot()} right after this component
 * activates and needs the node to already exist.
 */
@Implementation
public class CubeViewImpl implements CubeView, CubeViewServices, CubeViewParameters, CubeEvents {

	private static final double FACELET_SIZE = 18;

	private static final double FACELET_GAP = 1;

	private static final double FACE_SIZE = FACELET_SIZE * 3 + FACELET_GAP * 2;

	@UsedServices
	private ActionConsumerServices actionConsumerServices;

	private final Map<Face, Canvas> faceCanvases = new EnumMap<>(Face.class);

	private volatile CubeState state = new CubeState();

	private Node root;

	// Guards against a Platform.runLater callback (queued from an event
	// still in flight) executing after this component has been
	// passivated - the same idiom DatasetViewImpl uses its own "active"
	// flag for.
	private volatile boolean active;

	/**
	 * Builds the JavaFX root node synchronously (one canvas per face and a Scramble button), then draws
	 * the current state.
	 */
	@Activation
	public void buildRoot() {
		GridPane net = new GridPane();
		net.setHgap(6);
		net.setVgap(6);

		net.add(this.buildFaceCanvas(Face.UP), 1, 0);
		net.add(this.buildFaceCanvas(Face.LEFT), 0, 1);
		net.add(this.buildFaceCanvas(Face.FRONT), 1, 1);
		net.add(this.buildFaceCanvas(Face.RIGHT), 2, 1);
		net.add(this.buildFaceCanvas(Face.BACK), 3, 1);
		net.add(this.buildFaceCanvas(Face.DOWN), 1, 2);

		Button scrambleButton = new Button("Scramble");
		scrambleButton.setOnAction(event -> this.actionConsumerServices
				.executeAction(CubeActionKey.Scramble, System.nanoTime()));

		HBox buttons = new HBox(8, scrambleButton);
		buttons.setAlignment(Pos.CENTER);

		VBox pane = new VBox(8, net, buttons);
		pane.setPadding(new Insets(8));
		pane.setAlignment(Pos.CENTER);

		pane.getStyleClass().add("card");
		this.root = pane;
		this.active = true;
		this.redrawAll();
	}

	/** Marks the view inactive, so that pending JavaFX callbacks do not touch it afterwards. */
	@Passivation
	public void deactivate() {
		this.active = false;
	}

	/**
	 * Builds one face's canvas, wired so a click anywhere on it fires
	 * {@link CubeActionKey#RotateFace} for that face as a clockwise quarter
	 * turn.
	 *
	 * @param face the face this canvas renders
	 * @return the built canvas, already registered in {@link #faceCanvases}
	 */
	private Canvas buildFaceCanvas(final Face face) {
		Canvas canvas = new Canvas(FACE_SIZE, FACE_SIZE);
		canvas.setOnMouseClicked(event -> this.actionConsumerServices
				.executeAction(CubeActionKey.RotateFace, face, RotationDirection.CLOCKWISE));
		this.faceCanvases.put(face, canvas);
		return canvas;
	}

	@Override
	public Node getRoot() {
		return this.root;
	}

	@Override
	public void setAnimating(final boolean animating) {
		Platform.runLater(() -> {
			if (this.active) {
				for (Canvas canvas : this.faceCanvases.values()) {
					canvas.setOpacity(animating ? 0.6 : 1.0);
				}
			}
		});
	}

	@Override
	public void stateChanged(final CubeState snapshot) {
		this.state = snapshot;
		Platform.runLater(this::redrawAll);
	}

	private void redrawAll() {
		if (!this.active) {
			return;
		}
		for (Face face : Face.values()) {
			this.redrawFace(face);
		}
	}

	private void redrawFace(final Face face) {
		Canvas canvas = this.faceCanvases.get(face);
		GraphicsContext gc = canvas.getGraphicsContext2D();
		gc.setFill(Color.GRAY);
		gc.fillRect(0, 0, FACE_SIZE, FACE_SIZE);
		CubeColor[] colors = this.state.getFace(face);
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				double x = col * (FACELET_SIZE + FACELET_GAP);
				double y = row * (FACELET_SIZE + FACELET_GAP);
				gc.setFill(toColor(colors[row * 3 + col]));
				gc.fillRect(x, y, FACELET_SIZE, FACELET_SIZE);
			}
		}
	}

	/**
	 * Maps a facelet's logical color to the JavaFX color it is drawn with.
	 *
	 * @param color a facelet's logical color
	 * @return the JavaFX color it is drawn with
	 */
	private static Color toColor(final CubeColor color) {
		return switch (color) {
			case WHITE -> Color.WHITE;
			case YELLOW -> Color.web("#f5d90a");
			case GREEN -> Color.web("#2ecc71");
			case BLUE -> Color.web("#2e86de");
			case RED -> Color.web("#e74c3c");
			case ORANGE -> Color.web("#e67e22");
		};
	}
}
