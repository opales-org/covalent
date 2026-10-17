/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.hazardalert.view.impl;

import org.opales.covalent.examples.solarsystem.hazardalert.interaction.HazardAlertActionKey;
import org.opales.covalent.examples.solarsystem.hazardalert.model.Alert;
import org.opales.covalent.examples.solarsystem.hazardalert.model.HazardAlertEvents;
import org.opales.covalent.examples.solarsystem.hazardalert.view.HazardAlertView;
import org.opales.covalent.examples.solarsystem.hazardalert.view.HazardAlertViewParameters;
import org.opales.covalent.examples.solarsystem.hazardalert.view.HazardAlertViewServices;
import org.opales.covalent.examples.solarsystem.orbitalsystem.model.CloseApproach;

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
 * The hazard alert's View: keeps the last known alert list and renders it,
 * with an "Acknowledge all" button. Consumes {@link HazardAlertEvents}
 * directly (managed autowiring).
 * <p>
 * Builds its JavaFX root synchronously at {@code @Activation}, not in a
 * {@code Platform.runLater}: {@code HmiBuilder} reads the root right after this
 * component activates.
 */
@Implementation
public class HazardAlertViewImpl implements HazardAlertView, HazardAlertViewServices,
		HazardAlertViewParameters, HazardAlertEvents {

	@UsedServices
	private ActionConsumerServices actionManagerServices;

	private final ObservableList<String> entries = FXCollections.observableArrayList();

	private Node root;

	private Label titleLabel;

	// Set to false at passivation, so that JavaFX callbacks still queued stop
	// using the services of the passivated component.
	private volatile boolean active;

	/** Builds the JavaFX root (title, alert list, "Acknowledge all" button) and marks the view active. */
	@Activation
	public void buildRoot() {
		this.titleLabel = new Label("Hazard alerts");
		this.titleLabel.getStyleClass().add("card-title-hazard");

		ListView<String> listView = new ListView<>(this.entries);
		listView.setPrefSize(280, 160);

		Button acknowledgeButton = new Button("Acknowledge all");
		acknowledgeButton.setOnAction(event -> this.actionManagerServices
				.executeAction(HazardAlertActionKey.AcknowledgeAll));

		BorderPane pane = new BorderPane();
		pane.setPadding(new Insets(10));
		pane.getStyleClass().add("card");
		pane.setTop(this.titleLabel);
		pane.setCenter(listView);
		pane.setBottom(acknowledgeButton);
		BorderPane.setMargin(this.titleLabel, new Insets(0, 0, 6, 0));
		BorderPane.setMargin(acknowledgeButton, new Insets(6, 0, 0, 0));

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
	public void setAcknowledging(final boolean acknowledging) {
		Platform.runLater(() -> {
			if (this.active && this.titleLabel != null) {
				this.titleLabel.setOpacity(acknowledging ? 0.4 : 1.0);
			}
		});
	}

	@Override
	public void onAlertRaised(final Alert alert) {
		Platform.runLater(() -> {
			if (this.active) {
				CloseApproach approach = alert.approach();
				this.entries.add(String.format("t=%.2f  %s <-> %s  (%.2f, max radius %.2f)",
						approach.simulationTime(), approach.bodyAName(), approach.bodyBName(),
						approach.distance(), Math.max(approach.bodyARadius(), approach.bodyBRadius())));
			}
		});
	}

	@Override
	public void onAllAcknowledged() {
		Platform.runLater(() -> {
			if (this.active) {
				this.entries.clear();
			}
		});
	}
}
