/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.view.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;


import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener.Change;
import javafx.event.EventHandler;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Group;
import javafx.scene.transform.Scale;
import javafx.scene.shape.Line;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
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
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.stage.Window;
import javafx.util.Duration;

import org.opales.covalent.supervision.componentview.internal.WebColors;
import org.opales.covalent.supervision.componentview.internal.CollapsibleSide;
import org.opales.covalent.supervision.componentview.internal.Tooltips;
import org.opales.covalent.supervision.componentview.internal.Toolbox;
import org.opales.covalent.hmi.action.ActionConsumerServices;
import org.opales.covalent.supervision.componentdiagram.interaction.ComponentDiagramActionKey;
import org.opales.covalent.supervision.componentdiagram.interaction.ComponentDiagramActionKey.Role;
import org.opales.covalent.supervision.componentdiagram.interaction.ExportViewRequest;
import org.opales.covalent.supervision.componentdiagram.interaction.RenameViewRequest;
import org.opales.covalent.supervision.componentdiagram.interaction.SaveViewRequest;
import org.opales.covalent.supervision.componentdiagram.model.ComponentActivity;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramEdge;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramExternalPort;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramEvents;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramLayout;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramModelReadServices;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramTopology;
import org.opales.covalent.supervision.componentdiagram.model.ComponentPortKind;
import org.opales.covalent.supervision.componentdiagram.model.ComponentStatus;
import org.opales.covalent.supervision.componentdiagram.model.PortColorKey;
import org.opales.covalent.supervision.componentdiagram.view.ComponentDiagramView;
import org.opales.covalent.supervision.componentdiagram.view.ComponentDiagramViewServices;
import org.opales.covalent.supervision.componentdiagram.view.SaveViewForm;
import org.opales.covalent.supervision.componentdiagram.view.ViewLibraryContent;
import org.opales.covalent.supervision.diagrampersistence.SavedView;
import org.opales.covalent.supervision.componentview.internal.Snapshots;
import org.opales.covalent.supervision.componentview.internal.Ordering;
import org.opales.covalent.supervision.componentview.internal.diagram.cells.ComponentPort;
import org.opales.covalent.supervision.componentview.internal.diagram.cells.ComponentPortActivity;
import org.opales.covalent.supervision.componentview.internal.diagram.cells.ComponentPortRole;
import org.opales.covalent.supervision.componentview.internal.diagram.cells.ComponentPortSide;
import org.opales.covalent.supervision.componentview.internal.diagram.cells.CovalentComponentCell;
import org.opales.covalent.supervision.componentview.internal.diagram.cells.PortPalette;
import org.opales.covalent.supervision.componentview.internal.diagram.edges.AnchoredPortEdge;
import org.opales.covalent.supervision.componentview.internal.diagram.edges.CompositionEdge;
import org.opales.covalent.supervision.componentview.internal.diagram.edges.EdgeRoutingStyle;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.Graph;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.Cell;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.Edge;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.ViewportFit;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.GraphModel;
import org.opales.covalent.supervision.componentview.internal.diagram.layout.ComponentPortSideLayout;
import org.opales.covalent.supervision.componentview.internal.diagram.layout.ElkLayout;
import org.opales.covalent.supervision.sequencediagram.interaction.SequenceRecordingActionKey;
import org.opales.covalent.supervision.supervisionhmi.interaction.SupervisionHmiActionKey;
import org.opales.covalent.supervision.supervisionhmi.view.FxThread;
import org.opales.covalent.supervision.supervisionhmi.view.PanelHostingServices;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisorMessages;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisorTabDescriptor;
import org.opales.covalent.supervision.supervisionhmi.view.RailEntry;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisorTabDescriptorAdapter;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisorTheme;
import org.opales.covalent.supervision.supervisionhmi.view.ToolbarFunction;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionEvents;
import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.hmi.action.ActionObservationServices;

/**
 * ComponentDiagram's View: one diagram canvas (nodes/edges/cells), one
 * component tree panel, and one PNG/clipboard export per open tab - opened
 * on {@code onSessionCreated} and closed for good on {@code onSessionClosed},
 * like {@code SequenceDiagramViewImpl}; the always-open first
 * tab goes through the same path as any tab opened with "+ New
 * diagram", since {@code ComponentDiagramControllerImpl} mints its session
 * identity the same way. Renders what {@link ComponentDiagramEvents}
 * announces, reading the Model through {@code ComponentDiagramModelReadServices},
 * and reports every gesture to
 * {@code ComponentDiagramInteraction} through {@link
 * ActionConsumerServices} using a {@link ComponentDiagramActionKey} it
 * constructs itself for that tab (structural equality - see that class's
 * javadoc).
 * <p>
 * The port/wire palette ({@code palettes}/{@code theme}) and the
 * auto-layout-on-hide preference are this tool's genuinely shared state,
 * not any one tab's own - edited through SupervisionHmi's shared
 * "Preferences" panel and applied to every open tab alike, so
 * they live on this outer class rather than inside {@code SessionPanel}.
 * <p>
 * A tab's rectangle-select replaces the selection with Shift and adds to
 * it with Ctrl: since {@code ComponentDiagramControllerServices} only
 * exposes a wholesale {@code selectComponents(ViewKey, Set)} and a single
 * {@code toggleComponentSelection(ViewKey, Class)}, the additive variant
 * sends the union of the tab's current selection and the rectangle's
 * contents. There is no "focused" component distinct from the selection
 * set: the zoom pivot simply uses the first selected component, if any.
 */
