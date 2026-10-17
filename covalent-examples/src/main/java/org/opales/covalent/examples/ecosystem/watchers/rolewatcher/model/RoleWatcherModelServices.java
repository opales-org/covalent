/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.rolewatcher.model;

import java.util.List;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.examples.ecosystem.ecosystem.model.Role;
import org.opales.covalent.examples.ecosystem.watchers.LogEntry;

/**
 * Pure state-store services for the role-partitioned logs: records the
 * outcome of a notification and exposes the current log for a given role.
 * Holds no filtering logic of its own; that lives in RoleWatcherController.
 */
@Services
public interface RoleWatcherModelServices {

    /**
     * Appends an entry to the log of a role, dropping the oldest one beyond the cap, and
     * notifies it.
     * @param role the role whose log receives the entry
     * @param entry the entry to record
     */
    void recordEntry(Role role, LogEntry entry);

    /**
     * Empties the log of a role and notifies it.
     * @param role the role whose log is emptied
     */
    void clearLog(Role role);

    /**
     * Returns a copy of the log of a role.
     * @param role the role whose log is read
     * @return the entries, oldest first
     */
    List<LogEntry> getRecordedEntries(Role role);
}
