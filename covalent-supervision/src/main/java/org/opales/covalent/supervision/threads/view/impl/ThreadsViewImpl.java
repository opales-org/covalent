/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.view.impl;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.LongToDoubleFunction;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.workers.DurationHistogram;
import org.opales.covalent.container.workers.WorkerPoolKind;
import org.opales.covalent.hmi.action.ActionConsumerServices;
import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.supervision.componentview.internal.CollapsibleSide;
import org.opales.covalent.supervision.componentview.internal.Toolbox;
import org.opales.covalent.supervision.componentview.internal.Tooltips;
import org.opales.covalent.supervision.containermanager.interaction.ContainerManagerActionKey;
import org.opales.covalent.supervision.sequencediagram.interaction.SequenceRecordingActionKey;
import org.opales.covalent.supervision.supervisionhmi.view.FxThread;
import org.opales.covalent.supervision.supervisionhmi.view.PanelHostingServices;
import org.opales.covalent.supervision.supervisionhmi.view.RailEntry;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisorMessages;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisorTabDescriptorAdapter;
import org.opales.covalent.supervision.supervisionhmi.view.ToolbarFunction;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;
import org.opales.covalent.supervision.threads.interaction.ThreadsActionKey;
import org.opales.covalent.supervision.threads.interaction.ThreadsViewKey;
import org.opales.covalent.supervision.threads.model.Contention;
import org.opales.covalent.supervision.threads.model.ThreadActivity;
import org.opales.covalent.supervision.threads.model.ThreadStack;
import org.opales.covalent.supervision.threads.model.ThreadsEvents;
import org.opales.covalent.supervision.threads.model.ThreadsModelReadServices;
import org.opales.covalent.supervision.threads.model.ThreadsSnapshot;
import org.opales.covalent.supervision.threads.view.ThreadsView;
import org.opales.covalent.supervision.threads.view.ThreadsViewServices;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.shape.SVGPath;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;

/**
 * The Threads panel: key figures, the pools' occupancy over time, their health, one lane per
 * thread on a shared time axis, and the selected thread. It records every sample from the
 * start, shown or not, and keeps the last quarter of an hour, of which the range selector shows
 * the last 15 s, minute, 5 or 15 minutes. Pause freezes what is shown while recording goes on.
 */
@Implementation
public class ThreadsViewImpl implements ThreadsView, ThreadsViewServices, ThreadsEvents {

	/** The component calls the inspector has rows for. */
	private static final int CALL_ROWS = 5;

	/** The recent events shown. */
	private static final int EVENT_ROWS = 4;

	/** The lines of recent activity the inspector has rows for. */
	private static final int ACTIVITY_ROWS = 8;


	/** Creates the View; the container injects the services it uses. */
	public ThreadsViewImpl() {
		// Nothing to initialize.
	}

	private static final int[] RANGES = { 15, 60, 300, 900 };

	private static final String[] RANGE_LABELS = { "15 s", "1 min", "5 min", "15 min" };

	/** The points of a tile's sparkline. */
	private static final int SPARK_POINTS = 60;

	private static final String DISCONNECTED_STYLE_CLASS = "sv-disconnected";

	@UsedServices
	private ActionConsumerServices actionManagerServices;

	@UsedServices
	private PanelHostingServices useHostingServices;

	@UsedServices
	private ThreadsModelReadServices useThreadsModelReadServices;

	private final ThreadsHistory history = new ThreadsHistory();

	private ThreadsSnapshot latest;

	private int rangeSeconds = 60;

	/** The last sample shown while paused, {@code -1} while live. */
	private long pausedAt = -1;

	private long hoverSample = -1;

	/** The lanes card's subtitle and legend, which tell threads or, grouped by component, loads. */
	private Label lanesSub;

	private Node lanesLegend;

	/** The component whose load is under the pointer, {@code null} if none. */
	private String hoverComponent;

	private final ComponentLoadRows loadRows = new ComponentLoadRows(this);

	/** The lanes' first column's heading: threads, or components while grouped by component. */
	private final Label threadColumn = new Label(ThreadsTexts.get("threadColumn"));

	private long selectedThreadId = -1;

	private BorderPane root;

	private final Palette palette = new Palette();

	private HBox liveBadge;
	private Label liveLabel;
	private final ToggleButton trackingSwitch = new ToggleButton();
	private final Button pauseButton = Toolbox.iconButton(ICON_PAUSE, ThreadsTexts.get("pause.tip"));

	/** Pause: two bars; resume: a play triangle. */
	private static final String ICON_PAUSE = "M5.5 3 V13 M10.5 3 V13";
	private static final String ICON_RESUME = "M5 3 L13 8 L5 13 Z";
	/** Copy: a sheet over another. */
	private static final String ICON_COPY = "M5.5 5.5 H13.5 V13.5 H5.5 Z M2.5 10.5 V2.5 H10.5";
	private final Label deadlockBanner = new Label();

	/** The banner with its commands: shown while a deadlock lasts. */
	private final HBox deadlockBar = new HBox(12);

	private final Label threadsValue = bigValue();
	private final Label threadsDetail = detail();
	private final Label averageValue = bigValue();
	private final Label averageDetail = detail();
	private final Sparkline averageSpark = new Sparkline(110, 30);
	private final Label peakValue = bigValue();
	private final Label peakDetail = detail();
	private VBox peakTile;
	private final Label queuedValue = bigValue();
	private final Label queuedDetail = detail();
	private final Sparkline queuedSpark = new Sparkline(110, 30);
	private final Label rejectionsValue = bigValue();
	private final Label rejectionsDetail = detail();
	private VBox rejectionsTile;

	private final OccupancyChart chart = new OccupancyChart();
	private final HBox legend = new HBox(16);

	/** The pools' verdict, and one line per pool saying why. */
	private final PoolHealth poolHealth = new PoolHealth(this);
	/** The threads' states now: folded by default, as they say what threads do, not how pressed pools are. */
	private final VBox threadsNow = new VBox(14);
	/** What the occupancy chart measures: the share of time with task tracking, busy now otherwise. */
	private final Label occupancySubtitle = new Label(ThreadsTexts.get("occupancy.sub.tracked"));
	private final HBox healthBar = new HBox(2);
	private final Map<ThreadActivity, Region> healthSegments = new EnumMap<>(ThreadActivity.class);
	private final Map<ThreadActivity, Label> healthCounts = new EnumMap<>(ThreadActivity.class);
	private final Map<ThreadActivity, HBox> healthLines = new EnumMap<>(ThreadActivity.class);
	private final Map<ThreadActivity, Double> healthShares = new EnumMap<>(ThreadActivity.class);
	private final VBox eventsList = new VBox(0);
	private final List<EventRow> eventRows = new ArrayList<>();
	private final Label eventsNone = new Label();
	/** The recent events' fold: their count, the worst one's color and the last one's time. */
	private final Button eventsFold = new Button();
	private final Label eventsFoldText = new Label();
	private final Region eventsFoldDot = new Region();
	private final Label eventsFoldTime = new Label();

	private final Label lanesReadout = new Label();
	private final ToggleButton byComponentButton = new ToggleButton(ThreadsTexts.get("groupBy.component"));
	/** Whether the lanes are gathered by component rather than by pool. */
	private boolean byComponent;
	/** While paused: moves what is shown back in time, over the samples kept. */
	private final Slider scrubber = new Slider();
	private boolean scrubbing;
	private final Label trackingHint = new Label(ThreadsTexts.get("trackingHint"));
	private final HBox axis = new HBox();
	private final VBox lanes = new VBox(10);
	private final Map<String, PoolSection> sections = new HashMap<>();
	private List<String> sectionOrder = List.of();

	private final VBox inspector = new VBox(0);
	private long inspectedThreadId = -2;
	private final Label stackHint = new Label();
	private final VBox stackFrames = new VBox(2);
	private final VBox callStack = new VBox(6);
	private final List<CallRow> callRows = new ArrayList<>();
	private final Label callHint = fixedLines(muted(""), 2);
	private ThreadStack stack = ThreadStack.NONE;
	private boolean javaFramesShown;
	private long stackRequestMillis;
	private final Label inspectorHint = muted(ThreadsTexts.get("inspector.hint"));
	private final Label inspectorName = new Label();
	private final Label inspectorPool = new Label();
	private final Label inspectorState = new Label();
	private final VBox inspectorDetails = new VBox(0);
	private final Label taskText = fixedLines(mono(""), 1);
	private final Tooltip taskTip = Tooltips.install(this.taskText, "");
	private final Label taskDetail = fixedLines(muted(""), 1);
	private final Label lockText = fixedLines(muted(""), 2);
	private final Tooltip lockTip = Tooltips.install(this.lockText, "");
	private final Label cpuText = fixedLines(mono(""), 1);
	private final Label cpuDetail = fixedLines(muted(""), 2);
	private final Sparkline threadSpark = new Sparkline(270, 60);
	private final VBox recentActivity = new VBox(0);
	private final List<ActivityRow> activityRows = new ArrayList<>();
	private final Label taskEyebrow = eyebrow(ThreadsTexts.get("inspector.currentTask"));
	/** The moment picked on a lane: its thread, first and last samples; {@code null} if none. */
	private long[] moment;
	/** The components last handed to the shared Record button, {@code null} before any. */
	private Set<Class<?>> recordScope;

