/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.hmibuilder.impl;

import org.opales.covalent.container.component.annotations.AccessedParameters;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.examples.ecosystem.ecosystem.view.EcosystemViewParameters;
import org.opales.covalent.examples.ecosystem.hmibuilder.HmiBuilder;
import org.opales.covalent.examples.ecosystem.simulationrunner.view.RunnerViewParameters;
import org.opales.covalent.examples.ecosystem.statsexport.StatsExportWidgetParameters;
import org.opales.covalent.examples.ecosystem.userconfiguration.view.UserConfigurationViewParameters;
import org.opales.covalent.examples.ecosystem.watchers.crosswatcher.view.CrossWatcherViewParameters;
import org.opales.covalent.examples.ecosystem.watchers.rolewatcher.view.RoleWatcherViewParameters;
import org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.view.WatchlistWatcherViewParameters;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Assembles the JavaFX scene graph for the whole demo out of each
 * function's already-built view root(s), read through their
 * *ViewParameters interfaces, plus a static "Help" menu of its own. Pure
 * assembly: no business logic, no domain event subscriptions. The views of
 * the three watchers (RoleWatcher, WatchlistWatcher and CrossWatcher) are
 * embedded one by one, not through a combined panel.
 * <p>
 * Layout is a grid of three columns and one row, under a top-level "Help"
 * {@code MenuBar} of this component's own.
 * </p>
 * <p>
 * The left column holds the configuration and the runner controls. The
 * center column holds the ecosystem chart, which takes the spare height,
 * above the populations counts table and the time readout. The right column
 * holds the watchlist table, the prey log, the predator log and the
 * cross-watcher table, followed by the raw StatsExportWidget's
 * checkbox-and-counter row stacked above its export log. The side columns
 * take the width their content needs; the center column takes the rest.
 * </p>
 * <p>
 * The grid's width is pinned at exactly the lesser of 1920 pixels and
 * the screen's visual width: its minimum, preferred and maximum width
 * are all set to that same value, so the grid cannot be pushed wider
 * by its own children. The center column's minimum width is pinned at
 * zero for the same reason - left at its default, a column's minimum
 * is derived from its children's natural size and can silently force
 * the whole grid wider than the cap, which is exactly what a child
 * such as the ecosystem chart would otherwise do. With the center
 * column free to shrink, each table still fills its own grid cell at
 * full width through its own {@code CONSTRAINED_RESIZE_POLICY}.
 * There is no scroll pane anywhere in this assembly: nothing here can
 * make the rendered content wider than the window actually is. The
 * grid is wrapped in a top-level {@code VBox} together with the menu
 * bar, which never affects this width cap: a {@code VBox} stretches
 * every child to its widest member, and the menu bar is always far
 * narrower than the capped grid.
 * </p>
 * <p>
 * The StatsExportWidget's two nodes are embedded through the ordinary
 * {@code @AccessedParameters} + {@code getXxxRoot()} pattern, exactly
 * like every other function's own *ViewParameters, even though its
 * offeror is a raw, not a managed, component. Only present when this
 * composite is deployed through {@code EcosystemStatsExportDemoLauncher},
 * which alone registers that raw component before this composite
 * deploys (the Ecosystem Demo row in the shared multi-demo launcher
 * uses it; see that launcher).
 * </p>
 */
@Implementation
public class HmiBuilderImpl implements HmiBuilder {

    private static final String APP_NAME = "Ecosystem Demo";

    private static final double MAX_WINDOW_WIDTH = 1920;

    private static final String STYLESHEET =
            HmiBuilderImpl.class.getResource("/org/opales/covalent/examples/theme/dark-theme.css").toExternalForm();

