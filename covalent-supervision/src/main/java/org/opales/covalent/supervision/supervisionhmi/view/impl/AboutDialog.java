/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.supervisionhmi.view.impl;

import java.util.LinkedHashMap;
import java.util.Map;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * The supervisor's About box: what it is, its version and the platform it runs on, its license
 * and the third-party material it embeds, with a button that copies the system information for a
 * bug report.
 */
final class AboutDialog {

	private AboutDialog() {
	}

	/**
	 * Shows the About box, modal to the supervisor's window.
	 *
	 * @param owner the supervisor's window
	 * @param logo  the brand mark to show
	 */
	static void show(final Window owner, final Node logo) {
		Map<String, String> facts = systemInformation();

		logo.setScaleX(2.2);
		logo.setScaleY(2.2);
		HBox logoBox = new HBox(logo);
		logoBox.setAlignment(Pos.CENTER);
		logoBox.setMinHeight(76);

		Label name = new Label(SupervisionHmiTexts.get("title"));
		name.getStyleClass().add("sv-about-name");
		Label tagline = new Label(SupervisionHmiTexts.get("about.tagline"));
		tagline.getStyleClass().add("sv-dialog-muted");

		GridPane grid = new GridPane();
		grid.setHgap(18);
		grid.setVgap(6);
		int row = 0;
		for (Map.Entry<String, String> fact : facts.entrySet()) {
			Label key = new Label(SupervisionHmiTexts.get("about.fact." + fact.getKey()));
			key.getStyleClass().add("sv-fact-key");
			Label value = new Label(fact.getValue());
			value.getStyleClass().add("sv-fact-value");
			grid.addRow(row++, key, value);
		}
		grid.setAlignment(Pos.CENTER);

		Label license = new Label(SupervisionHmiTexts.get("about.license"));
		license.getStyleClass().add("sv-dialog-muted");
		Label notices = new Label(SupervisionHmiTexts.get("about.fonts"));
		notices.getStyleClass().add("sv-dialog-muted");
		for (Label label : new Label[] { license, notices }) {
			label.setWrapText(true);
			label.setStyle("-fx-text-alignment: center;");
		}

		VBox body = new VBox(10, logoBox, name, tagline, grid, license, notices);
		body.setAlignment(Pos.CENTER);
		body.setPadding(new Insets(22, 32, 22, 32));
		VBox.setMargin(grid, new Insets(8, 0, 8, 0));

		Stage stage = new Stage();
		stage.setTitle(SupervisionHmiTexts.get("help.about"));
		stage.setResizable(false);
		if (owner != null) {
			stage.initOwner(owner);
			if (owner instanceof Stage ownerStage) {
				stage.getIcons().setAll(ownerStage.getIcons());
			}
		}
		stage.initModality(Modality.WINDOW_MODAL);

		Button copy = new Button(SupervisionHmiTexts.get("about.copy"));
		copy.setOnAction(event -> {
			ClipboardContent content = new ClipboardContent();
			content.putString(asText(facts));
			Clipboard.getSystemClipboard().setContent(content);
			copy.setText(SupervisionHmiTexts.get("about.copied"));
		});
		Button close = new Button(SupervisionHmiTexts.get("about.close"));
		close.getStyleClass().add("sv-primary-button");
		close.setDefaultButton(true);
		close.setOnAction(event -> stage.close());
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox footer = new HBox(8, copy, spacer, close);
		footer.setAlignment(Pos.CENTER_LEFT);
		footer.getStyleClass().add("sv-dialog-foot");

		BorderPane root = new BorderPane(body, null, null, footer, null);
		root.getStyleClass().add("sv-dialog");
		Scene scene = new Scene(root);
		if (owner != null && owner.getScene() != null) {
			scene.getStylesheets().setAll(owner.getScene().getStylesheets());
		}
		scene.setOnKeyPressed(event -> {
			if (event.getCode() == KeyCode.ESCAPE) {
				stage.close();
			}
		});
		stage.setScene(scene);
		stage.show();
	}

	/** The version of the supervisor and the platform it runs on, in display order. */
	private static Map<String, String> systemInformation() {
		Map<String, String> facts = new LinkedHashMap<>();
		String version = AboutDialog.class.getPackage().getImplementationVersion();
		facts.put("Version", version != null ? version : SupervisionHmiTexts.get("about.developmentBuild"));
		facts.put("Java", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
		facts.put("JavaFX", String.valueOf(System.getProperty("javafx.runtime.version",
				System.getProperty("javafx.version", "unknown"))));
		facts.put("System", System.getProperty("os.name") + " " + System.getProperty("os.version") + " ("
				+ System.getProperty("os.arch") + ")");
		return facts;
	}

	private static String asText(final Map<String, String> facts) {
		StringBuilder text = new StringBuilder("Covalent Supervisor\n");
		facts.forEach((key, value) -> text.append(key).append(": ").append(value).append('\n'));
		return text.toString();
	}
}
