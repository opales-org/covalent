/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the pure lifecycle-grouping composite of the three
 * watcher components (RoleWatcher, WatchlistWatcher, CrossWatcher). No
 * View or interface aggregation of its own: each child keeps its own
 * View, assembled directly by HmiBuilder, bypassing this composite.
 */
@Component
public interface Watchers {
}
