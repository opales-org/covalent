/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.cockpit.view.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.hmi.action.ActionConsumerServices;
import org.opales.covalent.supervision.cockpit.interaction.CockpitViewKey;
import org.opales.covalent.supervision.cockpit.model.CockpitEvents;
import org.opales.covalent.supervision.cockpit.model.CockpitModelReadServices;
import org.opales.covalent.supervision.cockpit.model.CockpitSnapshot;
import org.opales.covalent.supervision.cockpit.view.CockpitView;
import org.opales.covalent.supervision.cockpit.view.CockpitViewServices;
import org.opales.covalent.supervision.componentdiagram.interaction.ComponentDiagramToolActionKey;
import org.opales.covalent.supervision.componentdiagram.model.ComponentPortKind;
import org.opales.covalent.supervision.componentview.internal.Tooltips;
import org.opales.covalent.supervision.supervisionhmi.view.FxThread;
import org.opales.covalent.supervision.supervisionhmi.view.PanelHostingServices;
import org.opales.covalent.supervision.supervisionhmi.view.RailEntry;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisorTabDescriptorAdapter;
import org.opales.covalent.supervision.supervisionhmi.view.ToolbarFunction;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;
import org.opales.covalent.supervision.threads.interaction.ThreadsActionKey;
import org.opales.covalent.supervision.threads.model.Contention;
import org.opales.covalent.supervision.threads.model.ThreadsEvents;
import org.opales.covalent.supervision.threads.model.ThreadsModelReadServices;
import org.opales.covalent.supervision.threads.model.ThreadsSnapshot;

import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;

/**
 * The cockpit's panel: key figures, throughput over time, lifecycle, top-level composites and
 * busiest ports. It keeps the last quarter of an hour of samples, of which the range selector
 * shows the last 15 s, minute, 5 or 15 minutes.
 */
@Implementation
public class CockpitViewImpl implements CockpitView, CockpitViewServices, CockpitEvents, ThreadsEvents {

	/** Creates the view of the cockpit function. */
	public CockpitViewImpl() {
		// Nothing to initialize.
	}

	/** Samples kept: one per second for 15 minutes. */
	private static final int HISTORY = 900;

	/** How long failures stay counted. */
	private static final long FAILURE_WINDOW_MILLIS = 5 * 60_000;

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

	/** How many ports the controller ranks. */
	private static final int HOT_PORTS = 8;

	private static final int[] RANGES = { 15, 60, 300, 900 };
	private static final String[] RANGE_LABELS = { "15 s", "1 min", "5 min", "15 min" };

	/** Style class graying the live badge while no container is observed - see {@code supervision.css}. */
	private static final String DISCONNECTED_STYLE_CLASS = "sv-disconnected";

	/** The badge telling whether the observed container is connected, and its label. */
	private HBox liveBadge;
	private Label liveLabel;

	@UsedServices
	private ActionConsumerServices actionManagerServices;

	@UsedServices
	private PanelHostingServices useHostingServices;

	@UsedServices
	private CockpitModelReadServices useCockpitModelReadServices;

	@UsedServices
	private ThreadsModelReadServices useThreadsModelReadServices;

	/** The Threads tile: what the observed worker pools are doing, opening the Thread Supervisor. */
	private VBox threadsTile;
	private final Label threadsValue = bigValue();
	private final Label threadsDetail = detail();
	private final Hyperlink threadsTracking = new Hyperlink(CockpitTexts.get("threads.trackingOff"));

	/** When the Threads tile was last updated: at most once a second. */
	private long threadsShownMillis;

	/** One sample of the throughput by kind. */
	private record Sample(long timeMillis, double services, double events, double parameters) {
	}

	private final Deque<Sample> history = new ArrayDeque<>();
	private final Deque<CockpitSnapshot.Failure> failures = new ArrayDeque<>();
	private CockpitSnapshot latest;
	private int rangeSeconds = 60;
	private ScrollPane root;
	private ConsolePanel console;

	private final Label componentsValue = bigValue();
	private final Label componentsDetail = detail();
	private final Label wiringValue = bigValue();
	private final Label wiringDetail = detail();
	private final Label servicesValue = bigValue();
	private final Label servicesDetail = detail();
	private final Label eventsValue = bigValue();
	private final Label eventsDetail = detail();
	private final Label failuresValue = bigValue();
	private final Label failuresDetail = detail();
	private final Pane servicesSpark = spark();
	private final Pane eventsSpark = spark();
	private VBox failuresTile;
	private final ThroughputChart chart = new ThroughputChart();
	private final HBox lifecycleBar = new HBox(2);
	private final GridPane lifecycleCounts = new GridPane();
	private final Label lifecycleHeadline = new Label();
	private final VBox transitionsList = new VBox(0);
	private final FlowPane compositeCards = new FlowPane(12, 12);
	private final VBox hotPortsList = new VBox(0);
	/*
	 * The overview is refreshed every second. Its nodes are built once and updated in place: a node
	 * replaced at each refresh drops the tooltip it is showing and the pointer it is under.
	 */
	private final Map<ComponentState, Region> segments = new EnumMap<>(ComponentState.class);
	private final Map<ComponentState, Tooltip> segmentTips = new EnumMap<>(ComponentState.class);
	private final Map<ComponentState, Label> stateCounts = new EnumMap<>(ComponentState.class);
	private final Map<Class<?>, CompositeCard> cardsByType = new HashMap<>();
	private List<Class<?>> cardOrder = List.of();
	private final List<PortRow> portRows = new ArrayList<>();

	// --- CockpitViewServices ---

	@Override
	public void showView(final ViewKey key) {
		Platform.runLater(() -> {
			if (this.root == null) {
				this.root = this.build();
				this.console = new ConsolePanel(this.actionManagerServices);
			}
			// The overview last, so that it is the tab shown.
			this.useHostingServices.openPanel(CockpitViewKey.INSTANCE, this.root, this.descriptor());
			this.useHostingServices.openPanel(CockpitViewKey.CONSOLE, this.console.getRoot(),
					this.consoleDescriptor());
			this.useHostingServices.openPanel(CockpitViewKey.INSTANCE, this.root, this.descriptor());
			this.render();
		});
	}

