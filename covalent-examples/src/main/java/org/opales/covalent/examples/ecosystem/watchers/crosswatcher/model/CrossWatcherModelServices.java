/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.crosswatcher.model;

import java.util.List;

import org.opales.covalent.container.component.annotations.Services;

/** Pure state-store services for the predation log. Holds no filtering logic of its own. */
@Services
public interface CrossWatcherModelServices {

    /**
     * Appends a predation to the log, dropping the oldest one beyond the cap, and notifies it.
     * @param predation the predation to record
     */
    void recordPredation(Predation predation);

    /** Empties the log and notifies it. */
    void clearLog();

    /**
     * Returns a copy of the logged predations.
     * @return the predations, oldest first
     */
    List<Predation> getRecordedPredations();
}
