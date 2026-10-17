/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.userconfiguration.view;

import java.util.Map;
import java.util.Set;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Piloting commands for the configuration view, used by
 * UserConfigurationInteraction to keep the displayed values in sync
 * with what was actually applied.
 */
@Services
public interface UserConfigurationViewServices {

    /**
     * Shows the growth rates, adding a slider for each species not yet displayed.
     * @param growthRates growth rate by species name
     */
    void displayGrowthRates(Map<String, Double> growthRates);

    /**
     * Shows the time scale on its slider.
     * @param timeScale the time scale to show
     */
    void displayTimeScale(double timeScale);

    /**
     * Shows the species watched by the watchlist, by checking their boxes.
     * @param watchedSpecies the watched species names
     */
    void displayWatchlistSpecies(Set<String> watchedSpecies);

    /**
     * Offers the species that can be watched by the cross-watcher, one box each: the species
     * some other species consumes. Boxes of species already offered are kept.
     * @param preySpecies the names of the species that are consumed
     */
    void displayPreySpecies(Set<String> preySpecies);

    /**
     * Shows the prey species watched by the cross-watcher, by checking their boxes.
     * @param watchedSpecies the watched species names
     */
    void displayCrossWatcherSpecies(Set<String> watchedSpecies);
}
