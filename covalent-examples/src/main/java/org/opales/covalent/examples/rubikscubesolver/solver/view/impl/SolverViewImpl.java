/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.solver.view.impl;

import org.opales.covalent.examples.rubikscubesolver.cube.Move;
import org.opales.covalent.examples.rubikscubesolver.cube.SolveProgress;
import org.opales.covalent.examples.rubikscubesolver.cube.SolvingAlgorithm;
import org.opales.covalent.examples.rubikscubesolver.solver.interaction.SolverActionKey;
import org.opales.covalent.examples.rubikscubesolver.solver.model.SolverEvents;
import org.opales.covalent.examples.rubikscubesolver.solver.view.SolverView;
import org.opales.covalent.examples.rubikscubesolver.solver.view.SolverViewParameters;
import org.opales.covalent.examples.rubikscubesolver.solver.view.SolverViewServices;

import java.util.EnumMap;
import java.util.Map;

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
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The solver's View: an algorithm picker (two toggle buttons), a Solve/Cancel/Reset button row, a
 * progress label, and a move history panel (a {@link ListView} of short text entries) that can be
 * shown or hidden. It reflects every update pushed through {@link SolverViewServices} and consumes
 * {@link SolverEvents} directly (auto-subscribed simply by implementing that interface).
 * <p>
 * The "Reset" button is built here, not in {@code CubeViewImpl}, even though it resets the cube:
 * starting over is a call about the solving session, which {@code Solver} owns - see
 * {@link SolverActionKey#Reset}'s javadoc. {@code HmiBuilder} places this whole panel to the cube's
 * right; interleaving this one button next to the cube would need a second, button-level root node.
 * <p>
 * The algorithm toggle group uses the reentrancy-guard idiom of {@code DatasetViewImpl}'s shape
 * selector in the Neural Network Trainer demo: {@code applyingDisplayedAlgorithm} guards the toggle
 * group's selection listener against re-firing its own selection action when the displayed algorithm
 * is set programmatically.
 * <p>
 * Builds its JavaFX root synchronously at {@code @Activation}: {@code HmiBuilder} reads
 * {@link #getRoot()} right after this component activates and needs the node to already exist.
 */
@Implementation
public class SolverViewImpl implements SolverView, SolverViewServices, SolverViewParameters, SolverEvents {

	@UsedServices
	private ActionConsumerServices actionConsumerServices;

	private Node root;

	private ToggleGroup algorithmGroup;

	private final Map<SolvingAlgorithm, ToggleButton> algorithmButtons = new EnumMap<>(SolvingAlgorithm.class);

	private Label progressLabel;

	private ListView<String> moveHistoryList;

	private volatile boolean applyingDisplayedAlgorithm;

	private volatile boolean active;

	/**
	 * Builds the JavaFX root node synchronously (algorithm picker, Solve/Cancel/Reset/Move history
	 * buttons, progress label, hidden move history list) and marks the view active.
	 */
	@Activation
	public void buildRoot() {
		HBox algorithmRow = this.buildAlgorithmPicker();

		Button solveButton = new Button("Solve");
		solveButton.setOnAction(event -> this.actionConsumerServices.executeAction(
				SolverActionKey.StartSolve, this.selectedAlgorithm()));

		Button cancelButton = new Button("Cancel");
		cancelButton.setOnAction(event -> this.actionConsumerServices.executeAction(SolverActionKey.CancelSolve));

		Button resetButton = new Button("Reset");
		resetButton.setOnAction(event -> this.actionConsumerServices.executeAction(SolverActionKey.Reset));

		this.progressLabel = new Label("Idle");

		ToggleButton historyToggle = new ToggleButton("Move history");
		this.moveHistoryList = new ListView<>();
		this.moveHistoryList.setPrefHeight(120);
		this.moveHistoryList.setVisible(false);
		this.moveHistoryList.setManaged(false);
		historyToggle.setOnAction(event -> {
			boolean visible = historyToggle.isSelected();
			this.actionConsumerServices.executeAction(SolverActionKey.ToggleMoveHistory, visible);
		});

		HBox buttons = new HBox(6, solveButton, cancelButton, resetButton, historyToggle);

		VBox pane = new VBox(8, algorithmRow, buttons, this.progressLabel, this.moveHistoryList);
		pane.setPadding(new Insets(8));

		pane.getStyleClass().add("card");
		this.root = pane;
		this.active = true;
	}

	/** Marks the view inactive, so that pending JavaFX callbacks do not touch it afterwards. */
	@Passivation
	public void deactivate() {
		this.active = false;
	}

	private HBox buildAlgorithmPicker() {
		this.algorithmGroup = new ToggleGroup();
		HBox row = new HBox(4);
		for (SolvingAlgorithm algorithm : SolvingAlgorithm.values()) {
			ToggleButton button = new ToggleButton(algorithm.name());
			button.setToggleGroup(this.algorithmGroup);
			button.setUserData(algorithm);
			this.algorithmButtons.put(algorithm, button);
			row.getChildren().add(button);
		}
		this.algorithmGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
			if (!this.applyingDisplayedAlgorithm && newToggle != null) {
				this.actionConsumerServices.executeAction(
						SolverActionKey.SelectAlgorithm, (SolvingAlgorithm) newToggle.getUserData());
			}
		});
		// Start with HUMAN selected, matching SolverModelImpl's own default.
		this.applyingDisplayedAlgorithm = true;
		this.algorithmButtons.get(SolvingAlgorithm.HUMAN).setSelected(true);
		this.applyingDisplayedAlgorithm = false;
		return row;
	}

	private SolvingAlgorithm selectedAlgorithm() {
		var toggle = this.algorithmGroup.getSelectedToggle();
		return toggle == null ? SolvingAlgorithm.HUMAN : (SolvingAlgorithm) toggle.getUserData();
	}

	@Override
	public Node getRoot() {
		return this.root;
	}

	@Override
	public void displayProgress(final SolveProgress progress) {
		Platform.runLater(() -> {
			if (this.active) {
				this.progressLabel.setText(String.format(
						"%s (depth %d, %d nodes, %d ms)",
						progress.phase(), progress.searchDepth(), progress.nodesExplored(),
						progress.elapsedMillis()));
			}
		});
	}

	@Override
	public void displaySolution(final Move[] moves) {
		Platform.runLater(() -> {
			if (this.active) {
				this.progressLabel.setText("Solved in " + moves.length + " moves");
			}
		});
	}

	@Override
	public void setMoveHistoryVisible(final boolean visible) {
		Platform.runLater(() -> {
			if (this.active) {
				this.moveHistoryList.setVisible(visible);
				this.moveHistoryList.setManaged(visible);
				if (!visible) {
					this.moveHistoryList.getItems().clear();
				}
			}
		});
	}

	@Override
	public void appendMoveToHistory(final String entry) {
		Platform.runLater(() -> {
			if (this.active && this.moveHistoryList.isVisible()) {
				this.moveHistoryList.getItems().add(entry);
			}
		});
	}

	@Override
	public void progressUpdated(final SolveProgress progress) {
		this.displayProgress(progress);
	}

	@Override
	public void solveCompleted(final Move[] moves) {
		this.displaySolution(moves);
	}
}
