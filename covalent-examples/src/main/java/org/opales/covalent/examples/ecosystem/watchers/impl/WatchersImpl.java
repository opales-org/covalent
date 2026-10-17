/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.examples.ecosystem.watchers.Watchers;
import org.opales.covalent.examples.ecosystem.watchers.crosswatcher.impl.CrossWatcherImpl;
import org.opales.covalent.examples.ecosystem.watchers.rolewatcher.impl.RoleWatcherImpl;
import org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.impl.WatchlistWatcherImpl;

/**
 * The Watchers composite: pure lifecycle grouping of the three watcher
 * components. No View or interface aggregation of its own.
 */
@Implementation
public class WatchersImpl implements Watchers {

    @Implementation
    private RoleWatcherImpl roleWatcher;

    @Implementation
    private WatchlistWatcherImpl watchlistWatcher;

    @Implementation
    private CrossWatcherImpl crossWatcher;
}