	@Override
	public void hideView(final ViewKey key) {
		FxThread.run(() -> {
			this.useHostingServices.closePanel(CockpitViewKey.CONSOLE);
			if (key != CockpitViewKey.CONSOLE) {
				this.useHostingServices.closePanel(key);
			}
		});
	}

	private SupervisorTabDescriptorAdapter consoleDescriptor() {
		return new SupervisorTabDescriptorAdapter() {
			@Override
			public RailEntry railEntry() {
				return RailEntry.COCKPIT;
			}

			@Override
			public String title() {
				return CockpitTexts.get("tab.console");
			}

			@Override
			public EnumSet<ToolbarFunction> supportedToolbarFunctions() {
				return EnumSet.noneOf(ToolbarFunction.class);
			}

			@Override
			public boolean closable() {
				return false;
			}
		};
	}

	private SupervisorTabDescriptorAdapter descriptor() {
		return new SupervisorTabDescriptorAdapter() {
			@Override
			public RailEntry railEntry() {
				return RailEntry.COCKPIT;
			}

			@Override
			public String title() {
				return CockpitTexts.get("tab.overview");
			}

			@Override
			public EnumSet<ToolbarFunction> supportedToolbarFunctions() {
				return EnumSet.noneOf(ToolbarFunction.class);
			}

			@Override
			public boolean closable() {
				return false;
			}
		};
	}

	// --- CockpitEvents ---

	@Override
	public void onConnectionChanged() {
		Platform.runLater(this::showConnection);
	}

	/** Shows on the live badge whether a container is observed, as the model tells now. */
	private void showConnection() {
		if (this.liveBadge == null) {
			return;
		}
		boolean connected = this.useCockpitModelReadServices.isConnected();
		this.liveLabel.setText(CockpitTexts.get(connected ? "live" : "disconnected"));
		if (connected) {
			this.liveBadge.getStyleClass().remove(DISCONNECTED_STYLE_CLASS);
		} else if (!this.liveBadge.getStyleClass().contains(DISCONNECTED_STYLE_CLASS)) {
			this.liveBadge.getStyleClass().add(DISCONNECTED_STYLE_CLASS);
		}
	}

	@Override
	public void onSnapshotChanged() {
		Platform.runLater(() -> {
			// The latest snapshot, whatever order the notifications arrive in: one already
			// shown, or older than it, is not shown again.
			CockpitSnapshot snapshot = this.useCockpitModelReadServices.getSnapshot();
			if (snapshot == null || snapshot == this.latest
					|| (this.latest != null && snapshot.timeMillis() < this.latest.timeMillis())) {
				return;
			}
			this.latest = snapshot;
			this.history.addLast(new Sample(snapshot.timeMillis(), snapshot.servicesPerSecond(),
					snapshot.eventsPerSecond(), snapshot.parametersPerSecond()));
			while (this.history.size() > HISTORY) {
				this.history.removeFirst();
			}
			for (CockpitSnapshot.Failure failure : snapshot.newFailures()) {
				this.failures.addFirst(failure);
			}
			while (!this.failures.isEmpty()
					&& this.failures.getLast().timeMillis() < snapshot.timeMillis() - FAILURE_WINDOW_MILLIS) {
				this.failures.removeLast();
			}
			if (this.root != null && this.root.getScene() != null) {
				this.render();
			}
			if (this.console != null && this.console.getRoot().getScene() != null) {
				this.console.update(snapshot);
			}
		});
	}

	// --- ThreadsEvents: the Threads tile ---

	@Override
	public void onThreadsSampled() {
		Platform.runLater(() -> {
			long now = System.currentTimeMillis();
			if (this.threadsTile != null && now - this.threadsShownMillis >= 1000) {
				this.threadsShownMillis = now;
				this.renderThreads();
			}
		});
	}

	@Override
	public void onStackRead(final long threadId) {
	}

	/** The Threads tile: occupancy over the last minute while tasks are tracked, the threads otherwise. */
	private void renderThreads() {
		ThreadsSnapshot snapshot = this.useThreadsModelReadServices.getSnapshot();
		int threads = 0;
		int saturated = 0;
		long rejected = 0;
		for (ThreadsSnapshot.Pool pool : snapshot.pools()) {
			threads += pool.workers().size();
			saturated += pool.contention() == Contention.SATURATED ? 1 : 0;
			rejected += pool.rejectedTasks();
		}
		boolean tracking = snapshot.connected() && snapshot.taskTracking();
		this.threadsValue.setText(!snapshot.connected() ? "–"
				: tracking ? CockpitTexts.get("threads.busy", Math.round(snapshot.minuteOccupancy() * 100))
						: CockpitTexts.count("threads.threads", threads));
		List<String> parts = new ArrayList<>();
		if (tracking) {
			parts.add(CockpitTexts.count("threads.threads", threads));
		}
		parts.add(CockpitTexts.count("threads.pools", snapshot.pools().size()));
		if (saturated > 0) {
			parts.add(CockpitTexts.count("threads.saturated", saturated));
		}
		if (rejected > 0) {
			parts.add(CockpitTexts.count("threads.rejected", rejected));
		}
		if (!snapshot.deadlocks().isEmpty()) {
			parts.add(CockpitTexts.get("threads.deadlock"));
		}
		this.threadsDetail.setText(snapshot.connected() ? String.join(" · ", parts)
				: CockpitTexts.get("threads.noContainer"));
		this.threadsTracking.setVisible(snapshot.connected() && !tracking);
		this.threadsTracking.setManaged(this.threadsTracking.isVisible());
		this.threadsTile.getStyleClass().remove("sv-cockpit-tile-bad");
		if (saturated > 0 || !snapshot.deadlocks().isEmpty()) {
			this.threadsTile.getStyleClass().add("sv-cockpit-tile-bad");
		}
	}

	// --- Building ---

