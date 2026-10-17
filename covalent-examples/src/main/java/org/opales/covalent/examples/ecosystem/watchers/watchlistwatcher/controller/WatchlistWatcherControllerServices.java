/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.controller;

import org.opales.covalent.container.component.annotations.Services;

/** User-facing "clear" command, provided by WatchlistWatcherController and used by WatchlistWatcherInteraction. */
@Services
public interface WatchlistWatcherControllerServices {

    /** Empties the log. */
    void clear();
}
