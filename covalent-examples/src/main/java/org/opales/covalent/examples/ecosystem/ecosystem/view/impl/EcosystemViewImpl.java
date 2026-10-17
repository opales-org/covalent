/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.ecosystem.view.impl;

import java.util.LinkedHashMap;
import java.util.Map;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.examples.ecosystem.ecosystem.model.EcosystemStateEvents;
import org.opales.covalent.examples.ecosystem.ecosystem.view.EcosystemView;
import org.opales.covalent.examples.ecosystem.ecosystem.view.EcosystemViewParameters;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The ecosystem's View: keeps the latest known populations and renders
 * them as a live curve and a counts table. Consumes
 * {@link EcosystemStateEvents} directly: no Interaction Controller
 * mediates, since there is no user action to route back into
 * EcosystemController.
 * <p>
 * Subscribes manually, through
 * {@link EcosystemStateEvents.EcosystemStateEventsSubscribe}, rather than
 * through automatic autowiring: "Pause display" stops the reception while
 * the simulation keeps running, and resuming subscribes again, telling
 * the model which state this view last received and how many of the missed
 * states it wants back ("History depth"), so the curve catches up instead
 * of jumping. The pause is purely a matter of this view, hence handled
 * here rather than through an action.
 * <p>
 * Builds its JavaFX root synchronously at {@code @Activation}.
 */
@Implementation
public class EcosystemViewImpl implements EcosystemView, EcosystemViewParameters, EcosystemStateEvents {

    private static final int MAX_POINTS = 300;

    /** One row of the counts table: a species name and its current population. */
    public static final class PopulationRow {

        private final String species;
        private final double count;

        /**
         * Creates a row.
         * @param species the species name
         * @param count the current population of the species
         */
        public PopulationRow(String species, double count) {
            this.species = species;
            this.count = count;
        }

        /**
         * Returns the species name shown in the row.
         * @return the species name
         */
        public String getSpecies() {
            return species;
        }

        /**
         * Returns the population shown in the row.
         * @return the current population of the species
         */
        public double getCount() {
            return count;
        }
    }

    private volatile boolean active;

    /** See this class's javadoc. Declaring this field suppresses the automatic subscription. */
    @UsedEventsSubscribe
    private EcosystemStateEvents.EcosystemStateEventsSubscribe useStateEventsSubscribe;

    private volatile boolean paused;

    /**
     * The arguments of the current subscription: unsubscribing must repeat them, since they
     * identify the subscription.
     */
    private double subscribedSince;

    private int subscribedDepth;

    private CheckBox pauseCheckBox;

    private Spinner<Integer> historyDepthSpinner;

    private Node chartRoot;

    private Node countsRoot;

    private LineChart<Number, Number> chart;

    private final Map<String, XYChart.Series<Number, Number>> series = new LinkedHashMap<>();

    private double lastSimulationTime = -1.0;

    private TableView<PopulationRow> table;

    private final ObservableList<PopulationRow> rows = FXCollections.observableArrayList();

    /**
     * Builds a new root at each activation, with a new, empty chart. The
     * series of the previous chart are dropped with it: kept in
     * {@link #series}, new points would go to series no longer shown, and
     * the new chart would stay empty.
     */
    @Activation
    public void buildRoot() {
        this.series.clear();
        this.lastSimulationTime = -1.0;
        this.paused = false;
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Simulation time");
        // NumberAxis defaults to always including zero in its range.
        // Each series is capped to MAX_POINTS, so once the simulation
        // has run long enough to trim its oldest points, the actual
        // data only spans a recent, comparatively narrow window - with
        // zero forced into the range, that window keeps shrinking into
        // a sliver at the far right of the chart as simulation time
        // grows. Disabling it lets the axis track just the currently
        // visible window instead.
        xAxis.setForceZeroInRange(false);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Population");

        this.chart = new LineChart<>(xAxis, yAxis);
        this.chart.setTitle("Populations over time");
        this.chart.setCreateSymbols(false);
        this.chart.setAnimated(false);

        this.table = new TableView<>(this.rows);
        TableColumn<PopulationRow, String> speciesColumn = new TableColumn<>("Species");
        speciesColumn.setCellValueFactory(new PropertyValueFactory<>("species"));
        TableColumn<PopulationRow, Double> countColumn = new TableColumn<>("Count");
        countColumn.setCellValueFactory(new PropertyValueFactory<>("count"));
        this.table.getColumns().add(speciesColumn);
        this.table.getColumns().add(countColumn);
        this.table.setPrefHeight(130);
        this.table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        Label chartTitle = new Label("Ecosystem");
        chartTitle.getStyleClass().add("card-title");

        this.pauseCheckBox = new CheckBox("Pause display");
        this.pauseCheckBox.setOnAction(event -> setPaused(this.pauseCheckBox.isSelected()));
        this.historyDepthSpinner = new Spinner<>(0, MAX_POINTS, MAX_POINTS);
        this.historyDepthSpinner.setEditable(true);
        this.historyDepthSpinner.setPrefWidth(90);
        HBox pauseBar = new HBox(8, this.pauseCheckBox, new Label("History depth"), this.historyDepthSpinner);
        pauseBar.setAlignment(Pos.CENTER_LEFT);

        VBox chartCard = new VBox(4, chartTitle, this.chart, pauseBar);
        VBox.setVgrow(this.chart, Priority.ALWAYS);
        chartCard.getStyleClass().add("card");
        chartCard.setPadding(new Insets(6));

        Label countsTitle = new Label("Populations");
        countsTitle.getStyleClass().add("card-title");

        VBox countsCard = new VBox(4, countsTitle, this.table);
        countsCard.getStyleClass().add("card");
        countsCard.setPadding(new Insets(6));

        this.chartRoot = chartCard;
        this.countsRoot = countsCard;
        this.active = true;

        // Reproduces the automatic subscription this class would otherwise get (see this class's
        // javadoc): nothing received yet, so the model replays what it has, up to the depth shown.
        subscribeFrom(this.lastSimulationTime);
    }

