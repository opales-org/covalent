/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.dependencymanagement.view.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.Separator;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;

import org.opales.covalent.hmi.action.ActionConsumerServices;
import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.supervision.componentview.internal.Snapshots;
import org.opales.covalent.supervision.componentview.internal.Ordering;
import org.opales.covalent.supervision.componentview.internal.CollapsibleSide;
import org.opales.covalent.supervision.componentview.internal.diagram.cells.ClassCell;
import org.opales.covalent.supervision.componentview.internal.diagram.edges.DependencyEdge;
import org.opales.covalent.supervision.componentview.internal.Toolbox;
import org.opales.covalent.supervision.componentview.internal.diagram.edges.EdgeRoutingStyle;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.Graph;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.GraphModel;
import org.opales.covalent.supervision.componentview.internal.diagram.layout.ElkLayout;
import org.opales.covalent.supervision.dependencymanagement.interaction.DependencyManagementActionKey;
import org.opales.covalent.supervision.dependencymanagement.model.ComponentKind;
import org.opales.covalent.supervision.dependencymanagement.model.DependencyManagementEvents;
import org.opales.covalent.supervision.dependencymanagement.model.DependencyManagementGraph;
import org.opales.covalent.supervision.dependencymanagement.model.DependencyManagementModelReadServices;
import org.opales.covalent.supervision.dependencymanagement.view.DependencyManagementView;
import org.opales.covalent.supervision.dependencymanagement.view.DependencyManagementViewServices;
import org.opales.covalent.supervision.supervisionhmi.view.FxThread;
import org.opales.covalent.supervision.supervisionhmi.view.PanelHostingServices;
import org.opales.covalent.supervision.supervisionhmi.view.RailEntry;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisorTabDescriptorAdapter;
import org.opales.covalent.supervision.supervisionhmi.view.ToolbarFunction;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;

/**
 * DependencyManagement's View: a simplified diagram canvas (identity-only
 * boxes, no port anchors - see {@code ClassCell}) plus a side panel listing
 * circular dependencies and a few summary statistics; selecting a cycle
 * highlights its components. Renders the graph it reads from the Model through
 * {@code DependencyManagementModelReadServices} when {@link DependencyManagementEvents}
 * announces a change; reports gestures through
 * {@link ActionConsumerServices}.
 * <p>
 * Cycle membership is color-coded: each detected cycle gets a distinct color
 * from a small fixed palette (see {@link #CYCLE_PALETTE}), shown as a
 * vertical accent bar on its components' cards, on the composition links
 * that are steps of the cycle, and on the dependencies that make its other
 * steps - from a component to the next one or to one of its composed
 * descendants; anything outside a cycle is neutral gray.
 * <p>
 * Only a cycle's component boxes highlight on selection (reusing
 * {@code ClassCell}'s selected-style toggle), not its edges.
 * <p>
 * A component tree on the left scopes the graph: a click on a composite shows it and all its
 * descendants down to the leaf components, with the dependencies between them (see
 * {@link DependencyScope}); the Outside neighbors toggle adds, toned down, the components
 * outside that subtree linked to it. A breadcrumb above the graph goes back up.
 */
