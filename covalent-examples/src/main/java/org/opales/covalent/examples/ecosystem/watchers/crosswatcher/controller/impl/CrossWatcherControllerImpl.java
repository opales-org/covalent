/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.crosswatcher.controller.impl;

import java.util.LinkedHashSet;
import java.util.Set;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.examples.ecosystem.ecosystem.model.PopulationEvents;
import org.opales.covalent.examples.ecosystem.ecosystem.model.Role;
import org.opales.covalent.examples.ecosystem.watchers.crosswatcher.controller.CrossWatcherController;
import org.opales.covalent.examples.ecosystem.watchers.crosswatcher.controller.CrossWatcherControllerServices;
import org.opales.covalent.examples.ecosystem.watchers.crosswatcher.controller.CrossWatcherParameters;
import org.opales.covalent.examples.ecosystem.watchers.crosswatcher.model.CrossWatcherModelServices;
import org.opales.covalent.examples.ecosystem.watchers.crosswatcher.model.Predation;

/**
 * CrossWatcher's Controller: subscribes to PopulationEvents manually, with
 * a filter combining two independent criteria - event kind (predation
 * only) and this function's own live watched-species set, re-read
 * directly on every call so an edit applies immediately.
 */
@Implementation
public class CrossWatcherControllerImpl implements CrossWatcherController,
        CrossWatcherControllerServices, CrossWatcherParameters, PopulationEvents {

    @UsedServices
    private CrossWatcherModelServices modelServices;

    @UsedEventsSubscribe
    private PopulationEvents.PopulationEventsSubscribe populationEventsSubscribe;

    private volatile Set<String> watchedSpecies = defaultWatchedSpecies();

    /**
     * Kept in a field, not rebuilt on each activation: a subscription is identified by
     * its observer and filter compared with {@code equals}, so reusing this one filter makes
     * a passivation then reactivation resume the same subscription instead of adding a second.
     */
    private final PredationFilter filter = new PredationFilter();

    /**
     * Subscribes this controller to PopulationEvents, with a filter that lets only predations
     * of watched prey through.
     */
    @Activation
    public void subscribeFiltered() {
        populationEventsSubscribe.subscribe(this, filter);
    }

    @Override
    public void onBirth(String species, Role role) {
        // Not this watcher's concern: filtered out entirely, see PredationFilter.
    }

    @Override
    public void onDeath(String species, Role role, String cause) {
        // Not this watcher's concern: filtered out entirely, see PredationFilter.
    }

    @Override
    public void onPredation(String predatorSpecies, String preySpecies, double simulationTime) {
        modelServices.recordPredation(new Predation(predatorSpecies, preySpecies, simulationTime));
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
        return defaults;
    }

    /** Keeps only predations whose prey species is in {@link #watchedSpecies}. */
    private final class PredationFilter implements PopulationEvents.PopulationEventsFilter {

        @Override
        public boolean filterBirth(String species, Role role) {
            return true;
        }

        @Override
        public boolean filterDeath(String species, Role role, String cause) {
            return true;
        }

        @Override
        public boolean filterPredation(String predatorSpecies, String preySpecies, double simulationTime) {
            return !watchedSpecies.contains(preySpecies);
        }
    }
}
