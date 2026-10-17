/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.crosswatcher;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the cross-criteria watcher MVIC: combines two
 * independent criteria, event kind (predation only) and its own watched
 * prey-species set, kept separate from WatchlistWatcher's own.
 */
@Component
public interface CrossWatcher {
}
