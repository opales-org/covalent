/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.supervisionhmi.view.impl;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.opales.covalent.supervision.componentview.internal.WebColors;
import org.opales.covalent.supervision.componentview.internal.Snapshots;
import org.opales.covalent.supervision.componentview.internal.Tooltips;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.ViewportGestures;
import org.opales.covalent.supervision.componentview.internal.Toolbox;
import org.opales.covalent.supervision.cockpit.interaction.CockpitActionKey;
import org.opales.covalent.supervision.componentdiagram.interaction.ComponentDiagramPreferencesActionKey;
import org.opales.covalent.supervision.componentdiagram.interaction.ComponentDiagramToolActionKey;
import org.opales.covalent.supervision.componentdiagram.model.PortColorKey;
import org.opales.covalent.supervision.componentdiagram.view.PortColorKeyTexts;
import org.opales.covalent.supervision.containermanager.interaction.ContainerManagerActionKey;
import org.opales.covalent.supervision.containermanager.view.ContainerManagerViewParameters;
import org.opales.covalent.supervision.dependencymanagement.interaction.DependencyManagementActionKey;
import org.opales.covalent.supervision.exclusionpolicy.interaction.ExclusionPolicyActionKey;
import org.opales.covalent.supervision.stereotypepolicy.interaction.StereotypePolicyActionKey;
import org.opales.covalent.supervision.sequencediagram.interaction.SequenceDiagramPreferenceActionKey;
import org.opales.covalent.supervision.sequencediagram.SequenceLog;
import org.opales.covalent.supervision.sequencediagram.view.TimelineStyle;
import org.opales.covalent.supervision.supervisionhmi.interaction.SupervisionHmiActionKey;
import org.opales.covalent.supervision.supervisionhmi.view.FxThread;
import org.opales.covalent.supervision.supervisionhmi.view.PanelHostingServices;
import org.opales.covalent.supervision.supervisionhmi.view.RailEntry;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisionHmiView;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisionHmiViewServices;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisorTabDescriptor;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisorMessages;
import org.opales.covalent.supervision.supervisionhmi.SupervisorSettings;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisorTheme;
import org.opales.covalent.supervision.supervisionhmi.view.ToolbarFunction;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;
import org.opales.covalent.supervision.threads.interaction.ThreadsActionKey;

import org.opales.covalent.container.component.annotations.AccessedParameters;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionConsumerServices;
import org.opales.covalent.hmi.action.ActionKey;

import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.event.Event;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * SupervisionHmi's View: the window and its chrome - the only place in
 * SupervisionHmi allowed to hold a JavaFX {@link Node} reference, and the
 * only place that calls JavaFX-shaped APIs such as {@code
 * ActionConsumerServices.bindActionAvailability}/{@code
 * unbindActionAvailability}. Provides {@link PanelHostingServices} to every
 * hosted function's own View, and embeds {@code ContainerManagerView}'s
 * selector widget into its header at activation.
 * <p>
 * The window is laid out as:
 * <ul>
 * <li>a header: brand mark, the container selector, the Flows toggle
 * (data flows on or off for the observed container), the shared Record
 * button (showing the recording state and elapsed time of the tab it
 * controls, read from that tab's descriptor's {@code
 * recordingStateActionKey()}; it is available only while data flows are
 * on) and the light/dark theme switch ({@code applyTheme(SupervisorTheme)});</li>
 * <li>a side rail: one button per main {@link RailEntry} (Cockpit,
 * Components, Sequences, Graph, Threads, Exclusions, Settings) and a Help
 * button whose menu offers the help, the keyboard shortcuts and the About
 * box;</li>
 * <li>a content area per rail entry, only the active one visible. Every
 * panel opened through {@link #openPanel} lands in the area of its
 * descriptor's {@link SupervisorTabDescriptor#railEntry()}; an area with
 * panels shows them as sub-tabs (each with its own close button), plus a
 * picker listing them all once there are several. An area without panels
 * shows an empty state offering to open its function;</li>
 * <li>a status bar: the visible panel's name and the shared zoom slider.</li>
 * </ul>
 * Whenever the visible panel changes - a rail entry or a sub-tab is
 * selected, the visible panel is opened or closed - its descriptor is
 * broadcast as {@code SupervisionHmiActionKey.TabSelected}, which both
 * rebinds the shared Record button and zoom slider (through {@code
 * SupervisionHmiInteraction}) and lets hosted functions know which of
 * their tabs is on screen.
 * <p>
 * The Settings entry hosts this View's own Preferences panel ({@code
 * buildPreferencesPanel()}) grouping everything that belongs here as a
 * preference rather than a per-diagram gesture, in three tabs. General has
 * one card per domain: Data flows (enabled on connect), Threads (task
 * tracking on connect, long-task threshold), Components (entry points to
 * {@code ExclusionPolicy}'s and {@code StereotypePolicy}'s own panels),
 * Language, and Diagram (ComponentDiagram's "recalculate layout
 * automatically when hiding components" switch, Sequence Diagram's default
 * observation mode, timeline style and live log, and the snapshot, views
 * and log folders). Colors has a {@link ColorPicker} per {@code
 * PortColorKey} for ComponentDiagram's port/wire palette. Stereotypes hosts
 * the stereotype table's panel. The panel talks to ComponentDiagram purely
 * through {@code ActionConsumerServices} (execute to write, bind-value to
 * read), never a direct Services reference, since ComponentDiagram depends
 * on this class through {@link PanelHostingServices} and the reverse edge
 * would cycle.
 */
