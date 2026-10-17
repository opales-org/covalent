/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.view.impl;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import org.opales.covalent.supervision.componentdiagram.model.ComponentActivity;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramEdge;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramExternalPort;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramTopology;
import org.opales.covalent.supervision.componentdiagram.model.ComponentPortKind;
import org.opales.covalent.supervision.componentdiagram.model.ComponentPortRole;
import org.opales.covalent.supervision.componentdiagram.model.ComponentStatus;
import org.opales.covalent.supervision.componentview.internal.Tooltips;

import javafx.animation.PauseTransition;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

/**
 * The component diagram's side panel: what the selected component is, how busy it has been
 * over the last minute, the lifecycle states it went through while observed, its ports with
 * their call rates, and the actions on it.
 */
final class ComponentInspector {

	/** What the panel's buttons do, on the current selection. */
	interface Actions {

		void showDependencies();

		void hide();

		/** @param componentTypes the components selected, to record */
		void record(Set<Class<?>> componentTypes);
	}

	/** How many activity samples the sparkline shows: one per second. */
	private static final int HISTORY = 60;

	private static final double SPARKLINE_WIDTH = 296;
	private static final double SPARKLINE_HEIGHT = 46;

	/** The copy button's icon: a sheet in front of another, drawn on a 24 grid. */
	private static final String COPY_ICON = "M9 9h11v12H9zM5 15V3h11";

	private static final double COPY_ICON_SIZE = 15;
	private static final Duration COPIED_TIP_DURATION = Duration.millis(1500);

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
			.withZone(ZoneId.systemDefault());

	/** A lifecycle state as observed, and when. */
	private record Transition(String state, Color color, long timeMillis, boolean firstSeen) {
	}

	private final Actions actions;
	private final Function<ComponentStatus, Color> statusColor;
	private final VBox content = new VBox(18);
	private final BorderPane graphics = new BorderPane();
	private final Button showDependencies = new Button(ComponentDiagramMessages.get("button.showDeps"));
	private final Button hide = new Button(ComponentDiagramMessages.get("button.hide"));
	private final Button record = new Button(ComponentDiagramMessages.get("inspector.record.one"));

	private ComponentDiagramTopology topology;
	private final Set<Class<?>> selection = new LinkedHashSet<>();
	private final Map<Class<?>, ComponentStatus> statuses = new HashMap<>();
	private final Map<Class<?>, List<Transition>> lifecycles = new HashMap<>();
	private final Map<Class<?>, Deque<Double>> histories = new HashMap<>();
	private Map<Class<?>, ComponentActivity> latest = Map.of();

	/** The component the panel describes, {@code null} when none or several are selected. */
	private Class<?> shownType;

	/** The figures updated in place at each sample. */
	private final Label rateValue = new Label();
	private final Polyline sparkLine = new Polyline();
	private final Polygon sparkArea = new Polygon();
	private Tooltip sparkTooltip;
	private final VBox portsList = new VBox(8);
	private List<String> shownPortKeys = List.of();
	private final Map<String, Label> portDetails = new HashMap<>();

	/** Whether nothing is selected: the record button has nothing to record. */
	private final BooleanProperty noSelection = new SimpleBooleanProperty(true);

	/**
	 * @param actions       what the buttons do
	 * @param statusColor   the color of a lifecycle state
	 * @param recordBlocked whether no recording can start now, one running: grays the record button
	 */
	ComponentInspector(final Actions actions, final Function<ComponentStatus, Color> statusColor,
			final ObservableBooleanValue recordBlocked) {
		this.actions = actions;
		this.statusColor = statusColor;
		this.content.getStyleClass().add("sv-inspector");
		ScrollPane scroll = new ScrollPane(this.content);
		scroll.setFitToWidth(true);
		scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		scroll.getStyleClass().add("sv-inspector-scroll");

		this.showDependencies.setOnAction(event -> this.actions.showDependencies());
		this.hide.setOnAction(event -> this.actions.hide());
		this.record.setOnAction(event -> this.actions.record(Set.copyOf(this.selection)));
		this.record.disableProperty().bind(this.noSelection.or(recordBlocked));
		this.record.getStyleClass().add("sv-primary-button");
		for (Button button : List.of(this.showDependencies, this.hide, this.record)) {
			button.setMaxWidth(Double.MAX_VALUE);
		}
		HBox.setHgrow(this.showDependencies, Priority.ALWAYS);
		HBox.setHgrow(this.hide, Priority.ALWAYS);
		VBox buttons = new VBox(8, new HBox(8, this.showDependencies, this.hide), this.record);
		buttons.getStyleClass().add("sv-inspector-actions");

		this.graphics.setCenter(scroll);
		this.graphics.setBottom(buttons);
		this.graphics.getStyleClass().add("sv-inspector-pane");
		this.graphics.setPrefWidth(340);
		this.refresh();
	}

