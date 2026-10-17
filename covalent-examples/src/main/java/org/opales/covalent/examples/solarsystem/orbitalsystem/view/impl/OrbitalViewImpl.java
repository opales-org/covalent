/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalsystem.view.impl;

import org.opales.covalent.examples.solarsystem.hazardalert.HazardAlert;
import org.opales.covalent.examples.solarsystem.orbitalsystem.Body;
import org.opales.covalent.examples.solarsystem.orbitalsystem.controller.BodyDetails;
import org.opales.covalent.examples.solarsystem.orbitalsystem.controller.impl.OrbitalControllerImpl;
import org.opales.covalent.examples.solarsystem.orbitalsystem.interaction.OrbitalActionKey;
import org.opales.covalent.examples.solarsystem.orbitalsystem.model.CloseApproach;
import org.opales.covalent.examples.solarsystem.orbitalsystem.model.CloseApproachEvents;
import org.opales.covalent.examples.solarsystem.orbitalsystem.view.OrbitalView;
import org.opales.covalent.examples.solarsystem.orbitalsystem.view.OrbitalViewParameters;
import org.opales.covalent.examples.solarsystem.orbitalsystem.view.OrbitalViewServices;
import org.opales.covalent.examples.solarsystem.orbitalwatch.OrbitalWatch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionConsumerServices;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * The orbital system's View: keeps the latest known body snapshot and
 * renders it as a 2D orrery. Consumes {@link CloseApproachEvents} directly
 * (managed autowiring): {@link #onSystemAdvanced(Body[], double)} drives the
 * continuous animation, {@link #onCloseApproachDetected(CloseApproach)}
 * briefly flashes the involved bodies.
 * <p>
 * Builds its JavaFX root synchronously at {@code @Activation}, not in a
 * {@code Platform.runLater}: {@code HmiBuilder} reads the root right after this
 * component activates.
 */
@Implementation
public class OrbitalViewImpl implements OrbitalView, OrbitalViewServices, OrbitalViewParameters, CloseApproachEvents {

	private static final double CANVAS_SIZE = 420;

	// Half-span of the simulation coordinate system shown on the canvas -
	// comfortably beyond Mars' 11.5-unit orbit (see OrbitalControllerImpl).
	private static final double VIEW_HALF_SPAN = 14.0;

	private static final double MIN_VISUAL_RADIUS = 3.0;

	private static final double VISUAL_RADIUS_SCALE = 9.0;

	private static final int STAR_COUNT = 90;

	/**
	 * A fixed decorative background star, purely cosmetic (no relation to
	 * any {@link Body}) - generated once so the starfield does not
	 * flicker between redraws.
	 */
	private record Star(double x, double y, double diameter) {
	}

	private final List<Star> stars = buildStarfield();

	@UsedServices
	private ActionConsumerServices actionManagerServices;

	private volatile Body[] bodies = new Body[0];

	private volatile String selectedBodyName;

	private volatile BodyDetails selectedBodyDetails;

	private Node root;

	private Canvas canvas;

	private Label detailsLabel;

	// Set to false at passivation, so that JavaFX callbacks still queued stop
	// using the services of the passivated component.
	private volatile boolean active;

	/** Builds the JavaFX root (orrery canvas, "Add Comet" button, body details label) and marks the view active. */
	@Activation
	public void buildRoot() {
		this.canvas = new Canvas(CANVAS_SIZE, CANVAS_SIZE);
		this.canvas.setOnMouseClicked(event -> {
			String nearest = this.findNearestBody(event.getX(), event.getY());
			if (nearest != null) {
				this.actionManagerServices.executeAction(OrbitalActionKey.SelectBody, nearest);
			}
		});

		this.detailsLabel = new Label("Click a body to inspect it");

		Button addCometButton = new Button("Add Comet");
		addCometButton.getStyleClass().add("accent-button");
		addCometButton.setOnAction(event -> this.actionManagerServices.executeAction(OrbitalActionKey.AddComet));

		BorderPane pane = new BorderPane();
		pane.setTop(addCometButton);
		BorderPane.setMargin(addCometButton, new Insets(0, 0, 8, 0));
		pane.setCenter(this.canvas);
		pane.setBottom(this.detailsLabel);
		BorderPane.setMargin(this.detailsLabel, new Insets(8, 0, 0, 0));

		Label sectionTitle = new Label("Orbital System");
		sectionTitle.getStyleClass().add("card-title");

		VBox card = new VBox(6, sectionTitle, pane);
		card.getStyleClass().add("card");
		card.setPadding(new Insets(10));

		this.root = card;
		this.active = true;
		this.redraw();
	}

	/** Marks the view inactive so that late events are ignored by the UI. */
	@Passivation
	public void deactivate() {
		this.active = false;
	}

	@Override
	public Node getRoot() {
		return this.root;
	}

	@Override
	public void showBodyDetails(final BodyDetails details) {
		this.selectedBodyName = details.name();
		this.selectedBodyDetails = details;
		Platform.runLater(() -> {
			if (this.active && this.detailsLabel != null) {
				this.detailsLabel.setText(String.format(
						"%s - mass %.1f, radius %.2f, distance from Sun %.2f, speed %.2f",
						details.name(), details.mass(), details.radius(),
						details.distanceFromSun(), details.speed()));
			}
		});
	}

	@Override
	public void onSystemAdvanced(final Body[] bodies, final double simulationTime) {
		this.bodies = bodies;
		Platform.runLater(this::redraw);
	}

	@Override
	public void onCloseApproachDetected(final CloseApproach approach) {
		// The next onSystemAdvanced redraw already reflects the bodies'
		// current positions; nothing further needed for this demo's own
		// rendering (OrbitalWatch/HazardAlert are the ones that keep a
		// record of approaches).
	}

	private void redraw() {
		if (!this.active || this.canvas == null) {
			return;
		}
		GraphicsContext gc = this.canvas.getGraphicsContext2D();
		gc.setFill(Color.BLACK);
		gc.fillRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);

		gc.setFill(Color.WHITE);
		for (Star star : this.stars) {
			gc.fillOval(star.x(), star.y(), star.diameter(), star.diameter());
		}

		for (Body body : this.bodies) {
			double px = toPixels(body.x());
			double py = toPixels(body.y());
			double visualRadius = Math.max(MIN_VISUAL_RADIUS, body.radius() * VISUAL_RADIUS_SCALE);
			gc.setFill(bodyColor(body.name()));
			gc.fillOval(px - visualRadius, py - visualRadius, visualRadius * 2, visualRadius * 2);
			if (body.name().equals(this.selectedBodyName)) {
				gc.setStroke(Color.WHITE);
				gc.strokeOval(px - visualRadius - 3, py - visualRadius - 3, visualRadius * 2 + 6, visualRadius * 2 + 6);
			}
		}
	}

	/**
	 * Finds the body nearest to a canvas click, within a small pick radius.
	 *
	 * @param canvasX the click's X coordinate on the canvas
	 * @param canvasY the click's Y coordinate on the canvas
	 * @return the nearest body's name, or {@code null} if none is close enough
	 */
	private String findNearestBody(final double canvasX, final double canvasY) {
		String nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (Body body : this.bodies) {
			double px = toPixels(body.x());
			double py = toPixels(body.y());
			double visualRadius = Math.max(MIN_VISUAL_RADIUS, body.radius() * VISUAL_RADIUS_SCALE);
			double dx = canvasX - px;
			double dy = canvasY - py;
			double distance = Math.sqrt(dx * dx + dy * dy);
			double pickRadius = visualRadius + 6.0;
			if (distance <= pickRadius && distance < nearestDistance) {
				nearest = body.name();
				nearestDistance = distance;
			}
		}
		return nearest;
	}

	private static double toPixels(final double simulationCoordinate) {
		return CANVAS_SIZE / 2.0 + simulationCoordinate / VIEW_HALF_SPAN * (CANVAS_SIZE / 2.0);
	}

	/**
	 * Generates a fixed, purely decorative starfield behind the orrery -
	 * a one-time random scatter of small dots, not part of the physical
	 * simulation.
	 *
	 * @return the generated stars
	 */
	private static List<Star> buildStarfield() {
		List<Star> generated = new ArrayList<>();
		for (int i = 0; i < STAR_COUNT; i++) {
			double x = ThreadLocalRandom.current().nextDouble(0, CANVAS_SIZE);
			double y = ThreadLocalRandom.current().nextDouble(0, CANVAS_SIZE);
			double diameter = ThreadLocalRandom.current().nextDouble(0.6, 1.8);
			generated.add(new Star(x, y, diameter));
		}
		return generated;
	}

	private static Color bodyColor(final String bodyName) {
		if ("Sun".equals(bodyName)) {
			return Color.GOLD;
		}
		if (bodyName.startsWith("Comet-")) {
			return Color.LIGHTGRAY;
		}
		if ("Earth".equals(bodyName)) {
			return Color.ROYALBLUE;
		}
		return Color.SADDLEBROWN;
	}
}
