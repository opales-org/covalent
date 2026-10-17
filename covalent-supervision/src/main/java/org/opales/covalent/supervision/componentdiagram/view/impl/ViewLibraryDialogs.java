/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.view.impl;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramSnapshot;
import org.opales.covalent.supervision.componentdiagram.view.SaveViewForm;
import org.opales.covalent.supervision.componentdiagram.view.ViewLibraryContent;
import org.opales.covalent.supervision.diagrampersistence.SavedView;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisorMessages;

/**
 * The views library's dialogs: saving a view, the library itself, and the
 * confirmations they need. Built on the supervisor's own stylesheets, owned by
 * the window of the tab they act for; every outcome is handed to a callback,
 * the View dispatching it as the tab's action.
 */
final class ViewLibraryDialogs {

	private static final String VIEW_FILE_PATTERN = "*.cow";

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM HH:mm", SupervisorMessages.locale());

	private ViewLibraryDialogs() {
	}

	// --- Save view ---

	/**
	 * Shows the save dialog; once confirmed, hands the name and description
	 * over.
	 *
	 * @param owner   the window owning the dialog
	 * @param form    what the dialog shows
	 * @param preview the diagram as it is now, {@code null} if none
	 * @param onSave  receives the confirmed name and description
	 */
	static void showSaveDialog(final Window owner, final SaveViewForm form, final Image preview,
			final BiConsumer<String, String> onSave) {
		Stage stage = newStage(owner, ComponentDiagramMessages.get("save.title"));

		TextField name = new TextField(form.name());
		name.setPromptText(ComponentDiagramMessages.get("save.name.prompt"));
		Label replaceWarning = new Label();
		replaceWarning.setWrapText(true);
		replaceWarning.getStyleClass().add("sv-dialog-warning");
		replaceWarning.setMaxWidth(Double.MAX_VALUE);
		TextArea description = new TextArea(form.description());
		description.setPromptText(ComponentDiagramMessages.get("save.description.prompt"));
		description.setPrefRowCount(3);
		description.setWrapText(true);

		GridPane facts = facts(
				ComponentDiagramMessages.get("fact.container"), form.containerName(),
				ComponentDiagramMessages.get("fact.level"), form.level(),
				ComponentDiagramMessages.get("fact.content"), content(form.componentCount(), form.hiddenCount(), form.zoom()));

		VBox fields = new VBox(6, fieldLabel(ComponentDiagramMessages.get("save.name")), name, replaceWarning, spacer(6),
				fieldLabel(ComponentDiagramMessages.get("save.description")), description, facts);
		HBox.setHgrow(fields, Priority.ALWAYS);
		VBox previewBox = new VBox(6, fieldLabel(ComponentDiagramMessages.get("save.preview")), thumbnail(preview, 200, 130),
				muted(ComponentDiagramMessages.get("save.preview.hint")));
		previewBox.setPrefWidth(200);
		HBox body = new HBox(18, fields, previewBox);
		body.setPadding(new Insets(18, 20, 18, 20));

		Button cancel = new Button(ComponentDiagramMessages.get("dialog.cancel"));
		cancel.setCancelButton(true);
		cancel.setOnAction(event -> stage.close());
		Button save = new Button(ComponentDiagramMessages.get("save.save"));
		save.getStyleClass().add("sv-primary-button");
		save.setDefaultButton(true);
		Runnable refresh = () -> {
			String typed = name.getText().trim();
			boolean replaces = form.existingNames().contains(typed);
			replaceWarning.setText(ComponentDiagramMessages.get("save.replaceWarning", typed));
			replaceWarning.setVisible(replaces);
			replaceWarning.setManaged(replaces);
			save.setText(ComponentDiagramMessages.get(replaces ? "save.replace" : "save.save"));
			save.setDisable(typed.isEmpty());
		};
		name.textProperty().addListener((observable, before, after) -> refresh.run());
		refresh.run();
		save.setOnAction(event -> {
			stage.close();
			onSave.accept(name.getText().trim(), description.getText().trim());
		});
		HBox footer = footer(muted(ComponentDiagramMessages.get("save.folder.hint")), cancel, save);

		BorderPane root = dialogRoot(header(ComponentDiagramMessages.get("save.title"), null), body, footer);
		root.setPrefWidth(640);
		show(stage, owner, root);
		name.requestFocus();
		name.selectAll();
	}