@Implementation
public class SupervisionHmiViewImpl
		implements SupervisionHmiView, PanelHostingServices, SupervisionHmiViewServices {

	/** Creates the view of the supervisor's window. */
	public SupervisionHmiViewImpl() {
		// Nothing to initialize.
	}

	@UsedServices
	private ActionConsumerServices actionManagerServices;

	@AccessedParameters
	private ContainerManagerViewParameters accessContainerManagerViewParameters;

	private static final double PREVIEW_CARD_WIDTH = 170;

	private static final double PREVIEW_CARD_HEIGHT = 260;

	/** Theme-independent stylesheet, always loaded first - see {@link #applyTheme(SupervisorTheme)}. */
	private static final String BASE_STYLESHEET = "supervision.css";

	/** The sizes of the window icon, in pixels; the system picks the one that fits. */
	private static final int[] ICON_SIZES = {16, 24, 32, 48, 64, 128, 256};

	/** How long after a color is validated the window is put back in front, so that the picker's dialog is gone. */
	private static final double WINDOW_TO_FRONT_DELAY_MILLIS = 10;

	/** Fonts the stylesheets reference by family name, embedded so every platform renders the same. */
	private static final String[] EMBEDDED_FONTS = { "IBMPlexSans-Regular.ttf", "IBMPlexSans-SemiBold.ttf",
			"IBMPlexSans-Bold.ttf", "Sora-SemiBold.ttf", "JetBrainsMono-Regular.ttf", "JetBrainsMono-SemiBold.ttf" };

	/** Style class of the Record button while the visible tab records - see {@code supervision.css}. */
	private static final String RECORDING_STYLE_CLASS = "sv-recording";

	// --- 24x24 stroke icons (SVG path data), drawn with the "sv-icon" style class ---
	private static final String ICON_COCKPIT = "M4 17a8 8 0 1 1 16 0M12 17l4-5M8 17h.01M16 17h.01M7 11.5l.8.6M12 9v1M17 11.5l-.8.6";
	private static final String ICON_COMPONENTS = "M3 4h7v6H3zM14 4h7v6h-7zM8.5 14h7v6h-7zM10 7h4M6.5 10v2.5h5.5V14M17.5 10v2.5H12";
	private static final String ICON_SEQUENCES = "M6 3v18M18 3v18M6 8h10M14 6l2 2-2 2M18 15H8M10 13l-2 2 2 2";
	private static final String ICON_GRAPH = "M8.5 6a2.5 2.5 0 1 0-5 0a2.5 2.5 0 1 0 5 0M20.5 7a2.5 2.5 0 1 0-5 0a2.5 2.5 0 1 0 5 0"
			+ "M14.5 18a2.5 2.5 0 1 0-5 0a2.5 2.5 0 1 0 5 0M8.3 7l7.3.6M7.2 8.2l3.7 7.6M16.9 9.3l-3.6 6.5";
	private static final String ICON_THREADS = "M3 6h7M13 6h8M3 12h3M9 12h12M3 18h11M17 18h4";
	private static final String ICON_EXCLUSIONS = "M3 5h18l-7 8.5V19l-4 2v-7.5z";

	/** A tag: what the stereotype table gives each component. */
	private static final String ICON_STEREOTYPES = "M3 12V4h8l9 9-8 8zM7.5 8.5h.01";
	private static final String ICON_SETTINGS = "M4 7h10M18 7h2M4 17h4M12 17h8M18.2 7a2.2 2.2 0 1 0-4.4 0a2.2 2.2 0 1 0 4.4 0"
			+ "M12.2 17a2.2 2.2 0 1 0-4.4 0a2.2 2.2 0 1 0 4.4 0";
	private static final String ICON_SUN = "M16 12a4 4 0 1 0-8 0a4 4 0 1 0 8 0M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2"
			+ "M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4";
	private static final String ICON_MOON = "M20.5 13.5A8.5 8.5 0 1 1 10.5 3.5a6.6 6.6 0 0 0 10 10z";
	private static final String ICON_HELP = "M21 12a9 9 0 1 0-18 0a9 9 0 1 0 18 0M9.4 9.3a2.7 2.7 0 0 1 5.2 1"
			+ "c0 1.8-2.6 2.3-2.6 4M12 17.5h.01";
	private static final String ICON_LANGUAGE = "M21 12a9 9 0 1 0-18 0a9 9 0 1 0 18 0M3.5 9h17M3.5 15h17"
			+ "M12 3c-2.4 2.6-3.6 5.6-3.6 9s1.2 6.4 3.6 9M12 3c2.4 2.6 3.6 5.6 3.6 9s-1.2 6.4-3.6 9";
	private static final String ICON_CLOSE = "M6 6l12 12M18 6L6 18";
	private static final String ICON_FLOWS = "M2.5 12h4l2.5-6 4 12 2.5-6h6";
	private static final String ICON_CHEVRON_RIGHT = "M9 6l6 6-6 6";
	private static final String ICON_INTERACTION = "M5 3l14 9-6.5 1.5L15 20l-3 1-2.5-6.5z";
	private static final String ICON_LIVE_ACTIVITY = "M13 2 4 14h6l-1 8 9-12h-6z";
	private static final String ICON_CARD_CHROME = "M3.5 5h17v14h-17zM3.5 9.5h17";
	private static final String ICON_STATUS_BADGE = "M12 3 19 6v6c0 5-3.5 7.5-7 9-3.5-1.5-7-4-7-9V6z";
	private static final String ICON_PORTS_WIRES = "M9 3v5M15 3v5M6 8h12l-1.5 6a4.5 4.5 0 0 1-9 0zM12 17.5v3.5";

	/** Every hosted panel, by the key its function opened it with. */
	private final Map<ViewKey, HostedPanel> panelsByKey = new HashMap<>();

	/** One content area per rail entry. */
	private final Map<RailEntry, EntryArea> areas = new EnumMap<>(RailEntry.class);

	private final Map<PortColorKey, ColorPicker> portColorPickers = new EnumMap<>(PortColorKey.class);

	/** Every preview {@link Node} a given color shows up on, for {@link #showColorCallout}'s callout lines. */
	private final Map<PortColorKey, List<Node>> colorPreviewAnchors = new EnumMap<>(PortColorKey.class);

	/** Every live-repaint closure a given color drives in {@link #buildColorPreviewPane()}'s preview. */
	private final Map<PortColorKey, List<Consumer<Color>>> colorPreviewAppliers = new EnumMap<>(PortColorKey.class);

	/**
	 * The preview's port and icon outlines, drawn in the card outline color
	 * like {@code CovalentComponentCell}'s own shapes, so they follow the
	 * chosen {@link PortColorKey#GRAPHITE} in either theme.
	 */
	private final List<Shape> colorPreviewOutlines = new ArrayList<>();

	/** The preview's card body and hover colors, as last applied: [0] body, [1] hover. */
	private final Color[] colorPreviewBodyAndHover = { Color.WHITE, Color.WHITE };

	/** The preview's cards, repainted when their body or hover color changes. */
	private final List<PreviewCard> colorPreviewCards = new ArrayList<>();

	/** True while the hover color's callout is shown: the first preview card then shows it. */
	private boolean colorPreviewHoverShown;

	/** The preview's port pads, filled with {@link PortColorKey#SLOT_IDLE} like a real idle pad. */
	private final List<Shape> colorPreviewPads = new ArrayList<>();

	/** Mouse-transparent layer the hover callout lines are drawn into - see {@link #buildColorsSection()}. */
	private Pane colorCalloutOverlay;

	private Stage stage;
	private Scene scene;

	/** The theme currently applied - light by default. */
	private SupervisorTheme theme = SupervisorSettings.getEnum(SupervisorSettings.THEME, SupervisorTheme.LIGHT);

	private final ToggleGroup railGroup = new ToggleGroup();
	private StackPane contentStack;
	private RailEntry activeEntry = RailEntry.COMPONENTS;
	private Label statusText;

	private Button recordButton;
	/**
	 * Carries the Record action's own availability, kept apart from
	 * {@link #recordButton} so the button can also be disabled while data
	 * flows are off: a recording is made of those flows.
	 */
	private final Region recordAvailability = new Region();
	private ToggleButton flowsToggle;
	private Timeline recordTimeline;
	private long recordStartMillis;
	/** The zoom steps of the Zoom out and Zoom in buttons. */
	private static final double[] ZOOM_STEPS = { 0.10, 0.15, 0.25, 0.33, 0.50, 0.67, 0.75, 1.00, 1.25, 1.50, 2.00 };
	private static final String ICON_ZOOM_OUT = "M3.5 8 H12.5";
	private static final String ICON_ZOOM_IN = "M3.5 8 H12.5 M8 3.5 V12.5";
	private static final String ICON_RESET = "M4 4v6h6M4.5 10A8 8 0 1 1 6 17";

	private Slider zoomSlider;
	private Label zoomLabel;
	/** The zoom tools of the status bar: Zoom out, the slider, Zoom in - enabled with the visible tab's zoom. */
	private HBox zoomTools;
	private Button zoomOutButton;
	private Button zoomInButton;
	/** Set while the slider is moved to the visible tab's scale, so that it does not ask for it again. */
	private boolean showingZoomScale;

	/** The help window, created when the help is first asked for. */
	private final HelpWindow helpWindow = new HelpWindow();

	/** This tool's single Preferences panel, built lazily when the Settings entry is first shown. */
	private Node preferencesPanel;

	/** The Preferences panel's tabs; its last one hosts the stereotype table's panel. */
	private TabPane preferencesTabs;

	/** The Preferences panel's Stereotypes tab, filled with the stereotype table's panel once it is opened. */
	private Tab stereotypesTab;

	/** The stereotype table panel's key while it is hosted in {@link #stereotypesTab}, else {@code null}. */
	private ViewKey stereotypesKey;

	/** The current tab's Record ActionKey, or {@code null} while unbound. */
	private ActionKey currentRecordKey;

	/** The current tab's zoom "set scale" ActionKey, or {@code null} while unbound. */
	private ActionKey currentZoomSetScaleKey;

	/** Descriptor of the panel currently on screen, or {@code null} when none is. */
	private SupervisorTabDescriptor visibleDescriptor;

	/** Value keys currently bound for {@link #visibleDescriptor}, each {@code null} when not applicable. */
	private ActionKey boundRecordingStateKey;
	private ActionKey boundZoomScaleKey;
	private final Consumer<Object> recordingStateConsumer = this::onRecordingStateChanged;
	private final Consumer<Object> zoomScaleConsumer = this::onZoomScaleChanged;

	/** Builds the supervisor's window on the JavaFX application thread when the component activates. */
	@Activation
	public void display() {
		if (Platform.isFxApplicationThread()) {
			this.initFx();
		} else {
			Platform.runLater(this::initFx);
		}
	}

	/**
	 * Stops the recording timer and closes the help window and the supervisor's window when the component passivates.
	 */
	@Passivation
	public void dispose() {
		try {
			Platform.runLater(() -> {
				if (this.recordTimeline != null) {
					this.recordTimeline.stop();
				}
				this.helpWindow.close();
				if (this.stage != null) {
					this.stage.close();
					this.stage = null;
				}
			});
		} catch (IllegalStateException e) {
			// The JavaFX toolkit is already stopped, and its windows with it.
		}
	}

	private void initFx() {
		this.contentStack = new StackPane();
		for (RailEntry entry : RailEntry.values()) {
			EntryArea area = new EntryArea(entry);
			this.areas.put(entry, area);
			this.contentStack.getChildren().add(area.root);
			this.renderArea(area);
		}

		BorderPane root = new BorderPane();
		root.setTop(this.buildHeader());
		root.setLeft(this.buildRail());
		root.setCenter(this.contentStack);
		root.setBottom(this.buildStatusBar());

		loadEmbeddedFonts();
		this.scene = new Scene(root, 1280, 820);
		this.scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F1), this::showHelp);
		this.applyStylesheets();
		this.showEntry(this.activeEntry);

		this.stage = new Stage();
		this.stage.setTitle(SupervisionHmiTexts.get("title"));
		for (int size : ICON_SIZES) {
			this.stage.getIcons().add(new Image(
					SupervisionHmiViewImpl.class.getResource("icons/covalent-" + size + ".png").toExternalForm()));
		}
		this.stage.setScene(this.scene);
		this.restoreWindowPlacement();
		this.stage.show();
		this.rememberWindowPlacement();
	}

	/** Puts the window back where it was left, if that place is still on a screen. */
	private void restoreWindowPlacement() {
		double x = SupervisorSettings.getDouble(SupervisorSettings.WINDOW_X, Double.NaN);
		double y = SupervisorSettings.getDouble(SupervisorSettings.WINDOW_Y, Double.NaN);
		double width = SupervisorSettings.getDouble(SupervisorSettings.WINDOW_WIDTH, Double.NaN);
		double height = SupervisorSettings.getDouble(SupervisorSettings.WINDOW_HEIGHT, Double.NaN);
		if (!Double.isNaN(x) && !Double.isNaN(y) && width > 200 && height > 150
				&& !Screen.getScreensForRectangle(x, y, width, height).isEmpty()) {
			this.stage.setX(x);
			this.stage.setY(y);
			this.stage.setWidth(width);
			this.stage.setHeight(height);
		}
		this.stage.setMaximized(SupervisorSettings.getBoolean(SupervisorSettings.WINDOW_MAXIMIZED, false));
	}

	/** Stores the window's place and size as they change; not while maximized, to restore it unmaximized. */
	private void rememberWindowPlacement() {
		Stage window = this.stage;
		Runnable store = () -> {
			if (window.isMaximized() || window.isIconified()) {
				return;
			}
			SupervisorSettings.putDouble(SupervisorSettings.WINDOW_X, window.getX());
			SupervisorSettings.putDouble(SupervisorSettings.WINDOW_Y, window.getY());
			SupervisorSettings.putDouble(SupervisorSettings.WINDOW_WIDTH, window.getWidth());
			SupervisorSettings.putDouble(SupervisorSettings.WINDOW_HEIGHT, window.getHeight());
		};
		// Moving or resizing fires many changes: stored once it settles.
		PauseTransition settle = new PauseTransition(Duration.millis(400));
		settle.setOnFinished(event -> store.run());
		for (ObservableValue<?> property : List.of(window.xProperty(), window.yProperty(),
				window.widthProperty(), window.heightProperty())) {
			property.addListener(observable -> settle.playFromStart());
		}
		window.maximizedProperty().addListener((observable, wasMaximized, isMaximized) ->
				SupervisorSettings.putBoolean(SupervisorSettings.WINDOW_MAXIMIZED, isMaximized));
	}

	private static void loadEmbeddedFonts() {
		for (String font : EMBEDDED_FONTS) {
			Font.loadFont(SupervisionHmiViewImpl.class.getResourceAsStream("fonts/" + font), 13);
		}
	}

	/**
	 * Switches the whole tool to {@code newTheme}: swaps this scene's theme
	 * stylesheet (popups and context menus follow, since they are styled
	 * from their owner's scene), then asks ComponentDiagram to switch its
	 * port/wire palette - through {@code ActionConsumerServices}, same as
	 * the Preferences panel's color pickers, never a direct Services
	 * reference (see class javadoc).
	 */
	private void applyTheme(final SupervisorTheme newTheme) {
		if (newTheme == this.theme) {
			return;
		}
		this.theme = newTheme;
		SupervisorSettings.putEnum(SupervisorSettings.THEME, newTheme);
		this.applyStylesheets();
		this.helpWindow.setTheme(newTheme);
		this.actionManagerServices.executeAction(ComponentDiagramPreferencesActionKey.SetTheme, newTheme);
	}

	private void applyStylesheets() {
		String themeStylesheet = this.theme == SupervisorTheme.DARK ? "supervision-dark.css" : "supervision-light.css";
		this.scene.getStylesheets().setAll(stylesheetUrl(BASE_STYLESHEET), stylesheetUrl(themeStylesheet));
	}

	private static String stylesheetUrl(final String name) {
		return SupervisionHmiViewImpl.class.getResource(name).toExternalForm();
	}

	// --- Header ---

	private Node buildHeader() {
		Label name = new Label("Covalent");
		name.getStyleClass().add("sv-brand-name");
		Label subtitle = new Label(SupervisionHmiTexts.get("brand.sub"));
		subtitle.getStyleClass().add("sv-brand-sub");
		VBox brandText = new VBox(name, subtitle);
		brandText.setAlignment(Pos.CENTER_LEFT);
		HBox brand = new HBox(9, this.buildLogo(), brandText);
		brand.setAlignment(Pos.CENTER_LEFT);

		Circle recordDot = new Circle(5);
		recordDot.getStyleClass().add("sv-record-dot");
		this.recordButton = new Button(SupervisionHmiTexts.get("record"), recordDot);
		Tooltips.install(this.recordButton, SupervisionHmiTexts.get("record.tip"));
		this.recordButton.getStyleClass().add("sv-record-button");
		this.recordAvailability.setDisable(true);
		this.recordButton.setOnAction(event -> this.triggerCurrentKey(this.currentRecordKey));
		this.recordTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> this.refreshRecordLabel()));
		this.recordTimeline.setCycleCount(Animation.INDEFINITE);

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);

		this.flowsToggle = new ToggleButton(SupervisionHmiTexts.get("flows"), icon(ICON_FLOWS, 16));
		this.flowsToggle.getStyleClass().add("sv-flows-toggle");
		Tooltips.install(this.flowsToggle, SupervisionHmiTexts.get("flows.tip"));
		this.flowsToggle.setOnAction(event -> this.onFlowsToggled(this.flowsToggle.isSelected()));
		this.actionManagerServices.bindActionValue(ContainerManagerActionKey.SetFlowsEnabled,
				value -> Platform.runLater(() -> this.flowsToggle.setSelected(Boolean.TRUE.equals(value))));
		this.recordButton.disableProperty()
				.bind(this.recordAvailability.disableProperty().or(this.flowsToggle.selectedProperty().not()));

		HBox header = new HBox(brand, this.accessContainerManagerViewParameters.getSelectorRoot(), spacer,
				this.flowsToggle, this.recordButton, this.buildThemeSwitch());
		header.getStyleClass().add("sv-header");
		return header;
	}

	/** Switching data flows off first stops an ongoing recording, which is made of them. */
	private void onFlowsToggled(final boolean enabled) {
		if (!enabled && this.recordButton.getStyleClass().contains(RECORDING_STYLE_CLASS)) {
			this.triggerCurrentKey(this.currentRecordKey);
		}
		this.actionManagerServices.executeAction(ContainerManagerActionKey.SetFlowsEnabled, enabled);
	}

	/** Brand mark: three touching hexagon outlines, two side by side and one resting on top. */
	private Node buildLogo() {
		double size = 5;
		double dx = Math.sqrt(3) * size / 2;
		double refY = 0.75 * size;
		Group hexagons = new Group(logoHexagon(0, refY - 1.5 * size, size), logoHexagon(-dx, refY, size),
				logoHexagon(dx, refY, size));
		StackPane logo = new StackPane(hexagons);
		logo.getStyleClass().add("sv-logo-box");
		return logo;
	}

	private static Polygon logoHexagon(final double x, final double y, final double size) {
		Polygon hexagon = new Polygon();
		for (int i = 0; i < 6; i++) {
			double angle = Math.PI / 6 + Math.PI / 3 * i;
			hexagon.getPoints().addAll(x + size * Math.cos(angle), y + size * Math.sin(angle));
		}
		hexagon.getStyleClass().add("sv-logo-hex");
		return hexagon;
	}

	/** Two mutually exclusive toggles, one per {@link SupervisorTheme}, that never leave both unselected. */
	private Node buildThemeSwitch() {
		ToggleGroup themes = new ToggleGroup();
		ToggleButton light = new ToggleButton(null, icon(ICON_SUN, 16));
		Tooltips.install(light, SupervisionHmiTexts.get("theme.light"));
		light.setToggleGroup(themes);
		light.setSelected(this.theme == SupervisorTheme.LIGHT);
		light.setOnAction(event -> this.applyTheme(SupervisorTheme.LIGHT));
		ToggleButton dark = new ToggleButton(null, icon(ICON_MOON, 16));
		Tooltips.install(dark, SupervisionHmiTexts.get("theme.dark"));
		dark.setToggleGroup(themes);
		dark.setSelected(this.theme == SupervisorTheme.DARK);
		dark.setOnAction(event -> this.applyTheme(SupervisorTheme.DARK));
		keepOneSelected(themes);

		HBox themeSwitch = new HBox(light, dark);
		themeSwitch.getStyleClass().add("sv-theme-switch");
		return themeSwitch;
	}

	/**
	 * Recording state of the tab the Record button controls - see its
	 * descriptor's {@code recordingStateActionKey()}: the recording's start
	 * time in epoch milliseconds, or {@code 0} while not recording.
	 */
	private void onRecordingStateChanged(final Object value) {
		long startMillis = value instanceof Number number ? number.longValue() : 0L;
		Platform.runLater(() -> this.showRecording(startMillis));
	}

	private void showRecording(final long startMillis) {
		boolean recording = startMillis > 0;
		this.recordStartMillis = startMillis;
		boolean wasRecording = this.recordButton.getStyleClass().contains(RECORDING_STYLE_CLASS);
		if (recording && !wasRecording) {
			this.recordButton.getStyleClass().add(RECORDING_STYLE_CLASS);
			this.recordTimeline.playFromStart();
		} else if (!recording && wasRecording) {
			this.recordButton.getStyleClass().remove(RECORDING_STYLE_CLASS);
			this.recordTimeline.stop();
		}
		this.refreshRecordLabel();
	}

	private void refreshRecordLabel() {
		if (!this.recordButton.getStyleClass().contains(RECORDING_STYLE_CLASS)) {
			this.recordButton.setText(SupervisionHmiTexts.get("record"));
			return;
		}
		long seconds = (System.currentTimeMillis() - this.recordStartMillis) / 1000;
		this.recordButton.setText(String.format("REC  %02d:%02d", seconds / 60, seconds % 60));
	}

	// --- Rail ---

	private Node buildRail() {
		VBox rail = new VBox();
		rail.getStyleClass().add("sv-rail");
		Region spacer = new Region();
		VBox.setVgrow(spacer, Priority.ALWAYS);
		rail.getChildren().addAll(this.railButton(RailEntry.COCKPIT), this.railButton(RailEntry.COMPONENTS), this.railButton(RailEntry.SEQUENCES),
				this.railButton(RailEntry.GRAPH), this.railButton(RailEntry.THREADS), spacer, this.railButton(RailEntry.EXCLUSIONS),
				this.railButton(RailEntry.SETTINGS), this.helpButton());
		keepOneSelected(this.railGroup);
		return rail;
	}

	/** The rail's Help button: a menu with the help, the keyboard shortcuts and the About box. */
	private Button helpButton() {
		Button button = new Button(SupervisionHmiTexts.get("help"), icon(ICON_HELP, 22));
		button.getStyleClass().add("sv-rail-button");
		MenuItem help = new MenuItem(SupervisionHmiTexts.get("help.supervisor"));
		help.setAccelerator(new KeyCodeCombination(KeyCode.F1));
		help.setOnAction(event -> this.showHelp());
		MenuItem shortcuts = new MenuItem(SupervisionHmiTexts.get("help.shortcuts"));
		shortcuts.setOnAction(event -> this.helpWindow.show(this.stage, "shortcuts", this.theme));
		MenuItem about = new MenuItem(SupervisionHmiTexts.get("help.about"));
		about.setOnAction(event -> AboutDialog.show(this.stage, this.buildLogo()));
		ContextMenu menu = new ContextMenu(help, shortcuts, new SeparatorMenuItem(), about);
		button.setOnAction(event -> menu.show(button, Side.RIGHT, 6, 0));
		return button;
	}

	/** Opens the help on the section of the function shown. */
	private void showHelp() {
		String section = switch (this.activeEntry) {
			case COCKPIT -> "cockpit";
			case COMPONENTS -> "components";
			case SEQUENCES -> "sequences";
			case GRAPH -> "graph";
			case THREADS -> "threads";
			case EXCLUSIONS -> "exclusions";
			case SETTINGS, STEREOTYPES -> "settings";
		};
		this.helpWindow.show(this.stage, section, this.theme);
	}

	private ToggleButton railButton(final RailEntry entry) {
		ToggleButton button = new ToggleButton(entryLabel(entry), icon(entryIcon(entry), 22));
		button.getStyleClass().add("sv-rail-button");
		button.setToggleGroup(this.railGroup);
		button.setOnAction(event -> this.onRailEntryClicked(entry));
		this.areas.get(entry).railButton = button;
		return button;
	}

	/**
	 * Shows {@code entry}'s area. An entry with no panel yet opens its
	 * function, as its empty-state button would: a new component diagram,
	 * the dependency graph or the exclusion policy; Sequences only shows its
	 * empty state, since recordings start from a component diagram's Record
	 * button.
	 */
	private void onRailEntryClicked(final RailEntry entry) {
		this.showEntry(entry);
		if (this.areas.get(entry).panels.isEmpty()) {
			this.openFunction(entry);
		}
	}

	private void openFunction(final RailEntry entry) {
		switch (entry) {
			case COCKPIT -> this.actionManagerServices.executeAction(CockpitActionKey.Toggle);
			case COMPONENTS -> this.actionManagerServices.executeAction(ComponentDiagramToolActionKey.NewDiagram);
			case GRAPH -> this.actionManagerServices.executeAction(DependencyManagementActionKey.Toggle);
			case THREADS -> this.actionManagerServices.executeAction(ThreadsActionKey.Show);
			case EXCLUSIONS -> this.actionManagerServices.executeAction(ExclusionPolicyActionKey.Toggle);
			case STEREOTYPES -> this.actionManagerServices.executeAction(StereotypePolicyActionKey.Show);
			case SEQUENCES, SETTINGS -> {
				// Nothing to open: see onRailEntryClicked's javadoc.
			}
		}
	}

	private static String entryLabel(final RailEntry entry) {
		return SupervisionHmiTexts.get("rail." + entry.name());
	}

	private static String entryIcon(final RailEntry entry) {
		return switch (entry) {
			case COCKPIT -> ICON_COCKPIT;
			case COMPONENTS -> ICON_COMPONENTS;
			case SEQUENCES -> ICON_SEQUENCES;
			case GRAPH -> ICON_GRAPH;
			case THREADS -> ICON_THREADS;
			case EXCLUSIONS -> ICON_EXCLUSIONS;
			case STEREOTYPES -> ICON_STEREOTYPES;
			case SETTINGS -> ICON_SETTINGS;
		};
	}

	// --- Content areas ---

	/** One rail entry's content area: its panels as sub-tabs plus a picker, or its empty state. */
	private final class EntryArea {

		private final RailEntry entry;
		private final List<HostedPanel> panels = new ArrayList<>();
		private final ToggleGroup tabGroup = new ToggleGroup();
		private final HBox tabs = new HBox(2);
		private final ComboBox<HostedPanel> picker = new ComboBox<>();
		private final HBox panelBar;
		private final StackPane content = new StackPane();
		private final BorderPane root = new BorderPane();
		private HostedPanel selected;
		private ToggleButton railButton;

		/** Set while {@link #renderArea} updates the picker, so its listener ignores that change. */
		private boolean rendering;

		private EntryArea(final RailEntry entry) {
			this.entry = entry;
			this.picker.getStyleClass().add("sv-panel-picker");
			this.picker.valueProperty().addListener((observable, oldPanel, newPanel) -> {
				if (!this.rendering && newPanel != null && newPanel != this.selected) {
					SupervisionHmiViewImpl.this.selectPanel(this, newPanel);
				}
			});
			keepOneSelected(this.tabGroup);
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			this.panelBar = new HBox(4, this.tabs, spacer, this.picker);
			this.panelBar.getStyleClass().add("sv-panel-bar");
			this.root.setTop(this.panelBar);
			this.root.setCenter(this.content);
		}
	}

	/** A panel hosted for one {@link ViewKey}, with its own sub-tab. */
	private static final class HostedPanel {

		private final ViewKey key;
		private final ToggleButton tab = new ToggleButton();
		private Node node;
		private SupervisorTabDescriptor descriptor;
		private String title = "";
		/** The title the user or the owner set, kept when the panel is opened again. */
		private String userTitle;

		private HostedPanel(final ViewKey key) {
			this.key = key;
		}

		@Override
		public String toString() {
			return this.title;
		}
	}

	/** Brings {@code entry}'s area on screen and reports its selected panel, if any, as visible. */
	private void showEntry(final RailEntry entry) {
		this.activeEntry = entry;
		for (EntryArea area : this.areas.values()) {
			area.root.setVisible(area.entry == entry);
		}
		EntryArea area = this.areas.get(entry);
		if (area.railButton != null) {
			area.railButton.setSelected(true);
		}
		if (entry == RailEntry.SETTINGS && this.preferencesPanel == null) {
			this.preferencesPanel = this.buildPreferencesPanel();
			this.renderArea(area);
		}
		this.onVisiblePanelChanged(area.selected);
	}

	/**
	 * Shows {@code panel}. A panel no longer hosted is ignored: the click that closes a sub-tab on its
	 * close button also reaches the sub-tab itself, after the panel is gone, and must not bring it back.
	 */
	private void selectPanel(final EntryArea area, final HostedPanel panel) {
		if (!area.panels.contains(panel)) {
			return;
		}
		area.selected = panel;
		this.renderArea(area);
		if (area.entry == this.activeEntry) {
			this.onVisiblePanelChanged(panel);
		}
	}

	/** Rebuilds {@code area}'s sub-tabs, picker and content from its panel list and selection. */
	private void renderArea(final EntryArea area) {
		area.rendering = true;
		try {
			area.tabs.getChildren().setAll(area.panels.stream().map(panel -> panel.tab).toList());
			if (area.selected != null) {
				area.selected.tab.setSelected(true);
			}
			boolean hasPanels = !area.panels.isEmpty();
			area.panelBar.setVisible(hasPanels);
			area.panelBar.setManaged(hasPanels);
			boolean severalPanels = area.panels.size() > 1;
			area.picker.setVisible(severalPanels);
			area.picker.setManaged(severalPanels);
			area.picker.getItems().setAll(area.panels);
			area.picker.setValue(area.selected);
			area.content.getChildren().setAll(area.selected != null ? area.selected.node : this.emptyState(area.entry));
		} finally {
			area.rendering = false;
		}
		if (area.entry == this.activeEntry) {
			this.refreshStatusText();
		}
	}

	/** What an area shows without any panel: an invitation to open its function, or Preferences for Settings. */
	private Node emptyState(final RailEntry entry) {
		if (entry == RailEntry.SETTINGS) {
			return this.preferencesPanel != null ? this.preferencesPanel : new Region();
		}
		Label title = new Label();
		title.getStyleClass().add("sv-empty-title");
		Label text = new Label();
		text.getStyleClass().add("sv-empty-text");
		VBox box = new VBox(title, text);
		box.getStyleClass().add("sv-empty-state");
		switch (entry) {
			case COCKPIT -> {
				title.setText(SupervisionHmiTexts.get("empty.COCKPIT.title"));
				text.setText(SupervisionHmiTexts.get("empty.COCKPIT.text"));
				box.getChildren().add(this.emptyStateButton(SupervisionHmiTexts.get("empty.COCKPIT.button"), entry));
			}
			case COMPONENTS -> {
				title.setText(SupervisionHmiTexts.get("empty.COMPONENTS.title"));
				text.setText(SupervisionHmiTexts.get("empty.COMPONENTS.text"));
				box.getChildren().add(this.emptyStateButton(SupervisionHmiTexts.get("empty.COMPONENTS.button"), entry));
			}
			case SEQUENCES -> {
				title.setText(SupervisionHmiTexts.get("empty.SEQUENCES.title"));
				text.setText(SupervisionHmiTexts.get("empty.SEQUENCES.text"));
			}
			case THREADS -> {
				title.setText(SupervisionHmiTexts.get("empty.THREADS.title"));
				text.setText(SupervisionHmiTexts.get("empty.THREADS.text"));
				box.getChildren().add(this.emptyStateButton(SupervisionHmiTexts.get("empty.THREADS.button"), entry));
			}
			case GRAPH -> {
				title.setText(SupervisionHmiTexts.get("empty.GRAPH.title"));
				text.setText(SupervisionHmiTexts.get("empty.GRAPH.text"));
				box.getChildren().add(this.emptyStateButton(SupervisionHmiTexts.get("empty.GRAPH.button"), entry));
			}
			case EXCLUSIONS -> {
				title.setText(SupervisionHmiTexts.get("empty.EXCLUSIONS.title"));
				text.setText(SupervisionHmiTexts.get("empty.EXCLUSIONS.text"));
				box.getChildren().add(this.emptyStateButton(SupervisionHmiTexts.get("empty.EXCLUSIONS.button"), entry));
			}
			case SETTINGS, STEREOTYPES -> {
				// Settings is handled above; the stereotype table is a tab of its page.
			}
		}
		return box;
	}

	private Button emptyStateButton(final String text, final RailEntry entry) {
		Button button = new Button(text);
		VBox.setMargin(button, new Insets(10, 0, 0, 0));
		button.setOnAction(event -> this.openFunction(entry));
		return button;
	}

	/**
	 * A sub-tab's own close button: runs the panel's {@code closeActionKey()}
	 * when it declares one, so the function disposes of that panel's state
	 * exactly as with its own in-panel close control, then removes the panel
	 * here - closing an already-removed panel is a no-op.
	 */
	private void closeFromTab(final HostedPanel panel) {
		ActionKey closeKey = panel.descriptor.closeActionKey();
		if (closeKey != null) {
			this.actionManagerServices.executeAction(closeKey);
		}
		this.removePanel(panel.key);
	}

	private void removePanel(final ViewKey key) {
		HostedPanel panel = this.panelsByKey.remove(key);
		if (panel == null) {
			return;
		}
		EntryArea area = this.areas.get(panel.descriptor.railEntry());
		int index = area.panels.indexOf(panel);
		area.panels.remove(panel);
		panel.tab.setToggleGroup(null);
		if (area.selected == panel) {
			area.selected = area.panels.isEmpty() ? null : area.panels.get(Math.max(0, index - 1));
			this.renderArea(area);
			if (area.entry == this.activeEntry) {
				this.onVisiblePanelChanged(area.selected);
			}
		} else {
			this.renderArea(area);
		}
	}

	/**
	 * Called whenever the panel on screen may have changed. Rebinds the
	 * Record button's recording state and the zoom readout to the new
	 * panel's value keys, then broadcasts its descriptor as {@code
	 * TabSelected} - see class javadoc. Same rule as the shared controls'
	 * own bindings: a function the new panel does not support - or a
	 * {@code null} panel (an empty area, Settings) - stays bound to the
	 * last panel that did.
	 */
	private void onVisiblePanelChanged(final HostedPanel panel) {
		this.refreshStatusText();
		SupervisorTabDescriptor descriptor = panel == null ? null : panel.descriptor;
		if (descriptor == null || descriptor == this.visibleDescriptor) {
			return;
		}
		this.visibleDescriptor = descriptor;
		this.rebindVisibleValueKeys(descriptor);
		this.actionManagerServices.executeAction(SupervisionHmiActionKey.TabSelected, descriptor);
	}

	private void rebindVisibleValueKeys(final SupervisorTabDescriptor descriptor) {
		if (descriptor.supportedToolbarFunctions().contains(ToolbarFunction.RECORD_TOGGLE)) {
			if (this.boundRecordingStateKey != null) {
				this.actionManagerServices.unbindActionValue(this.boundRecordingStateKey, this.recordingStateConsumer);
			}
			this.boundRecordingStateKey = descriptor.recordingStateActionKey();
			this.showRecording(0L);
			if (this.boundRecordingStateKey != null) {
				this.actionManagerServices.bindActionValue(this.boundRecordingStateKey, this.recordingStateConsumer);
			}
		}
		if (descriptor.supportedToolbarFunctions().contains(ToolbarFunction.ZOOM_SLIDER)) {
			if (this.boundZoomScaleKey != null) {
				this.actionManagerServices.unbindActionValue(this.boundZoomScaleKey, this.zoomScaleConsumer);
			}
			this.boundZoomScaleKey = descriptor.zoomCurrentScaleActionKey();
			this.zoomLabel.setText("");
			this.actionManagerServices.bindActionValue(this.boundZoomScaleKey, this.zoomScaleConsumer);
		}
	}

	// --- Status bar ---

	private Node buildStatusBar() {
		this.statusText = new Label();
		this.statusText.getStyleClass().add("sv-status-text");

		// On a logarithmic scale, so that 10 %, 100 % and 200 % spread evenly along the track.
		this.zoomSlider = new Slider(Math.log(ViewportGestures.DEFAULT_MIN_SCALE),
				Math.log(ViewportGestures.DEFAULT_MAX_SCALE), 0);
		this.zoomSlider.getStyleClass().add("sv-zoom-slider");
		// Zooms live, while dragged and on a click on the track alike.
		this.zoomSlider.valueProperty().addListener((observable, previous, value) -> {
			if (!this.showingZoomScale) {
				this.setZoomScale(Math.exp(value.doubleValue()));
			}
		});
		this.zoomOutButton = Toolbox.iconButton(ICON_ZOOM_OUT, SupervisionHmiTexts.get("zoom.out"));
		this.zoomOutButton.setOnAction(event -> this.setZoomScale(zoomStep(this.zoomScale(), false)));
		this.zoomInButton = Toolbox.iconButton(ICON_ZOOM_IN, SupervisionHmiTexts.get("zoom.in"));
		this.zoomInButton.setOnAction(event -> this.setZoomScale(zoomStep(this.zoomScale(), true)));
		for (Button step : List.of(this.zoomOutButton, this.zoomInButton)) {
			step.getStyleClass().add("sv-zoom-step");
		}
		this.zoomTools = new HBox(2, this.zoomOutButton, this.zoomSlider, this.zoomInButton);
		this.zoomTools.setAlignment(Pos.CENTER);
		this.zoomTools.setDisable(true);
		this.zoomLabel = new Label();
		this.zoomLabel.getStyleClass().add("sv-zoom-label");
		Tooltips.install(this.zoomLabel, SupervisionHmiTexts.get("zoom.reset"));
		this.zoomLabel.setOnMouseClicked(event -> {
			if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !this.zoomTools.isDisabled()) {
				this.setZoomScale(1.0);
			}
		});

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox statusBar = new HBox(this.statusText, spacer, this.zoomTools, this.zoomLabel);
		statusBar.getStyleClass().add("sv-statusbar");
		return statusBar;
	}

	private void refreshStatusText() {
		if (this.statusText == null) {
			return;
		}
		EntryArea area = this.areas.get(this.activeEntry);
		String text = entryLabel(this.activeEntry);
		if (area.selected != null && !area.selected.title.isEmpty() && !area.selected.title.equals(text)) {
			text += "  ·  " + area.selected.title;
		}
		this.statusText.setText(text);
		// Sequences scroll with the wheel and zoom with Ctrl + wheel; the other diagrams zoom with it.
		Tooltips.install(this.zoomSlider, this.activeEntry == RailEntry.SEQUENCES
				? SupervisionHmiTexts.get("zoom.tip.sequences")
				: SupervisionHmiTexts.get("zoom.tip"));
	}

	/** Current zoom scale of the visible tab - see its descriptor's {@code zoomCurrentScaleActionKey()}. */
	private void onZoomScaleChanged(final Object value) {
		if (value instanceof Number scale) {
			Platform.runLater(() -> this.showZoomScale(scale.doubleValue()));
		}
	}

	/** Shows the visible tab's zoom scale: its percentage, the slider's position, the step buttons' limits. */
	private void showZoomScale(final double scale) {
		this.zoomLabel.setText(Math.round(scale * 100) + "%");
		this.showingZoomScale = true;
		try {
			this.zoomSlider.setValue(Math.log(scale));
		} finally {
			this.showingZoomScale = false;
		}
		this.zoomOutButton.setDisable(scale <= ViewportGestures.DEFAULT_MIN_SCALE * 1.001);
		this.zoomInButton.setDisable(scale >= ViewportGestures.DEFAULT_MAX_SCALE * 0.999);
	}

	/** @return the zoom scale the slider shows */
	private double zoomScale() {
		return Math.exp(this.zoomSlider.getValue());
	}

	/** Asks the visible tab for a zoom scale, within the zoom's limits. */
	private void setZoomScale(final double scale) {
		if (this.currentZoomSetScaleKey != null) {
			this.actionManagerServices.executeAction(this.currentZoomSetScaleKey, Math.max(
					ViewportGestures.DEFAULT_MIN_SCALE, Math.min(ViewportGestures.DEFAULT_MAX_SCALE, scale)));
		}
	}

	/**
	 * Returns the next zoom step from a scale, as the step buttons go.
	 *
	 * @param scale the current scale
	 * @param in    {@code true} for the next larger step, {@code false} for the next smaller one
	 * @return that step, or the limit when there is none
	 */
	static double zoomStep(final double scale, final boolean in) {
		if (in) {
			for (double step : ZOOM_STEPS) {
				if (step > scale * 1.001) {
					return step;
				}
			}
			return ZOOM_STEPS[ZOOM_STEPS.length - 1];
		}
		for (int i = ZOOM_STEPS.length - 1; i >= 0; i--) {
			if (ZOOM_STEPS[i] < scale * 0.999) {
				return ZOOM_STEPS[i];
			}
		}
		return ZOOM_STEPS[0];
	}

	// --- Shared helpers ---

	/** A stroke icon drawn from 24x24 SVG path data, scaled to {@code size} pixels. */
	private static Node icon(final String pathData, final double size) {
		SVGPath path = new SVGPath();
		path.setContent(pathData);
		path.getStyleClass().add("sv-icon");
		double scale = size / 24;
		path.setScaleX(scale);
		path.setScaleY(scale);
		return new Group(path);
	}

	/** Re-selects the previous toggle whenever a click on the selected one would leave {@code group} empty. */
	private static void keepOneSelected(final ToggleGroup group) {
		group.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
			if (newToggle == null && oldToggle != null) {
				oldToggle.setSelected(true);
			}
		});
	}

	// --- Preferences (Settings entry) ---

	private Node buildPreferencesPanel() {
		TabPane tabs = new TabPane();
		tabs.getStyleClass().add("sv-prefs-tabs");
		tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
		this.stereotypesTab = new Tab(entryLabel(RailEntry.STEREOTYPES), new Region());
		// The stereotype table's panel belongs to its own function: it is asked for when its tab is first shown.
		this.stereotypesTab.selectedProperty().addListener((observable, wasSelected, selected) -> {
			if (selected && this.stereotypesKey == null) {
				this.actionManagerServices.executeAction(StereotypePolicyActionKey.Show);
			}
		});
		tabs.getTabs().addAll(new Tab(SupervisionHmiTexts.get("prefs.general"), this.buildGeneralPreferencesTab()),
				new Tab(SupervisionHmiTexts.get("prefs.colors"), this.buildColorsSection()), this.stereotypesTab);
		this.preferencesTabs = tabs;
		return tabs;
	}

	/** Hosts the stereotype table's panel in the Preferences' Stereotypes tab, and brings that tab forward. */
	private void showStereotypesTab(final ViewKey key, final Node panel) {
		this.stereotypesKey = key;
		this.showEntry(RailEntry.SETTINGS);
		this.stereotypesTab.setContent(panel);
		this.preferencesTabs.getSelectionModel().select(this.stereotypesTab);
	}

	/**
	 * Every textual (non-color) preference, grouped into one bordered
	 * {@link #preferenceCategory} box per domain - Data flows, Threads,
	 * Components, Language, Diagram - rather than one long list of controls
	 * separated only by thin rules. The boxes are laid out in a two-column grid that follows the
	 * panel's width.
	 */
	private Node buildGeneralPreferencesTab() {
		HBox autoLayoutOnHide = this.toggleRow(SupervisionHmiTexts.get("pref.autoLayoutOnHide"),
				SupervisorSettings.getBoolean(SupervisorSettings.AUTO_LAYOUT_ON_HIDE, false), isSelected -> {
					SupervisorSettings.putBoolean(SupervisorSettings.AUTO_LAYOUT_ON_HIDE, isSelected);
					this.actionManagerServices.executeAction(ComponentDiagramPreferencesActionKey.ToggleAutoLayoutOnHide,
							isSelected);
				});

		HBox dynamicObservationByDefault = this.toggleRow(SupervisionHmiTexts.get("pref.liveDrawing"),
				SupervisorSettings.getBoolean(SupervisorSettings.LIVE_DRAWING_DEFAULT, true), isSelected -> {
					SupervisorSettings.putBoolean(SupervisorSettings.LIVE_DRAWING_DEFAULT, isSelected);
					this.actionManagerServices.executeAction(
							SequenceDiagramPreferenceActionKey.ToggleDynamicObservationDefault, isSelected);
				});

		HBox flowsOnConnect = this.toggleRow(SupervisionHmiTexts.get("pref.flowsOnConnect"),
				SupervisorSettings.getBoolean(SupervisorSettings.FLOWS_ON_CONNECT, false), isSelected -> {
					SupervisorSettings.putBoolean(SupervisorSettings.FLOWS_ON_CONNECT, isSelected);
					this.actionManagerServices.executeAction(ContainerManagerActionKey.SetFlowsEnabledByDefault, isSelected);
				});

		ComboBox<TimelineStyle> timelineStyle = new ComboBox<>();
		timelineStyle.getItems().setAll(TimelineStyle.values());
		timelineStyle.setValue(SupervisorSettings.getEnum(SupervisorSettings.TIMELINE_STYLE, TimelineStyle.SWITCH));
		timelineStyle.valueProperty().addListener((observable, oldStyle, newStyle) -> {
			if (newStyle != null) {
				SupervisorSettings.putEnum(SupervisorSettings.TIMELINE_STYLE, newStyle);
				this.actionManagerServices.executeAction(SequenceDiagramPreferenceActionKey.SetTimelineStyle, newStyle);
			}
		});
		Label timelineStyleLabel = new Label(SupervisionHmiTexts.get("pref.timeline"));
		HBox timelineStyleRow = new HBox(10, timelineStyleLabel, timelineStyle);
		timelineStyleRow.setAlignment(Pos.CENTER_LEFT);

		Label snapshotFolder = new Label(Snapshots.directory().getPath());
		snapshotFolder.getStyleClass().addAll("sv-preference-path", "sv-preference-path-chip");
		snapshotFolder.setMaxWidth(Double.MAX_VALUE);
		Tooltips.install(snapshotFolder, SupervisionHmiTexts.get("pref.snapshotFolder.tip"));
		Button chooseSnapshotFolder = new Button(SupervisionHmiTexts.get("pref.choose"));
		chooseSnapshotFolder.setOnAction(event -> {
			DirectoryChooser chooser = new DirectoryChooser();
			chooser.setTitle(SupervisionHmiTexts.get("pref.snapshotFolder"));
			chooser.setInitialDirectory(Snapshots.directory());
			File folder = chooser.showDialog(this.stage);
			if (folder != null) {
				SupervisorSettings.putString(SupervisorSettings.SNAPSHOT_DIRECTORY, folder.getAbsolutePath());
				snapshotFolder.setText(folder.getAbsolutePath());
			}
		});
		HBox snapshotFolderRow = new HBox(10, new Label(SupervisionHmiTexts.get("pref.snapshotFolder")), snapshotFolder, chooseSnapshotFolder);
		snapshotFolderRow.setAlignment(Pos.CENTER_LEFT);
		HBox.setHgrow(snapshotFolder, Priority.ALWAYS);

		Label viewsFolder = new Label(viewsFolder().getPath());
		viewsFolder.getStyleClass().addAll("sv-preference-path", "sv-preference-path-chip");
		viewsFolder.setMaxWidth(Double.MAX_VALUE);
		Tooltips.install(viewsFolder, SupervisionHmiTexts.get("pref.viewsFolder.tip"));
		Button chooseViewsFolder = new Button(SupervisionHmiTexts.get("pref.choose"));
		chooseViewsFolder.setOnAction(event -> {
			DirectoryChooser chooser = new DirectoryChooser();
			chooser.setTitle(SupervisionHmiTexts.get("pref.viewsFolder"));
			if (viewsFolder().isDirectory()) {
				chooser.setInitialDirectory(viewsFolder());
			}
			File folder = chooser.showDialog(this.stage);
			if (folder != null) {
				SupervisorSettings.putString(SupervisorSettings.VIEWS_DIRECTORY, folder.getAbsolutePath());
				viewsFolder.setText(folder.getAbsolutePath());
			}
		});
		HBox viewsFolderRow = new HBox(10, new Label(SupervisionHmiTexts.get("pref.viewsFolder")), viewsFolder, chooseViewsFolder);
		viewsFolderRow.setAlignment(Pos.CENTER_LEFT);
		HBox.setHgrow(viewsFolder, Priority.ALWAYS);

		// Read by the sequence diagram when a recording starts or a log is written: nothing to notify.
		HBox liveLogByDefault = this.toggleRow(SupervisionHmiTexts.get("pref.liveLog"),
				SupervisorSettings.getBoolean(SupervisorSettings.SEQUENCE_LIVE_LOG, false),
				isSelected -> SupervisorSettings.putBoolean(SupervisorSettings.SEQUENCE_LIVE_LOG, isSelected));

		ComboBox<SequenceLog.Format> logFormat = new ComboBox<>();
		logFormat.getItems().setAll(SequenceLog.Format.values());
		logFormat.setConverter(new StringConverter<>() {
			@Override
			public String toString(final SequenceLog.Format format) {
				return format == null ? "" : SupervisionHmiTexts.get("logFormat." + format.name());
			}

			@Override
			public SequenceLog.Format fromString(final String text) {
				return null;
			}
		});
		logFormat.setValue(SequenceLog.preferredFormat());
		logFormat.valueProperty().addListener((observable, oldFormat, newFormat) -> {
			if (newFormat != null) {
				SupervisorSettings.putEnum(SupervisorSettings.SEQUENCE_LOG_FORMAT, newFormat);
			}
		});
		HBox logFormatRow = new HBox(10, new Label(SupervisionHmiTexts.get("pref.logFormat")), logFormat);
		logFormatRow.setAlignment(Pos.CENTER_LEFT);

		Label logsFolder = new Label(SequenceLog.folder().getPath());
		logsFolder.getStyleClass().addAll("sv-preference-path", "sv-preference-path-chip");
		logsFolder.setMaxWidth(Double.MAX_VALUE);
		Tooltips.install(logsFolder, SupervisionHmiTexts.get("pref.logsFolder.tip"));
		Button chooseLogsFolder = new Button(SupervisionHmiTexts.get("pref.choose"));
		chooseLogsFolder.setOnAction(event -> {
			DirectoryChooser chooser = new DirectoryChooser();
			chooser.setTitle(SupervisionHmiTexts.get("pref.logsFolder"));
			if (SequenceLog.folder().isDirectory()) {
				chooser.setInitialDirectory(SequenceLog.folder());
			}
			File folder = chooser.showDialog(this.stage);
			if (folder != null) {
				SupervisorSettings.putString(SupervisorSettings.SEQUENCE_LOG_DIRECTORY, folder.getAbsolutePath());
				logsFolder.setText(folder.getAbsolutePath());
			}
		});
		HBox logsFolderRow = new HBox(10, new Label(SupervisionHmiTexts.get("pref.logsFolder")), logsFolder, chooseLogsFolder);
		logsFolderRow.setAlignment(Pos.CENTER_LEFT);
		HBox.setHgrow(logsFolder, Priority.ALWAYS);

		HBox trackingOnConnect = this.toggleRow(SupervisionHmiTexts.get("pref.trackingOnConnect"),
				SupervisorSettings.getBoolean(SupervisorSettings.THREADS_TRACKING_ON_CONNECT, false),
				isSelected -> SupervisorSettings.putBoolean(SupervisorSettings.THREADS_TRACKING_ON_CONNECT, isSelected));
		Tooltips.install(trackingOnConnect, SupervisionHmiTexts.get("pref.trackingOnConnect.tip"));
		ComboBox<Integer> longTask = new ComboBox<>();
		longTask.getItems().setAll(1, 2, 5, 10, 30, 60, 300);
		longTask.setValue((int) SupervisorSettings.getDouble(SupervisorSettings.THREADS_LONG_TASK_SECONDS, 10));
		longTask.valueProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue != null) {
				SupervisorSettings.putDouble(SupervisorSettings.THREADS_LONG_TASK_SECONDS, newValue);
			}
		});
		HBox longTaskRow = new HBox(10, new Label(SupervisionHmiTexts.get("pref.longTask")), longTask, new Label(SupervisionHmiTexts.get("pref.longTask.unit")));
		longTaskRow.setAlignment(Pos.CENTER_LEFT);

		Node exclusionChevron = icon(ICON_CHEVRON_RIGHT, 13);
		Button exclusionPolicy = new Button(SupervisionHmiTexts.get("pref.exclusionPolicy"), exclusionChevron);
		exclusionPolicy.setContentDisplay(ContentDisplay.RIGHT);
		exclusionPolicy.getStyleClass().add("sv-settings-row-button");
		exclusionPolicy.setMaxWidth(Double.MAX_VALUE);
		exclusionPolicy.setOnAction(event -> this.actionManagerServices.executeAction(ExclusionPolicyActionKey.Toggle));

		Button stereotypes = new Button(SupervisionHmiTexts.get("pref.stereotypes"), icon(ICON_CHEVRON_RIGHT, 13));
		stereotypes.setContentDisplay(ContentDisplay.RIGHT);
		stereotypes.getStyleClass().add("sv-settings-row-button");
		stereotypes.setMaxWidth(Double.MAX_VALUE);
		stereotypes.setOnAction(event -> this.actionManagerServices.executeAction(StereotypePolicyActionKey.Show));

		VBox dataFlowsCard = this.preferenceCategory(ICON_FLOWS, SupervisionHmiTexts.get("card.flows"),
				SupervisionHmiTexts.get("card.flows.caption"), flowsOnConnect);
		VBox threadsCard = this.preferenceCategory(ICON_THREADS, SupervisionHmiTexts.get("card.threads"),
				SupervisionHmiTexts.get("card.threads.caption"), trackingOnConnect, longTaskRow);
		VBox componentsCard = this.preferenceCategory(ICON_EXCLUSIONS, SupervisionHmiTexts.get("card.components"),
				SupervisionHmiTexts.get("card.components.caption"), exclusionPolicy, stereotypes);
		VBox languageCard = this.preferenceCategory(ICON_LANGUAGE, SupervisionHmiTexts.get("card.language"),
				SupervisionHmiTexts.get("card.language.caption"), this.languageRow());
		// Diagram spans both columns: its own rows (a combo box, two chooser rows
		// with a full folder path) need the panel's whole width to avoid
		// truncating down to "..." - a single column would cramp them.
		VBox diagramCard = this.preferenceCategory(ICON_SEQUENCES, SupervisionHmiTexts.get("card.diagram"),
				SupervisionHmiTexts.get("card.diagram.caption"), autoLayoutOnHide, dynamicObservationByDefault,
				timelineStyleRow, liveLogByDefault, logFormatRow, snapshotFolderRow, viewsFolderRow, logsFolderRow);

		ColumnConstraints leftColumn = new ColumnConstraints();
		leftColumn.setPercentWidth(50);
		leftColumn.setHgrow(Priority.ALWAYS);
		ColumnConstraints rightColumn = new ColumnConstraints();
		rightColumn.setPercentWidth(50);
		rightColumn.setHgrow(Priority.ALWAYS);
		GridPane groups = new GridPane();
		groups.getColumnConstraints().addAll(leftColumn, rightColumn);
		groups.setHgap(18);
		groups.setVgap(18);
		groups.add(dataFlowsCard, 0, 0);
		groups.add(threadsCard, 1, 0);
		groups.add(componentsCard, 0, 1);
		groups.add(languageCard, 1, 1);
		groups.add(diagramCard, 0, 2, 2, 1);
		for (Node card : List.of(dataFlowsCard, threadsCard, componentsCard, languageCard, diagramCard)) {
			GridPane.setFillWidth(card, true);
			GridPane.setHgrow(card, Priority.ALWAYS);
		}

		VBox content = new VBox(groups);
		content.setPadding(new Insets(16));

		ScrollPane scrollPane = new ScrollPane(content);
		scrollPane.setFitToWidth(true);
		return scrollPane;
	}

	/**
	 * The supervisor's language: the system's, or one of {@link SupervisorMessages#LANGUAGES}, each
	 * named in its own language. It is stored in {@link SupervisorSettings#LANGUAGE} and applies at
	 * the next start.
	 */
	private HBox languageRow() {
		ComboBox<String> language = new ComboBox<>();
		language.getItems().add("system");
		language.getItems().addAll(SupervisorMessages.LANGUAGES);
		String stored = SupervisorSettings.getString(SupervisorSettings.LANGUAGE);
		language.setValue(stored != null && language.getItems().contains(stored) ? stored : "system");
		language.setConverter(new StringConverter<>() {
			@Override
			public String toString(final String value) {
				return value == null ? "" : SupervisionHmiTexts.get("language." + value);
			}

			@Override
			public String fromString(final String text) {
				return null;
			}
		});
		language.valueProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue != null) {
				SupervisorSettings.putString(SupervisorSettings.LANGUAGE, newValue);
			}
		});
		Label label = new Label(SupervisionHmiTexts.get("pref.language"));
		label.setMaxWidth(Double.MAX_VALUE);
		HBox row = new HBox(10, label, language);
		row.setAlignment(Pos.CENTER_LEFT);
		HBox.setHgrow(label, Priority.ALWAYS);
		return row;
	}

	/** A "label left, {@code sv-switch} right" preference row - the same toggle as in Threads/Exclusion Policy. */
	private HBox toggleRow(final String text, final boolean initial, final Consumer<Boolean> onChange) {
		Label label = new Label(text);
		label.setMaxWidth(Double.MAX_VALUE);
		ToggleButton toggle = new ToggleButton();
		toggle.getStyleClass().add("sv-switch");
		toggle.setSelected(initial);
		toggle.setOnAction(event -> onChange.accept(toggle.isSelected()));
		HBox row = new HBox(10, label, toggle);
		row.setAlignment(Pos.CENTER_LEFT);
		row.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(label, Priority.ALWAYS);
		return row;
	}

	/** One bordered box for a group of preferences that belong to the same domain, headed by an icon badge, title and caption. */
	private VBox preferenceCategory(final String pathData, final String title, final String caption,
			final Node... controls) {
		VBox box = new VBox(12, this.categoryHeader(pathData, title, caption));
		box.getChildren().addAll(controls);
		box.getStyleClass().add("sv-category");
		box.setPadding(new Insets(16));
		box.setMaxWidth(Double.MAX_VALUE);
		return box;
	}

	/** Icon badge + title header for a category card, with no caption line - see {@link #buildPortColorsControls}. */
	private Node categoryHeader(final String pathData, final String title) {
		return this.categoryHeader(pathData, title, null);
	}

	/** Icon badge + title (+ optional caption) header shared by every preferences category card. */
	private Node categoryHeader(final String pathData, final String title, final String caption) {
		Node badge = categoryBadge(pathData);
		Label titleLabel = new Label(title);
		titleLabel.getStyleClass().add("sv-pref-card-title");
		Node titleBlock = titleLabel;
		if (caption != null) {
			Label captionLabel = new Label(caption);
			captionLabel.getStyleClass().add("sv-pref-card-caption");
			captionLabel.setWrapText(true);
			titleBlock = new VBox(2, titleLabel, captionLabel);
		}
		HBox header = new HBox(10, badge, titleBlock);
		header.setAlignment(Pos.CENTER_LEFT);
		return header;
	}

	/** A small accent-tinted icon badge for a preferences category header. */
	private static Node categoryBadge(final String pathData) {
		Node glyph = icon(pathData, 17);
		glyph.getStyleClass().add("sv-category-badge-icon");
		StackPane badge = new StackPane(glyph);
		badge.getStyleClass().add("sv-category-badge");
		return badge;
	}

	/** The views folder: the one chosen, {@code ~/.covalent/supervisor/views} otherwise. */
	private static File viewsFolder() {
		String chosen = SupervisorSettings.getString(SupervisorSettings.VIEWS_DIRECTORY);
		return chosen != null ? new File(chosen) : new File(System.getProperty("user.home"), ".covalent/supervisor/views");
	}

	/**
	 * The Colors section's own layout: a multi-column control grid on the
	 * left, a live two-component preview on the right, and hover-triggered
	 * callout lines connecting a control to wherever its color actually
	 * shows up in that preview. Built preview-first: every
	 * control's hover handler reads {@link #colorPreviewAnchors}, and
	 * {@link #applyPortColorsSnapshot(Object)}'s first callback - fired the
	 * instant {@code bindActionValue} runs, at the very end of {@link
	 * #buildPortColorsControls()} - needs {@link #colorPreviewAppliers}
	 * already populated to paint into.
	 * <p>
	 * Callout lines live in {@link #colorCalloutOverlay}, a
	 * mouse-transparent {@link Pane} stacked on top of both halves. A
	 * node's position only exists once it is laid out, so each line is
	 * computed fresh on hover from both ends' current
	 * {@code localToScene(...)} bounds rather than tracked continuously;
	 * scrolling the controls list just clears whatever line is showing,
	 * since one computed before the scroll would otherwise point at stale
	 * coordinates.
	 */
	private Node buildColorsSection() {
		Pane preview = this.buildColorPreviewPane();
		VBox previewColumn = new VBox(8, this.sectionHeader(SupervisionHmiTexts.get("colors.preview")), preview);

		// The settings sit around the preview, each group next to what it paints: interaction
		// and live activity above, card chrome on the left, status badges on the right, ports
		// and wires below.
		Map<String, Node> categories = this.buildPortColorsControls();
		HBox top = new HBox(16, categories.get("Interaction"), categories.get("Live activity"));
		top.setAlignment(Pos.CENTER);
		GridPane around = new GridPane();
		around.setHgap(16);
		around.setVgap(16);
		around.add(top, 0, 0, 3, 1);
		around.add(categories.get("Card chrome"), 0, 1);
		around.add(previewColumn, 1, 1);
		around.add(categories.get("Status badge"), 2, 1);
		Node portsAndWires = categories.get("Ports & wires");
		around.add(portsAndWires, 0, 2, 3, 1);
		GridPane.setHalignment(top, HPos.CENTER);
		GridPane.setHalignment(portsAndWires, HPos.CENTER);
		GridPane.setValignment(categories.get("Card chrome"), VPos.CENTER);
		GridPane.setValignment(categories.get("Status badge"), VPos.CENTER);
		for (Map.Entry<String, Node> category : categories.entrySet()) {
			if (category.getValue().getParent() == null) {
				// A category this layout does not place: below everything.
				around.add(category.getValue(), 0, around.getRowCount(), 3, 1);
			}
		}

		ScrollPane controlsScroll = new ScrollPane(around);
		controlsScroll.vvalueProperty().addListener((observable, oldValue, newValue) -> this.hideColorCallout());
		controlsScroll.hvalueProperty().addListener((observable, oldValue, newValue) -> this.hideColorCallout());

		Button reset = new Button(SupervisionHmiTexts.get("colors.reset"));
		reset.setGraphic(icon(ICON_RESET, 14));
		Tooltips.install(reset, SupervisionHmiTexts.get("colors.reset.tip"));
		reset.setOnAction(event -> this.confirmColorsReset(reset));
		this.actionManagerServices.bindActionAvailability(ComponentDiagramPreferencesActionKey.ResetPortColors, reset);
		Region resetSpacer = new Region();
		HBox.setHgrow(resetSpacer, Priority.ALWAYS);
		HBox resetBar = new HBox(resetSpacer, reset);
		resetBar.setAlignment(Pos.CENTER_RIGHT);
		resetBar.setPadding(new Insets(8, 16, 4, 0));
		VBox.setVgrow(controlsScroll, Priority.ALWAYS);
		StackPane layout = new StackPane(new VBox(resetBar, controlsScroll));

		this.colorCalloutOverlay = new Pane();
		this.colorCalloutOverlay.setMouseTransparent(true);
		this.colorCalloutOverlay.setPickOnBounds(false);

		return new StackPane(layout, this.colorCalloutOverlay);
	}

	/**
	 * Asks before restoring the current theme's default colors, then forgets
	 * the colors stored for that theme and has the palette restored: the
	 * colors the user chose in this theme are lost, the other theme's are
	 * kept.
	 */
	private void confirmColorsReset(final Node owner) {
		Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
		alert.setTitle(SupervisionHmiTexts.get("colors.reset"));
		alert.setHeaderText(null);
		Label message = new Label(SupervisionHmiTexts.get(
				this.theme == SupervisorTheme.DARK ? "colors.reset.confirm.dark" : "colors.reset.confirm.light"));
		message.setWrapText(true);
		message.setPrefWidth(420);
		alert.getDialogPane().setContent(message);
		ButtonType accept = new ButtonType(SupervisionHmiTexts.get("colors.reset.accept"), ButtonBar.ButtonData.OK_DONE);
		ButtonType cancel = new ButtonType(SupervisionHmiTexts.get("colors.reset.cancel"),
				ButtonBar.ButtonData.CANCEL_CLOSE);
		alert.getButtonTypes().setAll(cancel, accept);
		alert.getDialogPane().lookupButton(accept).getStyleClass().add("sv-danger-button");
		if (owner.getScene() != null) {
			alert.initOwner(owner.getScene().getWindow());
			alert.getDialogPane().getStylesheets().setAll(owner.getScene().getStylesheets());
			alert.getDialogPane().getStyleClass().add("sv-dialog-pane");
		}
		if (alert.showAndWait().filter(answer -> answer == accept).isPresent()) {
			List<String> elements = new ArrayList<>();
			for (PortColorKey key : PortColorKey.values()) {
				elements.add(key.name());
			}
			SupervisorSettings.removeColors(this.theme, elements);
			this.actionManagerServices.executeAction(ComponentDiagramPreferencesActionKey.ResetPortColors);
		}
	}

	/** The icon badge for one of {@link PortColorKey#getCategory()}'s five categories. */
	private static String categoryIcon(final String category) {
		return switch (category) {
			case "Interaction" -> ICON_INTERACTION;
			case "Live activity" -> ICON_LIVE_ACTIVITY;
			case "Card chrome" -> ICON_CARD_CHROME;
			case "Status badge" -> ICON_STATUS_BADGE;
			case "Ports & wires" -> ICON_PORTS_WIRES;
			default -> ICON_SETTINGS;
		};
	}

	/**
	 * One category per {@link PortColorKey#getCategory()}, each its own
	 * bordered box of label + {@link ColorPicker} rows, by category name -
	 * {@link #buildColorsSection()} places them around the preview. Hovering
	 * a row's label or picker draws a callout line to that key's spot(s) in
	 * the preview via {@link #installColorCallout}. All pickers are seeded
	 * together from one {@code ComponentDiagramPreferencesActionKey.PortColorsSnapshot}
	 * bind via {@link #applyPortColorsSnapshot(Object)}, registered last so
	 * every row and every preview target already exists when its initial
	 * callback fires.
	 */
	private Map<String, Node> buildPortColorsControls() {
		Map<String, GridPane> gridsByCategory = new LinkedHashMap<>();
		Map<String, Integer> rowsByCategory = new HashMap<>();
		Map<String, String> categoryTitles = new HashMap<>();
		for (PortColorKey key : PortColorKey.values()) {
			GridPane grid = gridsByCategory.computeIfAbsent(key.getCategory(), category -> {
				GridPane newGrid = new GridPane();
				newGrid.setHgap(10);
				newGrid.setVgap(8);
				return newGrid;
			});
			categoryTitles.putIfAbsent(key.getCategory(), PortColorKeyTexts.category(key));
			int index = rowsByCategory.merge(key.getCategory(), 1, Integer::sum) - 1;
			// Ports and wires run along the preview's width: two columns of four.
			boolean twoColumns = "Ports & wires".equals(key.getCategory());
			int row = twoColumns ? index % 4 : index;
			int column = twoColumns ? index / 4 * 2 : 0;

			Label label = new Label(PortColorKeyTexts.label(key));
			ColorPicker picker = new ColorPicker();
			picker.getStyleClass().add(ColorPicker.STYLE_CLASS_BUTTON);
			picker.valueProperty().addListener((observable, oldValue, newValue) ->
					this.actionManagerServices.executeAction(ComponentDiagramPreferencesActionKey.SetPortColor, key,
							WebColors.toWeb(newValue)));
			// Only a color the user picks is stored, for the current theme.
			picker.setOnAction(event -> {
				SupervisorSettings.putColor(this.theme, key.name(), WebColors.toWeb(picker.getValue()));
				this.bringWindowToFront();
			});
			this.portColorPickers.put(key, picker);
			this.installColorCallout(label, label, key);
			this.installColorCallout(picker, label, key);
			grid.add(label, column, row);
			grid.add(picker, column + 1, row);
			if (twoColumns && column > 0) {
				GridPane.setMargin(label, new Insets(0, 0, 0, 24));
			}
		}

		Map<String, Node> categories = new LinkedHashMap<>();
		for (Map.Entry<String, GridPane> entry : gridsByCategory.entrySet()) {
			Node header = this.categoryHeader(categoryIcon(entry.getKey()), categoryTitles.get(entry.getKey()));
			VBox box = new VBox(10, header, entry.getValue());
			box.setPadding(new Insets(14));
			box.getStyleClass().add("sv-category");
			box.setMaxWidth(Region.USE_PREF_SIZE);
			box.setMaxHeight(Region.USE_PREF_SIZE);
			categories.put(entry.getKey(), box);
		}

		this.actionManagerServices.bindActionValue(ComponentDiagramPreferencesActionKey.PortColorsSnapshot,
				this::applyPortColorsSnapshot);
		return categories;
	}

	/**
	 * Puts the window back in front once the picker's custom color dialog has closed: Windows then hands the focus
	 * to another application's window, which leaves this one behind it. Waits for the dialog to be gone.
	 */
	private void bringWindowToFront() {
		PauseTransition wait = new PauseTransition(Duration.millis(WINDOW_TO_FRONT_DELAY_MILLIS));
		wait.setOnFinished(event -> {
			if (this.stage != null) {
				this.stage.toFront();
				this.stage.requestFocus();
			}
		});
		wait.play();
	}

	/** Hovering {@code hoverTarget} draws a callout from {@code anchorRow} to {@code key}'s preview target(s). */
	private void installColorCallout(final Node hoverTarget, final Node anchorRow, final PortColorKey key) {
		hoverTarget.setOnMouseEntered(event -> this.showColorCallout(key, anchorRow));
		hoverTarget.setOnMouseExited(event -> this.hideColorCallout());
	}

	private void showColorCallout(final PortColorKey key, final Node anchorRow) {
		if (this.colorCalloutOverlay == null) {
			return;
		}
		this.colorCalloutOverlay.getChildren().clear();
		this.colorPreviewHoverShown = key == PortColorKey.HOVER;
		this.paintPreviewCardBodies();
		List<Node> targets = this.colorPreviewAnchors.get(key);
		if (targets == null) {
			return;
		}
		Point2D anchorScene = anchorRow.localToScene(anchorRow.getBoundsInLocal().getMaxX(),
				anchorRow.getBoundsInLocal().getCenterY());
		Point2D anchor = this.colorCalloutOverlay.sceneToLocal(anchorScene);
		for (Node target : targets) {
			Point2D targetScene = target.localToScene(target.getBoundsInLocal().getCenterX(),
					target.getBoundsInLocal().getCenterY());
			Point2D targetPoint = this.colorCalloutOverlay.sceneToLocal(targetScene);
			Line callout = new Line(anchor.getX(), anchor.getY(), targetPoint.getX(), targetPoint.getY());
			callout.setStroke(Color.web("#2563eb", 0.65));
			callout.setStrokeWidth(1.6);
			callout.getStrokeDashArray().addAll(6.0, 5.0);
			Circle marker = new Circle(targetPoint.getX(), targetPoint.getY(), 4.5, Color.web("#2563eb", 0.85));
			this.colorCalloutOverlay.getChildren().addAll(callout, marker);
		}
	}

	private void hideColorCallout() {
		if (this.colorCalloutOverlay != null) {
			this.colorCalloutOverlay.getChildren().clear();
		}
		if (this.colorPreviewHoverShown) {
			this.colorPreviewHoverShown = false;
			this.paintPreviewCardBodies();
		}
	}

	private void registerColorPreview(final PortColorKey key, final Node anchor, final Consumer<Color> applier) {
		this.colorPreviewAnchors.computeIfAbsent(key, unused -> new ArrayList<>()).add(anchor);
		this.colorPreviewAppliers.computeIfAbsent(key, unused -> new ArrayList<>()).add(applier);
	}

	/**
	 * A small "Alpha connected to Beta" scene, deliberately built from the
	 * same color roles {@code CovalentComponentCell}/{@code
	 * AnchoredPortEdge} use, with its own drawing code instead of theirs
	 * (this panel does not use those two classes): a wire's color is its supplying end's color - the provided
	 * service, the produced events or the offered parameters - matching
	 * {@code AnchoredPortEdge.color()} exactly; ports are drawn with the very
	 * shapes {@code CovalentComponentCell} itself uses - the PROVIDED
	 * lollipop (filled circle), the USED socket (open crescent), the EVENT
	 * chevron, the OFFERED bar and the ACCESSED bracket - re-created here at
	 * preview scale rather than reused directly, so this preview never shows a
	 * shape or a color combination the real diagram couldn't. Every shape is
	 * registered via {@link #registerColorPreview} against the {@link
	 * PortColorKey} it stands for; every key gets at least one.
	 */
	private Pane buildColorPreviewPane() {
		// Clearance above the cards for the component-type lollipop (stem +
		// circle), the same shape CovalentComponentCell itself draws above a
		// real component - see the COMPONENT_TYPE marker below.
		double lollipopHeadroom = 58;
		double cardY = 16 + lollipopHeadroom;
		double alphaX = 20;
		double betaX = 300;
		double cardWidth = PREVIEW_CARD_WIDTH;
		double cardHeight = PREVIEW_CARD_HEIGHT;

		this.colorPreviewOutlines.clear();
		this.colorPreviewPads.clear();
		this.colorPreviewCards.clear();
		Pane pane = new Pane();
		pane.setPrefSize(betaX + cardWidth + 24, cardY + cardHeight + 16);
		pane.getStyleClass().add("sv-color-preview");

		PreviewCard alpha = this.buildPreviewCard(pane, alphaX, cardY);
		PreviewCard beta = this.buildPreviewCard(pane, betaX, cardY);

		// Each card's own body icon, between the header and the first port row -
		// the leaf card's hex-with-spokes glyph, CovalentComponentCell.hexagonCubeIcon's
		// own shape, scaled down: so each card reads as a real component rather than
		// an empty box: it carries no name label.
		double bodyIconY = cardY + 77;
		List<Shape> alphaIcon = this.previewComponentIcon(pane, alphaX + cardWidth / 2, bodyIconY, 11);
		List<Shape> betaIcon = this.previewComponentIcon(pane, betaX + cardWidth / 2, bodyIconY, 11);

		this.registerColorPreview(PortColorKey.GRAPHITE, alpha.outer, color -> {
			alpha.outer.setStroke(color);
			beta.outer.setStroke(color);
			for (Shape shape : alphaIcon) {
				shape.setStroke(color);
			}
			for (Shape shape : betaIcon) {
				shape.setStroke(color);
			}
		});
		this.registerColorPreview(PortColorKey.BODY, alpha.inner, color -> {
			this.colorPreviewBodyAndHover[0] = color;
			this.paintPreviewCardBodies();
		});
		// Like a composite card, a preview card takes the hover color while the pointer is
		// over it, and the first card shows it while that color's own setting is pointed at.
		this.registerColorPreview(PortColorKey.HOVER, alpha.inner, color -> {
			this.colorPreviewBodyAndHover[1] = color;
			this.paintPreviewCardBodies();
		});
		for (PreviewCard card : List.of(alpha, beta)) {
			this.colorPreviewCards.add(card);
			Rectangle hoverArea = new Rectangle(card.outer.getX(), card.outer.getY(), card.outer.getWidth(),
					card.outer.getHeight());
			hoverArea.setFill(Color.TRANSPARENT);
			hoverArea.setOnMouseEntered(event -> {
				card.hovered = true;
				this.paintPreviewCardBodies();
			});
			hoverArea.setOnMouseExited(event -> {
				card.hovered = false;
				this.paintPreviewCardBodies();
			});
			card.hoverArea = hoverArea;
		}
		this.registerColorPreview(PortColorKey.HEADER, alpha.header, color -> {
			alpha.header.setFill(color);
			beta.header.setFill(color);
		});
		this.registerColorPreview(PortColorKey.CARD_BORDER_SOFT, alpha.inner, color -> {
			alpha.inner.setStroke(color);
			beta.inner.setStroke(color);
		});
		this.registerColorPreview(PortColorKey.HEADER_DIVIDER, alpha.headerDivider, color -> {
			alpha.headerDivider.setStroke(color);
			beta.headerDivider.setStroke(color);
		});
		this.registerColorPreview(PortColorKey.ICON_DIVIDER, alpha.iconDivider, color -> {
			alpha.iconDivider.setStroke(color);
			beta.iconDivider.setStroke(color);
		});

		// --- Lifecycle status badge, shown on Alpha only - no need for a second copy.
		// Every status color previews on this one badge, which takes on the
		// status whose color was last applied, tinted the way a real card is. ---
		Rectangle badgePill = this.previewRoundedRect(pane, alphaX + 74, cardY + 14, 82, 20, 14);
		Circle badgeDot = this.previewCircle(pane, alphaX + 87, cardY + 24, 3.5);
		Label badgeText = new Label(SupervisionHmiTexts.get("badge.ACTIVATED"));
		badgeText.getStyleClass().add("sv-preview-badge-text");
		badgeText.setLayoutX(alphaX + 94);
		badgeText.setLayoutY(cardY + 17);
		pane.getChildren().add(badgeText);
		// BODY and GRAPHITE precede every status key, so they are applied first.
		Color[] badgeBase = { Color.WHITE, Color.BLACK };
		this.colorPreviewAppliers.computeIfAbsent(PortColorKey.BODY, unused -> new ArrayList<>())
				.add(color -> badgeBase[0] = color);
		this.colorPreviewAppliers.computeIfAbsent(PortColorKey.GRAPHITE, unused -> new ArrayList<>())
				.add(color -> badgeBase[1] = color);
		for (PortColorKey statusKey : List.of(PortColorKey.STATUS_ACTIVATED, PortColorKey.STATUS_PASSIVATED,
				PortColorKey.STATUS_STARTING, PortColorKey.STATUS_INACTIVE, PortColorKey.STATUS_FAILED,
				PortColorKey.STATUS_MIXED, PortColorKey.STATUS_DEGRADED)) {
			String label = SupervisionHmiTexts.get("badge." + statusKey.name().substring("STATUS_".length()));
			this.registerColorPreview(statusKey, badgePill, color -> {
				badgePill.setFill(badgeBase[0].interpolate(color, 0.12));
				badgePill.setStroke(badgeBase[0].interpolate(color, 0.4));
				badgeDot.setFill(color);
				badgeText.setText(label);
				badgeText.setTextFill(color.interpolate(badgeBase[1], 0.25));
			});
		}

		// --- Component-type marker, shown on Beta only: a lollipop above the
		// card - a stem rising from its top edge to a filled circle - exactly
		// how CovalentComponentCell.renderTopPorts draws a real component's
		// own type, not a dot floating inside the card body. ---
		double typeX = betaX + cardWidth / 2.0;
		double typeCircleY = cardY - 34;
		this.outlineInGraphite(this.previewLine(pane, typeX, cardY, typeX, typeCircleY + 11));
		Circle typeMarker = this.previewCircle(pane, typeX, typeCircleY, 11);
		this.registerColorPreview(PortColorKey.COMPONENT_TYPE, typeMarker, typeMarker::setFill);

		// --- Facing ports: Alpha provides/produces/offers, Beta uses/consumes/accesses,
		// connected by one wire each, colored exactly like AnchoredPortEdge.color() would.
		// Each one sits on a pad flush against its own card edge, with a short
		// colored stem reaching out to the shape - CovalentComponentCell.renderSidePort's
		// own anatomy, so a port reads as plugged into its card, not floating on the wire. ---
		double servicePadY = cardY + 110;
		double eventPadY = cardY + 150;
		double parameterPadY = cardY + 190;

		double alphaEdgeX = alphaX + cardWidth;
		double betaEdgeX = betaX;
		double padShort = 5;
		double padLong = 16;
		// The stem's own VISIBLE length - identical on both sides, by
		// construction: every shape below is centered at stemEndAlpha/Beta
		// plus or minus its own radius, so the stem is drawn right up to the
		// shape's rim and never disappears underneath it.
		double visibleStemLen = 18;
		double stemEndAlpha = alphaEdgeX + padShort + visibleStemLen;
		double stemEndBeta = betaEdgeX - padShort - visibleStemLen;

		// Real shapes from here on, at preview scale.
		this.previewPortPad(pane, alphaEdgeX, servicePadY, padShort, padLong, 1);
		this.previewPortPad(pane, betaEdgeX, servicePadY, padShort, padLong, -1);
		Line serviceStemAlpha = this.previewLine(pane, alphaEdgeX + padShort, servicePadY, stemEndAlpha, servicePadY);
		serviceStemAlpha.setStrokeWidth(2.0);
		Line serviceStemBeta = this.previewLine(pane, betaEdgeX - padShort, servicePadY, stemEndBeta, servicePadY);
		serviceStemBeta.setStrokeWidth(2.0);
		double serviceCircleRadius = 8;
		double serviceSocketRadius = 12; // 9 radius + ~3.2 outline half-width: its true visual rim.
		double serviceAlphaX = stemEndAlpha + serviceCircleRadius;
		double serviceBetaX = stemEndBeta - serviceSocketRadius;
		Circle servicePad = this.previewCircle(pane, serviceAlphaX, servicePadY, serviceCircleRadius);
		Arc serviceUsedPad = this.previewServiceSocket(pane, serviceBetaX, servicePadY, 9, true);
		Line serviceWire = this.previewWire(pane, stemEndAlpha, servicePadY, stemEndBeta, servicePadY);
		this.registerColorPreview(PortColorKey.SERVICE_PROVIDED, servicePad, color -> {
			servicePad.setFill(color);
			serviceWire.setStroke(color);
		});
		this.registerColorPreview(PortColorKey.SERVICE_USED, serviceUsedPad, serviceUsedPad::setStroke);

		this.previewPortPad(pane, alphaEdgeX, eventPadY, padShort, padLong, 1);
		this.previewPortPad(pane, betaEdgeX, eventPadY, padShort, padLong, -1);
		Line eventStemAlpha = this.previewLine(pane, alphaEdgeX + padShort, eventPadY, stemEndAlpha, eventPadY);
		eventStemAlpha.setStrokeWidth(2.0);
		Line eventStemBeta = this.previewLine(pane, betaEdgeX - padShort, eventPadY, stemEndBeta, eventPadY);
		eventStemBeta.setStrokeWidth(2.0);
		double eventChevronSize = 8;
		double eventAlphaX = stemEndAlpha + eventChevronSize; // meets the chevron's flat back.
		double eventBetaX = stemEndBeta - eventChevronSize * 1.5; // meets the chevron's tip.
		Polygon eventPad = this.previewEventChevron(pane, eventAlphaX, eventPadY, eventChevronSize);
		Polygon eventConsumedPad = this.previewEventChevron(pane, eventBetaX, eventPadY, eventChevronSize);
		Line eventWire = this.previewWire(pane, stemEndAlpha, eventPadY, stemEndBeta, eventPadY);
		this.registerColorPreview(PortColorKey.EVENT_PRODUCED, eventPad, color -> {
			eventPad.setFill(color);
			eventWire.setStroke(color);
		});
		this.registerColorPreview(PortColorKey.EVENT_CONSUMED, eventConsumedPad, eventConsumedPad::setFill);

		this.previewPortPad(pane, alphaEdgeX, parameterPadY, padShort, padLong, 1);
		this.previewPortPad(pane, betaEdgeX, parameterPadY, padShort, padLong, -1);
		Line parameterStemAlpha = this.previewLine(pane, alphaEdgeX + padShort, parameterPadY, stemEndAlpha, parameterPadY);
		parameterStemAlpha.setStrokeWidth(2.0);
		Line parameterStemBeta = this.previewLine(pane, betaEdgeX - padShort, parameterPadY, stemEndBeta, parameterPadY);
		parameterStemBeta.setStrokeWidth(2.0);
		// OFFERED is a bar, but - unlike the real bottom-mounted port it's modeled on -
		// this one sits on a side edge with a horizontal wire, so its long axis turns
		// 90 degrees to run across the wire instead of along it: a small vertical bar.
		double parameterBarWidth = 7;
		double parameterBracketHalfWidth = 4; // half the bracket's width: a shallow grip.
		double parameterAlphaX = stemEndAlpha + parameterBarWidth / 2;
		double parameterBetaX = stemEndBeta - parameterBracketHalfWidth;
		Rectangle parameterPad = this.previewParameterBar(pane, parameterAlphaX, parameterPadY, parameterBarWidth, 18);
		Path parameterAccessedPad = this.previewParameterBracket(pane, parameterBetaX, parameterPadY,
				parameterBracketHalfWidth, 9);
		Line parameterWire = this.previewWire(pane, stemEndAlpha, parameterPadY, stemEndBeta, parameterPadY);
		this.registerColorPreview(PortColorKey.PARAMETER, parameterPad, color -> {
			parameterPad.setFill(color);
			parameterWire.setStroke(color);
		});
		this.registerColorPreview(PortColorKey.PARAMETER_ACCESSED, parameterAccessedPad, parameterAccessedPad::setStroke);

		// Every local stem is the port's mechanical attachment to its own card -
		// never part of the link, which spans shape-to-shape only - so all six
		// stay graphite, like the rest of the card's chrome, instead of being
		// tinted by their own port's role color.
		List<Line> stems = List.of(serviceStemAlpha, serviceStemBeta, eventStemAlpha, eventStemBeta,
				parameterStemAlpha, parameterStemBeta);
		this.colorPreviewAppliers.computeIfAbsent(PortColorKey.GRAPHITE, unused -> new ArrayList<>())
				.add(color -> {
					for (Line stem : stems) {
						stem.setStroke(color);
					}
				});

		// --- Alpha's outer edge: idle/highlighted/activity pads that have no wire of their own ---
		double outerPadX = alphaX;
		Circle idlePad = this.previewCircle(pane, outerPadX, cardY + 110, 8);
		this.registerColorPreview(PortColorKey.SLOT_IDLE, idlePad, idlePad::setFill);

		Circle highlightPad = this.previewCircle(pane, outerPadX, cardY + 150, 8);
		this.registerColorPreview(PortColorKey.HIGHLIGHT, highlightPad, highlightPad::setFill);

		Circle serviceActivityPad = this.previewCircle(pane, outerPadX, cardY + 190, 7);
		this.registerColorPreview(PortColorKey.SERVICE_ACTIVITY, serviceActivityPad,
				serviceActivityPad::setFill);

		Circle serviceFailurePad = this.previewCircle(pane, outerPadX, cardY + 218, 7);
		this.registerColorPreview(PortColorKey.SERVICE_FAILURE, serviceFailurePad,
				serviceFailurePad::setFill);

		Circle eventActivityPad = this.previewCircle(pane, outerPadX, cardY + 246, 7);
		this.registerColorPreview(PortColorKey.EVENT_ACTIVITY, eventActivityPad,
				eventActivityPad::setFill);

		// --- Selection outline, shown around Beta ---
		Rectangle selection = new Rectangle(betaX - 4, cardY - 4, cardWidth + 8, cardHeight + 8);
		selection.setFill(Color.TRANSPARENT);
		selection.setArcWidth(20);
		selection.setArcHeight(20);
		selection.setStrokeWidth(2.5);
		pane.getChildren().add(selection);
		this.registerColorPreview(PortColorKey.SELECTION, selection, selection::setStroke);

		// On top of everything, so the pointer over a card's own shapes still counts as over the card.
		for (PreviewCard card : this.colorPreviewCards) {
			pane.getChildren().add(card.hoverArea);
		}

		this.colorPreviewAppliers.computeIfAbsent(PortColorKey.GRAPHITE, unused -> new ArrayList<>())
				.add(color -> {
					for (Shape outline : this.colorPreviewOutlines) {
						outline.setStroke(color);
					}
				});
		this.colorPreviewAppliers.computeIfAbsent(PortColorKey.SLOT_IDLE, unused -> new ArrayList<>())
				.add(color -> {
					for (Shape pad : this.colorPreviewPads) {
						pad.setFill(color);
					}
				});

		return pane;
	}

	/**
	 * Draws {@code shape}'s outline in the card outline color, following it
	 * on every change; the color is set by the first snapshot applied.
	 */
	private <S extends Shape> S outlineInGraphite(final S shape) {
		this.colorPreviewOutlines.add(shape);
		return shape;
	}

	private PreviewCard buildPreviewCard(final Pane pane, final double x, final double y) {
		PreviewCard card = new PreviewCard();
		card.outer = this.previewRoundedRect(pane, x, y, PREVIEW_CARD_WIDTH, PREVIEW_CARD_HEIGHT, 16);
		card.outer.setStrokeWidth(1.6);
		card.inner = this.previewRoundedRect(pane, x + 6, y + 6, PREVIEW_CARD_WIDTH - 12, PREVIEW_CARD_HEIGHT - 12, 12);
		card.header = this.previewRoundedRect(pane, x + 10, y + 10, PREVIEW_CARD_WIDTH - 20, 34, 10);
		card.headerDivider = this.previewLine(pane, x + 10, y + 44, x + PREVIEW_CARD_WIDTH - 10, y + 44);
		Group icon = this.previewCubeIcon(x + 24, y + 27, 5);
		pane.getChildren().add(icon);
		card.iconDivider = this.previewLine(pane, x + 40, y + 18, x + 40, y + 36);
		return card;
	}

	/** Fills each preview card's body with the hover color while it is hovered, the body color otherwise. */
	private void paintPreviewCardBodies() {
		for (int i = 0; i < this.colorPreviewCards.size(); i++) {
			PreviewCard card = this.colorPreviewCards.get(i);
			boolean hovered = card.hovered || (i == 0 && this.colorPreviewHoverShown);
			Color fill = hovered ? this.colorPreviewBodyAndHover[1] : this.colorPreviewBodyAndHover[0];
			card.outer.setFill(fill);
			card.inner.setFill(fill);
		}
	}

	private static final class PreviewCard {
		private boolean hovered;
		private Rectangle hoverArea;
		private Rectangle outer;
		private Rectangle inner;
		private Rectangle header;
		private Line headerDivider;
		private Line iconDivider;
	}

	private Group previewCubeIcon(final double x, final double y, final double size) {
		double dx = Math.sqrt(3) * size / 2;
		double refY = y + 0.75 * size;
		Polygon top = this.hexagonPolygon(x, refY - 1.5 * size, size);
		Polygon left = this.hexagonPolygon(x - dx, refY, size);
		Polygon right = this.hexagonPolygon(x + dx, refY, size);
		for (Polygon hex : new Polygon[] { top, left, right }) {
			hex.setFill(Color.TRANSPARENT);
			this.outlineInGraphite(hex);
			hex.setStrokeWidth(1.1);
		}
		return new Group(top, left, right);
	}

	private Polygon hexagonPolygon(final double x, final double y, final double size) {
		Polygon hexagon = new Polygon();
		for (int i = 0; i < 6; i++) {
			double angle = Math.PI / 6 + Math.PI / 3 * i;
			hexagon.getPoints().addAll(x + size * Math.cos(angle), y + size * Math.sin(angle));
		}
		return hexagon;
	}

	private Rectangle previewRoundedRect(final Pane pane, final double x, final double y, final double width,
			final double height, final double radius) {
		Rectangle rectangle = new Rectangle(x, y, width, height);
		rectangle.setArcWidth(radius);
		rectangle.setArcHeight(radius);
		pane.getChildren().add(rectangle);
		return rectangle;
	}

	private Line previewLine(final Pane pane, final double x1, final double y1, final double x2, final double y2) {
		Line line = new Line(x1, y1, x2, y2);
		line.setStrokeWidth(1.2);
		pane.getChildren().add(line);
		return line;
	}

	private Line previewWire(final Pane pane, final double x1, final double y1, final double x2, final double y2) {
		Line line = new Line(x1, y1, x2, y2);
		line.setStrokeWidth(2.2);
		pane.getChildren().add(0, line);
		return line;
	}

	/**
	 * The pad every real port sits on: a small rounded slot flush
	 * against the card edge - {@code CovalentComponentCell.renderSidePort}'s
	 * own anatomy - so a port reads as plugged into its card rather than
	 * floating out on the wire. {@code direction} is +1 to stick out to the
	 * right of {@code edgeX} (Alpha's side), -1 to stick out to the left
	 * (Beta's side).
	 */
	private Rectangle previewPortPad(final Pane pane, final double edgeX, final double cy, final double shortSide,
			final double longSide, final double direction) {
		double x = direction > 0 ? edgeX : edgeX - shortSide;
		Rectangle pad = new Rectangle(x, cy - longSide / 2, shortSide, longSide);
		pad.setArcWidth(6);
		pad.setArcHeight(6);
		this.colorPreviewPads.add(pad);
		this.outlineInGraphite(pad);
		pad.setStrokeWidth(1.0);
		pane.getChildren().add(pad);
		return pad;
	}

	/**
	 * The leaf card's own body icon: an outer hexagon, a concentric inner
	 * one at {@code size*0.45}, and 3 diagonals joining its opposite vertex
	 * pairs - the same glyph {@code CovalentComponentCell.hexWithSpokes}
	 * draws, at preview scale. Every stroke comes back unset (default
	 * black) so the GRAPHITE applier can color all 5 pieces together.
	 */
	private List<Shape> previewComponentIcon(final Pane pane, final double cx, final double cy, final double size) {
		Polygon outerHex = this.previewHexagon(cx, cy, size);
		Polygon innerHex = this.previewHexagon(cx, cy, size * 0.45);
		pane.getChildren().addAll(outerHex, innerHex);
		Line a = this.previewLine(pane, cx, cy - size * 0.45, cx, cy + size * 0.45);
		Line b = this.previewLine(pane, cx - size * 0.39, cy - size * 0.2, cx + size * 0.39, cy + size * 0.2);
		Line c = this.previewLine(pane, cx + size * 0.39, cy - size * 0.2, cx - size * 0.39, cy + size * 0.2);
		return List.of(outerHex, innerHex, a, b, c);
	}

	private Polygon previewHexagon(final double x, final double y, final double size) {
		Polygon hexagon = new Polygon();
		for (int i = 0; i < 6; i++) {
			double angle = Math.PI / 6 + Math.PI / 3 * i;
			hexagon.getPoints().addAll(x + Math.cos(angle) * size, y + Math.sin(angle) * size);
		}
		hexagon.setFill(Color.TRANSPARENT);
		hexagon.setStrokeWidth(1.4);
		return hexagon;
	}

	private Circle previewCircle(final Pane pane, final double cx, final double cy, final double radius) {
		Circle circle = new Circle(cx, cy, radius);
		this.outlineInGraphite(circle);
		circle.setStrokeWidth(1.2);
		pane.getChildren().add(circle);
		return circle;
	}

	/**
	 * The real SERVICE/USED socket: two stacked open arcs - a wider graphite
	 * band behind a narrower colored one - the same two-stroke technique
	 * {@code CovalentComponentCell.portShape} uses for its crescent, since
	 * an {@code Arc} has no fill of its own. The mouth opens toward the
	 * wire, left when {@code mouthLeft}.
	 */
	private Arc previewServiceSocket(final Pane pane, final double cx, final double cy, final double radius,
			final boolean mouthLeft) {
		double startAngle = mouthLeft ? -90 : 90;
		Arc outline = new Arc(cx, cy, radius, radius, startAngle, 180);
		outline.setFill(null);
		this.outlineInGraphite(outline);
		outline.setStrokeWidth(6.4);
		outline.setStrokeLineCap(StrokeLineCap.ROUND);
		outline.setType(ArcType.OPEN);
		Arc fill = new Arc(cx, cy, radius, radius, startAngle, 180);
		fill.setFill(null);
		fill.setStrokeWidth(4.0);
		fill.setStrokeLineCap(StrokeLineCap.ROUND);
		fill.setType(ArcType.OPEN);
		pane.getChildren().addAll(outline, fill);
		return fill;
	}

	/**
	 * The real EVENT port: a direction-pointing chevron, the same shape
	 * {@code CovalentComponentCell.eventShape} draws for both PRODUCED and
	 * CONSUMED - only the fill color differs by role in the real diagram
	 * (direction there follows which card edge the port sits on); here both
	 * pads simply point the same way, following the wire from Alpha to
	 * Beta. {@code cx} is the shape's visual center, kept centered on it
	 * regardless of the tip's reach.
	 */
	private Polygon previewEventChevron(final Pane pane, final double cx, final double cy, final double size) {
		double tip = size * 1.5;
		double notch = size * 0.9;
		double center = cx - (tip - size) / 2.0;
		Polygon chevron = new Polygon(center - size, cy - size, center + notch, cy - size, center + tip, cy,
				center + notch, cy + size, center - size, cy + size, center - size * 0.1, cy);
		this.outlineInGraphite(chevron);
		chevron.setStrokeWidth(1.2);
		pane.getChildren().add(chevron);
		return chevron;
	}

	/**
	 * The real PARAMETER/OFFERED shape: a filled rounded bar - {@code
	 * CovalentComponentCell.renderParameterPorts}'s own shape for the
	 * component providing the parameter.
	 */
	private Rectangle previewParameterBar(final Pane pane, final double cx, final double cy, final double width,
			final double height) {
		Rectangle bar = new Rectangle(cx - width / 2, cy - height / 2, width, height);
		// Rounded by the SHORTER side, same as CovalentComponentCell's own bar - rounding
		// by the longer side (height, here) over-rounds a narrow shape into an oval with
		// no straight edge left at all, rather than a rectangle with rounded ends.
		double rounding = Math.min(width, height);
		bar.setArcWidth(rounding);
		bar.setArcHeight(rounding);
		this.outlineInGraphite(bar);
		bar.setStrokeWidth(1.2);
		pane.getChildren().add(bar);
		return bar;
	}

	/**
	 * The real PARAMETER/ACCESSED shape: an open three-sided bracket, open
	 * toward the wire - same two-stroke technique as {@link
	 * #previewServiceSocket} - matching {@code
	 * CovalentComponentCell.bracketShape}'s own shape for the component
	 * reading the parameter.
	 */
	private Path previewParameterBracket(final Pane pane, final double cx, final double cy, final double halfWidth,
			final double halfHeight) {
		Path outline = this.bracketPreviewPath(cx, cy, halfWidth, halfHeight);
		this.outlineInGraphite(outline);
		outline.setStrokeWidth(6.4);
		outline.setStrokeLineCap(StrokeLineCap.ROUND);
		outline.setStrokeLineJoin(StrokeLineJoin.ROUND);
		Path fill = this.bracketPreviewPath(cx, cy, halfWidth, halfHeight);
		fill.setStrokeWidth(4.0);
		fill.setStrokeLineCap(StrokeLineCap.ROUND);
		fill.setStrokeLineJoin(StrokeLineJoin.ROUND);
		pane.getChildren().addAll(outline, fill);
		return fill;
	}

	/** Open on the left, toward the wire: top, right, bottom arms - no left side. */
	private Path bracketPreviewPath(final double cx, final double cy, final double halfWidth, final double halfHeight) {
		Path path = new Path();
		path.setFill(null);
		path.getElements().addAll(new MoveTo(cx - halfWidth, cy - halfHeight), new LineTo(cx + halfWidth, cy - halfHeight),
				new LineTo(cx + halfWidth, cy + halfHeight), new LineTo(cx - halfWidth, cy + halfHeight));
		return path;
	}

	@SuppressWarnings("unchecked")
	private void applyPortColorsSnapshot(final Object snapshotValue) {
		if (!(snapshotValue instanceof Map)) {
			return;
		}
		Map<PortColorKey, String> snapshot = (Map<PortColorKey, String>) snapshotValue;
		for (Map.Entry<PortColorKey, ColorPicker> entry : this.portColorPickers.entrySet()) {
			Color color = WebColors.toColor(snapshot.get(entry.getKey()));
			if (color != null) {
				entry.getValue().setValue(color);
			}
		}
		for (Map.Entry<PortColorKey, List<Consumer<Color>>> entry : this.colorPreviewAppliers.entrySet()) {
			Color color = WebColors.toColor(snapshot.get(entry.getKey()));
			if (color == null) {
				continue;
			}
			for (Consumer<Color> applier : entry.getValue()) {
				applier.accept(color);
			}
		}
		// The status badge previews whichever status was applied last: settle it on "activated".
		Color activated = WebColors.toColor(snapshot.get(PortColorKey.STATUS_ACTIVATED));
		if (activated != null) {
			for (Consumer<Color> applier : this.colorPreviewAppliers.getOrDefault(PortColorKey.STATUS_ACTIVATED,
					List.of())) {
				applier.accept(activated);
			}
		}
	}

	private Label sectionHeader(final String text) {
		Label label = new Label(text);
		label.getStyleClass().add("sv-section-header");
		return label;
	}

	private void triggerCurrentKey(final ActionKey key) {
		if (key != null) {
			this.actionManagerServices.executeAction(key);
		}
	}

	@Override
	public void openPanel(final ViewKey key, final Node panel, final SupervisorTabDescriptor descriptor) {
		Platform.runLater(() -> {
			if (descriptor.railEntry() == RailEntry.STEREOTYPES) {
				this.showStereotypesTab(key, panel);
				return;
			}
			HostedPanel hosted = this.panelsByKey.get(key);
			EntryArea area = this.areas.get(descriptor.railEntry());
			if (hosted == null) {
				HostedPanel created = new HostedPanel(key);
				created.tab.getStyleClass().add("sv-subtab");
				created.tab.setToggleGroup(area.tabGroup);
				created.tab.setOnAction(event -> this.selectPanel(area, created));
				created.tab.setContentDisplay(ContentDisplay.RIGHT);
				created.tab.setOnMouseClicked(event -> {
					if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
						this.startRename(area, created);
					}
				});
				created.tab.setOnContextMenuRequested(event -> {
					if (created.descriptor != null && created.descriptor.renamable()) {
						MenuItem rename = new MenuItem(SupervisionHmiTexts.get("tab.rename"));
						rename.setOnAction(action -> this.startRename(area, created));
						new ContextMenu(rename).show(created.tab, event.getScreenX(), event.getScreenY());
						event.consume();
					}
				});
				this.panelsByKey.put(key, created);
				area.panels.add(created);
				hosted = created;
			}
			hosted.node = panel;
			hosted.descriptor = descriptor;
			if (!descriptor.closable()) {
				hosted.tab.setGraphic(null);
			} else if (hosted.tab.getGraphic() == null) {
				HostedPanel closed = hosted;
				Button close = new Button(null, icon(ICON_CLOSE, 12));
				close.getStyleClass().add("sv-subtab-close");
				Tooltips.install(close, SupervisionHmiTexts.get("tab.close.tip"));
				close.setOnAction(event -> this.closeFromTab(closed));
				hosted.tab.setGraphic(close);
			}
			hosted.title = hosted.userTitle != null ? hosted.userTitle
					: descriptor.title().isEmpty() ? entryLabel(descriptor.railEntry()) : descriptor.title();
			hosted.tab.setText(hosted.title);
			area.selected = hosted;
			this.renderArea(area);
			this.showEntry(area.entry);
		});
	}

	@Override
	public void setPanelTitle(final ViewKey key, final String title) {
		Platform.runLater(() -> {
			HostedPanel hosted = this.panelsByKey.get(key);
			if (hosted == null) {
				return;
			}
			hosted.userTitle = title;
			hosted.title = title;
			hosted.tab.setText(title);
			this.renderArea(this.areas.get(hosted.descriptor.railEntry()));
		});
	}

	/**
	 * Edits a renamable tab's title in place: Enter or leaving the field keeps
	 * the new title, Escape restores the old one. The tab's owner is told the
	 * new title through the descriptor's rename action, if any.
	 */
	private void startRename(final EntryArea area, final HostedPanel hosted) {
		if (hosted.descriptor == null || !hosted.descriptor.renamable() || hosted.tab.getGraphic() instanceof TextField) {
			return;
		}
		TextField field = new TextField(hosted.title);
		field.getStyleClass().add("sv-subtab-rename");
		field.setPrefColumnCount(Math.max(8, hosted.title.length()));
		Node closeButton = hosted.tab.getGraphic();
		ContentDisplay display = hosted.tab.getContentDisplay();
		boolean[] finished = { false };
		Consumer<Boolean> finish = keep -> {
			if (finished[0]) {
				return;
			}
			finished[0] = true;
			hosted.tab.setGraphic(closeButton);
			hosted.tab.setContentDisplay(display);
			String newTitle = field.getText().trim();
			if (keep && !newTitle.isEmpty() && !newTitle.equals(hosted.title)) {
				hosted.userTitle = newTitle;
				hosted.title = newTitle;
				hosted.tab.setText(newTitle);
				this.renderArea(area);
				ActionKey renameKey = hosted.descriptor.renameActionKey();
				if (renameKey != null) {
					this.actionManagerServices.executeAction(renameKey, newTitle);
				}
			}
		};
		field.setOnAction(event -> finish.accept(true));
		field.setOnKeyPressed(event -> {
			if (event.getCode() == KeyCode.ESCAPE) {
				event.consume();
				finish.accept(false);
			}
		});
		field.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
			if (!isFocused) {
				finish.accept(true);
			}
		});
		// Clicks inside the field edit it, not the tab.
		field.addEventHandler(MouseEvent.MOUSE_CLICKED, Event::consume);
		hosted.tab.setGraphic(field);
		hosted.tab.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		Platform.runLater(() -> {
			field.requestFocus();
			field.selectAll();
		});
	}

	@Override
	public void closePanel(final ViewKey key) {
		Platform.runLater(() -> {
			if (key.equals(this.stereotypesKey)) {
				this.stereotypesKey = null;
				this.stereotypesTab.setContent(new Region());
				return;
			}
			this.removePanel(key);
		});
	}

	@Override
	public void bindToolbarFunction(final ToolbarFunction function, final ActionKey key) {
		FxThread.run(() -> {
			this.actionManagerServices.bindActionAvailability(key, this.nodeFor(function));
			this.setCurrentKey(function, key);
		});
	}

	@Override
	public void unbindToolbarFunction(final ToolbarFunction function, final ActionKey key) {
		FxThread.run(() -> {
			this.actionManagerServices.unbindActionAvailability(key, this.nodeFor(function));
			this.setCurrentKey(function, null);
		});
	}

	@Override
	public void showWindow() {
		if (Platform.isFxApplicationThread()) {
			this.revealWindow();
		} else {
			Platform.runLater(this::revealWindow);
		}
	}

	private void revealWindow() {
		if (this.stage == null) {
			return;
		}
		this.stage.show();
		this.stage.setIconified(false);
		this.stage.toFront();
		this.stage.requestFocus();
	}

	private void setCurrentKey(final ToolbarFunction function, final ActionKey key) {
		switch (function) {
			case RECORD_TOGGLE:
				this.currentRecordKey = key;
				break;
			case ZOOM_SLIDER:
				this.currentZoomSetScaleKey = key;
				// Enabled once a tab's zoom is bound - the availability binding only ever disables it, while
				// the zoom is unavailable - and disabled again once unbound.
				this.zoomTools.setDisable(key == null);
				break;
			default:
				throw new IllegalArgumentException("Unknown toolbar function: " + function);
		}
	}

	private Node nodeFor(final ToolbarFunction function) {
		switch (function) {
			case RECORD_TOGGLE:
				return this.recordAvailability;
			case ZOOM_SLIDER:
				return this.zoomTools;
			default:
				throw new IllegalArgumentException("Unknown toolbar function: " + function);
		}
	}
}