	// --- ThreadsViewServices ---

	@Override
	public void showView(final ViewKey key) {
		Platform.runLater(() -> {
			if (this.root == null) {
				this.root = this.build();
			}
			this.useHostingServices.openPanel(ThreadsViewKey.INSTANCE, this.root, this.descriptor());
			this.render();
		});
	}

	@Override
	public void hideView(final ViewKey key) {
		FxThread.run(() -> {
			this.useHostingServices.closePanel(key);
		});
	}

	private SupervisorTabDescriptorAdapter descriptor() {
		return new SupervisorTabDescriptorAdapter() {
			@Override
			public RailEntry railEntry() {
				return RailEntry.THREADS;
			}

			@Override
			public String title() {
				return ThreadsTexts.get("title");
			}

			@Override
			public EnumSet<ToolbarFunction> supportedToolbarFunctions() {
				return EnumSet.of(ToolbarFunction.RECORD_TOGGLE);
			}

			@Override
			public ActionKey recordActionKey() {
				return ThreadsActionKey.RecordToggle;
			}

			@Override
			public ActionKey recordingStateActionKey() {
				return SequenceRecordingActionKey.RecordingState;
			}

			@Override
			public boolean closable() {
				return false;
			}
		};
	}

	// --- ThreadsEvents ---

	@Override
	public void onThreadsSampled() {
		Platform.runLater(() -> {
			ThreadsSnapshot snapshot = this.useThreadsModelReadServices.getSnapshot();
			if (snapshot == this.latest) {
				return;
			}
			boolean newConnection = snapshot.connected() && (this.latest == null || !this.latest.connected());
			if (newConnection || !snapshot.connected()) {
				if (this.latest == null || this.latest.connected() != snapshot.connected()) {
					this.history.clear();
					this.pausedAt = -1;
					this.selectedThreadId = -1;
					this.moment = null;
				}
			}
			if (snapshot.connected()) {
				this.history.add(snapshot);
			}
			this.latest = snapshot;
			if (this.root != null && this.root.getScene() != null) {
				this.render();
			}
		});
	}

	@Override
	public void onStackRead(final long threadId) {
		Platform.runLater(() -> {
			if (threadId == this.selectedThreadId) {
				this.stack = this.useThreadsModelReadServices.getStack(threadId);
				this.showStack();
			}
		});
	}

	// --- Building ---

	private BorderPane build() {
		this.liveLabel = new Label(ThreadsTexts.get("live"));
		this.liveBadge = new HBox(6, new Circle(4), this.liveLabel);
		this.liveBadge.getStyleClass().add("sv-cockpit-live");
		this.liveBadge.setAlignment(Pos.CENTER_LEFT);

		Label trackingLabel = new Label(ThreadsTexts.get("taskTracking"));
		trackingLabel.getStyleClass().add("sv-threads-switch-label");
		this.trackingSwitch.getStyleClass().add("sv-switch");
		Tooltips.install(this.trackingSwitch, ThreadsTexts.get("taskTracking.tip"));
		this.trackingSwitch.setOnAction(event -> {
			boolean wanted = this.trackingSwitch.isSelected();
			this.trackingSwitch.setSelected(this.latest != null && this.latest.taskTracking());
			this.actionManagerServices.executeAction(ThreadsActionKey.SetTaskTracking, wanted);
		});
		HBox tracking = new HBox(8, trackingLabel, this.trackingSwitch);
		tracking.setAlignment(Pos.CENTER_LEFT);

		this.pauseButton.setOnAction(event -> {
			this.pausedAt = this.pausedAt < 0 ? Math.max(0, this.history.count() - 1) : -1;
			this.render();
		});
		Button copy = Toolbox.iconButton(ICON_COPY, ThreadsTexts.get("copyReport.tip"));
		copy.setOnAction(event -> {
			ClipboardContent content = new ClipboardContent();
			content.putString(this.report());
			Clipboard.getSystemClipboard().setContent(content);
		});

		Label groupLabel = new Label(ThreadsTexts.get("groupBy"));
		groupLabel.getStyleClass().add("sv-eyebrow");
		ToggleGroup grouping = new ToggleGroup();
		ToggleButton byPool = new ToggleButton(ThreadsTexts.get("groupBy.pool"));
		byPool.setToggleGroup(grouping);
		byPool.setSelected(true);
		byPool.setOnAction(event -> {
			byPool.setSelected(true);
			this.byComponent = false;
			this.render();
		});
		Tooltips.install(byPool, ThreadsTexts.get("groupBy.pool.tip"));
		this.byComponentButton.setToggleGroup(grouping);
		this.byComponentButton.setOnAction(event -> {
			this.byComponentButton.setSelected(true);
			this.byComponent = true;
			this.loadRows.resort();
			this.render();
		});
		Tooltips.install(this.byComponentButton, ThreadsTexts.get("groupBy.component.tip"));
		HBox groupBy = new HBox(2, byPool, this.byComponentButton);
		groupBy.getStyleClass().add("sv-cockpit-ranges");
		Node groupByWrapper = groupBy;

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
			Tooltips.install(range, ThreadsTexts.get("range.tip", RANGE_LABELS[index]));
			range.setOnAction(event -> {
				if (!range.isSelected()) {
					range.setSelected(true);
				}
				this.rangeSeconds = seconds;
				this.loadRows.resort();
				this.render();
			});
			ranges.getChildren().add(range);
		}
		HBox header = new HBox(12, this.liveBadge, tracking, spacer, this.pauseButton, copy, groupLabel,
				groupByWrapper, ranges);
		header.setAlignment(Pos.CENTER_LEFT);
		header.getStyleClass().add("sv-threads-header");

		this.deadlockBanner.getStyleClass().add("sv-threads-deadlock-text");
		this.deadlockBanner.setWrapText(true);
		this.deadlockBanner.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(this.deadlockBanner, Priority.ALWAYS);
		Button copyDeadlock = new Button(ThreadsTexts.get("deadlock.copy"));
		copyDeadlock.getStyleClass().add("sv-threads-button");
		copyDeadlock.setMinWidth(Region.USE_PREF_SIZE);
		Tooltips.install(copyDeadlock, ThreadsTexts.get("deadlock.copy.tip"));
		copyDeadlock.setOnAction(event -> {
			if (this.latest != null && !this.latest.deadlocks().isEmpty()) {
				ClipboardContent content = new ClipboardContent();
				content.putString(this.latest.deadlocks().get(0).report());
				Clipboard.getSystemClipboard().setContent(content);
			}
		});
		Button interruptDeadlock = new Button(ThreadsTexts.get("deadlock.interrupt"));
		interruptDeadlock.getStyleClass().add("sv-threads-button");
		interruptDeadlock.setMinWidth(Region.USE_PREF_SIZE);
		Tooltips.install(interruptDeadlock, ThreadsTexts.get("deadlock.interrupt.tip"));
		interruptDeadlock.setOnAction(event -> this.confirmInterrupt());
		this.deadlockBar.getChildren().setAll(this.deadlockBanner, copyDeadlock, interruptDeadlock);
		this.deadlockBar.setAlignment(Pos.CENTER_LEFT);
		this.deadlockBar.getStyleClass().add("sv-threads-deadlock");
		this.deadlockBar.setManaged(false);
		this.deadlockBar.setVisible(false);

		this.peakTile = tile(ThreadsTexts.get("tile.peak"), this.peakValue, this.peakDetail, null);
		this.rejectionsTile = tile(ThreadsTexts.get("tile.rejections"), this.rejectionsValue, this.rejectionsDetail, null);
		GridPane tiles = new GridPane();
		tiles.setHgap(14);
		for (int column = 0; column < 5; column++) {
			ColumnConstraints constraints = new ColumnConstraints();
			constraints.setPercentWidth(20);
			tiles.getColumnConstraints().add(constraints);
		}
		tiles.addRow(0, tile(ThreadsTexts.get("tile.threads"), this.threadsValue, this.threadsDetail, null),
				tile(ThreadsTexts.get("tile.average"), this.averageValue, this.averageDetail, this.averageSpark), this.peakTile,
				tile(ThreadsTexts.get("tile.queued"), this.queuedValue, this.queuedDetail, this.queuedSpark), this.rejectionsTile);

		this.legend.setAlignment(Pos.CENTER_RIGHT);
		this.chart.setRedraw(this::drawChart);
		HBox occupancyHeading = (HBox) heading(ThreadsTexts.get("occupancy"), null, this.legend);
		this.occupancySubtitle.getStyleClass().add("sv-cockpit-muted");
		occupancyHeading.getChildren().add(1, this.occupancySubtitle);
		VBox occupancy = card(occupancyHeading, this.chart);
		// As tall as the pool health card beside it.
		VBox.setVgrow(this.chart, Priority.ALWAYS);
		HBox.setHgrow(occupancy, Priority.ALWAYS);
		VBox health = this.buildHealth();
		HBox middle = new HBox(14, occupancy, health);