	Region getGraphics() {
		return this.graphics;
	}

	void setTopology(final ComponentDiagramTopology topology) {
		this.topology = topology;
		// A composite's rate depends on which of its ports are public at the shown level.
		this.histories.clear();
		this.refresh();
	}

	void setSelection(final Set<Class<?>> selection) {
		this.selection.clear();
		this.selection.addAll(selection);
		this.refresh();
	}

	/** Records each component whose state changed, with the time the change was seen. */
	void setStatuses(final Map<Class<?>, ComponentStatus> statuses) {
		long now = System.currentTimeMillis();
		for (Map.Entry<Class<?>, ComponentStatus> entry : statuses.entrySet()) {
			ComponentStatus previous = this.statuses.get(entry.getKey());
			if (previous != entry.getValue()) {
				List<Transition> lifecycle = this.lifecycles.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>());
				lifecycle.add(new Transition(ComponentDiagramMessages.status(entry.getValue()),
						this.statusColor.apply(entry.getValue()), now, previous == null));
			}
		}
		ComponentStatus shownBefore = this.shownType == null ? null : this.statuses.get(this.shownType);
		this.statuses.clear();
		this.statuses.putAll(statuses);
		// Rebuilt only when what it shows changed, not at every other component's change.
		if (this.shownType == null || shownBefore != statuses.get(this.shownType)) {
			this.refresh();
		}
	}

	/**
	 * Adds a sample to every component's history; a component absent from it saw no call. The
	 * panel's figures are updated in place, so whatever is under the pointer stays there.
	 */
	void addActivitySample(final Map<Class<?>, ComponentActivity> activities) {
		this.latest = activities;
		Set<Class<?>> known = new LinkedHashSet<>(this.histories.keySet());
		known.addAll(activities.keySet());
		if (this.topology != null) {
			known.addAll(this.topology.getComponentTypes());
		}
		for (Class<?> componentType : known) {
			Deque<Double> history = this.histories.computeIfAbsent(componentType, ignored -> new ArrayDeque<>());
			history.addLast(this.messagesPerSecond(componentType));
			while (history.size() > HISTORY) {
				history.removeFirst();
			}
		}
		if (this.shownType != null) {
			this.updateActivity(this.shownType);
			this.updatePorts(this.shownType);
		}
	}

	private void refresh() {
		boolean single = this.selection.size() == 1;
		this.shownType = single ? this.selection.iterator().next() : null;
		this.showDependencies.setDisable(this.selection.isEmpty());
		this.hide.setDisable(this.selection.isEmpty());
		this.noSelection.set(this.selection.isEmpty());
		this.record.setText(ComponentDiagramMessages
				.get(this.selection.size() > 1 ? "inspector.record.many" : "inspector.record.one"));
		if (this.selection.isEmpty()) {
			Label hint = new Label(ComponentDiagramMessages.get("inspector.noSelection"));
			hint.setWrapText(true);
			hint.getStyleClass().add("sv-inspector-hint");
			this.content.getChildren().setAll(eyebrow(ComponentDiagramMessages.get("inspector.selected")), hint);
			return;
		}
		if (!single) {
			VBox names = new VBox(6);
			for (Class<?> componentType : this.selection) {
				Label name = new Label(componentType.getSimpleName());
				name.getStyleClass().add("sv-inspector-list-name");
				names.getChildren().add(name);
			}
			this.content.getChildren().setAll(
					eyebrow(ComponentDiagramMessages.get("inspector.selectedCount", this.selection.size())), names);
			return;
		}
		Class<?> componentType = this.shownType;
		this.content.getChildren().setAll(this.identity(componentType), this.activity(componentType),
				this.lifecycle(componentType), this.ports(componentType));
		this.updateActivity(componentType);
		this.updatePorts(componentType);
	}

	private Node identity(final Class<?> componentType) {
		Label name = new Label(componentType.getSimpleName());
		name.getStyleClass().add("sv-inspector-name");
		name.setWrapText(true);
		VBox qualifiedNames = new VBox(4, nameRow("inspector.type", componentType));
		Class<?> implementation = this.topology == null ? null : this.topology.getImplementation(componentType);
		if (implementation != null) {
			qualifiedNames.getChildren().add(nameRow("inspector.implementation", implementation));
		}
		boolean composite = this.topology != null && this.topology.getCompositeTypes().contains(componentType);
		boolean raw = this.topology != null && this.topology.getRawTypes().contains(componentType);
		String nature = composite ? "inspector.composite" : raw ? "inspector.raw" : "inspector.managed";
		HBox chips = new HBox(6, chip(ComponentDiagramMessages.get(nature), null));
		Tooltips.install(chips.getChildren().get(0), ComponentDiagramMessages.get(nature + ".tip"));
		ComponentStatus status = this.statuses.get(componentType);
		if (status != null) {
			chips.getChildren().add(chip(ComponentDiagramMessages.status(status), this.statusColor.apply(status)));
		}
		return new VBox(6, eyebrow(ComponentDiagramMessages.get("inspector.selected")), name, qualifiedNames, chips);
	}

	/**
	 * A class of the selected component - its type or its implementation - by its full name, which a
	 * button copies, to be pasted for instance in the exclusions.
	 */
	private static Node nameRow(final String kindKey, final Class<?> aClass) {
		Label kind = new Label(ComponentDiagramMessages.get(kindKey));
		kind.getStyleClass().add("sv-inspector-kind");
		Label qualifiedName = new Label(aClass.getName());
		qualifiedName.getStyleClass().add("sv-inspector-package");
		qualifiedName.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
		qualifiedName.setMinWidth(0);
		HBox.setHgrow(qualifiedName, Priority.ALWAYS);
		qualifiedName.setMaxWidth(Double.MAX_VALUE);
		Tooltips.install(qualifiedName, aClass.getName());
		SVGPath icon = new SVGPath();
		icon.setContent(COPY_ICON);
		icon.getStyleClass().add("sv-tool-icon");
		icon.setScaleX(COPY_ICON_SIZE / 24);
		icon.setScaleY(COPY_ICON_SIZE / 24);
		Button copy = new Button(null, new Group(icon));
		copy.getStyleClass().addAll("sv-icon-button", "sv-copy-button");
		String copyTip = ComponentDiagramMessages.get("inspector.copy");
		Tooltip tooltip = Tooltips.install(copy, copyTip);
		PauseTransition confirmation = new PauseTransition(COPIED_TIP_DURATION);
		confirmation.setOnFinished(event -> {
			tooltip.hide();
			tooltip.setText(copyTip);
		});
		copy.setOnAction(event -> {
			ClipboardContent content = new ClipboardContent();
			content.putString(aClass.getName());
			Clipboard.getSystemClipboard().setContent(content);
			tooltip.setText(ComponentDiagramMessages.get("inspector.copied"));
			// A click usually comes before the tooltip's own delay: show the confirmation now.
			Bounds onScreen = copy.localToScreen(copy.getBoundsInLocal());
			if (!tooltip.isShowing() && onScreen != null) {
				tooltip.show(copy, onScreen.getMinX(), onScreen.getMaxY());
			}
			confirmation.playFromStart();
		});
		copy.setOnMouseExited(event -> {
			confirmation.stop();
			tooltip.hide();
			tooltip.setText(copyTip);
		});
		HBox line = new HBox(4, qualifiedName, copy);
		line.setAlignment(Pos.CENTER_LEFT);
		return new VBox(1, kind, line);
	}

	private Node activity(final Class<?> componentType) {
		this.rateValue.getStyleClass().setAll("label", "sv-inspector-value");
		this.sparkLine.getStyleClass().setAll("sv-inspector-spark-line");
		this.sparkArea.getStyleClass().setAll("sv-inspector-spark-area");
		Rectangle frame = new Rectangle(SPARKLINE_WIDTH, SPARKLINE_HEIGHT, Color.TRANSPARENT);
		Pane sparkline = new Pane(frame, this.sparkArea, this.sparkLine);
		sparkline.setMinSize(SPARKLINE_WIDTH, SPARKLINE_HEIGHT);
		sparkline.setPrefSize(SPARKLINE_WIDTH, SPARKLINE_HEIGHT);
		sparkline.setMaxSize(SPARKLINE_WIDTH, SPARKLINE_HEIGHT);
		this.sparkTooltip = Tooltips.install(sparkline, "");
		return new VBox(8, heading(ComponentDiagramMessages.get("inspector.activity"), this.rateValue), sparkline);
	}

	/**
	 * A component's messages per second: the calls through its ports, summed. For a
	 * composite, only its own ports count, the ones shared with the outside.
	 */
	private double messagesPerSecond(final Class<?> componentType) {
		if (!this.latest.containsKey(componentType)) {
			return 0;
		}
		double total = 0;
		for (PortRow row : this.portRows(componentType).values()) {
			total += row.callsPerSecond;
		}
		return total;
	}

	/** The rate and the last minute's curve of the shown component. */
	private void updateActivity(final Class<?> componentType) {
		this.rateValue.setText(
				ComponentDiagramMessages.get("inspector.rate", formatRate(this.messagesPerSecond(componentType))));
		Deque<Double> history = this.histories.getOrDefault(componentType, new ArrayDeque<>());
		double peak = 0;
		for (double sample : history) {
			peak = Math.max(peak, sample);
		}
		double max = Math.max(1, peak);
		double step = SPARKLINE_WIDTH / (HISTORY - 1);
		// The newest sample at the right edge: an incomplete minute starts further right.
		double x = SPARKLINE_WIDTH - step * (history.size() - 1);
		List<Double> line = new ArrayList<>();
		List<Double> area = new ArrayList<>(List.of(x, SPARKLINE_HEIGHT));
		for (double sample : history) {
			double y = SPARKLINE_HEIGHT - 2 - (SPARKLINE_HEIGHT - 6) * sample / max;
			line.add(x);
			line.add(y);
			area.add(x);
			area.add(y);
			x += step;
		}
		area.add(SPARKLINE_WIDTH);
		area.add(SPARKLINE_HEIGHT);
		this.sparkLine.getPoints().setAll(line);
		this.sparkArea.getPoints().setAll(history.isEmpty() ? List.of() : area);
		if (this.sparkTooltip != null) {
			this.sparkTooltip.setText(ComponentDiagramMessages.get("inspector.rate.tip", formatRate(peak)));
		}
	}

	private Node lifecycle(final Class<?> componentType) {
		VBox rows = new VBox(8);
		List<Transition> transitions = this.lifecycles.getOrDefault(componentType, List.of());
		for (int index = 0; index < transitions.size(); index++) {
			Transition transition = transitions.get(index);
			Circle dot = new Circle(5, index == transitions.size() - 1 ? transition.color() : Color.TRANSPARENT);
			dot.setStroke(transition.color());
			dot.setStrokeWidth(1.6);
			Label state = new Label(transition.state());
			state.getStyleClass().add(index == transitions.size() - 1 ? "sv-inspector-state-current" : "sv-inspector-state");
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			Label time = new Label(TIME.format(Instant.ofEpochMilli(transition.timeMillis())));
			time.getStyleClass().add("sv-inspector-time");
			if (transition.firstSeen()) {
				Tooltips.install(time, ComponentDiagramMessages.get("inspector.firstSeen.tip"));
				time.setText(time.getText() + " *");
			}
			HBox row = new HBox(10, dot, state, spacer, time);
			row.setAlignment(Pos.CENTER_LEFT);
			rows.getChildren().add(row);
		}
		if (transitions.isEmpty()) {
			Label none = new Label(ComponentDiagramMessages.get("inspector.noState"));
			none.getStyleClass().add("sv-inspector-hint");
			rows.getChildren().add(none);
		}
		return new VBox(8, eyebrow(ComponentDiagramMessages.get("inspector.lifecycle")), rows);
	}

	private Node ports(final Class<?> componentType) {
		this.portsList.getChildren().clear();
		this.shownPortKeys = List.of();
		this.portDetails.clear();
		return new VBox(8, eyebrow(ComponentDiagramMessages.get("inspector.ports")), this.portsList);
	}

	/**
	 * One row per interface and role, with the components on the other side and its call rate.
	 * Rows are rebuilt only when they change; otherwise their figures are updated in place.
	 */
	private void updatePorts(final Class<?> componentType) {
		Map<String, PortRow> rows = this.portRows(componentType);
		List<String> keys = new ArrayList<>(rows.keySet());
		if (!keys.equals(this.shownPortKeys)) {
			this.shownPortKeys = keys;
			this.portDetails.clear();
			this.portsList.getChildren().clear();
			for (Map.Entry<String, PortRow> row : rows.entrySet()) {
				this.portsList.getChildren().add(this.portCard(row.getKey(), row.getValue()));
			}
			if (rows.isEmpty()) {
				Label none = new Label(ComponentDiagramMessages.get("inspector.noPort"));
				none.getStyleClass().add("sv-inspector-hint");
				this.portsList.getChildren().add(none);
			}
			return;
		}
		for (Map.Entry<String, PortRow> row : rows.entrySet()) {
			Label detail = this.portDetails.get(row.getKey());
			if (detail != null) {
				detail.setText(detailText(row.getValue()));
			}
		}
	}

	private Map<String, PortRow> portRows(final Class<?> componentType) {
		Map<String, PortRow> rows = new TreeMap<>();
		if (this.topology != null) {
			for (ComponentDiagramEdge edge : this.topology.getEdges()) {
				if (edge.getKind() == ComponentPortKind.COMPONENT_TYPE || edge.getRelation() == null) {
					continue;
				}
				if (edge.getFrom() == componentType) {
					row(rows, edge.getRelation(), edge.getKind(), fromRole(edge.getKind())).others.add(edge.getTo());
				}
				if (edge.getTo() == componentType) {
					row(rows, edge.getRelation(), edge.getKind(), toRole(edge.getKind())).others.add(edge.getFrom());
				}
			}
			// A dependency whose other end is outside the shown level: its interface is not known here.
			for (ComponentDiagramExternalPort port : this.topology.getExternalPorts()) {
				if (port.getOwner() == componentType) {
					PortRow external = rows.computeIfAbsent("~" + port.getKind() + "|" + port.getRole(),
							ignored -> new PortRow(null, port.getKind(), port.getRole()));
					external.others.addAll(port.getTargets());
				}
			}
		}
		// A composite's rates are those of the components inside: only its own ports show them,
		// not the interfaces its components use among themselves.
		boolean composite = this.topology != null && this.topology.getCompositeTypes().contains(componentType);
		ComponentActivity current = this.latest.get(componentType);
		if (current != null) {
			for (ComponentActivity.PortRate rate : current.ports()) {
				String key = rowKey(rate.portType(), rate.role());
				if (composite && !rows.containsKey(key)) {
					continue;
				}
				row(rows, rate.portType(), rate.kind(), rate.role()).callsPerSecond = rate.callsPerSecond();
			}
		}
		return rows;
	}

	private Node portCard(final String key, final PortRow row) {
		boolean filled = row.role == ComponentPortRole.PROVIDED || row.role == ComponentPortRole.PRODUCED
				|| row.role == ComponentPortRole.OFFERED;
		Node marker = row.kind == ComponentPortKind.PARAMETER ? new Rectangle(9, 9) : new Circle(5);
		marker.getStyleClass().addAll("sv-inspector-port-marker", "sv-kind-" + suffix(row.kind),
				filled ? "sv-filled" : "sv-hollow");
		Label name = new Label(row.portType == null ? ComponentDiagramMessages.get("inspector.outside." + suffix(row.kind))
				: row.portType.getSimpleName());
		name.getStyleClass().add("sv-inspector-port-name");
		name.setMinWidth(0);
		if (row.portType != null) {
			Tooltips.install(name, row.portType.getName());
		}
		Label detail = new Label(detailText(row));
		detail.getStyleClass().add("sv-inspector-port-detail");
		this.portDetails.put(key, detail);
		detail.setWrapText(true);
		VBox text = new VBox(2, name, detail);
		HBox.setHgrow(text, Priority.ALWAYS);
		HBox card = new HBox(10, marker, text);
		card.setAlignment(Pos.CENTER_LEFT);
		card.getStyleClass().add("sv-inspector-port");
		return card;
	}

	private static String detailText(final PortRow row) {
		StringBuilder details = new StringBuilder(ComponentDiagramMessages.role(row.role));
		if (row.callsPerSecond > 0) {
			details.append(" · ").append(ComponentDiagramMessages.get("inspector.calls", formatRate(row.callsPerSecond)));
		}
		if (!row.others.isEmpty()) {
			List<String> names = new ArrayList<>();
			for (Class<?> other : row.others) {
				names.add(other.getSimpleName());
			}
			details.append(" · ")
					.append(ComponentDiagramMessages.get("inspector.others." + row.role.name(), String.join(", ", names)));
		}
		return details.toString();
	}

	private static final class PortRow {
		private final Class<?> portType;
		private final ComponentPortKind kind;
		private final ComponentPortRole role;
		private final Set<Class<?>> others = new LinkedHashSet<>();
		private double callsPerSecond;

		private PortRow(final Class<?> portType, final ComponentPortKind kind, final ComponentPortRole role) {
			this.portType = portType;
			this.kind = kind;
			this.role = role;
		}
	}

	private static PortRow row(final Map<String, PortRow> rows, final Class<?> portType, final ComponentPortKind kind,
			final ComponentPortRole role) {
		return rows.computeIfAbsent(rowKey(portType, role), ignored -> new PortRow(portType, kind, role));
	}

	private static String rowKey(final Class<?> portType, final ComponentPortRole role) {
		return portType.getSimpleName() + "|" + role;
	}

	private static ComponentPortRole fromRole(final ComponentPortKind kind) {
		switch (kind) {
			case SERVICE:
				return ComponentPortRole.USED;
			case EVENT:
				return ComponentPortRole.PRODUCED;
			default:
				return ComponentPortRole.ACCESSED;
		}
	}

	private static ComponentPortRole toRole(final ComponentPortKind kind) {
		switch (kind) {
			case SERVICE:
				return ComponentPortRole.PROVIDED;
			case EVENT:
				return ComponentPortRole.CONSUMED;
			default:
				return ComponentPortRole.OFFERED;
		}
	}

	private static String suffix(final ComponentPortKind kind) {
		switch (kind) {
			case SERVICE:
				return "service";
			case EVENT:
				return "event";
			default:
				return "parameter";
		}
	}


	private static String formatRate(final double rate) {
		return rate == 0 ? "0" : rate < 10 ? String.format(Locale.ROOT, "%.1f", rate)
				: String.format(Locale.ROOT, "%.0f", rate);
	}

	private static Label eyebrow(final String text) {
		Label label = new Label(text.toUpperCase(Locale.ROOT));
		label.getStyleClass().add("sv-eyebrow");
		return label;
	}

	private static Node heading(final String title, final Node right) {
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox heading = new HBox(eyebrow(title), spacer, right);
		heading.setAlignment(Pos.CENTER_LEFT);
		return heading;
	}

	private static HBox chip(final String text, final Color dotColor) {
		Label label = new Label(text);
		HBox chip = new HBox(6, label);
		if (dotColor != null) {
			chip.getChildren().add(0, new Circle(3.5, dotColor));
		}
		chip.setAlignment(Pos.CENTER_LEFT);
		chip.getStyleClass().add("sv-chip");
		return chip;
	}
}
