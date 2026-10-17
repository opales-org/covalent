/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.controller;

import java.util.Set;

import org.opales.covalent.container.component.annotations.Parameters;

/**
 * This watcher's own watched species set, editable live from
 * UserConfiguration; re-read on every notification.
 */
@Parameters
public interface WatchlistParameters {

    /**
     * Returns a copy of the set of species whose births and deaths are logged.
     * @return the watched species names
     */
    Set<String> getWatchedSpecies();

    /**
     * Replaces the watched species, effective from the next notification.
     * @param watchedSpecies the species names to watch
     */
    void setWatchedSpecies(Set<String> watchedSpecies);
}