	private ScrollPane build() {
		Label title = new Label(CockpitTexts.get("title"));
		title.getStyleClass().add("sv-cockpit-title");
		this.liveLabel = new Label(CockpitTexts.get("live"));
		HBox live = new HBox(6, new Circle(4), this.liveLabel);
		live.getStyleClass().add("sv-cockpit-live");
		this.liveBadge = live;
		this.showConnection();
		live.setAlignment(Pos.CENTER_LEFT);
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox ranges = new HBox(2);
		ranges.getStyleClass().add("sv-cockpit-ranges");
		ToggleGroup rangeGroup = new ToggleGroup();
		for (int index = 0; index < RANGES.length; index++) {
			int seconds = RANGES[index];
			ToggleButton range = new ToggleButton(RANGE_LABELS[index]);
			range.setToggleGroup(rangeGroup);
			range.setSelected(seconds == this.rangeSeconds);
			range.setOnAction(event -> {
				if (!range.isSelected()) {
					range.setSelected(true);
				}
				this.rangeSeconds = seconds;
				this.render();
			});
			ranges.getChildren().add(range);
		}
		HBox header = new HBox(12, title, live, spacer, ranges);
		header.setAlignment(Pos.CENTER_LEFT);

		this.servicesSpark.widthProperty().addListener(observable -> this.render());
		this.failuresTile = tile(CockpitTexts.get("tile.failures"), this.failuresValue, this.failuresDetail, null);
		this.threadsTracking.getStyleClass().add("sv-cockpit-threads-link");
		this.threadsTracking.setOnAction(event -> {
			this.actionManagerServices.executeAction(ThreadsActionKey.SetTaskTracking, true);
			event.consume();
		});
		this.threadsTile = tile(CockpitTexts.get("tile.threads"), this.threadsValue, this.threadsDetail, null);
		this.threadsTile.getChildren().add(this.threadsTracking);
		this.threadsTile.getStyleClass().add("sv-cockpit-tile-link");
		Tooltips.install(this.threadsTile, CockpitTexts.get("tile.threads.tip"));
		this.threadsTile.setOnMouseClicked(event -> this.actionManagerServices.executeAction(ThreadsActionKey.Show));
		GridPane tiles = new GridPane();
		tiles.setHgap(14);
		for (int column = 0; column < 6; column++) {
			ColumnConstraints constraints = new ColumnConstraints();
			constraints.setPercentWidth(100.0 / 6);
			tiles.getColumnConstraints().add(constraints);
		}
		tiles.addRow(0, tile(CockpitTexts.get("tile.components"), this.componentsValue, this.componentsDetail, null),
				tile(CockpitTexts.get("tile.wiring"), this.wiringValue, this.wiringDetail, null),
				tile(CockpitTexts.get("tile.services"), this.servicesValue, this.servicesDetail, this.servicesSpark),
				tile(CockpitTexts.get("tile.events"), this.eventsValue, this.eventsDetail, this.eventsSpark), this.failuresTile,
				this.threadsTile);

		VBox throughput = card(heading(CockpitTexts.get("throughput"), CockpitTexts.get("throughput.subtitle"), this.chart.legend()), this.chart);
		HBox.setHgrow(throughput, Priority.ALWAYS);
		this.lifecycleBar.getStyleClass().add("sv-cockpit-lifecycle-bar");
		this.lifecycleCounts.setHgap(18);
		this.lifecycleCounts.setVgap(8);
		this.lifecycleHeadline.getStyleClass().add("sv-cockpit-headline");
		Label recent = new Label(CockpitTexts.get("lifecycle.recent"));
		recent.getStyleClass().add("sv-eyebrow");
		VBox lifecycle = card(heading(CockpitTexts.get("lifecycle"), null, this.lifecycleHeadline), this.lifecycleBar,
				this.lifecycleCounts, recent, this.transitionsList);
		lifecycle.setPrefWidth(420);
		lifecycle.setMinWidth(360);
		HBox middle = new HBox(14, throughput, lifecycle);

		this.compositeCards.prefWrapLengthProperty().bind(this.compositeCards.widthProperty());
		VBox composites = card(heading(CockpitTexts.get("composites"),
				CockpitTexts.get("composites.subtitle"), null), this.compositeCards);
		VBox ports = card(heading(CockpitTexts.get("ports"), CockpitTexts.get("ports.subtitle"), null), this.hotPortsList);
		// Fixed shares of the width, whatever the names shown, so that the two cards never resize
		// each other as the figures change.
		GridPane bottom = new GridPane();
		bottom.setHgap(14);
		ColumnConstraints compositesColumn = new ColumnConstraints();
		compositesColumn.setPercentWidth(42);
		ColumnConstraints portsColumn = new ColumnConstraints();
		portsColumn.setPercentWidth(58);
		bottom.getColumnConstraints().addAll(compositesColumn, portsColumn);
		composites.setMinWidth(0);
		ports.setMinWidth(0);
		composites.setMaxHeight(Double.MAX_VALUE);
		ports.setMaxHeight(Double.MAX_VALUE);
		bottom.addRow(0, composites, ports);

		VBox content = new VBox(16, header, tiles, middle, bottom);
		content.getStyleClass().add("sv-cockpit");
		ScrollPane scroll = new ScrollPane(content);
		scroll.setFitToWidth(true);
		scroll.getStyleClass().add("sv-cockpit-scroll");
		return scroll;
	}

	// --- Rendering ---

	private void render() {
		CockpitSnapshot snapshot = this.latest;
		if (snapshot == null || this.root == null) {
			return;
		}
		List<Sample> window = this.window(this.rangeSeconds);
		this.componentsValue.setText(String.valueOf(snapshot.managedCount() + snapshot.rawCount()));
		this.componentsDetail.setText(CockpitTexts.get("components.detail", snapshot.managedCount(),
				snapshot.rawCount(), snapshot.compositeCount()));
		int links = snapshot.serviceLinks() + snapshot.eventLinks() + snapshot.parameterLinks();
		this.wiringValue.setText(CockpitTexts.count("wiring.links", links));
		this.wiringDetail.setText(CockpitTexts.get("wiring.detail", snapshot.serviceLinks(), snapshot.eventLinks(),
				snapshot.parameterLinks()));
		this.servicesValue.setText(rate(snapshot.servicesPerSecond()) + " /s");
		this.servicesDetail.setText(this.trend(Sample::services));
		drawSpark(this.servicesSpark, window, Sample::services, "sv-cockpit-spark-service");
		this.eventsValue.setText(rate(snapshot.eventsPerSecond()) + " /s");
		this.eventsDetail.setText(this.trend(Sample::events));
		drawSpark(this.eventsSpark, window, Sample::events, "sv-cockpit-spark-event");
		int failed = 0;
		for (CockpitSnapshot.Failure failure : this.failures) {
			failed += failure.count();
		}
		this.failuresValue.setText(CockpitTexts.get("failures.value", failed));
		this.failuresDetail.setText(this.failures.isEmpty() ? CockpitTexts.get("failures.none")
				: describe(this.failures.getFirst()));
		this.failuresTile.getStyleClass().remove("sv-cockpit-tile-bad");
		if (failed > 0) {
			this.failuresTile.getStyleClass().add("sv-cockpit-tile-bad");
		}
		this.chart.draw(window, this.rangeSeconds);
		this.renderLifecycle(snapshot);
		this.renderComposites(snapshot);
		this.renderPorts(snapshot);
		this.renderThreads();
	}

