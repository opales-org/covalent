/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.rolewatcher.model.impl;

import java.util.ArrayList;
import java.util.List;

import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.examples.ecosystem.watchers.LogEntry;

/**
 * Internal item of RoleWatcherModel: a plain, owner-only log of entries
 * for the predator role. Covalent resolves an {@code @Item} field by the
 * item's declared type, so the predator log and the prey log need two
 * distinct item types (this class and its sibling PreyLogItem) rather
 * than two fields sharing one type. Like any {@code @Item}, the item class
 * lives in its own top-level file, a sibling of the Implementation that
 * owns it, not nested inside it. Its methods are synchronized, since events
 * reach it on pool threads.
 */
@Item
public class PredatorLogItem {

    /**
     * Caps how many entries this log keeps, so a long-running
     * simulation cannot grow this list without bound.
     */
    private static final int MAX_ENTRIES = 200;

    private final List<LogEntry> entries = new ArrayList<>();

    synchronized void add(LogEntry entry) {
        entries.add(entry);
        if (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }
    }

    synchronized void clear() {
        entries.clear();
    }

    synchronized List<LogEntry> read() {
        return new ArrayList<>(entries);
    }
}
