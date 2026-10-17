/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.interaction;

import org.opales.covalent.hmi.action.ActionKey;

/** Action identifier registered by WatchlistWatcherInteraction with the HMI's ActionManager. */
public enum WatchlistWatcherActionKey implements ActionKey {

    /** Empties the log. */
    Clear;
}