    /**
     * Marks the view inactive, so that notifications still pending are ignored, and unsubscribes -
     * unless the display is paused, since it is then already unsubscribed (Covalent rejects
     * unsubscribing twice).
     */
    @Passivation
    public void deactivate() {
        this.active = false;
        if (!this.paused) {
            unsubscribe();
        }
    }

    /**
     * Stops or resumes the reception of {@link EcosystemStateEvents} while the simulation keeps
     * running. Resuming subscribes again, from the simulation time of the last state received, and
     * with the history depth currently entered.
     */
    private void setPaused(boolean paused) {
        if (paused == this.paused) {
            return;
        }
        this.paused = paused;
        if (paused) {
            unsubscribe();
        } else {
            subscribeFrom(this.lastSimulationTime);
        }
    }

    private void subscribeFrom(double since) {
        this.subscribedSince = since;
        this.subscribedDepth = historyDepth();
        this.useStateEventsSubscribe.subscribe(this, this.subscribedSince, this.subscribedDepth);
    }

    private void unsubscribe() {
        this.useStateEventsSubscribe.unsubscribe(this, this.subscribedSince, this.subscribedDepth);
    }

    /** The history depth entered, committing what is being typed, within 0 and the chart's capacity. */
    private int historyDepth() {
        Integer depth;
        try {
            this.historyDepthSpinner.increment(0);
            depth = this.historyDepthSpinner.getValue();
        } catch (RuntimeException invalidText) {
            depth = this.historyDepthSpinner.getValue();
        }
        return depth == null ? MAX_POINTS : Math.max(0, Math.min(MAX_POINTS, depth));
    }

    @Override
    public Node getChartRoot() {
        return this.chartRoot;
    }

    @Override
    public Node getCountsRoot() {
        return this.countsRoot;
    }

    @Override
    public void onPopulationsChanged(Map<String, Double> populations, double simulationTime) {
        Platform.runLater(() -> {
            if (!this.active) {
                return;
            }
            if (simulationTime < this.lastSimulationTime) {
                // Time went backwards: EcosystemController.reset() just
                // ran. Clear the previous run's curve instead of letting
                // the new one grow into it point by point.
                for (XYChart.Series<Number, Number> s : this.series.values()) {
                    s.getData().clear();
                }
            }
            this.lastSimulationTime = simulationTime;

            for (Map.Entry<String, Double> entry : populations.entrySet()) {
                XYChart.Series<Number, Number> s = this.series.computeIfAbsent(entry.getKey(), name -> {
                    XYChart.Series<Number, Number> created = new XYChart.Series<>();
                    created.setName(name);
                    this.chart.getData().add(created);
                    return created;
                });
                s.getData().add(new XYChart.Data<>(simulationTime, entry.getValue()));
                if (s.getData().size() > MAX_POINTS) {
                    s.getData().remove(0);
                }
            }

            this.rows.clear();
            for (Map.Entry<String, Double> entry : populations.entrySet()) {
                this.rows.add(new PopulationRow(entry.getKey(), entry.getValue()));
            }
        });
    }
}
