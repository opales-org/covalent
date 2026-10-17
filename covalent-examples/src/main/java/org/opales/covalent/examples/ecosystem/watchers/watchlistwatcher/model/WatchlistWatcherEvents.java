/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.model;

import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.examples.ecosystem.watchers.LogEntry;

/** Notifications produced by WatchlistWatcherModel and consumed directly by WatchlistWatcherView. */
@Events
public interface WatchlistWatcherEvents {

    /**
     * Raised when an entry is added to the log.
     * @param entry the entry just recorded
     */
    void onEntryRecorded(LogEntry entry);

    /** Raised when the log has been emptied. */
    void onLogCleared();
}
