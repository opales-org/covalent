/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.crosswatcher.controller;

import java.util.Set;

import org.opales.covalent.container.component.annotations.Parameters;

/**
 * This watcher's own watched prey-species set, editable live from
 * UserConfiguration.
 */
@Parameters
public interface CrossWatcherParameters {

    /**
     * Returns a copy of the set of prey species whose predations are logged.
     * @return the watched prey species names
     */
    Set<String> getWatchedSpecies();

    /**
     * Replaces the watched prey species, effective from the next notification.
     * @param watchedSpecies the prey species names to watch
     */
    void setWatchedSpecies(Set<String> watchedSpecies);
}
