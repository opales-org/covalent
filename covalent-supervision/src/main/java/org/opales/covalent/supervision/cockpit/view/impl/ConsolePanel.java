/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.cockpit.view.impl;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.hmi.action.ActionConsumerServices;
import org.opales.covalent.supervision.cockpit.interaction.CockpitActionKey;
import org.opales.covalent.supervision.cockpit.interaction.LifecycleRequest;
import org.opales.covalent.supervision.cockpit.model.CockpitSnapshot;
import org.opales.covalent.supervision.cockpit.model.LifecycleCommand;
import org.opales.covalent.supervision.componentdiagram.interaction.ComponentDiagramToolActionKey;
import org.opales.covalent.supervision.componentview.internal.Tooltips;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisorMessages;

import javafx.animation.PauseTransition;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

/**
 * The cockpit's console: one slice per composite of the level shown, like a mixing desk, those
 * nobody depends on at the left, and at the bottom, apart, the container's own controls, which
 * act only once armed. A slice shows its composite's direct children; a slice or a channel
 * holding composites can be entered, and a breadcrumb goes back up. The displays show the real
 * states; a command never changes them itself.
 */
final class ConsolePanel {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

	/** How long the container's controls stay armed. */
	private static final Duration ARMED = Duration.seconds(60);

	/** Why Covalent's own components can be seen but not driven from here. */
	private static final String INTERNAL_NOTE = CockpitTexts.get("console.internalNote");

	/** A composite channel's icon: three hexagons, as the component diagram's composites. */
	private static final String ICON_COMPOSITE = "M12 2.5l4 2.3v4.6l-4 2.3-4-2.3V4.8zM7 11.6l4 2.3v4.6l-4 2.3-4-2.3v-4.6z"
			+ "M17 11.6l4 2.3v4.6l-4 2.3-4-2.3v-4.6z";

	/** The most state dots a composite channel shows; its count gives the rest. */
	private static final int DOTS = 10;

	/** How far behind a navigable slice its stacked cards stand. */
	private static final double CARD_OFFSET = 6;

	/** The lines of the container's log. */
	private static final int LOG_LINES = 5;

	/** The height of the container commands' row, matching {@code .sv-console-commands-hidden}. */
	private static final double COMMAND_ROW_HEIGHT = 40;

	/** The vu-meter's segments. */
	private static final int SEGMENTS = 8;

	/** The small commands under a slice, in the order of the state diagram. */
	private static final LifecycleCommand[] SLICE_COMMANDS = { LifecycleCommand.INSTANTIATE, LifecycleCommand.INITIALIZE,
			LifecycleCommand.REMOVE, LifecycleCommand.UNDEPLOY, LifecycleCommand.RESET };

	private final ActionConsumerServices actions;
	private final BorderPane root = new BorderPane();
	private final HBox rack = new HBox(12);
	private final Map<Object, Strip> strips = new HashMap<>();
	private List<Object> stripOrder = List.of();
	private final HBox crumbs = new HBox(6);
	private final Button up = new Button(CockpitTexts.get("console.up"));
	private List<Class<?>> focus;

	private final Label containerDisplay = new Label();
	private final Label containerState = new Label(stateText(ComponentState.activated));
	private final ToggleButton arm = new ToggleButton();
	private final Label armLabel = new Label();
	private final Label armState = new Label();
	private final Label armText = new Label();
	private final VBox safety = new VBox(10);
	private final Region countdown = new Region();
	private final DoubleProperty timeLeft = new SimpleDoubleProperty();
	private final HBox commands = new HBox(10);
	private final Label commandsHidden = new Label(CockpitTexts.get("console.commands.hidden"));
	private final List<Button> containerButtons = new ArrayList<>();
	private final VBox containerLog = new VBox(4);
	private final List<LogLine> logLines = new ArrayList<>();
	private final PauseTransition disarm = new PauseTransition(ARMED);

	ConsolePanel(final ActionConsumerServices actions) {
		this.actions = actions;
		this.build();
	}

	Node getRoot() {
		return this.root;
	}

	// --- Building ---