	/** The change against the minute before the last one, or "steady". */
	private String trend(final ToDoubleFunction<Sample> value) {
		List<Sample> last = this.window(60);
		List<Sample> all = this.window(120);
		if (all.size() < 120) {
			return CockpitTexts.get("trend.measuring");
		}
		double recent = last.stream().mapToDouble(value).average().orElse(0);
		double before = all.subList(0, 60).stream().mapToDouble(value).average().orElse(0);
		if (before < 0.5) {
			return CockpitTexts.get(recent < 0.5 ? "trend.steady" : "trend.rising");
		}
		double change = (recent - before) / before * 100;
		if (Math.abs(change) < 5) {
			return CockpitTexts.get("trend.steadyVs");
		}
		return CockpitTexts.get(change > 0 ? "trend.up" : "trend.down", Math.round(Math.abs(change)));
	}

	private void renderLifecycle(final CockpitSnapshot snapshot) {
		Map<ComponentState, Integer> states = snapshot.states();
		int total = states.values().stream().mapToInt(Integer::intValue).sum();
		this.lifecycleHeadline.setText(CockpitTexts.get("lifecycle.headline",
				states.getOrDefault(ComponentState.activated, 0), total));
		if (this.segments.isEmpty()) {
			this.buildLifecycle();
		}
		for (ComponentState state : ComponentState.values()) {
			int count = states.getOrDefault(state, 0);
			Region segment = this.segments.get(state);
			boolean shown = count > 0 && total > 0;
			segment.setVisible(shown);
			segment.setManaged(shown);
			if (shown) {
				segment.setPrefWidth(Math.max(6, 1000.0 * count / total));
				this.segmentTips.get(state).setText(CockpitTexts.get("lifecycle.segment", count, CockpitTexts.state(state)));
			}
			this.stateCounts.get(state).setText(String.valueOf(count));
		}
		this.transitionsList.getChildren().clear();
		for (CockpitSnapshot.Transition transition : snapshot.recentTransitions()) {
			if (this.transitionsList.getChildren().size() == 5) {
				break;
			}
			Label time = new Label(TIME.format(Instant.ofEpochMilli(transition.timeMillis())));
			time.getStyleClass().add("sv-cockpit-mono-muted");
			Label component = new Label(transition.componentType().getSimpleName());
			component.getStyleClass().add("sv-cockpit-strong");
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			Label from = new Label(transition.from() == null ? "?" : CockpitTexts.state(transition.from()));
			from.getStyleClass().add("sv-cockpit-mono-muted");
			Label to = new Label(CockpitTexts.state(transition.to()));
			to.getStyleClass().addAll("sv-cockpit-mono", "sv-state-text-" + transition.to().name());
			HBox line = new HBox(12, time, component, spacer, from, new Label("→"), to);
			line.setAlignment(Pos.CENTER_LEFT);
			line.getStyleClass().add("sv-cockpit-row");
			this.transitionsList.getChildren().add(line);
		}
		if (snapshot.recentTransitions().isEmpty()) {
			Label none = new Label(CockpitTexts.get("lifecycle.noTransition"));
			none.getStyleClass().add("sv-cockpit-muted");
			this.transitionsList.getChildren().add(none);
		}
	}

	/** One bar segment and one count line per state, shown or hidden as the counts change. */
	private void buildLifecycle() {
		int row = 0;
		int column = 0;
		for (ComponentState state : ComponentState.values()) {
			Region segment = new Region();
			segment.getStyleClass().addAll("sv-cockpit-segment", "sv-state-" + state.name());
			HBox.setHgrow(segment, Priority.ALWAYS);
			this.segmentTips.put(state, Tooltips.install(segment, CockpitTexts.state(state)));
			this.segments.put(state, segment);
			this.lifecycleBar.getChildren().add(segment);
			Region dot = new Region();
			dot.getStyleClass().addAll("sv-cockpit-state-dot", "sv-state-" + state.name());
			Label name = new Label(CockpitTexts.state(state));
			Label value = new Label();
			value.getStyleClass().add("sv-cockpit-mono");
			this.stateCounts.put(state, value);
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			HBox line = new HBox(8, dot, name, spacer, value);
			line.setAlignment(Pos.CENTER_LEFT);
			line.setMinWidth(150);
			this.lifecycleCounts.add(line, column, row);
			column++;
			if (column == 2) {
				column = 0;
				row++;
			}
		}
	}

	private void renderComposites(final CockpitSnapshot snapshot) {
		double busiest = 1;
		for (CockpitSnapshot.CompositeSummary composite : snapshot.composites()) {
			busiest = Math.max(busiest, composite.messagesPerSecond());
		}
		List<Class<?>> order = new ArrayList<>();
		for (CockpitSnapshot.CompositeSummary composite : snapshot.composites()) {
			order.add(composite.compositeType());
			this.cardsByType.computeIfAbsent(composite.compositeType(), CompositeCard::new).update(composite, busiest);
		}
		this.cardsByType.keySet().retainAll(order);
		if (!order.equals(this.cardOrder)) {
			this.cardOrder = order;
			List<Node> cards = new ArrayList<>();
			for (Class<?> compositeType : order) {
				cards.add(this.cardsByType.get(compositeType).root);
			}
			this.compositeCards.getChildren().setAll(cards);
		}
	}

	/** A top-level composite's card: built once, then updated in place. */
	private final class CompositeCard {

		private final VBox root;
		private final Label count = new Label();
		private final FlowPane dots = new FlowPane(4, 4);
		private final Region fill = new Region();
		private final Label rate = new Label();
		private final Label status = new Label();
		private List<ComponentState> shownStates = List.of();

