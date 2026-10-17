/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.exclusionpolicy.view.impl;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.hmi.action.ActionConsumerServices;
import org.opales.covalent.supervision.componentview.internal.Tooltips;
import org.opales.covalent.supervision.exclusionpolicy.BuiltInExclusions;
import org.opales.covalent.supervision.exclusionpolicy.interaction.ExclusionPolicyActionKey;
import org.opales.covalent.supervision.exclusionpolicy.model.ExclusionPolicyEvents;
import org.opales.covalent.supervision.exclusionpolicy.view.ExclusionPolicyView;
import org.opales.covalent.supervision.exclusionpolicy.view.ExclusionPolicyViewServices;
import org.opales.covalent.supervision.supervisionhmi.view.FxThread;
import org.opales.covalent.supervision.supervisionhmi.view.PanelHostingServices;
import org.opales.covalent.supervision.supervisionhmi.view.RailEntry;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisorTabDescriptorAdapter;
import org.opales.covalent.supervision.supervisionhmi.view.ToolbarFunction;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

/**
 * ExclusionPolicy's View: the excluded component types as a filterable table, a field to add
 * one by its qualified name (this function has no way to enumerate every managed type in the
 * observed container), and the "exclude internal
 * components" rule. The supervisor's own components are excluded by default and stay so. Renders
 * whatever {@link ExclusionPolicyEvents} delivers, never queries the Model back for it; reports
 * every edit through {@link ActionConsumerServices}.
 */
@Implementation
public class ExclusionPolicyViewImpl implements ExclusionPolicyView, ExclusionPolicyViewServices, ExclusionPolicyEvents {

	/** Creates the View; the panel is built when the component activates. */
	public ExclusionPolicyViewImpl() {
		// Nothing to initialize.
	}

	private static final String ICON_LOCK = "M5 11h14v10H5zM8 11V8a4 4 0 0 1 8 0v3";
	private static final String ICON_TRASH = "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3";
	private static final String ICON_FILTER = "M3 5h18l-7 8v6l-4-2v-4z";

	@UsedServices
	private ActionConsumerServices actionManagerServices;

	@UsedServices
	private PanelHostingServices useHostingServices;

	private final List<Class<?>> excludedComponents = new ArrayList<>();

	private ScrollPane root;
	private final Label count = new Label("0");
	private final TextField filter = new TextField();
	private final VBox rows = new VBox();
	private final TextField addTypeNameField = new TextField();
	private final Label addErrorLabel = new Label();
	private final ToggleButton excludeInternal = new ToggleButton();
	private boolean contentBuilt = false;
	private boolean updatingFromModel = false;

	// --- Building ---

	private void ensureContentBuilt() {
		if (this.contentBuilt) {
			return;
		}
		if (Platform.isFxApplicationThread()) {
			this.buildContent();
		} else {
			Platform.runLater(this::buildContent);
		}
	}

