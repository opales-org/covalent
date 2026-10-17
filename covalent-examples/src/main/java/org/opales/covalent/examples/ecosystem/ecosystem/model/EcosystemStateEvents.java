/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */

package org.opales.covalent.examples.ecosystem.ecosystem.model;

import java.util.Map;

import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsSubscribe;

/**
 * Internal event notifying that the ecosystem's populations changed.
 * Consumed directly by EcosystemView, so it can refresh its chart and
 * table without the Interaction Controller mediating. EcosystemView
 * subscribes manually, through {@link EcosystemStateEventsSubscribe}, so
 * that it can pause and resume its reception.
 */
@Events
public interface EcosystemStateEvents {

    /**
     * Raised each time the model records populations, after a step or a reset.
     * @param populations population by species name
     * @param simulationTime simulation time reached
     */
    void onPopulationsChanged(Map<String, Double> populations, double simulationTime);

    /**
     * Manual subscription port, used by EcosystemView to stop and resume
     * its reception on demand. A subscription carries two free arguments,
     * neither a filter nor subscribe parameters: they only tell the
     * producer how to catch the new subscriber up. EcosystemModel
     * implements this interface (delegating - the container manages the
     * actual subscription), so it is told about each subscription and
     * replays the states the subscriber missed.
     */
    @EventsSubscribe
    interface EcosystemStateEventsSubscribe {

        /**
         * Subscribes an observer, and asks for the states it missed to be replayed to it first.
         * @param observer the object receiving the notifications
         * @param sinceSimulationTime simulation time of the last state the observer already has, or
         *        a negative value if it has none: only later states are replayed
         * @param historyDepth the most states to replay (the latest ones are kept), {@code 0} for none
         */
        void subscribe(EcosystemStateEvents observer, double sinceSimulationTime, int historyDepth);

        /**
         * Cancels a subscription of an observer. As with any custom subscription, the arguments
         * identify which subscription is cancelled, so they must be those of the {@code subscribe}
         * call.
         * @param observer the observer to unsubscribe
         * @param sinceSimulationTime the value given when subscribing
         * @param historyDepth the value given when subscribing
         */
        void unsubscribe(EcosystemStateEvents observer, double sinceSimulationTime, int historyDepth);
    }
}