		CompositeCard(final Class<?> compositeType) {
			Label name = new Label(compositeType.getSimpleName());
			name.getStyleClass().add("sv-cockpit-strong");
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			this.count.getStyleClass().add("sv-cockpit-mono-muted");
			StackPane bar = new StackPane();
			bar.getStyleClass().add("sv-cockpit-bar-track");
			this.fill.getStyleClass().add("sv-cockpit-bar-fill");
			StackPane.setAlignment(this.fill, Pos.CENTER_LEFT);
			bar.getChildren().add(this.fill);
			this.rate.getStyleClass().add("sv-cockpit-mono");
			Region footerSpacer = new Region();
			HBox.setHgrow(footerSpacer, Priority.ALWAYS);
			HBox footer = new HBox(this.rate, footerSpacer, this.status);
			footer.setAlignment(Pos.CENTER_LEFT);
			this.root = new VBox(10, new HBox(name, spacer, this.count), this.dots, bar, footer);
			this.root.getStyleClass().add("sv-cockpit-composite");
			this.root.setPrefWidth(158);
			this.root.setCursor(Cursor.HAND);
			Tooltips.install(this.root, CockpitTexts.get("composite.open.tip", compositeType.getSimpleName()));
			this.root.setOnMouseClicked(event -> CockpitViewImpl.this.actionManagerServices
					.executeAction(ComponentDiagramToolActionKey.OpenComposite, compositeType));
		}

		void update(final CockpitSnapshot.CompositeSummary composite, final double busiest) {
			List<ComponentState> states = composite.componentStates();
			this.count.setText(String.valueOf(states.size()));
			if (!states.equals(this.shownStates)) {
				this.shownStates = List.copyOf(states);
				List<Node> stateDots = new ArrayList<>();
				for (ComponentState state : states) {
					Region dot = new Region();
					dot.getStyleClass().addAll("sv-cockpit-state-dot", "sv-state-" + state.name());
					stateDots.add(dot);
				}
				this.dots.getChildren().setAll(stateDots);
			}
			long failed = states.stream().filter(state -> state == ComponentState.failed).count();
			this.fill.setMaxWidth(Math.max(2, 134 * composite.messagesPerSecond() / busiest));
			this.rate.setText(rate(composite.messagesPerSecond()) + " msg/s");
			this.status.getStyleClass().removeAll("sv-state-text-failed", "sv-cockpit-muted");
			if (failed > 0) {
				this.status.setText(CockpitTexts.get("composite.failed", failed));
				this.status.getStyleClass().add("sv-state-text-failed");
			} else if (composite.status() != null && !"ACTIVATED".equals(composite.status().getLabel())) {
				this.status.setText(CockpitTexts.get("status." + composite.status().name()));
				this.status.getStyleClass().add("sv-cockpit-muted");
			} else {
				this.status.setText("");
			}
			this.root.getStyleClass().remove("sv-cockpit-composite-bad");
			if (failed > 0) {
				this.root.getStyleClass().add("sv-cockpit-composite-bad");
			}
		}
	}

	private void renderPorts(final CockpitSnapshot snapshot) {
		if (this.portRows.isEmpty()) {
			List<Node> rows = new ArrayList<>();
			rows.add(portRow(new Label(CockpitTexts.get("ports.port")), new Label(CockpitTexts.get("ports.owner")),
					new Label(CockpitTexts.get("ports.rate")), new Label(CockpitTexts.get("ports.p95")), true));
			// Always as many rows as ports ranked, so that the table keeps its height.
			for (int index = 0; index < HOT_PORTS; index++) {
				PortRow row = new PortRow();
				this.portRows.add(row);
				rows.add(row.root);
			}
			this.hotPortsList.getChildren().setAll(rows);
		}
		double busiest = 1;
		for (CockpitSnapshot.HotPort port : snapshot.hottestPorts()) {
			busiest = Math.max(busiest, port.callsPerSecond());
		}
		List<CockpitSnapshot.HotPort> ports = snapshot.hottestPorts();
		for (int index = 0; index < this.portRows.size(); index++) {
			PortRow row = this.portRows.get(index);
			if (index < ports.size()) {
				row.show(ports.get(index), busiest);
			} else if (index == 0) {
				row.note(CockpitTexts.get("ports.none"));
			} else {
				row.note(" ");
			}
		}
	}

	/** A row of the hottest ports table: built once, then given a port or a note. */
	private static final class PortRow {

		private final Region marker = new Region();
		private final Label name = new Label();
		private final Label owner = new Label();
		private final Region bar = new Region();
		private final Label rate = new Label();
		private final Label p95 = new Label();
		private final Tooltip nameTip = Tooltips.install(this.name, "");
		private final Tooltip ownerTip = Tooltips.install(this.owner, "");
		private final Node root;

		PortRow() {
			this.marker.getStyleClass().add("sv-cockpit-port-marker");
			this.name.setMinWidth(0);
			HBox portCell = new HBox(8, this.marker, this.name);
			portCell.setMinWidth(0);
			portCell.setAlignment(Pos.CENTER_LEFT);
			this.owner.setMinWidth(0);
			this.bar.getStyleClass().add("sv-cockpit-rate-bar");
			this.bar.setMaxWidth(Region.USE_PREF_SIZE);
			this.rate.getStyleClass().add("sv-cockpit-mono");
			this.rate.setMinWidth(Region.USE_PREF_SIZE);
			HBox rateCell = new HBox(8, this.bar, this.rate);
			rateCell.setAlignment(Pos.CENTER_LEFT);
			this.p95.getStyleClass().add("sv-cockpit-mono");
			this.root = portRow(portCell, this.owner, rateCell, this.p95, false);
		}

		void show(final CockpitSnapshot.HotPort port, final double busiest) {
			String kind = "sv-kind-bg-" + suffix(port.kind());
			setKind(this.marker, kind);
			setKind(this.bar, kind);
			showMarks(true);
			this.name.setMinWidth(0);
			this.name.setText(port.portType().getSimpleName());
			this.name.getStyleClass().setAll("label", "sv-cockpit-mono");
			this.nameTip.setText(port.portType().getName());
			this.name.setTooltip(this.nameTip);
			this.owner.setText(port.owner().getSimpleName());
			this.ownerTip.setText(port.owner().getName());
			this.owner.setTooltip(this.ownerTip);
			this.bar.setPrefWidth(Math.max(4, 56 * port.callsPerSecond() / busiest));
			this.rate.setText(rate(port.callsPerSecond()) + "/s");
			this.p95.setText(port.p95Nanos() < 0 ? "-" : duration(port.p95Nanos()));
		}

