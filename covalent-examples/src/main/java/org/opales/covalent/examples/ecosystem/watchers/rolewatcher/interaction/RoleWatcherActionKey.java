/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.rolewatcher.interaction;

import org.opales.covalent.hmi.action.ActionKey;

/**
 * Action identifiers registered by RoleWatcherInteraction with the
 * HMI's ActionManager, executed from each of RoleWatcher's two tables'
 * own "Clear" button.
 */
public enum RoleWatcherActionKey implements ActionKey {

    /** Empties the log of one role; takes the {@code Role} as argument. */
    ClearRole;
}
