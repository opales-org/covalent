/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.WatchlistWatcher;
import org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.controller.impl.WatchlistWatcherControllerImpl;
import org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.interaction.impl.WatchlistWatcherInteractionImpl;
import org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.model.impl.WatchlistWatcherModelImpl;
import org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.view.impl.WatchlistWatcherViewImpl;

/** The WatchlistWatcher composite: deploys its four MVIC parts together. */
@Implementation
public class WatchlistWatcherImpl implements WatchlistWatcher {

    @Implementation
    private WatchlistWatcherModelImpl model;

    @Implementation
    private WatchlistWatcherControllerImpl controller;

    @Implementation
    private WatchlistWatcherViewImpl view;

    @Implementation
    private WatchlistWatcherInteractionImpl interaction;
}