	private void buildContent() {
		if (this.contentBuilt) {
			return;
		}
		this.contentBuilt = true;

		Label title = new Label(ExclusionPolicyTexts.get("title"));
		title.getStyleClass().add("sv-excl-title");
		Label lead = new Label(ExclusionPolicyTexts.get("lead"));
		lead.getStyleClass().add("sv-excl-muted");

		Label listTitle = new Label(ExclusionPolicyTexts.get("list.title"));
		listTitle.getStyleClass().add("sv-excl-card-title");
		this.count.getStyleClass().add("sv-excl-count");
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		this.filter.setPromptText(ExclusionPolicyTexts.get("filter.prompt"));
		this.filter.getStyleClass().add("sv-excl-filter");
		this.filter.textProperty().addListener(observable -> this.renderRows());
		HBox head = new HBox(10, listTitle, this.count, spacer, this.filter);
		head.setAlignment(Pos.CENTER_LEFT);
		head.getStyleClass().add("sv-excl-card-head");

		this.addTypeNameField.setPromptText(ExclusionPolicyTexts.get("add.prompt"));
		this.addTypeNameField.getStyleClass().add("sv-excl-add-field");
		HBox.setHgrow(this.addTypeNameField, Priority.ALWAYS);
		this.addTypeNameField.setOnAction(event -> this.handleAddRequested());
		this.addTypeNameField.textProperty().addListener(observable -> this.addErrorLabel.setText(""));
		Button add = new Button(ExclusionPolicyTexts.get("add"));
		add.getStyleClass().add("sv-primary-button");
		add.setOnAction(event -> this.handleAddRequested());
		HBox addRow = new HBox(8, this.addTypeNameField, add);
		addRow.setAlignment(Pos.CENTER_LEFT);
		this.addErrorLabel.getStyleClass().add("sv-error-label");
		this.addErrorLabel.managedProperty().bind(this.addErrorLabel.textProperty().isNotEmpty());
		VBox addBox = new VBox(6, addRow, this.addErrorLabel);
		addBox.getStyleClass().add("sv-excl-add");

		VBox listCard = new VBox(head, this.rows, addBox);
		listCard.getStyleClass().add("sv-excl-card");

		Label ruleTitle = new Label(ExclusionPolicyTexts.get("rule.title"));
		ruleTitle.getStyleClass().add("sv-excl-rule-title");
		Label ruleText = new Label(ExclusionPolicyTexts.get("rule.text"));
		ruleText.getStyleClass().add("sv-excl-muted");
		VBox ruleTextBox = new VBox(2, ruleTitle, ruleText);
		HBox.setHgrow(ruleTextBox, Priority.ALWAYS);
		this.excludeInternal.getStyleClass().add("sv-switch");
		this.excludeInternal.selectedProperty().addListener((observable, before, selected) -> {
			if (!this.updatingFromModel) {
				this.actionManagerServices.executeAction(ExclusionPolicyActionKey.SetExcludeInternalComponents,
						selected);
			}
		});
		HBox rule = new HBox(12, ruleTextBox, this.excludeInternal);
		rule.setAlignment(Pos.CENTER_LEFT);
		rule.getStyleClass().addAll("sv-excl-card", "sv-excl-rule");

		VBox content = new VBox(14, new VBox(4, title, lead), listCard, rule);
		content.getStyleClass().add("sv-excl");
		content.setMaxWidth(980);
		this.root = new ScrollPane(content);
		this.root.setFitToWidth(true);
		this.root.getStyleClass().add("sv-excl-scroll");
		this.renderRows();
	}

	// --- Rendering ---

	private void renderRows() {
		List<Class<?>> shown = this.excludedComponents.stream().filter(type -> !BuiltInExclusions.isHidden(type))
				.toList();
		this.count.setText(String.valueOf(shown.size()));
		List<Node> nodes = new ArrayList<>();
		if (shown.isEmpty()) {
			Label emptyTitle = new Label(ExclusionPolicyTexts.get("empty.title"));
			emptyTitle.getStyleClass().add("sv-excl-empty-title");
			Label emptyText = new Label(ExclusionPolicyTexts.get("empty.text"));
			emptyText.getStyleClass().add("sv-excl-muted");
			VBox empty = new VBox(6, icon(ICON_FILTER, 28), emptyTitle, emptyText);
			empty.setAlignment(Pos.CENTER);
			empty.getStyleClass().add("sv-excl-empty");
			nodes.add(empty);
			this.rows.getChildren().setAll(nodes);
			return;
		}
		nodes.add(row(header(ExclusionPolicyTexts.get("column.component")), header(ExclusionPolicyTexts.get("column.package")),
				header(ExclusionPolicyTexts.get("column.origin")), new Region(), "sv-excl-header"));
		String query = this.filter.getText() == null ? "" : this.filter.getText().trim().toLowerCase(Locale.ROOT);
		for (Class<?> componentType : shown) {
			if (!query.isEmpty() && !componentType.getName().toLowerCase(Locale.ROOT).contains(query)) {
				continue;
			}
			boolean builtIn = BuiltInExclusions.contains(componentType);
			Label name = new Label(componentType.getSimpleName());
			name.getStyleClass().add("sv-excl-name");
			Label packageName = new Label(componentType.getPackageName());
			packageName.getStyleClass().add("sv-excl-package");
			Tooltips.install(packageName, componentType.getName());
			Label origin = new Label(ExclusionPolicyTexts.get(builtIn ? "origin.builtIn" : "origin.added"));
			origin.getStyleClass().addAll("sv-excl-badge", builtIn ? "sv-excl-badge-builtin" : "sv-excl-badge-added");
			Button action = new Button(null, icon(builtIn ? ICON_LOCK : ICON_TRASH, 15));
			action.getStyleClass().add("sv-excl-action");
			if (builtIn) {
				action.setDisable(true);
				Tooltips.install(origin, ExclusionPolicyTexts.get("origin.builtIn.tip"));
			} else {
				Tooltips.install(action, ExclusionPolicyTexts.get("remove.tip"));
				action.setOnAction(event -> this.dispatchRemove(componentType));
			}
			nodes.add(row(name, packageName, origin, action, "sv-excl-row"));
		}
		if (nodes.size() == 1) {
			Label none = new Label(ExclusionPolicyTexts.get("filter.none"));
			none.getStyleClass().add("sv-excl-muted");
			HBox noneRow = new HBox(none);
			noneRow.getStyleClass().add("sv-excl-row");
			nodes.add(noneRow);
		}
		this.rows.getChildren().setAll(nodes);
	}