	private void build() {
		this.crumbs.getStyleClass().add("sv-console-crumbs");
		this.crumbs.setAlignment(Pos.CENTER_LEFT);
		this.crumbs.setMinWidth(Region.USE_PREF_SIZE);
		this.up.getStyleClass().add("sv-console-up");
		this.up.setMinWidth(Region.USE_PREF_SIZE);
		// Visible before the first snapshot has given the focus: the way up then leads to the top.
		this.up.setOnAction(event -> this.actions.executeAction(CockpitActionKey.ShowConsoleLevel,
				Math.max(0, (this.focus == null ? 0 : this.focus.size()) - 1)));
		HBox navigation = new HBox(14, this.crumbs, this.up);
		navigation.setAlignment(Pos.CENTER_LEFT);
		this.rack.getStyleClass().add("sv-console-rack");
		this.rack.setAlignment(Pos.TOP_LEFT);
		ScrollPane rackScroll = new ScrollPane(this.rack);
		rackScroll.getStyleClass().add("sv-console-scroll");
		// As tall as the slices, as wide as it is given: beyond it, the slices scroll sideways.
		rackScroll.setPrefViewportWidth(0);
		rackScroll.setMinWidth(0);
		rackScroll.setMinHeight(Region.USE_PREF_SIZE);
		rackScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
		rackScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		VBox top = new VBox(12, navigation, rackScroll);
		top.getStyleClass().add("sv-console-top");

		// The whole console scrolls vertically when the window is too short for it.
		VBox content = new VBox(top, this.buildContainerConsole());
		content.setMinWidth(0);
		ScrollPane page = new ScrollPane(content);
		page.setFitToWidth(true);
		page.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		page.getStyleClass().add("sv-console-scroll");
		page.setMinWidth(0);
		// Over the slices, the wheel scrolls the console up and down while it is taller than the
		// window, sideways otherwise; Shift + wheel always scrolls sideways.
		rackScroll.addEventFilter(ScrollEvent.SCROLL, event -> {
			double pageHidden = content.getHeight() - page.getViewportBounds().getHeight();
			boolean sideways = event.isShiftDown() || event.getDeltaX() != 0 || pageHidden <= 1;
			if (sideways) {
				double delta = event.getDeltaX() != 0 ? event.getDeltaX() : event.getDeltaY();
				double hidden = Math.max(1, this.rack.getWidth() - rackScroll.getViewportBounds().getWidth());
				rackScroll.setHvalue(rackScroll.getHvalue() - delta / hidden);
			} else {
				page.setVvalue(page.getVvalue() - event.getDeltaY() / pageHidden);
			}
			event.consume();
		});
		this.root.setCenter(page);
		this.root.getStyleClass().add("sv-console");
		this.root.setMinWidth(0);
	}

	/**
	 * The container's own controls, apart under the slices: its readout; the safety, whose
	 * button arms the commands for a while, with the time left; the commands, shown only while
	 * armed. Under them, past the hazard band, the log of what the console did.
	 */
	private Node buildContainerConsole() {
		// The container display, the arm button and the commands each stand in a row of the
		// commands' own height, centered, so that the three columns line up.
		this.containerDisplay.setMinWidth(Region.USE_PREF_SIZE);
		this.containerState.setMinWidth(Region.USE_PREF_SIZE);
		HBox display = new HBox(10, this.containerDisplay, this.containerState);
		display.setMinWidth(Region.USE_PREF_SIZE);
		display.getStyleClass().addAll("sv-console-display", "sv-console-display-accent");
		this.containerDisplay.getStyleClass().add("sv-console-display-value");
		display.setAlignment(Pos.BASELINE_LEFT);
		HBox displayRow = commandRow(display);
		VBox identity = new VBox(10, eyebrow("console.container"), displayRow);
		identity.getStyleClass().add("sv-console-container-column");
		identity.setMinWidth(Region.USE_PREF_SIZE);

		// The arm button's label stands on a plain patch over its stripes.
		this.armLabel.getStyleClass().add("sv-console-arm-label");
		this.arm.setGraphic(this.armLabel);
		this.arm.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		this.arm.getStyleClass().add("sv-console-arm");
		this.arm.setMinWidth(Region.USE_PREF_SIZE);
		this.arm.selectedProperty().addListener((observable, before, armed) -> this.onArmed(armed));
		this.disarm.setOnFinished(event -> this.arm.setSelected(false));
		this.disarm.currentTimeProperty().addListener((observable, before, now) -> this.showTimeLeft());
		this.armState.getStyleClass().add("sv-console-arm-state");
		this.armText.getStyleClass().add("sv-console-arm-text");
		this.armState.setMinWidth(Region.USE_PREF_SIZE);
		this.armText.setMinWidth(Region.USE_PREF_SIZE);
		VBox armWords = new VBox(2, this.armState, this.armText);
		armWords.setAlignment(Pos.CENTER_LEFT);
		HBox armRow = commandRow(this.arm, armWords);
		armRow.setSpacing(12);
		// The time left before disarming, as a line that shortens.
		this.countdown.getStyleClass().add("sv-console-countdown-fill");
		this.countdown.setMaxWidth(0);
		HBox countdownTrack = new HBox(this.countdown);
		countdownTrack.getStyleClass().add("sv-console-countdown");
		this.countdown.maxWidthProperty().bind(countdownTrack.widthProperty().multiply(this.timeLeft));
		this.countdown.prefWidthProperty().bind(this.countdown.maxWidthProperty());
		this.safety.getChildren().setAll(eyebrow("console.safety"), armRow, countdownTrack);
		this.safety.getStyleClass().addAll("sv-console-container-column", "sv-console-safety");
		this.safety.setMinWidth(Region.USE_PREF_SIZE);

		Region removeApart = new Region();
		removeApart.getStyleClass().add("sv-console-command-gap");
		this.commands.getChildren().setAll(this.containerButton(LifecycleCommand.ACTIVATE, false),
				this.containerButton(LifecycleCommand.PASSIVATE, false), removeApart,
				this.containerButton(LifecycleCommand.REMOVE, true));
		this.commands.setAlignment(Pos.CENTER_LEFT);
		this.commandsHidden.getStyleClass().add("sv-console-commands-hidden");
		this.commandsHidden.setMaxWidth(Double.MAX_VALUE);
		StackPane commandArea = new StackPane(this.commands, this.commandsHidden);
		commandArea.setAlignment(Pos.CENTER_LEFT);
		VBox commandColumn = new VBox(10, eyebrow("console.commands"), commandArea);
		commandColumn.getStyleClass().add("sv-console-container-column");
		commandColumn.setMinWidth(Region.USE_PREF_SIZE);
		HBox.setHgrow(commandColumn, Priority.ALWAYS);

		this.containerLog.getStyleClass().add("sv-console-log");
		for (int index = 0; index < LOG_LINES; index++) {
			LogLine line = new LogLine();
			this.logLines.add(line);
			this.containerLog.getChildren().add(line.node);
		}
		this.containerLog.setMinHeight(Region.USE_PREF_SIZE);
		VBox logColumn = new VBox(8, eyebrow("console.log"), this.containerLog);
		logColumn.getStyleClass().add("sv-console-log-area");

		HBox body = new HBox(identity, separator(), this.safety, separator(), commandColumn);
		body.getStyleClass().add("sv-console-container-body");
		Region topStripes = new Region();
		topStripes.getStyleClass().add("sv-console-hazard");
		Region bottomStripes = new Region();
		bottomStripes.getStyleClass().add("sv-console-hazard");
		// The log goes under the band, apart from the controls.
		VBox container = new VBox(topStripes, body, bottomStripes, logColumn);
		container.getStyleClass().add("sv-console-container");
		this.onArmed(false);
		return container;
	}