		void note(final String text) {
			// A note is laid out like the table's other text: across the row, without the port marks.
			showMarks(false);
			this.name.setMinWidth(Region.USE_PREF_SIZE);
			this.name.setText(text);
			this.name.getStyleClass().setAll("label", "sv-cockpit-muted");
			this.name.setTooltip(null);
			this.owner.setText("");
			this.owner.setTooltip(null);
			this.rate.setText("");
			this.p95.setText("");
		}

		private void showMarks(final boolean shown) {
			for (Region mark : List.of(this.marker, this.bar)) {
				mark.setVisible(shown);
				mark.setManaged(shown);
			}
		}

		private static void setKind(final Region region, final String kind) {
			region.getStyleClass().removeIf(styleClass -> styleClass.startsWith("sv-kind-bg-"));
			region.getStyleClass().add(kind);
		}
	}

	private static Node portRow(final Node port, final Node owner, final Node rate, final Node p95,
			final boolean header) {
		GridPane row = new GridPane();
		ColumnConstraints portColumn = new ColumnConstraints();
		portColumn.setPercentWidth(40);
		ColumnConstraints ownerColumn = new ColumnConstraints();
		ownerColumn.setPercentWidth(26);
		ColumnConstraints rateColumn = new ColumnConstraints();
		rateColumn.setPercentWidth(22);
		ColumnConstraints p95Column = new ColumnConstraints();
		p95Column.setPercentWidth(12);
		row.getColumnConstraints().addAll(portColumn, ownerColumn, rateColumn, p95Column);
		row.addRow(0, port, owner, rate, p95);
		row.setMinWidth(0);
		GridPane.setHalignment(p95, HPos.RIGHT);
		row.getStyleClass().add(header ? "sv-cockpit-table-header" : "sv-cockpit-row");
		return row;
	}

	// --- Helpers ---

	private List<Sample> window(final int seconds) {
		List<Sample> samples = new ArrayList<>();
		Iterator<Sample> newestFirst = this.history.descendingIterator();
		while (newestFirst.hasNext() && samples.size() < seconds) {
			samples.add(0, newestFirst.next());
		}
		return samples;
	}

	private static void drawSpark(final Pane spark, final List<Sample> samples,
			final ToDoubleFunction<Sample> value, final String styleClass) {
		if (spark.getChildren().isEmpty()) {
			Polygon area = new Polygon();
			area.getStyleClass().addAll("sv-cockpit-spark-area", styleClass + "-area");
			Polyline line = new Polyline();
			line.getStyleClass().addAll("sv-cockpit-spark", styleClass);
			Circle end = new Circle(2.5);
			end.getStyleClass().add(styleClass + "-end");
			spark.getChildren().addAll(area, line, end);
		}
		Polygon area = (Polygon) spark.getChildren().get(0);
		Polyline line = (Polyline) spark.getChildren().get(1);
		Circle end = (Circle) spark.getChildren().get(2);
		List<Double> points = new ArrayList<>();
		double width = spark.getWidth() > 0 ? spark.getWidth() - 3 : 107;
		double height = 30;
		if (samples.size() >= 2) {
			double max = Math.max(1, samples.stream().mapToDouble(value).max().orElse(1));
			for (int index = 0; index < samples.size(); index++) {
				points.add(width * index / (samples.size() - 1));
				points.add(height - 2 - (height - 4) * value.applyAsDouble(samples.get(index)) / max);
			}
		}
		line.getPoints().setAll(points);
		List<Double> closed = new ArrayList<>();
		if (!points.isEmpty()) {
			closed.addAll(List.of(points.get(0), height));
			closed.addAll(points);
			closed.addAll(List.of(points.get(points.size() - 2), height));
			end.setCenterX(points.get(points.size() - 2));
			end.setCenterY(points.get(points.size() - 1));
		}
		area.getPoints().setAll(closed);
		end.setVisible(!points.isEmpty());
	}

	private static String describe(final CockpitSnapshot.Failure failure) {
		return failure.owner().getSimpleName() + "." + (failure.method() == null ? failure.portType().getSimpleName()
				: failure.method() + "()");
	}

	private static String rate(final double value) {
		return value < 10 ? String.format(Locale.ROOT, "%.1f", value) : String.format(Locale.ROOT, "%.0f", value);
	}

	private static String duration(final long nanos) {
		double millis = nanos / 1_000_000.0;
		return String.format(Locale.ROOT, millis < 10 ? "%.2f ms" : "%.0f ms", millis);
	}

	private static String suffix(final ComponentPortKind kind) {
		return switch (kind) {
			case SERVICE -> "service";
			case EVENT -> "event";
			default -> "parameter";
		};
	}

	private static Label bigValue() {
		Label label = new Label("–");
		label.getStyleClass().add("sv-cockpit-big");
		label.setMinWidth(Region.USE_PREF_SIZE);
		return label;
	}

	private static Label detail() {
		Label label = new Label();
		label.getStyleClass().add("sv-cockpit-detail");
		return label;
	}

	private static Pane spark() {
		Pane pane = new Pane();
		pane.setMinSize(30, 30);
		pane.setPrefSize(110, 30);
		pane.setMaxSize(110, 30);
		return pane;
	}

	private static VBox tile(final String title, final Label value, final Label detail, final Node spark) {
		Label eyebrow = new Label(title.toUpperCase(Locale.ROOT));
		eyebrow.getStyleClass().add("sv-eyebrow");
		HBox valueRow = new HBox(12, value);
		valueRow.setAlignment(Pos.CENTER_LEFT);
		if (spark != null) {
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			valueRow.getChildren().addAll(spacer, spark);
		}
		HBox.setHgrow(valueRow, Priority.ALWAYS);
		VBox tile = new VBox(8, eyebrow, valueRow, detail);
		tile.getStyleClass().add("sv-cockpit-tile");
		tile.setMaxWidth(Double.MAX_VALUE);
		return tile;
	}