	// --- Views library ---

	/** What the views library's actions do. */
	interface LibraryActions {

		void open(SavedView view);

		void rename(SavedView view, String newName);

		void delete(SavedView view);

		void export(SavedView view, File file);

		void importFile(File file);
	}

	/**
	 * The views library, kept open while views are renamed, deleted or
	 * imported, then updated with the library as it is after.
	 */
	static final class LibraryDialog {

		private final LibraryActions actions;
		private final Stage stage;
		private final ToggleButton thisContainer = new ToggleButton(ComponentDiagramMessages.get("library.thisContainer"));
		private final ToggleButton allContainers = new ToggleButton(ComponentDiagramMessages.get("library.allContainers"));
		private final ComboBox<String> sort = new ComboBox<>();
		private final VBox list = new VBox(4);
		private final VBox detail = new VBox(12);
		private final Label count = new Label();
		private final Label folder = new Label();
		private ViewLibraryContent content;
		private SavedView selected;

		LibraryDialog(final Window owner, final ViewLibraryContent content, final LibraryActions actions) {
			this.actions = actions;
			this.stage = newStage(owner, ComponentDiagramMessages.get("library.title"));

			ToggleGroup scope = new ToggleGroup();
			this.thisContainer.setToggleGroup(scope);
			this.allContainers.setToggleGroup(scope);
			this.thisContainer.setSelected(true);
			scope.selectedToggleProperty().addListener((observable, before, after) -> {
				if (after == null) {
					before.setSelected(true);
				} else {
					this.refresh();
				}
			});
			HBox segmented = new HBox(2, this.thisContainer, this.allContainers);
			segmented.getStyleClass().add("sv-segmented");
			this.sort.getItems().setAll(ComponentDiagramMessages.get("library.sort.lastSaved"),
					ComponentDiagramMessages.get("library.sort.name"));
			this.sort.setValue(ComponentDiagramMessages.get("library.sort.lastSaved"));
			this.sort.valueProperty().addListener((observable, before, after) -> this.refresh());
			HBox tools = new HBox(10, segmented, grow(), new Label(ComponentDiagramMessages.get("library.sort")), this.sort);
			tools.setAlignment(Pos.CENTER_LEFT);
			tools.setPadding(new Insets(12, 16, 12, 16));
			tools.getStyleClass().add("sv-dialog-tools");

			this.list.setPadding(new Insets(6));
			ScrollPane listScroll = new ScrollPane(this.list);
			listScroll.setFitToWidth(true);
			listScroll.getStyleClass().add("sv-library-list");
			VBox left = new VBox(tools, listScroll);
			VBox.setVgrow(listScroll, Priority.ALWAYS);
			HBox.setHgrow(left, Priority.ALWAYS);

			this.detail.setPadding(new Insets(16, 18, 16, 18));
			this.detail.setPrefWidth(360);
			this.detail.setMinWidth(360);
			this.detail.getStyleClass().add("sv-library-detail");
			HBox body = new HBox(left, this.detail);
			body.setPrefHeight(480);

			Button importButton = new Button(ComponentDiagramMessages.get("library.import"));
			importButton.setOnAction(event -> this.chooseImport());
			this.folder.getStyleClass().add("sv-dialog-path");
			Button close = new Button(ComponentDiagramMessages.get("library.close"));
			close.setCancelButton(true);
			close.setOnAction(event -> this.stage.close());
			HBox footer = footer(importButton, this.folder, close);

			this.count.getStyleClass().add("sv-chip-label");
			HBox countChip = new HBox(this.count);
			countChip.getStyleClass().add("sv-chip");
			BorderPane root = dialogRoot(header(ComponentDiagramMessages.get("library.title"), countChip), body, footer);
			root.setPrefWidth(1040);
			this.update(content);
			show(this.stage, owner, root);
		}

