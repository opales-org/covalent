/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.model;

import java.util.List;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.examples.ecosystem.watchers.LogEntry;

/** Pure state-store services for the watchlist log. Holds no filtering logic of its own. */
@Services
public interface WatchlistWatcherModelServices {

    /**
     * Appends an entry to the log, dropping the oldest one beyond the cap, and notifies it.
     * @param entry the entry to record
     */
    void recordEntry(LogEntry entry);

    /** Empties the log and notifies it. */
    void clearLog();

    /**
     * Returns a copy of the logged entries.
     * @return the entries, oldest first
     */
    List<LogEntry> getRecordedEntries();
}
