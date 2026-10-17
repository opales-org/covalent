/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.rolewatcher.view.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.examples.ecosystem.ecosystem.model.Role;
import org.opales.covalent.examples.ecosystem.watchers.LogEntry;
import org.opales.covalent.examples.ecosystem.watchers.rolewatcher.model.RoleWatcherEvents;
import org.opales.covalent.examples.ecosystem.watchers.rolewatcher.view.RoleWatcherView;
import org.opales.covalent.examples.ecosystem.watchers.rolewatcher.view.RoleWatcherViewParameters;
import org.opales.covalent.examples.ecosystem.watchers.rolewatcher.view.RoleWatcherViewServices;

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
 * RoleWatcher's View: two independent tables, one per role. Consumes
 * RoleWatcherEvents directly (managed autowiring).
 * <p>
 * Builds both root nodes synchronously at {@code @Activation}.
 */
@Implementation
public class RoleWatcherViewImpl
        implements RoleWatcherView, RoleWatcherViewServices, RoleWatcherViewParameters, RoleWatcherEvents {

    /** One row of a role's log table. */
    public static final class EntryRow {

        private final String species;
        private final String description;

        /**
         * Creates a row.
         * @param species the species name
         * @param description what happened to the species
         */
        public EntryRow(String species, String description) {
            this.species = species;
            this.description = description;
        }

        /**
         * Returns the species concerned.
         * @return the species name
         */
        public String getSpecies() {
            return species;
        }

        /**
         * Returns what happened.
         * @return the event description
         */
        public String getDescription() {
            return description;
        }
    }

    /**
     * Caps how many rows each table keeps, so a long-running
     * simulation cannot grow either list without bound.
     */
    private static final int MAX_ROWS = 200;

    private volatile boolean active;

    private Node preyRoot;
    private Node predatorRoot;

    private final ObservableList<EntryRow> preyRows = FXCollections.observableArrayList();
    private final ObservableList<EntryRow> predatorRows = FXCollections.observableArrayList();

    /** Builds the prey and predator table cards, then marks the view active. */
    @Activation
    public void buildRoots() {
        this.preyRoot = buildTable("Prey Log", preyRows);
        this.predatorRoot = buildTable("Predator Log", predatorRows);
        this.active = true;
    }

    /** Marks the view inactive, so that notifications still pending are ignored. */
    @Passivation
    public void deactivate() {
        this.active = false;
    }

    private static Node buildTable(String title, ObservableList<EntryRow> rows) {
        TableView<EntryRow> table = new TableView<>(rows);
        TableColumn<EntryRow, String> speciesColumn = new TableColumn<>("Species");
        speciesColumn.setCellValueFactory(new PropertyValueFactory<>("species"));
        TableColumn<EntryRow, String> descriptionColumn = new TableColumn<>("Event");
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        table.getColumns().add(speciesColumn);
        table.getColumns().add(descriptionColumn);
        table.setPrefHeight(100);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        Label sectionTitle = new Label(title);
        sectionTitle.getStyleClass().add("card-title");

        VBox card = new VBox(4, sectionTitle, table);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(6));
        return card;
    }

    @Override
    public Node getPreyRoot() {
        return preyRoot;
    }

    @Override
    public Node getPredatorRoot() {
        return predatorRoot;
    }

    @Override
    public void setClearing(Role role, boolean clearing) {
        // No visual feedback needed for this demo beyond the table
        // emptying itself via onLogCleared.
    }

    @Override
    public void onEntryRecorded(Role role, LogEntry entry) {
        Platform.runLater(() -> {
            if (!active) {
                return;
            }
            ObservableList<EntryRow> rows = rowsFor(role);
            rows.add(0, new EntryRow(entry.species(), entry.description()));
            if (rows.size() > MAX_ROWS) {
                rows.remove(rows.size() - 1);
            }
        });
    }

    @Override
    public void onLogCleared(Role role) {
        Platform.runLater(() -> {
            if (!active) {
                return;
            }
            rowsFor(role).clear();
        });
    }

    private ObservableList<EntryRow> rowsFor(Role role) {
        return role == Role.PREDATOR ? predatorRows : preyRows;
    }
}