		boolean isShowing() {
			return this.stage.isShowing();
		}

		void toFront() {
			this.stage.toFront();
		}

		/** The library's own window, owning the dialogs shown over it. */
		Window window() {
			return this.stage;
		}

		/** Shows the library as it is now, keeping the selected view if it still exists. */
		void update(final ViewLibraryContent newContent) {
			this.content = newContent;
			this.folder.setText(newContent.viewsFolder().getPath());
			this.count.setText(ComponentDiagramMessages.count("library.count", newContent.entries().size()));
			this.thisContainer.setDisable(newContent.containerName() == null);
			if (newContent.containerName() == null) {
				this.allContainers.setSelected(true);
			}
			this.refresh();
		}

		private void refresh() {
			List<ViewLibraryContent.Entry> shown = new ArrayList<>();
			for (ViewLibraryContent.Entry entry : this.content.entries()) {
				if (this.allContainers.isSelected() || this.isOfThisContainer(entry.view())) {
					shown.add(entry);
				}
			}
			if (ComponentDiagramMessages.get("library.sort.name").equals(this.sort.getValue())) {
				shown.sort(Comparator.comparing(entry -> entry.view().name().toLowerCase(Locale.ROOT)));
			}
			this.list.getChildren().clear();
			ViewLibraryContent.Entry selectedEntry = null;
			for (ViewLibraryContent.Entry entry : shown) {
				if (this.selected != null && entry.view().file().equals(this.selected.file())) {
					selectedEntry = entry;
				}
			}
			if (selectedEntry == null && !shown.isEmpty()) {
				selectedEntry = shown.get(0);
			}
			this.selected = selectedEntry == null ? null : selectedEntry.view();
			for (ViewLibraryContent.Entry entry : shown) {
				this.list.getChildren().add(this.row(entry, entry == selectedEntry));
			}
			if (shown.isEmpty()) {
				this.list.getChildren().add(this.emptyState());
			}
			this.showDetail(selectedEntry);
		}

		private boolean isOfThisContainer(final SavedView view) {
			return view.readable() && view.containerName().equals(this.content.containerName());
		}

		private Node row(final ViewLibraryContent.Entry entry, final boolean selected) {
			SavedView view = entry.view();
			Label name = new Label(view.name());
			name.getStyleClass().add("sv-view-name");
			Label description = new Label(view.readable() ? view.description() : view.problem());
			description.getStyleClass().add("sv-view-description");
			HBox meta = new HBox(6);
			meta.setAlignment(Pos.CENTER_LEFT);
			if (view.readable()) {
				meta.getChildren().addAll(chip(view.containerName(), null), crumb(level(view.snapshot())));
				if (!entry.missingComponents().isEmpty()) {
					meta.getChildren().add(chip(ComponentDiagramMessages.get("library.missing", entry.missingComponents().size()),
							"sv-chip-warn"));
				}
			} else {
				meta.getChildren().add(chip(ComponentDiagramMessages.get("library.unreadable"), "sv-chip-bad"));
			}
			VBox text = new VBox(2, name, description, meta);
			HBox.setHgrow(text, Priority.ALWAYS);
			Label date = new Label(view.readable() ? when(view) : "");
			date.getStyleClass().add("sv-view-date");
			HBox row = new HBox(12, thumbnail(image(view), 96, 58), text, date);
			row.setAlignment(Pos.CENTER_LEFT);
			row.getStyleClass().add("sv-view-row");
			if (selected) {
				row.getStyleClass().add("sv-selected");
			}
			if (view.readable() && !this.isOfThisContainer(view)) {
				row.getStyleClass().add("sv-other");
			}
			row.setOnMouseClicked(event -> {
				if (event.getButton() != MouseButton.PRIMARY) {
					return;
				}
				this.selected = view;
				if (event.getClickCount() == 2 && view.readable()) {
					this.open(entry);
				} else {
					this.refresh();
				}
			});
			return row;
		}