	private static Label eyebrow(final String key) {
		Label eyebrow = new Label(CockpitTexts.get(key));
		eyebrow.getStyleClass().add("sv-console-eyebrow");
		eyebrow.setMinWidth(Region.USE_PREF_SIZE);
		return eyebrow;
	}

	/** A thin line between two columns of the container's controls. */

	/**
	 * A row as high as the container commands, its content centered on it at its own height: the height of the
	 * dashed note that stands where the commands appear until armed ({@code
	 * .sv-console-commands-hidden}).
	 */
	private static HBox commandRow(final Node... content) {
		HBox row = new HBox(content);
		row.setMinHeight(COMMAND_ROW_HEIGHT);
		row.setAlignment(Pos.CENTER_LEFT);
		row.setFillHeight(false);
		return row;
	}

	private static Region separator() {
		Region separator = new Region();
		separator.getStyleClass().add("sv-console-container-separator");
		return separator;
	}

	/** A button applying a command to the whole container: activate, passivate or remove all. */
	private Button containerButton(final LifecycleCommand command, final boolean danger) {
		String key = "all." + command.name();
		Button button = new Button(CockpitTexts.get(key).toUpperCase(SupervisorMessages.locale()));
		button.getStyleClass().add("sv-console-big");
		button.setMinWidth(Region.USE_PREF_SIZE);
		if (danger) {
			button.getStyleClass().add("sv-console-danger");
		}
		button.setOnAction(event -> {
			if (command == LifecycleCommand.ACTIVATE || this.confirm(CockpitTexts.get(key + ".title"),
					CockpitTexts.get(key + ".text"), List.of(), CockpitTexts.get(key + ".accept"), danger)) {
				this.actions.executeAction(CockpitActionKey.ContainerCommand, command);
				this.arm.setSelected(false);
			}
		});
		this.containerButtons.add(button);
		return button;
	}

	private void onArmed(final boolean armed) {
		for (Button button : this.containerButtons) {
			button.setDisable(!armed);
		}
		this.commands.setVisible(armed);
		this.commandsHidden.setVisible(!armed);
		this.armLabel.setText(CockpitTexts.get(armed ? "console.disarm" : "console.arm"));
		this.armText.setText(CockpitTexts.get(armed ? "console.armed.text" : "console.arm.text"));
		this.safety.getStyleClass().remove("sv-console-armed");
		this.armState.getStyleClass().remove("sv-console-armed");
		if (armed) {
			this.safety.getStyleClass().add("sv-console-armed");
			this.armState.getStyleClass().add("sv-console-armed");
			this.disarm.playFromStart();
		} else {
			this.disarm.stop();
		}
		this.showTimeLeft();
	}

	/** The state of the safety and, while armed, the seconds left before it disarms. */
	private void showTimeLeft() {
		if (this.arm.isSelected()) {
			double left = Math.max(0, ARMED.toSeconds() - this.disarm.getCurrentTime().toSeconds());
			this.timeLeft.set(left / ARMED.toSeconds());
			this.armState.setText(CockpitTexts.get("console.armed.state", (int) Math.ceil(left)));
		} else {
			this.timeLeft.set(0);
			this.armState.setText(CockpitTexts.get("console.disarmed.state"));
		}
	}

	// --- Updating ---