@Implementation
public class DependencyManagementViewImpl implements DependencyManagementView, DependencyManagementViewServices,
		DependencyManagementEvents {

	/** Creates the View; the graph canvas is built when the component activates. */
	public DependencyManagementViewImpl() {
		// Nothing to initialize.
	}

	private static final double COMPONENT_LEVEL_GAP = 120;
	private static final double COMPONENT_NODE_GAP = 28;
	private static final double GRAPH_VIEW_MARGIN = 80;

	/** Distinct colors assigned to detected cycles, in order; wraps if more cycles exist. */
	private static final Color[] CYCLE_PALETTE = {
			Color.web("#ef4444"), // red
			Color.web("#8b5cf6"), // violet
			Color.web("#f59e0b"), // amber
			Color.web("#10b981"), // emerald
			Color.web("#3b82f6"), // blue
			Color.web("#ec4899"), // pink
	};

	/** Components/edges not part of any detected cycle. */
	private static final Color NEUTRAL_COLOR = Color.web("#9ca3af");

	/** The Outside neighbors toggle: three touching hexagons around the one looked at, toned down. */
	private static final String ICON_NEIGHBORS_AROUND = "M4.1 5 L6.7 6.5 M6.7 9.5 L4.1 11 L1.51 9.5 L1.51 6.5 L4.1 5 "
			+ "M11.9 0.5 L14.5 2 L14.5 5 L11.9 6.5 M9.3 5 L9.3 2 L11.9 0.5 "
			+ "M11.9 9.5 L14.5 11 L14.5 14 L11.9 15.5 L9.3 14 L9.3 11";
	/** The Outside neighbors toggle: the hexagon looked at, at full strength. */
	private static final String ICON_NEIGHBORS_CENTRE = "M9.3 5 L11.9 6.5 L11.9 9.5 L9.3 11 L6.7 9.5 L6.7 6.5 Z";
	/** How much the neighbors of the toggle's icon are toned down. */
	private static final double ICON_NEIGHBORS_OPACITY = 0.4;
	/** How much an outside neighbor's card is toned down, apart from the scoped subtree. */
	private static final double OUTSIDE_NEIGHBOR_OPACITY = 0.5;
	/** The component tree's width when the panel opens. */
	private static final double TREE_PANEL_WIDTH = 250;
	/** The side panel's share of what the tree leaves. */
	private static final double SIDE_PANEL_SHARE = 0.26;
	/** The whole graph's key among the scopes, which no composite roots. */
	private static final Object WHOLE_GRAPH = new Object();
	/** Going one level up in the breadcrumb, as in the component diagram. */
	private static final String ICON_UP = "M10 3.5 L5.5 8 L10 12.5";

	/** How far aside each of the two links of a dependency both ways is drawn, in pixels. */
	private static final double MUTUAL_LINK_OFFSET = 3;

	/** The composition links' color, as in the component diagram. */
	private static final Color COMPOSITION_COLOR = Color.web("#475569");

	/** The Refresh action's icon: two arrows chasing each other in a circle. */
	private static final String ICON_REFRESH = "M3.02 6.19 A5.3 5.3 0 0 1 12.49 5.19 M13.65 3.03 L12.97 5.95 L10.05 5.28 "
			+ "M12.98 9.81 A5.3 5.3 0 0 1 3.51 10.81 M2.35 12.97 L3.03 10.05 L5.95 10.72";

	/** A composite's diamond, then the link to its child. */
	private static final String ICON_COMPOSITION = "M1.5 8 L4.5 5 L7.5 8 L4.5 11 Z M7.5 8 H14.5";

	/** What the focused component directly depends on or composes ("upstream"). */
	private static final Color UPSTREAM_COLOR = Color.web("#0ea5e9");

	/** What directly depends on the focused component or composes it ("downstream"). */
	private static final Color DOWNSTREAM_COLOR = Color.web("#d946ef");

	// Same icon paths as ComponentDiagramViewImpl's toolbox, for the same
	// Structure category.
	private static final String ICON_COMPOSITES = "M6.22 3.25 L8.00 4.28 L8.00 6.32 L6.22 7.35 L4.45 6.32 L4.45 4.27 Z "
			+ "M9.78 3.25 L11.55 4.28 L11.55 6.32 L9.78 7.35 L8.00 6.32 L8.00 4.27 Z "
			+ "M8.00 0.18 L9.78 1.20 L9.78 3.25 L8.00 4.28 L6.22 3.25 L6.22 1.20 Z";
	private static final String ICON_COMPONENT = "M8.00 0.60 L10.86 2.25 L10.86 5.55 L8.00 7.20 L5.14 5.55 L5.14 2.25 Z "
			+ "M8 3.2 A0.7 0.7 0 1 1 7.99 3.2";
	private static final String ICON_DEPENDENCY = "M1.5 4 H3.3 M5 4 H6.8 M8.5 4 H10.3 M11.6 1.6 L14 4 L11.6 6.4";
	private static final String ICON_EYE_OPEN = "M2.2 12.4 Q8 7.6 13.8 12.4 Q8 17.2 2.2 12.4 Z M8 11 A1.4 1.4 0 1 1 7.99 11";
	private static final String ICON_EYE_CLOSED = "M2.2 11.6 Q8 15.8 13.8 11.6 M4.3 13.9 L3.4 15.1 M8 15 V16.2 "
			+ "M11.7 13.9 L12.6 15.1";

	/** The composition links drawn, shown or hidden together. */
	private final List<DependencyEdge> compositionEdges = new ArrayList<>();

	private ToggleButton compositionToggle;

	/** Adds the components outside the scoped subtree linked to it; off by default. */
	private ToggleButton neighborsToggle;

	/** The whole graph as the Model published it; {@link #currentGraph} is the part shown. */
	private DependencyManagementGraph fullGraph = new DependencyManagementGraph(Set.of(), Map.of(), List.of());

	/** The component the graph is scoped to from the tree, {@code null} for the whole graph. */
	private Class<?> scopeRoot;

	/**
	 * How a scope was left: its components' positions, the zoom and the view's offset.
	 *
	 * @param positions  each component's position
	 * @param scale      the zoom
	 * @param translateX the view's horizontal offset
	 * @param translateY the view's vertical offset
	 */
	private record ScopeLayout(Map<Class<?>, double[]> positions, double scale, double translateX, double translateY) {
	}

	/** The scope shown: its root ({@link #WHOLE_GRAPH} for none) and whether the outside neighbors are. */
	private List<Object> shownScope;

	/** How each scope visited was left, so that coming back shows it as it was. */
	private final Map<List<Object>, ScopeLayout> scopeLayouts = new HashMap<>();

	/** A component to center once the scoped graph is laid out and fitted, {@code null} for none. */
	private Class<?> pendingCenter;

	private TreeView<Class<?>> componentTree;
	private TextField treeSearchField;
	private final Map<Class<?>, TreeItem<Class<?>>> treeItemsByType = new HashMap<>();
	private HBox breadcrumbBar;

	/** The latest data graph, kept to compute the upstream and downstream components and for Show All. */
	private DependencyManagementGraph currentGraph = new DependencyManagementGraph(Set.of(), Map.of(), List.of());

	private final Map<ClassCell, Class<?>> componentTypeByCell = new HashMap<>();

	/** Dependency edges (not compositions), keyed by {@code List.of(from, to)}, for upstream/downstream re-coloring and the link menu. */
	private final Map<List<Class<?>>, DependencyEdge> dependencyEdgeByPair = new HashMap<>();

	/** Each dependency edge's color before any click-highlight is applied - cycle membership or neutral. */
	private final Map<DependencyEdge, Color> baseEdgeColor = new LinkedHashMap<>();

	/** Composition edges, keyed by {@code List.of(composite, child)} - same role as {@link #dependencyEdgeByPair}. */
	private final Map<List<Class<?>>, DependencyEdge> compositionEdgeByPair = new HashMap<>();

	/** Each composition edge's color before any click-highlight is applied - cycle membership or neutral. */
	private final Map<DependencyEdge, Color> baseCompositionColor = new LinkedHashMap<>();

	/** Links hidden one at a time from their own context menu ("Hide this link"). */
	private final Set<DependencyEdge> manuallyHiddenLinks = new HashSet<>();

	/** The outside neighbors hidden because no visible link is left to them, not hidden by hand. */
	private final Set<Class<?>> hiddenForWantOfLink = new HashSet<>();

	/** The component last clicked, if any - what the upstream/downstream highlight and the Structure buttons act on. */
	private Class<?> focusedComponent;

	/** This panel's only context menu at a time - see {@link #showContextMenu}. */
	private ContextMenu openContextMenu;

	@UsedServices
	private ActionConsumerServices actionManagerServices;

	@UsedServices
	private PanelHostingServices useHostingServices;

	@UsedServices
	private DependencyManagementModelReadServices useDependencyManagementModelReadServices;

	private final Map<Class<?>, ClassCell> componentItems = new HashMap<>();

	private GraphModel model;
	private Graph graph;
	private ElkLayout layout;
	private BorderPane root;
	private BorderPane graphViewContainer;
	private ListView<List<Class<?>>> cyclesList;
	private VBox legendContent;
	/** Shows or hides the legend over the canvas. */
	private ToggleButton legendToggle;
	private Label statsComponentsValue;
	private Label statsDependenciesValue;
	private Label statsCyclesValue;
	private Label statsIsolatedValue;
	private TextFlow statusLeftFlow;
	private Label statusZoomLabel;

	private Label unavailableLabel;

	private boolean graphViewportFitPending = false;
	private boolean contentBuilt = false;

	/**
	 * True from the canvas's first attach to a scene until its first
	 * successful viewport fit: the canvas stays hidden for that whole
	 * stretch, so the very first ElkLayout pass - needing a scene to measure
	 * label sizes, hence unavoidably a pulse or two after the panel opens -
	 * is never caught mid-way, with every cell still at its unlaid-out
	 * default position bunched in the corner.
	 */
	private boolean awaitingInitialReveal = false;

	// --- Lifecycle ---

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
		this.root = new BorderPane();
		this.root.getStyleClass().add("sv-dep-root");

		this.layout = new ElkLayout(COMPONENT_LEVEL_GAP, COMPONENT_NODE_GAP);
		this.graph = new Graph();
		// Straight links read better here than the elbowed ones the Components diagram keeps by default.
		this.graph.edgeRoutingStyleProperty().set(EdgeRoutingStyle.STRAIGHT);
		this.model = this.graph.getModel();
		this.graph.getViewportGestures().setPanButton(MouseButton.PRIMARY);
		this.graph.getCanvas().sceneProperty().addListener((s, o, n) -> {
			if (n != null && o == null) {
				this.graph.getCanvas().setVisible(false);
				this.awaitingInitialReveal = true;
				Platform.runLater(this::layoutGraph);
			}
		});

		this.breadcrumbBar = new HBox(4);
		this.breadcrumbBar.setPadding(new Insets(4, 8, 4, 8));
		this.breadcrumbBar.setAlignment(Pos.CENTER_LEFT);
		this.breadcrumbBar.setVisible(false);
		this.breadcrumbBar.setManaged(false);
		this.root.setTop(new VBox(this.buildToolbox(), this.breadcrumbBar));

		this.graphViewContainer = new BorderPane();
		this.graphViewContainer.widthProperty().addListener((s, o, n) -> this.fitGraphToViewportIfPending());
		this.graphViewContainer.heightProperty().addListener((s, o, n) -> this.fitGraphToViewportIfPending());
		this.graphViewContainer.getStyleClass().add("sv-dep-canvas");

		VBox legendBox = this.buildLegendBox();
		legendBox.visibleProperty().bind(this.legendToggle.selectedProperty());
		legendBox.managedProperty().bind(this.legendToggle.selectedProperty());
		this.unavailableLabel = new Label(
				DependencyGraphTexts.get("unavailable"));
		this.unavailableLabel.getStyleClass().add("sv-dep-unavailable");
		this.unavailableLabel.setWrapText(true);
		this.unavailableLabel.setMaxWidth(360);
		this.unavailableLabel.setTextAlignment(TextAlignment.CENTER);
		this.unavailableLabel.setVisible(false);
		this.unavailableLabel.setManaged(false);
		StackPane canvasStack = new StackPane(this.graph.getCanvas(), legendBox, this.unavailableLabel);
		StackPane.setAlignment(legendBox, Pos.BOTTOM_LEFT);
		StackPane.setMargin(legendBox, new Insets(12));
		canvasStack.setOnContextMenuRequested(event -> {
			this.showContextMenu(this.buildBackgroundContextMenu(), canvasStack, event.getScreenX(), event.getScreenY());
			event.consume();
		});
		this.installContextMenuDismissal(canvasStack);
		canvasStack.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
			boolean backgroundClicked = event.getButton() == MouseButton.PRIMARY
					&& event.isStillSincePress()
					&& (event.getTarget() == canvasStack || event.getTarget() == this.graph.getCanvas());
			if (backgroundClicked) {
				this.clearSelection();
			}
		});
		this.graphViewContainer.setCenter(canvasStack);

		this.cyclesList = new ListView<>();
		this.cyclesList.setMinWidth(220);
		this.cyclesList.getStyleClass().add("sv-dep-cycles");
		this.cyclesList.setCellFactory(list -> new ListCell<>() {
			@Override
			protected void updateItem(final List<Class<?>> cycle, final boolean empty) {
				super.updateItem(cycle, empty);
				if (empty || cycle == null) {
					this.setGraphic(null);
				} else {
					this.setGraphic(DependencyManagementViewImpl.this.buildCycleCard(cycle, this.getIndex()));
				}
			}
		});
		this.cyclesList.getSelectionModel().selectedItemProperty()
				.addListener((observable, oldValue, newValue) -> this.highlightCycle(newValue));

		VBox rightPanel = this.buildRightPanel();

		VBox treePanel = this.buildTreePanel();
		SplitPane split = new SplitPane(treePanel, this.graphViewContainer, rightPanel);
		CollapsibleSide.install(split, treePanel, true, "dependencies.tree");
		CollapsibleSide.install(split, rightPanel, false, "dependencies.side");
		// The tree opens at a fixed width, as in the component diagram; the graph and the side panel share the rest.
		split.widthProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(final ObservableValue<? extends Number> observable,
					final Number oldWidth, final Number newWidth) {
				double width = newWidth.doubleValue();
				if (width > TREE_PANEL_WIDTH) {
					split.setDividerPositions(TREE_PANEL_WIDTH / width,
							1 - SIDE_PANEL_SHARE * (width - TREE_PANEL_WIDTH) / width);
					observable.removeListener(this);
				}
			}
		});
		SplitPane.setResizableWithParent(treePanel, false);
		this.root.setCenter(split);

		this.root.setBottom(this.buildStatusBar());

		this.zoomScaleProperty().addListener((observable, oldValue, newValue) -> {
			this.actionManagerServices.executeAction(DependencyManagementActionKey.ZoomChanged, newValue.doubleValue());
			this.statusZoomLabel.setText(DependencyGraphTexts.get("zoom", Math.round(newValue.doubleValue() * 100)));
		});
	}

	/** Toolbox above the graph, in the same style as the component diagram's. */
	private Node buildToolbox() {
		Button recenter = Toolbox.iconButton(Toolbox.ICON_RECENTER, DependencyGraphTexts.get("recenter"));
		recenter.setOnAction(e -> this.actionManagerServices.executeAction(DependencyManagementActionKey.Recenter));
		this.legendToggle = Toolbox.iconToggle(Toolbox.ICON_LEGEND, DependencyGraphTexts.get("legend.toggle"));
		this.legendToggle.setSelected(true);
		Button refresh = Toolbox.iconButton(ICON_REFRESH, DependencyGraphTexts.get("refresh"));
		refresh.setOnAction(e -> this.actionManagerServices.executeAction(DependencyManagementActionKey.Refresh));
		Button snapshot = Toolbox.iconButton(Toolbox.ICON_SNAPSHOT, DependencyGraphTexts.get("snapshot"));
		snapshot.setOnAction(e -> this.actionManagerServices.executeAction(DependencyManagementActionKey.Export));
		this.compositionToggle = Toolbox.iconToggle(ICON_COMPOSITION, DependencyGraphTexts.get("compositionLinks"));
		this.compositionToggle.setSelected(true);
		this.compositionToggle.selectedProperty().addListener((observable, wasShown, shown) -> {
			this.refreshEdgeVisibility();
			if (this.scopeRoot != null) {
				this.showScope();
			}
		});

		// Same Structure category as the component diagram's own toolbox: show/hide
		// every component, hide the focused one, show/hide what it depends on.
		Button showAll = this.symbolIconButton(ICON_COMPOSITES, ICON_EYE_OPEN, DependencyGraphTexts.get("showAll"));
		showAll.setOnAction(e -> this.showAllComponents());
		Button hideAll = this.symbolIconButton(ICON_COMPOSITES, ICON_EYE_CLOSED, DependencyGraphTexts.get("hideAll"));
		hideAll.setOnAction(e -> this.hideAllComponents());
		Button hideSelected = this.symbolIconButton(ICON_COMPONENT, ICON_EYE_CLOSED, DependencyGraphTexts.get("hideSelected"));
		hideSelected.setOnAction(e -> this.hideComponent(this.focusedComponent));
		Button showDeps = this.symbolIconButton(ICON_DEPENDENCY, ICON_EYE_OPEN, DependencyGraphTexts.get("showDependencies"));
		showDeps.setOnAction(e -> this.setDependenciesVisible(this.focusedComponent, true));
		Button hideDeps = this.symbolIconButton(ICON_DEPENDENCY, ICON_EYE_CLOSED, DependencyGraphTexts.get("hideDependencies"));
		hideDeps.setOnAction(e -> this.setDependenciesVisible(this.focusedComponent, false));
		this.neighborsToggle = Toolbox.iconToggle(ICON_NEIGHBORS_CENTRE, DependencyGraphTexts.get("outsideNeighbors"));
		SVGPath around = new SVGPath();
		around.setContent(ICON_NEIGHBORS_AROUND);
		around.getStyleClass().add("sv-tool-icon");
		around.setOpacity(ICON_NEIGHBORS_OPACITY);
		this.neighborsToggle.setGraphic(new Group(around, this.neighborsToggle.getGraphic()));
		this.neighborsToggle.setDisable(true);
		this.neighborsToggle.selectedProperty().addListener((observable, wasShown, shown) -> this.showScope());

		return Toolbox.bar(
				Toolbox.category(DependencyGraphTexts.get("toolbox.formatting"), Toolbox.routingStyleSelector(this.graph.edgeRoutingStyleProperty()),
						recenter, this.legendToggle, this.compositionToggle),
				Toolbox.category(DependencyGraphTexts.get("toolbox.structure"), showAll, hideAll, hideSelected, showDeps, hideDeps,
						this.neighborsToggle),
				Toolbox.category(DependencyGraphTexts.get("toolbox.diagram"), refresh, snapshot));
	}

	/** A toolbox button whose icon is a finely drawn symbol above an eye, as in the component diagram's toolbox. */
	private Button symbolIconButton(final String symbolPath, final String eyePath, final String tooltip) {
		Button button = Toolbox.iconButton(eyePath, tooltip);
		SVGPath symbol = new SVGPath();
		symbol.setContent(symbolPath);
		symbol.getStyleClass().addAll("sv-tool-icon", "sv-tool-icon-fine");
		button.setGraphic(new Group(symbol, button.getGraphic()));
		return button;
	}

	private VBox buildLegendBox() {
		Label legendHeader = new Label(DependencyGraphTexts.get("legend"));
		legendHeader.getStyleClass().addAll("sv-dep-heading", "sv-dep-legend-heading");

		this.legendContent = new VBox(4);

		VBox legendBox = new VBox(6, legendHeader, this.legendContent);
		legendBox.setPadding(new Insets(10));
		legendBox.setMaxWidth(Region.USE_PREF_SIZE);
		legendBox.setMaxHeight(Region.USE_PREF_SIZE);
		legendBox.getStyleClass().add("sv-dep-legend");
		return legendBox;
	}

	private VBox buildRightPanel() {
		Label cyclesHeader = new Label(DependencyGraphTexts.get("cyclesDetected"));
		cyclesHeader.getStyleClass().add("sv-dep-heading");

		Label statsHeader = new Label(DependencyGraphTexts.get("statistics"));
		statsHeader.getStyleClass().add("sv-dep-heading");

		this.statsComponentsValue = new Label("0");
		this.statsDependenciesValue = new Label("0");
		this.statsCyclesValue = new Label("0");
		this.statsIsolatedValue = new Label("0");

		GridPane statsGrid = new GridPane();
		statsGrid.setHgap(12);
		statsGrid.setVgap(6);
		ColumnConstraints labelColumn = new ColumnConstraints();
		ColumnConstraints valueColumn = new ColumnConstraints();
		valueColumn.setHgrow(Priority.ALWAYS);
		valueColumn.setHalignment(HPos.RIGHT);
		statsGrid.getColumnConstraints().addAll(labelColumn, valueColumn);
		statsGrid.addRow(0, new Label(DependencyGraphTexts.get("stats.components")), this.statsComponentsValue);
		statsGrid.addRow(1, new Label(DependencyGraphTexts.get("stats.dependencies")), this.statsDependenciesValue);
		statsGrid.addRow(2, new Label(DependencyGraphTexts.get("stats.cycles")), this.statsCyclesValue);
		statsGrid.addRow(3, new Label(DependencyGraphTexts.get("stats.isolated")), this.statsIsolatedValue);

		VBox statsBox = new VBox(6, statsHeader, statsGrid);

		VBox rightPanel = new VBox(10, cyclesHeader, this.cyclesList, new Separator(), statsBox);
		rightPanel.setPadding(new Insets(12));
		rightPanel.setMinWidth(240);
		rightPanel.getStyleClass().add("sv-dep-side");
		VBox.setVgrow(this.cyclesList, Priority.ALWAYS);
		return rightPanel;
	}

	private HBox buildStatusBar() {
		this.statusLeftFlow = new TextFlow();
		this.statusZoomLabel = new Label(DependencyGraphTexts.get("zoom", 100));
		this.statusZoomLabel.getStyleClass().add("sv-dep-status");

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);

		HBox statusBar = new HBox(8, this.statusLeftFlow, spacer, this.statusZoomLabel);
		statusBar.setPadding(new Insets(6, 12, 6, 12));
		statusBar.setAlignment(Pos.CENTER_LEFT);
		statusBar.getStyleClass().add("sv-dep-statusbar");
		return statusBar;
	}

	/** One "CYCLES DETECTED" card: colored dot, name, component-count badge, indented chain. */
	private Node buildCycleCard(final List<Class<?>> cycle, final int index) {
		Color color = CYCLE_PALETTE[index % CYCLE_PALETTE.length];

		Circle dot = new Circle(5, color);
		Label title = new Label(DependencyGraphTexts.get("cycle", index + 1));
		title.getStyleClass().add("sv-dep-card-title");
		Label countBadge = new Label(DependencyGraphTexts.count("components", cycle.size()));
		countBadge.setStyle("-fx-background-color: " + toRgba(color, 0.18) + "; -fx-text-fill: " + toWebColor(color)
				+ "; -fx-background-radius: 10; -fx-padding: 2 8 2 8; -fx-font-size: 11px;");
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox header = new HBox(6, dot, title, spacer, countBadge);
		header.setAlignment(Pos.CENTER_LEFT);

		VBox chain = new VBox(2);
		for (int i = 0; i < cycle.size(); i++) {
			String prefix = i == 0 ? "" : "  ".repeat(i) + "└ ";
			chain.getChildren().add(this.monospaceLine(prefix + cycle.get(i).getSimpleName()));
		}
		if (!cycle.isEmpty()) {
			chain.getChildren().add(this.monospaceLine("  ".repeat(cycle.size()) + "└ " + cycle.get(0).getSimpleName()));
		}

		VBox card = new VBox(8, header, chain);
		card.setPadding(new Insets(10));
		card.setStyle("-fx-background-color: " + toRgba(color, 0.10) + "; -fx-background-radius: 8; -fx-border-color: "
				+ toRgba(color, 0.35) + "; -fx-border-radius: 8; -fx-border-width: 1;");
		return card;
	}

	private Label monospaceLine(final String text) {
		Label label = new Label(text);
		label.getStyleClass().add("sv-dep-mono");
		return label;
	}

	private HBox buildLegendRow(final Color color, final String label) {
		Line swatch = new Line(0, 0, 18, 0);
		swatch.setStroke(color);
		swatch.setStrokeWidth(2);
		swatch.getStrokeDashArray().setAll(5.0, 3.0);
		Label text = new Label(label);
		text.getStyleClass().add("sv-dep-small");
		HBox row = new HBox(6, swatch, text);
		row.setAlignment(Pos.CENTER_LEFT);
		return row;
	}

	/** The composition links' legend: a diamond, a solid line, then an open arrowhead. */
	private HBox buildCompositionLegendRow() {
		Polygon diamond = new Polygon(0, 4, 5, 0, 10, 4, 5, 8);
		diamond.setFill(COMPOSITION_COLOR);
		Line swatch = new Line(0, 0, 10, 0);
		swatch.setStroke(COMPOSITION_COLOR);
		swatch.setStrokeWidth(2);
		Polyline arrow = new Polyline(0, 0, 4, 4, 0, 8);
		arrow.setStroke(COMPOSITION_COLOR);
		arrow.setStrokeWidth(1.5);
		arrow.setFill(null);
		HBox symbol = new HBox(diamond, swatch, arrow);
		HBox.setMargin(arrow, new Insets(0, 0, 0, -4));
		symbol.setAlignment(Pos.CENTER_LEFT);
		Label text = new Label(DependencyGraphTexts.get("composition"));
		text.getStyleClass().add("sv-dep-small");
		HBox row = new HBox(6, symbol, text);
		row.setAlignment(Pos.CENTER_LEFT);
		return row;
	}

	private static String toWebColor(final Color color) {
		return String.format("#%02x%02x%02x", Math.round(color.getRed() * 255), Math.round(color.getGreen() * 255),
				Math.round(color.getBlue() * 255));
	}

	private static String toRgba(final Color color, final double alpha) {
		return String.format(Locale.ROOT, "rgba(%d,%d,%d,%.2f)", Math.round(color.getRed() * 255), Math.round(color.getGreen() * 255),
				Math.round(color.getBlue() * 255), alpha);
	}

	// --- DependencyManagementViewServices ---

	@Override
	public void showView(final ViewKey key) {
		this.ensureContentBuilt();
		Platform.runLater(() -> {
			this.useHostingServices.openPanel(key, this.root, this.buildTabDescriptor());
			this.requestGraphViewportFit();
		});
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
				return RailEntry.GRAPH;
			}

			@Override
			public String title() {
				return DependencyGraphTexts.get("title");
			}

			@Override
			public EnumSet<ToolbarFunction> supportedToolbarFunctions() {
				return EnumSet.of(ToolbarFunction.ZOOM_SLIDER);
			}

			@Override
			public ActionKey zoomSetScaleActionKey() {
				return DependencyManagementActionKey.ZoomSetScale;
			}

			@Override
			public ActionKey zoomCurrentScaleActionKey() {
				return DependencyManagementActionKey.ZoomCurrentScale;
			}
		};
	}

	@Override
	public void recenter() {
		FxThread.run(() -> {
			this.requestGraphViewportFit();
		});
	}

	@Override
	public void setZoomScale(final double scale) {
		FxThread.run(() -> {
			Point2D pivot = Graph.sceneCenterOf(this.graphViewContainer);
			this.graph.getViewportGestures().zoomTo(scale, pivot.getX(), pivot.getY());
		});
	}

	@Override
	public void exportSnapshot() {
		FxThread.run(() -> {
			this.graph.getEdgeRouting().layoutNow();
			// Cropped to the components, over the theme's background.
			Snapshots.saveAndCopy(Snapshots.captureContent(this.graph.getCanvas(), 16), "deps");
		});
	}

	private DoubleProperty zoomScaleProperty() {
		return this.graph.getCanvas().scaleProperty();
	}

	// --- DependencyManagementEvents ---

	@Override
	public void onGraphChanged() {
		Platform.runLater(() -> {
			this.ensureContentBuilt();
			// The current graph, whatever order the notifications arrive in.
			this.fullGraph = this.useDependencyManagementModelReadServices.getGraph();
			if (this.scopeRoot != null && !this.fullGraph.getComponents().contains(this.scopeRoot)) {
				this.scopeRoot = null;
			}
			this.rebuildTree();
			this.showScope();
		});
	}

	private void rebuildGraph(final DependencyManagementGraph graph) {
		this.componentItems.clear();
		this.componentTypeByCell.clear();
		this.dependencyEdgeByPair.clear();
		this.baseEdgeColor.clear();
		this.compositionEdgeByPair.clear();
		this.baseCompositionColor.clear();
		this.manuallyHiddenLinks.clear();
		this.hiddenForWantOfLink.clear();
		this.focusedComponent = null;
		this.currentGraph = graph;
		this.unavailableLabel.setVisible(!graph.isAvailable());
		this.unavailableLabel.setManaged(!graph.isAvailable());
		// A new graph replaces the previous one as a whole: no cell of it is left to lay out.
		this.model.clear();
		this.graph.beginUpdate();

		List<List<Class<?>>> cycles = graph.getCircularDependencies();
		Map<Class<?>, Integer> cycleIndexByComponent = new HashMap<>();
		for (int i = 0; i < cycles.size(); i++) {
			for (Class<?> componentType : cycles.get(i)) {
				cycleIndexByComponent.putIfAbsent(componentType, i);
			}
		}

		for (Class<?> componentType : Ordering.byName(graph.getComponents())) {
			ClassCell cell = new ClassCell(componentType.getSimpleName(), null, componentType.getName());
			ComponentKind kind = graph.getKind(componentType);
			cell.setComposite(kind == ComponentKind.COMPOSITE);
			cell.setRaw(kind == ComponentKind.RAW);
			cell.onClick(() -> this.onComponentClicked(componentType));
			cell.onContextMenuRequested(event -> this.handleComponentContextMenu(componentType, event));
			this.model.addCell(cell);
			this.componentItems.put(componentType, cell);
			this.componentTypeByCell.put(cell, componentType);
		}

		Map<DependencyEdge, Color> edgeColors = new LinkedHashMap<>();
		Ordering.entriesByName(graph.getDependencies()).forEach(entry -> {
			Class<?> componentType = entry.getKey();
			ClassCell fromCell = this.componentItems.get(componentType);
			if (fromCell == null) {
				return;
			}
			for (Class<?> dependency : Ordering.byName(entry.getValue())) {
				ClassCell toCell = this.componentItems.get(dependency);
				if (toCell != null) {
					DependencyEdge edge = new DependencyEdge(fromCell, toCell);
					this.model.addEdge(edge);
					this.dependencyEdgeByPair.put(List.of(componentType, dependency), edge);
					Integer cycleIndex = cycleIndexOfDependency(cycles, graph.getCompositions(), componentType,
							dependency);
					Color color = cycleIndex == null ? NEUTRAL_COLOR : CYCLE_PALETTE[cycleIndex % CYCLE_PALETTE.length];
					edgeColors.put(edge, color);
					this.baseEdgeColor.put(edge, color);
				}
			}
		});

		// Composition links, from each composite to its children: drawn apart from the
		// dependencies, and shown or hidden with the toolbox's composition toggle.
		this.compositionEdges.clear();
		Map<DependencyEdge, Color> compositionColors = new HashMap<>();
		Ordering.entriesByName(graph.getCompositions()).forEach(entry -> {
			Class<?> composite = entry.getKey();
			ClassCell compositeCell = this.componentItems.get(composite);
			if (compositeCell == null) {
				return;
			}
			for (Class<?> child : Ordering.byName(entry.getValue())) {
				ClassCell childCell = this.componentItems.get(child);
				if (childCell != null) {
					DependencyEdge edge = new DependencyEdge(compositeCell, childCell);
					edge.setVisible(this.compositionToggle.isSelected());
					this.model.addEdge(edge);
					this.compositionEdges.add(edge);
					this.compositionEdgeByPair.put(List.of(composite, child), edge);
					Integer cycleIndex = cycleIndexOfStep(cycles, composite, child);
					Color color = cycleIndex == null ? COMPOSITION_COLOR : CYCLE_PALETTE[cycleIndex % CYCLE_PALETTE.length];
					compositionColors.put(edge, color);
					this.baseCompositionColor.put(edge, color);
				}
			}
		});

		this.graph.endUpdate();
		this.layoutGraph();

		this.styleComponents(cycleIndexByComponent);
		this.styleEdges(edgeColors);
		// A dependency both ways: its two links side by side, each with its own arrowhead.
		this.dependencyEdgeByPair.forEach((pair, edge) -> {
			if (this.dependencyEdgeByPair.containsKey(List.of(pair.get(1), pair.get(0)))
					&& this.graph.getGraphic(edge) instanceof DependencyEdge.EdgeGraphic graphic) {
				graphic.setParallelOffset(MUTUAL_LINK_OFFSET);
			}
		});
		for (DependencyEdge edge : this.compositionEdges) {
			if (this.graph.getGraphic(edge) instanceof DependencyEdge.EdgeGraphic graphic) {
				graphic.setComposition(compositionColors.get(edge));
			}
		}
		this.wireLinkContextMenus();
		this.refreshEdgeVisibility();

		this.cyclesList.setItems(FXCollections.observableArrayList(cycles));
		this.updateStatistics(graph);
		this.updateLegend(cycles, !graph.getCompositions().isEmpty());
		this.updateStatusBar(graph);
		this.updateScopeChrome();
		this.requestGraphViewportFit();
	}

	/** Every link's own context menu - both the dependencies and the composition links. */
	private void wireLinkContextMenus() {
		List<DependencyEdge> allLinks = new ArrayList<>(this.dependencyEdgeByPair.values());
		allLinks.addAll(this.compositionEdges);
		for (DependencyEdge edge : allLinks) {
			Class<?> sourceType = edge.getSource() instanceof ClassCell sourceCell
					? this.componentTypeByCell.get(sourceCell) : null;
			Class<?> targetType = edge.getTarget() instanceof ClassCell targetCell
					? this.componentTypeByCell.get(targetCell) : null;
			edge.onContextMenuRequested(event -> {
				this.handleLinkContextMenu(edge, sourceType, targetType, event);
				event.consume();
			});
		}
	}

	/**
	 * Index of the first cycle with the step {@code from -> to}, the cycle
	 * closing from its last component back to its first; {@code null} when
	 * no cycle has that step.
	 */
	private static Integer cycleIndexOfStep(final List<List<Class<?>>> cycles, final Class<?> from,
			final Class<?> to) {
		for (int i = 0; i < cycles.size(); i++) {
			List<Class<?>> cycle = cycles.get(i);
			for (int j = 0; j < cycle.size(); j++) {
				if (cycle.get(j).equals(from) && cycle.get((j + 1) % cycle.size()).equals(to)) {
					return i;
				}
			}
		}
		return null;
	}

	/**
	 * Index of the first cycle that the dependency {@code from -> dependency}
	 * takes part in: a cycle with a step {@code from -> Y} where
	 * {@code dependency} is {@code Y} itself or one of its composed
	 * descendants, as a cycle steps over the composite holding the component
	 * depended on; {@code null} when there is none.
	 */
	private static Integer cycleIndexOfDependency(final List<List<Class<?>>> cycles,
			final Map<Class<?>, Set<Class<?>>> compositions, final Class<?> from, final Class<?> dependency) {
		Map<Class<?>, Class<?>> parents = new HashMap<>();
		compositions.forEach((composite, children) -> children.forEach(child -> parents.putIfAbsent(child, composite)));
		for (int i = 0; i < cycles.size(); i++) {
			List<Class<?>> cycle = cycles.get(i);
			for (int j = 0; j < cycle.size(); j++) {
				if (!cycle.get(j).equals(from)) {
					continue;
				}
				Class<?> next = cycle.get((j + 1) % cycle.size());
				for (Class<?> current = dependency; current != null; current = parents.get(current)) {
					if (current.equals(next)) {
						return i;
					}
				}
			}
		}
		return null;
	}

	/** Marks the cards of a cycle with a left accent bar in the cycle's color. */
	private void styleComponents(final Map<Class<?>, Integer> cycleIndexByComponent) {
		this.componentItems.forEach((componentType, cell) -> {
			Integer cycleIndex = cycleIndexByComponent.get(componentType);
			if (cycleIndex == null) {
				return;
			}
			Region accent = new Region();
			accent.setMinWidth(4);
			accent.setPrefWidth(4);
			accent.setMaxWidth(4);
			accent.setStyle("-fx-background-color: " + toWebColor(CYCLE_PALETTE[cycleIndex % CYCLE_PALETTE.length])
					+ "; -fx-background-radius: 2;");

			Region cellGraphic = this.graph.getGraphic(cell);
			if (cellGraphic instanceof BorderPane) {
				((BorderPane) cellGraphic).setLeft(accent);
				BorderPane.setMargin(accent, new Insets(10, 8, 10, 0));
			}
		});
	}

	/** Dashes every edge and colors it by cycle membership. */
	private void styleEdges(final Map<DependencyEdge, Color> edgeColors) {
		edgeColors.forEach((edge, color) -> {
			if (this.graph.getGraphic(edge) instanceof DependencyEdge.EdgeGraphic graphic) {
				graphic.setStroke(color, true);
			}
		});
	}

	private void updateStatistics(final DependencyManagementGraph graph) {
		int components = graph.getComponents().size();
		int dependencies = graph.getDependencies().values().stream().mapToInt(Set::size).sum();
		int cyclesCount = graph.getCircularDependencies().size();

		Set<Class<?>> withOutgoing = new HashSet<>();
		Set<Class<?>> withIncoming = new HashSet<>();
		graph.getDependencies().forEach((componentType, dependsOn) -> {
			if (!dependsOn.isEmpty()) {
				withOutgoing.add(componentType);
			}
			withIncoming.addAll(dependsOn);
		});
		// A composite structures its children: neither is isolated.
		graph.getCompositions().forEach((composite, children) -> {
			withOutgoing.add(composite);
			withIncoming.addAll(children);
		});
		long isolated = graph.getComponents().stream()
				.filter(componentType -> !withOutgoing.contains(componentType) && !withIncoming.contains(componentType))
				.count();

		this.statsComponentsValue.setText(String.valueOf(components));
		this.statsDependenciesValue.setText(String.valueOf(dependencies));
		this.statsCyclesValue.setText(String.valueOf(cyclesCount));
		this.statsCyclesValue.setStyle(cyclesCount > 0 ? "-fx-text-fill: " + toWebColor(CYCLE_PALETTE[0]) + ";" : "");
		this.statsIsolatedValue.setText(String.valueOf(isolated));
	}

	private void updateLegend(final List<List<Class<?>>> cycles, final boolean compositions) {
		this.legendContent.getChildren().clear();
		for (int i = 0; i < cycles.size(); i++) {
			Color color = CYCLE_PALETTE[i % CYCLE_PALETTE.length];
			this.legendContent.getChildren()
					.add(this.buildLegendRow(color, DependencyGraphTexts.get("cycle.legend", i + 1, DependencyGraphTexts.count("components", cycles.get(i).size()))));
		}
		this.legendContent.getChildren().add(this.buildLegendRow(NEUTRAL_COLOR, DependencyGraphTexts.get("simpleDependency")));
		if (compositions) {
			this.legendContent.getChildren().add(this.buildCompositionLegendRow());
		}
	}

	private void updateStatusBar(final DependencyManagementGraph graph) {
		int components = graph.getComponents().size();
		int dependencies = graph.getDependencies().values().stream().mapToInt(Set::size).sum();
		int cyclesCount = graph.getCircularDependencies().size();

		Text componentsText = new Text(DependencyGraphTexts.count("components", components));
		Text separator1 = new Text("  ·  ");
		Text dependenciesText = new Text(DependencyGraphTexts.count("dependencies", dependencies));
		Text separator2 = new Text("  ·  ");
		Text cyclesText = new Text(DependencyGraphTexts.count("cycles", cyclesCount));
		for (Text text : new Text[] { componentsText, separator1, dependenciesText, separator2 }) {
			text.getStyleClass().add("sv-dep-status-text");
		}
		cyclesText.getStyleClass().add("sv-dep-status-text");
		cyclesText.setStyle(cyclesCount > 0 ? "-fx-fill: " + toWebColor(CYCLE_PALETTE[0]) + "; -fx-font-weight: bold;" : "");

		this.statusLeftFlow.getChildren().setAll(componentsText, separator1, dependenciesText, separator2, cyclesText);
		if (this.scopeRoot != null) {
			Text scope = new Text(DependencyGraphTexts.get("scope", this.scopeRoot.getSimpleName()) + "  ·  ");
			scope.getStyleClass().add("sv-dep-status-text");
			this.statusLeftFlow.getChildren().add(0, scope);
			Set<Class<?>> subtree = DependencyScope.subtree(this.fullGraph.getCompositions(), this.scopeRoot);
			String outside = this.neighborsToggle.isSelected()
					? DependencyGraphTexts.count("neighbors", DependencyScope.outsideNeighbors(this.fullGraph, subtree).size())
					: DependencyGraphTexts.count("outsideLinks", DependencyScope.outsideLinks(this.fullGraph, subtree));
			Text outsideText = new Text("  ·  " + outside);
			outsideText.getStyleClass().add("sv-dep-status-text");
			this.statusLeftFlow.getChildren().add(outsideText);
		}
	}

	// --- Component tree and scope ---

	/** The left panel: a search field and the component tree, folding like the component diagram's. */
	private VBox buildTreePanel() {
		TreeItem<Class<?>> containerItem = new TreeItem<>(ContainerRoot.class);
		containerItem.setExpanded(true);
		this.componentTree = new TreeView<>(containerItem);
		this.componentTree.setShowRoot(true);
		this.componentTree.setMinWidth(180);
		this.componentTree.setFixedCellSize(22);
		this.componentTree.getStyleClass().add("sv-component-tree");
		this.componentTree.setCellFactory(view -> this.buildTreeCell());
		this.treeSearchField = new TextField();
		this.treeSearchField.setPromptText(DependencyGraphTexts.get("tree.search.prompt"));
		this.treeSearchField.textProperty().addListener((observable, oldValue, newValue) -> this.rebuildTree());
		VBox treePanel = new VBox(4, this.treeSearchField, this.componentTree);
		treePanel.setPadding(new Insets(4, 0, 0, 4));
		treePanel.setMaxWidth(384);
		VBox.setVgrow(this.componentTree, Priority.ALWAYS);
		return treePanel;
	}

	/**
	 * One row of the tree: composite or leaf glyph, name, and a composite's number of descendants.
	 * The first row stands for the whole container, named after it. A click on it shows the whole
	 * graph; on a composite, scopes the graph to it; on a leaf, centers it - scoping the graph to
	 * its composite first when it is not shown. The triangle only opens or closes the row.
	 */
	private TreeCell<Class<?>> buildTreeCell() {
		TreeCell<Class<?>> cell = new TreeCell<>() {
			@Override
			protected void updateItem(final Class<?> type, final boolean empty) {
				super.updateItem(type, empty);
				if (empty || type == null) {
					this.setText(null);
					this.setGraphic(null);
					return;
				}
				boolean container = type == ContainerRoot.class;
				boolean composite = container || DependencyManagementViewImpl.this.isComposite(type);
				Node glyph;
				if (composite) {
					Rectangle square = new Rectangle(9, 9);
					square.setArcWidth(3);
					square.setArcHeight(3);
					glyph = square;
				} else {
					glyph = new Circle(4.5);
				}
				glyph.getStyleClass().add("sv-dep-tree-glyph");
				Label name = new Label(container ? DependencyManagementViewImpl.this.containerLabel() : type.getSimpleName());
				HBox row = new HBox(6, glyph, name);
				row.setAlignment(Pos.CENTER_LEFT);
				if (composite && !container) {
					Label count = new Label(String.valueOf(DependencyScope.subtree(
							DependencyManagementViewImpl.this.fullGraph.getCompositions(), type).size() - 1));
					count.getStyleClass().add("sv-dep-tree-count");
					row.getChildren().add(count);
				}
				this.setText(null);
				this.setGraphic(row);
			}
		};
		cell.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
			Class<?> type = cell.getItem();
			if (type == null || event.getButton() != MouseButton.PRIMARY
					|| isWithin(event.getPickResult().getIntersectedNode(), cell.getDisclosureNode())) {
				return;
			}
			this.onTreeRowClicked(type);
		});
		return cell;
	}

	/** The name the tree's first row bears: the observed container's, or "Whole graph" when none is known. */
	private String containerLabel() {
		String name = this.fullGraph.getContainerName();
		return name.isEmpty() ? DependencyGraphTexts.get("breadcrumb.whole") : name;
	}

	private void onTreeRowClicked(final Class<?> type) {
		if (type == ContainerRoot.class) {
			this.setScope(null);
		} else if (this.isComposite(type)) {
			this.setScope(type);
			if (this.componentItems.containsKey(type)) {
				this.applyDependencyHighlight(type);
			}
		} else if (this.componentItems.containsKey(type)) {
			this.goToComponent(type);
		} else {
			List<Class<?>> path = DependencyScope.path(this.fullGraph.getCompositions(), type);
			this.pendingCenter = type;
			this.setScope(path.size() > 1 ? path.get(path.size() - 2) : null);
		}
	}

	private boolean isComposite(final Class<?> type) {
		return !this.fullGraph.getCompositions().getOrDefault(type, Set.of()).isEmpty();
	}

	private void setScope(final Class<?> root) {
		this.scopeRoot = root;
		this.showScope();
	}

	/**
	 * Shows the part of the graph the tree scopes it to, with or without its outside neighbors.
	 * Leaving a scope keeps how it was left - moved components, zoom and offset - and coming back
	 * to it shows it so, as the component diagram does with its levels; a component that was not in
	 * the scope when it was left is placed by the layout.
	 */
	private void showScope() {
		if (this.neighborsToggle == null) {
			return;
		}
		if (this.shownScope != null && !this.componentItems.isEmpty()) {
			this.scopeLayouts.put(this.shownScope, new ScopeLayout(this.getPositions(), this.graph.getCanvas().getScale(),
					this.graph.getCanvas().getTranslateX(), this.graph.getCanvas().getTranslateY()));
		}
		List<Object> previous = this.shownScope;
		this.shownScope = List.of(this.scopeRoot == null ? WHOLE_GRAPH : this.scopeRoot,
				this.neighborsToggle.isSelected(), this.compositionToggle.isSelected());
		this.rebuildGraph(DependencyScope.scope(this.fullGraph, this.scopeRoot, this.neighborsToggle.isSelected(),
				this.compositionToggle.isSelected()));
		ScopeLayout left = this.scopeLayouts.get(this.shownScope);
		if (left != null && this.graph.getCanvas().getScene() != null) {
			// Back to a scope, or the one shown rebuilt: its components where they were left. Back
			// to it, the view is as it was left; rebuilt in place, it stays as it is.
			this.graphViewportFitPending = false;
			this.componentItems.forEach((type, cell) -> {
				double[] xy = left.positions().get(type);
				if (xy != null) {
					this.graph.getGraphic(cell).relocate(xy[0], xy[1]);
				}
			});
			if (!this.shownScope.equals(previous)) {
				this.graph.getCanvas().setScale(left.scale());
				this.graph.getCanvas().setTranslateX(left.translateX());
				this.graph.getCanvas().setTranslateY(left.translateY());
			}
			if (this.pendingCenter != null) {
				Class<?> toCenter = this.pendingCenter;
				this.pendingCenter = null;
				Platform.runLater(() -> this.goToComponent(toCenter));
			}
		}
	}

	private Map<Class<?>, double[]> getPositions() {
		Map<Class<?>, double[]> positions = new HashMap<>();
		this.componentItems.forEach((type, cell) -> {
			Region graphic = this.graph.getGraphic(cell);
			positions.put(type, new double[] { graphic.getLayoutX(), graphic.getLayoutY() });
		});
		return positions;
	}

	/** The breadcrumb, the toggle, the tree's selection and the outside neighbors' toned-down cards. */
	private void updateScopeChrome() {
		this.neighborsToggle.setDisable(this.scopeRoot == null);
		Set<Class<?>> subtree = this.scopeRoot == null ? null
				: DependencyScope.subtree(this.fullGraph.getCompositions(), this.scopeRoot);
		this.componentItems.forEach((type, cell) -> {
			boolean neighbor = subtree != null && !subtree.contains(type);
			cell.setNeighbor(neighbor);
			this.graph.getGraphic(cell).setOpacity(neighbor ? OUTSIDE_NEIGHBOR_OPACITY : 1);
		});
		this.renderBreadcrumb();
		TreeItem<Class<?>> item = this.scopeRoot == null ? this.componentTree.getRoot()
				: this.treeItemsByType.get(this.scopeRoot);
		if (item != null) {
			this.componentTree.getSelectionModel().select(item);
		}
	}

	/** "Whole graph", then each composite down to the scoped one, in bold; hidden for the whole graph. */
	private void renderBreadcrumb() {
		this.breadcrumbBar.getChildren().clear();
		boolean scoped = this.scopeRoot != null;
		this.breadcrumbBar.setVisible(scoped);
		this.breadcrumbBar.setManaged(scoped);
		if (!scoped) {
			return;
		}
		List<Class<?>> path = DependencyScope.path(this.fullGraph.getCompositions(), this.scopeRoot);
		Button up = Toolbox.iconButton(ICON_UP, DependencyGraphTexts.get("breadcrumb.up"));
		up.setOnAction(e -> this.setScope(path.size() > 1 ? path.get(path.size() - 2) : null));
		Hyperlink whole = new Hyperlink(DependencyGraphTexts.get("breadcrumb.whole"));
		whole.setOnAction(e -> this.setScope(null));
		this.breadcrumbBar.getChildren().addAll(up, whole);
		for (int i = 0; i < path.size(); i++) {
			this.breadcrumbBar.getChildren().add(new Label("›"));
			Class<?> level = path.get(i);
			if (i == path.size() - 1) {
				Label current = new Label(level.getSimpleName());
				current.getStyleClass().add("sv-breadcrumb-current");
				this.breadcrumbBar.getChildren().add(current);
			} else {
				Hyperlink link = new Hyperlink(level.getSimpleName());
				link.setOnAction(e -> this.setScope(level));
				this.breadcrumbBar.getChildren().add(link);
			}
		}
	}

	/**
	 * Rebuilds the tree from the whole graph's compositions, each level by name. The search field
	 * keeps the rows whose name holds its text, with their composites; while searching every row
	 * is open, otherwise the top level and the path down to the scoped composite are.
	 */
	private void rebuildTree() {
		if (this.componentTree == null) {
			return;
		}
		this.treeItemsByType.clear();
		Map<Class<?>, Set<Class<?>>> compositions = this.fullGraph.getCompositions();
		Set<Class<?>> children = new HashSet<>();
		compositions.values().forEach(children::addAll);
		String needle = this.treeSearchField.getText() == null ? ""
				: this.treeSearchField.getText().trim().toLowerCase(Locale.ROOT);
		List<TreeItem<Class<?>>> roots = new ArrayList<>();
		for (Class<?> type : byName(this.fullGraph.getComponents())) {
			if (!children.contains(type)) {
				TreeItem<Class<?>> item = this.buildTreeItem(type, compositions, needle, new HashSet<>());
				if (item != null) {
					roots.add(item);
				}
			}
		}
		this.componentTree.getRoot().getChildren().setAll(roots);
		Set<Class<?>> open = this.scopeRoot == null ? Set.of()
				: new HashSet<>(DependencyScope.path(compositions, this.scopeRoot));
		this.treeItemsByType.forEach((type, item) -> item.setExpanded(!needle.isEmpty() || open.contains(type)
				|| item.getParent() == this.componentTree.getRoot()));
		TreeItem<Class<?>> selected = this.scopeRoot == null ? this.componentTree.getRoot()
				: this.treeItemsByType.get(this.scopeRoot);
		if (selected != null) {
			this.componentTree.getSelectionModel().select(selected);
		}
		this.componentTree.refresh();
	}

	private TreeItem<Class<?>> buildTreeItem(final Class<?> type, final Map<Class<?>, Set<Class<?>>> compositions,
			final String needle, final Set<Class<?>> visiting) {
		if (!visiting.add(type)) {
			return null;
		}
		List<TreeItem<Class<?>>> matchingChildren = new ArrayList<>();
		for (Class<?> child : byName(compositions.getOrDefault(type, Set.of()))) {
			TreeItem<Class<?>> childItem = this.buildTreeItem(child, compositions, needle, visiting);
			if (childItem != null) {
				matchingChildren.add(childItem);
			}
		}
		visiting.remove(type);
		boolean matches = needle.isEmpty() || type.getSimpleName().toLowerCase(Locale.ROOT).contains(needle);
		if (!matches && matchingChildren.isEmpty()) {
			return null;
		}
		TreeItem<Class<?>> item = new TreeItem<>(type);
		item.getChildren().setAll(matchingChildren);
		this.treeItemsByType.put(type, item);
		return item;
	}

	/** Marks the tree's first row, which stands for the whole container rather than for a component. */
	private static final class ContainerRoot {
		private ContainerRoot() {
			// Only used as a marker.
		}
	}

	private static List<Class<?>> byName(final Set<Class<?>> types) {
		List<Class<?>> sorted = new ArrayList<>(types);
		sorted.sort(Comparator.comparing(Class::getSimpleName));
		return sorted;
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

	private void highlightCycle(final List<Class<?>> cycle) {
		for (ClassCell cell : this.componentItems.values()) {
			cell.setSelected(false);
			cell.setSelectedColor(null);
		}
		if (cycle != null) {
			int index = this.currentGraph.getCircularDependencies().indexOf(cycle);
			Color color = index < 0 ? null : CYCLE_PALETTE[index % CYCLE_PALETTE.length];
			for (Class<?> componentType : cycle) {
				ClassCell cell = this.componentItems.get(componentType);
				if (cell != null) {
					cell.setSelectedColor(color);
					cell.setSelected(true);
				}
			}
		}
	}

	// --- Click-to-highlight upstream/downstream, Structure actions, context menus ---

	/** A component's own click: focuses it (upstream/downstream highlight), or un-focuses it on a second click. */
	private void onComponentClicked(final Class<?> componentType) {
		if (componentType.equals(this.focusedComponent)) {
			this.clearDependencyHighlight();
		} else {
			this.applyDependencyHighlight(componentType);
		}
	}

	/** A click on the canvas background: drops the focused component's highlight and the selected cycle. */
	private void clearSelection() {
		this.clearDependencyHighlight();
		this.cyclesList.getSelectionModel().clearSelection();
	}

	private void clearDependencyHighlight() {
		this.focusedComponent = null;
		for (ClassCell cell : this.componentItems.values()) {
			cell.setFocused(false);
			cell.setHighlightColor(null);
		}
		this.styleEdges(this.baseEdgeColor);
		for (DependencyEdge edge : this.compositionEdges) {
			if (this.graph.getGraphic(edge) instanceof DependencyEdge.EdgeGraphic edgeGraphic) {
				edgeGraphic.setComposition(this.baseCompositionColor.get(edge));
			}
		}
	}

	/**
	 * Colors {@code componentType} itself, every component it directly
	 * depends on (upstream, {@link #UPSTREAM_COLOR}) and every component that
	 * directly depends on it (downstream, {@link #DOWNSTREAM_COLOR}), along with
	 * the one dependency or composition link that makes each of those direct
	 * steps - everything else, including anything only reachable through one
	 * more step, reverts to its plain cycle/neutral look. Deliberately not
	 * transitive: the chain beyond a direct neighbor is its own upstream/downstream to
	 * explore with its own click, one hop at a time.
	 */
	private void applyDependencyHighlight(final Class<?> componentType) {
		if (componentType == null || !this.componentItems.containsKey(componentType)) {
			return;
		}
		this.focusedComponent = componentType;
		Set<Class<?>> upstream = this.directDependencyDirectionTargets(componentType);
		Set<Class<?>> downstream = this.directDependents(componentType);

		this.componentItems.forEach((type, cell) -> {
			// The clicked component itself: the theme's focus look, from the stylesheet.
			cell.setFocused(type.equals(componentType));
			if (type.equals(componentType)) {
				cell.setHighlightColor(null);
			} else if (upstream.contains(type)) {
				cell.setHighlightColor(UPSTREAM_COLOR);
			} else if (downstream.contains(type)) {
				cell.setHighlightColor(DOWNSTREAM_COLOR);
			} else {
				cell.setHighlightColor(null);
			}
		});

		this.styleEdges(this.baseEdgeColor);
		this.colorDependencyDirectionSteps(componentType, upstream, downstream,
				(edge, color) -> {
					if (this.graph.getGraphic(edge) instanceof DependencyEdge.EdgeGraphic edgeGraphic) {
						edgeGraphic.setStroke(color, false);
					}
				}, this.dependencyEdgeByPair);

		for (DependencyEdge edge : this.compositionEdges) {
			if (this.graph.getGraphic(edge) instanceof DependencyEdge.EdgeGraphic edgeGraphic) {
				edgeGraphic.setComposition(this.baseCompositionColor.get(edge));
			}
		}
		this.colorDependencyDirectionSteps(componentType, upstream, downstream,
				(edge, color) -> {
					if (this.graph.getGraphic(edge) instanceof DependencyEdge.EdgeGraphic edgeGraphic) {
						edgeGraphic.setComposition(color);
					}
				}, this.compositionEdgeByPair);
	}

	/**
	 * Colors, among {@code edgeByPair} (either the dependency or the
	 * composition edges), the one link from {@code componentType} to each of
	 * its direct {@code upstream} targets, and the one link from each of its
	 * direct {@code downstream} dependents back to it - shared by both link
	 * kinds so a composition step highlights exactly like a dependency step
	 * does.
	 */
	private void colorDependencyDirectionSteps(final Class<?> componentType, final Set<Class<?>> upstream,
			final Set<Class<?>> downstream, final BiConsumer<DependencyEdge, Color> applyColor,
			final Map<List<Class<?>>, DependencyEdge> edgeByPair) {
		for (Class<?> to : upstream) {
			DependencyEdge edge = edgeByPair.get(List.of(componentType, to));
			if (edge != null) {
				applyColor.accept(edge, UPSTREAM_COLOR);
			}
		}
		for (Class<?> from : downstream) {
			DependencyEdge edge = edgeByPair.get(List.of(from, componentType));
			if (edge != null) {
				applyColor.accept(edge, DOWNSTREAM_COLOR);
			}
		}
	}

	/** Every component that directly depends on {@code componentType}, or directly composes it - "downstream". */
	private Set<Class<?>> directDependents(final Class<?> componentType) {
		Set<Class<?>> result = new LinkedHashSet<>();
		this.currentGraph.getDependencies().forEach((from, dependsOn) -> {
			if (dependsOn.contains(componentType)) {
				result.add(from);
			}
		});
		this.currentGraph.getCompositions().forEach((composite, children) -> {
			if (children.contains(componentType)) {
				result.add(composite);
			}
		});
		return result;
	}

	/** {@code componentType}'s direct dependencies plus, if it is a composite, its direct children - "upstream". */
	private Set<Class<?>> directDependencyDirectionTargets(final Class<?> componentType) {
		Set<Class<?>> targets = new LinkedHashSet<>(this.currentGraph.getDependencies().getOrDefault(componentType, Set.of()));
		targets.addAll(this.currentGraph.getCompositions().getOrDefault(componentType, Set.of()));
		return targets;
	}

	/**
	 * Every dependency and composition link's visibility: hidden when either
	 * end is hidden, when it was hidden one at a time from its own context
	 * menu, or - for a composition link - when the composition toggle itself
	 * is off.
	 */
	private void refreshEdgeVisibility() {
		this.refreshNeighborVisibility();
		for (DependencyEdge edge : this.dependencyEdgeByPair.values()) {
			this.applyEdgeVisibility(edge, true);
		}
		for (DependencyEdge edge : this.compositionEdges) {
			this.applyEdgeVisibility(edge, this.compositionToggle.isSelected());
		}
	}

	/**
	 * Hides an outside neighbor left without a visible dependency link to a visible component, and shows it
	 * again when one is back: a neighbor is there for a link only. A component hidden by hand stays hidden.
	 */
	private void refreshNeighborVisibility() {
		if (this.scopeRoot == null) {
			return;
		}
		Set<Class<?>> subtree = DependencyScope.subtree(this.fullGraph.getCompositions(), this.scopeRoot);
		this.componentItems.forEach((type, cell) -> {
			if (subtree.contains(type)) {
				return;
			}
			boolean linked = this.hasVisibleLink(type);
			if (!linked && cell.visibleProperty().get()) {
				cell.setVisible(false);
				this.hiddenForWantOfLink.add(type);
			} else if (linked && this.hiddenForWantOfLink.remove(type)) {
				cell.setVisible(true);
			}
		});
	}

	/** Whether a dependency link of {@code type} is not hidden by hand and ends on a visible component. */
	private boolean hasVisibleLink(final Class<?> type) {
		for (Map.Entry<List<Class<?>>, DependencyEdge> entry : this.dependencyEdgeByPair.entrySet()) {
			List<Class<?>> pair = entry.getKey();
			if (this.manuallyHiddenLinks.contains(entry.getValue())) {
				continue;
			}
			Class<?> other = pair.get(0).equals(type) ? pair.get(1) : pair.get(1).equals(type) ? pair.get(0) : null;
			ClassCell otherCell = other == null ? null : this.componentItems.get(other);
			if (otherCell != null && otherCell.visibleProperty().get()) {
				return true;
			}
		}
		return false;
	}

	private void applyEdgeVisibility(final DependencyEdge edge, final boolean allowedByKind) {
		boolean sourceVisible = !(edge.getSource() instanceof ClassCell sourceCell) || sourceCell.visibleProperty().get();
		boolean targetVisible = !(edge.getTarget() instanceof ClassCell targetCell) || targetCell.visibleProperty().get();
		edge.setVisible(allowedByKind && sourceVisible && targetVisible && !this.manuallyHiddenLinks.contains(edge));
	}

	private void showAllComponents() {
		for (ClassCell cell : this.componentItems.values()) {
			cell.setVisible(true);
		}
		this.manuallyHiddenLinks.clear();
		this.refreshEdgeVisibility();
	}

	private void hideAllComponents() {
		for (ClassCell cell : this.componentItems.values()) {
			cell.setVisible(false);
		}
		this.clearDependencyHighlight();
		this.refreshEdgeVisibility();
	}

	/** A no-op when {@code componentType} is null (nothing focused): used directly by the toolbox buttons. */
	private void hideComponent(final Class<?> componentType) {
		if (componentType == null) {
			return;
		}
		ClassCell cell = this.componentItems.get(componentType);
		if (cell != null) {
			cell.setVisible(false);
		}
		if (componentType.equals(this.focusedComponent)) {
			this.clearDependencyHighlight();
		}
		this.refreshEdgeVisibility();
	}

	/**
	 * Shows or hides every component {@code componentType} directly depends on, and its direct children if it is a
	 * composite. A no-op when it is null.
	 */
	private void setDependenciesVisible(final Class<?> componentType, final boolean visible) {
		if (componentType == null) {
			return;
		}
		for (Class<?> dependency : this.directDependencyDirectionTargets(componentType)) {
			ClassCell cell = this.componentItems.get(dependency);
			if (cell != null) {
				cell.setVisible(visible);
			}
		}
		this.refreshEdgeVisibility();
	}

	/**
	 * Any mouse press anywhere in the window closes this panel's open context
	 * menu, even when a gesture handler (panning, link click) consumes that
	 * press before the popup's own auto-hide sees it. Without this, a left
	 * click elsewhere on the canvas would leave the menu standing.
	 */
	private void installContextMenuDismissal(final Node canvas) {
		EventHandler<MouseEvent> hideOnPress = event -> this.hideContextMenu();
		canvas.sceneProperty().addListener((observable, oldScene, newScene) -> {
			if (oldScene != null) {
				oldScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, hideOnPress);
			}
			if (newScene != null) {
				newScene.addEventFilter(MouseEvent.MOUSE_PRESSED, hideOnPress);
			}
		});
		if (canvas.getScene() != null) {
			canvas.getScene().addEventFilter(MouseEvent.MOUSE_PRESSED, hideOnPress);
		}
	}

	/** Shows {@code menu} as this panel's only context menu - whichever one is still open is hidden first. */
	private void showContextMenu(final ContextMenu menu, final Node anchor, final double screenX, final double screenY) {
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

	/** A component's own context menu: Hide, Show dependencies, Hide dependencies - also focuses it. */
	private void handleComponentContextMenu(final Class<?> componentType, final ContextMenuEvent event) {
		this.applyDependencyHighlight(componentType);
		ContextMenu menu = new ContextMenu();
		MenuItem hide = new MenuItem(DependencyGraphTexts.get("menu.hide"));
		hide.setOnAction(e -> this.hideComponent(componentType));
		MenuItem showDeps = new MenuItem(DependencyGraphTexts.get("showDependencies"));
		showDeps.setOnAction(e -> this.setDependenciesVisible(componentType, true));
		MenuItem hideDeps = new MenuItem(DependencyGraphTexts.get("hideDependencies"));
		hideDeps.setOnAction(e -> this.setDependenciesVisible(componentType, false));
		menu.getItems().addAll(hide, showDeps, hideDeps);
		ClassCell cell = this.componentItems.get(componentType);
		this.showContextMenu(menu, this.graph.getGraphic(cell), event.getScreenX(), event.getScreenY());
		event.consume();
	}

	/** The canvas background's own context menu: Show all components, Recenter. */
	private ContextMenu buildBackgroundContextMenu() {
		ContextMenu menu = new ContextMenu();
		MenuItem showAll = new MenuItem(DependencyGraphTexts.get("menu.showAllComponents"));
		showAll.setOnAction(e -> this.showAllComponents());
		MenuItem recenter = new MenuItem(DependencyGraphTexts.get("recenter"));
		recenter.setOnAction(e -> this.recenter());
		menu.getItems().addAll(showAll, recenter);
		return menu;
	}

	/** A link's own context menu: Highlight, Go to source/target, Hide this link. */
	private void handleLinkContextMenu(final DependencyEdge edge, final Class<?> sourceType, final Class<?> targetType,
			final ContextMenuEvent event) {
		ContextMenu menu = new ContextMenu();
		MenuItem highlight = new MenuItem(DependencyGraphTexts.get("menu.highlight"));
		highlight.setOnAction(e -> this.applyDependencyHighlight(sourceType));
		MenuItem goToSource = new MenuItem(sourceType == null ? DependencyGraphTexts.get("menu.goToSource") : DependencyGraphTexts.get("menu.goToSourceNamed", sourceType.getSimpleName()));
		goToSource.setOnAction(e -> this.goToComponent(sourceType));
		MenuItem goToTarget = new MenuItem(targetType == null ? DependencyGraphTexts.get("menu.goToTarget") : DependencyGraphTexts.get("menu.goToTargetNamed", targetType.getSimpleName()));
		goToTarget.setOnAction(e -> this.goToComponent(targetType));
		MenuItem hideLink = new MenuItem(DependencyGraphTexts.get("menu.hideLink"));
		hideLink.setOnAction(e -> {
			this.manuallyHiddenLinks.add(edge);
			this.refreshEdgeVisibility();
		});
		menu.getItems().addAll(highlight, goToSource, goToTarget, new SeparatorMenuItem(), hideLink);
		ClassCell anchorCell = sourceType != null ? this.componentItems.get(sourceType) : this.componentItems.get(targetType);
		Node anchor = anchorCell != null ? this.graph.getGraphic(anchorCell) : this.graph.getCanvas();
		this.showContextMenu(menu, anchor, event.getScreenX(), event.getScreenY());
	}

	/** Focuses {@code componentType} (upstream/downstream highlight) and pans the view to center it, keeping the current zoom. */
	private void goToComponent(final Class<?> componentType) {
		if (componentType == null) {
			return;
		}
		this.applyDependencyHighlight(componentType);
		ClassCell cell = this.componentItems.get(componentType);
		if (cell == null || this.graphViewContainer == null) {
			return;
		}
		Region cellGraphic = this.graph.getGraphic(cell);
		Bounds cellSceneBounds = cellGraphic.localToScene(cellGraphic.getBoundsInLocal());
		// The viewport is the container's own area: its local bounds also cover the canvas wherever it
		// was panned to, so their center moves with the content.
		Bounds viewportSceneBounds = this.graphViewContainer.localToScene(this.graphViewContainer.getLayoutBounds());
		if (cellSceneBounds == null || viewportSceneBounds == null) {
			return;
		}
		double cellCenterX = (cellSceneBounds.getMinX() + cellSceneBounds.getMaxX()) / 2;
		double cellCenterY = (cellSceneBounds.getMinY() + cellSceneBounds.getMaxY()) / 2;
		double viewportCenterX = (viewportSceneBounds.getMinX() + viewportSceneBounds.getMaxX()) / 2;
		double viewportCenterY = (viewportSceneBounds.getMinY() + viewportSceneBounds.getMaxY()) / 2;
		this.graph.getCanvas().setTranslateX(this.graph.getCanvas().getTranslateX() + viewportCenterX - cellCenterX);
		this.graph.getCanvas().setTranslateY(this.graph.getCanvas().getTranslateY() + viewportCenterY - cellCenterY);
	}

	// --- Viewport fit (same math as ComponentDiagramViewImpl) ---

	private void requestGraphViewportFit() {
		this.graphViewportFitPending = true;
		this.fitGraphToViewportIfPending();
	}

	private void fitGraphToViewportIfPending() {
		if (!this.graphViewportFitPending) {
			return;
		}
		Platform.runLater(() -> {
			// A scope shown again as it was left cancels the fit asked for while it was rebuilt.
			if (this.graphViewportFitPending && this.fitGraphToViewportNow()) {
				this.graphViewportFitPending = false;
			}
		});
	}

	/**
	 * Places the cells once they can be measured: their labels only get a size
	 * after styling, which needs the canvas to be in a scene.
	 */
	private void layoutGraph() {
		if (this.graph.getCanvas().getScene() == null || this.model.getAllCells().isEmpty()) {
			return;
		}
		this.graph.getCanvas().applyCss();
		this.graph.getCanvas().layout();
		this.graph.layout(this.layout);
		this.requestGraphViewportFit();
	}

	private boolean fitGraphToViewportNow() {
		if (this.graphViewContainer == null || this.graph == null || this.model == null
				|| this.model.getAllCells().isEmpty()) {
			return false;
		}
		double viewportWidth = this.graphViewContainer.getWidth();
		double viewportHeight = this.graphViewContainer.getHeight();
		if (viewportWidth <= GRAPH_VIEW_MARGIN || viewportHeight <= GRAPH_VIEW_MARGIN) {
			return false;
		}

		this.graph.getCanvas().setTranslateX(0);
		this.graph.getCanvas().setTranslateY(0);
		this.graph.getCanvas().setScale(1.0);

		this.graph.getEdgeRouting().layoutNow();
		this.graph.getCanvas().applyCss();
		this.graph.getCanvas().layout();

		Bounds graphBounds = this.graph.getCanvas().getBoundsInParent();
		if (graphBounds.getWidth() <= 0 || graphBounds.getHeight() <= 0) {
			return false;
		}

		double scale = Math.min((viewportWidth - GRAPH_VIEW_MARGIN) / graphBounds.getWidth(),
				(viewportHeight - GRAPH_VIEW_MARGIN) / graphBounds.getHeight());
		scale = Math.max(this.graph.getViewportGestures().getMinScale(), Math.min(1.0, scale));
		this.graph.getCanvas().setScale(scale);

		Bounds scaledBounds = this.graph.getCanvas().getBoundsInParent();
		double targetCenterX = viewportWidth / 2;
		double targetCenterY = viewportHeight / 2;
		double graphCenterX = (scaledBounds.getMinX() + scaledBounds.getMaxX()) / 2;
		double graphCenterY = (scaledBounds.getMinY() + scaledBounds.getMaxY()) / 2;
		this.graph.getCanvas().setTranslateX(this.graph.getCanvas().getTranslateX() + targetCenterX - graphCenterX);
		this.graph.getCanvas().setTranslateY(this.graph.getCanvas().getTranslateY() + targetCenterY - graphCenterY);
		if (this.awaitingInitialReveal) {
			this.graph.getCanvas().setVisible(true);
			this.awaitingInitialReveal = false;
		}
		if (this.pendingCenter != null) {
			Class<?> toCenter = this.pendingCenter;
			this.pendingCenter = null;
			Platform.runLater(() -> this.goToComponent(toCenter));
		}
		return true;
	}
}