		VBox lanesCard = this.buildLanesCard();

		VBox content = new VBox(16, this.deadlockBar, tiles, middle, lanesCard);
		content.getStyleClass().add("sv-cockpit");
		ScrollPane scroll = new ScrollPane(content);
		scroll.setFitToWidth(true);
		scroll.getStyleClass().add("sv-cockpit-scroll");

		this.inspector.getStyleClass().add("sv-threads-inspector");
		this.inspector.setPrefWidth(310);
		this.inspector.setMinWidth(310);
		this.stackHint.getStyleClass().add("sv-cockpit-muted");
		this.stackHint.setWrapText(true);
		this.buildInspector();
		ScrollPane inspectorScroll = new ScrollPane(this.inspector);
		inspectorScroll.setFitToWidth(true);
		inspectorScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		inspectorScroll.getStyleClass().add("sv-threads-inspector-scroll");
		inspectorScroll.setMinWidth(320);

		SplitPane split = new SplitPane(scroll, inspectorScroll);
		split.setDividerPositions(0.78);
		CollapsibleSide.install(split, inspectorScroll, false, "threads.inspector");

		BorderPane pane = new BorderPane(split);
		pane.setTop(header);
		pane.getChildren().add(this.palette.node());
		pane.getStyleClass().add("sv-threads");
		return pane;
	}

	private VBox buildHealth() {
		this.healthBar.getStyleClass().add("sv-cockpit-lifecycle-bar");
		GridPane counts = new GridPane();
		counts.setHgap(18);
		counts.setVgap(8);
		int row = 0;
		int column = 0;
		for (ThreadActivity activity : ThreadActivity.values()) {
			Region segment = new Region();
			segment.getStyleClass().addAll("sv-cockpit-segment", "sv-threads-bg-" + ThreadsFormat.styleSuffix(activity));
			segment.setMinWidth(0);
			this.healthSegments.put(activity, segment);
			this.healthBar.getChildren().add(segment);
			Region dot = new Region();
			dot.getStyleClass().addAll("sv-cockpit-state-dot", "sv-threads-bg-" + ThreadsFormat.styleSuffix(activity));
			Label name = new Label(ThreadsFormat.label(activity).toLowerCase(Locale.ROOT));
			Label value = new Label();
			value.getStyleClass().add("sv-cockpit-mono");
			this.healthCounts.put(activity, value);
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			HBox line = new HBox(8, dot, name, spacer, value);
			line.setAlignment(Pos.CENTER_LEFT);
			line.setMinWidth(150);
			this.healthLines.put(activity, line);
			counts.add(line, column, row);
			column++;
			if (column == 2) {
				column = 0;
				row++;
			}
		}
		// Folded by default: their count, the worst one's color and the last one's time tell
		// whether to look; a click shows them.
		this.eventsFoldText.getStyleClass().add("sv-eyebrow");
		this.eventsFoldDot.getStyleClass().add("sv-threads-event-dot");
		this.eventsFoldTime.getStyleClass().add("sv-cockpit-mono-muted");
		HBox foldContent = new HBox(8, this.eventsFoldText, this.eventsFoldDot, this.eventsFoldTime);
		foldContent.setAlignment(Pos.CENTER_LEFT);
		this.eventsFold.setGraphic(foldContent);
		this.eventsFold.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		this.eventsFold.getStyleClass().add("sv-threads-fold");
		this.eventsFold.setMaxWidth(Double.MAX_VALUE);
		this.eventsFold.setAlignment(Pos.CENTER_LEFT);
		this.eventsList.setVisible(false);
		this.eventsList.setManaged(false);
		this.eventsFold.setOnAction(event -> {
			boolean shown = !this.eventsList.isVisible();
			this.eventsList.setVisible(shown);
			this.eventsList.setManaged(shown);
			this.renderEventsFold(this.latest == null ? ThreadsSnapshot.EMPTY : this.latest);
		});
		for (int index = 0; index < EVENT_ROWS; index++) {
			EventRow eventRow = new EventRow();
			this.eventRows.add(eventRow);
			this.eventsList.getChildren().add(eventRow.node);
		}
		this.eventsNone.getStyleClass().add("sv-cockpit-muted");
		this.eventsList.getChildren().add(this.eventsNone);
		Button threadsNowFold = new Button(ThreadsTexts.get("threadsNow.folded"));
		threadsNowFold.getStyleClass().addAll("sv-eyebrow", "sv-threads-fold");
		this.threadsNow.getChildren().setAll(this.healthBar, counts);
		this.threadsNow.setVisible(false);
		this.threadsNow.setManaged(false);
		threadsNowFold.setOnAction(event -> {
			boolean shown = !this.threadsNow.isVisible();
			this.threadsNow.setVisible(shown);
			this.threadsNow.setManaged(shown);
			threadsNowFold.setText(ThreadsTexts.get(shown ? "threadsNow.unfolded" : "threadsNow.folded"));
		});
		Tooltips.install(threadsNowFold, ThreadsTexts.get("threadsNow.tip"));
		this.healthBar.widthProperty().addListener(observable -> this.layoutHealthBar());
		VBox events = new VBox(4, this.eventsFold, this.eventsList);
		events.getStyleClass().addAll("sv-threads-compartment", "sv-threads-compartment-padded");
		// Framed as the sections above it.
		VBox threadsNowSection = new VBox(4, threadsNowFold, this.threadsNow);
		threadsNowSection.getStyleClass().addAll("sv-threads-compartment", "sv-threads-compartment-padded");
		VBox health = card(heading(ThreadsTexts.get("poolHealth"), null, null), this.poolHealth.verdict(), this.poolHealth.lines(),
				events, threadsNowSection);
		health.setPrefWidth(460);
		health.setMinWidth(400);
		return health;
	}

	private VBox buildLanesCard() {
		HBox legendItems = new HBox(14);
		legendItems.setAlignment(Pos.CENTER_RIGHT);
		for (ThreadActivity activity : new ThreadActivity[] { ThreadActivity.RUNS, ThreadActivity.WAITS,
				ThreadActivity.BLOCKED, ThreadActivity.IDLE, ThreadActivity.UNTRACKED }) {
			legendItems.getChildren().add(legendItem(ThreadsFormat.label(activity), "sv-threads-bg-"
					+ ThreadsFormat.styleSuffix(activity)));
		}
		this.lanesReadout.getStyleClass().add("sv-cockpit-mono-muted");
		HBox.setHgrow(this.scrubber, Priority.ALWAYS);
		this.scrubber.setVisible(false);
		this.scrubber.setManaged(false);
		Tooltips.install(this.scrubber, ThreadsTexts.get("scrubber.tip"));
		this.scrubber.valueProperty().addListener((observable, before, value) -> {
			if (!this.scrubbing && this.pausedAt >= 0) {
				this.pausedAt = Math.round(value.doubleValue());
				this.render();
			}
		});
		this.trackingHint.getStyleClass().add("sv-threads-hint");
		this.threadColumn.getStyleClass().add("sv-eyebrow");
		this.threadColumn.setMinWidth(PoolSection.NAME_WIDTH);
		this.threadColumn.setPrefWidth(PoolSection.NAME_WIDTH);
		HBox.setHgrow(this.axis, Priority.ALWAYS);
		HBox axisRow = new HBox(this.threadColumn, this.axis);
		axisRow.setAlignment(Pos.CENTER_LEFT);
		// Lined up with the lanes, inside their compartments: border, padding, row inset.
		axisRow.setPadding(new Insets(0, 17, 0, 14));
		HBox lanesHeading = (HBox) heading(ThreadsTexts.get("lanes.title"), ThreadsTexts.get("lanes.sub"), legendItems);
		this.lanesSub = (Label) lanesHeading.getChildren().get(1);
		this.lanesLegend = legendItems;
		return card(lanesHeading,
				new HBox(12, this.lanesReadout, this.trackingHint, this.scrubber), axisRow, this.lanes);
	}

	// --- Rendering ---

	private long windowEnd() {
		return this.pausedAt >= 0 ? this.pausedAt : this.history.count() - 1;
	}

	private int windowSize() {
		return this.rangeSeconds * ThreadsHistory.PER_SECOND;
	}

	private String rangeLabel() {
		for (int index = 0; index < RANGES.length; index++) {
			if (RANGES[index] == this.rangeSeconds) {
				return RANGE_LABELS[index];
			}
		}
		return this.rangeSeconds + " s";
	}

	private void render() {
		if (this.root == null) {
			return;
		}
		ThreadsSnapshot snapshot = this.latest == null ? ThreadsSnapshot.EMPTY : this.latest;
		boolean connected = snapshot.connected();
		this.liveLabel.setText(ThreadsTexts.get(!connected ? "disconnected" : this.pausedAt >= 0 ? "paused" : "live"));
		this.liveBadge.getStyleClass().remove(DISCONNECTED_STYLE_CLASS);
		if (!connected || this.pausedAt >= 0) {
			this.liveBadge.getStyleClass().add(DISCONNECTED_STYLE_CLASS);
		}
		boolean frozen = this.pausedAt >= 0;
		if (this.pauseButton.getGraphic() instanceof SVGPath icon
				&& !icon.getContent().equals(frozen ? ICON_RESUME : ICON_PAUSE)) {
			icon.setContent(frozen ? ICON_RESUME : ICON_PAUSE);
			Tooltips.install(this.pauseButton, ThreadsTexts.get(frozen ? "resume.tip" : "pause.tip"));
		}
		long first = Math.min(this.history.count() - 1, this.history.oldest() + this.windowSize() - 1);
		// Only worth showing once more has been kept than the range shows.
		boolean paused = this.pausedAt >= 0 && first < this.history.count() - 1;
		this.scrubber.setVisible(paused);
		this.scrubber.setManaged(paused);
		if (paused) {
			this.pausedAt = Math.max(first, Math.min(this.history.count() - 1, this.pausedAt));
			this.scrubbing = true;
			this.scrubber.setMin(first);
			this.scrubber.setMax(Math.max(first + 1, this.history.count() - 1));
			this.scrubber.setValue(this.pausedAt);
			this.scrubbing = false;
		}
		this.trackingSwitch.setSelected(snapshot.taskTracking());
		this.trackingSwitch.setDisable(!connected);
		this.trackingHint.setVisible(connected && !snapshot.taskTracking());
		this.trackingHint.setManaged(this.trackingHint.isVisible());
		boolean deadlock = !snapshot.deadlocks().isEmpty();
		this.deadlockBar.setVisible(deadlock);
		this.deadlockBar.setManaged(deadlock);
		if (deadlock) {
			this.deadlockBanner.setText(ThreadsTexts.get("deadlock.banner", waits(snapshot.deadlocks().get(0))));
		}
		this.renderTiles(snapshot);
		this.renderLegend(snapshot);
		this.drawChart();
		this.renderHealth(snapshot);
		this.renderLanes(snapshot);
		this.renderInspector(snapshot);
	}

	private void drawChart() {
		if (this.root != null) {
			this.chart.draw(this.history, this.windowEnd(), this.windowSize(), this.rangeLabel(), this.palette);
		}
	}

	private void renderPoolsHealth(final ThreadsSnapshot snapshot) {
		this.poolHealth.render(snapshot, this.history);
	}

	private void renderLegend(final ThreadsSnapshot snapshot) {
		this.occupancySubtitle.setText(ThreadsTexts.get(!snapshot.connected() || snapshot.taskTracking()
				? "occupancy.sub.tracked" : "occupancy.sub.sampled"));
		EnumSet<WorkerPoolKind> kinds = EnumSet.noneOf(WorkerPoolKind.class);
		snapshot.pools().forEach(pool -> kinds.add(pool.kind()));
		List<Node> items = new ArrayList<>();
		for (WorkerPoolKind kind : kinds) {
			items.add(legendItem(ThreadsFormat.kindLabel(kind), "sv-threads-series-" + ThreadsFormat.kindStyle(kind)));
		}
		this.legend.getChildren().setAll(items);
	}

	/** The share of every worker busy at a sample, {@code NaN} when there were none. */
	private double overallOccupancy(final long sample) {
		int slot = (int) (sample % ThreadsHistory.CAPACITY);
		double busy = 0;
		double total = 0;
		for (ThreadsHistory.PoolTrack pool : this.history.pools().values()) {
			if (pool.lastSample >= sample) {
				busy += pool.occupancy[slot] * pool.workers[slot];
				total += pool.workers[slot];
			}
		}
		return total == 0 ? Double.NaN : busy / total;
	}

	private int totalQueued(final long sample) {
		int slot = (int) (sample % ThreadsHistory.CAPACITY);
		int queued = 0;
		for (ThreadsHistory.PoolTrack pool : this.history.pools().values()) {
			if (pool.lastSample >= sample) {
				queued += pool.queued[slot];
			}
		}
		return queued;
	}

	/** A value over the window, reduced to {@link #SPARK_POINTS} averages. */
	private double[] spark(final LongToDoubleFunction value) {
		long end = this.windowEnd();
		long first = Math.max(this.history.oldest(), end - this.windowSize() + 1);
		long length = end - first + 1;
		if (length < 2) {
			return new double[0];
		}
		int points = (int) Math.min(SPARK_POINTS, length);
		double[] values = new double[points];
		for (int point = 0; point < points; point++) {
			long from = first + length * point / points;
			long to = first + length * (point + 1) / points;
			double sum = 0;
			int counted = 0;
			for (long sample = from; sample < Math.max(to, from + 1); sample++) {
				double sampleValue = value.applyAsDouble(sample);
				if (!Double.isNaN(sampleValue)) {
					sum += sampleValue;
					counted++;
				}
			}
			values[point] = counted == 0 ? Double.NaN : sum / counted;
		}
		return values;
	}

	private void renderTiles(final ThreadsSnapshot snapshot) {
		Map<WorkerPoolKind, Integer> byKind = new EnumMap<>(WorkerPoolKind.class);
		int threads = 0;
		for (ThreadsSnapshot.Pool pool : snapshot.pools()) {
			byKind.merge(pool.kind(), pool.workers().size(), Integer::sum);
			threads += pool.workers().size();
		}
		this.threadsValue.setText(String.valueOf(threads));
		List<String> parts = new ArrayList<>();
		byKind.forEach((kind, count) -> parts.add(count + " " + ThreadsFormat.kindLabel(kind)));
		double rate = 0;
		for (ThreadsHistory.PoolTrack track : this.history.pools().values()) {
			rate += this.history.throughput(track, track.lastSample);
		}
		if (snapshot.taskTracking() && !parts.isEmpty()) {
			parts.add(ThreadsTexts.get("tasksPerSecond", ThreadsFormat.rate(rate)));
		}
		this.threadsDetail.setText(parts.isEmpty() ? ThreadsTexts.get("tile.threads.none") : String.join(" · ", parts));

		long end = this.windowEnd();
		boolean any = this.history.count() > 0 && end >= 0;
		double[] occupancy = any ? this.spark(this::overallOccupancy) : new double[0];
		double sum = 0;
		int counted = 0;
		for (double value : occupancy) {
			if (!Double.isNaN(value)) {
				sum += value;
				counted++;
			}
		}
		this.averageValue.setText(counted == 0 ? "–" : ThreadsFormat.percent(sum / counted));
		this.averageDetail.setText(snapshot.taskTracking() ? ThreadsTexts.get("tile.average.tracked", this.rangeLabel())
				: ThreadsTexts.get("tile.average.untracked"));
		this.averageSpark.draw(occupancy, 1, this.palette.color("general"));

		// Peak: the busiest second of any pool over the window.
		String peakPool = null;
		double peak = -1;
		long first = Math.max(this.history.oldest(), end - this.windowSize() + 1);
		for (ThreadsHistory.PoolTrack pool : this.history.pools().values()) {
			for (long second = first; second + ThreadsHistory.PER_SECOND - 1 <= end; second += ThreadsHistory.PER_SECOND) {
				double secondSum = 0;
				for (long sample = second; sample < second + ThreadsHistory.PER_SECOND; sample++) {
					secondSum += pool.occupancy[(int) (sample % ThreadsHistory.CAPACITY)];
				}
				if (secondSum / ThreadsHistory.PER_SECOND > peak && pool.lastSample >= second) {
					peak = secondSum / ThreadsHistory.PER_SECOND;
					peakPool = pool.name;
				}
			}
		}
		this.peakValue.setText(peakPool == null ? "–" : ThreadsFormat.percent(peak));
		ThreadsSnapshot.Pool peakState = this.poolNamed(snapshot, peakPool);
		this.peakDetail.setText(peakPool == null ? ThreadsTexts.get("tile.peak.measuring") : peakState == null
				? ThreadsTexts.get("tile.peak.pool", this.poolPhrase(snapshot, peakPool))
				: ThreadsTexts.get("tile.peak.poolState", this.poolPhrase(snapshot, peakPool),
						ThreadsFormat.label(peakState.contention()).toLowerCase(SupervisorMessages.locale())));
		setStyle(this.peakTile, "sv-threads-tile-warn", peakState != null && peakState.contention() != Contention.FLUID);

		int queued = snapshot.pools().stream().mapToInt(ThreadsSnapshot.Pool::queuedTasks).sum();
		this.queuedValue.setText(String.valueOf(queued));
		double[] queue = any ? this.spark(sample -> this.totalQueued(sample)) : new double[0];
		this.queuedSpark.draw(queue, 0, this.palette.color("events"));
		ThreadsSnapshot.Pool busiestQueue = snapshot.pools().stream()
				.max((a, b) -> Integer.compare(a.queuedTasks(), b.queuedTasks())).orElse(null);
		String trend = queue.length < 4 ? "" : ThreadsTexts.get(queue[queue.length - 1] > queue[0] * 1.2 + 1 ? "tile.queued.rising"
				: queue[queue.length - 1] < queue[0] * 0.8 - 1 ? "tile.queued.falling" : "tile.queued.steady");
		this.queuedDetail.setText(busiestQueue == null || busiestQueue.queuedTasks() == 0
				? (trend.isEmpty() ? ThreadsTexts.get("tile.queued.empty") : trend)
				: ThreadsTexts.get("tile.queued.detail", trend, this.poolPhrase(snapshot, busiestQueue.name())));

		long rejected = 0;
		String rejecting = null;
		long most = 0;
		for (ThreadsHistory.PoolTrack pool : this.history.pools().values()) {
			if (!any) {
				break;
			}
			long from = Math.max(first, this.history.oldest());
			long count = pool.rejected[(int) (Math.min(end, pool.lastSample) % ThreadsHistory.CAPACITY)]
					- pool.rejected[(int) (from % ThreadsHistory.CAPACITY)];
			count = Math.max(0, count);
			rejected += count;
			if (count > most) {
				most = count;
				rejecting = pool.name;
			}
		}
		this.rejectionsValue.setText(String.valueOf(rejected));
		this.rejectionsDetail.setText(rejecting == null ? ThreadsTexts.get("tile.rejections.none", this.rangeLabel())
				: ThreadsTexts.get("tile.rejections.detail", this.poolPhrase(snapshot, rejecting)));
		setStyle(this.rejectionsTile, "sv-cockpit-tile-bad", rejected > 0);
	}

	/**
	 * How a tile names a pool: by its kind for the events and general pools, as the pool health does,
	 * by its own name otherwise - a dedicated pool, named after its queue, or a pool no longer observed.
	 */
	private String poolPhrase(final ThreadsSnapshot snapshot, final String name) {
		ThreadsSnapshot.Pool pool = this.poolNamed(snapshot, name);
		if (pool != null && pool.kind() == WorkerPoolKind.EVENTS) {
			return ThreadsTexts.get("health.pool.events");
		}
		if (pool != null && pool.kind() == WorkerPoolKind.GENERAL) {
			return ThreadsTexts.get("health.pool.general");
		}
		return ThreadsTexts.get("tile.pool", name);
	}

	private ThreadsSnapshot.Pool poolNamed(final ThreadsSnapshot snapshot, final String name) {
		for (ThreadsSnapshot.Pool pool : snapshot.pools()) {
			if (pool.name().equals(name)) {
				return pool;
			}
		}
		return null;
	}

	private void renderHealth(final ThreadsSnapshot snapshot) {
		long end = this.windowEnd();
		Map<ThreadActivity, Integer> counts = new EnumMap<>(ThreadActivity.class);
		int total = 0;
		for (ThreadsHistory.Track track : this.history.tracks().values()) {
			ThreadActivity activity = end < 0 || track.caller ? null : this.history.activity(track, end);
			if (activity != null) {
				counts.merge(activity, 1, Integer::sum);
				total++;
			}
		}
		for (ThreadActivity activity : ThreadActivity.values()) {
			int count = counts.getOrDefault(activity, 0);
			Region segment = this.healthSegments.get(activity);
			segment.setVisible(count > 0);
			segment.setManaged(count > 0);
			this.healthShares.put(activity, total == 0 ? 0 : (double) count / total);
			this.healthCounts.get(activity).setText(String.valueOf(count));
			boolean shown = activity != ThreadActivity.UNTRACKED || count > 0;
			this.healthLines.get(activity).setVisible(shown);
		}
		this.layoutHealthBar();
		this.renderPoolsHealth(snapshot);
		// The rows stay in place and only change their text: a row rebuilt at each update would
		// close the tooltip under the pointer.
		List<ThreadsSnapshot.LogEntry> log = snapshot.log();
		for (int index = 0; index < EVENT_ROWS; index++) {
			this.eventRows.get(index).show(index < log.size() ? log.get(index) : null);
		}
		this.eventsNone.setText(ThreadsTexts.get(snapshot.connected() ? "events.none" : "noContainer"));
		this.eventsNone.setVisible(log.isEmpty());
		this.eventsNone.setManaged(log.isEmpty());
		this.renderEventsFold(snapshot);
	}

	/** The fold of the recent events: how many, the worst one's color, the last one's time. */
	private void renderEventsFold(final ThreadsSnapshot snapshot) {
		List<ThreadsSnapshot.LogEntry> log = snapshot.log();
		int shown = Math.min(EVENT_ROWS, log.size());
		this.eventsFoldText.setText(ThreadsTexts.get(this.eventsList.isVisible() ? "recentEvents.unfolded"
				: "recentEvents.folded", shown));
		ThreadsSnapshot.Severity worst = null;
		for (ThreadsSnapshot.LogEntry entry : log.subList(0, shown)) {
			if (worst == null || entry.severity().compareTo(worst) > 0) {
				worst = entry.severity();
			}
		}
		this.eventsFoldDot.getStyleClass().removeIf(styleClass -> styleClass.startsWith("sv-threads-event-dot-"));
		if (worst != null) {
			this.eventsFoldDot.getStyleClass().add("sv-threads-event-dot-" + worst.name().toLowerCase(Locale.ROOT));
		}
		this.eventsFoldDot.setVisible(worst != null);
		this.eventsFoldTime.setText(log.isEmpty() ? "" : ThreadsFormat.time(log.get(0).timeMillis()));
	}

	/** One recent event: its time, a dot of its severity, its subject and its text, whole in tooltips. */
	private static final class EventRow {

		final HBox node;
		private final Label time = new Label();
		private final Region severity = new Region();
		private final Label subject = new Label();
		private final Label text = new Label();
		private final Tooltip subjectTip = Tooltips.install(this.subject, "");
		private final Tooltip textTip = Tooltips.install(this.text, "");

		EventRow() {
			this.time.getStyleClass().add("sv-cockpit-mono-muted");
			this.time.setMinWidth(Region.USE_PREF_SIZE);
			this.severity.getStyleClass().add("sv-threads-event-dot");
			this.subject.getStyleClass().add("sv-threads-event-subject");
			this.subject.setMinWidth(0);
			this.text.getStyleClass().add("sv-threads-event-text");
			this.text.setMinWidth(0);
			VBox what = new VBox(1, this.subject, this.text);
			what.setMinWidth(0);
			HBox.setHgrow(what, Priority.ALWAYS);
			this.node = new HBox(8, this.time, this.severity, what);
			this.node.setAlignment(Pos.TOP_LEFT);
			this.node.getStyleClass().add("sv-threads-event");
		}

		/** @param entry the event, {@code null} for none: the row is then left out */
		void show(final ThreadsSnapshot.LogEntry entry) {
			this.node.setVisible(entry != null);
			this.node.setManaged(entry != null);
			if (entry == null) {
				return;
			}
			this.time.setText(ThreadsFormat.time(entry.timeMillis()));
			this.severity.getStyleClass().removeIf(styleClass -> styleClass.startsWith("sv-threads-event-dot-"));
			this.severity.getStyleClass().add("sv-threads-event-dot-" + entry.severity().name().toLowerCase(Locale.ROOT));
			String subjectText = ThreadsTexts.of(entry.subject());
			ThreadsViewImpl.show(this.subject, this.subjectTip, subjectText);
			// One line, its class names shortened; the whole text in the tooltip.
			String whole = ThreadsTexts.of(entry.text());
			this.text.setText(shortClassNames(whole));
			this.textTip.setText(whole);
		}
	}

	/**
	 * @return the text with each qualified class name cut to its simple name, an inner class's
	 *         own: {@code org.x.AsyncManagerImpl$WorkerPool@58fe} reads {@code WorkerPool@58fe}
	 */
	static String shortClassNames(final String text) {
		return text.replaceAll("\\b(?:[a-z_][\\w]*\\.)+(?:[A-Za-z_][\\w]*\\$)*([A-Za-z_][\\w]*)", "$1");
	}

	/** Each shown segment takes its share of the bar's width, gaps aside. */
	private void layoutHealthBar() {
		long shown = this.healthSegments.values().stream().filter(Node::isVisible).count();
		double width = Math.max(0, this.healthBar.getWidth() - this.healthBar.getSpacing() * Math.max(0, shown - 1));
		for (Map.Entry<ThreadActivity, Region> entry : this.healthSegments.entrySet()) {
			double share = this.healthShares.getOrDefault(entry.getKey(), 0.0);
			entry.getValue().setPrefWidth(share == 0 ? 0 : Math.max(4, Math.floor(width * share)));
		}
	}

	private void renderLanes(final ThreadsSnapshot snapshot) {
		long end = this.windowEnd();
		int size = this.windowSize();
		List<Node> ticks = new ArrayList<>();
		for (int step = 0; step <= 4; step++) {
			int secondsAgo = this.rangeSeconds - this.rangeSeconds * step / 4;
			Label tick = new Label(step == 4 ? ThreadsTexts.get("now")
					: secondsAgo >= 60 ? ThreadsTexts.get("ago.min", secondsAgo / 60) : ThreadsTexts.get("ago.s", secondsAgo));
			tick.getStyleClass().add("sv-cockpit-axis");
			ticks.add(tick);
			if (step < 4) {
				Region gap = new Region();
				HBox.setHgrow(gap, Priority.ALWAYS);
				ticks.add(gap);
			}
		}
		this.axis.getChildren().setAll(ticks);

		boolean grouped = this.byComponent && snapshot.taskTracking();
		this.byComponentButton.setDisable(!snapshot.taskTracking());
		List<String> order = new ArrayList<>();
		this.threadColumn.setText(ThreadsTexts.get(grouped ? "load.column" : "threadColumn"));
		if (this.lanesSub != null) {
			this.lanesSub.setText(ThreadsTexts.get(grouped ? "load.heading" : "lanes.sub"));
			this.lanesLegend.setVisible(!grouped);
		}
		if (grouped) {
			order.add(LOAD_KEY);
		} else {
			for (ThreadsSnapshot.Pool pool : snapshot.pools()) {
				String key = "pool:" + pool.name();
				order.add(key);
				this.sections.computeIfAbsent(key, ignored -> new PoolSection(this)).update(pool, this.history,
						end - size + 1);
			}
			if (snapshot.connected()) {
				order.add(CALLERS_KEY);
				this.updateCallers(snapshot.callers(), this.sections.computeIfAbsent(CALLERS_KEY,
						ignored -> new PoolSection(this)));
			}
		}
		this.sections.keySet().retainAll(order);
		if (!order.equals(this.sectionOrder)) {
			this.sectionOrder = order;
			List<Node> nodes = new ArrayList<>();
			for (String name : order) {
				nodes.add(name.equals(LOAD_KEY) ? this.loadRows.root : this.sections.get(name).root);
			}
			if (nodes.isEmpty()) {
				Label none = new Label(ThreadsTexts.get(snapshot.connected() ? "lanes.noPool" : "noContainer"));
				none.getStyleClass().add("sv-cockpit-muted");
				nodes.add(none);
			}
			this.lanes.getChildren().setAll(nodes);
		}
		this.drawLanes();
		this.renderReadout(end);
	}

	private static final String CALLERS_KEY = "callers";

	/** The rows of the load per component, in place of the sections while grouped by component. */
	private static final String LOAD_KEY = "load";

	/** The calling threads' section: their lanes while data flows are on, a way to turn them on otherwise. */
	private void updateCallers(final ThreadsSnapshot.Callers callers, final PoolSection section) {
		int count = callers.threads().size();
		String detail = !callers.dataFlows() ? ThreadsTexts.get("callers.flowsOff")
				: ThreadsTexts.get("callers.detail", ThreadsTexts.count("threads", count),
						ThreadsFormat.rate(this.history.callRate(this.history.count() - 1)));
		section.updateGroup(ThreadsTexts.get("callers.title"), ThreadsTexts.get("callers.badge"), detail,
				callers.threads());
		section.setAction(callers.dataFlows() ? null : ThreadsTexts.get("callers.turnOn"),
				() -> this.actionManagerServices.executeAction(ContainerManagerActionKey.SetFlowsEnabled, true));
	}

	void drawLanes() {
		long end = this.windowEnd();
		if (LOAD_KEY.equals(this.sectionOrder.isEmpty() ? null : this.sectionOrder.get(0))) {
			this.loadRows.draw(this.history, end, this.windowSize(), this.hoverSample, this.palette);
		}
		for (PoolSection section : this.sections.values()) {
			section.draw(this.history, end, this.windowSize(), this.hoverSample, this.palette, this.pausedAt >= 0,
					this.moment);
		}
	}

	/** The pointer over a component's load: the readout tells that component's workers then. */
	void hoverComponent(final String component, final long sample) {
		this.hoverComponent = sample < 0 ? null : component;
		this.hover(sample);
	}

	void hover(final long sample) {
		this.hoverSample = sample;
		this.drawLanes();
		this.renderReadout(sample >= 0 ? sample : this.windowEnd());
	}

	/** Tunes a pool's workers live, from its header - see {@link PoolTuningPopup}. */
	void tunePoolWorkers(final WorkerPoolKind kind, final int count) {
		this.actionManagerServices.executeAction(ThreadsActionKey.SetPoolWorkers, kind, count);
	}

	/** Tunes a pool's queue capacity live, from its header - see {@link PoolTuningPopup}. */
	void tunePoolQueueCapacity(final WorkerPoolKind kind, final int capacity) {
		this.actionManagerServices.executeAction(ThreadsActionKey.SetPoolQueueCapacity, kind, capacity);
	}

	/** A thread's row clicked outside its lane: the thread, with no moment picked. */
	void select(final long threadId) {
		this.moment = null;
		this.showThread(threadId);
	}

	/**
	 * A thread's lane clicked: the thread, and the moment under the click; clicked again, the
	 * moment is let go.
	 */
	void pickMoment(final long threadId, final long from, final long to) {
		boolean again = this.moment != null && this.moment[0] == threadId && from <= this.moment[2]
				&& to >= this.moment[1];
		this.moment = again ? null : new long[] { threadId, from, to };
		this.showThread(threadId);
		this.drawLanes();
	}

	private void showThread(final long threadId) {
		this.selectedThreadId = threadId;
		this.inspectedThreadId = -2;
		this.renderInspector(this.latest == null ? ThreadsSnapshot.EMPTY : this.latest);
		for (PoolSection section : this.sections.values()) {
			section.select(threadId);
		}
	}

	private void renderReadout(final long sample) {
		if (this.history.count() == 0 || sample < this.history.oldest()) {
			this.lanesReadout.setText("");
			return;
		}
		if (this.hoverComponent != null && this.sectionOrder.contains(LOAD_KEY)) {
			Map<WorkerPoolKind, Double> shares = this.history.componentSharesAt(this.hoverComponent, sample);
			String pools = String.join(" · ", shares.entrySet().stream()
					.map(entry -> ThreadsFormat.kindLabel(entry.getKey()) + " " + ThreadsFormat.share(entry.getValue()))
					.toList());
			this.lanesReadout.setText(ThreadsTexts.get(pools.isEmpty() ? "load.readout.none" : "load.readout",
					ThreadsFormat.time(this.history.timeOf(sample)),
					this.hoverComponent.equals(ThreadsHistory.UNKNOWN_COMPONENT) ? ThreadsTexts.get("load.unknown")
							: this.hoverComponent,
					pools));
			return;
		}
		Map<ThreadActivity, Integer> counts = new EnumMap<>(ThreadActivity.class);
		for (ThreadsHistory.Track track : this.history.tracks().values()) {
			ThreadActivity activity = this.history.activity(track, sample);
			if (activity != null) {
				counts.merge(activity, 1, Integer::sum);
			}
		}
		this.lanesReadout.setText(ThreadsTexts.get("readout", ThreadsFormat.time(this.history.timeOf(sample)),
				counts.getOrDefault(ThreadActivity.RUNS, 0), counts.getOrDefault(ThreadActivity.WAITS, 0),
				counts.getOrDefault(ThreadActivity.BLOCKED, 0)));
	}

	/** Asks before interrupting the deadlocked threads: it may free none of them. */
	private void confirmInterrupt() {
		if (this.latest == null || this.latest.deadlocks().isEmpty()) {
			return;
		}
		Set<Long> ids = new HashSet<>();
		this.latest.deadlocks().forEach(deadlock -> ids.addAll(deadlock.threadIds()));
		Alert alert = new Alert(Alert.AlertType.WARNING);
		alert.setTitle(ThreadsTexts.get("deadlock.interrupt"));
		alert.setHeaderText(ThreadsTexts.get("deadlock.interrupt"));
		Label message = new Label(ThreadsTexts.get("deadlock.interrupt.confirm"));
		message.setWrapText(true);
		message.setPrefWidth(420);
		alert.getDialogPane().setContent(message);
		ButtonType accept = new ButtonType(ThreadsTexts.get("deadlock.interrupt.accept"),
				ButtonBar.ButtonData.OK_DONE);
		ButtonType cancel = new ButtonType(ThreadsTexts.get("deadlock.interrupt.cancel"),
				ButtonBar.ButtonData.CANCEL_CLOSE);
		alert.getButtonTypes().setAll(cancel, accept);
		if (this.root != null && this.root.getScene() != null) {
			alert.initOwner(this.root.getScene().getWindow());
			alert.getDialogPane().getStylesheets().setAll(this.root.getScene().getStylesheets());
		}
		if (alert.showAndWait().filter(answer -> answer == accept).isPresent()) {
			this.actionManagerServices.executeAction(ThreadsActionKey.InterruptThreads, ids);
		}
	}

	/** @return how a deadlock's threads wait on each other, in words */
	private static String waits(final ThreadsSnapshot.Deadlock deadlock) {
		return String.join("; ", deadlock.waits().stream().map(ThreadsTexts::of).toList());
	}

	// --- Inspector ---

	/** Builds the inspector's sections once: they are updated in place, so that its button keeps its clicks. */
	private void buildInspector() {
		this.inspectorName.getStyleClass().add("sv-threads-inspector-name");
		this.inspectorName.setWrapText(true);
		this.inspectorPool.getStyleClass().add("sv-threads-chip");
		this.inspectorState.getStyleClass().add("sv-threads-state-badge");
		this.inspectorState.setMinWidth(Region.USE_PREF_SIZE);
		this.inspectorHint.setWrapText(true);
		// Always two lines: side by side, the badges sometimes fit on one line and sometimes
		// wrapped, which made the sections below jump with the thread's state.
		VBox chips = new VBox(6, this.inspectorPool, this.inspectorState);
		chips.setFillWidth(false);
		chips.setAlignment(Pos.CENTER_LEFT);
		VBox identity = section(List.of(eyebrow(ThreadsTexts.get("inspector.selected")), this.inspectorHint, this.inspectorName, chips));
		// Every section keeps its height whatever it shows - a fixed number of lines, longer texts
		// ending with "…" and whole in their tooltips - so that nothing moves as the thread works.
		for (int index = 0; index < CALL_ROWS; index++) {
			CallRow row = new CallRow();
			this.callRows.add(row);
			this.callStack.getChildren().add(row.node);
		}
		for (int index = 0; index < ACTIVITY_ROWS; index++) {
			ActivityRow row = new ActivityRow();
			this.activityRows.add(row);
			this.recentActivity.getChildren().add(row.node);
		}
		this.inspectorDetails.getChildren().setAll(
				section(List.of(eyebrow(ThreadsTexts.get("inspector.occupancy")), this.threadSpark)),
				section(List.of(this.taskEyebrow, this.taskText, this.taskDetail, this.lockText)),
				section(List.of(eyebrow(ThreadsTexts.get("inspector.callStack")), this.callStack, this.callHint)),
				section(List.of(eyebrow(ThreadsTexts.get("inspector.cpu")), this.cpuText, this.cpuDetail)),
				section(List.of(eyebrow(ThreadsTexts.get("inspector.recentActivity")), this.recentActivity)));
		Button toggle = new Button(ThreadsTexts.get("show"));
		toggle.getStyleClass().add("sv-threads-button");
		Tooltips.install(toggle, ThreadsTexts.get("javaStack.tip"));
		toggle.setOnAction(event -> {
			this.javaFramesShown = !this.javaFramesShown;
			toggle.setText(ThreadsTexts.get(this.javaFramesShown ? "hide" : "show"));
			this.showStack();
		});
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox stackHeader = new HBox(eyebrow(ThreadsTexts.get("inspector.javaStack")), spacer, toggle);
		stackHeader.setAlignment(Pos.CENTER_LEFT);
		// Last: its frames come and go with the stack, and nothing lies below them.
		this.inspectorDetails.getChildren().add(section(List.of(stackHeader, this.stackHint, this.stackFrames)));
		this.inspector.getChildren().setAll(identity, this.inspectorDetails);
		this.showStack();
	}

	/**
	 * What the shared Record button records on this panel: with a moment picked on a lane, the
	 * components its thread worked for then; with a thread alone, the last one it worked for.
	 */
	private void renderRecordScope(final ThreadsHistory.Track track) {
		if (this.moment != null && (track == null || this.moment[0] != track.threadId
				|| this.moment[2] < this.history.oldest())) {
			// Its thread is gone, or the moment is older than the samples kept.
			this.moment = null;
		}
		Set<Class<?>> scope;
		if (track == null) {
			scope = Set.of();
		} else if (this.moment != null) {
			scope = this.history.components(track, this.moment[1], this.moment[2]);
		} else {
			scope = track.lastComponent == null ? Set.of() : Set.of(track.lastComponent);
		}
		if (!scope.equals(this.recordScope)) {
			this.recordScope = scope;
			this.actionManagerServices.executeAction(ThreadsActionKey.SetRecordScope, scope);
		}
	}

	private void renderInspector(final ThreadsSnapshot snapshot) {
		ThreadsHistory.Track track = this.selectedThreadId < 0 ? null : this.history.track(this.selectedThreadId);
		this.renderRecordScope(track);
		boolean shown = track != null;
		this.inspectorHint.setVisible(!shown);
		this.inspectorHint.setManaged(!shown);
		for (Node node : List.of(this.inspectorName, this.inspectorPool.getParent(), this.inspectorDetails)) {
			node.setVisible(shown);
			node.setManaged(shown);
		}
		if (!shown) {
			this.inspectedThreadId = -1;
			return;
		}
		ThreadsSnapshot.Worker worker = track.latest;
		long end = this.windowEnd();
		ThreadActivity activity = this.history.activity(track, Math.min(end, track.lastSample));
		this.inspectorName.setText(track.name);
		ThreadsSnapshot.Pool pool = this.poolNamed(snapshot, track.poolName);
		this.inspectorPool.setText(track.caller ? ThreadsTexts.get("callers.title")
				: pool == null ? track.poolName : pool.title().replace(" · ", " › "));
		this.inspectorState.setText(activity == null ? ThreadsTexts.get("ended") : ThreadsFormat.label(activity));
		this.inspectorState.getStyleClass().removeIf(styleClass -> styleClass.startsWith("sv-threads-state-")
				&& !styleClass.equals("sv-threads-state-badge"));
		if (activity != null) {
			this.inspectorState.getStyleClass().add("sv-threads-state-" + ThreadsFormat.styleSuffix(activity));
		}

		boolean tracked = track.caller || snapshot.taskTracking();
		boolean inTask = tracked && worker != null && worker.taskNanos() >= 0;
		this.taskEyebrow.setText(ThreadsTexts.get(track.caller ? "inspector.currentCall" : "inspector.currentTask"));
		show(this.taskText, this.taskTip, inTask ? (worker.taskLabel() == null ? ThreadsTexts.get("task.a") : worker.taskLabel())
				: ThreadsTexts.get(track.caller ? "task.outsideCalls" : tracked ? "task.none" : "task.unknown"));
		this.taskDetail.setText(!inTask ? "" : worker.taskComponent() == null
				? ThreadsTexts.get("task.running", ThreadsFormat.duration(worker.taskNanos()))
				: ThreadsTexts.get("task.runningFor", ThreadsFormat.duration(worker.taskNanos()),
						worker.taskComponent().getSimpleName()));
		boolean lock = worker != null && worker.lockName() != null;
		show(this.lockText, this.lockTip, !lock ? "" : ThreadsTexts.get((activity == ThreadActivity.BLOCKED ? "lock.blocked" : "lock.waits")
				+ (worker.lockOwnerName() == null ? "" : "Held"), worker.lockName(), worker.lockOwnerName()));

		long now = track.cpuNanos[(int) (track.lastSample % ThreadsHistory.CAPACITY)];
		long secondAgo = track.lastSample - ThreadsHistory.PER_SECOND;
		long before = secondAgo >= this.history.oldest() ? track.cpuNanos[(int) (secondAgo % ThreadsHistory.CAPACITY)] : -1;
		if (now < 0) {
			this.cpuText.setText(ThreadsTexts.get("cpu.notMeasured"));
			this.cpuDetail.setText("");
		} else {
			this.cpuText.setText(before < 0 ? "–" : ThreadsTexts.get("cpu.lastSecond", Math.round(Math.max(0, now - before) / 1e7)));
			this.cpuDetail.setText(ThreadsTexts.get("cpu.since", ThreadsFormat.duration(now))
					+ (activity == ThreadActivity.RUNS && before >= 0 && now - before < 50_000_000L
							? "\n" + ThreadsTexts.get("cpu.io") : ""));
		}

		double[] values = new double[60];
		long last = track.lastSample;
		for (int point = 0; point < 60; point++) {
			long from = last - (60 - point) * 5L * ThreadsHistory.PER_SECOND + 1;
			double busy = 0;
			int counted = 0;
			for (long sample = from; sample < from + 5L * ThreadsHistory.PER_SECOND; sample++) {
				if (sample >= this.history.oldest() && sample <= last) {
					ThreadActivity sampled = this.history.activity(track, sample);
					if (sampled != null) {
						counted++;
						float share = this.history.busy(track, sample);
						busy += share >= 0 ? Math.max(share, sampled == ThreadActivity.BLOCKED ? 1 : 0)
								: sampled.isBusy() ? 1 : 0;
					}
				}
			}
			values[point] = counted == 0 ? Double.NaN : busy / counted;
		}
		this.threadSpark.draw(values, 1, LaneChart.color(activity == null || !activity.isBusy() ? ThreadActivity.RUNS
				: activity, this.palette));

		if (this.inspectedThreadId != track.threadId) {
			this.stack = ThreadStack.NONE;
			this.showStack();
			this.stackRequestMillis = 0;
		}
		// The selected thread's stack, read again every second while live.
		long nowMillis = System.currentTimeMillis();
		if (this.pausedAt < 0 && nowMillis - this.stackRequestMillis >= 1000 && snapshot.connected()) {
			this.stackRequestMillis = nowMillis;
			this.actionManagerServices.executeAction(ThreadsActionKey.ReadStack, track.threadId);
		}
		// The rows stay in place and only change their text, the newest first.
		List<ThreadsHistory.Activity> recent = new ArrayList<>(track.recent);
		for (int index = 0; index < ACTIVITY_ROWS; index++) {
			this.activityRows.get(index).show(index < recent.size() ? recent.get(index) : null);
		}
		this.inspectedThreadId = track.threadId;
	}

	/** The selected thread's component calls, and its Java frames when asked for. */
	private void showStack() {
		ThreadStack shown = this.stack;
		// A fixed number of rows: beyond them, the last row counts the calls left out.
		List<String> calls = shown.componentCalls();
		for (int index = 0; index < CALL_ROWS; index++) {
			CallRow row = this.callRows.get(index);
			if (index == CALL_ROWS - 1 && calls.size() > CALL_ROWS) {
				row.show(ThreadsTexts.get("callStack.more", calls.size() - CALL_ROWS + 1), false, false);
			} else {
				row.show(index < calls.size() ? calls.get(index) : null, index == 0, true);
			}
		}
		this.callHint.setText(ThreadsTexts.get(shown.threadId() < 0 ? "callStack.reading"
				: shown.frames().isEmpty() ? "callStack.ended"
						: shown.componentCalls().isEmpty() ? "callStack.none" : "callStack.order"));
		this.stackFrames.getChildren().clear();
		this.stackHint.setVisible(this.javaFramesShown);
		this.stackHint.setManaged(this.javaFramesShown);
		if (!this.javaFramesShown) {
			return;
		}
		List<String> frames = shown.frames();
		this.stackHint.setText(frames.isEmpty() ? ThreadsTexts.get("javaStack.none") : ThreadsTexts.get("javaStack.count", frames.size()));
		for (String frame : frames.subList(0, Math.min(24, frames.size()))) {
			Label line = new Label(shortFrame(frame));
			line.getStyleClass().add("sv-threads-frame");
			Tooltips.install(line, frame);
			this.stackFrames.getChildren().add(line);
		}
		if (frames.size() > 24) {
			this.stackFrames.getChildren().add(muted(ThreadsTexts.get("javaStack.more", frames.size() - 24)));
		}
	}

	/**
	 * A stack frame as {@code Class.method(File.java:12)}: without its module, class loader and
	 * package, kept in its tooltip.
	 */
	static String shortFrame(final String frame) {
		String rest = frame.substring(frame.lastIndexOf('/', Math.max(0, frame.indexOf('('))) + 1);
		int open = rest.indexOf('(');
		String qualified = open < 0 ? rest : rest.substring(0, open);
		String location = open < 0 ? "" : rest.substring(open);
		int method = qualified.lastIndexOf('.');
		int type = method <= 0 ? -1 : qualified.lastIndexOf('.', method - 1);
		return qualified.substring(type + 1) + location;
	}

	// --- Report ---

	private String report() {
		ThreadsSnapshot snapshot = this.latest == null ? ThreadsSnapshot.EMPTY : this.latest;
		StringBuilder report = new StringBuilder(ThreadsTexts.get("report.title", ThreadsFormat.time(snapshot.timeMillis())))
				.append('\n')
				.append(ThreadsTexts.get("report.tracking",
						ThreadsTexts.get(snapshot.taskTracking() ? "report.tracking.on" : "report.tracking.off")))
				.append('\n');
		for (ThreadsSnapshot.Deadlock deadlock : snapshot.deadlocks()) {
			report.append(ThreadsTexts.get("report.deadlock", waits(deadlock))).append('\n');
		}
		for (ThreadsSnapshot.Pool pool : snapshot.pools()) {
			List<DurationHistogram> window = this.history.pool(pool.name()) == null ? List.of(DurationHistogram.EMPTY, DurationHistogram.EMPTY)
					: this.history.histogramsBetween(this.history.pool(pool.name()), this.windowEnd() - this.windowSize() + 1);
			report.append('\n').append(ThreadsTexts.get("report.pool", pool.title(), ThreadsFormat.kindBadge(pool.kind()),
					pool.workers().size(), pool.targetWorkers(), pool.queuedTasks(), pool.queueCapacity(),
					ThreadsFormat.percent(pool.occupancy()), ThreadsFormat.label(pool.contention()), pool.rejectedTasks(),
					ThreadsFormat.duration(window.get(0).percentileNanos(95)),
					ThreadsFormat.duration(window.get(1).percentileNanos(95)))).append('\n');
			for (ThreadsSnapshot.Worker worker : pool.workers()) {
				report.append("  ").append(ThreadsTexts.get("report.worker", worker.name(), ThreadsFormat.label(worker.activity()),
						worker.jvmState()));
				if (worker.taskNanos() >= 0) {
					report.append("  ").append(ThreadsTexts.get("report.task", worker.taskLabel() == null ? "?" : worker.taskLabel(),
							ThreadsFormat.duration(worker.taskNanos())));
					if (worker.taskComponent() != null) {
						report.append(' ').append(ThreadsTexts.get("report.taskComponent", worker.taskComponent().getSimpleName()));
					}
				}
				if (worker.lockName() != null) {
					report.append("  ").append(ThreadsTexts.get("report.lock", worker.lockName()));
					if (worker.lockOwnerName() != null) {
						report.append(' ').append(ThreadsTexts.get("report.lock.held", worker.lockOwnerName()));
					}
				}
				report.append('\n');
			}
		}
		return report.toString();
	}

	// --- Helpers ---

	static void setStyle(final Node node, final String styleClass, final boolean on) {
		node.getStyleClass().remove(styleClass);
		if (on) {
			node.getStyleClass().add(styleClass);
		}
	}

	/**
	 * Gives a label the height of a number of lines, whatever its text, so that what lies below it
	 * never moves; a longer text ends with "…".
	 */
	static Label fixedLines(final Label label, final int lines) {
		label.setWrapText(lines > 1);
		label.setTextOverrun(OverrunStyle.ELLIPSIS);
		Runnable fit = () -> {
			Text probe = new Text("X");
			probe.setFont(label.getFont());
			// A pixel of slack per line: a label one hair too short wraps no more and ends with "…".
			double height = Math.ceil((probe.getLayoutBounds().getHeight() + 1) * lines + label.getLineSpacing() * (lines - 1)
					+ label.getInsets().getTop() + label.getInsets().getBottom());
			label.setMinHeight(height);
			label.setPrefHeight(height);
			label.setMaxHeight(height);
		};
		fit.run();
		label.fontProperty().addListener((observable, before, font) -> fit.run());
		label.insetsProperty().addListener((observable, before, insets) -> fit.run());
		return label;
	}

	/** Shows a text in a label, and whole in its tooltip, which an empty text goes without. */
	private static void show(final Label label, final Tooltip tip, final String text) {
		label.setText(text);
		tip.setText(text);
		label.setTooltip(text.isBlank() ? null : tip);
	}

	/** One row of the selected thread's component calls: a dot, the innermost one filled, and the call. */
	private static final class CallRow {

		final HBox node;
		private final Region dot = new Region();
		private final Label call = fixedLines(new Label(), 1);
		private final Tooltip tip = Tooltips.install(this.call, "");

		CallRow() {
			this.call.getStyleClass().add("sv-threads-call");
			this.node = new HBox(10, this.dot, this.call);
			this.node.setAlignment(Pos.CENTER_LEFT);
		}

		/**
		 * @param text    the call, {@code null} for an empty row
		 * @param current whether it is the innermost call
		 * @param dotted  whether the row shows a dot
		 */
		void show(final String text, final boolean current, final boolean dotted) {
			ThreadsViewImpl.show(this.call, this.tip, text == null ? " " : text);
			this.dot.getStyleClass().setAll(current ? "sv-threads-call-current" : "sv-threads-call-dot");
			this.dot.setVisible(text != null && dotted);
		}
	}

	/** One line of the selected thread's recent activity: its time and what happened. */
	private static final class ActivityRow {

		final HBox node;
		private final Label time = new Label();
		private final Label text = fixedLines(new Label(), 1);
		private final Tooltip tip = Tooltips.install(this.text, "");

		ActivityRow() {
			this.time.getStyleClass().add("sv-cockpit-mono-muted");
			this.time.setMinWidth(Region.USE_PREF_SIZE);
			this.node = new HBox(10, this.time, this.text);
			this.node.getStyleClass().add("sv-cockpit-row");
		}

		void show(final ThreadsHistory.Activity activity) {
			this.time.setText(activity == null ? " " : ThreadsFormat.time(activity.timeMillis()));
			ThreadsViewImpl.show(this.text, this.tip, activity == null ? " " : activity.text());
		}
	}

	private static VBox section(final List<Node> nodes) {
		VBox section = new VBox(8);
		section.getChildren().addAll(nodes);
		section.getStyleClass().add("sv-threads-inspector-section");
		return section;
	}

	private static Label eyebrow(final String text) {
		Label label = new Label(text);
		label.getStyleClass().add("sv-eyebrow");
		return label;
	}

	private static Label mono(final String text) {
		Label label = new Label(text);
		label.getStyleClass().add("sv-cockpit-mono");
		label.setWrapText(true);
		return label;
	}

	static Label muted(final String text) {
		Label label = new Label(text);
		label.getStyleClass().add("sv-cockpit-muted");
		label.setWrapText(true);
		return label;
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
		label.setWrapText(true);
		return label;
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

	private static Node legendItem(final String text, final String swatchClass) {
		Region swatch = new Region();
		swatch.getStyleClass().addAll("sv-cockpit-legend-swatch", swatchClass);
		Label label = new Label(text);
		label.getStyleClass().add("sv-cockpit-muted");
		HBox item = new HBox(6, swatch, label);
		item.setAlignment(Pos.CENTER_LEFT);
		return item;
	}
}
