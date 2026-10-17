/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.model.impl;

import java.util.ArrayList;
import java.util.List;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.examples.ecosystem.watchers.LogEntry;
import org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.model.WatchlistWatcherEvents;
import org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.model.WatchlistWatcherModel;
import org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.model.WatchlistWatcherModelServices;

/**
 * WatchlistWatcher's Model: a pure state store, no filtering logic of its own.
 * Events are delivered on pool threads, so access to the log is synchronized.
 */
@Implementation
public class WatchlistWatcherModelImpl implements WatchlistWatcherModel, WatchlistWatcherModelServices {

    /**
     * Caps how many entries this log keeps, so a long-running
     * simulation cannot grow this list without bound.
     */
    private static final int MAX_ENTRIES = 200;

    @ProducedEvents
    private WatchlistWatcherEvents watchlistWatcherEvents;

    private final List<LogEntry> entries = new ArrayList<>();

    @Override
    public void recordEntry(LogEntry entry) {
        synchronized (entries) {
            entries.add(entry);
            if (entries.size() > MAX_ENTRIES) {
                entries.remove(0);
            }
        }
        watchlistWatcherEvents.onEntryRecorded(entry);
    }

    @Override
    public void clearLog() {
        synchronized (entries) {
            entries.clear();
        }
        watchlistWatcherEvents.onLogCleared();
    }

    @Override
    public List<LogEntry> getRecordedEntries() {
        synchronized (entries) {
            return new ArrayList<>(entries);
        }
    }
}