		private Node emptyState() {
			Label title = new Label(this.thisContainer.isSelected() && this.content.containerName() != null
					? ComponentDiagramMessages.get("library.empty.container", this.content.containerName())
					: ComponentDiagramMessages.get("library.empty"));
			title.getStyleClass().add("sv-empty-title");
			VBox empty = new VBox(8, title, muted(ComponentDiagramMessages.get("library.empty.hint")));
			empty.setAlignment(Pos.CENTER);
			empty.setPadding(new Insets(60, 20, 60, 20));
			return empty;
		}

		private void showDetail(final ViewLibraryContent.Entry entry) {
			this.detail.getChildren().clear();
			if (entry == null) {
				return;
			}
			SavedView view = entry.view();
			Label name = new Label(view.name());
			name.getStyleClass().add("sv-detail-name");
			name.setWrapText(true);
			Button delete = new Button(ComponentDiagramMessages.get("library.delete"));
			delete.getStyleClass().add("sv-danger-button");
			delete.setOnAction(event -> {
				if (confirm(this.stage, ComponentDiagramMessages.get("library.delete.title"),
						ComponentDiagramMessages.get("library.delete.confirm", view.name()),
						ComponentDiagramMessages.get("library.delete"), true)) {
					this.actions.delete(view);
				}
			});
			if (!view.readable()) {
				Label problem = new Label(view.problem());
				problem.setWrapText(true);
				problem.getStyleClass().add("sv-dialog-warning");
				HBox buttons = new HBox(8, delete);
				this.detail.getChildren().addAll(name, problem, grow(), buttons);
				return;
			}
			ComponentDiagramSnapshot snapshot = view.snapshot();
			Label description = new Label(view.description());
			description.setWrapText(true);
			description.getStyleClass().add("sv-detail-description");
			GridPane facts = facts(
					ComponentDiagramMessages.get("fact.container"), view.containerName(),
					ComponentDiagramMessages.get("fact.level"), level(snapshot),
					ComponentDiagramMessages.get("fact.content"),
					content(snapshot.positions().size(), snapshot.hiddenComponents().size(), snapshot.zoom()),
					ComponentDiagramMessages.get("fact.saved"), when(view));

			Button open = new Button(ComponentDiagramMessages.get("library.open"));
			open.getStyleClass().add("sv-primary-button");
			open.setDefaultButton(true);
			open.setOnAction(event -> this.open(entry));
			Button rename = new Button(ComponentDiagramMessages.get("library.rename"));
			rename.setOnAction(event -> this.rename(view));
			Button export = new Button(ComponentDiagramMessages.get("library.export"));
			export.setOnAction(event -> this.chooseExport(view));
			HBox buttons = new HBox(8, open, rename, export, delete);
			buttons.setAlignment(Pos.CENTER_LEFT);

			Region push = grow();
			VBox.setVgrow(push, Priority.ALWAYS);
			this.detail.getChildren().addAll(thumbnail(image(view), 324, 180), name, description, facts);
			if (!entry.missingComponents().isEmpty()) {
				Label missing = new Label(missingText(entry.missingComponents()));
				missing.setWrapText(true);
				missing.getStyleClass().add("sv-dialog-warning");
				this.detail.getChildren().add(missing);
			}
			this.detail.getChildren().addAll(push, buttons);
		}

		/** Opens a view in a new tab, once its missing components are acknowledged. */
		private void open(final ViewLibraryContent.Entry entry) {
			if (!entry.missingComponents().isEmpty()
					&& !confirm(this.stage, entry.view().name(), missingText(entry.missingComponents()),
							ComponentDiagramMessages.get("library.openAnyway"), false)) {
				return;
			}
			this.stage.close();
			this.actions.open(entry.view());
		}

