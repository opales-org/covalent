/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.stereotypepolicy.view.impl;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.hmi.action.ActionConsumerServices;
import org.opales.covalent.supervision.componentview.internal.Tooltips;
import org.opales.covalent.supervision.stereotypepolicy.BuiltInStereotypes;
import org.opales.covalent.supervision.stereotypepolicy.StereotypeRule;
import org.opales.covalent.supervision.stereotypepolicy.StereotypeRules;
import org.opales.covalent.supervision.stereotypepolicy.interaction.StereotypePolicyActionKey;
import org.opales.covalent.supervision.stereotypepolicy.model.StereotypePolicyEvents;
import org.opales.covalent.supervision.stereotypepolicy.view.StereotypePolicyView;
import org.opales.covalent.supervision.stereotypepolicy.view.StereotypePolicyViewServices;
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
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

/**
 * StereotypePolicy's View: the stereotype table as a filterable list - a name ending, the
 * stereotype it gives, where the rule comes from - a bar to add a rule, a switch that lets the
 * built-in rules be removed, a reset to the built-in rules, and a small tester that
 * shows which stereotype a component name gets and which ending won. Built like the Exclusions
 * panel, and styled with the same classes. Renders whatever {@link StereotypePolicyEvents}
 * delivers, never queries the Model back for it; reports every edit through
 * {@link ActionConsumerServices}.
 */
