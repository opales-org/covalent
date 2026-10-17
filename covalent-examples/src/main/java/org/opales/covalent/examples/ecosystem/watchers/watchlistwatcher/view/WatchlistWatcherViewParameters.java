/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.view;

import org.opales.covalent.container.component.annotations.Parameters;

import javafx.scene.Node;

/** Exposes WatchlistWatcher's root JavaFX node, accessed by HmiBuilder. */
@Parameters
public interface WatchlistWatcherViewParameters {

    /**
     * Returns the root of the watchlist table card.
     * @return the table card node
     */
    Node getRoot();
}
