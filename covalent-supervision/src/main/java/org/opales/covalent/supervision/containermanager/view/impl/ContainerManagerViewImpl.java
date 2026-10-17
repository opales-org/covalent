/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.containermanager.view.impl;

import java.util.logging.Logger;

import org.opales.covalent.Covalent;
import org.opales.covalent.supervision.containermanager.interaction.ContainerManagerActionKey;
import org.opales.covalent.supervision.containermanager.view.ContainerManagerView;
import org.opales.covalent.supervision.containermanager.view.ContainerManagerViewParameters;
import org.opales.covalent.supervision.containermanager.view.ContainerManagerViewServices;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.hmi.action.ActionConsumerServices;
import org.opales.covalent.supervision.componentview.internal.Tooltips;
import org.opales.covalent.supervision.supervisionhmi.view.FxThread;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Circle;

/**
 * ContainerManager's View: builds and owns the managed-container selector
 * widget (a connection dot, a {@code ComboBox}, a "Connect" button and the
 * observed container's state when it is stopped or disposed) -
 * the only JavaFX {@link Node} this function holds. The dot shows whether
 * the selected container is currently connected. Exposes it as
 * {@link #getSelectorRoot()} for {@code SupervisionHmiView} to embed, and
 * dispatches "Connect" through {@code ActionConsumerServices}, never by
 * calling {@code ContainerManagerInteraction} directly.
 */
@Implementation
public class ContainerManagerViewImpl
		implements ContainerManagerView, ContainerManagerViewParameters, ContainerManagerViewServices {

	/** Creates the View; the selector widget itself is built at activation. */
	public ContainerManagerViewImpl() {
		// Nothing to initialize.
	}

	private static final Logger log = Logger.getLogger(ContainerManagerViewImpl.class.getName());

	/** Style class turning the connection dot from idle to connected - see {@code supervision.css}. */
	private static final String CONNECTED_STYLE_CLASS = "sv-connected";

	@UsedServices
	private ActionConsumerServices actionManagerServices;

	private ComboBox<String> containerSelector;
	private Circle connectionDot;
	private Label statusLabel;
	private HBox root;

	/**
	 * Builds the selector widget (connection dot, container combo box, "Connect" button and status label)
	 * and its root node, and installs their tooltips and handlers.
	 */
	@Activation
	public void buildRoot() {
		this.containerSelector = new ComboBox<>();
		this.containerSelector.setPromptText(ContainerManagerTexts.get("selector.prompt"));
		this.containerSelector.setPrefWidth(200);
		this.containerSelector.setOnShowing(event -> this.refreshContainerNames());
		Tooltips.install(this.containerSelector, ContainerManagerTexts.get("selector.tip"));

		Button connectButton = new Button(ContainerManagerTexts.get("connect"));
		connectButton.disableProperty().bind(this.containerSelector.valueProperty().isNull());
		connectButton.setOnAction(event -> this.onConnectClicked());
		Tooltips.install(connectButton, ContainerManagerTexts.get("connect.tip"));

		this.connectionDot = new Circle(4);
		this.connectionDot.getStyleClass().add("sv-connection-dot");

		this.statusLabel = new Label();
		this.statusLabel.getStyleClass().add("sv-container-status");

		this.root = new HBox(8, this.connectionDot, this.containerSelector, connectButton, this.statusLabel);
		this.root.setAlignment(Pos.CENTER_LEFT);
		this.root.getStyleClass().add("sv-container-selector");
	}

	@Override
	public Node getSelectorRoot() {
		return this.root;
	}

	private void refreshContainerNames() {
		String previouslySelected = this.containerSelector.getValue();
		this.containerSelector.getItems().setAll(Covalent.getManagedContainerNames());
		if (previouslySelected != null && this.containerSelector.getItems().contains(previouslySelected)) {
			this.containerSelector.setValue(previouslySelected);
		}
	}

	private void onConnectClicked() {
		String containerName = this.containerSelector.getValue();
		if (containerName != null) {
			this.actionManagerServices.executeAction(ContainerManagerActionKey.Connect, containerName);
		}
	}

	@Override
	public void connectionSucceeded(final String containerName) {
		Platform.runLater(() -> {
			this.containerSelector.setValue(containerName);
			if (!this.connectionDot.getStyleClass().contains(CONNECTED_STYLE_CLASS)) {
				this.connectionDot.getStyleClass().add(CONNECTED_STYLE_CLASS);
			}
			this.statusLabel.setText("");
		});
	}

	@Override
	public void connectionFailed(final String reason) {
		log.warning(() -> "Connection failed: " + reason);
		Platform.runLater(() -> {
			this.containerSelector.setValue(null);
			this.connectionDot.getStyleClass().remove(CONNECTED_STYLE_CLASS);
			this.statusLabel.setText("");
		});
	}

	@Override
	public void showConnectionError(final String containerName, final String reason) {
		FxThread.run(() -> {
			Alert alert = new Alert(AlertType.ERROR,
					ContainerManagerTexts.get("error.connect", containerName, reason));
			alert.setTitle(ContainerManagerTexts.get("error.title"));
			alert.setHeaderText(null);
			alert.initOwner(this.root.getScene() == null ? null : this.root.getScene().getWindow());
			alert.showAndWait();
		});
	}

	@Override
	public void containerStopped(final String containerName) {
		FxThread.run(() -> {
			this.showDisconnected(containerName, ContainerManagerTexts.get("status.stopped"));
		});
	}

	@Override
	public void containerDisposed(final String containerName) {
		FxThread.run(() -> {
			this.showDisconnected(containerName, ContainerManagerTexts.get("status.disposed"));
		});
	}

	/** Keeps the container selected, shown as no longer connected, with its state. */
	private void showDisconnected(final String containerName, final String status) {
		Platform.runLater(() -> {
			this.containerSelector.setValue(containerName);
			this.connectionDot.getStyleClass().remove(CONNECTED_STYLE_CLASS);
			this.statusLabel.setText(status);
		});
	}
}
