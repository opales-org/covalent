/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.rolewatcher.model;

import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.examples.ecosystem.ecosystem.model.Role;
import org.opales.covalent.examples.ecosystem.watchers.LogEntry;

/**
 * Notifications produced by RoleWatcherModel and consumed directly by
 * RoleWatcherView.
 */
@Events
public interface RoleWatcherEvents {

    /**
     * Raised when an entry is added to the log of a role.
     * @param role the role whose log changed
     * @param entry the entry just recorded
     */
    void onEntryRecorded(Role role, LogEntry entry);

    /**
     * Raised when the log of a role has been emptied.
     * @param role the role whose log was emptied
     */
    void onLogCleared(Role role);
}
