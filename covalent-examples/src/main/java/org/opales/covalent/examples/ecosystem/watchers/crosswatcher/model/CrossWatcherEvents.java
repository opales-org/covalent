/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.crosswatcher.model;

import org.opales.covalent.container.component.annotations.Events;

/** Notifications produced by CrossWatcherModel and consumed directly by CrossWatcherView. */
@Events
public interface CrossWatcherEvents {

    /**
     * Raised when a predation is added to the log.
     * @param predation the predation just recorded
     */
    void onPredationRecorded(Predation predation);

    /** Raised when the log has been emptied. */
    void onLogCleared();
}
