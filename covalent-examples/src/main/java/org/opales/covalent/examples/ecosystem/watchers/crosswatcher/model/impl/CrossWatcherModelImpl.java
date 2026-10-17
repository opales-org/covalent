/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.crosswatcher.model.impl;

import java.util.ArrayList;
import java.util.List;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.examples.ecosystem.watchers.crosswatcher.model.CrossWatcherEvents;
import org.opales.covalent.examples.ecosystem.watchers.crosswatcher.model.CrossWatcherModel;
import org.opales.covalent.examples.ecosystem.watchers.crosswatcher.model.CrossWatcherModelServices;
import org.opales.covalent.examples.ecosystem.watchers.crosswatcher.model.Predation;

/** CrossWatcher's Model: a pure state store, no filtering logic of its own. */
@Implementation
public class CrossWatcherModelImpl implements CrossWatcherModel, CrossWatcherModelServices {

    /**
     * Caps how many predations this log keeps, so a long-running
     * simulation cannot grow this list without bound.
     */
    private static final int MAX_ENTRIES = 200;

    @ProducedEvents
    private CrossWatcherEvents crossWatcherEvents;

    private final List<Predation> predations = new ArrayList<>();

    @Override
    public void recordPredation(Predation predation) {
        synchronized (predations) {
            predations.add(predation);
            if (predations.size() > MAX_ENTRIES) {
                predations.remove(0);
            }
        }
        crossWatcherEvents.onPredationRecorded(predation);
    }

    @Override
    public void clearLog() {
        synchronized (predations) {
            predations.clear();
        }
        crossWatcherEvents.onLogCleared();
    }

    @Override
    public List<Predation> getRecordedPredations() {
        synchronized (predations) {
            return new ArrayList<>(predations);
        }
    }
}