    private static final String ABOUT_TEXT = """
            Ecosystem Demo is a small population-dynamics simulation: Grass, \
            Prey and Predator populations interact through a stochastic, \
            tau-leaping generalized Lotka-Volterra model, with live population \
            curves showing the resulting growth, decline and oscillation. \
            Three watchers observe the same stream of birth/death/predation \
            notifications, each scoped to its own concern, and a raw component \
            mirrors the running population counts out to a CSV file.

            It illustrates several Covalent aspects side by side:

            - One interface, PopulationEvents, produced once by Ecosystem and \
            consumed independently by the three watchers inside Watchers, each \
            with its own event filter - the same broadcast stream partitioned \
            three different ways.

            - Subscribing an object instead of the consumer component: \
            RoleWatcher combines its filter with two small delegate instances, \
            one per role, so its own callback never branches on which role a \
            notification belongs to.

            - Raw components: a pair, StatsExporter and StatsExportWidget, \
            mirrors the running population counts to a CSV file from outside \
            the managed component graph entirely, wired by the demo's own \
            launcher rather than the ordinary deployComponents(...) sequence.

            - Composite components and a root composite, as in the Neural \
            Network Trainer and Solar System Simulation demos: every function is its own @Implementation wiring its parts \
            together, and the whole demo deploys as a single root composite, \
            EcosystemDemoImpl.

            - Actions routed exclusively through the HMI's ActionManager, \
            exactly as in the other demos.
            """;

    private static final String USAGE_TEXT = """
            The window has three columns.

            Left column, top (User configuration): a growth rate per species, the \
            simulation's time scale, and the watched-species sets for the \
            Watchlist and Cross-watcher watchers below. Every change takes \
            effect immediately.

            Each species' net rate of change at every step is its own growth \
            rate plus, for every other species, that other species' current \
            population times the pairwise interaction coefficient between the \
            two. Grass is the only species with a positive growth rate - it \
            grows on its own - and even it has a small negative \
            self-interaction coefficient, which caps how large it can grow \
            (a carrying capacity). Every Prey and Predator species instead has \
            a negative growth rate: left alone, it declines and eventually dies \
            out, and only turns net-positive through its interaction \
            coefficient with whatever it eats - a Prey species with a positive \
            coefficient toward Grass, a Predator species with a positive \
            coefficient toward its own prey. This is why a Predator's \
            population collapses once its prey is gone, rather than drifting \
            along on a growth rate of its own.

            Left column (run controls): Start/Pause/Step/Reset, exactly as in \
            Solar System Simulation, below the user configuration.

            Center column: a live chart of every species' population over \
            time, then a table of the current population counts and the \
            elapsed time.

            Right column: four watcher tables - a Watchlist table, a Prey log \
            and a Predator log (paired), and a Cross-watcher table - followed \
            by the Stats Export panel and its log.

            Right column, bottom (Stats Export): an "Export stats" checkbox that \
            starts or stops computing one population snapshot per second and \
            logging it below, and a separate "Save to CSV file" checkbox that \
            turns persisting those same snapshots to disk on or off - the \
            file itself is only created the first time saving is turned on. \
            A running count of lines processed sits next to the checkboxes, \
            and an Export Log table mirrors the exact header and data lines \
            as they are produced, whether or not saving is currently on.
            """;

    @AccessedParameters
    private EcosystemViewParameters ecosystemViewParameters;

    @AccessedParameters
    private RunnerViewParameters runnerViewParameters;

    @AccessedParameters
    private RoleWatcherViewParameters roleWatcherViewParameters;

    @AccessedParameters
    private WatchlistWatcherViewParameters watchlistWatcherViewParameters;

    @AccessedParameters
    private CrossWatcherViewParameters crossWatcherViewParameters;

    @AccessedParameters
    private UserConfigurationViewParameters userConfigurationViewParameters;

    @AccessedParameters
    private StatsExportWidgetParameters statsExportWidgetViewParameters;

    private Stage stage;

