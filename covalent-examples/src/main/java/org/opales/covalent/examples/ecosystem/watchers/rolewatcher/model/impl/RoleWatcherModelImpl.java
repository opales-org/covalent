/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.rolewatcher.model.impl;

import java.util.List;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.examples.ecosystem.ecosystem.model.Role;
import org.opales.covalent.examples.ecosystem.watchers.LogEntry;
import org.opales.covalent.examples.ecosystem.watchers.rolewatcher.model.RoleWatcherEvents;
import org.opales.covalent.examples.ecosystem.watchers.rolewatcher.model.RoleWatcherModel;
import org.opales.covalent.examples.ecosystem.watchers.rolewatcher.model.RoleWatcherModelServices;

/**
 * RoleWatcher's Model: two @Item instances, one per role (prey,
 * predator), each keeping its own list of entries, addressed only by
 * this Model itself. The two items use distinct types (PreyLogItem,
 * PredatorLogItem) rather than sharing one type, since Covalent resolves
 * an @Item field by the item's declared type: two fields of the same
 * item type in one Implementation would collide. Notifies
 * RoleWatcherEvents on every change; holds no filtering logic of its
 * own.
 */
@Implementation
public class RoleWatcherModelImpl implements RoleWatcherModel, RoleWatcherModelServices {

    @ProducedEvents
    private RoleWatcherEvents roleWatcherEvents;

    @Item
    private PreyLogItem preyLog;

    @Item
    private PredatorLogItem predatorLog;

    @Override
    public void recordEntry(Role role, LogEntry entry) {
        addEntry(role, entry);
        roleWatcherEvents.onEntryRecorded(role, entry);
    }

    @Override
    public void clearLog(Role role) {
        clearEntries(role);
        roleWatcherEvents.onLogCleared(role);
    }

    @Override
    public List<LogEntry> getRecordedEntries(Role role) {
        return readEntries(role);
    }

    private void addEntry(Role role, LogEntry entry) {
        if (role == Role.PREDATOR) {
            predatorLog.add(entry);
        } else {
            preyLog.add(entry);
        }
    }

    private void clearEntries(Role role) {
        if (role == Role.PREDATOR) {
            predatorLog.clear();
        } else {
            preyLog.clear();
        }
    }

    private List<LogEntry> readEntries(Role role) {
        return role == Role.PREDATOR ? predatorLog.read() : preyLog.read();
    }
}