	private static Label header(final String text) {
		Label label = new Label(text);
		label.getStyleClass().add("sv-excl-column");
		return label;
	}

	private static Node row(final Node name, final Node packageName, final Node origin, final Node action,
			final String styleClass) {
		GridPane row = new GridPane();
		ColumnConstraints nameColumn = new ColumnConstraints();
		nameColumn.setPercentWidth(30);
		ColumnConstraints packageColumn = new ColumnConstraints();
		packageColumn.setPercentWidth(48);
		ColumnConstraints originColumn = new ColumnConstraints();
		originColumn.setPercentWidth(14);
		ColumnConstraints actionColumn = new ColumnConstraints();
		actionColumn.setPercentWidth(8);
		actionColumn.setHalignment(HPos.RIGHT);
		row.getColumnConstraints().addAll(nameColumn, packageColumn, originColumn, actionColumn);
		row.addRow(0, name, packageName, origin, action);
		row.getStyleClass().add(styleClass);
		return row;
	}

	private static Node icon(final String pathData, final double size) {
		SVGPath path = new SVGPath();
		path.setContent(pathData);
		path.getStyleClass().add("sv-icon");
		double scale = size / 24;
		path.setScaleX(scale);
		path.setScaleY(scale);
		return new Group(path);
	}

	// --- Editing ---

	private void handleAddRequested() {
		String typeName = this.addTypeNameField.getText() == null ? "" : this.addTypeNameField.getText().trim();
		if (typeName.isEmpty()) {
			return;
		}
		try {
			Class<?> componentType = Class.forName(typeName, false, ExclusionPolicyViewImpl.class.getClassLoader());
			this.addErrorLabel.setText("");
			this.addTypeNameField.clear();
			this.actionManagerServices.executeAction(ExclusionPolicyActionKey.AddExcludedComponent, componentType);
		} catch (ClassNotFoundException | LinkageError e) {
			this.addErrorLabel.setText(ExclusionPolicyTexts.get("add.unknownType", typeName));
		}
	}

	private void dispatchRemove(final Class<?> componentType) {
		List<Class<?>> remaining = new ArrayList<>(this.excludedComponents);
		remaining.remove(componentType);
		this.actionManagerServices.executeAction(ExclusionPolicyActionKey.SetExcludedComponents, remaining);
	}

	// --- ExclusionPolicyViewServices ---

	@Override
	public void showView(final ViewKey key) {
		this.ensureContentBuilt();
		Platform.runLater(() -> this.useHostingServices.openPanel(key, this.root, this.buildTabDescriptor()));
	}

	@Override
	public void hideView(final ViewKey key) {
		FxThread.run(() -> {
			this.useHostingServices.closePanel(key);
		});
	}

	private SupervisorTabDescriptorAdapter buildTabDescriptor() {
		return new SupervisorTabDescriptorAdapter() {
			@Override
			public RailEntry railEntry() {
				return RailEntry.EXCLUSIONS;
			}

			@Override
			public String title() {
				return ExclusionPolicyTexts.get("title");
			}

			@Override
			public EnumSet<ToolbarFunction> supportedToolbarFunctions() {
				return EnumSet.noneOf(ToolbarFunction.class);
			}
		};
	}

	// --- ExclusionPolicyEvents ---

	@Override
	public void onExcludedComponentsChanged(final List<Class<?>> componentTypes) {
		Platform.runLater(() -> {
			this.ensureContentBuilt();
			this.excludedComponents.clear();
			this.excludedComponents.addAll(componentTypes);
			this.renderRows();
		});
	}

	@Override
	public void onExcludeInternalComponentsChanged(final boolean exclude) {
		Platform.runLater(() -> {
			this.ensureContentBuilt();
			this.updatingFromModel = true;
			this.excludeInternal.setSelected(exclude);
			this.updatingFromModel = false;
		});
	}
}