@Implementation
public class StereotypePolicyViewImpl
		implements StereotypePolicyView, StereotypePolicyViewServices, StereotypePolicyEvents {

	/** Creates the View; its content is built when it is first shown or fed. */
	public StereotypePolicyViewImpl() {
		// Nothing to initialize.
	}

	private static final String ICON_LOCK = "M5 11h14v10H5zM8 11V8a4 4 0 0 1 8 0v3";
	private static final String ICON_TRASH = "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3";
	private static final String ICON_RESET = "M4 4v6h6M4.5 10A8 8 0 1 1 6 17";

	@UsedServices
	private ActionConsumerServices actionManagerServices;

	@UsedServices
	private PanelHostingServices useHostingServices;

	private final List<StereotypeRule> rules = new ArrayList<>();

	private ScrollPane root;
	private final Label count = new Label("0");
	private final TextField filter = new TextField();
	private final VBox rows = new VBox();
	private final TextField addSuffixField = new TextField();
	private final TextField addLabelField = new TextField();
	private final Label labelCounter = new Label();
	private final Label addErrorLabel = new Label();
	private final TextField tryField = new TextField();
	private final HBox tryResult = new HBox(14);
	/** Unlocks the built-in rules: their lock becomes a trash can. Not kept from one run to the next. */
	private final CheckBox allowBuiltInRemoval = new CheckBox();
	private final Button reset = new Button();
	private boolean contentBuilt = false;

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

		Label title = new Label(StereotypePolicyTexts.get("title"));
		title.getStyleClass().add("sv-excl-title");
		Label lead = new Label(StereotypePolicyTexts.get("lead"));
		lead.getStyleClass().add("sv-excl-muted");
		lead.setWrapText(true);

		Label listTitle = new Label(StereotypePolicyTexts.get("list.title"));
		listTitle.getStyleClass().add("sv-excl-card-title");
		this.count.getStyleClass().add("sv-excl-count");
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		this.filter.setPromptText(StereotypePolicyTexts.get("filter.prompt"));
		this.filter.getStyleClass().add("sv-excl-filter");
		this.filter.textProperty().addListener(observable -> this.renderRows());
		HBox head = new HBox(10, listTitle, this.count, spacer, this.filter);
		head.setAlignment(Pos.CENTER_LEFT);
		head.getStyleClass().add("sv-excl-card-head");

		this.allowBuiltInRemoval.setText(StereotypePolicyTexts.get("builtIns.allowRemoval"));
		this.allowBuiltInRemoval.selectedProperty().addListener(observable -> this.renderRows());
		this.reset.setText(StereotypePolicyTexts.get("reset"));
		this.reset.setGraphic(icon(ICON_RESET, 14));
		Tooltips.install(this.reset, StereotypePolicyTexts.get("reset.tip"));
		this.reset.setOnAction(event -> this.confirmReset());
		Region builtInsSpacer = new Region();
		HBox.setHgrow(builtInsSpacer, Priority.ALWAYS);
		HBox builtIns = new HBox(10, this.allowBuiltInRemoval, builtInsSpacer, this.reset);
		builtIns.setAlignment(Pos.CENTER_LEFT);
		builtIns.getStyleClass().add("sv-stereo-builtins");

		this.addSuffixField.setPromptText(StereotypePolicyTexts.get("add.suffix.prompt"));
		this.addSuffixField.getStyleClass().add("sv-excl-add-field");
		HBox.setHgrow(this.addSuffixField, Priority.ALWAYS);
		this.addSuffixField.setOnAction(event -> this.handleAddRequested());
		this.addLabelField.setPromptText(StereotypePolicyTexts.get("add.label.prompt", StereotypeRules.MAX_LABEL_LENGTH));
		this.addLabelField.getStyleClass().add("sv-excl-add-field");
		HBox.setHgrow(this.addLabelField, Priority.ALWAYS);
		this.addLabelField.setOnAction(event -> this.handleAddRequested());
		this.addLabelField.setTextFormatter(new TextFormatter<String>(
				change -> change.getControlNewText().length() <= StereotypeRules.MAX_LABEL_LENGTH ? change : null));
		this.labelCounter.getStyleClass().add("sv-stereo-counter");
		this.labelCounter.setMinWidth(40);
		this.labelCounter.setAlignment(Pos.CENTER_RIGHT);
		this.refreshLabelCounter();
		this.addSuffixField.textProperty().addListener(observable -> this.addErrorLabel.setText(""));
		this.addLabelField.textProperty().addListener(observable -> {
			this.addErrorLabel.setText("");
			this.refreshLabelCounter();
		});
		Button add = new Button(StereotypePolicyTexts.get("add"));
		add.getStyleClass().add("sv-primary-button");
		add.setOnAction(event -> this.handleAddRequested());
		HBox addRow = new HBox(8, this.addSuffixField, this.addLabelField, this.labelCounter, add);
		addRow.setAlignment(Pos.CENTER_LEFT);
		this.addErrorLabel.getStyleClass().add("sv-error-label");
		this.addErrorLabel.managedProperty().bind(this.addErrorLabel.textProperty().isNotEmpty());
		VBox addBox = new VBox(6, addRow, this.addErrorLabel);
		addBox.getStyleClass().add("sv-excl-add");

		VBox listCard = new VBox(head, builtIns, this.rows, addBox);
		listCard.getStyleClass().add("sv-excl-card");

		Label tryTitle = new Label(StereotypePolicyTexts.get("try.title"));
		tryTitle.getStyleClass().add("sv-excl-card-title");
		Label tryLead = new Label(StereotypePolicyTexts.get("try.lead"));
		tryLead.getStyleClass().add("sv-excl-muted");
		this.tryField.setPromptText(StereotypePolicyTexts.get("try.prompt"));
		this.tryField.getStyleClass().add("sv-excl-add-field");
		this.tryField.setMaxWidth(420);
		this.tryField.textProperty().addListener(observable -> this.renderTry());
		this.tryResult.setAlignment(Pos.CENTER_LEFT);
		this.tryResult.setMinHeight(30);
		VBox tryCard = new VBox(10, new VBox(2, tryTitle, tryLead), this.tryField, this.tryResult);
		tryCard.getStyleClass().addAll("sv-excl-card", "sv-stereo-try");

		VBox content = new VBox(14, new VBox(4, title, lead), listCard, tryCard);
		content.getStyleClass().add("sv-excl");
		content.setMaxWidth(980);
		this.root = new ScrollPane(content);
		this.root.setFitToWidth(true);
		this.root.getStyleClass().add("sv-excl-scroll");
		this.renderRows();
		this.renderTry();
	}

	// --- Rendering ---

	private void renderRows() {
		this.count.setText(String.valueOf(this.rules.size()));
		this.reset.setDisable(this.rules.equals(BuiltInStereotypes.rules()));
		boolean builtInsRemovable = this.allowBuiltInRemoval.isSelected();
		List<Node> nodes = new ArrayList<>();
		nodes.add(row(header(StereotypePolicyTexts.get("column.suffix")), header(StereotypePolicyTexts.get("column.stereotype")),
				header(StereotypePolicyTexts.get("column.origin")), new Region(), "sv-excl-header"));
		String query = this.filter.getText() == null ? "" : this.filter.getText().trim().toLowerCase(Locale.ROOT);
		for (StereotypeRule rule : this.rules) {
			if (!query.isEmpty() && !(rule.suffix() + " " + rule.label()).toLowerCase(Locale.ROOT).contains(query)) {
				continue;
			}
			Label suffix = new Label(rule.suffix());
			suffix.getStyleClass().add("sv-excl-name");
			Label stereotype = new Label(rule.label());
			stereotype.getStyleClass().add("sv-excl-package");
			Label origin = new Label(StereotypePolicyTexts.get(rule.builtIn() ? "origin.builtIn" : "origin.added"));
			origin.getStyleClass().addAll("sv-excl-badge", rule.builtIn() ? "sv-excl-badge-builtin" : "sv-excl-badge-added");
			boolean locked = rule.builtIn() && !builtInsRemovable;
			Button action = new Button(null, icon(locked ? ICON_LOCK : ICON_TRASH, 15));
			action.getStyleClass().add("sv-excl-action");
			if (rule.builtIn()) {
				Tooltips.install(origin, StereotypePolicyTexts.get("origin.builtIn.tip"));
			}
			if (locked) {
				action.setDisable(true);
			} else {
				Tooltips.install(action, StereotypePolicyTexts.get("remove.tip"));
				action.setOnAction(event -> this.dispatchRemove(rule));
			}
			nodes.add(row(suffix, stereotype, origin, action, "sv-excl-row"));
		}
		if (nodes.size() == 1) {
			Label none = new Label(StereotypePolicyTexts.get("filter.none"));
			none.getStyleClass().add("sv-excl-muted");
			HBox noneRow = new HBox(none);
			noneRow.getStyleClass().add("sv-excl-row");
			nodes.add(noneRow);
		}
		this.rows.getChildren().setAll(nodes);
	}

	/** What the tester tells about the name typed: the stereotype it gets and the endings that matched. */
	private void renderTry() {
		String name = this.tryField.getText() == null ? "" : this.tryField.getText().trim();
		List<Node> nodes = new ArrayList<>();
		if (name.isEmpty()) {
			nodes.add(muted(StereotypePolicyTexts.get("try.empty")));
		} else {
			List<StereotypeRule> matching = StereotypeRules.matching(this.rules, name);
			if (matching.isEmpty()) {
				Label fallback = muted(StereotypePolicyTexts.get("try.fallback"));
				fallback.setWrapText(true);
				nodes.add(fallback);
			} else {
				Label pill = new Label("«" + matching.get(0).label() + "»");
				pill.getStyleClass().add("sv-stereo-pill");
				StringBuilder explanation = new StringBuilder(StereotypePolicyTexts.get("try.winner", matching.get(0).suffix()));
				if (matching.size() > 1) {
					List<String> others = new ArrayList<>();
					for (StereotypeRule other : matching.subList(1, matching.size())) {
						others.add(other.suffix());
					}
					explanation.append(". ").append(StereotypePolicyTexts.get("try.others", String.join(", ", others)));
				}
				nodes.add(pill);
				nodes.add(muted(explanation.toString()));
			}
		}
		this.tryResult.getChildren().setAll(nodes);
	}

	private void refreshLabelCounter() {
		this.labelCounter.setText(this.addLabelField.getText().length() + "/" + StereotypeRules.MAX_LABEL_LENGTH);
	}

	private static Label muted(final String text) {
		Label label = new Label(text);
		label.getStyleClass().add("sv-excl-muted");
		return label;
	}

	private static Label header(final String text) {
		Label label = new Label(text);
		label.getStyleClass().add("sv-excl-column");
		return label;
	}

	private static Node row(final Node suffix, final Node stereotype, final Node origin, final Node action,
			final String styleClass) {
		GridPane row = new GridPane();
		ColumnConstraints suffixColumn = new ColumnConstraints();
		suffixColumn.setPercentWidth(32);
		ColumnConstraints stereotypeColumn = new ColumnConstraints();
		stereotypeColumn.setPercentWidth(34);
		ColumnConstraints originColumn = new ColumnConstraints();
		originColumn.setPercentWidth(22);
		ColumnConstraints actionColumn = new ColumnConstraints();
		actionColumn.setPercentWidth(12);
		actionColumn.setHalignment(HPos.RIGHT);
		row.getColumnConstraints().addAll(suffixColumn, stereotypeColumn, originColumn, actionColumn);
		row.addRow(0, suffix, stereotype, origin, action);
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
		String suffix = this.addSuffixField.getText() == null ? "" : this.addSuffixField.getText().trim();
		String label = this.addLabelField.getText() == null ? "" : this.addLabelField.getText().trim();
		StereotypeRules.Problem problem = StereotypeRules.check(this.rules, suffix, label);
		if (problem != null) {
			this.addErrorLabel.setText(describe(problem, suffix));
			return;
		}
		this.addSuffixField.clear();
		this.addLabelField.clear();
		this.addErrorLabel.setText("");
		this.actionManagerServices.executeAction(StereotypePolicyActionKey.AddStereotype,
				new StereotypeRule(suffix, label, false));
	}

	private static String describe(final StereotypeRules.Problem problem, final String suffix) {
		return switch (problem) {
			case SUFFIX_EMPTY -> StereotypePolicyTexts.get("error.suffixEmpty");
			case SUFFIX_INVALID -> StereotypePolicyTexts.get("error.suffixInvalid");
			case LABEL_EMPTY -> StereotypePolicyTexts.get("error.labelEmpty");
			case LABEL_TOO_LONG -> StereotypePolicyTexts.get("error.labelTooLong", StereotypeRules.MAX_LABEL_LENGTH);
			case SUFFIX_EXISTS -> StereotypePolicyTexts.get("error.suffixExists", suffix);
		};
	}

	/** Asks before bringing the table back to the built-in rules: the rules the user added are lost. */
	private void confirmReset() {
		Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
		alert.setTitle(StereotypePolicyTexts.get("reset"));
		alert.setHeaderText(null);
		Label message = new Label(StereotypePolicyTexts.get("reset.confirm"));
		message.setWrapText(true);
		message.setPrefWidth(420);
		alert.getDialogPane().setContent(message);
		ButtonType accept = new ButtonType(StereotypePolicyTexts.get("reset.accept"), ButtonBar.ButtonData.OK_DONE);
		ButtonType cancel = new ButtonType(StereotypePolicyTexts.get("reset.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
		alert.getButtonTypes().setAll(cancel, accept);
		alert.getDialogPane().lookupButton(accept).getStyleClass().add("sv-danger-button");
		if (this.root != null && this.root.getScene() != null) {
			alert.initOwner(this.root.getScene().getWindow());
			alert.getDialogPane().getStylesheets().setAll(this.root.getScene().getStylesheets());
			alert.getDialogPane().getStyleClass().add("sv-dialog-pane");
		}
		if (alert.showAndWait().filter(answer -> answer == accept).isPresent()) {
			this.allowBuiltInRemoval.setSelected(false);
			this.actionManagerServices.executeAction(StereotypePolicyActionKey.ResetStereotypes);
		}
	}

	private void dispatchRemove(final StereotypeRule rule) {
		this.actionManagerServices.executeAction(StereotypePolicyActionKey.RemoveStereotype, rule.suffix());
	}

	// --- StereotypePolicyViewServices ---

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
				return RailEntry.STEREOTYPES;
			}

			@Override
			public String title() {
				return StereotypePolicyTexts.get("title");
			}

			@Override
			public EnumSet<ToolbarFunction> supportedToolbarFunctions() {
				return EnumSet.noneOf(ToolbarFunction.class);
			}
		};
	}

	// --- StereotypePolicyEvents ---

	@Override
	public void onStereotypesChanged(final List<StereotypeRule> changedRules) {
		Platform.runLater(() -> {
			this.ensureContentBuilt();
			this.rules.clear();
			this.rules.addAll(changedRules);
			this.renderRows();
			this.renderTry();
		});
	}
}
