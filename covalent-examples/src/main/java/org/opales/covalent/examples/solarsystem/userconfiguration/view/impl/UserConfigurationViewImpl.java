/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.userconfiguration.view.impl;

import org.opales.covalent.examples.solarsystem.userconfiguration.interaction.UserConfigurationActionKey;
import org.opales.covalent.examples.solarsystem.userconfiguration.interaction.UserConfigurationInteraction;
import org.opales.covalent.examples.solarsystem.userconfiguration.view.UserConfigurationView;
import org.opales.covalent.examples.solarsystem.userconfiguration.view.UserConfigurationViewParameters;
import org.opales.covalent.examples.solarsystem.userconfiguration.view.UserConfigurationViewServices;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionConsumerServices;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * The configuration composite's View: keeps the last known value of every
 * user-editable parameter, as pushed by
 * {@code UserConfigurationInteraction}, and renders one slider per
 * parameter. Has no Model/Events to consume, unlike the MVIC functions:
 * this composite owns no state of its own, it only fronts other functions'
 * Parameters.
 * <p>
 * Builds its JavaFX root synchronously at {@code @Activation}, not in a
 * {@code Platform.runLater}: {@code HmiBuilder} reads the root right after this
 * component activates.
 */
@Implementation
public class UserConfigurationViewImpl
		implements UserConfigurationView, UserConfigurationViewServices, UserConfigurationViewParameters {

	@UsedServices
	private ActionConsumerServices actionManagerServices;

	private volatile double gravitationalConstant;

	private volatile double timeScale;

	private volatile double distanceThreshold;

	private volatile double sizeThreshold;

	private Node root;

	private Slider gravitationalConstantSlider;

	private Slider timeScaleSlider;

	private Slider distanceThresholdSlider;

	private Slider sizeThresholdSlider;

	// Set to false at passivation, so that JavaFX callbacks still queued stop
	// using the services of the passivated component.
	private volatile boolean active;

	/** Builds the JavaFX root (one labeled slider per parameter) and marks the view active. */
	@Activation
	public void buildRoot() {
		GridPane grid = new GridPane();
		grid.setHgap(8);
		grid.setVgap(6);
		ColumnConstraints labelColumn = new ColumnConstraints(190);
		ColumnConstraints controlColumn = new ColumnConstraints(160);
		grid.getColumnConstraints().addAll(labelColumn, controlColumn);

		this.gravitationalConstantSlider = new Slider(0.1, 3.0, this.gravitationalConstant);
		this.timeScaleSlider = new Slider(0.1, 5.0, this.timeScale);
		this.distanceThresholdSlider = new Slider(0.2, 3.0, this.distanceThreshold);
		this.sizeThresholdSlider = new Slider(0.05, 0.5, this.sizeThreshold);

		int row = 0;
		row = this.addRow(grid, row, "Gravitational constant", this.gravitationalConstantSlider);
		row = this.addRow(grid, row, "Time scale", this.timeScaleSlider);
		row = this.addRow(grid, row, "Hazard distance threshold", this.distanceThresholdSlider);
		row = this.addRow(grid, row, "Hazard size threshold", this.sizeThresholdSlider);

		this.gravitationalConstantSlider.valueProperty().addListener((obs, oldValue, newValue) -> this.actionManagerServices
				.executeAction(UserConfigurationActionKey.SetGravitationalConstant, newValue.doubleValue()));
		this.timeScaleSlider.valueProperty().addListener((obs, oldValue, newValue) -> this.actionManagerServices
				.executeAction(UserConfigurationActionKey.SetTimeScale, newValue.doubleValue()));
		this.distanceThresholdSlider.valueProperty().addListener((obs, oldValue, newValue) -> this.actionManagerServices
				.executeAction(UserConfigurationActionKey.SetDistanceThreshold, newValue.doubleValue()));
		this.sizeThresholdSlider.valueProperty().addListener((obs, oldValue, newValue) -> this.actionManagerServices
				.executeAction(UserConfigurationActionKey.SetSizeThreshold, newValue.doubleValue()));

		Label sectionTitle = new Label("Configuration");
		sectionTitle.getStyleClass().add("card-title");

		VBox card = new VBox(6, sectionTitle, grid);
		card.getStyleClass().add("card");
		card.setPadding(new Insets(10));

		this.root = card;
		this.active = true;
	}

	/** Marks the view inactive so that late events are ignored by the UI. */
	@Passivation
	public void deactivate() {
		this.active = false;
	}

	private int addRow(final GridPane grid, final int row, final String labelText, final Node control) {
		grid.add(new Label(labelText), 0, row);
		grid.add(control, 1, row);
		return row + 1;
	}

	@Override
	public Node getRoot() {
		return this.root;
	}

	@Override
	public void displayGravitationalConstant(final double gravitationalConstant) {
		this.gravitationalConstant = gravitationalConstant;
		Platform.runLater(() -> {
			if (this.active && this.gravitationalConstantSlider != null) {
				this.gravitationalConstantSlider.setValue(gravitationalConstant);
			}
		});
	}

	@Override
	public void displayTimeScale(final double timeScale) {
		this.timeScale = timeScale;
		Platform.runLater(() -> {
			if (this.active && this.timeScaleSlider != null) {
				this.timeScaleSlider.setValue(timeScale);
			}
		});
	}

	@Override
	public void displayDistanceThreshold(final double distanceThreshold) {
		this.distanceThreshold = distanceThreshold;
		Platform.runLater(() -> {
			if (this.active && this.distanceThresholdSlider != null) {
				this.distanceThresholdSlider.setValue(distanceThreshold);
			}
		});
	}

	@Override
	public void displaySizeThreshold(final double sizeThreshold) {
		this.sizeThreshold = sizeThreshold;
		Platform.runLater(() -> {
			if (this.active && this.sizeThresholdSlider != null) {
				this.sizeThresholdSlider.setValue(sizeThreshold);
			}
		});
	}
}