	private static VBox card(final Node heading, final Node... content) {
		VBox card = new VBox(14, heading);
		card.getChildren().addAll(content);
		card.getStyleClass().add("sv-cockpit-card");
		return card;
	}

	private static Node heading(final String title, final String subtitle, final Node right) {
		Label titleLabel = new Label(title);
		titleLabel.getStyleClass().add("sv-cockpit-card-title");
		HBox heading = new HBox(10, titleLabel);
		heading.setAlignment(Pos.BASELINE_LEFT);
		if (subtitle != null) {
			Label subtitleLabel = new Label(subtitle);
			subtitleLabel.getStyleClass().add("sv-cockpit-muted");
			heading.getChildren().add(subtitleLabel);
		}
		if (right != null) {
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			heading.getChildren().addAll(spacer, right);
		}
		return heading;
	}

	/**
	 * Throughput by kind over the selected range. Hovering shows the values at that moment
	 * in a box drawn on the chart itself.
	 */
	private static final class ThroughputChart extends Pane {

		private static final double LEFT = 44;
		private static final double BOTTOM = 22;
		private static final double HEIGHT = 240;

		private List<Sample> samples = List.of();
		private int rangeSeconds = 60;
		private final Line cursor = new Line();
		private final VBox readout = new VBox(4);

		private final Line[] gridLines = new Line[5];
		private final Label[] valueLabels = new Label[5];
		private final Label[] timeLabels = new Label[5];
		private final Polygon servicesArea = new Polygon();
		private final Polygon eventsArea = new Polygon();
		private final Polygon parametersArea = new Polygon();
		private final Circle servicesDot = new Circle(4);
		private final Circle eventsDot = new Circle(4);
		private final Circle parametersDot = new Circle(4);
		private final Polyline services = new Polyline();
		private final Polyline events = new Polyline();
		private final Polyline parameters = new Polyline();

		/** The pointer's position while it is over the chart, else NaN. */
		private double pointerX = Double.NaN;

		ThroughputChart() {
			this.setMinHeight(HEIGHT);
			this.setPrefHeight(HEIGHT);
			this.setMaxHeight(HEIGHT);
			this.getStyleClass().add("sv-cockpit-chart");
			this.cursor.getStyleClass().add("sv-cockpit-cursor");
			this.readout.getStyleClass().add("sv-cockpit-readout");
			this.readout.setManaged(false);
			this.readout.setMouseTransparent(true);
			this.cursor.setManaged(false);
			this.cursor.setMouseTransparent(true);
			for (int step = 0; step <= 4; step++) {
				this.gridLines[step] = new Line();
				this.gridLines[step].getStyleClass().add("sv-cockpit-grid");
				this.valueLabels[step] = axisLabel();
				this.valueLabels[step].setPrefWidth(LEFT - 8);
				this.valueLabels[step].setAlignment(Pos.CENTER_RIGHT);
				this.timeLabels[step] = axisLabel();
				this.getChildren().addAll(this.gridLines[step], this.valueLabels[step], this.timeLabels[step]);
			}
			this.servicesArea.getStyleClass().addAll("sv-cockpit-area", "sv-cockpit-area-service");
			this.eventsArea.getStyleClass().addAll("sv-cockpit-area", "sv-cockpit-area-event");
			this.parametersArea.getStyleClass().addAll("sv-cockpit-area", "sv-cockpit-area-parameter");
			this.servicesDot.getStyleClass().addAll("sv-cockpit-dot", "sv-kind-fill-service");
			this.eventsDot.getStyleClass().addAll("sv-cockpit-dot", "sv-kind-fill-event");
			this.parametersDot.getStyleClass().addAll("sv-cockpit-dot", "sv-kind-fill-parameter");
			for (Circle dot : new Circle[] { this.servicesDot, this.eventsDot, this.parametersDot }) {
				dot.setManaged(false);
				dot.setMouseTransparent(true);
				dot.setVisible(false);
			}
			this.services.getStyleClass().addAll("sv-cockpit-series", "sv-kind-stroke-service");
			this.events.getStyleClass().addAll("sv-cockpit-series", "sv-kind-stroke-event");
			this.parameters.getStyleClass().addAll("sv-cockpit-series", "sv-kind-stroke-parameter");
			this.getChildren().addAll(this.servicesArea, this.eventsArea, this.parametersArea, this.parameters, this.events,
					this.services, this.cursor, this.servicesDot, this.eventsDot, this.parametersDot, this.readout);
			this.widthProperty().addListener(observable -> this.redraw());
			this.addEventHandler(MouseEvent.MOUSE_MOVED, event -> {
				this.pointerX = event.getX();
				this.showAt(this.pointerX);
			});
			this.addEventHandler(MouseEvent.MOUSE_EXITED, event -> {
				this.pointerX = Double.NaN;
				this.hideReadout();
			});
		}

		Node legend() {
			HBox legend = new HBox(16, legendItem(CockpitTexts.get("tile.services"), "service"),
					legendItem(CockpitTexts.get("tile.events"), "event"),
					legendItem(CockpitTexts.get("legend.parameters"), "parameter"));
			legend.setAlignment(Pos.CENTER_RIGHT);
			return legend;
		}

		private static Node legendItem(final String text, final String kind) {
			Region swatch = new Region();
			swatch.getStyleClass().addAll("sv-cockpit-legend-swatch", "sv-kind-bg-" + kind);
			Label label = new Label(text);
			label.getStyleClass().add("sv-cockpit-muted");
			HBox item = new HBox(6, swatch, label);
			item.setAlignment(Pos.CENTER_LEFT);
			return item;
		}

		void draw(final List<Sample> samples, final int rangeSeconds) {
			this.samples = samples;
			this.rangeSeconds = rangeSeconds;
			this.redraw();
		}

		private double max() {
			double max = 1;
			for (Sample sample : this.samples) {
				max = Math.max(max, Math.max(sample.services(), Math.max(sample.events(), sample.parameters())));
			}
			// A round top for the scale: 1, 2 or 5 times a power of ten.
			double magnitude = Math.pow(10, Math.floor(Math.log10(max)));
			for (double step : new double[] { 1, 2, 5, 10 }) {
				if (max <= step * magnitude) {
					return step * magnitude;
				}
			}
			return max;
		}

