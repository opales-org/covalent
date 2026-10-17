/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.crosswatcher.view.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.examples.ecosystem.watchers.crosswatcher.model.CrossWatcherEvents;
import org.opales.covalent.examples.ecosystem.watchers.crosswatcher.model.Predation;
import org.opales.covalent.examples.ecosystem.watchers.crosswatcher.view.CrossWatcherView;
import org.opales.covalent.examples.ecosystem.watchers.crosswatcher.view.CrossWatcherViewParameters;
import org.opales.covalent.examples.ecosystem.watchers.crosswatcher.view.CrossWatcherViewServices;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;

/**
 * CrossWatcher's View: a single table of logged predations. Consumes
 * CrossWatcherEvents directly.
 */
@Implementation
public class CrossWatcherViewImpl
        implements CrossWatcherView, CrossWatcherViewServices, CrossWatcherViewParameters, CrossWatcherEvents {

    /** One row of the predations table. */
    public static final class PredationRow {

        private final String predatorSpecies;
        private final String preySpecies;
        private final double simulationTime;

        /**
         * Creates a row.
         * @param predatorSpecies name of the consuming species
         * @param preySpecies name of the consumed species
         * @param simulationTime simulation time of the predation
         */
        public PredationRow(String predatorSpecies, String preySpecies, double simulationTime) {
            this.predatorSpecies = predatorSpecies;
            this.preySpecies = preySpecies;
            this.simulationTime = simulationTime;
        }

        /**
         * Returns the name of the consuming species.
         * @return the predator species
         */
        public String getPredatorSpecies() {
            return predatorSpecies;
        }

        /**
         * Returns the name of the consumed species.
         * @return the prey species
         */
        public String getPreySpecies() {
            return preySpecies;
        }

        /**
         * Returns the time of the predation.
         * @return the simulation time
         */
        public double getSimulationTime() {
            return simulationTime;
        }
    }

    /**
     * Caps how many rows this table keeps, so a long-running
     * simulation cannot grow this list without bound.
     */
    private static final int MAX_ROWS = 200;

    private volatile boolean active;

    private Node root;

    private final ObservableList<PredationRow> rows = FXCollections.observableArrayList();

    /** Builds the predations table card, then marks the view active. */
    @Activation
    public void buildRoot() {
        TableView<PredationRow> table = new TableView<>(rows);
        TableColumn<PredationRow, String> predatorColumn = new TableColumn<>("Predator");
        predatorColumn.setCellValueFactory(new PropertyValueFactory<>("predatorSpecies"));
        TableColumn<PredationRow, String> preyColumn = new TableColumn<>("Prey");
        preyColumn.setCellValueFactory(new PropertyValueFactory<>("preySpecies"));
        TableColumn<PredationRow, Double> timeColumn = new TableColumn<>("Time");
        timeColumn.setCellValueFactory(new PropertyValueFactory<>("simulationTime"));
        table.getColumns().add(predatorColumn);
        table.getColumns().add(preyColumn);
        table.getColumns().add(timeColumn);
        table.setPrefHeight(100);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        Label sectionTitle = new Label("Predations");
        sectionTitle.getStyleClass().add("card-title");

        VBox card = new VBox(4, sectionTitle, table);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(6));

        this.root = card;
        this.active = true;
    }

    /** Marks the view inactive, so that notifications still pending are ignored. */
    @Passivation
    public void deactivate() {
        this.active = false;
    }

    @Override
    public Node getRoot() {
        return root;
    }

    @Override
    public void setClearing(boolean clearing) {
        // No visual feedback needed for this demo beyond the table
        // emptying itself via onLogCleared.
    }

    @Override
    public void onPredationRecorded(Predation predation) {
        Platform.runLater(() -> {
            if (active) {
                rows.add(0, new PredationRow(
                        predation.predatorSpecies(), predation.preySpecies(), predation.simulationTime()));
                if (rows.size() > MAX_ROWS) {
                    rows.remove(rows.size() - 1);
                }
            }
        });
    }

    @Override
    public void onLogCleared() {
        Platform.runLater(() -> {
            if (active) {
                rows.clear();
            }
        });
    }
}