	void update(final CockpitSnapshot snapshot) {
		if (!snapshot.consoleFocus().equals(this.focus)) {
			this.focus = snapshot.consoleFocus();
			this.updateNavigation();
		}
		double busiest = 1;
		for (CockpitSnapshot.Slice slice : snapshot.slices()) {
			busiest = Math.max(busiest, slice.messagesPerSecond());
		}
		List<Object> order = new ArrayList<>();
		for (CockpitSnapshot.Slice slice : snapshot.slices()) {
			Object key = slice.compositeType() == null ? "standalone" : slice.compositeType();
			order.add(key);
			Strip strip = this.strips.computeIfAbsent(key, ignored -> new Strip());
			strip.update(slice, busiest);
		}
		this.strips.keySet().retainAll(order);
		if (!order.equals(this.stripOrder)) {
			this.stripOrder = order;
			List<Node> nodes = new ArrayList<>();
			for (Object key : order) {
				nodes.add(this.strips.get(key).node);
			}
			this.rack.getChildren().setAll(nodes);
		}

		this.containerDisplay.setText(snapshot.consoleActivated() + "/" + snapshot.consoleDriven());
		// The log's lines stay in place and only change their text: a line rebuilt at each update
		// would close the tooltip under the pointer.
		List<CockpitSnapshot.LogEntry> entries = snapshot.consoleLog();
		for (int index = 0; index < LOG_LINES; index++) {
			LogLine line = this.logLines.get(index);
			if (index < entries.size()) {
				CockpitSnapshot.LogEntry entry = entries.get(index);
				line.show(TIME.format(Instant.ofEpochMilli(entry.timeMillis())), CockpitTexts.of(entry.text()),
						entry.failed());
			} else if (index == 0) {
				line.show("", CockpitTexts.get("console.nothingDone"), false);
			} else {
				// Blank lines keep the console's height as the log fills.
				line.show("", " ", false);
			}
		}
	}

	/** The breadcrumb, from the top to the composite entered, and the way up. */
	private void updateNavigation() {
		List<Node> nodes = new ArrayList<>();
		for (int depth = 0; depth <= this.focus.size(); depth++) {
			String name = depth == 0 ? CockpitTexts.get("console.top") : this.focus.get(depth - 1).getSimpleName();
			if (depth > 0) {
				Label separator = new Label("›");
				separator.getStyleClass().add("sv-console-crumb-separator");
				nodes.add(separator);
			}
			if (depth == this.focus.size()) {
				Label current = new Label(name);
				current.getStyleClass().add("sv-console-crumb-current");
				nodes.add(current);
			} else {
				Button crumb = new Button(name);
				crumb.getStyleClass().add("sv-console-crumb");
				int level = depth;
				crumb.setOnAction(event -> this.actions.executeAction(CockpitActionKey.ShowConsoleLevel, level));
				if (depth > 0) {
					Tooltips.install(crumb, this.focus.get(depth - 1).getName());
				}
				nodes.add(crumb);
			}
		}
		this.crumbs.getChildren().setAll(nodes);
		boolean inside = !this.focus.isEmpty();
		this.up.setVisible(inside);
		this.up.setManaged(inside);
	}

	/** Whether a click landed on one of a slice's own controls, which handle it themselves. */
	private static boolean onControl(final Object target, final Node slice) {
		for (Node node = target instanceof Node start ? start : null; node != null && node != slice;
				node = node.getParent()) {
			if (node instanceof ButtonBase || node.getStyleClass().contains("sv-console-composite")) {
				return true;
			}
		}
		return false;
	}

	// --- Commands ---

	/** Asks for a transition, after confirmation when it takes something away from others. */
	private void request(final LifecycleCommand command, final Class<?> componentType,
			final List<CockpitSnapshot.Dependent> dependents) {
		String name = componentType.getSimpleName();
		long users = dependents.stream().map(CockpitSnapshot.Dependent::componentType).distinct().count();
		boolean confirmed = switch (command) {
			case PASSIVATE -> dependents.isEmpty() || this.confirm(CockpitTexts.get("confirm.passivate.title", name),
					CockpitTexts.count("confirm.passivate.users", users), dependents,
					CockpitTexts.get("confirm.passivate.accept"), false);
			case REMOVE -> this.confirm(CockpitTexts.get("confirm.remove.title", name),
					CockpitTexts.get("confirm.remove.text") + dependentsText(users), dependents,
					CockpitTexts.get("confirm.remove.accept"), true);
			case UNDEPLOY -> this.confirm(CockpitTexts.get("confirm.undeploy.title", name),
					CockpitTexts.get("confirm.undeploy.text") + dependentsText(users), dependents,
					CockpitTexts.get("confirm.undeploy.accept"), true);
			default -> true;
		};
		if (confirmed) {
			this.actions.executeAction(CockpitActionKey.Command, new LifecycleRequest(command, componentType));
		}
	}

	/** @return the sentence counting the components depending on one, after a space; empty for none */
	private static String dependentsText(final long users) {
		return users == 0 ? "" : " " + CockpitTexts.count("confirm.dependents", users);
	}

