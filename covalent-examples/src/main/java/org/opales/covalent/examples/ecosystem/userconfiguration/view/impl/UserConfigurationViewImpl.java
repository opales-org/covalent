/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.userconfiguration.view.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.examples.ecosystem.userconfiguration.interaction.UserConfigurationActionKey;
import org.opales.covalent.examples.ecosystem.userconfiguration.view.UserConfigurationView;
import org.opales.covalent.examples.ecosystem.userconfiguration.view.UserConfigurationViewParameters;
import org.opales.covalent.examples.ecosystem.userconfiguration.view.UserConfigurationViewServices;
import org.opales.covalent.hmi.action.ActionConsumerServices;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * The configuration composite's View: one slider per species growth rate
 * (built dynamically once the initial rates are known), a time scale
 * slider, and one check box per species, in two groups, for the
 * watchlist / cross-watcher watched species sets. Has no Model/Events to consume: this composite owns no
 * state of its own, it only fronts other functions' Parameters.
 */
@Implementation
public class UserConfigurationViewImpl
        implements UserConfigurationView, UserConfigurationViewServices, UserConfigurationViewParameters {

    @UsedServices
    private ActionConsumerServices actionManagerServices;

    private Node root;

    private GridPane grid;

    private final Map<String, Slider> growthRateSliders = new LinkedHashMap<>();

    private Slider timeScaleSlider;

    private FlowPane watchlistPane;

    private FlowPane crossWatcherPane;

    private final Map<String, CheckBox> watchlistBoxes = new LinkedHashMap<>();

    private final Map<String, CheckBox> crossWatcherBoxes = new LinkedHashMap<>();

    private volatile boolean active;

    /**
     * Builds a new root at each activation. The sliders of the previous
     * root are dropped with it: kept in {@link #growthRateSliders}, they
     * would be updated in place of new ones, and the new grid would show
     * no growth rate at all. The same goes for the check boxes. The
     * Interaction Controller pushes the rates again at its own activation,
     * which builds the new sliders and check boxes.
     */
    @Activation
    public void buildRoot() {
        this.growthRateSliders.clear();
        this.watchlistBoxes.clear();
        this.crossWatcherBoxes.clear();
        this.grid = new GridPane();
        this.grid.setHgap(8);
        this.grid.setVgap(6);
        ColumnConstraints labelColumn = new ColumnConstraints(180);
        ColumnConstraints controlColumn = new ColumnConstraints(220);
        this.grid.getColumnConstraints().addAll(labelColumn, controlColumn);

        this.timeScaleSlider = new Slider(0.1, 5.0, 1.0);
        int row = addRow(0, "Time scale", this.timeScaleSlider);
        this.timeScaleSlider.valueProperty().addListener((obs, oldValue, newValue) -> this.actionManagerServices
                .executeAction(UserConfigurationActionKey.SetTimeScale, newValue.doubleValue()));

        // The check boxes are added once the species are known (see displayGrowthRates and
        // displayPreySpecies): every species can be on the watchlist, only the prey ones can be watched
        // by the cross-watcher.
        this.watchlistPane = newCheckBoxPane();
        row = addRow(row, "Watchlist", this.watchlistPane);
        this.crossWatcherPane = newCheckBoxPane();
        row = addRow(row, "Cross-watcher prey", this.crossWatcherPane);

        Label sectionTitle = new Label("Configuration");
        sectionTitle.getStyleClass().add("card-title");

        VBox card = new VBox(6, sectionTitle, this.grid);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(10));

        this.root = card;
        this.active = true;
    }

    /** Marks the view inactive, so that notifications still pending are ignored. */
    @Passivation
    public void deactivate() {
        this.active = false;
    }

    private static FlowPane newCheckBoxPane() {
        FlowPane pane = new FlowPane(10, 4);
        pane.setPrefWrapLength(220);
        return pane;
    }

    private void addWatchlistCheckBox(String species) {
        this.watchlistBoxes.put(species,
                newSpeciesCheckBox(this.watchlistPane, species, UserConfigurationActionKey.SetWatchlistSpecies));
    }

    private CheckBox newSpeciesCheckBox(FlowPane pane, String species, UserConfigurationActionKey actionKey) {
        CheckBox box = new CheckBox(species);
        // Fired by the user only, not when displayWatchlistSpecies / displayCrossWatcherSpecies check the box.
        box.setOnAction(event -> this.actionManagerServices.executeAction(actionKey, species, box.isSelected()));
        pane.getChildren().add(box);
        return box;
    }

    private int addRow(int row, String labelText, Node control) {
        this.grid.add(new Label(labelText), 0, row);
        this.grid.add(control, 1, row);
        return row + 1;
    }

    @Override
    public Node getRoot() {
        return this.root;
    }

    @Override
    public void displayGrowthRates(Map<String, Double> growthRates) {
        Platform.runLater(() -> {
            if (!this.active) {
                return;
            }
            for (Map.Entry<String, Double> entry : growthRates.entrySet()) {
                if (this.growthRateSliders.containsKey(entry.getKey())) {
                    this.growthRateSliders.get(entry.getKey()).setValue(entry.getValue());
                    continue;
                }
                Slider slider = new Slider(-1.0, 1.0, entry.getValue());
                String species = entry.getKey();
                slider.valueProperty().addListener((obs, oldValue, newValue) -> this.actionManagerServices
                        .executeAction(UserConfigurationActionKey.SetGrowthRate, species, newValue.doubleValue()));
                this.growthRateSliders.put(species, slider);
                this.addWatchlistCheckBox(species);
                int row = this.grid.getRowCount();
                this.addRow(row, species + " growth rate", slider);
            }
        });
    }

    @Override
    public void displayTimeScale(double timeScale) {
        Platform.runLater(() -> {
            if (this.active && this.timeScaleSlider != null) {
                this.timeScaleSlider.setValue(timeScale);
            }
        });
    }

    @Override
    public void displayWatchlistSpecies(Set<String> watchedSpecies) {
        Platform.runLater(() -> {
            if (this.active) {
                this.watchlistBoxes.forEach((species, box) -> box.setSelected(watchedSpecies.contains(species)));
            }
        });
    }

    @Override
    public void displayPreySpecies(Set<String> preySpecies) {
        Platform.runLater(() -> {
            if (!this.active) {
                return;
            }
            for (String species : preySpecies) {
                if (!this.crossWatcherBoxes.containsKey(species)) {
                    this.crossWatcherBoxes.put(species, newSpeciesCheckBox(this.crossWatcherPane, species,
                            UserConfigurationActionKey.SetCrossWatcherSpecies));
                }
            }
        });
    }

    @Override
    public void displayCrossWatcherSpecies(Set<String> watchedSpecies) {
        Platform.runLater(() -> {
            if (this.active) {
                this.crossWatcherBoxes.forEach((species, box) -> box.setSelected(watchedSpecies.contains(species)));
            }
        });
    }
}
