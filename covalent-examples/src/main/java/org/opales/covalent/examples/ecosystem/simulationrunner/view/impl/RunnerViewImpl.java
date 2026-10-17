/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.simulationrunner.view.impl;

import org.opales.covalent.examples.ecosystem.simulationrunner.interaction.RunnerActionKey;
import org.opales.covalent.examples.ecosystem.simulationrunner.model.RunnerEvents;
import org.opales.covalent.examples.ecosystem.simulationrunner.view.RunnerView;
import org.opales.covalent.examples.ecosystem.simulationrunner.view.RunnerViewParameters;
import org.opales.covalent.examples.ecosystem.simulationrunner.view.RunnerViewServices;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionConsumerServices;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The simulation run's View: keeps the last known pacing state and renders
 * two separate graphical views - run controls, and an elapsed-time readout -
 * grouped in this one View component per doctrine (not two MVICs). Consumes
 * {@link RunnerEvents} directly (managed autowiring).
 * <p>
 * Builds both root nodes synchronously at {@code @Activation}, not in a
 * {@code Platform.runLater}: {@code HmiBuilder} reads the root right after this
 * component activates.
 */
@Implementation
public class RunnerViewImpl implements RunnerView, RunnerViewServices, RunnerViewParameters, RunnerEvents {

	@UsedServices
	private ActionConsumerServices actionManagerServices;

	private volatile double elapsedSimulationTime;

	private volatile boolean running;

	private Node controlsRoot;

	private Node readoutRoot;

	private Button startPauseButton;

	private Label readoutLabel;

	// Set to false at passivation, so that JavaFX callbacks still queued stop
	// using the services of the passivated component.
	private volatile boolean active;

	/** Builds the run controls card and the elapsed-time readout, then marks the view active. */
	@Activation
	public void buildRoots() {
		this.startPauseButton = new Button("Start");
		this.startPauseButton.getStyleClass().add("accent-button");
		this.startPauseButton.setOnAction(event -> this.actionManagerServices.executeAction(
				this.running ? RunnerActionKey.Pause : RunnerActionKey.Start));

		Button stepButton = new Button("Step");
		stepButton.setOnAction(event -> this.actionManagerServices.executeAction(RunnerActionKey.Step));

		Button resetButton = new Button("Reset");
		resetButton.setOnAction(event -> this.actionManagerServices.executeAction(RunnerActionKey.Reset));

		Slider timeScaleSlider = new Slider(0.1, 5.0, 1.0);
		timeScaleSlider.valueProperty().addListener((obs, oldValue, newValue) -> this.actionManagerServices
				.executeAction(RunnerActionKey.SetTimeScale, newValue.doubleValue()));

		HBox buttons = new HBox(8, this.startPauseButton, stepButton, resetButton,
				new Label("Time scale"), timeScaleSlider);

		Label sectionTitle = new Label("Simulation Runner");
		sectionTitle.getStyleClass().add("card-title");

		VBox controls = new VBox(6, sectionTitle, buttons);
		controls.getStyleClass().add("card");
		controls.setPadding(new Insets(10));
		this.controlsRoot = controls;

		this.readoutLabel = new Label("t = 0.00");
		this.readoutLabel.getStyleClass().add("readout-value");
		HBox readout = new HBox(this.readoutLabel);
		readout.getStyleClass().add("status-bar");
		readout.setPadding(new Insets(8));
		this.readoutRoot = readout;

		this.active = true;
	}

	/** Marks the view inactive, so that notifications still pending are ignored. */
	@Passivation
	public void deactivate() {
		this.active = false;
	}

	@Override
	public Node getControlsRoot() {
		return this.controlsRoot;
	}

	@Override
	public Node getReadoutRoot() {
		return this.readoutRoot;
	}

	@Override
	public void pulseStep() {
		Platform.runLater(() -> {
			if (this.active && this.readoutLabel != null) {
				this.readoutLabel.setStyle("-fx-font-weight: bold;");
				Platform.runLater(() -> {
					if (this.active && this.readoutLabel != null) {
						this.readoutLabel.setStyle(null);
					}
				});
			}
		});
	}

	@Override
	public void onTick(final double elapsedSimulationTime) {
		this.elapsedSimulationTime = elapsedSimulationTime;
		Platform.runLater(() -> {
			if (this.active && this.readoutLabel != null) {
				this.readoutLabel.setText(String.format("t = %.2f", elapsedSimulationTime));
			}
		});
	}

	@Override
	public void onRunningChanged(final boolean running) {
		this.running = running;
		Platform.runLater(() -> {
			if (this.active && this.startPauseButton != null) {
				this.startPauseButton.setText(running ? "Pause" : "Start");
			}
		});
	}
}
