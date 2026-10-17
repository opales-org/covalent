/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */

package org.opales.covalent.examples.ecosystem.ecosystem.model.impl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.examples.ecosystem.ecosystem.model.EcosystemModel;
import org.opales.covalent.examples.ecosystem.ecosystem.model.EcosystemModelServices;
import org.opales.covalent.examples.ecosystem.ecosystem.model.EcosystemStateEvents;
import org.opales.covalent.examples.ecosystem.ecosystem.model.EcosystemStatsServices;
import org.opales.covalent.examples.ecosystem.ecosystem.model.Species;

/**
 * Pure state store for the ecosystem: current populations and simulation
 * time. Notifies EcosystemStateEvents on every change; holds no
 * simulation logic of its own.
 * <p>
 * Also keeps a bounded history of the states it notified, and implements
 * {@link EcosystemStateEvents.EcosystemStateEventsSubscribe}: when a
 * subscriber arrives, it replays the states that subscriber missed (see
 * {@link #subscribe(EcosystemStateEvents, double, int)}).
 */
@Implementation
public class EcosystemModelImpl implements EcosystemModel, EcosystemModelServices, EcosystemStatsServices,
        EcosystemStateEvents.EcosystemStateEventsSubscribe {

    /** How many past states are kept to catch up a subscriber. */
    private static final int HISTORY_LIMIT = 1000;

    /** One notified state. */
    private record Snapshot(Map<String, Double> populations, double simulationTime) {
    }

    @ProducedEvents
    private EcosystemStateEvents stateEvents;

    private final List<Species> species = new ArrayList<>();
    private final Map<String, Double> populations = new LinkedHashMap<>();
    private double simulationTime;

    /**
     * Guards the state and the history together, and is held while recording and notifying and while
     * replaying, so that a replay covers exactly the states recorded before it. With the default
     * asynchronous event distribution, notifications are only handed to the worker pool under the
     * lock: it does not order their delivery to the subscribers.
     */
    private final Object lock = new Object();
    private final Deque<Snapshot> history = new ArrayDeque<>();

    @Override
    public void setSpecies(List<Species> species) {
        this.species.clear();
        this.species.addAll(species);
    }

    @Override
    public void updatePopulations(Map<String, Double> populations, double simulationTime) {
        synchronized (lock) {
            this.populations.clear();
            this.populations.putAll(populations);
            this.simulationTime = simulationTime;
            if (!history.isEmpty() && simulationTime < history.getLast().simulationTime()) {
                // Time went backwards: a reset started a new run, the previous one is no longer history.
                history.clear();
            }
            history.addLast(new Snapshot(getPopulations(), simulationTime));
            if (history.size() > HISTORY_LIMIT) {
                history.removeFirst();
            }
            stateEvents.onPopulationsChanged(getPopulations(), simulationTime);
        }
    }

    @Override
    public Map<String, Double> getPopulations() {
        synchronized (lock) {
            return new LinkedHashMap<>(populations);
        }
    }

    @Override
    public List<Species> getSpecies() {
        return new ArrayList<>(species);
    }

    @Override
    public double getSimulationTime() {
        synchronized (lock) {
            return simulationTime;
        }
    }

    /**
     * Called by Covalent the moment a subscriber's subscription to {@link EcosystemStateEvents} is
     * registered. Replays to it the states it missed, oldest first, so that its chart continues
     * without a gap: the states after {@code sinceSimulationTime}, at most the latest
     * {@code historyDepth} of them. If the current simulation time is before
     * {@code sinceSimulationTime}, a reset started a new run since the subscriber's last state, and
     * the whole history is new to it. (A reset during a pause is therefore only noticed while the
     * new run has not yet passed the time the subscriber had reached.)
     *
     * @param observer the new subscriber
     * @param sinceSimulationTime simulation time of the last state the subscriber already has,
     *        negative if it has none
     * @param historyDepth the most states to replay, {@code 0} for none
     */
    @Override
    public void subscribe(EcosystemStateEvents observer, double sinceSimulationTime, int historyDepth) {
        synchronized (lock) {
            boolean newRun = simulationTime < sinceSimulationTime;
            List<Snapshot> missed = new ArrayList<>();
            for (Snapshot snapshot : history) {
                if (newRun || snapshot.simulationTime() > sinceSimulationTime) {
                    missed.add(snapshot);
                }
            }
            int first = Math.max(0, missed.size() - Math.max(0, historyDepth));
            for (Snapshot snapshot : missed.subList(first, missed.size())) {
                observer.onPopulationsChanged(new LinkedHashMap<>(snapshot.populations()), snapshot.simulationTime());
            }
        }
    }

    /**
     * Called by Covalent when a subscriber unsubscribes. Nothing to react to here: this model keeps
     * no per-subscriber state.
     *
     * @param observer the subscriber that just unsubscribed
     * @param sinceSimulationTime the value it gave when subscribing
     * @param historyDepth the value it gave when subscribing
     */
    @Override
    public void unsubscribe(EcosystemStateEvents observer, double sinceSimulationTime, int historyDepth) {
        // Nothing to do: no per-subscriber state is kept.
    }
}
