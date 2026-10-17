/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.ecosystem.model;

import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsFilter;
import org.opales.covalent.container.component.annotations.EventsSubscribe;

/**
 * Events raised whenever the ecosystem's populations change: a birth, a
 * death, or a predation. Produced by EcosystemController and consumed
 * independently by each watcher inside Watchers, each with its own
 * filter (see {@link PopulationEventsSubscribe} / {@link PopulationEventsFilter}).
 */
@Events
public interface PopulationEvents {

    /**
     * Raised when the population of a species grows during a step.
     * @param species name of the species
     * @param role trophic role of the species
     */
    void onBirth(String species, Role role);

    /**
     * Raised when the population of a species declines during a step.
     * @param species name of the species
     * @param role trophic role of the species
     * @param cause {@code "starvation"} for a predator, {@code "natural"} otherwise
     */
    void onDeath(String species, Role role, String cause);

    /**
     * Raised, when a species declines, once for each other species that consumes it.
     * @param predatorSpecies name of the consuming species
     * @param preySpecies name of the consumed species
     * @param simulationTime simulation time of the step, in simulation time units
     */
    void onPredation(String predatorSpecies, String preySpecies, double simulationTime);

    /**
     * Manual subscription port, used by every watcher inside Watchers to
     * subscribe with its own filter (see {@link PopulationEventsFilter}).
     * The container provides the implementation: EcosystemController does
     * not implement this interface, since it has no need to be told about
     * its subscribers.
     */
    @EventsSubscribe
    interface PopulationEventsSubscribe {

        /**
         * Subscribes an observer to every notification.
         * @param observer the object receiving the notifications
         */
        void subscribe(PopulationEvents observer);

        /**
         * Subscribes an observer to the notifications that the filter lets through.
         * @param observer the object receiving the notifications
         * @param filter decides, notification by notification, whether it is filtered out
         */
        void subscribe(PopulationEvents observer, PopulationEventsFilter filter);

        /**
         * Cancels the subscription of an observer.
         * @param observer the observer to unsubscribe
         */
        void unsubscribe(PopulationEvents observer);
    }

    /**
     * Filters each notification independently - a notification with no
     * matching filter method is always delivered.
     */
    @EventsFilter
    interface PopulationEventsFilter {

        /**
         * Decides whether a birth notification is filtered out.
         * @param species name of the species
         * @param role trophic role of the species
         * @return {@code true} to filter this birth out, {@code false} to let it through
         */
        boolean filterBirth(String species, Role role);

        /**
         * Decides whether a death notification is filtered out.
         * @param species name of the species
         * @param role trophic role of the species
         * @param cause the cause of the death
         * @return {@code true} to filter this death out, {@code false} to let it through
         */
        boolean filterDeath(String species, Role role, String cause);

        /**
         * Decides whether a predation notification is filtered out.
         * @param predatorSpecies name of the consuming species
         * @param preySpecies name of the consumed species
         * @param simulationTime simulation time of the step
         * @return {@code true} to filter this predation out, {@code false} to let it through
         */
        boolean filterPredation(String predatorSpecies, String preySpecies, double simulationTime);
    }
}
