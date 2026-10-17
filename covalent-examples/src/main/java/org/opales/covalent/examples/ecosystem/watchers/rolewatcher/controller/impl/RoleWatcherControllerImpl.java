/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.rolewatcher.controller.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.examples.ecosystem.ecosystem.model.PopulationEvents;
import org.opales.covalent.examples.ecosystem.ecosystem.model.Role;
import org.opales.covalent.examples.ecosystem.watchers.LogEntry;
import org.opales.covalent.examples.ecosystem.watchers.rolewatcher.controller.RoleWatcherController;
import org.opales.covalent.examples.ecosystem.watchers.rolewatcher.controller.RoleWatcherControllerServices;
import org.opales.covalent.examples.ecosystem.watchers.rolewatcher.model.RoleWatcherModelServices;

/**
 * RoleWatcher's Controller: subscribes to PopulationEvents twice, once per
 * role, each with a plain delegate object holding its own fixed role
 * (see "Subscribing an Object Instead of the Consumer Component" in
 * doc/managed-components.md) and its own filter instance. Only one "consumes" edge appears in the architecture diagram:
 * which Java object actually receives each callback is an implementation
 * detail below that abstraction.
 */
@Implementation
public class RoleWatcherControllerImpl implements RoleWatcherController, RoleWatcherControllerServices {

    @UsedServices
    private RoleWatcherModelServices modelServices;

    @UsedEventsSubscribe
    private PopulationEvents.PopulationEventsSubscribe populationEventsSubscribe;

    private final RoleObserver preyObserver = new RoleObserver(Role.PREY);

    private final RoleObserver predatorObserver = new RoleObserver(Role.PREDATOR);

    /**
     * Kept in a field, not rebuilt on each activation: a subscription is identified by
     * its observer and filter compared with {@code equals}, so reusing these two filters makes
     * a passivation then reactivation resume the same subscription instead of adding a second.
     */
    private final RoleFilter preyFilter = new RoleFilter(Role.PREY);

    private final RoleFilter predatorFilter = new RoleFilter(Role.PREDATOR);

    /**
     * Subscribes one observer per role to PopulationEvents, each with a filter letting only
     * the births and deaths of that role through.
     */
    @Activation
    public void subscribeBothRoles() {
        populationEventsSubscribe.subscribe(preyObserver, preyFilter);
        populationEventsSubscribe.subscribe(predatorObserver, predatorFilter);
    }

    @Override
    public void clear(Role role) {
        modelServices.clearLog(role);
    }

    /** Receives PopulationEvents already filtered down to one fixed role. */
    private final class RoleObserver implements PopulationEvents {

        private final Role role;

        RoleObserver(Role role) {
            this.role = role;
        }

        @Override
        public void onBirth(String species, Role role) {
            modelServices.recordEntry(this.role, new LogEntry(species, "birth"));
        }

        @Override
        public void onDeath(String species, Role role, String cause) {
            modelServices.recordEntry(this.role, new LogEntry(species, "death (" + cause + ")"));
        }

        @Override
        public void onPredation(String predatorSpecies, String preySpecies, double simulationTime) {
            // Not this watcher's concern: filtered out entirely, see RoleFilter.
        }
    }

    /** Lets through only births and deaths for one fixed role. */
    private static final class RoleFilter implements PopulationEvents.PopulationEventsFilter {

        private final Role role;

        RoleFilter(Role role) {
            this.role = role;
        }

        @Override
        public boolean filterBirth(String species, Role role) {
            return role != this.role;
        }

        @Override
        public boolean filterDeath(String species, Role role, String cause) {
            return role != this.role;
        }

        @Override
        public boolean filterPredation(String predatorSpecies, String preySpecies, double simulationTime) {
            return true;
        }
    }
}
