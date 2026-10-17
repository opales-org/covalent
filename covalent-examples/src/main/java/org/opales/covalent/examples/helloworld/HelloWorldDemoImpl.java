/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.helloworld;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Managed class of the {@link HelloWorldDemo} component: opens a small JavaFX window with a label and
 * a button that counts its clicks when activated, and closes it when passivated. All window work runs
 * on the JavaFX thread.
 */
@Implementation
public class HelloWorldDemoImpl implements HelloWorldDemo {

	private Stage stage;

	private int clickCount;

	/** Opens the demo window on the JavaFX thread and resets the click counter. */
	@Activation
	public void activate() {
		Platform.runLater(() -> {
			this.clickCount = 0;

			Label label = new Label("Hello from Covalent!");
			Button button = new Button("Click me");
			button.setOnAction(event -> {
				this.clickCount++;
				label.setText("Clicked " + this.clickCount + " time(s)");
			});

			VBox root = new VBox(10, label, button);
			root.setPadding(new Insets(16));

			this.stage = new Stage();
			this.stage.setTitle("Hello World demo");
			this.stage.setScene(new Scene(root, 280, 140));
			this.stage.show();
		});
	}

	/** Closes the demo window, if it is open, on the JavaFX thread. */
	@Passivation
	public void passivate() {
		Platform.runLater(() -> {
			if (this.stage != null) {
				this.stage.close();
				this.stage = null;
			}
		});
	}
}