	private boolean confirm(final String title, final String text, final List<CockpitSnapshot.Dependent> dependents,
			final String accept, final boolean danger) {
		Alert alert = new Alert(Alert.AlertType.WARNING);
		alert.setTitle(title);
		alert.setHeaderText(title);
		VBox content = new VBox(8);
		Label message = new Label(text);
		message.setWrapText(true);
		content.getChildren().add(message);
		Map<Class<?>, List<String>> portsByComponent = new LinkedHashMap<>();
		for (CockpitSnapshot.Dependent dependent : dependents) {
			portsByComponent.computeIfAbsent(dependent.componentType(), ignored -> new ArrayList<>())
					.add(dependent.portType().getSimpleName());
		}
		for (Map.Entry<Class<?>, List<String>> dependent : portsByComponent.entrySet()) {
			Label component = new Label("•  " + dependent.getKey().getSimpleName());
			Label port = new Label("· " + String.join(", ", dependent.getValue()));
			port.setWrapText(true);
			port.getStyleClass().add("sv-console-dialog-port");
			content.getChildren().add(new HBox(6, component, port));
		}
		content.setPrefWidth(420);
		alert.getDialogPane().setContent(content);
		ButtonType acceptType = new ButtonType(accept, ButtonBar.ButtonData.OK_DONE);
		ButtonType cancelType = new ButtonType(CockpitTexts.get("confirm.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
		alert.getButtonTypes().setAll(cancelType, acceptType);
		if (this.root.getScene() != null) {
			alert.initOwner(this.root.getScene().getWindow());
			alert.getDialogPane().getStylesheets().setAll(this.root.getScene().getStylesheets());
			alert.getDialogPane().getStyleClass().add("sv-console-dialog");
		}
		Node acceptButton = alert.getDialogPane().lookupButton(acceptType);
		acceptButton.getStyleClass().add(danger ? "sv-console-dialog-danger" : "sv-console-dialog-warn");
		Optional<ButtonType> answer = alert.showAndWait();
		return answer.isPresent() && answer.get() == acceptType;
	}

	private static String stateText(final ComponentState state) {
		return CockpitTexts.state(state).toUpperCase(SupervisorMessages.locale());
	}

	/** @return the text with a hair space between its letters: a readout's wide lettering */
	private static String spaced(final String text) {
		return String.join("\u200A", text.codePoints().mapToObj(Character::toString).toList());
	}

	/** @return a component's state, as its tooltip tells it */
	private static String stateTip(final ComponentState state) {
		return state == null ? CockpitTexts.get("channel.stateUnknown") : CockpitTexts.state(state);
	}

	private static String rate(final double value) {
		return value < 10 ? String.format(Locale.ROOT, "%.1f", value) : String.format(Locale.ROOT, "%.0f", value);
	}

	private static void setStateClass(final Node node, final String prefix, final ComponentState state) {
		node.getStyleClass().removeIf(styleClass -> styleClass.startsWith(prefix));
		node.getStyleClass().add(prefix + (state == null ? "unknown" : state.name()));
	}

	// --- One slice ---

	/** A slice: built once, then updated in place so that its menus and tooltips stay put. */
	private final class Strip {

		private final VBox root = new VBox(12);
		/** The slice with, behind it, the stacked cards drawn while it can be entered. */
		private final StackPane node;
		private final Region[] cards = { new Region(), new Region() };
		private final Label enter = new Label(CockpitTexts.get("strip.enter"));
		private final Label title = new Label();
		/** Installed once and updated in place: a tooltip replaced at each refresh hides as soon as it shows. */
		private final Tooltip titleTip = Tooltips.install(this.title, "");
		private final Label count = new Label();
		private final Label display = new Label();
		private final Label displayRate = new Label();
		private final HBox displayBox = new HBox(display, new Region(), displayRate);
		private final ToggleButton master = new ToggleButton();
		private final HBox masterRow;
		private final HBox vu = new HBox(3);
		private final VBox channels = new VBox(7);
		private final Button restart = new Button(CockpitTexts.get("strip.restart"));
		private final Button view = new Button(CockpitTexts.get("strip.view"));
		private final HBox pads = new HBox(6, this.restart, this.view);
		private final FlowPane commands = new FlowPane(4, 4);
		private final Map<LifecycleCommand, Button> commandButtons = new HashMap<>();
		private final Map<Class<?>, Row> rows = new HashMap<>();
		private List<Class<?>> rowOrder = List.of();
		private CockpitSnapshot.Slice slice;

		Strip() {
			this.title.getStyleClass().add("sv-console-strip-title");
			this.count.getStyleClass().add("sv-console-count");
			Region titleSpacer = new Region();
			HBox.setHgrow(titleSpacer, Priority.ALWAYS);
			this.enter.getStyleClass().add("sv-console-enter");
			this.enter.setMinWidth(Region.USE_PREF_SIZE);
			HBox titleRow = new HBox(8, this.title, titleSpacer, this.enter, this.count);
			titleRow.setAlignment(Pos.CENTER_LEFT);
			this.count.setMinWidth(Region.USE_PREF_SIZE);

			HBox.setHgrow(this.displayBox.getChildren().get(1), Priority.ALWAYS);
			this.displayBox.getStyleClass().add("sv-console-display");
			this.displayBox.setAlignment(Pos.BASELINE_LEFT);
			this.display.getStyleClass().add("sv-console-display-value");
			this.displayRate.getStyleClass().add("sv-console-display-small");

			Label masterLabel = new Label(spaced(CockpitTexts.get("strip.activated").toUpperCase(SupervisorMessages.locale())));
			masterLabel.getStyleClass().add("sv-console-master-label");
			Region masterSpacer = new Region();
			HBox.setHgrow(masterSpacer, Priority.ALWAYS);
			this.master.getStyleClass().addAll("sv-switch", "sv-console-switch");
			this.master.setOnAction(event -> {
				boolean wanted = this.master.isSelected();
				this.master.setSelected(this.slice.state() == ComponentState.activated);
				request(wanted ? LifecycleCommand.ACTIVATE : LifecycleCommand.PASSIVATE, this.slice.compositeType(),
						this.slice.dependents());
			});
			this.masterRow = new HBox(masterLabel, masterSpacer, this.master);
			this.masterRow.setAlignment(Pos.CENTER_LEFT);

			this.vu.getStyleClass().add("sv-console-vu");
			this.vu.setAlignment(Pos.BOTTOM_LEFT);
			for (int segment = 0; segment < SEGMENTS; segment++) {
				Region bar = new Region();
				bar.getStyleClass().add("sv-console-vu-bar");
				bar.setPrefHeight(6 + 20.0 * segment / (SEGMENTS - 1));
				bar.setMaxHeight(Region.USE_PREF_SIZE);
				if (segment >= SEGMENTS - 2) {
					bar.getStyleClass().add("sv-console-vu-hot");
				}
				this.vu.getChildren().add(bar);
			}

			this.channels.getStyleClass().add("sv-console-channels");

			this.restart.getStyleClass().add("sv-console-pad");
			this.view.getStyleClass().add("sv-console-pad");
			Tooltips.install(this.restart, CockpitTexts.get("strip.restart.tip"));
			Tooltips.install(this.view, CockpitTexts.get("strip.view.tip"));
			this.restart.setOnAction(event -> request(LifecycleCommand.RESTART, this.slice.compositeType(), List.of()));
			this.view.setOnAction(event -> actions.executeAction(ComponentDiagramToolActionKey.OpenComposite,
					this.slice.compositeType()));

			for (LifecycleCommand command : SLICE_COMMANDS) {
				Button button = new Button(CockpitTexts.command(command));
				button.getStyleClass().add("sv-console-cmd");
				if (command.isDestructive()) {
					button.getStyleClass().add("sv-console-cmd-danger");
				}
				button.setOnAction(event -> request(command, this.slice.compositeType(), this.slice.dependents()));
				this.commandButtons.put(command, button);
				this.commands.getChildren().add(button);
			}

			// Every component shown: the slices take the height they need, and the console scrolls.
			Region grow = new Region();
			VBox.setVgrow(grow, Priority.ALWAYS);
			// The state's color as a straight line across the slice's top, like a lit strip on a rack.
			Region accent = new Region();
			accent.getStyleClass().add("sv-console-strip-accent");
			VBox.setMargin(accent, new Insets(-4, -4, -2, -4));
			this.root.getChildren().addAll(accent, titleRow, this.displayBox, this.masterRow, this.vu, this.channels,
					grow, this.pads, this.commands);
			this.root.getStyleClass().add("sv-console-strip");
			this.root.setPrefWidth(250);
			this.root.setMinWidth(250);
			this.root.setOnMouseClicked(event -> {
				if (this.slice.navigable() && !onControl(event.getTarget(), this.root)) {
					actions.executeAction(CockpitActionKey.EnterComposite, this.slice.compositeType());
				}
			});

			for (int card = 0; card < this.cards.length; card++) {
				this.cards[card].getStyleClass().addAll("sv-console-strip-card", "sv-console-strip-card-" + (card + 1));
				this.cards[card].setTranslateX(CARD_OFFSET * (card + 1));
				this.cards[card].setTranslateY(CARD_OFFSET * (card + 1));
				this.cards[card].setMouseTransparent(true);
			}
			this.node = new StackPane(this.cards[1], this.cards[0], this.root);
			this.node.setAlignment(Pos.TOP_LEFT);
		}

		void update(final CockpitSnapshot.Slice slice, final double busiest) {
			this.slice = slice;
			boolean composite = slice.compositeType() != null;
			this.title.setText(composite ? slice.compositeType().getSimpleName() : CockpitTexts.get("strip.standalone"));
			this.titleTip.setText(!composite ? CockpitTexts.get("strip.standalone.tip")
					: slice.internal() ? slice.compositeType().getName() + "\n" + INTERNAL_NOTE
							: slice.compositeType().getName());
			boolean navigable = slice.navigable();
			this.enter.setVisible(navigable);
			this.enter.setManaged(navigable);
			this.root.getStyleClass().remove("sv-console-navigable");
			if (navigable) {
				this.root.getStyleClass().add("sv-console-navigable");
			}
			for (Region card : this.cards) {
				card.setVisible(navigable);
			}
			// Room for the cards behind, inside the slice's own place in the rack; every slice keeps
			// it below, so that they all end at the same height.
			this.node.setPadding(new Insets(0, navigable ? CARD_OFFSET * 2 : 0, CARD_OFFSET * 2, 0));
			this.count.setText(String.valueOf(slice.channels().size()));
			ComponentState state = composite ? slice.state() : null;
			this.display.setText(composite ? spaced(stateText(state)) : "-");
			this.displayRate.setText(rate(slice.messagesPerSecond()) + "/s");
			setStateClass(this.root, "sv-console-strip-", state);

			this.masterRow.setVisible(composite);
			this.masterRow.setManaged(composite);
			this.master.setSelected(state == ComponentState.activated);
			this.master.setDisable(slice.internal() || !LifecycleCommand.ACTIVATE.isAllowedFrom(state)
					&& !LifecycleCommand.PASSIVATE.isAllowedFrom(state));
			this.pads.setVisible(composite);
			this.pads.setManaged(composite);
			this.restart.setDisable(slice.internal() || !LifecycleCommand.RESTART.isAllowedFrom(state));
			this.commands.setVisible(composite);
			this.commands.setManaged(composite);
			for (Map.Entry<LifecycleCommand, Button> entry : this.commandButtons.entrySet()) {
				entry.getValue().setDisable(slice.internal() || !entry.getKey().isAllowedFrom(state));
			}

			int lit = slice.messagesPerSecond() <= 0 ? 0
					: (int) Math.ceil(SEGMENTS * Math.log1p(slice.messagesPerSecond()) / Math.log1p(busiest));
			for (int segment = 0; segment < SEGMENTS; segment++) {
				Node bar = this.vu.getChildren().get(segment);
				bar.getStyleClass().remove("sv-console-vu-lit");
				if (segment < lit) {
					bar.getStyleClass().add("sv-console-vu-lit");
				}
			}

			List<Class<?>> order = new ArrayList<>();
			for (CockpitSnapshot.Channel channel : slice.channels()) {
				order.add(channel.componentType());
				Row row = this.rows.get(channel.componentType());
				if (row == null || row.isComposite() != channel.composite()) {
					row = channel.composite() ? new CompositeRow() : new ChannelRow();
					this.rows.put(channel.componentType(), row);
					this.rowOrder = List.of();
				}
				row.update(channel);
			}
			this.rows.keySet().retainAll(order);
			if (!order.equals(this.rowOrder)) {
				this.rowOrder = order;
				List<Node> nodes = new ArrayList<>();
				for (Class<?> componentType : order) {
					nodes.add(this.rows.get(componentType).root());
				}
				this.channels.getChildren().setAll(nodes);
			}
		}
	}

	// --- One channel ---

	/** A channel of a slice: a component, or a composite that can be entered. */
	private interface Row {

		Node root();

		boolean isComposite();

		void update(CockpitSnapshot.Channel channel);
	}

	/** A component of a slice: its LED, its switch and its transitions. */
	private final class ChannelRow implements Row {

		private final HBox root;
		private final Region led = new Region();
		private final Label name = new Label();
		/** Installed once and updated in place, as the slice title's. */
		private final Tooltip nameTip = Tooltips.install(this.name, "");
		private final ToggleButton toggle = new ToggleButton();
		private final MenuButton more = new MenuButton("⋯");
		private final Map<LifecycleCommand, MenuItem> items = new HashMap<>();
		private CockpitSnapshot.Channel channel;

		ChannelRow() {
			this.led.getStyleClass().add("sv-console-led");
			this.name.getStyleClass().add("sv-console-channel-name");
			this.name.setMaxWidth(Double.MAX_VALUE);
			HBox.setHgrow(this.name, Priority.ALWAYS);
			this.toggle.getStyleClass().addAll("sv-switch", "sv-switch-small", "sv-console-switch");
			this.toggle.setOnAction(event -> {
				boolean wanted = this.toggle.isSelected();
				this.toggle.setSelected(this.channel.state() == ComponentState.activated);
				request(wanted ? LifecycleCommand.ACTIVATE : LifecycleCommand.PASSIVATE,
						this.channel.componentType(), this.channel.dependents());
			});
			this.more.getStyleClass().add("sv-console-more");
			this.more.setMinWidth(Region.USE_PREF_SIZE);
			for (LifecycleCommand command : LifecycleCommand.values()) {
				MenuItem item = new MenuItem(CockpitTexts.command(command));
				item.setOnAction(event -> request(command, this.channel.componentType(), this.channel.dependents()));
				this.items.put(command, item);
				this.more.getItems().add(item);
			}
			this.root = new HBox(8, this.led, this.name, this.toggle, this.more);
			this.root.setAlignment(Pos.CENTER_LEFT);
		}

		@Override
		public Node root() {
			return this.root;
		}

		@Override
		public boolean isComposite() {
			return false;
		}

		@Override
		public void update(final CockpitSnapshot.Channel channel) {
			boolean changed = this.channel == null || this.channel.state() != channel.state()
					|| this.channel.raw() != channel.raw() || this.channel.internal() != channel.internal()
					|| !Objects.equals(this.channel.dependents(), channel.dependents());
			this.channel = channel;
			if (!changed) {
				return;
			}
			this.name.setText(channel.componentType().getSimpleName());
			setStateClass(this.led, "sv-console-led-", channel.state());
			StringBuilder tip = new StringBuilder(channel.componentType().getName());
			if (channel.internal()) {
				tip.append('\n').append(INTERNAL_NOTE);
			} else if (channel.raw()) {
				tip.append('\n').append(CockpitTexts.get("channel.raw"));
			} else {
				tip.append('\n').append(stateTip(channel.state()));
			}
			long users = channel.dependents().stream().map(CockpitSnapshot.Dependent::componentType).distinct().count();
			if (users > 0) {
				tip.append('\n').append(CockpitTexts.count("channel.usedBy", users));
			}
			this.nameTip.setText(tip.toString());
			this.toggle.setSelected(channel.state() == ComponentState.activated);
			this.toggle.setDisable(!this.allows(LifecycleCommand.ACTIVATE) && !this.allows(LifecycleCommand.PASSIVATE));
			for (Map.Entry<LifecycleCommand, MenuItem> entry : this.items.entrySet()) {
				entry.getValue().setDisable(!this.allows(entry.getKey()));
			}
		}

		private boolean allows(final LifecycleCommand command) {
			if (this.channel.internal()) {
				return false;
			}
			return this.channel.raw() ? command.isAllowedForRawFrom(this.channel.state())
					: command.isAllowedFrom(this.channel.state());
		}
	}

	/**
	 * A composite of a slice: one dot per component it contains, its switch, which applies to all
	 * of them, and the way in.
	 */
	private final class CompositeRow implements Row {

		private final HBox root;
		private final Label name = new Label();
		private final Tooltip nameTip = Tooltips.install(this.name, "");
		private final HBox dots = new HBox(3);
		private final Label count = new Label();
		private final ToggleButton toggle = new ToggleButton();
		private final Button go = new Button("›");
		private CockpitSnapshot.Channel channel;

		CompositeRow() {
			SVGPath icon = new SVGPath();
			icon.setContent(ICON_COMPOSITE);
			icon.getStyleClass().add("sv-console-composite-icon");
			icon.setScaleX(0.7);
			icon.setScaleY(0.7);
			StackPane iconBox = new StackPane(icon);
			iconBox.setMinSize(16, 16);
			iconBox.setPrefSize(16, 16);
			iconBox.setMaxSize(16, 16);
			this.name.getStyleClass().add("sv-console-composite-name");
			this.name.setMaxWidth(Double.MAX_VALUE);
			this.count.getStyleClass().add("sv-console-composite-count");
			this.count.setMinWidth(Region.USE_PREF_SIZE);
			this.dots.setAlignment(Pos.CENTER_LEFT);
			HBox summary = new HBox(4, this.dots, this.count);
			summary.setAlignment(Pos.CENTER_LEFT);
			VBox text = new VBox(3, this.name, summary);
			text.setMinWidth(0);
			HBox.setHgrow(text, Priority.ALWAYS);
			this.toggle.getStyleClass().addAll("sv-switch", "sv-switch-small", "sv-console-switch");
			this.toggle.setOnAction(event -> {
				boolean wanted = this.toggle.isSelected();
				this.toggle.setSelected(this.channel.state() == ComponentState.activated);
				request(wanted ? LifecycleCommand.ACTIVATE : LifecycleCommand.PASSIVATE,
						this.channel.componentType(), this.channel.dependents());
			});
			this.go.getStyleClass().add("sv-console-go");
			this.go.setMinWidth(Region.USE_PREF_SIZE);
			this.go.setOnAction(event -> this.enter());
			this.root = new HBox(9, iconBox, text, this.toggle, this.go);
			this.root.setAlignment(Pos.CENTER_LEFT);
			this.root.getStyleClass().add("sv-console-composite");
			this.root.setOnMouseClicked(event -> {
				if (!onControl(event.getTarget(), this.root)) {
					this.enter();
				}
				event.consume();
			});
		}

		private void enter() {
			actions.executeAction(CockpitActionKey.EnterComposite, this.channel.componentType());
		}

		@Override
		public Node root() {
			return this.root;
		}

		@Override
		public boolean isComposite() {
			return true;
		}

		@Override
		public void update(final CockpitSnapshot.Channel channel) {
			boolean changed = this.channel == null || this.channel.state() != channel.state()
					|| this.channel.internal() != channel.internal() || this.channel.componentCount() != channel.componentCount()
					|| !this.channel.componentStates().equals(channel.componentStates())
					|| !Objects.equals(this.channel.dependents(), channel.dependents());
			this.channel = channel;
			if (!changed) {
				return;
			}
			this.name.setText(channel.componentType().getSimpleName());
			List<Node> nodes = new ArrayList<>();
			for (ComponentState state : channel.componentStates()) {
				if (nodes.size() == DOTS) {
					break;
				}
				Region dot = new Region();
				dot.getStyleClass().add("sv-console-dot");
				setStateClass(dot, "sv-console-led-", state);
				nodes.add(dot);
			}
			this.dots.getChildren().setAll(nodes);
			int components = channel.componentCount();
			this.count.setText(CockpitTexts.count("channel.components", components));
			StringBuilder tip = new StringBuilder(channel.componentType().getName());
			tip.append('\n').append(channel.internal() ? INTERNAL_NOTE : stateTip(channel.state()));
			long users = channel.dependents().stream().map(CockpitSnapshot.Dependent::componentType).distinct().count();
			if (users > 0) {
				tip.append('\n').append(CockpitTexts.count("channel.usedBy", users));
			}
			this.nameTip.setText(tip.toString());
			this.toggle.setSelected(channel.state() == ComponentState.activated);
			this.toggle.setDisable(channel.internal() || !LifecycleCommand.ACTIVATE.isAllowedFrom(channel.state())
					&& !LifecycleCommand.PASSIVATE.isAllowedFrom(channel.state()));
		}
	}

	/** One line of the container's log: its time and its text, whole in a tooltip. */
	private static final class LogLine {

		final HBox node;
		private final Label time = new Label();
		private final Label text = new Label();
		private final Tooltip tip = Tooltips.install(this.text, "");

		LogLine() {
			this.time.getStyleClass().add("sv-console-log-time");
			this.time.setMinWidth(Region.USE_PREF_SIZE);
			this.text.getStyleClass().add("sv-console-log-text");
			this.node = new HBox(8, this.time, this.text);
		}

		void show(final String time, final String text, final boolean failed) {
			this.time.setText(time);
			this.time.setVisible(!time.isEmpty());
			this.time.setManaged(!time.isEmpty());
			this.text.setText(text);
			this.tip.setText(text);
			this.text.setTooltip(text.isBlank() ? null : this.tip);
			this.text.getStyleClass().removeAll("sv-console-log-text", "sv-console-log-bad");
			this.text.getStyleClass().add(failed ? "sv-console-log-bad" : "sv-console-log-text");
		}
	}
}
