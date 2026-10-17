/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.view.impl;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import org.opales.covalent.supervision.SupervisionThreads;
import org.opales.covalent.supervision.componentview.internal.Snapshots;
import org.opales.covalent.supervision.componentview.internal.CollapsibleSide;
import org.opales.covalent.supervision.componentview.internal.Toolbox;
import org.opales.covalent.supervision.componentview.internal.Tooltips;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.PannableCanvas;
import org.opales.covalent.hmi.action.ActionConsumerServices;
import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.Graph;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.SequenceDiagram;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.SequenceDiagram.ActorCellImpl;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.SequenceDiagram.ActorCell;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.SequenceDiagram.MessageEdge;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.SequenceDiagram.MessageStyle;
import org.opales.covalent.supervision.componentdiagram.interaction.ComponentDiagramToolActionKey;
import org.opales.covalent.supervision.componentview.internal.diagram.edges.MessageEdgeImpl;
import org.opales.covalent.supervision.componentview.sequence.SequenceTraceMessage;
import org.opales.covalent.supervision.observed.CallOutcome;
import org.opales.covalent.supervision.sequencediagram.SequenceLog;
import org.opales.covalent.supervision.sequencediagram.interaction.SequenceDiagramActionKey;
import org.opales.covalent.supervision.sequencediagram.interaction.SequenceDiagramActionKey.Role;
import org.opales.covalent.supervision.sequencediagram.interaction.SequenceRecordingActionKey;
import org.opales.covalent.supervision.sequencediagram.controller.RecordingStateEvents;
import org.opales.covalent.supervision.sequencediagram.model.SequenceDiagramEvents;
import org.opales.covalent.supervision.sequencediagram.model.SequenceDiagramModelReadServices;
import org.opales.covalent.supervision.sequencediagram.view.SequenceDiagramView;
import org.opales.covalent.supervision.sequencediagram.view.SequenceDiagramViewServices;
import org.opales.covalent.supervision.sequencediagram.view.TimelineStyle;
import org.opales.covalent.supervision.supervisionhmi.view.FxThread;
import org.opales.covalent.supervision.supervisionhmi.view.PanelHostingServices;
import org.opales.covalent.supervision.supervisionhmi.view.RailEntry;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisorTabDescriptorAdapter;
import org.opales.covalent.supervision.supervisionhmi.view.ToolbarFunction;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;

/**
 * SequenceDiagram's View: one diagram actor/message canvas per recording
 * session, opened on {@code onSessionCreated} and closed for good on
 * {@code onSessionClosed} - renders what {@link SequenceDiagramEvents}
 * announces, reading the messages of a session through
 * {@code SequenceDiagramModelReadServices}, and reports every gesture to
 * {@code SequenceDiagramInteraction} through
 * {@link ActionConsumerServices} using a {@link SequenceDiagramActionKey}
 * it constructs itself for that session (structural equality - see that
 * class's javadoc).
 * <p>
 * There is no blocking "recording in progress" overlay covering the graph
 * while the session records: the shared toolbar's Record toggle (owned by
 * {@code ComponentDiagram}) is the sole record control, and this panel keeps a
 * live message counter in its own toolbar. The tab's title does not carry the
 * running message count.
 * <p>
 * Live drawing: each session draws its messages as they arrive only while
 * its own "Live drawing" toggle (defaulted from {@code
 * SequenceDiagramPreferenceActionKey#ToggleDynamicObservationDefault}) is
 * on - a deliberate, explicit user choice, never automatic. While it is
 * off, the session keeps every message, its counters and its timeline up
 * to date, and draws everything it kept as soon as the toggle is back on
 * or the recording stops. A recording session keeps drawing while its tab is hidden.
 */
