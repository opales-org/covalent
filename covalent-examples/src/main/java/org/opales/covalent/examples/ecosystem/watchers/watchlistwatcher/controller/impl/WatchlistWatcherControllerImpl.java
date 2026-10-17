/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.controller.impl;

import java.util.LinkedHashSet;
import java.util.Set;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.examples.ecosystem.ecosystem.model.PopulationEvents;
import org.opales.covalent.examples.ecosystem.ecosystem.model.Role;
import org.opales.covalent.examples.ecosystem.watchers.LogEntry;
import org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.controller.WatchlistParameters;
import org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.controller.WatchlistWatcherController;
import org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.controller.WatchlistWatcherControllerServices;
import org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.model.WatchlistWatcherModelServices;

/**
 * WatchlistWatcher's Controller: subscribes to PopulationEvents manually,
 * with a filter built from this function's own live WatchlistParameters -
 * the filter reads the watched-species set directly, so editing it takes
 * effect immediately without re-subscribing. Mirrors HazardAlertControllerImpl.
 */
@Implementation
public class WatchlistWatcherControllerImpl implements WatchlistWatcherController,
        WatchlistWatcherControllerServices, WatchlistParameters, PopulationEvents {

    @UsedServices
    private WatchlistWatcherModelServices modelServices;

    @UsedEventsSubscribe
    private PopulationEvents.PopulationEventsSubscribe populationEventsSubscribe;

    private volatile Set<String> watchedSpecies = defaultWatchedSpecies();

    /**
     * Kept in a field, not rebuilt on each activation: a subscription is identified by
     * its observer and filter compared with {@code equals}, so reusing this one filter makes
     * a passivation then reactivation resume the same subscription instead of adding a second.
     */
    private final WatchlistFilter filter = new WatchlistFilter();

    /**
     * Subscribes this controller to PopulationEvents, with a filter that lets only births and
     * deaths of watched species through.
     */
    @Activation
    public void subscribeFiltered() {
        populationEventsSubscribe.subscribe(this, filter);
    }

    @Override
    public void onBirth(String species, Role role) {
        modelServices.recordEntry(new LogEntry(species, "birth"));
    }

    @Override
    public void onDeath(String species, Role role, String cause) {
        modelServices.recordEntry(new LogEntry(species, "death (" + cause + ")"));
    }

    @Override
    public void onPredation(String predatorSpecies, String preySpecies, double simulationTime) {
        // Not this watcher's concern: filtered out entirely, see WatchlistFilter.
    }

    @Override
    public void clear() {
        modelServices.clearLog();
    }

    @Override
    public Set<String> getWatchedSpecies() {
        return new LinkedHashSet<>(watchedSpecies);
    }

    @Override
    public void setWatchedSpecies(Set<String> watchedSpecies) {
        this.watchedSpecies = new LinkedHashSet<>(watchedSpecies);
    }

    private static Set<String> defaultWatchedSpecies() {
        Set<String> defaults = new LinkedHashSet<>();
        defaults.add("Rabbit");
        defaults.add("Fox");
        return defaults;
    }

    /** Reads {@link #watchedSpecies} directly on every call, so an edit applies immediately. */
    private final class WatchlistFilter implements PopulationEvents.PopulationEventsFilter {

        @Override
        public boolean filterBirth(String species, Role role) {
            return !watchedSpecies.contains(species);
        }

        @Override
        public boolean filterDeath(String species, Role role, String cause) {
            return !watchedSpecies.contains(species);
        }

        @Override
        public boolean filterPredation(String predatorSpecies, String preySpecies, double simulationTime) {
            return true;
        }
    }
}