		private double xOf(final int index) {
			double plotWidth = this.getWidth() - LEFT - 8;
			// The newest sample at the right edge; the range's width gives the scale.
			int offset = this.rangeSeconds - this.samples.size();
			return LEFT + plotWidth * (offset + index) / Math.max(1, this.rangeSeconds - 1);
		}

		private double yOf(final double value, final double max) {
			double plotHeight = HEIGHT - BOTTOM - 8;
			return 8 + plotHeight * (1 - value / max);
		}

		private void redraw() {
			double width = this.getWidth();
			if (width <= LEFT + 20) {
				return;
			}
			double max = this.max();
			for (int step = 0; step <= 4; step++) {
				double value = max * step / 4;
				double y = this.yOf(value, max);
				Line grid = this.gridLines[step];
				grid.setStartX(LEFT);
				grid.setEndX(width - 8);
				grid.setStartY(y);
				grid.setEndY(y);
				Label valueLabel = this.valueLabels[step];
				valueLabel.setText(axisValue(value));
				valueLabel.relocate(0, y - 8);
			}
			for (int step = 0; step <= 4; step++) {
				int secondsAgo = this.rangeSeconds - this.rangeSeconds * step / 4;
				double x = LEFT + (width - LEFT - 8) * step / 4;
				Label timeLabel = this.timeLabels[step];
				timeLabel.setText(step == 4 ? CockpitTexts.get("chart.now") : "-" + label(secondsAgo));
				timeLabel.relocate(step == 4 ? x - 24 : x - 4, HEIGHT - BOTTOM + 4);
			}
			this.services.getPoints().setAll(this.series(Sample::services, max));
			this.events.getPoints().setAll(this.series(Sample::events, max));
			this.parameters.getPoints().setAll(this.series(Sample::parameters, max));
			this.servicesArea.getPoints().setAll(this.closed(this.services.getPoints(), max));
			this.eventsArea.getPoints().setAll(this.closed(this.events.getPoints(), max));
			this.parametersArea.getPoints().setAll(this.closed(this.parameters.getPoints(), max));
			if (Double.isNaN(this.pointerX)) {
				this.hideReadout();
			} else {
				this.showAt(this.pointerX);
			}
		}

		@Override
		protected void layoutChildren() {
			super.layoutChildren();
			for (int step = 0; step <= 4; step++) {
				this.valueLabels[step].autosize();
				this.timeLabels[step].autosize();
			}
		}

		/** A scale value with no more decimals than it needs, at most two. */
		private static String axisValue(final double value) {
			return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
					.toPlainString();
		}

		private static Label axisLabel() {
			Label label = new Label();
			label.getStyleClass().add("sv-cockpit-axis");
			label.setManaged(false);
			return label;
		}

		private static String label(final int seconds) {
			return seconds >= 60 ? seconds / 60 + " min" : seconds + " s";
		}

		/** One series' points, or none with fewer than two samples. */
		private List<Double> series(final ToDoubleFunction<Sample> value, final double max) {
			List<Double> points = new ArrayList<>();
			if (this.samples.size() >= 2) {
				for (int index = 0; index < this.samples.size(); index++) {
					points.add(this.xOf(index));
					points.add(this.yOf(value.applyAsDouble(this.samples.get(index)), max));
				}
			}
			return points;
		}

		/** A line's points closed down to the zero line, for the area under it. */
		private List<Double> closed(final List<Double> line, final double max) {
			List<Double> points = new ArrayList<>();
			if (line.size() >= 4) {
				points.add(line.get(0));
				points.add(this.yOf(0, max));
				points.addAll(line);
				points.add(line.get(line.size() - 2));
				points.add(this.yOf(0, max));
			}
			return points;
		}

		private void showAt(final double x) {
			if (this.samples.isEmpty()) {
				return;
			}
			int nearest = 0;
			for (int index = 0; index < this.samples.size(); index++) {
				if (Math.abs(this.xOf(index) - x) < Math.abs(this.xOf(nearest) - x)) {
					nearest = index;
				}
			}
			Sample sample = this.samples.get(nearest);
			double cursorX = this.xOf(nearest);
			this.cursor.setStartX(cursorX);
			this.cursor.setEndX(cursorX);
			this.cursor.setStartY(8);
			this.cursor.setEndY(HEIGHT - BOTTOM);
			this.cursor.setVisible(true);
			double max = this.max();
			placeDot(this.servicesDot, cursorX, this.yOf(sample.services(), max));
			placeDot(this.eventsDot, cursorX, this.yOf(sample.events(), max));
			placeDot(this.parametersDot, cursorX, this.yOf(sample.parameters(), max));
			Label time = new Label(TIME.format(Instant.ofEpochMilli(sample.timeMillis())));
			time.getStyleClass().add("sv-cockpit-mono-muted");
			this.readout.getChildren().setAll(time, readoutLine(CockpitTexts.get("readout.services"), sample.services(), "service"),
					readoutLine(CockpitTexts.get("readout.events"), sample.events(), "event"),
					readoutLine(CockpitTexts.get("readout.parameters"), sample.parameters(), "parameter"));
			this.readout.applyCss();
			this.readout.layout();
			this.readout.autosize();
			double boxX = cursorX + 12 + this.readout.getWidth() > this.getWidth() ? cursorX - 12 - this.readout.getWidth()
					: cursorX + 12;
			this.readout.relocate(boxX, 12);
			this.readout.setVisible(true);
		}

		private static Node readoutLine(final String name, final double value, final String kind) {
			Region swatch = new Region();
			swatch.getStyleClass().addAll("sv-cockpit-legend-swatch", "sv-kind-bg-" + kind);
			Label label = new Label(name);
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			Label number = new Label(rate(value) + "/s");
			number.getStyleClass().add("sv-cockpit-mono");
			HBox line = new HBox(8, swatch, label, spacer, number);
			line.setAlignment(Pos.CENTER_LEFT);
			line.setMinWidth(170);
			return line;
		}

		private static void placeDot(final Circle dot, final double x, final double y) {
			dot.setCenterX(x);
			dot.setCenterY(y);
			dot.setVisible(true);
		}

		private void hideReadout() {
			this.servicesDot.setVisible(false);
			this.eventsDot.setVisible(false);
			this.parametersDot.setVisible(false);
			this.cursor.setVisible(false);
			this.readout.setVisible(false);
		}
	}
}