    /**
     * Lays out every function's view roots in a three-column grid (configuration and run controls,
     * chart and populations, watchers and stats export), under the Help menu bar, and shows the
     * application window on the JavaFX thread.
     */
    @Activation
    public void assembleAndShow() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(12));

        // The side columns take the width their content needs; the center column takes the rest.
        ColumnConstraints col0 = new ColumnConstraints();
        col0.setMinWidth(Region.USE_PREF_SIZE);
        col0.setHgrow(Priority.NEVER);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setMinWidth(0);
        col1.setHgrow(Priority.ALWAYS);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setMinWidth(Region.USE_PREF_SIZE);
        col2.setHgrow(Priority.NEVER);
        grid.getColumnConstraints().addAll(col0, col1, col2);

        RowConstraints row0 = new RowConstraints();
        row0.setPercentHeight(100);
        row0.setMinHeight(0);
        grid.getRowConstraints().add(row0);

        // Left column: configuration and run controls.
        VBox controls = new VBox(12, userConfigurationViewParameters.getRoot(),
                runnerViewParameters.getControlsRoot());

        // Center column: the chart (taking the spare height), the populations table and the time readout.
        VBox chartCell = new VBox(12, ecosystemViewParameters.getChartRoot(),
                ecosystemViewParameters.getCountsRoot(), runnerViewParameters.getReadoutRoot());
        VBox.setVgrow(ecosystemViewParameters.getChartRoot(), Priority.ALWAYS);

        // Right column: the watchers, then the stats export and its log.
        VBox watchersCell = new VBox(12, watchlistWatcherViewParameters.getRoot(),
                roleWatcherViewParameters.getPreyRoot(), roleWatcherViewParameters.getPredatorRoot(),
                crossWatcherViewParameters.getRoot(), statsExportWidgetViewParameters.getRoot(),
                statsExportWidgetViewParameters.getLogRoot());
        for (Node watcher : watchersCell.getChildren()) {
            VBox.setVgrow(watcher, Priority.ALWAYS);
        }
        VBox.setVgrow(statsExportWidgetViewParameters.getRoot(), Priority.NEVER);

        grid.add(controls, 0, 0);
        grid.add(chartCell, 1, 0);
        grid.add(watchersCell, 2, 0);
        GridPane.setVgrow(chartCell, Priority.ALWAYS);
        GridPane.setVgrow(watchersCell, Priority.ALWAYS);
        GridPane.setFillHeight(chartCell, true);
        GridPane.setFillHeight(watchersCell, true);

        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        double windowWidth = Math.min(MAX_WINDOW_WIDTH, visualBounds.getWidth());
        grid.setMinWidth(windowWidth);
        grid.setPrefWidth(windowWidth);
        grid.setMaxWidth(windowWidth);
        grid.setPrefHeight(900);

        VBox windowRoot = new VBox(this.buildMenuBar(), grid);
        windowRoot.getStyleClass().add("app-eco");
        VBox.setVgrow(grid, Priority.ALWAYS);

        Platform.runLater(() -> {
            this.stage = new Stage();
            this.stage.setTitle(APP_NAME);
            Scene scene = new Scene(windowRoot);
            scene.getStylesheets().add(STYLESHEET);
            this.stage.setScene(scene);
            this.stage.setMaxWidth(windowWidth);
            this.stage.sizeToScene();
            this.stage.centerOnScreen();
            this.stage.show();
        });
    }

    /** Closes the application window, if it is open, on the JavaFX thread. */
    @Passivation
    public void closeWindow() {
        Platform.runLater(() -> {
            if (this.stage != null) {
                this.stage.close();
                this.stage = null;
            }
        });
    }

    /**
     * Builds the top-level "Help" menu: "About {@value #APP_NAME}" (what
     * this application does, and which Covalent mechanisms it illustrates)
     * and "Using {@value #APP_NAME}" (how to operate its controls). Both
     * entries just show a static, read-only help window.
     *
     * @return the assembled menu bar, left-aligned by the default
     *         {@link MenuBar} behavior
     */
    private MenuBar buildMenuBar() {
        MenuItem about = new MenuItem("About " + APP_NAME);
        about.setOnAction(event -> this.showHelpDialog("About " + APP_NAME, ABOUT_TEXT));

        MenuItem usage = new MenuItem("Using " + APP_NAME);
        usage.setOnAction(event -> this.showHelpDialog("Using " + APP_NAME, USAGE_TEXT));

        Menu helpMenu = new Menu("Help");
        helpMenu.getItems().addAll(about, usage);

        return new MenuBar(helpMenu);
    }

    /**
     * Shows one help topic in its own small, non-modal window, so it can be
     * left open for reference while the simulation keeps running.
     *
     * @param title the help window's title
     * @param text  the help topic's fixed text
     */
    private void showHelpDialog(final String title, final String text) {
        TextArea textArea = new TextArea(text);
        textArea.setEditable(false);
        textArea.setWrapText(true);

        Stage dialog = new Stage();
        dialog.initOwner(this.stage);
        dialog.initModality(Modality.NONE);
        dialog.setTitle(title);
        Scene dialogScene = new Scene(textArea, 560, 440);
        dialogScene.getStylesheets().add(STYLESHEET);
        dialog.setScene(dialogScene);
        dialog.show();
    }
}