		private void rename(final SavedView view) {
			TextInputDialog dialog = new TextInputDialog(view.name());
			dialog.setTitle(ComponentDiagramMessages.get("library.rename.title"));
			dialog.setHeaderText(null);
			dialog.setContentText(ComponentDiagramMessages.get("library.rename.name"));
			styleLike(dialog.getDialogPane(), this.stage);
			dialog.initOwner(this.stage);
			Optional<String> newName = dialog.showAndWait();
			if (newName.isPresent() && !newName.get().isBlank() && !newName.get().trim().equals(view.name())) {
				this.selected = null;
				this.actions.rename(view, newName.get().trim());
			}
		}

		private void chooseExport(final SavedView view) {
			FileChooser chooser = new FileChooser();
			chooser.setTitle(ComponentDiagramMessages.get("library.export.title"));
			chooser.getExtensionFilters().add(new ExtensionFilter(ComponentDiagramMessages.get("library.fileType"), VIEW_FILE_PATTERN));
			chooser.setInitialFileName(view.file().getName());
			File file = chooser.showSaveDialog(this.stage);
			if (file != null) {
				this.actions.export(view, file);
			}
		}

		private void chooseImport() {
			FileChooser chooser = new FileChooser();
			chooser.setTitle(ComponentDiagramMessages.get("library.import.title"));
			chooser.getExtensionFilters().add(new ExtensionFilter(ComponentDiagramMessages.get("library.fileType"), VIEW_FILE_PATTERN));
			File file = chooser.showOpenDialog(this.stage);
			if (file != null) {
				this.actions.importFile(file);
			}
		}
	}

	// --- Confirmations ---

