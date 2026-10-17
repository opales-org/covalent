/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalwatch.view.impl;

import org.opales.covalent.examples.solarsystem.orbitalsystem.model.CloseApproach;
import org.opales.covalent.examples.solarsystem.orbitalwatch.interaction.OrbitalWatchActionKey;
import org.opales.covalent.examples.solarsystem.orbitalwatch.model.OrbitalWatchEvents;
import org.opales.covalent.examples.solarsystem.orbitalwatch.view.OrbitalWatchView;
import org.opales.covalent.examples.solarsystem.orbitalwatch.view.OrbitalWatchViewParameters;
import org.opales.covalent.examples.solarsystem.orbitalwatch.view.OrbitalWatchViewServices;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionConsumerServices;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;

/**
 * The watch's View: keeps the last known recorded-approaches log and renders
 * it as a plain list, with a "Clear" button. Consumes
 * {@link OrbitalWatchEvents} directly (managed autowiring).
 * <p>
 * Builds its JavaFX root synchronously at {@code @Activation}, not in a
 * {@code Platform.runLater}: {@code HmiBuilder} reads the root right after this
 * component activates.
 */
@Implementation
public class OrbitalWatchViewImpl implements OrbitalWatchView, OrbitalWatchViewServices,
		OrbitalWatchViewParameters, OrbitalWatchEvents {

	@UsedServices
	private ActionConsumerServices actionManagerServices;

	private final ObservableList<String> entries = FXCollections.observableArrayList();

	private Node root;

	private Label titleLabel;

	// Set to false at passivation, so that JavaFX callbacks still queued stop
	// using the services of the passivated component.
	private volatile boolean active;

	/** Builds the JavaFX root (title, approach list, "Clear" button) and marks the view active. */
	@Activation
	public void buildRoot() {
		this.titleLabel = new Label("Recorded close approaches");
		this.titleLabel.getStyleClass().add("card-title");

		ListView<String> listView = new ListView<>(this.entries);
		listView.setPrefSize(280, 160);

		Button clearButton = new Button("Clear");
		clearButton.setOnAction(event -> this.actionManagerServices.executeAction(OrbitalWatchActionKey.Clear));

		BorderPane pane = new BorderPane();
		pane.setPadding(new Insets(10));
		pane.getStyleClass().add("card");
		pane.setTop(this.titleLabel);
		pane.setCenter(listView);
		pane.setBottom(clearButton);
		BorderPane.setMargin(this.titleLabel, new Insets(0, 0, 6, 0));
		BorderPane.setMargin(clearButton, new Insets(6, 0, 0, 0));

		this.root = pane;
		this.active = true;
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
	public void setClearing(final boolean clearing) {
		Platform.runLater(() -> {
			if (this.active && this.titleLabel != null) {
				this.titleLabel.setOpacity(clearing ? 0.4 : 1.0);
			}
		});
	}

	@Override
	public void onApproachRecorded(final CloseApproach approach) {
		Platform.runLater(() -> {
			if (this.active) {
				this.entries.add(String.format("t=%.2f  %s <-> %s  (%.2f)",
						approach.simulationTime(), approach.bodyAName(), approach.bodyBName(), approach.distance()));
			}
		});
	}

	@Override
	public void onLogCleared() {
		Platform.runLater(() -> {
			if (this.active) {
				this.entries.clear();
			}
		});
	}
}
