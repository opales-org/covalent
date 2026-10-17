/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.statsexport;

import org.opales.covalent.container.raw.RawComponentsServices;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Small Raw-styled widget pairing two checkboxes - one enabling or
 * disabling {@link StatsExporter}'s per-tick computation, the other
 * separately enabling or disabling persisting that same data to its
 * CSV file - with a label counting the lines processed so far, and a
 * log table mirroring the export file's exact content. Instantiated
 * and registered by {@link EcosystemStatsExportDemoLauncher}, outside any
 * managed container lifecycle: not annotated with {@code @Component}
 * or {@code @Implementation} and not deployable through the managed
 * lifecycle API, only shaped like a component - own file, own fields
 * and methods - for readability (see the Covalent Raw Components
 * Guide).
 * <p>
 * Offers {@link StatsExportWidgetParameters} the same way every managed
 * function's own View offers its *ViewParameters, so HmiBuilder embeds
 * its root nodes through the ordinary {@code @AccessedParameters} +
 * {@code getXxxRoot()} pattern. References {@link StatsExporter}
 * directly to toggle export and saving - plain object references, not
 * Covalent ports - and subscribes to {@link StatsExportEvents} to keep
 * its counter current and its log current.
 * </p>
 * <p>
 * The log table exists so a user can confirm the raw {@link
 * StatsExporter} component is working without having to go find and
 * open its CSV file on disk: it shows the exact header line once, at
 * the top, followed by each data line in the same order they are
 * written to the file, capped at {@link #MAX_LOG_ROWS} visible lines
 * (the header is never evicted; only the oldest data lines are, once
 * the cap is reached) - deliberately not styled like the other watcher
 * tables' newest-first logs, since this one's purpose is a verbatim,
 * file-shaped mirror of what {@link StatsExporter} is producing.
 * </p>
 */
public class StatsExportWidget implements StatsExportWidgetParameters, StatsExportEvents {

    private static final int MAX_LOG_ROWS = 200;

    private final RawComponentsServices rawComponentsServices;
    private final StatsExporter exporter;

    private final CheckBox exportCheckBox = new CheckBox("Export stats");
    private final CheckBox saveCheckBox = new CheckBox("Save to CSV file");
    private final Label countLabel = new Label(lineCountText(0));
    private final HBox exportRow = new HBox(8, this.exportCheckBox, this.countLabel);
    private final VBox root = new VBox(4, this.exportRow, this.saveCheckBox);

    private final ObservableList<String> logRows = FXCollections.observableArrayList();
    private final Node logRoot = buildLogRoot(this.logRows);

    /**
     * Creates the widget, with both checkboxes initialized from the exporter's switches; each
     * toggle is
     * forwarded to the exporter.
     * @param rawComponentsServices the services through which this widget registers itself
     * @param exporter the exporter whose export and save switches the checkboxes drive
     */
    public StatsExportWidget(final RawComponentsServices rawComponentsServices, final StatsExporter exporter) {
        this.rawComponentsServices = rawComponentsServices;
        this.exporter = exporter;
        this.exportCheckBox.setSelected(exporter.isExportEnabled());
        this.exportCheckBox.setOnAction(event -> this.exporter.setExportEnabled(this.exportCheckBox.isSelected()));
        this.saveCheckBox.setSelected(exporter.isSaveEnabled());
        this.saveCheckBox.setOnAction(event -> this.exporter.setSaveEnabled(this.saveCheckBox.isSelected()));
    }

    /**
     * Registers this widget as a raw component, offers {@link
     * StatsExportWidgetParameters}, and subscribes to {@link
     * StatsExportEvents}. Safe to call before {@link StatsExporter}
     * itself is registered: a raw subscription can be declared before
     * its producer exists, and attaches automatically once one appears.
     */
    public void register() {
        this.rawComponentsServices.registerComponent(StatsExportWidget.class, this);
        this.rawComponentsServices.offerParameters(StatsExportWidgetParameters.class);
        this.rawComponentsServices.subscribe(StatsExportEvents.class, this);
    }

    @Override
    public Node getRoot() {
        return this.root;
    }

    @Override
    public Node getLogRoot() {
        return this.logRoot;
    }

    @Override
    public void onLineExported(final int totalCount, final String headerLine, final String dataLine) {
        Platform.runLater(() -> {
            this.countLabel.setText(lineCountText(totalCount));
            if (headerLine != null && this.logRows.isEmpty()) {
                this.logRows.add(headerLine);
            }
            this.logRows.add(dataLine);
            while (this.logRows.size() > MAX_LOG_ROWS + 1) {
                this.logRows.remove(1);
            }
        });
    }

    private static String lineCountText(final int totalCount) {
        return totalCount + (totalCount == 1 ? " line exported" : " lines exported");
    }

    private static Node buildLogRoot(final ObservableList<String> logRows) {
        TableView<String> table = new TableView<>(logRows);
        TableColumn<String, String> entryColumn = new TableColumn<>("Entry");
        entryColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue()));
        table.getColumns().add(entryColumn);
        table.setPrefHeight(100);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        Label sectionTitle = new Label("Export Log");
        sectionTitle.getStyleClass().add("card-title");

        VBox card = new VBox(4, sectionTitle, table);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(6));
        VBox.setVgrow(table, Priority.ALWAYS);

        return card;
    }
}
