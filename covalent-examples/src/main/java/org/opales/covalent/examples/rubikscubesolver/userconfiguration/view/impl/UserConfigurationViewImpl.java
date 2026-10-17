/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.userconfiguration.view.impl;

import org.opales.covalent.examples.rubikscubesolver.userconfiguration.interaction.UserConfigurationActionKey;
import org.opales.covalent.examples.rubikscubesolver.userconfiguration.view.UserConfigurationView;
import org.opales.covalent.examples.rubikscubesolver.userconfiguration.view.UserConfigurationViewParameters;
import org.opales.covalent.examples.rubikscubesolver.userconfiguration.view.UserConfigurationViewServices;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionConsumerServices;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.GridPane;

/**
 * The configuration composite's View: a spinner for the Kociemba phase 2 maximum depth,
 * a spinner for the solution playback speed, and a read-only label reporting whether the cube was solved when the panel
 * activated.
 * <p>
 * All spinners use the reentrancy-guard idiom of the spinners of {@code UserConfigurationViewImpl} in
 * the Neural Network Trainer demo: {@code applyingDisplayedValue} guards every programmatic
 * {@code display*} call against re-firing that spinner's own value-change listener.
 * <p>
 * Builds its JavaFX root synchronously at {@code @Activation}: {@code HmiBuilder} reads
 * {@link #getRoot()} right after this component activates and needs the node to already exist.
 */
@Implementation
public class UserConfigurationViewImpl
		implements UserConfigurationView, UserConfigurationViewServices, UserConfigurationViewParameters {

	private static final int MIN_MAX_DEPTH = 1;

	private static final int MAX_MAX_DEPTH = 30;

	private static final int MIN_PLAYBACK_SPEED = 1;

	private static final int MAX_PLAYBACK_SPEED = 100;

	@UsedServices
	private ActionConsumerServices actionConsumerServices;

	private Node root;

	private Spinner<Integer> maxDepthSpinner;

	private Spinner<Integer> playbackSpeedSpinner;

	private Label solvedLabel;

	private volatile boolean active;

	private volatile boolean applyingDisplayedValue;

	/**
	 * Builds the JavaFX root node synchronously (two spinners and a solved label in a grid) and marks the
	 * view active.
	 */
	@Activation
	public void buildRoot() {
		this.maxDepthSpinner = new Spinner<>(
				new SpinnerValueFactory.IntegerSpinnerValueFactory(MIN_MAX_DEPTH, MAX_MAX_DEPTH, MIN_MAX_DEPTH));
		this.maxDepthSpinner.setEditable(true);
		this.maxDepthSpinner.valueProperty().addListener((observable, oldValue, newValue) -> {
			if (!this.applyingDisplayedValue) {
				this.actionConsumerServices.executeAction(UserConfigurationActionKey.SetMaxDepth, newValue);
			}
		});

		this.playbackSpeedSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(
				MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED, MIN_PLAYBACK_SPEED));
		this.playbackSpeedSpinner.setEditable(true);
		this.playbackSpeedSpinner.valueProperty().addListener((observable, oldValue, newValue) -> {
			if (!this.applyingDisplayedValue) {
				this.actionConsumerServices.executeAction(UserConfigurationActionKey.SetPlaybackSpeed, newValue);
			}
		});

		this.solvedLabel = new Label();

		GridPane grid = new GridPane();
		grid.setHgap(6);
		grid.setVgap(6);
		grid.setPadding(new Insets(8));
		grid.addRow(0, new Label("Kociemba max depth"), this.maxDepthSpinner);
		grid.addRow(1, new Label("Playback speed (moves/s)"), this.playbackSpeedSpinner);
		grid.addRow(2, new Label("Cube"), this.solvedLabel);

		grid.getStyleClass().add("card");
		this.root = grid;
		this.active = true;
	}

	/** Marks the view inactive, so that pending JavaFX callbacks do not touch it afterwards. */
	@Passivation
	public void deactivate() {
		this.active = false;
	}

	@Override
	public Node getRoot() {
		return this.root;
	}

	@Override
	public void displayMaxDepth(final int maxDepth) {
		Platform.runLater(() -> {
			if (!this.active) {
				return;
			}
			this.applyingDisplayedValue = true;
			try {
				this.maxDepthSpinner.getValueFactory().setValue(maxDepth);
			} finally {
				this.applyingDisplayedValue = false;
			}
		});
	}

	@Override
	public void displayPlaybackSpeed(final int movesPerSecond) {
		Platform.runLater(() -> {
			if (!this.active) {
				return;
			}
			this.applyingDisplayedValue = true;
			try {
				this.playbackSpeedSpinner.getValueFactory().setValue(movesPerSecond);
			} finally {
				this.applyingDisplayedValue = false;
			}
		});
	}

	@Override
	public void displayCubeSolved(final boolean solved) {
		Platform.runLater(() -> {
			if (this.active) {
				this.solvedLabel.setText(solved ? "solved" : "not solved");
			}
		});
	}
}