	/**
	 * Asks the user to confirm.
	 *
	 * @param owner        the window owning the dialog
	 * @param title        the dialog's title
	 * @param message      what to confirm
	 * @param confirmLabel the confirming button's label
	 * @param danger       whether confirming destroys something
	 * @return {@code true} if confirmed
	 */
	static boolean confirm(final Window owner, final String title, final String message, final String confirmLabel,
			final boolean danger) {
		Alert alert = new Alert(AlertType.CONFIRMATION, message);
		alert.setTitle(title);
		alert.setHeaderText(null);
		ButtonType confirmType = new ButtonType(confirmLabel, ButtonBar.ButtonData.OK_DONE);
		ButtonType cancelType = new ButtonType(ComponentDiagramMessages.get("dialog.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
		alert.getButtonTypes().setAll(cancelType, confirmType);
		if (owner != null) {
			alert.initOwner(owner);
			styleLike(alert.getDialogPane(), owner);
		}
		alert.getDialogPane().lookupButton(confirmType).getStyleClass()
				.add(danger ? "sv-danger-button" : "sv-primary-button");
		Optional<ButtonType> answer = alert.showAndWait();
		return answer.isPresent() && answer.get() == confirmType;
	}

	// --- Building blocks ---

	private static Stage newStage(final Window owner, final String title) {
		Stage stage = new Stage();
		stage.setTitle(title);
		if (owner != null) {
			stage.initOwner(owner);
			if (owner instanceof Stage ownerStage) {
				stage.getIcons().setAll(ownerStage.getIcons());
			}
		}
		stage.initModality(Modality.WINDOW_MODAL);
		return stage;
	}

	private static void show(final Stage stage, final Window owner, final BorderPane root) {
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

	private static void styleLike(final DialogPane pane, final Window owner) {
		if (owner != null && owner.getScene() != null) {
			pane.getStylesheets().setAll(owner.getScene().getStylesheets());
		}
		pane.getStyleClass().add("sv-dialog-pane");
	}

	private static BorderPane dialogRoot(final Node header, final Node body, final Node footer) {
		BorderPane root = new BorderPane(body, header, null, footer, null);
		root.getStyleClass().add("sv-dialog");
		return root;
	}

	private static Node header(final String title, final Node badge) {
		Label label = new Label(title);
		label.getStyleClass().add("sv-dialog-title");
		HBox header = new HBox(10, label);
		if (badge != null) {
			header.getChildren().add(badge);
		}
		header.setAlignment(Pos.CENTER_LEFT);
		header.getStyleClass().add("sv-dialog-head");
		return header;
	}

	private static HBox footer(final Node... nodes) {
		HBox footer = new HBox(8);
		footer.setAlignment(Pos.CENTER_LEFT);
		footer.getStyleClass().add("sv-dialog-foot");
		boolean spaced = false;
		for (Node node : nodes) {
			footer.getChildren().add(node);
			if (!spaced && (node instanceof Label)) {
				footer.getChildren().add(grow());
				spaced = true;
			}
		}
		return footer;
	}

	private static Region grow() {
		Region region = new Region();
		HBox.setHgrow(region, Priority.ALWAYS);
		return region;
	}

	private static Region spacer(final double height) {
		Region region = new Region();
		region.setMinHeight(height);
		return region;
	}

	private static Label fieldLabel(final String text) {
		Label label = new Label(text);
		label.getStyleClass().add("sv-field-label");
		return label;
	}

	private static Label muted(final String text) {
		Label label = new Label(text);
		label.setWrapText(true);
		label.getStyleClass().add("sv-dialog-muted");
		return label;
	}

	private static HBox chip(final String text, final String styleClass) {
		HBox chip = new HBox(new Label(text));
		chip.getStyleClass().add("sv-chip");
		if (styleClass != null) {
			chip.getStyleClass().add(styleClass);
		}
		return chip;
	}

	private static Label crumb(final String text) {
		Label crumb = new Label(text);
		crumb.getStyleClass().add("sv-crumb");
		return crumb;
	}

	private static GridPane facts(final String... keysAndValues) {
		GridPane grid = new GridPane();
		grid.setHgap(12);
		grid.setVgap(6);
		grid.setPadding(new Insets(10, 0, 0, 0));
		for (int index = 0; index < keysAndValues.length; index += 2) {
			Label key = new Label(keysAndValues[index]);
			key.getStyleClass().add("sv-fact-key");
			Label value = new Label(keysAndValues[index + 1]);
			value.getStyleClass().add("sv-fact-value");
			grid.addRow(index / 2, key, value);
		}
		return grid;
	}

	private static StackPane thumbnail(final Image image, final double width, final double height) {
		StackPane frame = new StackPane();
		frame.getStyleClass().add("sv-thumb");
		frame.setMinSize(width, height);
		frame.setPrefSize(width, height);
		frame.setMaxSize(width, height);
		if (image != null) {
			ImageView view = new ImageView(image);
			view.setPreserveRatio(true);
			view.setFitWidth(width - 4);
			view.setFitHeight(height - 4);
			view.setSmooth(true);
			frame.getChildren().add(view);
		}
		return frame;
	}

	private static Image image(final SavedView view) {
		File thumbnail = view.thumbnail();
		return thumbnail != null && thumbnail.isFile() ? new Image(thumbnail.toURI().toString(), true) : null;
	}

	private static String level(final ComponentDiagramSnapshot snapshot) {
		StringBuilder level = new StringBuilder(ComponentDiagramMessages.get("breadcrumb.top"));
		for (String className : snapshot.focusPath()) {
			level.append(" › ").append(className.substring(className.lastIndexOf('.') + 1));
		}
		return level.toString();
	}

	private static String content(final int componentCount, final int hiddenCount, final double zoom) {
		return ComponentDiagramMessages.count("fact.content.value", componentCount, hiddenCount, Math.round(zoom * 100));
	}

	private static String missingText(final Set<String> missing) {
		List<String> names = new ArrayList<>();
		for (String className : missing) {
			names.add(className.substring(className.lastIndexOf('.') + 1));
		}
		return ComponentDiagramMessages.count("library.missingText", missing.size(), String.join(", ", names));
	}

	private static String when(final SavedView view) {
		ZonedDateTime saved = view.savedAt().atZone(ZoneId.systemDefault());
		LocalDate today = LocalDate.now();
		if (saved.toLocalDate().equals(today)) {
			return ComponentDiagramMessages.get("date.today", TIME.format(saved));
		}
		if (saved.toLocalDate().equals(today.minusDays(1))) {
			return ComponentDiagramMessages.get("date.yesterday", TIME.format(saved));
		}
		return DATE.format(saved);
	}
}