@Implementation
public class SequenceDiagramViewImpl
		implements SequenceDiagramView, SequenceDiagramViewServices, SequenceDiagramEvents, RecordingStateEvents {

	/** Creates the View; the container injects the services and the producer of events it uses. */
	public SequenceDiagramViewImpl() {
		// Nothing to initialize.
	}

	private static final double TIMELINE_STEP = 55.0;
	private static final double TIMELINE_BOTTOM_PADDING = 90.0;

	/** How many actors Recenter fits into the view's width, at most. */
	private static final int RECENTERED_ACTORS = 6;

	/** Room right of the last fitted actor, in canvas units. */
	private static final double RECENTER_MARGIN = 24;

	/** Opens the live log's folder: a folder outline. */
	private static final String ICON_FOLDER = "M1.5 4 V12.5 H14.5 V5.5 H7.5 L6 4 Z";

	/** The Save log tool: an arrow down onto a tray. */
	private static final String ICON_SAVE_LOG = "M8 2 V10 M4.5 6.5 L8 10 L11.5 6.5 M2.5 13.5 H13.5";

	/** How many of a method's latest calls the selected message's duration history covers. */
	private static final int DURATION_HISTORY = 80;

	private static final double SPARKLINE_WIDTH = 296;
	private static final double SPARKLINE_HEIGHT = 60;

	/** The message kinds the band filters on: {@code SequenceTraceMessage.Facts#kind()}, style suffix. */
	private static final String[][] KINDS = {
			{ "Service", "service" },
			{ "Event", "event" },
			{ "Parameter", "parameter" },
			{ "Action", "action" } };

	@UsedServices
	private ActionConsumerServices actionManagerServices;

	@UsedServices
	private PanelHostingServices useHostingServices;

	@UsedServices
	private SequenceDiagramModelReadServices useSequenceDiagramModelReadServices;

	private final Map<ViewKey, SessionPanel> sessions = new HashMap<>();

	/** Live log states told before their session's panel exists: the file and whether it is written. */
	private final Map<ViewKey, File> pendingLiveLogs = new HashMap<>();

	/** The "live drawing" preference - applied to every session created from now on. */
	private boolean defaultAnimationActive = true;

	/** The "timeline style" preference, applied to every session. */
	private TimelineStyle timelineStyle = TimelineStyle.SWITCH;

	// --- SequenceDiagramViewServices ---

	@Override
	public void setZoomScale(final ViewKey sessionKey, final double scale) {
		FxThread.run(() -> {
			SessionPanel session = this.sessions.get(sessionKey);
			if (session != null) {
				session.setZoomScale(scale);
			}
		});
	}

	@Override
	public void setDefaultAnimationActive(final boolean active) {
		FxThread.run(() -> {
			this.defaultAnimationActive = active;
		});
	}

	@Override
	public void setTimelineStyle(final TimelineStyle style) {
		FxThread.run(() -> {
			this.timelineStyle = style;
			for (SessionPanel session : this.sessions.values()) {
				session.timeline.setPresentation(style);
			}
		});
	}

	@Override
	public void setAnimationActive(final ViewKey sessionKey, final boolean active) {
		FxThread.run(() -> {
			SessionPanel session = this.sessions.get(sessionKey);
			if (session != null) {
				session.setUserAnimationActive(active);
			}
		});
	}

	@Override
	public void setLiveLog(final ViewKey sessionKey, final File file) {
		Platform.runLater(() -> {
			SessionPanel session = this.sessions.get(sessionKey);
			if (session != null) {
				session.showLiveLog(file);
			} else if (file != null) {
				// Told before the session's panel exists: shown as soon as it does.
				this.pendingLiveLogs.put(sessionKey, file);
			} else {
				this.pendingLiveLogs.remove(sessionKey);
			}
		});
	}

	@Override
	public void recenter(final ViewKey sessionKey) {
		FxThread.run(() -> {
			SessionPanel session = this.sessions.get(sessionKey);
			if (session != null) {
				session.recenter();
			}
		});
	}

	@Override
	public void exportSnapshot(final ViewKey sessionKey) {
		FxThread.run(() -> {
			SessionPanel session = this.sessions.get(sessionKey);
			if (session != null) {
				Snapshots.saveAndCopy(session.captureSnapshot(), "sequence");
			}
		});
	}

	// --- RecordingStateEvents: at most one session records at a time, the one just created. ---

	@Override
	public void onRecordingStarted() {
		// A recording session announces itself through onSessionCreated.
	}

	@Override
	public void onRecordingStopped() {
		Platform.runLater(() -> this.sessions.values().forEach(SessionPanel::recordingStopped));
	}

	// --- SequenceDiagramEvents ---

	@Override
	public void onSessionCreated(final ViewKey sessionKey, final Set<Class<?>> observedComponentTypes) {
		Platform.runLater(() -> {
			SessionPanel session = new SessionPanel(sessionKey, observedComponentTypes);
			this.sessions.put(sessionKey, session);
			File liveLog = this.pendingLiveLogs.remove(sessionKey);
			if (liveLog != null) {
				session.showLiveLog(liveLog);
			}
			this.useHostingServices.openPanel(sessionKey, session.getGraphics(), session.getTabDescriptor());
		});
	}

	@Override
	public void onMessageAppended(final ViewKey sessionKey, final SequenceTraceMessage message) {
		Platform.runLater(() -> {
			SessionPanel session = this.sessions.get(sessionKey);
			if (session != null) {
				session.appendMessage(message);
			}
		});
	}

	@Override
	public void onSelectedActorsChanged(final ViewKey sessionKey, final Set<Class<?>> actorTypes) {
		Platform.runLater(() -> {
			SessionPanel session = this.sessions.get(sessionKey);
			if (session != null) {
				session.setSelectedActors(actorTypes);
			}
		});
	}

	@Override
	public void onRecordingLimitReached(final ViewKey sessionKey, final int limit) {
		Platform.runLater(() -> {
			SessionPanel session = this.sessions.get(sessionKey);
			Alert alert = new Alert(AlertType.INFORMATION, SequenceDiagramTexts.get("limit.message", limit));
			alert.setTitle(SequenceDiagramTexts.get("limit.title"));
			alert.setHeaderText(null);
			if (session != null && session.getGraphics().getScene() != null) {
				alert.initOwner(session.getGraphics().getScene().getWindow());
			}
			alert.showAndWait();
		});
	}

	@Override
	public void onSessionClosed(final ViewKey sessionKey) {
		Platform.runLater(() -> {
			SessionPanel closed = this.sessions.remove(sessionKey);
			if (closed != null) {
				closed.dispose();
			}
			this.useHostingServices.closePanel(sessionKey);
		});
	}

	/** One recording session's own canvas, actor map, and details panel. */
	private final class SessionPanel {

		private final ViewKey sessionKey;
		private final SequenceDiagram diagram;
		private final Map<Class<?>, ActorCell> actors = new HashMap<>();
		private final LinkedHashSet<Class<?>> selectedActorTypes = new LinkedHashSet<>();
		private final DoubleProperty diagramLifeLineLength = new SimpleDoubleProperty(TIMELINE_STEP);
		private final Label messageCountLabel = new Label();
		private final Label failureCountLabel = new Label();
		private final ToggleButton animationToggle = new ToggleButton(SequenceDiagramTexts.get("liveDrawing"));
		private final ToggleButton followLiveToggle = new ToggleButton(SequenceDiagramTexts.get("followLive"));
		private final Button openLogsFolder = Toolbox.iconButton(ICON_FOLDER, SequenceDiagramTexts.get("logsFolder.tip"));
		/** The file the session's live log writes to, {@code null} when none is written. */
		private File liveLogFile;
		private final BorderPane graphicContent;
		private final MessageInspector inspector = new MessageInspector();
		private final BorderPane root;
		private int displayedMessageCount = 0;

		/**
		 * How many of the session's messages - kept by the Model - this panel was told of so
		 * far: those it shows, in the Model's order, whether drawn or filtered out.
		 */
		private int recordedCount;

		/** How many of the session's completions, kept by the Model, this panel was told of so far. */
		private int completionCount;

		/** The messages drawn in the diagram, in diagram order. */
		private final List<SequenceTraceMessage> displayed = new ArrayList<>();

		private final SequenceTimeline timeline;

		/** Each invocation's return message, to complete its call message's details. */
		private final Map<Long, SequenceTraceMessage> returnsByInvocation = new LinkedHashMap<>();

		/** The kinds the band currently filters out - see {@link #KINDS}. */
		private final Set<String> hiddenKinds = new HashSet<>();

		private int failureCount = 0;

		/** When this session's recording started, for its timer. */
		private final long recordingStartMillis = System.currentTimeMillis();

		/** How long it recorded, once stopped; {@code -1} while it records. */
		private long recordedMillis = -1;

		private final Label timerLabel = new Label("00:00.00");
		private final Button stopButton = new Button();
		private final Timeline timerTicks = new Timeline();
		private final Label statusLabel = new Label();

		/** Each invocation's call edge, to mark it failed when its return turns out to be. */
		private final Map<Long, MessageEdge> callEdgesByInvocation = new HashMap<>();

		/** The edge of each drawn message, to show which one is selected. */
		private final Map<SequenceTraceMessage, MessageEdge> edgesByMessage = new HashMap<>();

		/** The message shown in the inspector, {@code null} when none. */
		private SequenceTraceMessage selectedMessage;

		/** The selected message's number, as the inspector shows it. */
		private int selectedNumber;

		/** The recorded components, as the band shows them. */
		private String scope = "";

		/** The components being recorded, whose lifelines carry the "recorded" mark. */
		private final Set<Class<?>> recordedTypes = new HashSet<>();

		/** The actors' band and the messages' viewport, together. */
		private VBox diagramArea;

		/** Whether a scroll to the latest message is already scheduled. */
		private boolean revealPending = false;

		/** This session's own tab descriptor, built once and cached - see {@link #buildTabDescriptor()}. */
		private final SupervisorTabDescriptorAdapter tabDescriptor;

		/** The user's own explicit animation choice for this session. */
		private boolean userAnimationActive;

		SessionPanel(final ViewKey sessionKey, final Set<Class<?>> observedComponentTypes) {
			this.sessionKey = sessionKey;
			this.userAnimationActive = SequenceDiagramViewImpl.this.defaultAnimationActive;
			this.tabDescriptor = this.buildTabDescriptor();
			this.diagram = new SequenceDiagram();
			this.diagram.getViewportGestures().setPanButton(MouseButton.PRIMARY);
			// Read from top to bottom like a document: the wheel scrolls, Ctrl + wheel zooms.
			this.diagram.getViewportGestures().setWheelScrolls(true);
			this.diagram.getViewportGestures().setContentFillsViewport(true);

			BorderPane graphicContent = new BorderPane(this.diagram.getCanvas());
			this.graphicContent = graphicContent;
			graphicContent.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
				boolean backgroundClicked = event.getButton() == MouseButton.PRIMARY
						&& event.getTarget() == this.diagram.getCanvas();
				if (backgroundClicked) {
					this.dispatchSelectActors(Set.of());
				}
			});

			BorderPane inspectorPane = this.inspector.getGraphics();
			// The actors' cards stay in their own band, above the scrolling messages.
			VBox diagramArea = new VBox(this.diagram.getHeader(), graphicContent);
			this.diagramArea = diagramArea;
			VBox.setVgrow(graphicContent, Priority.ALWAYS);
			graphicContent.setMinHeight(0);
			Rectangle viewportClip = new Rectangle();
			viewportClip.widthProperty().bind(graphicContent.widthProperty());
			viewportClip.heightProperty().bind(graphicContent.heightProperty());
			graphicContent.setClip(viewportClip);
			SplitPane graphic = new SplitPane(diagramArea, inspectorPane);
			graphic.setDividerPositions(0.74);
			graphic.setOrientation(Orientation.HORIZONTAL);
			CollapsibleSide.install(graphic, inspectorPane, false, "sequence.inspector");

			this.animationToggle.setSelected(this.userAnimationActive);
			Tooltips.install(this.animationToggle, SequenceDiagramTexts.get("liveDrawing.tip"));
			this.animationToggle.selectedProperty().addListener((observable, wasActive, isActive) ->
					SequenceDiagramViewImpl.this.actionManagerServices.executeAction(
							new SequenceDiagramActionKey(this.sessionKey, Role.ToggleAnimationActive), isActive));

			this.followLiveToggle.setSelected(true);
			// Nothing new is drawn while live drawing is off: there is nothing to follow.
			this.followLiveToggle.disableProperty().bind(this.animationToggle.selectedProperty().not());
			Tooltips.install(this.followLiveToggle, SequenceDiagramTexts.get("followLive.tip"));
			// Scrolling back up during a recording: following would pull the view straight down again.
			this.diagram.getViewportGestures().setOnWheelScroll(distance -> {
				if (distance > 0) {
					this.followLiveToggle.setSelected(false);
				}
			});
			this.followLiveToggle.selectedProperty().addListener((observable, wasFollowing, isFollowing) -> {
				if (isFollowing) {
					this.revealLatestMessage();
				}
			});

			this.timeline = new SequenceTimeline(new SequenceTimeline.Source() {
				@Override
				public List<SequenceTraceMessage> messages() {
					return SessionPanel.this.recorded();
				}

				@Override
				public List<SequenceTraceMessage> completions() {
					return SessionPanel.this.completions();
				}

				@Override
				public boolean isShown(final SequenceTraceMessage message) {
					return SessionPanel.this.isShown(message);
				}

				@Override
				public long[] viewedRange() {
					return SessionPanel.this.viewedRange();
				}
			}, this::bringIntoView, SequenceDiagramViewImpl.this.timelineStyle);
			Runnable refreshTimelineWindow = this.timeline::refreshWindow;
			this.diagram.getCanvas().translateYProperty().addListener((observable, oldValue, newValue) -> refreshTimelineWindow.run());
			this.diagram.getCanvas().scaleProperty().addListener((observable, oldValue, newValue) -> refreshTimelineWindow.run());
			graphicContent.heightProperty().addListener((observable, oldValue, newValue) -> refreshTimelineWindow.run());

			this.root = new BorderPane(graphic);
			this.root.setTop(new VBox(this.buildBand(observedComponentTypes), this.timeline));
			this.root.setBottom(this.buildStatusBar());
			this.updateCounters();

			this.zoomScaleProperty().addListener((observable, oldValue, newValue) -> SequenceDiagramViewImpl.this.actionManagerServices
					.executeAction(new SequenceDiagramActionKey(this.sessionKey, Role.ZoomChanged), newValue.doubleValue()));
		}

		BorderPane getGraphics() {
			return this.root;
		}

		/**
		 * Builds this session's tab descriptor - called once, from the
		 * constructor, and cached into {@link #tabDescriptor}.
		 */
		private SupervisorTabDescriptorAdapter buildTabDescriptor() {
			ActionKey zoomSetScaleKey = new SequenceDiagramActionKey(this.sessionKey, Role.ZoomSetScale);
			ActionKey zoomCurrentScaleKey = new SequenceDiagramActionKey(this.sessionKey, Role.ZoomCurrentScale);
			ActionKey closeKey = new SequenceDiagramActionKey(this.sessionKey, Role.Close);
			return new SupervisorTabDescriptorAdapter() {
				@Override
				public RailEntry railEntry() {
					return RailEntry.SEQUENCES;
				}

				@Override
				public boolean renamable() {
					return true;
				}

				@Override
				public EnumSet<ToolbarFunction> supportedToolbarFunctions() {
					return EnumSet.of(ToolbarFunction.ZOOM_SLIDER);
				}

				@Override
				public ActionKey zoomSetScaleActionKey() {
					return zoomSetScaleKey;
				}

				@Override
				public ActionKey zoomCurrentScaleActionKey() {
					return zoomCurrentScaleKey;
				}

				@Override
				public ActionKey closeActionKey() {
					return closeKey;
				}
			};
		}

		SupervisorTabDescriptorAdapter getTabDescriptor() {
			return this.tabDescriptor;
		}

		/**
		 * Called from this session's own control-panel toggle, through
		 * {@code SequenceDiagramInteractionImpl} and {@code
		 * SequenceDiagramViewServices#setAnimationActive} - the sole source
		 * of this session's animation state; see class javadoc.
		 *
		 * @param active the user's new explicit choice for this session
		 */
		void setUserAnimationActive(final boolean active) {
			boolean resumed = active && !this.userAnimationActive;
			this.userAnimationActive = active;
			this.animationToggle.setSelected(active);
			if (resumed) {
				// Draws what arrived while drawing was paused.
				this.redrawMessages();
			}
		}

		/**
		 * The band above the diagram: counters, recorded scope, kind filters, and this
		 * session's own toggles.
		 */
		private HBox buildBand(final Set<Class<?>> observedComponentTypes) {
			this.messageCountLabel.getStyleClass().add("sv-seq-count");
			this.failureCountLabel.getStyleClass().add("sv-seq-failures");
			HBox countLine = new HBox(6, this.messageCountLabel, this.failureCountLabel);
			countLine.setAlignment(Pos.BASELINE_LEFT);
			this.timerLabel.getStyleClass().add("sv-seq-timer");
			VBox counters = new VBox(2, this.timerLabel, countLine);
			counters.setAlignment(Pos.CENTER_LEFT);

			Region stopSquare = new Region();
			stopSquare.getStyleClass().add("sv-seq-stop-square");
			this.stopButton.setGraphic(stopSquare);
			this.stopButton.getStyleClass().add("sv-seq-stop");
			this.stopButton.setFocusTraversable(false);
			Tooltips.install(this.stopButton, SequenceDiagramTexts.get("stop.tip"));
			this.stopButton.setOnAction(event -> SequenceDiagramViewImpl.this.actionManagerServices
					.executeAction(SequenceRecordingActionKey.StopRecording));
			this.stopButton.managedProperty().bind(this.stopButton.visibleProperty());
			this.timerTicks.getKeyFrames().add(new KeyFrame(Duration.millis(100), event -> this.updateTimer()));
			this.timerTicks.setCycleCount(Animation.INDEFINITE);
			this.timerTicks.play();

			Region separator = new Region();
			separator.getStyleClass().add("sv-seq-band-separator");

			String scope = SequenceLog.scopeOf(observedComponentTypes);
			this.scope = scope;
			this.recordedTypes.addAll(observedComponentTypes);
			Label scopeLabel = new Label(scope);
			Tooltips.install(scopeLabel, scope);
			HBox scopeChip = new HBox(scopeLabel);
			scopeChip.getStyleClass().add("sv-chip");
			scopeChip.setMaxWidth(360);
			// When the band runs short, the scope gives way first, cut with an ellipsis.
			scopeLabel.setMinWidth(0);
			scopeChip.setMinWidth(80);

			HBox kinds = new HBox(4);
			for (String[] kind : KINDS) {
				ToggleButton kindToggle = new ToggleButton(SequenceDiagramTexts.get("kind." + kind[0]));
				kindToggle.setGraphic(kindDot(kind[1]));
				kindToggle.getStyleClass().add("sv-kind-toggle");
				kindToggle.setSelected(true);
				kindToggle.setFocusTraversable(false);
				Tooltips.install(kindToggle, SequenceDiagramTexts.get("kind." + kind[0] + ".tip"));
				kindToggle.selectedProperty().addListener((observable, wasShown, isShown) -> {
					if (isShown) {
						this.hiddenKinds.remove(kind[0]);
					} else {
						this.hiddenKinds.add(kind[0]);
					}
					this.redrawMessages();
				});
				kinds.getChildren().add(kindToggle);
			}

			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);

			Button recenter = Toolbox.iconButton(Toolbox.ICON_RECENTER, SequenceDiagramTexts.get("recenter.tip"));
			recenter.setOnAction(event -> SequenceDiagramViewImpl.this.actionManagerServices.executeAction(
					new SequenceDiagramActionKey(this.sessionKey, Role.Recenter)));
			Button snapshot = Toolbox.iconButton(Toolbox.ICON_SNAPSHOT,
					SequenceDiagramTexts.get("snapshot.tip"));
			snapshot.setOnAction(event -> SequenceDiagramViewImpl.this.actionManagerServices.executeAction(
					new SequenceDiagramActionKey(this.sessionKey, Role.Snapshot)));
			Button saveLog = Toolbox.iconButton(ICON_SAVE_LOG, SequenceDiagramTexts.get("saveLog.tip"));
			saveLog.setOnAction(event -> this.chooseTraceFile());
			this.openLogsFolder.setOnAction(event -> openFolder(this.liveLogFile != null
					? this.liveLogFile.getAbsoluteFile().getParentFile() : SequenceLog.folder()));
			// Only where the system lets an application open a folder in its file manager.
			this.openLogsFolder.setVisible(canOpenFolders());
			this.openLogsFolder.setManaged(canOpenFolders());
			HBox tools = new HBox(6, recenter, snapshot, saveLog, this.openLogsFolder);
			tools.setAlignment(Pos.CENTER_LEFT);

			Node scopeColumn = labeled(SequenceDiagramTexts.get("scope"), scopeChip);
			HBox band = new HBox(18, this.stopButton, counters, separator, scopeColumn, labeled(SequenceDiagramTexts.get("kinds"), kinds),
					spacer, tools, this.followLiveToggle, this.animationToggle);
			for (Node child : band.getChildren()) {
				if (child != scopeColumn && child != spacer && child instanceof Region region) {
					region.setMinWidth(Region.USE_PREF_SIZE);
				}
			}
			for (Node kindToggle : kinds.getChildren()) {
				((Region) kindToggle).setMinWidth(Region.USE_PREF_SIZE);
			}
			this.messageCountLabel.setMinWidth(Region.USE_PREF_SIZE);
			this.failureCountLabel.setMinWidth(Region.USE_PREF_SIZE);
			band.getStyleClass().add("sv-seq-band");
			band.setAlignment(Pos.CENTER_LEFT);
			return band;
		}

		private void updateTimer() {
			long millis = this.recordedMillis >= 0 ? this.recordedMillis
					: System.currentTimeMillis() - this.recordingStartMillis;
			this.timerLabel.setText(String.format(Locale.ROOT, "%02d:%02d.%02d", millis / 60_000, millis / 1000 % 60,
					millis / 10 % 100));
		}

		/** Shows, in the logs folder button's tooltip, the file the live log writes to, if any. */
		void showLiveLog(final File file) {
			this.liveLogFile = file;
			this.openLogsFolder.getTooltip().setText(file == null ? SequenceDiagramTexts.get("logsFolder.tip")
					: SequenceDiagramTexts.get("logsFolder.writing.tip", file.getName()));
		}

		/** The panel is closed: its timer stops, so that a recording still going on does not keep it alive. */
		void dispose() {
			this.timerTicks.stop();
		}

		/**
		 * The recording ended: the timer stops on its duration and the Stop button goes. Paused
		 * drawing catches up, so that every recorded message shows; the live drawing toggle keeps
		 * the state chosen, as nothing more is recorded.
		 */
		void recordingStopped() {
			if (this.recordedMillis >= 0) {
				return;
			}
			this.recordedMillis = System.currentTimeMillis() - this.recordingStartMillis;
			this.timerTicks.stop();
			this.updateTimer();
			this.stopButton.setVisible(false);
			if (!this.userAnimationActive) {
				this.redrawMessages();
			}
			this.updateCounters();
			Platform.runLater(this::recenter);
		}

		/**
		 * Fits the diagram's width to its actors, at most the first {@value #RECENTERED_ACTORS},
		 * keeping the messages shown at the top of the view.
		 */
		void recenter() {
			List<ActorCell> actorCells = this.diagram.getActors();
			if (actorCells.isEmpty() || this.graphicContent.getWidth() <= 0) {
				return;
			}
			Region last = this.diagram.getGraphic(actorCells.get(Math.min(RECENTERED_ACTORS, actorCells.size()) - 1));
			double contentWidth = last.getLayoutX() + last.getWidth() + RECENTER_MARGIN;
			PannableCanvas canvas = this.diagram.getCanvas();
			double topY = canvas.parentToLocal(0, 0).getY();
			double scale = Math.max(this.diagram.getViewportGestures().getMinScale(),
					Math.min(1.0, this.graphicContent.getWidth() / contentWidth));
			canvas.setScale(scale);
			Point2D origin = canvas.localToParent(0, topY);
			canvas.setTranslateX(canvas.getTranslateX() - origin.getX());
			canvas.setTranslateY(canvas.getTranslateY() - origin.getY());
			this.diagram.getViewportGestures().keepContentInView();
			this.diagram.layoutHeader();
			this.timeline.refreshWindow();
		}

		/** @return an image of the actors' band and the messages in view */
		Image captureSnapshot() {
			return this.diagramArea.snapshot(new SnapshotParameters(), null);
		}

		/** The bar under the diagram: state, lifelines, messages, failures and scope. */
		private HBox buildStatusBar() {
			this.statusLabel.getStyleClass().add("sv-seq-status-text");
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			Label scopeLabel = new Label(SequenceDiagramTexts.get("status.scope", this.scope));
			scopeLabel.getStyleClass().add("sv-seq-status-text");
			HBox bar = new HBox(12, this.statusLabel, spacer, scopeLabel);
			bar.getStyleClass().add("sv-seq-statusbar");
			bar.setAlignment(Pos.CENTER_LEFT);
			return bar;
		}

		private void updateCounters() {
			this.messageCountLabel.setText(SequenceDiagramTexts.count("counter.messages", this.recordedCount));
			this.failureCountLabel.setText(this.failureCount == 0 ? SequenceDiagramTexts.get("counter.failures.none")
					: SequenceDiagramTexts.count("counter.failures", this.failureCount));
			this.statusLabel.setText(SequenceDiagramTexts.get(this.recordedMillis < 0 ? "status.recording" : "status.recorded",
					SequenceDiagramTexts.count("status.lifelines", this.actors.size()),
					SequenceDiagramTexts.count("status.messages", this.recordedCount),
					SequenceDiagramTexts.count("status.failures", this.failureCount)));
			this.failureCountLabel.getStyleClass().remove("sv-has-failures");
			if (this.failureCount > 0) {
				this.failureCountLabel.getStyleClass().add("sv-has-failures");
			}
		}

		/**
		 * Returns the messages this panel was told of, read from the Model: those it may have
		 * drawn, never one whose notice is still to come.
		 */
		private List<SequenceTraceMessage> recorded() {
			List<SequenceTraceMessage> messages = SequenceDiagramViewImpl.this.useSequenceDiagramModelReadServices
					.getMessages(this.sessionKey);
			// Once the session is closed, the Model holds nothing more of it.
			return messages.subList(0, Math.min(this.recordedCount, messages.size()));
		}

		/** Returns the completions this panel was told of, read from the Model. */
		private List<SequenceTraceMessage> completions() {
			List<SequenceTraceMessage> completions = SequenceDiagramViewImpl.this.useSequenceDiagramModelReadServices
					.getCompletions(this.sessionKey);
			return completions.subList(0, Math.min(this.completionCount, completions.size()));
		}

		void appendMessage(final SequenceTraceMessage message) {
			if (message.getStyle() == SequenceTraceMessage.Style.COMPLETION) {
				this.completeInvocation(message);
				return;
			}
			this.recordedCount++;
			SequenceTraceMessage.Facts facts = message.getFacts();
			if (facts != null && message.getStyle() == SequenceTraceMessage.Style.RETURN) {
				this.returnsByInvocation.put(facts.invocationId(), message);
			}
			if (facts != null && facts.isFailure()) {
				this.failureCount++;
			}
			this.updateCounters();
			this.timeline.invalidate();
			if (!this.userAnimationActive && this.recordedMillis < 0) {
				// Drawing is paused: the message is kept and drawn when drawing resumes or the
				// recording stops - see class javadoc.
				return;
			}
			if (!this.isShown(message)) {
				return;
			}
			this.drawMessage(message, this.recordedCount);
			this.updateCounters();
			this.diagram.layout();
			if (this.followLiveToggle.isSelected()) {
				this.revealLatestMessage();
			}
		}

		private boolean isShown(final SequenceTraceMessage message) {
			SequenceTraceMessage.Facts facts = message.getFacts();
			return facts == null || !this.hiddenKinds.contains(facts.kind());
		}

		/** Records how an invocation without a return ended, for the inspector and the timeline. */
		private void completeInvocation(final SequenceTraceMessage completion) {
			SequenceTraceMessage.Facts facts = completion.getFacts();
			if (facts == null) {
				return;
			}
			this.returnsByInvocation.put(facts.invocationId(), completion);
			this.completionCount++;
			if (facts.isFailure()) {
				this.failureCount++;
				MessageEdge call = this.callEdgesByInvocation.get(facts.invocationId());
				if (call != null) {
					call.setFailed(true);
				}
				this.updateCounters();
			}
			this.timeline.invalidate();
			if (this.selectedMessage != null && this.selectedMessage.getFacts() != null
					&& this.selectedMessage.getFacts().invocationId() == facts.invocationId()) {
				this.inspector.show(this.selectedMessage, this.selectedNumber);
			}
		}

		private void drawMessage(final SequenceTraceMessage message, final int number) {
			ActorCell sourceCell = this.getOrCreateActor(message.getSourceType(), message.getSourceStereotype());
			ActorCell targetCell = this.getOrCreateActor(message.getTargetType(), message.getTargetStereotype());
			MessageEdgeImpl addedMessage = new MessageEdgeImpl(sourceCell, targetCell, message.getLabel(),
					this.toDiagramStyle(message));
			addedMessage.setTimeText(String.format(Locale.ROOT, "+%.3f s", message.getRelativeTimeMillis() / 1000.0));
			addedMessage.onClick(() -> this.select(message, number));
			SequenceTraceMessage.Facts facts = message.getFacts();
			if (facts != null && message.getStyle() == SequenceTraceMessage.Style.CALL) {
				this.callEdgesByInvocation.put(facts.invocationId(), addedMessage);
			}
			if (facts != null && facts.isFailure()) {
				addedMessage.setFailed(true);
				MessageEdge call = this.callEdgesByInvocation.get(facts.invocationId());
				if (call != null) {
					call.setFailed(true);
				}
			}
			addedMessage.setSelected(message == this.selectedMessage);
			addedMessage.setDimmed(this.dimmed(message));
			this.diagram.addMessage(addedMessage);
			this.edgesByMessage.put(message, addedMessage);
			this.displayed.add(message);
			this.displayedMessageCount++;
			this.diagramLifeLineLength.set(Math.max(TIMELINE_STEP,
					this.displayedMessageCount * this.diagram.getHorizontalSpacing() + TIMELINE_BOTTOM_PADDING));
		}

		/** Shows a message in the inspector and marks it selected in the diagram. */
		private void select(final SequenceTraceMessage message, final int number) {
			MessageEdge previous = this.edgesByMessage.get(this.selectedMessage);
			if (previous != null) {
				previous.setSelected(false);
			}
			this.selectedMessage = message;
			this.selectedNumber = number;
			MessageEdge current = this.edgesByMessage.get(message);
			if (current != null) {
				current.setSelected(true);
			}
			this.inspector.show(message, number);
		}

		/** Redraws the recorded messages the kind filters let through. */
		private void redrawMessages() {
			this.diagram.clearMessages();
			this.displayed.clear();
			this.callEdgesByInvocation.clear();
			this.edgesByMessage.clear();
			this.displayedMessageCount = 0;
			List<SequenceTraceMessage> recorded = this.recorded();
			for (int index = 0; index < recorded.size(); index++) {
				SequenceTraceMessage message = recorded.get(index);
				if (this.isShown(message)) {
					this.drawMessage(message, index + 1);
				}
			}
			this.diagramLifeLineLength.set(Math.max(TIMELINE_STEP,
					this.displayedMessageCount * this.diagram.getHorizontalSpacing() + TIMELINE_BOTTOM_PADDING));
			this.diagram.layout();
			// Back to the top: fewer messages may now be drawn than the view was scrolled past.
			this.diagram.getCanvas().setTranslateY(0);
			if (this.followLiveToggle.isSelected()) {
				this.revealLatestMessage();
			}
			this.timeline.refresh();
		}

		/** Height of the lifelines' anchors, in canvas units: the messages start below it. */
		private double headerHeight() {
			if (this.diagram.getActors().isEmpty()) {
				return 0;
			}
			Region header = this.diagram.getGraphic(this.diagram.getActors().get(0));
			return header == null ? 0 : header.getHeight();
		}

		/**
		 * @return the relative times of the first and last drawn messages in view, {@code null}
		 *         when none is drawn
		 */
		private long[] viewedRange() {
			int count = this.diagram.getMessages().size();
			if (count == 0 || count != this.displayed.size() || this.graphicContent.getScene() == null) {
				return null;
			}
			Bounds viewport = this.graphicContent.localToScene(this.graphicContent.getLayoutBounds());
			double headerHeight = this.headerHeight();
			double spacing = this.diagram.getHorizontalSpacing();
			double top = this.diagram.getCanvas().sceneToLocal(0, viewport.getMinY()).getY();
			double bottom = this.diagram.getCanvas().sceneToLocal(0, viewport.getMaxY()).getY();
			int first = (int) Math.ceil((top - headerHeight) / spacing) - 1;
			int last = (int) Math.floor((bottom - headerHeight) / spacing) - 1;
			first = Math.max(0, Math.min(count - 1, first));
			last = Math.max(first, Math.min(count - 1, last));
			return new long[] { this.displayed.get(first).getRelativeTimeMillis(),
					this.displayed.get(last).getRelativeTimeMillis() };
		}

		/** Scrolls the diagram so that its message closest to this time is in the middle of the view. */
		private void bringIntoView(final long relativeTimeMillis) {
			if (this.displayed.isEmpty() || this.diagram.getMessages().size() != this.displayed.size()) {
				return;
			}
			this.followLiveToggle.setSelected(false);
			int index = 0;
			while (index < this.displayed.size() - 1
					&& this.displayed.get(index).getRelativeTimeMillis() < relativeTimeMillis) {
				index++;
			}
			if (index > 0 && relativeTimeMillis - this.displayed.get(index - 1).getRelativeTimeMillis()
					< this.displayed.get(index).getRelativeTimeMillis() - relativeTimeMillis) {
				index--;
			}
			double headerHeight = this.headerHeight();
			double messageY = headerHeight + this.diagram.getMessages().get(index).yOffsetProperty().get();
			Bounds viewport = this.graphicContent.localToScene(this.graphicContent.getLayoutBounds());
			double currentSceneY = this.diagram.getCanvas().localToScene(0, messageY).getY();
			double wantedSceneY = (viewport.getMinY() + viewport.getMaxY()) / 2;
			// Never past the top of the diagram: its first messages stay right under the actors.
			this.diagram.getCanvas().setTranslateY(
					Math.min(0, this.diagram.getCanvas().getTranslateY() + wantedSceneY - currentSceneY));
		}

		/** Pans the diagram up just enough for its last message to be in view. */
		private void revealLatestMessage() {
			List<MessageEdge> messages = this.diagram.getMessages();
			if (messages.isEmpty()) {
				return;
			}
			if (this.revealPending) {
				return;
			}
			this.revealPending = true;
			Platform.runLater(() -> {
				this.revealPending = false;
				// Follow live may have been turned off, e.g. by a seek, since this was scheduled.
				if (!this.followLiveToggle.isSelected() || this.graphicContent.getScene() == null
						|| this.diagram.getActors().isEmpty() || this.diagram.getMessages().isEmpty()) {
					return;
				}
				MessageEdge latest = this.diagram.getMessages().get(this.diagram.getMessages().size() - 1);
				Region header = this.diagram.getGraphic(this.diagram.getActors().get(0));
				double headerHeight = header == null ? 0 : header.getHeight();
				// The message's label hangs below its arrow.
				double latestY = headerHeight + latest.yOffsetProperty().get() + 40;
				double latestSceneY = this.diagram.getCanvas().localToScene(0, latestY).getY();
				Bounds viewport = this.graphicContent.localToScene(this.graphicContent.getLayoutBounds());
				double overflow = latestSceneY - (viewport.getMaxY() - 16);
				if (overflow > 0) {
					this.diagram.getCanvas().setTranslateY(this.diagram.getCanvas().getTranslateY() - overflow);
				}
			});
		}

		void setSelectedActors(final Set<Class<?>> actorTypes) {
			this.selectedActorTypes.clear();
			this.selectedActorTypes.addAll(actorTypes);
			for (Map.Entry<Class<?>, ActorCell> entry : this.actors.entrySet()) {
				entry.getValue().setSelected(this.selectedActorTypes.contains(entry.getKey()));
			}
			for (Map.Entry<SequenceTraceMessage, MessageEdge> entry : this.edgesByMessage.entrySet()) {
				entry.getValue().setDimmed(this.dimmed(entry.getKey()));
			}
		}

		/**
		 * @return whether a message fades: some lifelines are selected, and it neither leaves nor
		 *         reaches one of them - the selected ones' exchanges stand out
		 */
		private boolean dimmed(final SequenceTraceMessage message) {
			return !this.selectedActorTypes.isEmpty() && !this.selectedActorTypes.contains(message.getSourceType())
					&& !this.selectedActorTypes.contains(message.getTargetType());
		}

		private MessageStyle toDiagramStyle(final SequenceTraceMessage message) {
			if (message.getStyle() == SequenceTraceMessage.Style.RETURN) {
				return MessageStyle.RETURN;
			}
			SequenceTraceMessage.Facts facts = message.getFacts();
			return facts != null && facts.asynchronous() ? MessageStyle.ASYNC_CALL : MessageStyle.CALL;
		}

		private ActorCell getOrCreateActor(final Class<?> actorType, final String stereotype) {
			ActorCell actor = this.actors.get(actorType);
			if (actor != null) {
				return actor;
			}
			ActorCellImpl newActor = new ActorCellImpl(actorType.getSimpleName(), this.diagramLifeLineLength);
			if (stereotype != null) {
				newActor.setKind(stereotype);
			}
			newActor.setRecorded(this.recordedTypes.contains(actorType));
			newActor.onClick(event -> this.handleActorClick(actorType, event));
			actor = newActor;
			this.actors.put(actorType, actor);
			this.diagram.addActor(actor);
			return actor;
		}

		private void handleActorClick(final Class<?> actorType, final MouseEvent event) {
			if (event.isControlDown()) {
				SequenceDiagramViewImpl.this.actionManagerServices.executeAction(
						new SequenceDiagramActionKey(this.sessionKey, Role.ToggleActorSelection), actorType);
			} else {
				this.dispatchSelectActors(Set.of(actorType));
			}
			event.consume();
		}

		private void dispatchSelectActors(final Set<Class<?>> actorTypes) {
			SequenceDiagramViewImpl.this.actionManagerServices
					.executeAction(new SequenceDiagramActionKey(this.sessionKey, Role.SelectActors), actorTypes);
		}

		/**
		 * Asks where to save this session's log, offering the three formats with the preferred
		 * one first, then has it saved there; a name without an extension takes the chosen format's.
		 */
		private void chooseTraceFile() {
			SequenceLog.Format preferred = SequenceLog.preferredFormat();
			FileChooser chooser = new FileChooser();
			chooser.setTitle(SequenceDiagramTexts.get("saveLog.title"));
			chooser.setInitialFileName(SequenceLog.fileName(this.scope, Instant.now(), preferred));
			Map<FileChooser.ExtensionFilter, SequenceLog.Format> formats = new LinkedHashMap<>();
			for (SequenceLog.Format format : SequenceLog.Format.values()) {
				FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter(
						SequenceDiagramTexts.get("logFormat." + format.name()), "*." + format.extension());
				formats.put(filter, format);
				chooser.getExtensionFilters().add(filter);
				if (format == preferred) {
					chooser.setSelectedExtensionFilter(filter);
				}
			}
			File folder = SequenceLog.folder();
			if (folder.isDirectory()) {
				chooser.setInitialDirectory(folder);
			}
			File file = chooser.showSaveDialog(this.root.getScene() == null ? null : this.root.getScene().getWindow());
			if (file == null) {
				return;
			}
			SequenceLog.Format chosen = formats.getOrDefault(chooser.getSelectedExtensionFilter(), preferred);
			if (!file.getName().contains(".")) {
				file = new File(file.getParentFile(), file.getName() + "." + chosen.extension());
			}
			SequenceDiagramViewImpl.this.actionManagerServices
					.executeAction(new SequenceDiagramActionKey(this.sessionKey, Role.ExportTrace), file);
		}

		/**
		 * The panel beside the diagram describing the message last clicked: what was
		 * called, between whom, when, how long it took compared with its latest calls,
		 * and with what arguments.
		 */
		private final class MessageInspector {

			private final VBox content = new VBox(18);
			private final ScrollPane scroll = new ScrollPane(this.content);
			private final Button showInDiagram = new Button(SequenceDiagramTexts.get("showInDiagram"));
			private final BorderPane graphics = new BorderPane(this.scroll);

			/** The message shown, {@code null} when none is. */
			private SequenceTraceMessage shownMessage;

			MessageInspector() {
				this.content.getStyleClass().add("sv-seq-inspector");
				this.content.setPadding(new Insets(18, 22, 18, 22));
				this.scroll.setFitToWidth(true);
				this.scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
				this.scroll.getStyleClass().add("sv-seq-inspector-scroll");

				this.showInDiagram.setMaxWidth(Double.MAX_VALUE);
				this.showInDiagram.setDisable(true);
				Tooltips.install(this.showInDiagram,
						SequenceDiagramTexts.get("showInDiagram.tip"));
				this.showInDiagram.setOnAction(event -> {
					if (this.shownMessage != null) {
						SequenceDiagramViewImpl.this.actionManagerServices.executeAction(
								ComponentDiagramToolActionKey.ShowLink,
								List.of(this.shownMessage.getSourceType(), this.shownMessage.getTargetType()));
					}
				});
				HBox.setHgrow(this.showInDiagram, Priority.ALWAYS);
				HBox actions = new HBox(8, this.showInDiagram);
				actions.getStyleClass().add("sv-seq-inspector-actions");

				this.graphics.setBottom(actions);
				this.graphics.getStyleClass().add("sv-seq-inspector-pane");
				this.graphics.setMinWidth(280);
				this.graphics.setPrefWidth(340);
				this.showNothing();
			}

			BorderPane getGraphics() {
				return this.graphics;
			}

			private void showNothing() {
				this.shownMessage = null;
				this.showInDiagram.setDisable(true);
				Label hint = new Label(SequenceDiagramTexts.get("inspector.hint"));
				hint.setWrapText(true);
				hint.getStyleClass().add("sv-seq-hint");
				this.content.getChildren().setAll(eyebrow(SequenceDiagramTexts.get("inspector.selected")), hint);
			}

			void show(final SequenceTraceMessage message, final int number) {
				this.shownMessage = message;
				this.showInDiagram.setDisable(false);
				SequenceTraceMessage.Facts facts = message.getFacts();
				if (facts == null) {
					Label details = new Label(message.getDetails());
					details.setWrapText(true);
					details.getStyleClass().add("sv-seq-code");
					this.content.getChildren().setAll(eyebrow(SequenceDiagramTexts.get("inspector.selectedNumber", number)), details);
					return;
				}
				boolean returning = message.getStyle() == SequenceTraceMessage.Style.RETURN;
				SequenceTraceMessage returnMessage = returning ? message
						: SessionPanel.this.returnsByInvocation.get(facts.invocationId());
				SequenceTraceMessage.Facts outcome = returnMessage == null ? null : returnMessage.getFacts();

				// What the message is, framed apart: title, kind, route and its method's durations.
				VBox summary = new VBox(14, this.header(facts, returning, number, outcome), this.route(message, facts));
				Node history = this.durationHistory(facts);
				if (history != null) {
					summary.getChildren().add(history);
				}
				summary.getStyleClass().add("sv-seq-summary");
				List<Node> sections = new ArrayList<>();
				sections.add(summary);
				sections.add(this.figures(message, facts, outcome));
				if (facts.receiverDescription() != null) {
					sections.add(labeled(SequenceDiagramTexts.get("inspector.receivedBy"), code(facts.receiverDescription())));
				}
				sections.add(labeled(SequenceDiagramTexts.get("inspector.arguments"), code(facts.argumentsDescription() == null
						|| facts.argumentsDescription().isBlank() ? SequenceDiagramTexts.get("inspector.noArguments")
								: facts.argumentsDescription())));
				if (outcome != null && outcome.outcome() != null
						&& outcome.outcome().kind() != CallOutcome.Kind.FAILED) {
					Label returned = code(outcome.outcome().toText());
					if (outcome.outcome().kind() != CallOutcome.Kind.VALUE) {
						// void and null are keywords, not values: set apart from a returned value.
						returned.getStyleClass().add("sv-seq-keyword");
					}
					sections.add(labeled(SequenceDiagramTexts.get("inspector.returned"), returned));
				}
				if (outcome != null && outcome.isFailure()) {
					Label failure = code(outcome.failureDescription());
					failure.getStyleClass().add("sv-seq-failure");
					sections.add(labeled(SequenceDiagramTexts.get("inspector.failure"), failure));
				}
				this.content.getChildren().setAll(sections);
			}

			private Node header(final SequenceTraceMessage.Facts facts, final boolean returning, final int number,
					final SequenceTraceMessage.Facts outcome) {
				// Wraps between the port and the method rather than cutting long names.
				FlowPane title = new FlowPane();
				if (facts.methodName() != null) {
					Label port = new Label(facts.portName() + ".");
					port.getStyleClass().add("sv-seq-title-port");
					Label method = new Label(facts.methodName() + "()");
					method.getStyleClass().add("sv-seq-title");
					title.getChildren().addAll(port, method);
				} else {
					Label action = new Label(facts.portName());
					action.getStyleClass().add("sv-seq-title");
					title.getChildren().add(action);
				}
				HBox chips = new HBox(6, chip(kindDot(styleSuffix(facts.kind())),
						SequenceDiagramTexts.getOr((returning ? "chip.return." : "chip.call.") + facts.kind(),
								returning ? "chip.return" : "chip.call", facts.kind())));
				chips.getChildren().add(chip(null, SequenceDiagramTexts.get(facts.asynchronous() ? "chip.asynchronous"
						: "chip.synchronous")));
				if (outcome != null && outcome.isFailure()) {
					HBox failed = chip(null, SequenceDiagramTexts.get("chip.failed"));
					failed.getStyleClass().add("sv-chip-bad");
					chips.getChildren().add(failed);
				}
				return new VBox(8, eyebrow(SequenceDiagramTexts.get("inspector.selectedNumber", number)), title, chips);
			}

			private Node route(final SequenceTraceMessage message, final SequenceTraceMessage.Facts facts) {
				Label from = new Label(message.getSourceType().getSimpleName());
				Label arrow = new Label("→");
				arrow.getStyleClass().addAll("sv-seq-route-arrow", "sv-kind-" + styleSuffix(facts.kind()));
				Label to = new Label(message.getTargetType().getSimpleName());
				Tooltips.install(from, message.getSourceType().getName());
				Tooltips.install(to, message.getTargetType().getName());
				// Long names go on two lines, the target under the source, rather than being cut.
				HBox target = new HBox(6, arrow, to);
				target.setAlignment(Pos.CENTER_LEFT);
				FlowPane route = new FlowPane(6, 4, from, target);
				route.getStyleClass().add("sv-seq-route");
				route.setAlignment(Pos.CENTER_LEFT);
				return route;
			}

			private Node figures(final SequenceTraceMessage message, final SequenceTraceMessage.Facts facts,
					final SequenceTraceMessage.Facts outcome) {
				long durationNanos = outcome != null ? outcome.durationNanos() : facts.durationNanos();
				String status;
				if (facts.methodName() == null) {
					status = "outcome.delivered";
				} else if ("Event".equals(facts.kind())) {
					status = outcome == null ? "outcome.beingDelivered"
							: outcome.isFailure() ? "outcome.failed" : "outcome.delivered";
				} else if (outcome == null) {
					status = "outcome.noReturnYet";
				} else {
					status = outcome.isFailure() ? "outcome.failed" : "outcome.returned";
				}
				GridPane grid = new GridPane();
				grid.setHgap(16);
				grid.setVgap(12);
				// The left column holds the thread name, the longest value.
				ColumnConstraints wide = new ColumnConstraints();
				wide.setPercentWidth(62);
				ColumnConstraints narrow = new ColumnConstraints();
				narrow.setPercentWidth(38);
				grid.getColumnConstraints().addAll(wide, narrow);
				grid.add(labeled(SequenceDiagramTexts.get("figure.start"), value("T0 + " + message.getRelativeTimeMillis() + " ms", true)), 0, 0);
				grid.add(labeled(SequenceDiagramTexts.get("figure.duration"), value(durationNanos >= 0 ? formatDuration(durationNanos) : "-", true)), 1, 0);
				Label thread = value(facts.threadName(), false);
				Tooltips.install(thread, facts.threadName());
				grid.add(labeled(SequenceDiagramTexts.get("figure.thread"), thread), 0, 1);
				grid.add(labeled(SequenceDiagramTexts.get("figure.status"), value(SequenceDiagramTexts.get(status), false)), 1, 1);
				if (facts.asynchronous() && facts.queueWaitNanos() >= 0) {
					grid.add(labeled(SequenceDiagramTexts.get("figure.waitedInQueue"), value(formatDuration(facts.queueWaitNanos()), true)), 0, 2);
				}
				return grid;
			}

			/** The duration of the latest returns of the same method, with their 95th percentile. */
			private Node durationHistory(final SequenceTraceMessage.Facts facts) {
				if (facts.methodName() == null) {
					return null;
				}
				List<Long> durations = new ArrayList<>();
				// Returns and completions, in arrival order.
				List<SequenceTraceMessage> endings = new ArrayList<>(SessionPanel.this.returnsByInvocation.values());
				for (int index = endings.size() - 1; index >= 0 && durations.size() < DURATION_HISTORY; index--) {
					SequenceTraceMessage.Facts candidateFacts = endings.get(index).getFacts();
					if (candidateFacts != null
							&& candidateFacts.durationNanos() >= 0
							&& facts.portName().equals(candidateFacts.portName())
							&& facts.methodName().equals(candidateFacts.methodName())) {
						durations.add(0, candidateFacts.durationNanos());
					}
				}
				if (durations.isEmpty()) {
					return null;
				}
				List<Long> sorted = new ArrayList<>(durations);
				sorted.sort(null);
				long p95 = sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * 0.95) - 1));
				long max = Math.max(1, sorted.get(sorted.size() - 1));

				Label title = eyebrow(SequenceDiagramTexts.count("history.title", durations.size()));
				Region spacer = new Region();
				HBox.setHgrow(spacer, Priority.ALWAYS);
				Label p95Label = new Label("p95 " + formatDuration(p95));
				p95Label.getStyleClass().add("sv-seq-p95");
				p95Label.setMinWidth(Region.USE_PREF_SIZE);
				title.setMinWidth(0);
				HBox heading = new HBox(title, spacer, p95Label);
				heading.setAlignment(Pos.CENTER_LEFT);

				Polyline curve = new Polyline();
				double step = durations.size() == 1 ? 0 : SPARKLINE_WIDTH / (durations.size() - 1);
				for (int index = 0; index < durations.size(); index++) {
					curve.getPoints().addAll(index * step, sparklineY(durations.get(index), max));
				}
				curve.getStyleClass().add("sv-seq-spark");
				double p95Y = sparklineY(p95, max);
				Line p95Line = new Line(0, p95Y, SPARKLINE_WIDTH, p95Y);
				p95Line.getStyleClass().add("sv-seq-spark-p95");
				Pane sparkline = new Pane(p95Line, curve);
				sparkline.setMinSize(SPARKLINE_WIDTH, SPARKLINE_HEIGHT);
				sparkline.setPrefSize(SPARKLINE_WIDTH, SPARKLINE_HEIGHT);
				sparkline.setMaxSize(SPARKLINE_WIDTH, SPARKLINE_HEIGHT);
				return new VBox(8, heading, sparkline);
			}
		}

		DoubleProperty zoomScaleProperty() {
			return this.diagram.getCanvas().scaleProperty();
		}

		void setZoomScale(final double scale) {
			Point2D pivot = this.zoomPivotSceneCoordinates();
			this.diagram.getViewportGestures().zoomTo(scale, pivot.getX(), pivot.getY());
		}
		private Point2D zoomPivotSceneCoordinates() {
			if (!this.selectedActorTypes.isEmpty()) {
				Class<?> selectedActorType = this.selectedActorTypes.iterator().next();
				ActorCell selectedActor = this.actors.get(selectedActorType);
				if (selectedActor != null) {
					return Graph.sceneCenterOf(this.diagram.getGraphic(selectedActor));
				}
			}
			// The middle of the messages' area, under the actors' band and beside the inspector.
			return Graph.sceneCenterOf(this.graphicContent);
		}
	}

	// --- Band and inspector building blocks ---

	private static Label eyebrow(final String text) {
		Label label = new Label(text.toUpperCase(Locale.ROOT));
		label.getStyleClass().add("sv-eyebrow");
		return label;
	}

	private static VBox labeled(final String title, final Node content) {
		VBox block = new VBox(6, eyebrow(title), content);
		block.setAlignment(Pos.TOP_LEFT);
		return block;
	}

	private static Label value(final String text, final boolean emphasized) {
		Label label = new Label(text);
		label.getStyleClass().add(emphasized ? "sv-seq-value" : "sv-seq-value-small");
		return label;
	}

	private static Label code(final String text) {
		Label label = new Label(text);
		label.setWrapText(true);
		label.setMaxWidth(Double.MAX_VALUE);
		label.getStyleClass().add("sv-seq-code");
		return label;
	}

	private static HBox chip(final Node graphic, final String text) {
		HBox chip = new HBox(new Label(text));
		if (graphic != null) {
			chip.getChildren().add(0, graphic);
		}
		chip.getStyleClass().add("sv-chip");
		chip.setAlignment(Pos.CENTER_LEFT);
		chip.setMaxWidth(Region.USE_PREF_SIZE);
		return chip;
	}

	private static Circle kindDot(final String styleSuffix) {
		Circle dot = new Circle(4);
		dot.getStyleClass().addAll("sv-kind-dot", "sv-kind-" + styleSuffix);
		return dot;
	}

	private static String styleSuffix(final String kind) {
		for (String[] candidate : KINDS) {
			if (candidate[0].equals(kind)) {
				return candidate[1];
			}
		}
		return "service";
	}

	private static String formatDuration(final long durationNanos) {
		double millis = durationNanos / 1_000_000.0;
		return String.format(Locale.ROOT, millis < 10 ? "%.3f ms" : "%.1f ms", millis);
	}

	private static double sparklineY(final long durationNanos, final long maxNanos) {
		double margin = 4;
		return SPARKLINE_HEIGHT - margin - (SPARKLINE_HEIGHT - 2 * margin) * durationNanos / maxNanos;
	}

	/** @return whether the system lets an application open a folder in its file manager */
	private static boolean canOpenFolders() {
		return Desktop.isDesktopSupported()
				&& Desktop.getDesktop().isSupported(Desktop.Action.OPEN);
	}

	/**
	 * Opens a folder in the system's file manager, off the JavaFX thread, making it first if no log
	 * was written there yet; a failure is only logged.
	 */
	private static void openFolder(final File folder) {
		if (folder == null) {
			return;
		}
		Thread opener = SupervisionThreads.newThread(() -> {
			try {
				folder.mkdirs();
				Desktop.getDesktop().open(folder);
			} catch (IOException | RuntimeException e) {
				Logger.getLogger(SequenceDiagramViewImpl.class.getName())
						.log(Level.WARNING, "Failed to open the folder " + folder, e);
			}
		}, "open-folder");
		opener.start();
	}
}