@Implementation
public class ComponentDiagramViewImpl
		implements ComponentDiagramView, ComponentDiagramViewServices, ComponentDiagramEvents, ActionEvents {

	/** Creates the view; its collaborators are injected by the container. */
	public ComponentDiagramViewImpl() {
		// Nothing to initialize.
	}

	private static final double COMPONENT_LEVEL_GAP = 140;
	private static final double COMPONENT_NODE_GAP = 90;
	private static final double INITIAL_TREE_WIDTH = 300;

	/** Initial width of the component tree, strip included. */
	private static final double TREE_PANEL_WIDTH = 250;

	/** Initial width of the side panel describing the selection, strip included. */
	private static final double INSPECTOR_PANEL_WIDTH = 350;

	// Toolbox icons, as SVG path data on a 16x16 grid, stroked by the
	// "sv-tool-icon" style class.
	private static final String ICON_EDGE_STRAIGHT = Toolbox.ICON_EDGE_STRAIGHT;
	private static final String ICON_EDGE_CORNER = Toolbox.ICON_EDGE_CORNER;
	private static final String ICON_EDGE_ROUND = Toolbox.ICON_EDGE_ROUND;
	private static final String ICON_RECENTER = Toolbox.ICON_RECENTER;
	private static final String ICON_SNAPSHOT = Toolbox.ICON_SNAPSHOT;
	/** The size the link kinds' icons are scaled to. */
	private static final double LINK_ICON_SIZE = 20;
	/** The margin left around the components when a snapshot or a thumbnail is cropped to them. */
	private static final int SNAPSHOT_MARGIN = 16;
	/** How far apart the two ports of a link kind's icon are drawn. */
	private static final double LINK_ICON_GAP = 12;
	/** The outline of a port's filled shape, as in the diagram. */
	private static final double PORT_OUTLINE = 1.2;
	private static final String ICON_RELAYOUT = "M2.5 2.5 H6.5 V6.5 H2.5 Z M9.5 9.5 H13.5 V13.5 H9.5 Z M6.5 4.5 H11.5 V9.5";
	private static final String ICON_LEGEND = Toolbox.ICON_LEGEND;
	private static final String ICON_UP = "M10 3.5 L5.5 8 L10 12.5";
	private static final String ICON_NEW_DIAGRAM = "M8 2.5 V13.5 M2.5 8 H13.5";
	private static final String ICON_SAVE_DIAGRAM = "M8 2 V10 M4.5 6.5 L8 10 L11.5 6.5 M2.5 11 V13.5 H13.5 V11";
	/** Books on a shelf. */
	private static final String ICON_VIEWS_LIBRARY = "M2.5 3 V12.5 M5 3 V12.5 M2.5 3 H5 M8.5 4 L11 3.4 L13.3 12 L10.8 12.6 Z M1.5 13.5 H14.5";

	private static final KeyCombination SAVE_VIEW_SHORTCUT = new KeyCodeCombination(KeyCode.S,
			KeyCombination.SHORTCUT_DOWN);

	private static final KeyCombination VIEWS_LIBRARY_SHORTCUT = new KeyCodeCombination(KeyCode.O,
			KeyCombination.SHORTCUT_DOWN);
	/** A used service plugged into a provided one, above the name glyphs. */
	private static final String ICON_PORT_NAMES = "M1.2 3.6 H5.0 M7.8 1.0 A2.6 2.6 0 0 0 7.8 6.2 "
			+ "M8.4 3.6 A1.35 1.35 0 1 0 11.1 3.6 A1.35 1.35 0 1 0 8.4 3.6 M11.1 3.6 H14.8";
	/** The composite symbol - three touching hexagons - above an eye. */
	private static final String ICON_COMPOSITES = "M6.22 3.25 L8.00 4.28 L8.00 6.32 L6.22 7.35 L4.45 6.32 L4.45 4.27 Z M9.78 3.25 L11.55 4.28 L11.55 6.32 L9.78 7.35 L8.00 6.32 L8.00 4.27 Z M8.00 0.18 L9.78 1.20 L9.78 3.25 L8.00 4.28 L6.22 3.25 L6.22 1.20 Z";
	/** The component symbol - a hexagon with its inclusion - above an eye. */
	private static final String ICON_COMPONENT = "M8.00 0.60 L10.86 2.25 L10.86 5.55 L8.00 7.20 L5.14 5.55 L5.14 2.25 Z M8 3.2 A0.7 0.7 0 1 1 7.99 3.2";
	/** A dependency's dashed arrow, above an eye. */
	private static final String ICON_DEPENDENCY = "M1.5 4 H3.3 M5 4 H6.8 M8.5 4 H10.3 M11.6 1.6 L14 4 L11.6 6.4";
	private static final String ICON_EYE_OPEN = "M2.2 12.4 Q8 7.6 13.8 12.4 Q8 17.2 2.2 12.4 Z M8 11 A1.4 1.4 0 1 1 7.99 11";
	private static final String ICON_EYE_CLOSED = "M2.2 11.6 Q8 15.8 13.8 11.6 M4.3 13.9 L3.4 15.1 M8 15 V16.2 "
			+ "M11.7 13.9 L12.6 15.1";
	/** A link's arrow, above the name glyphs. */
	private static final String ICON_RELATION_NAMES = "M1.5 3.4 H14 M11.6 1.2 L14 3.4 L11.6 5.6";
	/** "Ab" in IBM Plex Sans SemiBold, the interface's own font, as filled outlines. */
	private static final String ICON_NAME_GLYPHS = "M8.3 14.6H7.12L6.64 13.07H4.51L4.04 14.6H2.89L4.9 8.6H6.31ZM6.37 12.1 5.88 10.59 5.59 9.63H5.55L5.27 10.59L4.79 12.1Z"
			+ " M8.77 14.6V8.24H9.87V10.85H9.91Q10.03 10.47 10.37 10.24Q10.71 10.01 11.15 10.01Q11.72 10.01 12.11 10.28Q12.5 10.56 12.7 11.08Q12.91 11.6 12.91 12.35Q12.91 13.1 12.7 13.62Q12.5 14.15 12.11 14.43Q11.72 14.7 11.15 14.7Q10.71 14.7 10.37 14.47Q10.04 14.24 9.91 13.85H9.87V14.6ZM10.79 13.79Q11.22 13.79 11.49 13.5Q11.76 13.22 11.76 12.74V11.97Q11.76 11.49 11.49 11.2Q11.22 10.91 10.79 10.91Q10.54 10.91 10.33 11Q10.11 11.09 9.99 11.26Q9.87 11.42 9.87 11.63V13.06Q9.87 13.29 9.99 13.46Q10.11 13.62 10.33 13.71Q10.54 13.79 10.79 13.79Z";
	private static final double GRAPH_VIEW_MARGIN = 80;

	@UsedServices
	private ActionConsumerServices actionManagerServices;

	@UsedServices
	private PanelHostingServices useHostingServices;

	@UsedServices
	private ActionObservationServices actionObservationServices;

	@UsedServices
	private ComponentDiagramModelReadServices useComponentDiagramModelReadServices;

	private final Map<ViewKey, SessionPanel> sessions = new HashMap<>();

	/**
	 * Follows {@code SequenceRecordingActionKey.StartRecording}'s availability, the one rule for
	 * starting a recording: a hidden node, as availability binds to nodes, relayed to {@link
	 * #recordBlocked} on the FX thread.
	 */
	private final Region recordStartAvailability = new Region();

	/** Whether no recording can start now, one running: grays every control that starts one - FX thread only. */
	private final BooleanProperty recordBlocked = new SimpleBooleanProperty();

	/** The latest status badge of every observed component, applied to every tab's cards - FX thread only. */
	private Map<Class<?>, ComponentStatus> componentStatuses = Map.of();

	/**
	 * Registers this view as a listener of generic action events, and binds the availability of the recording start
	 * action to the record-blocked state that grays every control starting a recording.
	 */
	@Activation
	public void registerActionEventsListener() {
		this.actionObservationServices.addActionEventsListener(this);
		this.recordStartAvailability.disableProperty().addListener((observable, before, disabled) -> Platform
				.runLater(() -> this.recordBlocked.set(this.recordStartAvailability.isDisabled())));
		this.actionManagerServices.bindActionAvailability(SequenceRecordingActionKey.StartRecording,
				this.recordStartAvailability);
	}

	/** Removes the action events listener and the recording availability binding set up at activation. */
	@Passivation
	public void unregisterActionEventsListener() {
		this.actionObservationServices.removeActionEventsListener(this);
		this.actionManagerServices.unbindActionAvailability(SequenceRecordingActionKey.StartRecording,
				this.recordStartAvailability);
	}

	/** How many tabs this View has ever opened - only ever grows, used to title each tab. */
	private int sessionOrdinal = 0;

	/** The views library, while shown. */
	private ViewLibraryDialogs.LibraryDialog viewLibrary;

	/**
	 * Whether toggling a component's visibility from any tab's component
	 * tree should trigger an automatic relayout of that tab's graph - off
	 * by default (SupervisionHmi's shared "Preferences" menu turns it on),
	 * since hiding several components in quick succession (e.g. "Hide All"
	 * then re-checking a few) would otherwise reflow the whole diagram
	 * after every single click rather than once the user is done. Shared
	 * by every open tab alike - see class javadoc.
	 */
	private volatile boolean autoLayoutOnHide = false;

	/**
	 * The light theme's default palette: {@code PortPalette}'s values when this
	 * class is loaded, before any theme or user color is written to it.
	 */
	private static final Map<PortColorKey, Color> LIGHT_DEFAULTS = readLightDefaults();

	/**
	 * One port/wire palette per theme, each edited independently through
	 * {@link #setPortColor}. The light one starts from {@code PortPalette}'s
	 * own initial values, the dark one from {@link DarkPortPalette}. Only the
	 * current theme's palette is ever copied into {@code PortPalette}. Shared
	 * by every open tab alike - see class javadoc.
	 */
	private final Map<SupervisorTheme, Map<PortColorKey, Color>> palettes = createPalettes();

	/** The current theme, whose palette is the one copied into {@code PortPalette}. */
	private SupervisorTheme theme = SupervisorTheme.LIGHT;

	// --- ComponentDiagramEvents: tab lifecycle ---

	@Override
	public void onSessionCreated(final ViewKey sessionKey) {
		Platform.runLater(() -> {
			String title = this.useComponentDiagramModelReadServices.getSessionTitle(sessionKey);
			if (title == null) {
				this.sessionOrdinal++;
			}
			SessionPanel session = new SessionPanel(sessionKey, this.sessionOrdinal);
			session.title = title;
			session.initialLayout = this.useComponentDiagramModelReadServices.getInitialLayout(sessionKey);
			this.sessions.put(sessionKey, session);
			this.useHostingServices.openPanel(sessionKey, session.getGraphics(), session.buildTabDescriptor());
			session.requestGraphViewportFit();
		});
	}

	@Override
	public void onSessionClosed(final ViewKey sessionKey) {
		Platform.runLater(() -> {
			this.sessions.remove(sessionKey);
			this.useHostingServices.closePanel(sessionKey);
		});
	}

	@Override
	public void onTopologyChanged(final ViewKey sessionKey) {
		Platform.runLater(() -> {
			SessionPanel session = this.sessions.get(sessionKey);
			if (session != null) {
				// The current topology, whatever order the notifications arrive in.
				session.rebuildGraph(this.useComponentDiagramModelReadServices.getTopology(sessionKey));
			}
		});
	}

	@Override
	public void onSelectionChanged(final ViewKey sessionKey, final Set<Class<?>> selectedComponentTypes) {
		Platform.runLater(() -> {
			SessionPanel session = this.sessions.get(sessionKey);
			if (session != null) {
				session.setSelectedComponents(selectedComponentTypes);
			}
		});
	}

	// --- ActionEvents: generic observation, solely to recognize this View's own tab descriptors
	// coming back on a SupervisionHmiActionKey.TabSelected broadcast, so each tab's port/edge
	// activity flash animation can be skipped while that tab is not visible - a pure rendering-
	// cost saving: a flash is a transient live indicator, not a capture/history mechanism, so
	// nothing meaningful is lost by skipping one while hidden. ---

	@Override
	public void onActionTriggered(final ActionKey actionKey, final Class<?> actionCaller,
			final Class<?> actionImplementer, final Object... inputData) {
		if (actionKey == SupervisionHmiActionKey.TabSelected && inputData.length > 0
				&& inputData[0] instanceof SupervisorTabDescriptor) {
			// Always triggered from the shared TabPane's own selection listener, already on the
			// FX Application Thread - no Platform.runLater needed, unlike the ComponentDiagramEvents
			// handlers above which may be fired from any observed thread.
			this.onTabSelected((SupervisorTabDescriptor) inputData[0]);
		}
	}

	/**
	 * Marks the tab whose own descriptor just came back as selected as
	 * visible, and every other tab as hidden - a {@code TabPane} only ever
	 * has one selected tab, so no session matching {@code
	 * selectedDescriptor} means every one of this View's tabs is
	 * hidden (some other function's tab is showing).
	 *
	 * @param selectedDescriptor the newly selected tab's descriptor
	 */
	private void onTabSelected(final SupervisorTabDescriptor selectedDescriptor) {
		for (SessionPanel session : this.sessions.values()) {
			session.setVisible(session.getTabDescriptor() == selectedDescriptor);
		}
	}

	/** Applies the latest statuses to the cards and the inspector of every open tab. */
	@Override
	public void onComponentStatusesChanged() {
		Platform.runLater(() -> {
			Map<Class<?>, ComponentStatus> statuses = this.useComponentDiagramModelReadServices.getComponentStatuses();
			this.componentStatuses = statuses;
			for (SessionPanel session : this.sessions.values()) {
				session.applyComponentStatuses(statuses);
			}
		});
	}

	@Override
	public void onLiveStructureChanged() {
		// Each tab draws its own level, from onTopologyChanged.
	}

	@Override
	public void onActivitySampled(final long sampleTimeMillis, final Map<Class<?>, ComponentActivity> activities) {
		Platform.runLater(() -> {
			for (SessionPanel session : this.sessions.values()) {
				session.inspector.addActivitySample(activities);
			}
		});
	}

	/**
	 * Port activity is a live property of the observed component itself,
	 * not of any one tab's own narrowed view - so this flashes the matching
	 * ports in EVERY open tab that shows {@code componentType}, not just one.
	 * A tab that does not show it finds nothing to flash ({@code
	 * SessionPanel.findPorts(...)} guards on that).
	 */
	@Override
	public void onPortActivityChanged(final Class<?> componentType, final Class<?> portType,
			final ComponentPortKind portKind, final boolean active) {
		Platform.runLater(() -> {
			PortActivityKey key = new PortActivityKey(componentType, portType, portKind);
			if (active) {
				this.activePorts.add(key);
			} else {
				this.activePorts.remove(key);
			}
			for (SessionPanel session : this.sessions.values()) {
				session.applyPortActivity(componentType, portType, portKind, active);
			}
		});
	}

	@Override
	public void onFocusPathChanged(final ViewKey sessionKey, final List<Class<?>> focusPath) {
		Platform.runLater(() -> {
			SessionPanel session = this.sessions.get(sessionKey);
			if (session != null) {
				session.setFocusPath(focusPath);
			}
		});
	}

	@Override
	public void onDependenciesVisibilityChanged(final ViewKey sessionKey, final Class<?> componentType, final boolean visible) {
		Platform.runLater(() -> {
			SessionPanel session = this.sessions.get(sessionKey);
			if (session != null) {
				session.applyDependenciesVisibility(componentType, visible);
			}
		});
	}

	@Override
	public void onFlashComponentChanged(final ViewKey sessionKey, final Class<?> componentType) {
		Platform.runLater(() -> {
			SessionPanel session = this.sessions.get(sessionKey);
			if (session != null) {
				session.flashComponent(componentType);
			}
		});
	}

	@Override
	public void onLinkHighlightRequested(final ViewKey sessionKey, final Class<?> sourceType,
			final Class<?> targetType) {
		Platform.runLater(() -> {
			SessionPanel session = this.sessions.get(sessionKey);
			if (session != null) {
				this.useHostingServices.openPanel(sessionKey, session.getGraphics(), session.buildTabDescriptor());
				session.highlightLink(sourceType, targetType, true);
			}
		});
	}

	// --- ComponentDiagramViewServices: per-tab accessors ---

	@Override
	public void bringToFront(final ViewKey sessionKey) {
		Platform.runLater(() -> {
			SessionPanel session = this.sessions.get(sessionKey);
			if (session != null) {
				this.useHostingServices.openPanel(sessionKey, session.getGraphics(), session.buildTabDescriptor());
			}
		});
	}

	@Override
	public void recenter(final ViewKey sessionKey) {
		FxThread.run(() -> {
			SessionPanel session = this.sessions.get(sessionKey);
			if (session != null) {
				session.requestGraphViewportFit();
			}
		});
	}

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
	public double getZoomScale(final ViewKey sessionKey) {
		SessionPanel session = this.sessions.get(sessionKey);
		return session == null ? 1.0 : session.getZoomScale();
	}

	@Override
	public void exportSnapshot(final ViewKey sessionKey) {
		FxThread.run(() -> {
			SessionPanel session = this.sessions.get(sessionKey);
			if (session != null) {
				Snapshots.saveAndCopy(session.captureSnapshot(), "arch");
			}
		});
	}

	@Override
	public Set<Class<?>> getHiddenComponents(final ViewKey sessionKey) {
		SessionPanel session = this.sessions.get(sessionKey);
		return session == null ? Set.of() : session.getHiddenComponents();
	}

	@Override
	public Map<Class<?>, double[]> getPositions(final ViewKey sessionKey) {
		SessionPanel session = this.sessions.get(sessionKey);
		return session == null ? Map.of() : session.getPositions();
	}

	@Override
	public byte[] captureThumbnail(final ViewKey sessionKey) {
		SessionPanel session = this.sessions.get(sessionKey);
		Image thumbnail = session == null ? null : session.captureThumbnail();
		return thumbnail == null ? null : Snapshots.toPng(thumbnail);
	}

	@Override
	public String getTabTitle(final ViewKey sessionKey) {
		SessionPanel session = this.sessions.get(sessionKey);
		return session == null ? "" : session.currentTitle();
	}

	@Override
	public void setTabTitle(final ViewKey sessionKey, final String title) {
		FxThread.run(() -> {
			SessionPanel session = this.sessions.get(sessionKey);
			if (session != null) {
				session.title = title;
				this.useHostingServices.setPanelTitle(sessionKey, title);
			}
		});
	}

	@Override
	public void showSaveViewDialog(final ViewKey sessionKey, final SaveViewForm form) {
		FxThread.run(() -> {
			SessionPanel session = this.sessions.get(sessionKey);
			if (session == null) {
				return;
			}
			ViewLibraryDialogs.showSaveDialog(session.window(), form, session.captureThumbnail(),
					(name, description) -> this.actionManagerServices.executeAction(
							new ComponentDiagramActionKey(sessionKey, Role.ConfirmSaveView),
							new SaveViewRequest(name, description)));
		});
	}

	@Override
	public void showViewLibrary(final ViewKey sessionKey, final ViewLibraryContent content) {
		FxThread.run(() -> {
			SessionPanel session = this.sessions.get(sessionKey);
			if (session == null) {
				return;
			}
			if (this.viewLibrary != null && this.viewLibrary.isShowing()) {
				this.viewLibrary.update(content);
				this.viewLibrary.toFront();
				return;
			}
			this.viewLibrary = new ViewLibraryDialogs.LibraryDialog(session.window(), content,
					new ViewLibraryDialogs.LibraryActions() {

						@Override
						public void open(final SavedView view) {
							ComponentDiagramViewImpl.this.dispatch(sessionKey, Role.OpenView, view);
						}

						@Override
						public void rename(final SavedView view, final String newName) {
							ComponentDiagramViewImpl.this.dispatch(sessionKey, Role.RenameView,
									new RenameViewRequest(view, newName));
						}

						@Override
						public void delete(final SavedView view) {
							ComponentDiagramViewImpl.this.dispatch(sessionKey, Role.DeleteView, view);
						}

						@Override
						public void export(final SavedView view, final File file) {
							ComponentDiagramViewImpl.this.dispatch(sessionKey, Role.ExportView,
									new ExportViewRequest(view, file));
						}

						@Override
						public void importFile(final File file) {
							ComponentDiagramViewImpl.this.dispatch(sessionKey, Role.ImportView, file);
						}
					});
		});
	}

	@Override
	public void updateViewLibrary(final ViewLibraryContent content) {
		FxThread.run(() -> {
			if (this.viewLibrary != null && this.viewLibrary.isShowing()) {
				this.viewLibrary.update(content);
			}
		});
	}

	@Override
	public boolean confirm(final ViewKey sessionKey, final String title, final String message,
			final String confirmLabel) {
		SessionPanel session = this.sessions.get(sessionKey);
		Window owner = this.viewLibrary != null && this.viewLibrary.isShowing() ? this.viewLibrary.window()
				: session == null ? null : session.window();
		return ViewLibraryDialogs.confirm(owner, title, message, confirmLabel, false);
	}

	private void dispatch(final ViewKey sessionKey, final Role role, final Object payload) {
		this.actionManagerServices.executeAction(new ComponentDiagramActionKey(sessionKey, role), payload);
	}

	@Override
	public void showError(final ViewKey sessionKey, final String message) {
		FxThread.run(() -> {
			SessionPanel session = this.sessions.get(sessionKey);
			if (session != null) {
				session.showError(message);
			}
		});
	}

	// --- ComponentDiagramViewServices: tool-wide preferences, applied to every open tab ---

	@Override
	public void setAutoLayoutOnHide(final boolean autoLayoutOnHide) {
		FxThread.run(() -> {
			this.autoLayoutOnHide = autoLayoutOnHide;
		});
	}

	/**
	 * Records the color in the current theme's palette, writes it to
	 * {@code PortPalette}, then repaints every open tab through {@code
	 * SessionPanel.repaintPalette()}.
	 */
	@Override
	public void setPortColor(final PortColorKey key, final String web) {
		FxThread.run(() -> {
			Color value = WebColors.toColor(web);
			if (value == null) {
				return;
			}
			this.palettes.get(this.theme).put(key, value);
			writePortPalette(key, value);
			for (SessionPanel session : this.sessions.values()) {
				session.repaintPalette();
			}
		});
	}

	@Override
	public String getPortColor(final PortColorKey key) {
		return WebColors.toWeb(this.palettes.get(this.theme).get(key));
	}

	/**
	 * Copies {@code newTheme}'s whole palette into {@code PortPalette}, then
	 * repaints every open tab and rebuilds its component tree, whose node
	 * icons are drawn from the palette too.
	 */
	@Override
	public void setTheme(final SupervisorTheme newTheme) {
		FxThread.run(() -> {
			this.theme = newTheme;
			for (Map.Entry<PortColorKey, Color> entry : this.palettes.get(newTheme).entrySet()) {
				writePortPalette(entry.getKey(), entry.getValue());
			}
			for (SessionPanel session : this.sessions.values()) {
				session.repaintPalette();
				session.rebuildComponentTreeWithCurrentFilter();
			}
		});
	}

	@Override
	public String getDefaultPortColor(final PortColorKey key) {
		return WebColors.toWeb(defaultPalette(this.theme).get(key));
	}

	@Override
	public void resetPortColors() {
		FxThread.run(() -> {
			this.palettes.put(this.theme, defaultPalette(this.theme));
			this.setTheme(this.theme);
		});
	}

	private static Map<SupervisorTheme, Map<PortColorKey, Color>> createPalettes() {
		Map<SupervisorTheme, Map<PortColorKey, Color>> palettes = new EnumMap<>(SupervisorTheme.class);
		for (SupervisorTheme theme : SupervisorTheme.values()) {
			palettes.put(theme, defaultPalette(theme));
		}
		return palettes;
	}

	/**
	 * Returns a fresh, mutable copy of a theme's default palette: {@code
	 * PortPalette}'s initial values for the light theme, {@code
	 * DarkPortPalette}'s for the dark one.
	 */
	private static Map<PortColorKey, Color> defaultPalette(final SupervisorTheme theme) {
		return theme == SupervisorTheme.DARK ? DarkPortPalette.defaults() : new EnumMap<>(LIGHT_DEFAULTS);
	}

	private static Map<PortColorKey, Color> readLightDefaults() {
		Map<PortColorKey, Color> light = new EnumMap<>(PortColorKey.class);
		for (PortColorKey key : PortColorKey.values()) {
			light.put(key, readPortPalette(key));
		}
		return light;
	}

	private static void writePortPalette(final PortColorKey key, final Color value) {
		switch (key) {
			case GRAPHITE -> PortPalette.GRAPHITE = value;
			case BODY -> PortPalette.BODY = value;
			case HEADER -> PortPalette.HEADER = value;
			case CARD_BORDER_SOFT -> PortPalette.CARD_BORDER_SOFT = value;
			case HEADER_DIVIDER -> PortPalette.HEADER_DIVIDER = value;
			case ICON_DIVIDER -> PortPalette.ICON_DIVIDER = value;
			case STATUS_ACTIVATED -> PortPalette.STATUS_ACTIVATED = value;
			case STATUS_PASSIVATED -> PortPalette.STATUS_PASSIVATED = value;
			case STATUS_STARTING -> PortPalette.STATUS_STARTING = value;
			case STATUS_INACTIVE -> PortPalette.STATUS_INACTIVE = value;
			case STATUS_FAILED -> PortPalette.STATUS_FAILED = value;
			case STATUS_MIXED -> PortPalette.STATUS_MIXED = value;
			case STATUS_DEGRADED -> PortPalette.STATUS_DEGRADED = value;
			case SLOT_IDLE -> PortPalette.SLOT_IDLE = value;
			case SERVICE_PROVIDED -> PortPalette.SERVICE_PROVIDED = value;
			case SERVICE_USED -> PortPalette.SERVICE_USED = value;
			case EVENT_PRODUCED -> PortPalette.EVENT_PRODUCED = value;
			case EVENT_CONSUMED -> PortPalette.EVENT_CONSUMED = value;
			case PARAMETER -> PortPalette.PARAMETER = value;
			case PARAMETER_ACCESSED -> PortPalette.PARAMETER_ACCESSED = value;
			case COMPONENT_TYPE -> PortPalette.COMPONENT_TYPE = value;
			case SERVICE_ACTIVITY -> PortPalette.SERVICE_ACTIVITY = value;
			case SERVICE_FAILURE -> PortPalette.SERVICE_FAILURE = value;
			case EVENT_ACTIVITY -> PortPalette.EVENT_ACTIVITY = value;
			case HIGHLIGHT -> PortPalette.HIGHLIGHT = value;
			case HOVER -> PortPalette.HOVER = value;
			case SELECTION -> PortPalette.SELECTION = value;
		}
	}

	private static Color readPortPalette(final PortColorKey key) {
		return switch (key) {
			case GRAPHITE -> PortPalette.GRAPHITE;
			case BODY -> PortPalette.BODY;
			case HEADER -> PortPalette.HEADER;
			case CARD_BORDER_SOFT -> PortPalette.CARD_BORDER_SOFT;
			case HEADER_DIVIDER -> PortPalette.HEADER_DIVIDER;
			case ICON_DIVIDER -> PortPalette.ICON_DIVIDER;
			case STATUS_ACTIVATED -> PortPalette.STATUS_ACTIVATED;
			case STATUS_PASSIVATED -> PortPalette.STATUS_PASSIVATED;
			case STATUS_STARTING -> PortPalette.STATUS_STARTING;
			case STATUS_INACTIVE -> PortPalette.STATUS_INACTIVE;
			case STATUS_FAILED -> PortPalette.STATUS_FAILED;
			case STATUS_MIXED -> PortPalette.STATUS_MIXED;
			case STATUS_DEGRADED -> PortPalette.STATUS_DEGRADED;
			case SLOT_IDLE -> PortPalette.SLOT_IDLE;
			case SERVICE_PROVIDED -> PortPalette.SERVICE_PROVIDED;
			case SERVICE_USED -> PortPalette.SERVICE_USED;
			case EVENT_PRODUCED -> PortPalette.EVENT_PRODUCED;
			case EVENT_CONSUMED -> PortPalette.EVENT_CONSUMED;
			case PARAMETER -> PortPalette.PARAMETER;
			case PARAMETER_ACCESSED -> PortPalette.PARAMETER_ACCESSED;
			case COMPONENT_TYPE -> PortPalette.COMPONENT_TYPE;
			case SERVICE_ACTIVITY -> PortPalette.SERVICE_ACTIVITY;
			case SERVICE_FAILURE -> PortPalette.SERVICE_FAILURE;
			case EVENT_ACTIVITY -> PortPalette.EVENT_ACTIVITY;
			case HIGHLIGHT -> PortPalette.HIGHLIGHT;
			case HOVER -> PortPalette.HOVER;
			case SELECTION -> PortPalette.SELECTION;
		};
	}

	/** Translates this composite's own JavaFX-free {@link ComponentPortKind} into the
	 *  diagram rendering package's own enum of the same shape. */
	private static org.opales.covalent.supervision.componentview.internal.diagram.cells.ComponentPortKind toDiagramKind(
			final ComponentPortKind kind) {
		switch (kind) {
		case SERVICE:
			return org.opales.covalent.supervision.componentview.internal.diagram.cells.ComponentPortKind.SERVICE;
		case EVENT:
			return org.opales.covalent.supervision.componentview.internal.diagram.cells.ComponentPortKind.EVENT;
		case PARAMETER:
			return org.opales.covalent.supervision.componentview.internal.diagram.cells.ComponentPortKind.PARAMETER;
		default:
			return org.opales.covalent.supervision.componentview.internal.diagram.cells.ComponentPortKind.COMPONENT_TYPE;
		}
	}

	private static ComponentPortActivity activityFor(final ComponentPortKind portKind) {
		switch (portKind) {
		case SERVICE:
			return ComponentPortActivity.SERVICE_RUNNING;
		case PARAMETER:
			return ComponentPortActivity.PARAMETER_RUNNING;
		case EVENT:
			return ComponentPortActivity.EVENT_RUNNING;
		default:
			return ComponentPortActivity.IDLE;
		}
	}

	/** A component's ports of one specific interface and kind, as the activity notifications name them. */
	private record PortActivityKey(Class<?> componentType, Class<?> portType, ComponentPortKind portKind) {
	}

	/**
	 * The ports active right now. Activity arrives as changes only, so a tab whose cells are
	 * rebuilt - entering or leaving a composite - or which was hidden, starts from this set
	 * rather than waiting for the next change, which never comes while the flow lasts.
	 */
	private final Set<PortActivityKey> activePorts = new HashSet<>();

	/** The top level's key among a tab's levels: no composite holds its components. */
	private static final Object TOP_LEVEL = new Object();

	/**
	 * How a level was left: its components' positions, the zoom and the view's offset.
	 *
	 * @param positions  each component's position
	 * @param scale      the zoom
	 * @param translateX the view's horizontal offset
	 * @param translateY the view's vertical offset
	 */
	private record LevelLayout(Map<Class<?>, double[]> positions, double scale, double translateX, double translateY) {
	}

	/** One open tab's own canvas, component tree, selection and focus path. */
	private final class SessionPanel {

		private final ViewKey sessionKey;
		private final int ordinal;

		/** This tab's own descriptor, the one {@link #buildTabDescriptor()} built last (each call
		 *  builds a new instance, which its caller hands to the hosting panel, which keeps it):
		 *  identity matters here, since {@link ComponentDiagramViewImpl#onTabSelected} recognizes a
		 *  tab by comparing the descriptor a {@code TabSelected} broadcast carries against this
		 *  exact instance. */
		private SupervisorTabDescriptorAdapter tabDescriptor;

		/** Whether this tab is the one selected in the shared {@code TabPane} -
		 *  gates only the port/edge activity flash animation, see {@link #applyPortActivity}. */
		private boolean visible = true;

		/** The activities begun on this tab's current cells, each to be finished exactly once. */
		private final Set<PortActivityKey> begunPorts = new HashSet<>();

		private final Map<Class<?>, CovalentComponentCell> componentItems = new HashMap<>();

		/** The side panel describing the selected component. */
		private final ComponentInspector inspector = new ComponentInspector(new ComponentInspector.Actions() {
			@Override
			public void showDependencies() {
				SessionPanel.this.dispatchToggleDependenciesVisibility(true);
			}

			@Override
			public void hide() {
				SessionPanel.this.dispatchHideSelection();
			}

			@Override
			public void record(final Set<Class<?>> componentTypes) {
				ComponentDiagramViewImpl.this.actionManagerServices
						.executeAction(SequenceRecordingActionKey.StartRecording, componentTypes);
			}
		}, status -> readPortPalette(status.getColorKey()), ComponentDiagramViewImpl.this.recordBlocked);

		private GraphModel model;
		private Graph graph;
		private ElkLayout layout;
		private final ComponentPortSideLayout portSideLayout = new ComponentPortSideLayout();

		private TreeView<ComponentTreeItem> componentsList;
		private TreeItem<ComponentTreeItem> rootNode;
		private TextField treeSearchField;
		private final Map<Class<?>, TreeItem<ComponentTreeItem>> treeNodesByType = new HashMap<>();
		private final Map<CovalentComponentCell, Class<?>> componentTypesByCell = new HashMap<>();
		/** Full-hierarchy composite set (has children anywhere, not just at the current
		 *  focus level) - which tree rows get the composite icon. */
		private Set<Class<?>> allCompositeTypes = Set.of();
		/** The observed container's name, shown on the tree's virtual root; empty when unknown. */
		private String containerName = "";
		/** The last topology the Model sent - kept so the search field can rebuild
		 *  just the tree panel on every keystroke without a fresh Controller round-trip. */
		private ComponentDiagramTopology lastTopology;

		/** Local rendering mirror only - the Controller is the source of truth. */
		private final LinkedHashSet<Class<?>> selectedComponentTypes = new LinkedHashSet<>();

		/**
		 * Local rendering mirror only - the Controller is the source of truth.
		 * Which of the visible components have children to drill
		 * into, i.e. are worth reacting to a double-click on.
		 */
		private Set<Class<?>> compositeTypes = Set.of();

		/**
		 * Local rendering mirror only - the Controller is the source of truth.
		 * Drives {@link #renderBreadcrumb()}; the canvas itself renders whatever
		 * level {@link #rebuildGraph(ComponentDiagramTopology)} hands it, since
		 * the Controller publishes the topology already narrowed to this tab's
		 * current focus.
		 */
		private volatile List<Class<?>> focusPath = List.of();

		/** The tab's title, {@code null} for the default one. */
		private String title;

		/** The layout this tab starts with, until applied - see {@link #rebuildGraph(ComponentDiagramTopology)}. */
		private ComponentDiagramLayout initialLayout;

		/** The level shown: the composite entered, {@link #TOP_LEVEL} at the top - see {@link #levelOf}. */
		private Object level;

		/**
		 * How each level visited was last laid out, while the tab is open: going back to one
		 * finds it as it was left rather than laid out again.
		 */
		private final Map<Object, LevelLayout> levelLayouts = new HashMap<>();

		private final Map<CovalentComponentCell, Set<CovalentComponentCell>> comToDepsCom = new HashMap<>();
		private final Map<Pair, Integer> wiringCount = new HashMap<>();
		private final Set<AnchoredPortEdge> portEdges = new HashSet<>();

		private final BorderPane root;
		private HBox breadcrumbBar;
		private BorderPane componentsViewContainer;
		private StackPane graphCanvasHost;
		private Pane graphSelectionOverlay;
		private Rectangle graphSelectionRectangle;
		private boolean graphSelectionActive = false;
		/** Per link, whether it was hidden from its own context menu - reset by {@link #showAll()}. */
		private final Map<AnchoredPortEdge, BooleanProperty> linkShown = new HashMap<>();

		/** Whether the port color legend is shown over this tab's canvas. */
		private final BooleanProperty legendVisible = new SimpleBooleanProperty(true);

		/** The context menu shown by this tab, if any - see {@link #showContextMenu}. */
		private ContextMenu openContextMenu;
		/** Ctrl adds the rectangle's contents to the selection; Shift replaces it. */
		private boolean graphSelectionAdditive = false;
		private double graphSelectionStartX;
		private double graphSelectionStartY;
		private boolean notifyTreeSelection = true;
		private boolean graphViewportFitPending = false;

		/**
		 * Coalesces a burst of visibility changes from the same UI gesture
		 * (e.g. checking a package node cascades to every component beneath
		 * it, each one a separate {@code visibility.selectedProperty()}
		 * change) into a single relayout of this tab's graph, rather than one
		 * per change.
		 */
		private boolean relayoutScheduled = false;

		/**
		 * True only while {@link #rebuildGraph} is applying a saved view's own
		 * layout: {@link #applyHiddenComponents} toggles visibility exactly
		 * like Hide/Show do, which would otherwise arm the same auto-layout-
		 * on-hide relayout those gestures rely on - undoing the positions
		 * {@link #applyPositions} is about to restore a moment later, on the
		 * next pulse.
		 */
		private boolean restoringSavedLayout = false;

		SessionPanel(final ViewKey sessionKey, final int ordinal) {
			this.sessionKey = sessionKey;
			this.ordinal = ordinal;
			this.root = new BorderPane();
			this.buildContent();
			// Ctrl+S saves the view (replacing the tab's own view directly), Ctrl+O opens the views library.
			this.root.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
				if (SAVE_VIEW_SHORTCUT.match(event)) {
					event.consume();
					ComponentDiagramViewImpl.this.actionManagerServices
							.executeAction(new ComponentDiagramActionKey(this.sessionKey, Role.QuickSaveView));
				} else if (VIEWS_LIBRARY_SHORTCUT.match(event)) {
					event.consume();
					ComponentDiagramViewImpl.this.actionManagerServices
							.executeAction(new ComponentDiagramActionKey(this.sessionKey, Role.OpenViewLibrary));
				}
			});
		}

		// --- Content construction ---

		private void buildContent() {
			this.componentsList = new TreeView<>();
			this.componentsList.setShowRoot(true);
			this.componentsList.setRoot(new TreeItem<>(new ComponentTreeItem(null, "")));
			this.componentsList.setMinWidth(220);
			this.componentsList.setPrefWidth(INITIAL_TREE_WIDTH);
			this.rootNode = this.componentsList.getRoot();
			this.componentsList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
			this.componentsList.getSelectionModel().getSelectedItems()
					.addListener((final Change<? extends TreeItem<ComponentTreeItem>> c) -> {
						if (this.notifyTreeSelection) {
							this.syncSelectionFromTree();
						}
					});
			this.componentsList.setCellFactory(treeView -> this.buildComponentTreeCell());
			// Denser rows than the ~24px JavaFX default, sized for the 12px tree
			// font (see "sv-component-tree") and the icon glyph in buildComponentTreeCell().
			this.componentsList.setFixedCellSize(22);
			this.componentsList.getStyleClass().add("sv-component-tree");

			this.treeSearchField = new TextField();
			this.treeSearchField.setPromptText(ComponentDiagramMessages.get("tree.search.prompt"));
			this.treeSearchField.textProperty().addListener((observable, oldValue, newValue) -> {
				if (this.lastTopology != null) {
					this.rebuildComponentTree(this.lastTopology, newValue);
				}
			});
			VBox treePanel = new VBox(4, this.treeSearchField, this.componentsList);
			treePanel.setPadding(new Insets(4, 0, 0, 4));
			treePanel.setMaxWidth(384);
			VBox.setVgrow(this.componentsList, Priority.ALWAYS);

			this.layout = new ElkLayout(COMPONENT_LEVEL_GAP, COMPONENT_NODE_GAP);
			this.graph = new Graph();
			this.model = this.graph.getModel();
			this.configureGraphGestures();

			Button hideAll = this.symbolIconButton(ICON_COMPOSITES, ICON_EYE_CLOSED, "button.hideAll");
			hideAll.setOnAction(e -> {
				for (CovalentComponentCell component : this.componentItems.values()) {
					component.setVisible(false);
				}
			});
			// Hides only the current selection (and, for free, every link touching it -
			// an edge's visibility is the AND of both endpoints' own visibility,
			// so unchecking one end hides it) - Show All is the undo: it re-checks
			// every tree node regardless of how it got unchecked.
			Button hide = this.symbolIconButton(ICON_COMPONENT, ICON_EYE_CLOSED, "tooltip.hide");
			hide.setOnAction(e -> this.dispatchHideSelection());
			Button showAll = this.symbolIconButton(ICON_COMPOSITES, ICON_EYE_OPEN, "button.showAll");
			showAll.setOnAction(e -> this.showAll());
			Button showDeps = this.symbolIconButton(ICON_DEPENDENCY, ICON_EYE_OPEN, "tooltip.showDeps");
			showDeps.setOnAction(e -> this.dispatchToggleDependenciesVisibility(true));
			Button hideDeps = this.symbolIconButton(ICON_DEPENDENCY, ICON_EYE_CLOSED, "tooltip.hideDeps");
			hideDeps.setOnAction(e -> this.dispatchToggleDependenciesVisibility(false));
			Button recenter = this.iconButton(ICON_RECENTER, "button.recenter");
			recenter.setOnAction(e -> ComponentDiagramViewImpl.this.actionManagerServices
					.executeAction(new ComponentDiagramActionKey(this.sessionKey, Role.Recenter)));
			Button relayout = this.iconButton(ICON_RELAYOUT, "button.relayout");
			relayout.setOnAction(e -> this.relayout());
			ToggleButton legend = this.iconToggle(ICON_LEGEND, "button.legend");
			legend.selectedProperty().bindBidirectional(this.legendVisible);
			Button snapshot = this.iconButton(ICON_SNAPSHOT, "button.snapshot");
			snapshot.setOnAction(e -> ComponentDiagramViewImpl.this.actionManagerServices
					.executeAction(new ComponentDiagramActionKey(this.sessionKey, Role.Export)));
			Button newDiagram = this.iconButton(ICON_NEW_DIAGRAM, "button.newDiagram");
			newDiagram.setOnAction(e -> ComponentDiagramViewImpl.this.actionManagerServices
					.executeAction(new ComponentDiagramActionKey(this.sessionKey, Role.NewSession)));
			Button saveDiagram = this.iconButton(ICON_SAVE_DIAGRAM, "button.saveView");
			saveDiagram.setOnAction(e -> ComponentDiagramViewImpl.this.actionManagerServices
					.executeAction(new ComponentDiagramActionKey(this.sessionKey, Role.SaveView)));
			Button loadDiagram = this.iconButton(ICON_VIEWS_LIBRARY, "button.viewsLibrary");
			loadDiagram.setOnAction(e -> ComponentDiagramViewImpl.this.actionManagerServices
					.executeAction(new ComponentDiagramActionKey(this.sessionKey, Role.OpenViewLibrary)));

			ToggleButton showPortNames = this.namesToggle(ICON_PORT_NAMES, "tooltip.portNames");
			showPortNames.selectedProperty().bindBidirectional(this.graph.showPortNamesProperty());
			ToggleButton showRelationNames = this.namesToggle(ICON_RELATION_NAMES, "tooltip.relationNames");
			showRelationNames.selectedProperty().bindBidirectional(this.graph.showRelationNamesProperty());

			ToggleGroup edgeStyleGroup = new ToggleGroup();
			ToggleButton straightEdges = this.iconToggle(ICON_EDGE_STRAIGHT, "radio.straight");
			straightEdges.setToggleGroup(edgeStyleGroup);
			straightEdges.setUserData(EdgeRoutingStyle.STRAIGHT);
			ToggleButton cornerEdges = this.iconToggle(ICON_EDGE_CORNER, "radio.corner");
			cornerEdges.setToggleGroup(edgeStyleGroup);
			cornerEdges.setUserData(EdgeRoutingStyle.CORNER);
			ToggleButton roundEdges = this.iconToggle(ICON_EDGE_ROUND, "radio.round");
			roundEdges.setToggleGroup(edgeStyleGroup);
			roundEdges.setUserData(EdgeRoutingStyle.ROUND);
			// Two-way, without bindBidirectional (there's no built-in adapter between
			// an enum ObjectProperty and a ToggleGroup's selectedToggleProperty):
			// reflect the model into the initial selection, then each direction has
			// its own listener; the null-toggle guard on deselect keeps the group
			// from ever landing with nothing selected between two clicks.
			for (Toggle toggle : edgeStyleGroup.getToggles()) {
				if (toggle.getUserData() == this.graph.edgeRoutingStyleProperty().get()) {
					edgeStyleGroup.selectToggle(toggle);
				}
			}
			edgeStyleGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
				if (newToggle != null) {
					this.graph.edgeRoutingStyleProperty().set((EdgeRoutingStyle) newToggle.getUserData());
				} else if (oldToggle != null) {
					// A ToggleButton, unlike a RadioButton, deselects on a second click.
					edgeStyleGroup.selectToggle(oldToggle);
				}
			});
			this.graph.edgeRoutingStyleProperty().addListener((observable, oldStyle, newStyle) -> {
				for (Toggle toggle : edgeStyleGroup.getToggles()) {
					if (toggle.getUserData() == newStyle) {
						edgeStyleGroup.selectToggle(toggle);
					}
				}
			});

			ToggleButton showServiceLinks = this.linkKindToggle(serviceLinkIcon(), "checkbox.serviceLinks",
					this.graph.showServiceLinksProperty());
			ToggleButton showEventLinks = this.linkKindToggle(eventLinkIcon(), "checkbox.eventLinks",
					this.graph.showEventLinksProperty());
			ToggleButton showParameterLinks = this.linkKindToggle(parameterLinkIcon(), "checkbox.parameterLinks",
					this.graph.showParameterLinksProperty());
			HBox linkKinds = new HBox(2, showServiceLinks, showEventLinks, showParameterLinks);
			linkKinds.getStyleClass().add("sv-segmented");

			// Toolbox panel: three titled categories side by side, each in its own box -
			// formatting (edge shape, recenter, names, per-port-kind link visibility),
			// structure (show/hide all, show/hide deps) and diagram (snapshot, new
			// diagram, save, load). The diagram category is icon-only and never
			// shrinks; the other two wrap their own controls when space runs out.
			HBox edgeStyles = new HBox(2, straightEdges, cornerEdges, roundEdges);
			edgeStyles.getStyleClass().add("sv-segmented");
			// Three sub-groups, kept whole when the category wraps: how links are drawn,
			// how the diagram is laid out, what is shown.
			Node formattingCategory = this.buildToolboxCategory(ComponentDiagramMessages.get("toolbox.formatting.title"),
					edgeStyles, toolSeparator(), toolGroup(recenter, relayout, legend), toolSeparator(),
					toolGroup(showPortNames, showRelationNames, linkKinds));
			Node structureCategory = this.buildToolboxCategory(ComponentDiagramMessages.get("toolbox.structure.title"),
					showAll, hideAll, hide, showDeps, hideDeps);
			Node diagramCategory = this.buildFixedToolboxCategory(ComponentDiagramMessages.get("toolbox.diagram.title"),
					snapshot, newDiagram, saveDiagram, loadDiagram);
			HBox.setHgrow(formattingCategory, Priority.SOMETIMES);
			HBox.setHgrow(structureCategory, Priority.SOMETIMES);

			HBox toolbox = new HBox(8, formattingCategory, structureCategory, diagramCategory);
			toolbox.getStyleClass().add("sv-toolbox");
			toolbox.setPadding(new Insets(6, 8, 6, 8));

			this.breadcrumbBar = new HBox(4);
			this.breadcrumbBar.setPadding(new Insets(4, 8, 4, 8));
			this.breadcrumbBar.setAlignment(Pos.CENTER_LEFT);
			this.renderBreadcrumb();

			this.root.setTop(new VBox(toolbox, this.breadcrumbBar));

			this.componentsViewContainer = new BorderPane();
			this.componentsViewContainer.widthProperty().addListener((s, o, n) -> this.fitGraphToViewportIfPending());
			this.componentsViewContainer.heightProperty().addListener((s, o, n) -> this.fitGraphToViewportIfPending());
			this.installGraphCanvasHost();

			Region inspectorPanel = this.inspector.getGraphics();
			SplitPane split = new SplitPane(treePanel, this.componentsViewContainer, inspectorPanel);
			CollapsibleSide.install(split, treePanel, true, "components.tree");
			CollapsibleSide.install(split, inspectorPanel, false, "components.inspector");
			// The side panels open at fixed widths, whatever the window's.
			split.widthProperty().addListener(new ChangeListener<Number>() {
				@Override
				public void changed(final ObservableValue<? extends Number> observable,
						final Number oldWidth, final Number newWidth) {
					if (newWidth.doubleValue() > 0) {
						split.setDividerPositions(TREE_PANEL_WIDTH / newWidth.doubleValue(),
								1 - INSPECTOR_PANEL_WIDTH / newWidth.doubleValue());
						observable.removeListener(this);
					}
				}
			});
			SplitPane.setResizableWithParent(this.componentsViewContainer, true);
			this.root.setCenter(split);

			this.zoomScaleProperty().addListener((observable, oldValue, newValue) -> ComponentDiagramViewImpl.this.actionManagerServices
					.executeAction(new ComponentDiagramActionKey(this.sessionKey, Role.ZoomChanged), newValue.doubleValue()));
		}

		/**
		 * The Hide button: unchecks the tree node of every selected
		 * component, the same mechanism {@link #applyDependenciesVisibility}
		 * uses to hide a dependency - a no-op on an empty selection.
		 */
		private void dispatchHideSelection() {
			for (Class<?> componentType : this.selectedComponentTypes) {
				CovalentComponentCell component = this.componentItems.get(componentType);
				if (component != null) {
					component.setVisible(false);
				}
			}
		}

		/**
		 * Show/hide deps: dispatches once per selected component, so the whole
		 * multi-selection is affected - each dispatch is independent (the Model
		 * fires a transient event, nothing stored keyed by a single "current"
		 * component), so looping is safe.
		 */
		private void dispatchToggleDependenciesVisibility(final boolean show) {
			for (Class<?> componentType : this.selectedComponentTypes) {
				ComponentDiagramViewImpl.this.actionManagerServices.executeAction(
						new ComponentDiagramActionKey(this.sessionKey, Role.ToggleDependenciesVisibility), componentType, show);
			}
		}

		/** Controls that stay together on one row of a wrapping toolbox category. */
		private static HBox toolGroup(final Node... controls) {
			HBox group = new HBox(6, controls);
			group.setAlignment(Pos.CENTER_LEFT);
			return group;
		}

		/** A vertical line between two groups of a toolbox category. */
		private static Region toolSeparator() {
			Region separator = new Region();
			separator.getStyleClass().add("sv-tool-separator");
			return separator;
		}

		/**
		 * One bordered, titled group of controls for the toolbox panel, wrapped
		 * in a {@link FlowPane} so a category with many controls (formatting,
		 * with its link-visibility toggles) wraps onto another row instead of
		 * forcing the window wider - same visual language as the per-category
		 * boxes of the Preferences panel.
		 *
		 * @param title    the category's title
		 * @param controls the controls of the category
		 * @return the category's box
		 */
		private Node buildToolboxCategory(final String title, final Node... controls) {
			Label header = new Label(title);
			header.getStyleClass().add("sv-category-title");
			FlowPane controlsFlow = new FlowPane(6, 6, controls);
			controlsFlow.setAlignment(Pos.CENTER_LEFT);
			return this.wrapToolboxCategory(header, controlsFlow);
		}

		/**
		 * A titled toolbox category whose controls stay on a single row and
		 * never wrap - sized to its content, for a short, icon-only group.
		 */
		private Node buildFixedToolboxCategory(final String title, final Node... controls) {
			Label header = new Label(title);
			header.getStyleClass().add("sv-category-title");
			HBox controlsRow = new HBox(6, controls);
			controlsRow.setAlignment(Pos.CENTER_LEFT);
			Region box = this.wrapToolboxCategory(header, controlsRow);
			box.setMinWidth(Region.USE_PREF_SIZE);
			box.setMaxWidth(Region.USE_PREF_SIZE);
			return box;
		}

		private VBox wrapToolboxCategory(final Label header, final Node controls) {
			VBox box = new VBox(5, header, controls);
			box.setPadding(new Insets(6, 8, 8, 8));
			box.getStyleClass().add("sv-category");
			return box;
		}

		/**
		 * A toolbox toggle showing or hiding every link of one port kind, drawn
		 * as that kind's two port ends mated together; dimmed while its links
		 * are hidden.
		 */
		private ToggleButton linkKindToggle(final Node icon, final String labelKey, final BooleanProperty linksVisible) {
			ToggleButton toggle = new ToggleButton();
			toggle.setGraphic(icon);
			Tooltips.install(toggle, ComponentDiagramMessages.get(labelKey));
			toggle.getStyleClass().addAll("sv-icon-button", "sv-link-kind-toggle");
			toggle.setMinWidth(Region.USE_PREF_SIZE);
			toggle.selectedProperty().bindBidirectional(linksVisible);
			icon.opacityProperty().bind(Bindings.when(toggle.selectedProperty()).then(1.0).otherwise(0.35));
			return toggle;
		}

		/** Shows every component and every link hidden one by one from its own context menu. */
		private void showAll() {
			for (CovalentComponentCell component : this.componentItems.values()) {
				component.setVisible(true);
			}
			for (BooleanProperty shown : this.linkShown.values()) {
				shown.set(true);
			}
		}

		/** Brings a component's card into view at its own level, and flashes it. */
		private void navigateTo(final Class<?> componentType) {
			if (componentType != null) {
				ComponentDiagramViewImpl.this.actionManagerServices.executeAction(
						new ComponentDiagramActionKey(this.sessionKey, Role.NavigateToComponent), componentType);
			}
		}

		/**
		 * Selects exactly the two ends of a link, then starts recording a
		 * sequence diagram of them - the same as selecting both cards and
		 * pressing Record.
		 */
		private void recordBetween(final Class<?> sourceType, final Class<?> targetType) {
			ComponentDiagramViewImpl.this.actionManagerServices.executeAction(
					new ComponentDiagramActionKey(this.sessionKey, Role.SelectComponents), Set.of(sourceType, targetType));
			ComponentDiagramViewImpl.this.actionManagerServices
					.executeAction(SequenceRecordingActionKey.StartRecording, Set.of(sourceType, targetType));
		}

		/** Lays this tab's graph out from scratch, discarding manual moves, then fits it to the viewport. */
		private void relayout() {
			this.graph.layout(this.layout);
			this.layoutPortSides();
			this.requestGraphViewportFit();
		}

		/** The port color legend shown over the canvas, one row per port kind and role. */
		private Node buildLegend() {
			VBox legend = new VBox(3);
			legend.getStyleClass().add("sv-diagram-legend");
			legend.getChildren().addAll(
					legendRow(PortPalette.SERVICE_PROVIDED, "legend.serviceProvided"),
					legendRow(PortPalette.SERVICE_USED, "legend.serviceUsed"),
					legendRow(PortPalette.EVENT_PRODUCED, "legend.eventProduced"),
					legendRow(PortPalette.EVENT_CONSUMED, "legend.eventConsumed"),
					legendRow(PortPalette.PARAMETER, "legend.parameterOffered"),
					legendRow(PortPalette.PARAMETER_ACCESSED, "legend.parameterAccessed"));
			legend.setMouseTransparent(true);
			legend.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
			legend.visibleProperty().bind(this.legendVisible);
			return legend;
		}

		private static Node legendRow(final Color color, final String labelKey) {
			Rectangle swatch = new Rectangle(10, 10, color);
			swatch.setArcWidth(4);
			swatch.setArcHeight(4);
			swatch.setStroke(PortPalette.GRAPHITE);
			swatch.setStrokeWidth(0.8);
			Label label = new Label(ComponentDiagramMessages.get(labelKey));
			HBox row = new HBox(7, swatch, label);
			row.setAlignment(Pos.CENTER_LEFT);
			return row;
		}

		/** A toolbox button showing only an icon, its label moved to a tooltip. */
		private Button iconButton(final String iconPath, final String labelKey) {
			Button button = new Button();
			this.decorateIconControl(button, iconPath, labelKey);
			return button;
		}

		/** A toolbox toggle showing only an icon, its label moved to a tooltip. */
		private ToggleButton iconToggle(final String iconPath, final String labelKey) {
			ToggleButton toggle = new ToggleButton();
			this.decorateIconControl(toggle, iconPath, labelKey);
			return toggle;
		}

		/** A toolbox button whose icon is a finely drawn symbol above an eye. */
		private Button symbolIconButton(final String symbolPath, final String eyePath, final String labelKey) {
			Button button = this.iconButton(eyePath, labelKey);
			SVGPath symbol = new SVGPath();
			symbol.setContent(symbolPath);
			symbol.getStyleClass().addAll("sv-tool-icon", "sv-tool-icon-fine");
			button.setGraphic(new Group(symbol, button.getGraphic()));
			return button;
		}

		/** A toolbox toggle whose icon is a symbol drawn above the name glyphs. */
		private ToggleButton namesToggle(final String symbolPath, final String labelKey) {
			ToggleButton toggle = this.iconToggle(symbolPath, labelKey);
			SVGPath glyphs = new SVGPath();
			glyphs.setContent(ICON_NAME_GLYPHS);
			glyphs.getStyleClass().add("sv-tool-icon-glyphs");
			toggle.setGraphic(new Group(toggle.getGraphic(), glyphs));
			return toggle;
		}

		private void decorateIconControl(final ButtonBase control, final String iconPath,
				final String labelKey) {
			SVGPath icon = new SVGPath();
			icon.setContent(iconPath);
			icon.getStyleClass().add("sv-tool-icon");
			control.setGraphic(icon);
			Tooltips.install(control, ComponentDiagramMessages.get(labelKey));
			control.getStyleClass().add("sv-icon-button");
			control.setMinWidth(Region.USE_PREF_SIZE);
		}

		/**
		 * A provided service's ball wired to a used service's socket, drawn as in the diagram and
		 * scaled down.
		 */
		private static Node serviceLinkIcon() {
			double ballEdge = CovalentComponentCell.SERVICE_BALL_RADIUS + PORT_OUTLINE / 2;
			double band = CovalentComponentCell.SOCKET_BAND;
			double border = CovalentComponentCell.SOCKET_BAND_BORDER;
			double radius = CovalentComponentCell.SERVICE_RECEPTACLE_RADIUS;
			// The socket's mouth faces the ball, its round caps a gap away from it.
			double socketX = ballEdge + LINK_ICON_GAP + band / 2 + border;
			Line wire = wire(ballEdge, 0, socketX + radius - band / 2 - border, 0);
			Circle ball = outlined(new Circle(0, 0, CovalentComponentCell.SERVICE_BALL_RADIUS), PortPalette.SERVICE_PROVIDED);
			ball.setStrokeWidth(PORT_OUTLINE);
			Arc socketOutline = socketArc(socketX, radius, band + 2 * border, PortPalette.GRAPHITE);
			Arc socket = socketArc(socketX, radius, band, PortPalette.SERVICE_USED);
			return linkIcon(wire, ball, socketOutline, socket);
		}

		private static Arc socketArc(final double x, final double radius, final double width, final Color color) {
			Arc arc = new Arc(x, 0, radius, radius, -90, 180);
			arc.setType(ArcType.OPEN);
			arc.setFill(null);
			arc.setStroke(color);
			arc.setStrokeWidth(width);
			arc.setStrokeLineCap(StrokeLineCap.ROUND);
			return arc;
		}

		/** A produced event's notched arrow wired to a consumed event's, drawn as in the diagram and scaled down. */
		private static Node eventLinkIcon() {
			double consumedX = CovalentComponentCell.EVENT_TIP + CovalentComponentCell.EVENT_BACK + LINK_ICON_GAP;
			Line wire = wire(CovalentComponentCell.EVENT_TIP, 0, consumedX - CovalentComponentCell.EVENT_NOTCH, 0);
			return linkIcon(wire, eventArrow(0, PortPalette.EVENT_PRODUCED), eventArrow(consumedX, PortPalette.EVENT_CONSUMED));
		}

		private static Polygon eventArrow(final double x, final Color color) {
			double back = CovalentComponentCell.EVENT_BACK;
			double shoulder = CovalentComponentCell.EVENT_SHOULDER;
			double half = CovalentComponentCell.EVENT_HALF_HEIGHT;
			Polygon arrow = outlined(new Polygon(x - back, -half, x + shoulder, -half, x + CovalentComponentCell.EVENT_TIP, 0,
					x + shoulder, half, x - back, half, x - CovalentComponentCell.EVENT_NOTCH, 0), color);
			arrow.setStrokeWidth(PORT_OUTLINE);
			return arrow;
		}

		/**
		 * An offered parameter's bar wired to an accessed parameter's bracket below it, its mouth
		 * toward the bar, drawn as in the diagram and scaled down.
		 */
		private static Node parameterLinkIcon() {
			double barHeight = CovalentComponentCell.PARAMETER_SHAPE_HEIGHT;
			double barWidth = CovalentComponentCell.PARAMETER_SHAPE_WIDTH;
			double band = CovalentComponentCell.BRACKET_BAND;
			double border = CovalentComponentCell.SOCKET_BAND_BORDER;
			double halfWidth = CovalentComponentCell.PARAMETER_BRACKET_WIDTH / 2;
			double top = LINK_ICON_GAP + band / 2 + border;
			double bottom = top + CovalentComponentCell.PARAMETER_BRACKET_HEIGHT;
			Line wire = wire(0, 0, 0, bottom - band / 2);
			Rectangle bar = outlined(new Rectangle(-barWidth / 2, -barHeight, barWidth, barHeight), PortPalette.PARAMETER);
			bar.setArcWidth(barHeight);
			bar.setArcHeight(barHeight);
			bar.setStrokeWidth(PORT_OUTLINE);
			return linkIcon(wire, bar, bracket(halfWidth, top, bottom, band + 2 * border, PortPalette.GRAPHITE),
					bracket(halfWidth, top, bottom, band, PortPalette.PARAMETER_ACCESSED));
		}

		private static Polyline bracket(final double halfWidth, final double top, final double bottom, final double width,
				final Color color) {
			Polyline bracket = new Polyline(-halfWidth, top, -halfWidth, bottom, halfWidth, bottom, halfWidth, top);
			bracket.setFill(null);
			bracket.setStroke(color);
			bracket.setStrokeWidth(width);
			bracket.setStrokeLineCap(StrokeLineCap.ROUND);
			bracket.setStrokeLineJoin(StrokeLineJoin.ROUND);
			return bracket;
		}

		private static Line wire(final double startX, final double startY, final double endX, final double endY) {
			Line wire = new Line(startX, startY, endX, endY);
			wire.setStroke(PortPalette.GRAPHITE);
			wire.setStrokeWidth(PortPalette.WIRE_WIDTH);
			return wire;
		}

		/**
		 * Scales a link drawn at the diagram's own size down to fit {@value #LINK_ICON_SIZE} pixels,
		 * strokes included, centered.
		 */
		private static Node linkIcon(final Node... shapes) {
			Group drawing = new Group(shapes);
			Bounds bounds = drawing.getLayoutBounds();
			double scale = LINK_ICON_SIZE / Math.max(bounds.getWidth(), bounds.getHeight());
			drawing.getTransforms().add(new Scale(scale, scale));
			StackPane icon = new StackPane(new Group(drawing));
			icon.setMinSize(LINK_ICON_SIZE, LINK_ICON_SIZE);
			icon.setPrefSize(LINK_ICON_SIZE, LINK_ICON_SIZE);
			icon.setMaxSize(LINK_ICON_SIZE, LINK_ICON_SIZE);
			return icon;
		}

		private static <S extends Shape> S outlined(final S shape, final Color fill) {
			shape.setFill(fill);
			shape.setStroke(PortPalette.GRAPHITE);
			shape.setStrokeWidth(1);
			return shape;
		}

		// --- Piloting accessors, called from the outer ComponentDiagramViewServices ---

		BorderPane getGraphics() {
			return this.root;
		}

		SupervisorTabDescriptorAdapter buildTabDescriptor() {
			ActionKey recordKey = new ComponentDiagramActionKey(this.sessionKey, Role.RecordToggle);
			ActionKey zoomSetScaleKey = new ComponentDiagramActionKey(this.sessionKey, Role.ZoomSetScale);
			ActionKey zoomCurrentScaleKey = new ComponentDiagramActionKey(this.sessionKey, Role.ZoomCurrentScale);
			ActionKey closeKey = new ComponentDiagramActionKey(this.sessionKey, Role.Close);
			String title = this.currentTitle();
			ActionKey renameKey = new ComponentDiagramActionKey(this.sessionKey, Role.RenameTab);
			SupervisorTabDescriptorAdapter descriptor = new SupervisorTabDescriptorAdapter() {
				@Override
				public RailEntry railEntry() {
					return RailEntry.COMPONENTS;
				}

				@Override
				public String title() {
					return title;
				}

				@Override
				public boolean renamable() {
					return true;
				}

				@Override
				public ActionKey renameActionKey() {
					return renameKey;
				}

				@Override
				public EnumSet<ToolbarFunction> supportedToolbarFunctions() {
					return EnumSet.of(ToolbarFunction.RECORD_TOGGLE, ToolbarFunction.ZOOM_SLIDER);
				}

				@Override
				public ActionKey recordActionKey() {
					return recordKey;
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

				@Override
				public ActionKey recordingStateActionKey() {
					return SequenceRecordingActionKey.RecordingState;
				}
			};
			this.tabDescriptor = descriptor;
			return descriptor;
		}

		SupervisorTabDescriptorAdapter getTabDescriptor() {
			return this.tabDescriptor;
		}

		/**
		 * Called only from {@link ComponentDiagramViewImpl#onTabSelected} -
		 * automatic and unconditional: visibility gates only the port/edge
		 * activity flash ({@link #applyPortActivity}), never topology, tree or
		 * selection state, all of which stay current while hidden.
		 *
		 * @param visible whether this tab is now the selected one
		 */
		void setVisible(final boolean visible) {
			this.visible = visible;
			if (visible) {
				this.syncPortActivity();
			}
		}

		/** Begins the activities current but not shown, and finishes those shown but over. */
		private void syncPortActivity() {
			Set<PortActivityKey> current = ComponentDiagramViewImpl.this.activePorts;
			for (PortActivityKey key : List.copyOf(this.begunPorts)) {
				if (!current.contains(key)) {
					this.applyPortActivity(key.componentType(), key.portType(), key.portKind(), false);
				}
			}
			for (PortActivityKey key : List.copyOf(current)) {
				if (!this.begunPorts.contains(key)) {
					this.applyPortActivity(key.componentType(), key.portType(), key.portKind(), true);
				}
			}
		}

		void requestGraphViewportFit() {
			this.graphViewportFitPending = true;
			this.fitGraphToViewportIfPending();
		}

		void setZoomScale(final double scale) {
			Point2D pivot = this.zoomPivotSceneCoordinates();
			this.graph.getViewportGestures().zoomTo(scale, pivot.getX(), pivot.getY());
		}

		double getZoomScale() {
			return this.graph.getCanvas().getScale();
		}
		/** This tab's title: the one given to it, or the default one. */
		String currentTitle() {
			if (this.title != null) {
				return this.title;
			}
			return this.ordinal <= 1 ? ComponentDiagramMessages.get("tab.title")
					: ComponentDiagramMessages.get("tab.title.numbered", this.ordinal);
		}

		/** The window this tab is shown in, {@code null} if not shown. */
		Window window() {
			return this.root.getScene() == null ? null : this.root.getScene().getWindow();
		}

		/**
		 * The diagram as it is now, cropped to its components on a transparent
		 * background and scaled down to a thumbnail for the views library.
		 */
		Image captureThumbnail() {
			if (this.graph.getCanvas().getWidth() <= 0 || this.graph.getCanvas().getHeight() <= 0) {
				return null;
			}
			this.graph.getEdgeRouting().layoutNow();
			SnapshotParameters parameters = new SnapshotParameters();
			parameters.setFill(Color.TRANSPARENT);
			WritableImage full = this.graph.getCanvas().snapshot(parameters, null);
			Image cropped = Snapshots.cropToContent(full, SNAPSHOT_MARGIN);
			if (cropped == null) {
				return null;
			}
			ImageView scaled = new ImageView(cropped);
			scaled.setPreserveRatio(true);
			scaled.setSmooth(true);
			scaled.setFitWidth(Math.min(400, cropped.getWidth()));
			return scaled.snapshot(parameters, null);
		}

		/** The diagram as it is now, cropped to its components, over the theme's background. */
		Image captureSnapshot() {
			this.graph.getEdgeRouting().layoutNow();
			return Snapshots.captureContent(this.graph.getCanvas(), SNAPSHOT_MARGIN);
		}

		/**
		 * @return every present component type whose cell is not
		 *         visible - see {@code AbstractCell}'s own {@code visible}
		 *         property, the only place this state actually lives
		 */
		Set<Class<?>> getHiddenComponents() {
			Set<Class<?>> hidden = new LinkedHashSet<>();
			for (Entry<Class<?>, CovalentComponentCell> entry : this.componentItems.entrySet()) {
				if (!entry.getValue().visibleProperty().get()) {
					hidden.add(entry.getKey());
				}
			}
			return hidden;
		}

		/**
		 * @return every present component type's own graphic's
		 *         {@code (layoutX, layoutY)} - plain JavaFX node position,
		 *         whether it got there by manual dragging or the last
		 *         auto-layout pass
		 */
		Map<Class<?>, double[]> getPositions() {
			Map<Class<?>, double[]> positions = new LinkedHashMap<>();
			for (Entry<Class<?>, CovalentComponentCell> entry : this.componentItems.entrySet()) {
				Region graphic = SessionPanel.this.graph.getGraphic(entry.getValue());
				positions.put(entry.getKey(), new double[] { graphic.getLayoutX(), graphic.getLayoutY() });
			}
			return positions;
		}

		/**
		 * Hides every component named in {@code hiddenComponents} that is
		 * still present at this level, shows every other present
		 * one - a component in {@code hiddenComponents} no longer present
		 * here (e.g. a different focus level) is simply not among {@code
		 * componentItems}, so there is nothing to apply it to.
		 */
		void applyHiddenComponents(final Set<Class<?>> hiddenComponents) {
			for (Entry<Class<?>, CovalentComponentCell> entry : this.componentItems.entrySet()) {
				entry.getValue().setVisible(!hiddenComponents.contains(entry.getKey()));
			}
		}

		/**
		 * Relocates every present component named in {@code
		 * positions} to its saved {@code (layoutX, layoutY)} via plain {@code
		 * Node.relocate(...)} - the same mechanism manual dragging uses. A
		 * component present but absent from {@code positions} is left wherever
		 * the current auto/manual layout put it.
		 */
		void applyPositions(final Map<Class<?>, double[]> positions) {
			for (Entry<Class<?>, CovalentComponentCell> entry : this.componentItems.entrySet()) {
				double[] xy = positions.get(entry.getKey());
				if (xy != null) {
					SessionPanel.this.graph.getGraphic(entry.getValue()).relocate(xy[0], xy[1]);
				}
			}
		}

		/**
		 * Shows a short, modal error dialog owned by this tab's own window -
		 * how {@code ComponentDiagramInteractionImpl} reports the
		 * {@code IOException} of a views library operation, without this View
		 * exposing anything beyond a plain message string.
		 */
		void showError(final String message) {
			Alert alert = new Alert(AlertType.ERROR, message);
			alert.setTitle(ComponentDiagramMessages.get("alert.error.title"));
			alert.setHeaderText(null);
			alert.initOwner(this.root.getScene() == null ? null : this.root.getScene().getWindow());
			alert.showAndWait();
		}

		/**
		 * Repaints every visible cell and edge, in this tab alone,
		 * from {@code PortPalette}'s current values: {@code
		 * CovalentComponentCell.refreshPalette()} redraws a cell's chrome,
		 * ports/stems/shapes/labels and selection outline, and {@code
		 * AnchoredPortEdge.refreshColor()} re-binds that edge's line stroke
		 * the same way.
		 */
		void repaintPalette() {
			// Status colors come from the palette too, so they are re-read first.
			this.applyComponentStatuses(ComponentDiagramViewImpl.this.componentStatuses);
			for (CovalentComponentCell cell : this.componentItems.values()) {
				cell.refreshPalette();
			}
			for (AnchoredPortEdge edge : this.portEdges) {
				edge.refreshColor();
			}
			if (this.graphSelectionRectangle != null) {
				this.applySelectionRectangleColor();
			}
		}

		void rebuildComponentTreeWithCurrentFilter() {
			if (this.lastTopology != null) {
				this.rebuildComponentTree(this.lastTopology, this.treeSearchField.getText());
			}
		}

		private void applySelectionRectangleColor() {
			Color selection = PortPalette.SELECTION;
			this.graphSelectionRectangle.setFill(Color.color(selection.getRed(), selection.getGreen(), selection.getBlue(), 0.10));
			this.graphSelectionRectangle.setStroke(selection);
		}

		/**
		 * Schedules a single relayout on the next JavaFX pulse, if the
		 * tool-wide auto-layout-on-hide preference is on and one is not
		 * already pending for this tab - see {@link #relayoutScheduled}'s
		 * javadoc.
		 */
		private void requestRelayoutIfEnabled() {
			if (!ComponentDiagramViewImpl.this.autoLayoutOnHide || this.relayoutScheduled || this.restoringSavedLayout) {
				return;
			}
			this.relayoutScheduled = true;
			Platform.runLater(() -> {
				this.relayoutScheduled = false;
				this.graph.layout(this.layout);
				this.layoutPortSides();
			});
		}

		private DoubleProperty zoomScaleProperty() {
			return this.graph.getCanvas().scaleProperty();
		}

		private Point2D zoomPivotSceneCoordinates() {
			Class<?> focused = this.selectedComponentTypes.isEmpty() ? null : this.selectedComponentTypes.iterator().next();
			CovalentComponentCell selectedCell = focused == null ? null : this.componentItems.get(focused);
			if (selectedCell != null) {
				return Graph.sceneCenterOf(this.graph.getGraphic(selectedCell));
			}
			return Graph.sceneCenterOf(this.componentsViewContainer);
		}

		// --- Reactions to ComponentDiagramEvents, dispatched by the outer class ---

		void setSelectedComponents(final Set<Class<?>> selectedComponentTypes) {
			this.selectedComponentTypes.clear();
			this.selectedComponentTypes.addAll(selectedComponentTypes);
			this.refreshSelectionPresentation();
			this.inspector.setSelection(selectedComponentTypes);
		}

		/**
		 * Shows the status badges on this tab's cards and in its inspector: each
		 * card gets its status's upper-cased label and its palette color.
		 *
		 * @param statuses every component's status, by component type
		 */
		void applyComponentStatuses(final Map<Class<?>, ComponentStatus> statuses) {
			this.inspector.setStatuses(statuses);
			for (Entry<Class<?>, CovalentComponentCell> entry : this.componentItems.entrySet()) {
				ComponentStatus status = statuses.get(entry.getKey());
				if (status != null) {
					entry.getValue().setStatus(
							ComponentDiagramMessages.status(status).toUpperCase(SupervisorMessages.locale()),
							readPortPalette(status.getColorKey()));
				}
			}
		}

		/**
		 * Applies a live port/edge activity flash to this tab's cells -
		 * skipped while this tab is not the visible one (see {@link #visible}):
		 * a flash is a transient live indicator, not a capture/history
		 * mechanism. The activity that is on when the tab shows again, or when
		 * its cells are rebuilt, is picked up from the View's set of active
		 * ports by {@link #syncPortActivity()}. Topology, tree and selection
		 * state are untouched by this gate.
		 *
		 * @param componentType the component the port belongs to
		 * @param portType      the services/parameters/events interface of the port
		 * @param portKind      the port's kind
		 * @param active        whether a call is in flight through the port
		 */
		void applyPortActivity(final Class<?> componentType, final Class<?> portType, final ComponentPortKind portKind,
				final boolean active) {
			if (!this.visible) {
				return;
			}
			PortActivityKey key = new PortActivityKey(componentType, portType, portKind);
			if (active ? !this.begunPorts.add(key) : !this.begunPorts.remove(key)) {
				return;
			}
			// One port per real interface (see getOrCreatePort's own label convention): only
			// the port for this specific interface reacts, so a busy interface never lights up
			// a sibling, idle one that merely shares the same component and port kind.
			String label = portType.getSimpleName();
			List<ComponentPort> ports = new ArrayList<>();
			ports.addAll(this.findPorts(componentType, portKind, label, ComponentPortRole.USED));
			ports.addAll(this.findPorts(componentType, portKind, label, ComponentPortRole.PROVIDED));
			ports.addAll(this.findPorts(componentType, portKind, label, ComponentPortRole.PRODUCED));
			ports.addAll(this.findPorts(componentType, portKind, label, ComponentPortRole.CONSUMED));
			ports.addAll(this.findPorts(componentType, portKind, label, ComponentPortRole.ACCESSED));
			ports.addAll(this.findPorts(componentType, portKind, label, ComponentPortRole.OFFERED));
			ComponentPortActivity activity = activityFor(portKind);
			for (ComponentPort port : ports) {
				if (active) {
					port.beginActivity(activity);
				} else {
					port.finishActivity(activity);
				}
			}
		}

		/**
		 * The one port this tab's cell for {@code componentType} has for a given kind+role+
		 * interface label - {@code getOrCreatePort} dedups on exactly that triple, one port
		 * per real interface, so this never flashes a sibling interface's port for traffic
		 * that is not its own. Returns an empty list, safely, when this tab does not
		 * show {@code componentType} at all, or shows it without that particular port.
		 */
		private List<ComponentPort> findPorts(final Class<?> componentType, final ComponentPortKind kind,
				final String label, final ComponentPortRole role) {
			CovalentComponentCell componentItem = this.componentItems.get(componentType);
			if (componentItem == null) {
				return List.of();
			}
			ComponentPort port = componentItem.getPort(toDiagramKind(kind), role, label);
			return port == null ? List.of() : List.of(port);
		}

		void setFocusPath(final List<Class<?>> focusPath) {
			this.focusPath = focusPath;
			this.renderBreadcrumb();
			this.applyTreeExpansion(this.treeSearchField == null ? null : this.treeSearchField.getText());
		}

		/**
		 * Opens the tree along the focus path only: the focused composite shows its direct
		 * children, its own composites stay closed. A search opens every match instead.
		 */
		private void applyTreeExpansion(final String filter) {
			boolean searching = filter != null && !filter.isBlank();
			// Folding a row moves the TreeView's selection to its parent: not a selection by the user.
			boolean notify = this.notifyTreeSelection;
			this.notifyTreeSelection = false;
			try {
				this.rootNode.setExpanded(true);
				for (Map.Entry<Class<?>, TreeItem<ComponentTreeItem>> node : this.treeNodesByType.entrySet()) {
					node.getValue().setExpanded(searching || this.focusPath.contains(node.getKey()));
				}
			} finally {
				this.notifyTreeSelection = notify;
			}
		}

		/**
		 * Rebuilds the breadcrumb from {@link #focusPath}: a "Top" link back to
		 * the top-level forest, then one entry per focus level - every entry
		 * clickable via {@code ExitToDepth} except the last (the current
		 * level, shown in bold, not a link). Empty at the top-level forest
		 * itself.
		 */
		private void renderBreadcrumb() {
			this.breadcrumbBar.getChildren().clear();
			if (this.focusPath.isEmpty()) {
				return;
			}
			Button up = this.iconButton(ICON_UP, "breadcrumb.up");
			int parentDepth = this.focusPath.size() - 1;
			up.setOnAction(e -> ComponentDiagramViewImpl.this.actionManagerServices
					.executeAction(new ComponentDiagramActionKey(this.sessionKey, Role.ExitToDepth), parentDepth));
			this.breadcrumbBar.getChildren().add(up);
			Hyperlink top = new Hyperlink(this.rootLabel());
			top.setOnAction(e -> ComponentDiagramViewImpl.this.actionManagerServices
					.executeAction(new ComponentDiagramActionKey(this.sessionKey, Role.ExitToDepth), 0));
			this.breadcrumbBar.getChildren().add(top);
			for (int i = 0; i < this.focusPath.size(); i++) {
				this.breadcrumbBar.getChildren().add(new Label("›"));
				Class<?> level = this.focusPath.get(i);
				if (i == this.focusPath.size() - 1) {
					Label current = new Label(level.getSimpleName());
					current.getStyleClass().add("sv-breadcrumb-current");
					this.breadcrumbBar.getChildren().add(current);
				} else {
					Hyperlink link = new Hyperlink(level.getSimpleName());
					int depth = i + 1;
					link.setOnAction(e -> ComponentDiagramViewImpl.this.actionManagerServices
							.executeAction(new ComponentDiagramActionKey(this.sessionKey, Role.ExitToDepth), depth));
					this.breadcrumbBar.getChildren().add(link);
				}
			}
		}

		void applyDependenciesVisibility(final Class<?> componentType, final boolean visible) {
			CovalentComponentCell component = this.componentItems.get(componentType);
			if (component == null) {
				return;
			}
			for (CovalentComponentCell dependency : this.comToDepsCom.getOrDefault(component, Set.of())) {
				dependency.setVisible(visible);
			}
			if (visible) {
				component.setVisible(true);
			}
		}

		/**
		 * The landing flash after a cross-composite port-navigation jump -
		 * {@code ComponentDiagramControllerImpl.navigateToComponent(...)} changes
		 * this tab's focus path (handled like any other, via {@link #setFocusPath}
		 * and {@link #rebuildGraph}) then fires this once the target is an
		 * ordinary visible card again, so by the time this runs {@link
		 * #componentItems} has it. Also selects the landed-on component,
		 * through the same {@code SelectComponents} action an ordinary click
		 * dispatches (see {@link #handleGraphComponentClick}) rather than calling
		 * {@code CovalentComponentCell.setSelected(...)} directly, so the
		 * Controller's own idea of what's selected stays the single source of
		 * truth and the selection survives the flash fading rather than leaving
		 * the card unselected once it does.
		 */
		void flashComponent(final Class<?> componentType) {
			CovalentComponentCell component = this.componentItems.get(componentType);
			if (component != null) {
				component.flash();
				ComponentDiagramViewImpl.this.actionManagerServices.executeAction(
						new ComponentDiagramActionKey(this.sessionKey, Role.SelectComponents), Set.of(componentType));
			}
		}

		/**
		 * Selects two components and highlights every link between them, in
		 * either direction - see {@code ComponentDiagramControllerServices.showLink(...)}.
		 *
		 * @param retry whether to try once more shortly when the components are
		 *        not on screen yet - the level they are on may still be rendering
		 */
		void highlightLink(final Class<?> sourceType, final Class<?> targetType, final boolean retry) {
			CovalentComponentCell source = this.componentItems.get(sourceType);
			CovalentComponentCell target = this.componentItems.get(targetType);
			if (source == null || target == null) {
				if (retry) {
					PauseTransition later = new PauseTransition(Duration.millis(300));
					later.setOnFinished(event -> this.highlightLink(sourceType, targetType, false));
					later.play();
				}
				return;
			}
			ComponentDiagramViewImpl.this.actionManagerServices.executeAction(
					new ComponentDiagramActionKey(this.sessionKey, Role.SelectComponents), Set.of(sourceType, targetType));
			for (AnchoredPortEdge edge : this.portEdges) {
				boolean forward = edge.getSourceComponent() == source && edge.getTargetComponent() == target;
				boolean backward = edge.getSourceComponent() == target && edge.getTargetComponent() == source;
				if (forward || backward) {
					edge.highlight();
				}
			}
		}

		// --- Graph rebuild from the Model's topology snapshot ---

		void rebuildGraph(final ComponentDiagramTopology topology) {
			if (this.level != null && !this.componentItems.isEmpty()) {
				this.levelLayouts.put(this.level, new LevelLayout(this.getPositions(), this.graph.getCanvas().getScale(),
						this.graph.getCanvas().getTranslateX(), this.graph.getCanvas().getTranslateY()));
			}
			Object previousLevel = this.level;
			this.level = levelOf(topology);
			this.compositeTypes = topology.getCompositeTypes();
			this.lastTopology = topology;
			this.inspector.setTopology(topology);
			this.componentItems.clear();
			this.begunPorts.clear();
			this.componentTypesByCell.clear();
			this.comToDepsCom.clear();
			this.wiringCount.clear();
			this.portEdges.clear();
			this.linkShown.clear();

			// A level change (enterComposite/exitToDepth) replaces the whole
			// topology. ElkLayout reads Model#getAllCells()/getAllEdges() directly,
			// so the previous level's cells/edges must leave the model, not just the
			// canvas, or they would keep feeding every layout pass and distort
			// ranking. Clearing them here, before adding the new topology's, keeps
			// the model exactly matching what is visible - matches
			// EndUpdate's add/remove pipeline, so the component tree and
			// root-parent bookkeeping stay consistent too.
			for (final Edge edge : List.copyOf(this.model.getAllEdges())) {
				this.model.removeEdge(edge);
			}
			for (final Cell cell : List.copyOf(this.model.getAllCells())) {
				this.model.removeCell(cell);
			}

			this.graph.beginUpdate();

			for (Class<?> componentType : Ordering.byName(topology.getComponentTypes())) {
				this.getOrCreateComponentItem(componentType);
			}
			for (ComponentDiagramEdge edge : edgesInOrder(topology.getEdges())) {
				this.addTopologyEdge(edge);
			}
			for (ComponentDiagramExternalPort externalPort : topology.getExternalPorts()) {
				this.addExternalPort(externalPort);
			}

			this.graph.endUpdate();
			this.applyComponentStatuses(ComponentDiagramViewImpl.this.componentStatuses);
			this.graph.layout(this.layout);
			this.layoutPortSides();
			if (this.visible) {
				this.syncPortActivity();
			}
			this.rebuildComponentTree(topology, this.treeSearchField == null ? null : this.treeSearchField.getText());
			this.refreshSelectionPresentation();
			ComponentDiagramLayout initialLayout = this.initialLayout;
			if (initialLayout != null && !topology.getComponentTypes().isEmpty()) {
				// A tab opened from a saved view: its layout is applied once it shows its components.
				this.initialLayout = null;
				this.restoringSavedLayout = true;
				try {
					this.applyHiddenComponents(initialLayout.hiddenComponents());
					this.applyPositions(initialLayout.positions());
					this.setZoomScale(initialLayout.zoom());
				} finally {
					this.restoringSavedLayout = false;
				}
				return;
			}
			LevelLayout left = this.levelLayouts.get(this.level);
			if (left != null) {
				// A level visited, or the one shown rebuilt: its components where they were left, any
				// component that was not there placed by the layout. Back to it, the view is as it was
				// left; rebuilt in place, it stays as it is.
				this.graphViewportFitPending = false;
				this.applyPositions(left.positions());
				if (!this.level.equals(previousLevel)) {
					this.graph.getCanvas().setScale(left.scale());
					this.graph.getCanvas().setTranslateX(left.translateX());
					this.graph.getCanvas().setTranslateY(left.translateY());
				}
				return;
			}
			this.requestGraphViewportFit();
		}

		/**
		 * @return the level a topology shows: the composite holding its components, {@link
		 *         #TOP_LEVEL} for the top level, which no composite holds
		 */
		private static Object levelOf(final ComponentDiagramTopology topology) {
			var componentTypes = topology.getComponentTypes();
			if (componentTypes.isEmpty()) {
				return TOP_LEVEL;
			}
			Class<?> parent = topology.getCompositionParentOf().get(componentTypes.iterator().next());
			return parent == null ? TOP_LEVEL : parent;
		}

		private CovalentComponentCell getOrCreateComponentItem(final Class<?> componentType) {
			CovalentComponentCell componentItem = this.componentItems.get(componentType);
			if (componentItem != null) {
				return componentItem;
			}
			componentItem = new CovalentComponentCell(componentType.getSimpleName(), componentType.getName());
			componentItem.setIsComponent(true);
			componentItem.setIsComposite(this.compositeTypes.contains(componentType));
			componentItem.setIsRaw(this.lastTopology != null && this.lastTopology.getRawTypes().contains(componentType));
			String stereotype = this.lastTopology == null ? null : this.lastTopology.getStereotype(componentType);
			componentItem.setStereotype(stereotype);
			Class<?> implementation = this.lastTopology == null ? null : this.lastTopology.getImplementation(componentType);
			componentItem.setImplementationTip(implementation == null ? null
					: ComponentDiagramMessages.get("card.implementation", implementation.getName()));
			this.model.addCell(componentItem);
			this.componentItems.put(componentType, componentItem);
			this.componentTypesByCell.put(componentItem, componentType);
			final CovalentComponentCell wiredComponentItem = componentItem;
			wiredComponentItem.onClick(event -> this.handleGraphComponentClick(wiredComponentItem, event));
			wiredComponentItem.onPortClick(this::highlightPortGroup);
			wiredComponentItem.onPortNavigate(this::handlePortNavigate);
			wiredComponentItem.onContextMenuRequested(event -> this.handleGraphComponentContextMenu(wiredComponentItem, event));
			wiredComponentItem.onPortContextMenuRequested(this::handlePortContextMenu);
			// Single hook point for every way a component's visibility can change - Hide
			// All/Show All/Hide/Show deps all set it directly - see
			// requestRelayoutIfEnabled()'s own javadoc.
			componentItem.visibleProperty().addListener((s, o, n) -> this.requestRelayoutIfEnabled());
			return componentItem;
		}

		/** The edges in a fixed order, which the sets of a topology do not have: by ends, kind, then relation. */
		private static List<ComponentDiagramEdge> edgesInOrder(final Set<ComponentDiagramEdge> edges) {
			List<ComponentDiagramEdge> sorted = new ArrayList<>(edges);
			sorted.sort(Comparator.comparing((ComponentDiagramEdge edge) -> edge.getFrom().getName())
					.thenComparing(edge -> edge.getTo().getName()).thenComparing(edge -> edge.getKind().name())
					.thenComparing(edge -> edge.getRelation() == null ? "" : edge.getRelation().getName()));
			return sorted;
		}

		private void addTopologyEdge(final ComponentDiagramEdge edge) {
			CovalentComponentCell fromItem = this.componentItems.get(edge.getFrom());
			CovalentComponentCell toItem = this.componentItems.get(edge.getTo());
			if (fromItem == null || toItem == null) {
				return;
			}
			if (edge.getKind() == ComponentPortKind.COMPONENT_TYPE) {
				CompositionEdge compositionEdge = new CompositionEdge(fromItem, toItem, "");
				this.model.addEdge(compositionEdge);
				compositionEdge.visibleProperty()
						.bind(Bindings.and(fromItem.visibleProperty(), toItem.visibleProperty()));
				this.registerDependency(fromItem, toItem);
				this.registerDependency(toItem, fromItem);
				return;
			}

			ComponentPortRole fromRole;
			ComponentPortRole toRole;
			ComponentPortSide fromSide;
			ComponentPortSide toSide;
			BooleanProperty linksVisible;
			switch (edge.getKind()) {
			case SERVICE:
				fromRole = ComponentPortRole.USED;
				toRole = ComponentPortRole.PROVIDED;
				fromSide = ComponentPortSide.RIGHT;
				toSide = ComponentPortSide.LEFT;
				linksVisible = this.graph.showServiceLinksProperty();
				break;
			case EVENT:
				fromRole = ComponentPortRole.PRODUCED;
				toRole = ComponentPortRole.CONSUMED;
				fromSide = ComponentPortSide.LEFT;
				toSide = ComponentPortSide.RIGHT;
				linksVisible = this.graph.showEventLinksProperty();
				break;
			case PARAMETER:
				fromRole = ComponentPortRole.ACCESSED;
				toRole = ComponentPortRole.OFFERED;
				fromSide = ComponentPortSide.BOTTOM;
				toSide = ComponentPortSide.BOTTOM;
				linksVisible = this.graph.showParameterLinksProperty();
				break;
			default:
				return;
			}

			// One port per real interface - the label is what getOrCreatePort dedups on, so
			// two distinct interfaces of the same kind+role get two distinct ports instead of
			// collapsing onto one shared, generically-labeled port.
			String portLabel = edge.getRelation() != null ? edge.getRelation().getSimpleName() : edge.getKind().name();
			ComponentPort fromPort = fromItem.getOrCreatePort(toDiagramKind(edge.getKind()), fromRole, portLabel, fromSide);
			ComponentPort toPort = toItem.getOrCreatePort(toDiagramKind(edge.getKind()), toRole, portLabel, toSide);
			AnchoredPortEdge anchoredEdge = new AnchoredPortEdge(fromItem, fromPort, toItem, toPort, true);
			anchoredEdge.textProperty()
					.set(edge.getRelation() != null ? edge.getRelation().getSimpleName() : edge.getKind().name());
			anchoredEdge.onContextMenuRequested(event -> this.handleEdgeContextMenu(anchoredEdge, event));
			this.model.addEdge(anchoredEdge);
			this.portEdges.add(anchoredEdge);
			// Port visibility (composite enter/exit) AND the per-kind link toggle
			// (which hides the wire only, never the ports themselves) - both must
			// hold for this wire to actually show.
			BooleanProperty shown = new SimpleBooleanProperty(true);
			this.linkShown.put(anchoredEdge, shown);
			anchoredEdge.visibleProperty().bind(Bindings.and(
					Bindings.and(toItem.visibleProperty(), fromItem.visibleProperty()),
					linksVisible).and(shown));
			// So each end's socket shows a continuous line into its own open half
			// only while this exact wire is the reason it is there, and goes back
			// to looking free the moment the wire itself is hidden (any of the
			// three conditions just bound above) - see ComponentPort#connectedProperty.
			// A port a second, deduped edge for the same label also wants to bind
			// (shouldn't normally happen - getOrCreatePort already collapses same-
			// label connections) finds it already bound and is left alone.
			if (!fromPort.connectedProperty().isBound()) {
				fromPort.connectedProperty().bind(anchoredEdge.visibleProperty());
			}
			if (!toPort.connectedProperty().isBound()) {
				toPort.connectedProperty().bind(anchoredEdge.visibleProperty());
			}
			this.registerDependency(fromItem, toItem);
			this.registerDependency(toItem, fromItem);

			Pair key = new Pair(fromItem, toItem);
			int count = this.wiringCount.getOrDefault(key, 0);
			if (count > 0) {
				anchoredEdge.setOffset(count);
			}
			this.wiringCount.put(key, count + 1);
		}

		private void registerDependency(final CovalentComponentCell component, final CovalentComponentCell dependency) {
			this.comToDepsCom.computeIfAbsent(component, k -> new HashSet<>()).add(dependency);
		}

		/**
		 * Renders one {@code ComponentDiagramExternalPort} - the roll-up of every
		 * edge {@code focusTopology()} found crossing out of this tab's current
		 * focus level from this owner - as an ordinary port on the owner's own
		 * cell, marked navigable so {@link #handlePortNavigate} can jump to
		 * whichever real target(s) it was rolled up from. Silently skipped if
		 * the owner itself isn't part of this level (shouldn't happen: the
		 * Controller only ever reports an external port for a
		 * visible owner).
		 */
		private void addExternalPort(final ComponentDiagramExternalPort externalPort) {
			CovalentComponentCell ownerItem = this.componentItems.get(externalPort.getOwner());
			if (ownerItem == null) {
				return;
			}
			org.opales.covalent.supervision.componentview.internal.diagram.cells.ComponentPortKind diagramKind = toDiagramKind(
					externalPort.getKind());
			ComponentPortRole fxRole = this.toFxRole(externalPort.getRole());
			ComponentPortSide side = this.sideForFxRole(fxRole);
			Set<Class<?>> targets = externalPort.getTargets();
			String label = targets.size() == 1 ? targets.iterator().next().getSimpleName()
					: ComponentDiagramMessages.get("port.external." + externalPort.getKind().name(), targets.size());

			ComponentPort port = ownerItem.getOrCreatePort(diagramKind, fxRole, label, side);
			port.setNavigationTargets(targets);
		}

		/**
		 * Translates the model's own JavaFX-free {@code
		 * componentdiagram.model.ComponentPortRole} into the diagram
		 * rendering package's enum of the same shape - the role-side
		 * counterpart of {@link ComponentDiagramViewImpl#toDiagramKind}.
		 * {@code COMPONENT_TYPE} has no mirror on the model side (composition
		 * never crosses a focus boundary), so every value here maps cleanly.
		 */
		private ComponentPortRole toFxRole(
				final org.opales.covalent.supervision.componentdiagram.model.ComponentPortRole role) {
			switch (role) {
			case PROVIDED:
				return ComponentPortRole.PROVIDED;
			case USED:
				return ComponentPortRole.USED;
			case PRODUCED:
				return ComponentPortRole.PRODUCED;
			case CONSUMED:
				return ComponentPortRole.CONSUMED;
			case OFFERED:
				return ComponentPortRole.OFFERED;
			default:
				return ComponentPortRole.ACCESSED;
			}
		}

		/**
		 * Which card edge a role mounts on - the fromSide/toSide half of
		 * {@link #addTopologyEdge}'s own switch, kept as a method of its own so an
		 * external port (which only ever has one side, never a from/to pair) can
		 * use the same kind/role-to-side mapping.
		 */
		private ComponentPortSide sideForFxRole(final ComponentPortRole fxRole) {
			switch (fxRole) {
			case USED:
			case CONSUMED:
				return ComponentPortSide.RIGHT;
			case PRODUCED:
			case PROVIDED:
				return ComponentPortSide.LEFT;
			default:
				return ComponentPortSide.BOTTOM;
			}
		}

		private void layoutPortSides() {
			this.portSideLayout.execute(this.graph, this.portEdges);
		}

		/**
		 * Rebuilds this tab's component tree panel from the full composition
		 * hierarchy (no packages, only real composition - composite to child)
		 * rather than from the current drill-down focus level, so every
		 * component is always reachable from the tree regardless of where
		 * this tab's canvas is drilled into. {@code filter} narrows
		 * the tree to rows whose simple name contains it (case-insensitive),
		 * keeping the ancestors of any match so the result stays a tree
		 * rather than a flat list; {@code null} or blank shows everything.
		 */
		private void rebuildComponentTree(final ComponentDiagramTopology topology, final String filter) {
			// Rebuilding moves the TreeView's selection around: that is not a selection by the user.
			this.notifyTreeSelection = false;
			try {
				this.fillComponentTree(topology, filter);
			} finally {
				this.notifyTreeSelection = true;
			}
		}

		private void fillComponentTree(final ComponentDiagramTopology topology, final String filter) {
			this.treeNodesByType.clear();
			this.rootNode.getChildren().clear();
			this.containerName = topology.getContainerName();
			this.rootNode.setValue(new ComponentTreeItem(null, this.rootLabel()));
			if (this.breadcrumbBar != null) {
				this.renderBreadcrumb();
			}

			Map<Class<?>, Class<?>> parentOf = topology.getCompositionParentOf();
			this.allCompositeTypes = new HashSet<>(parentOf.values());

			Map<Class<?>, List<Class<?>>> childrenOf = new LinkedHashMap<>();
			for (Class<?> type : topology.getAllComponentTypes()) {
				childrenOf.computeIfAbsent(parentOf.get(type), key -> new ArrayList<>()).add(type);
			}

			String needle = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
			for (Class<?> root : childrenOf.getOrDefault(null, List.of())) {
				TreeItem<ComponentTreeItem> rootTreeItem = this.buildFilteredTreeNode(root, childrenOf, needle);
				if (rootTreeItem != null) {
					this.rootNode.getChildren().add(rootTreeItem);
				}
			}
			this.applyTreeExpansion(filter);
			if (this.componentsList != null) {
				this.pushStateIntoTree();
			}
		}

		/**
		 * @return a tree node for {@code type} (with every matching descendant
		 *         attached), or {@code null} if neither {@code type}
		 *         itself nor any of its descendants matches {@code needle} - an
		 *         empty {@code needle} always matches, i.e. no filtering at all.
		 */
		private TreeItem<ComponentTreeItem> buildFilteredTreeNode(final Class<?> type,
				final Map<Class<?>, List<Class<?>>> childrenOf, final String needle) {
			List<TreeItem<ComponentTreeItem>> matchingChildren = new ArrayList<>();
			for (Class<?> child : childrenOf.getOrDefault(type, List.of())) {
				TreeItem<ComponentTreeItem> childTreeItem = this.buildFilteredTreeNode(child, childrenOf, needle);
				if (childTreeItem != null) {
					matchingChildren.add(childTreeItem);
				}
			}
			boolean selfMatches = needle.isEmpty() || type.getSimpleName().toLowerCase(Locale.ROOT).contains(needle);
			if (!selfMatches && matchingChildren.isEmpty()) {
				return null;
			}
			TreeItem<ComponentTreeItem> treeItem = new TreeItem<>(new ComponentTreeItem(type, type.getSimpleName()));
			treeItem.getChildren().addAll(matchingChildren);
			this.treeNodesByType.put(type, treeItem);
			return treeItem;
		}

		/**
		 * The name of the tree's virtual root and of the breadcrumb's first entry: the observed
		 * container's name, or "Top" when none is known.
		 */
		private String rootLabel() {
			return this.containerName.isEmpty() ? ComponentDiagramMessages.get("breadcrumb.top") : this.containerName;
		}

		/**
		 * One row of this tab's component tree: an icon (composite vs leaf, matching the canvas)
		 * and the component's simple name; the virtual root shows the container's name. A plain
		 * click on a row navigates there, like in a file explorer: the root shows the top level, a
		 * composite shows its content, a leaf shows the level it lives in. The row is selected so
		 * that the inspector describes it. The press is intercepted before the TreeView's own
		 * click-to-select handling, so the tree selection only ever follows the view's state; a
		 * click with a modifier key keeps the TreeView's own multi-selection.
		 */
		private TreeCell<ComponentTreeItem> buildComponentTreeCell() {
			TreeCell<ComponentTreeItem> cell = new TreeCell<>() {
				@Override
				protected void updateItem(final ComponentTreeItem item, final boolean empty) {
					super.updateItem(item, empty);
					if (empty || item == null) {
						this.setText(null);
						this.setGraphic(null);
						return;
					}
					this.setText(item.toString());
					this.setGraphic(SessionPanel.this.treeNodeIcon(item.isRoot()
							|| SessionPanel.this.allCompositeTypes.contains(item.getComponentType())));
				}
			};
			cell.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
				ComponentTreeItem item = cell.getItem();
				// The triangle opens or closes the composite in the tree, without navigating.
				if (item == null || isWithin(event.getPickResult().getIntersectedNode(), cell.getDisclosureNode())) {
					return;
				}
				boolean modifier = event.isControlDown() || event.isShiftDown() || event.isShortcutDown();
				if (event.getButton() != MouseButton.PRIMARY || modifier) {
					return;
				}
				event.consume();
				this.navigateFromTree(item.getComponentType());
			});
			cell.setOnContextMenuRequested(event -> {
				ComponentTreeItem item = cell.getItem();
				if (item == null || item.isRoot()) {
					return;
				}
				this.showContextMenu(this.buildTreeNodeContextMenu(item.getComponentType()), cell, event.getScreenX(), event.getScreenY());
			});
			return cell;
		}

		/**
		 * Shows what a tree row stands for and selects it: {@code null} is the virtual root, which
		 * shows the top level; a composite shows its own content; a leaf shows its level and
		 * flashes. Each is one action, so the diagram is rebuilt once.
		 */
		private void navigateFromTree(final Class<?> componentType) {
			if (componentType == null) {
				ComponentDiagramViewImpl.this.actionManagerServices
						.executeAction(new ComponentDiagramActionKey(this.sessionKey, Role.ExitToDepth), 0);
			} else if (this.allCompositeTypes.contains(componentType)) {
				ComponentDiagramViewImpl.this.actionManagerServices.executeAction(
						new ComponentDiagramActionKey(this.sessionKey, Role.ShowComposite), componentType);
			} else {
				ComponentDiagramViewImpl.this.actionManagerServices.executeAction(
						new ComponentDiagramActionKey(this.sessionKey, Role.NavigateToComponent), componentType);
				ComponentDiagramViewImpl.this.actionManagerServices.executeAction(
						new ComponentDiagramActionKey(this.sessionKey, Role.SelectComponents), Set.of(componentType));
			}
		}

		/** @return whether the node is the container or lies inside it */
		private static boolean isWithin(final Node node, final Node container) {
			for (Node current = node; current != null && container != null; current = current.getParent()) {
				if (current == container) {
					return true;
				}
			}
			return false;
		}

		/** Small composite-vs-leaf glyph for a tree row, matching the canvas's own distinction. */
		private Node treeNodeIcon(final boolean composite) {
			if (composite) {
				Rectangle square = new Rectangle(9, 9);
				square.setArcWidth(3);
				square.setArcHeight(3);
				square.setFill(PortPalette.GRAPHITE);
				return square;
			}
			return new Circle(4.5, PortPalette.GRAPHITE);
		}

		// --- Context menus: a complement to the toolbox panel, not a
		// replacement - every action here already exists as a button or as the
		// tree's own click handling, just reachable at the point of interaction.

		/**
		 * @return the component types a card's or tree node's context-menu action
		 *         should apply to: the whole current selection if {@code clicked}
		 *         is part of it (right-clicking within a multi-selection acts on
		 *         all of it, the usual convention), otherwise just {@code clicked}
		 *         alone.
		 */
		private Set<Class<?>> contextMenuTargets(final Class<?> clicked) {
			if (this.selectedComponentTypes.contains(clicked)) {
				return new LinkedHashSet<>(this.selectedComponentTypes);
			}
			return Set.of(clicked);
		}

		/**
		 * Shows {@code menu} as this tab's only context menu: whichever one is
		 * still open is hidden first, so two menus are never shown at once.
		 */
		private void showContextMenu(final ContextMenu menu, final Node anchor, final double screenX,
				final double screenY) {
			this.hideContextMenu();
			this.openContextMenu = menu;
			menu.show(anchor, screenX, screenY);
		}

		private void hideContextMenu() {
			if (this.openContextMenu != null) {
				this.openContextMenu.hide();
				this.openContextMenu = null;
			}
		}

		private void handleGraphComponentContextMenu(final CovalentComponentCell componentItem, final ContextMenuEvent event) {
			Class<?> componentType = this.componentTypesByCell.get(componentItem);
			if (componentType == null) {
				return;
			}
			Set<Class<?>> targets = this.contextMenuTargets(componentType);
			ContextMenu menu = new ContextMenu();
			MenuItem hide = new MenuItem(ComponentDiagramMessages.get("button.hide"));
			hide.setOnAction(e -> {
				for (Class<?> target : targets) {
					CovalentComponentCell cell = this.componentItems.get(target);
					if (cell != null) {
						cell.setVisible(false);
					}
				}
			});
			MenuItem showDeps = new MenuItem(ComponentDiagramMessages.get("button.showDeps"));
			showDeps.setOnAction(e -> {
				for (Class<?> target : targets) {
					ComponentDiagramViewImpl.this.actionManagerServices.executeAction(
							new ComponentDiagramActionKey(this.sessionKey, Role.ToggleDependenciesVisibility), target, true);
				}
			});
			MenuItem hideDeps = new MenuItem(ComponentDiagramMessages.get("button.hideDeps"));
			hideDeps.setOnAction(e -> {
				for (Class<?> target : targets) {
					ComponentDiagramViewImpl.this.actionManagerServices.executeAction(
							new ComponentDiagramActionKey(this.sessionKey, Role.ToggleDependenciesVisibility), target, false);
				}
			});
			menu.getItems().addAll(hide, showDeps, hideDeps);
			if (this.compositeTypes.contains(componentType)) {
				MenuItem enterComposite = new MenuItem(ComponentDiagramMessages.get("menu.enterComposite"));
				enterComposite.setOnAction(e -> ComponentDiagramViewImpl.this.actionManagerServices.executeAction(
						new ComponentDiagramActionKey(this.sessionKey, Role.EnterComposite), componentType));
				menu.getItems().add(enterComposite);
			}
			this.showContextMenu(menu, this.graph.getGraphic(componentItem), event.getScreenX(), event.getScreenY());
			event.consume();
		}

		private ContextMenu buildCanvasBackgroundMenu() {
			ContextMenu menu = new ContextMenu();
			MenuItem showAll = new MenuItem(ComponentDiagramMessages.get("button.showAll"));
			showAll.setOnAction(e -> this.showAll());
			MenuItem recenter = new MenuItem(ComponentDiagramMessages.get("button.recenter"));
			recenter.setOnAction(e -> ComponentDiagramViewImpl.this.actionManagerServices
					.executeAction(new ComponentDiagramActionKey(this.sessionKey, Role.Recenter)));
			menu.getItems().addAll(showAll, recenter);
			return menu;
		}

		/**
		 * A tree node's context menu - the tree's on-the-spot show/hide control,
		 * complementing the toolbox buttons, since a plain click on a node
		 * selects/navigates.
		 * A node not part of the focus level at all only offers
		 * Navigate; one that is (whether shown or hidden) offers the
		 * same actions as a card's own menu.
		 */
		private ContextMenu buildTreeNodeContextMenu(final Class<?> componentType) {
			ContextMenu menu = new ContextMenu();
			CovalentComponentCell cell = this.componentItems.get(componentType);
			if (cell == null) {
				MenuItem navigateHere = new MenuItem(ComponentDiagramMessages.get("menu.navigateHere"));
				navigateHere.setOnAction(e -> ComponentDiagramViewImpl.this.actionManagerServices.executeAction(
						new ComponentDiagramActionKey(this.sessionKey, Role.NavigateToComponent), componentType));
				menu.getItems().add(navigateHere);
				return menu;
			}
			if (cell.visibleProperty().get()) {
				MenuItem hide = new MenuItem(ComponentDiagramMessages.get("button.hide"));
				hide.setOnAction(e -> cell.setVisible(false));
				menu.getItems().add(hide);
			} else {
				MenuItem show = new MenuItem(ComponentDiagramMessages.get("menu.show"));
				show.setOnAction(e -> cell.setVisible(true));
				menu.getItems().add(show);
			}
			MenuItem showDeps = new MenuItem(ComponentDiagramMessages.get("button.showDeps"));
			showDeps.setOnAction(e -> ComponentDiagramViewImpl.this.actionManagerServices.executeAction(
					new ComponentDiagramActionKey(this.sessionKey, Role.ToggleDependenciesVisibility), componentType, true));
			MenuItem hideDeps = new MenuItem(ComponentDiagramMessages.get("button.hideDeps"));
			hideDeps.setOnAction(e -> ComponentDiagramViewImpl.this.actionManagerServices.executeAction(
					new ComponentDiagramActionKey(this.sessionKey, Role.ToggleDependenciesVisibility), componentType, false));
			menu.getItems().addAll(showDeps, hideDeps);
			if (this.compositeTypes.contains(componentType)) {
				MenuItem enterComposite = new MenuItem(ComponentDiagramMessages.get("menu.enterComposite"));
				enterComposite.setOnAction(e -> ComponentDiagramViewImpl.this.actionManagerServices.executeAction(
						new ComponentDiagramActionKey(this.sessionKey, Role.EnterComposite), componentType));
				menu.getItems().add(enterComposite);
			}
			return menu;
		}

		/**
		 * Click-to-highlight: clicking a port's pad briefly highlights that pad, every wire actually
		 * drawn from it at this level, and the pad at the far end of each such
		 * wire (both ends of a connection light up together). The component-type
		 * port (rendered separately, never present in portEdges) simply finds no
		 * matches here and only flashes itself, matching it never being wired as
		 * a click target in the first place.
		 */
		private void highlightPortGroup(final ComponentPort port) {
			port.highlight();
			for (AnchoredPortEdge edge : this.portEdges) {
				ComponentPort other;
				if (edge.getSourcePort() == port) {
					other = edge.getTargetPort();
				} else if (edge.getTargetPort() == port) {
					other = edge.getSourcePort();
				} else {
					continue;
				}
				edge.highlight();
				other.highlight();
			}
		}

		/**
		 * Nav-badge click: a single hidden target jumps straight there, several
		 * show a small picker first. Either way the actual jump is the
		 * Controller's call ({@code NavigateToComponent}), never decided here;
		 * this method only resolves which target(s) the click was even about.
		 */
		private void handlePortNavigate(final ComponentPort port) {
			Set<Class<?>> targets = port.getNavigationTargets();
			if (targets.isEmpty()) {
				return;
			}
			if (targets.size() == 1) {
				ComponentDiagramViewImpl.this.actionManagerServices.executeAction(
						new ComponentDiagramActionKey(this.sessionKey, Role.NavigateToComponent), targets.iterator().next());
				return;
			}
			ContextMenu menu = new ContextMenu();
			for (Class<?> target : targets) {
				MenuItem item = new MenuItem(target.getSimpleName());
				item.setOnAction(e -> ComponentDiagramViewImpl.this.actionManagerServices.executeAction(
						new ComponentDiagramActionKey(this.sessionKey, Role.NavigateToComponent), target));
				menu.getItems().add(item);
			}
			this.hideContextMenu();
			this.openContextMenu = menu;
			menu.show(this.graph.getGraphic(port.getComponentCell()), Side.RIGHT, 0, 0);
		}

		/**
		 * A port's own context menu: Highlight (same gesture as clicking the
		 * pad), plus one Navigate entry per external target when the port is
		 * navigable - a complement to the nav badge for discoverability, not a
		 * replacement (the badge does the one-click jump for the common
		 * single-target case).
		 */
		private void handlePortContextMenu(final ComponentPort port, final ContextMenuEvent event) {
			ContextMenu menu = new ContextMenu();
			MenuItem highlight = new MenuItem(ComponentDiagramMessages.get("menu.highlight"));
			highlight.setOnAction(e -> this.highlightPortGroup(port));
			menu.getItems().add(highlight);
			for (Class<?> target : port.getNavigationTargets()) {
				MenuItem navigate = new MenuItem(ComponentDiagramMessages.get("menu.navigateTo", target.getSimpleName()));
				navigate.setOnAction(e -> ComponentDiagramViewImpl.this.actionManagerServices.executeAction(
						new ComponentDiagramActionKey(this.sessionKey, Role.NavigateToComponent), target));
				menu.getItems().add(navigate);
			}
			this.showContextMenu(menu, this.graph.getGraphic(port.getComponentCell()), event.getScreenX(), event.getScreenY());
			event.consume();
		}

		/**
		 * A wire's own context menu: Highlight, reusing {@link #highlightPortGroup}
		 * on the edge's source port so the same edge+both-ends highlighting as
		 * a port pad click applies here too, reached by clicking the wire
		 * itself instead of one of its ends.
		 */
		private void handleEdgeContextMenu(final AnchoredPortEdge edge, final ContextMenuEvent event) {
			ContextMenu menu = new ContextMenu();
			MenuItem highlight = new MenuItem(ComponentDiagramMessages.get("menu.highlight"));
			highlight.setOnAction(e -> this.highlightPortGroup(edge.getSourcePort()));
			Class<?> sourceType = this.componentTypesByCell.get(edge.getSourceComponent());
			Class<?> targetType = this.componentTypesByCell.get(edge.getTargetComponent());
			MenuItem goToSource = new MenuItem(ComponentDiagramMessages.get("menu.goToSource",
					sourceType == null ? "" : sourceType.getSimpleName()));
			goToSource.setOnAction(e -> this.navigateTo(sourceType));
			MenuItem goToTarget = new MenuItem(ComponentDiagramMessages.get("menu.goToTarget",
					targetType == null ? "" : targetType.getSimpleName()));
			goToTarget.setOnAction(e -> this.navigateTo(targetType));
			MenuItem record = new MenuItem(ComponentDiagramMessages.get("menu.recordBetween"));
			record.setOnAction(e -> this.recordBetween(sourceType, targetType));
			record.setDisable(
					sourceType == null || targetType == null || ComponentDiagramViewImpl.this.recordBlocked.get());
			MenuItem hideLink = new MenuItem(ComponentDiagramMessages.get("menu.hideLink"));
			hideLink.setOnAction(e -> {
				BooleanProperty shown = this.linkShown.get(edge);
				if (shown != null) {
					shown.set(false);
				}
			});
			menu.getItems().addAll(highlight, goToSource, goToTarget, new SeparatorMenuItem(), record,
					new SeparatorMenuItem(), hideLink);
			this.showContextMenu(menu, this.graph.getGraphic(edge.getSourceComponent()), event.getScreenX(), event.getScreenY());
			event.consume();
		}

		// --- Gestures: every one reports to the Interaction via ActionManager. ---

		private void configureGraphGestures() {
			this.graph.getViewportGestures().setPanButton(MouseButton.PRIMARY);
			this.graph.getNodeGestures().setDragButton(MouseButton.PRIMARY);
			this.graph.getNodeGestures().setOnNodeDragged(node -> this.layoutPortSides());
			this.graph.getNodeGestures().setOnNodeDragFinished(node -> this.layoutPortSides());
		}

		private void installGraphCanvasHost() {
			this.graphSelectionRectangle = new Rectangle();
			this.graphSelectionRectangle.setManaged(false);
			this.graphSelectionRectangle.setVisible(false);
			this.graphSelectionRectangle.setMouseTransparent(true);
			this.applySelectionRectangleColor();
			this.graphSelectionRectangle.getStrokeDashArray().setAll(8d, 6d);
			this.graphSelectionRectangle.setArcWidth(10);
			this.graphSelectionRectangle.setArcHeight(10);

			this.graphSelectionOverlay = new Pane(this.graphSelectionRectangle);
			this.graphSelectionOverlay.setPickOnBounds(false);
			this.graphSelectionOverlay.setMouseTransparent(true);

			Node legendOverlay = this.buildLegend();
			StackPane.setAlignment(legendOverlay, Pos.BOTTOM_LEFT);
			StackPane.setMargin(legendOverlay, new Insets(12));
			this.graphCanvasHost = new StackPane(this.graph.getCanvas(), this.graphSelectionOverlay, legendOverlay);
			this.graphCanvasHost.getStyleClass().add("sv-graph-canvas");
			this.graphCanvasHost.setOnContextMenuRequested(event ->
					this.showContextMenu(this.buildCanvasBackgroundMenu(), this.graphCanvasHost, event.getScreenX(),
							event.getScreenY()));
			this.installGraphSelectionGestures();

			this.componentsViewContainer.getChildren().clear();
			this.componentsViewContainer.setCenter(this.graphCanvasHost);
		}

		private void installGraphSelectionGestures() {
			// Any press anywhere in the window closes this tab's open context menu,
			// even when a gesture handler (panning, rectangle selection) consumes
			// that press before the popup's own auto-hide sees it.
			EventHandler<MouseEvent> hideOnPress = event -> this.hideContextMenu();
			this.graphCanvasHost.sceneProperty().addListener((observable, oldScene, newScene) -> {
				if (oldScene != null) {
					oldScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, hideOnPress);
				}
				if (newScene != null) {
					newScene.addEventFilter(MouseEvent.MOUSE_PRESSED, hideOnPress);
				}
			});
			if (this.graphCanvasHost.getScene() != null) {
				this.graphCanvasHost.getScene().addEventFilter(MouseEvent.MOUSE_PRESSED, hideOnPress);
			}
			this.graphCanvasHost.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
				if (event.getButton() == MouseButton.PRIMARY && (event.isShiftDown() || event.isControlDown())) {
					this.graphSelectionActive = true;
					this.graphSelectionAdditive = event.isControlDown();
					this.graphSelectionStartX = event.getX();
					this.graphSelectionStartY = event.getY();
					this.updateGraphSelectionRectangle(event.getX(), event.getY());
					this.graphSelectionRectangle.setVisible(true);
					event.consume();
				}
			});
			this.graphCanvasHost.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
				if (this.graphSelectionActive) {
					this.updateGraphSelectionRectangle(event.getX(), event.getY());
					event.consume();
				}
			});
			this.graphCanvasHost.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
				if (this.graphSelectionActive) {
					this.graphSelectionActive = false;
					this.graphSelectionRectangle.setVisible(false);
					if (event.isStillSincePress()) {
						// Not a drag but a click: JavaFX synthesizes a MOUSE_CLICKED for it,
						// and the card's own click handler (Ctrl toggles) is its only owner.
						return;
					}
					Set<Class<?>> componentTypesInRectangle = this.findComponentsInSelectionRectangle();
					Set<Class<?>> newSelection = componentTypesInRectangle;
					if (this.graphSelectionAdditive) {
						newSelection = new LinkedHashSet<>(this.selectedComponentTypes);
						newSelection.addAll(componentTypesInRectangle);
					}
					ComponentDiagramViewImpl.this.actionManagerServices.executeAction(
							new ComponentDiagramActionKey(this.sessionKey, Role.SelectComponents), newSelection);
					event.consume();
				}
			});
			this.graphCanvasHost.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
				boolean backgroundClicked = event.getButton() == MouseButton.PRIMARY && !event.isShiftDown()
						&& !event.isControlDown()
						&& (event.getTarget() == this.graphCanvasHost || event.getTarget() == this.graph.getCanvas());
				if (backgroundClicked) {
					ComponentDiagramViewImpl.this.actionManagerServices.executeAction(
							new ComponentDiagramActionKey(this.sessionKey, Role.SelectComponents), Set.of());
				}
			});
		}

		private void updateGraphSelectionRectangle(final double currentX, final double currentY) {
			double left = Math.min(this.graphSelectionStartX, currentX);
			double top = Math.min(this.graphSelectionStartY, currentY);
			this.graphSelectionRectangle.setX(left);
			this.graphSelectionRectangle.setY(top);
			this.graphSelectionRectangle.setWidth(Math.abs(currentX - this.graphSelectionStartX));
			this.graphSelectionRectangle.setHeight(Math.abs(currentY - this.graphSelectionStartY));
		}

		private Set<Class<?>> findComponentsInSelectionRectangle() {
			Set<Class<?>> selectedTypes = new LinkedHashSet<>();
			Bounds selectionBounds = this.graphSelectionRectangle.getBoundsInParent();
			for (Entry<Class<?>, CovalentComponentCell> entry : this.componentItems.entrySet()) {
				CovalentComponentCell componentItem = entry.getValue();
				if (!componentItem.visibleProperty().get()) {
					continue;
				}
				Bounds componentBounds = this.graphCanvasHost.sceneToLocal(this.graph.getGraphic(componentItem)
						.localToScene(this.graph.getGraphic(componentItem).getBoundsInLocal()));
				if (selectionBounds.intersects(componentBounds)) {
					selectedTypes.add(entry.getKey());
				}
			}
			return selectedTypes;
		}

		private void handleGraphComponentClick(final CovalentComponentCell componentItem, final MouseEvent event) {
			Class<?> componentType = this.componentTypesByCell.get(componentItem);
			if (componentType == null) {
				return;
			}
			if (event.getClickCount() >= 2 && this.compositeTypes.contains(componentType)) {
				ComponentDiagramViewImpl.this.actionManagerServices.executeAction(
						new ComponentDiagramActionKey(this.sessionKey, Role.EnterComposite), componentType);
				event.consume();
				return;
			}
			if (event.isControlDown()) {
				ComponentDiagramViewImpl.this.actionManagerServices.executeAction(
						new ComponentDiagramActionKey(this.sessionKey, Role.ToggleComponentSelection), componentType);
			} else {
				ComponentDiagramViewImpl.this.actionManagerServices.executeAction(
						new ComponentDiagramActionKey(this.sessionKey, Role.SelectComponents), Set.of(componentType));
			}
			event.consume();
		}

		private void syncSelectionFromTree() {
			LinkedHashSet<Class<?>> selectedTypes = new LinkedHashSet<>();
			for (TreeItem<ComponentTreeItem> selectedItem : this.componentsList.getSelectionModel().getSelectedItems()) {
				if (selectedItem != null && selectedItem.getValue() != null && !selectedItem.getValue().isRoot()) {
					selectedTypes.add(selectedItem.getValue().getComponentType());
				}
			}
			ComponentDiagramViewImpl.this.actionManagerServices.executeAction(
					new ComponentDiagramActionKey(this.sessionKey, Role.SelectComponents), selectedTypes);
		}

		private void refreshSelectionPresentation() {
			for (Entry<Class<?>, CovalentComponentCell> entry : this.componentItems.entrySet()) {
				entry.getValue().setSelected(this.selectedComponentTypes.contains(entry.getKey()));
			}
			this.syncTreeSelectionFromState();
		}

		private void syncTreeSelectionFromState() {
			if (this.componentsList == null) {
				return;
			}
			this.notifyTreeSelection = false;
			try {
				this.pushStateIntoTree();
			} finally {
				this.notifyTreeSelection = true;
			}
		}

		private void pushStateIntoTree() {
			this.componentsList.getSelectionModel().clearSelection();
			TreeItem<ComponentTreeItem> firstSelectedTreeItem = null;
			for (Class<?> componentType : this.selectedComponentTypes) {
				TreeItem<ComponentTreeItem> treeItem = this.treeNodesByType.get(componentType);
				if (treeItem != null) {
					// Shown, and a selected composite opened on its direct children only.
					for (TreeItem<ComponentTreeItem> ancestor = treeItem.getParent(); ancestor != null;
							ancestor = ancestor.getParent()) {
						ancestor.setExpanded(true);
					}
					treeItem.setExpanded(true);
					this.componentsList.getSelectionModel().select(treeItem);
					if (firstSelectedTreeItem == null) {
						firstSelectedTreeItem = treeItem;
					}
				}
			}
			if (firstSelectedTreeItem == null) {
				// Nothing selected: the tree shows the displayed level, whichever way it was reached.
				firstSelectedTreeItem = this.focusPath.isEmpty() ? this.rootNode
						: this.treeNodesByType.get(this.focusPath.get(this.focusPath.size() - 1));
				if (firstSelectedTreeItem != null) {
					this.componentsList.getSelectionModel().select(firstSelectedTreeItem);
				}
			}
			// Selection is being pushed into the tree from elsewhere (canvas
			// click, programmatic selection) rather than originating from the
			// user's own click in the tree, so it's safe to scroll it into view
			// here without fighting the user's own scroll position.
			if (firstSelectedTreeItem != null) {
				this.componentsList.scrollTo(this.componentsList.getRow(firstSelectedTreeItem));
			}
		}

		// --- Viewport fit ---

		private void fitGraphToViewportIfPending() {
			if (!this.graphViewportFitPending) {
				return;
			}
			Platform.runLater(() -> {
				if (this.fitGraphToViewportNow()) {
					this.graphViewportFitPending = false;
				}
			});
		}

		/**
		 * The bounds, in the canvas's own coordinates, of what the diagram shows: its visible cards,
		 * and the drawn paths of its visible wires. A wire's own region spans the whole canvas from
		 * its origin, so it is its paths that count, not the region.
		 *
		 * @return the content's bounds, {@code null} when nothing is shown
		 */
		private Bounds contentBounds() {
			double minX = Double.POSITIVE_INFINITY;
			double minY = Double.POSITIVE_INFINITY;
			double maxX = Double.NEGATIVE_INFINITY;
			double maxY = Double.NEGATIVE_INFINITY;
			List<Bounds> parts = new ArrayList<>();
			for (Cell cell : this.model.getAllCells()) {
				Region graphic = this.graph.getGraphic(cell);
				if (graphic.isVisible() && graphic.getParent() == this.graph.getCanvas()) {
					parts.add(graphic.getBoundsInParent());
				}
			}
			for (Edge edge : this.model.getAllEdges()) {
				Region graphic = this.graph.getGraphic(edge);
				if (!graphic.isVisible() || graphic.getParent() != this.graph.getCanvas()) {
					continue;
				}
				for (Node path : graphic.getChildrenUnmodifiable()) {
					if (path.isVisible()) {
						parts.add(graphic.localToParent(path.getBoundsInParent()));
					}
				}
			}
			for (Bounds part : parts) {
				if (part.isEmpty()) {
					continue;
				}
				minX = Math.min(minX, part.getMinX());
				minY = Math.min(minY, part.getMinY());
				maxX = Math.max(maxX, part.getMaxX());
				maxY = Math.max(maxY, part.getMaxY());
			}
			return minX > maxX ? null : new BoundingBox(minX, minY, maxX - minX, maxY - minY);
		}

		private boolean fitGraphToViewportNow() {
			if (this.componentsViewContainer == null || this.graph == null || this.model == null
					|| this.model.getAllCells().isEmpty()) {
				return false;
			}
			double viewportWidth = this.componentsViewContainer.getWidth();
			double viewportHeight = this.componentsViewContainer.getHeight();
			if (viewportWidth <= GRAPH_VIEW_MARGIN || viewportHeight <= GRAPH_VIEW_MARGIN) {
				return false;
			}

			this.graph.getCanvas().setTranslateX(0);
			this.graph.getCanvas().setTranslateY(0);
			this.graph.getCanvas().setScale(1.0);

			this.graph.getEdgeRouting().layoutNow();
			this.graph.getCanvas().applyCss();
			this.graph.getCanvas().layout();

			Bounds content = this.contentBounds();
			if (content == null || content.getWidth() <= 0 || content.getHeight() <= 0) {
				return false;
			}
			ViewportFit fit = ViewportFit.of(content, this.graph.getCanvas().getWidth(),
					this.graph.getCanvas().getHeight(), viewportWidth, viewportHeight, GRAPH_VIEW_MARGIN,
					this.graph.getViewportGestures().getMinScale());
			this.graph.getCanvas().setScale(fit.scale());
			this.graph.getCanvas().setTranslateX(fit.translateX());
			this.graph.getCanvas().setTranslateY(fit.translateY());
			return true;
		}
	}

	// --- Support types ---

	/** A tree row: a component, or the virtual root (no component type) that stands for the whole container. */
	private static final class ComponentTreeItem {
		private final Class<?> componentType;
		private final String label;

		ComponentTreeItem(final Class<?> componentType, final String label) {
			this.componentType = componentType;
			this.label = label;
		}

		/** @return the component, or {@code null} for the virtual root */
		Class<?> getComponentType() {
			return this.componentType;
		}

		boolean isRoot() {
			return this.componentType == null;
		}

		@Override
		public String toString() {
			return this.label;
		}
	}

	/** Two components, in a given order: the key of the links counted between them. */
	private record Pair(CovalentComponentCell first, CovalentComponentCell second) {
	}
}
