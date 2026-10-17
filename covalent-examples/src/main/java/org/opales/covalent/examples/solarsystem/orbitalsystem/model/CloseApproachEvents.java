/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalsystem.model;

import org.opales.covalent.examples.solarsystem.hazardalert.HazardAlert;
import org.opales.covalent.examples.solarsystem.hazardalert.controller.impl.HazardAlertControllerImpl;
import org.opales.covalent.examples.solarsystem.orbitalsystem.Body;
import org.opales.covalent.examples.solarsystem.orbitalsystem.controller.OrbitalController;
import org.opales.covalent.examples.solarsystem.orbitalsystem.view.OrbitalView;
import org.opales.covalent.examples.solarsystem.orbitalwatch.OrbitalWatch;

import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsFilter;
import org.opales.covalent.container.component.annotations.EventsSubscribe;

/**
 * Produced by {@code OrbitalModel} and consumed by {@code OrbitalView}
 * (managed autowiring), and externally by {@code OrbitalWatch} (unfiltered,
 * also managed autowiring) and {@code HazardAlert} (filtered, manual
 * subscription through {@link CloseApproachEventsSubscribe} - see
 * {@code HazardAlertControllerImpl}).
 * <p>
 * Public interface because functions other than the one producing it
 * ({@code OrbitalWatch} and {@code HazardAlert}) consume it.
 * <p>
 * Two distinct notifications share this one port:
 * <ul>
 *   <li>{@link #onSystemAdvanced(Body[], double)}, fired every simulation
 *       step with the full body snapshot - drives {@code OrbitalView}'s
 *       continuous animation. {@code OrbitalWatch} and {@code HazardAlert}
 *       are not interested in it and leave it a no-op, the same way
 *       {@code TrainingInteractionImpl} no-ops the
 *       {@code TrainingEvents} methods it does not care about.</li>
 *   <li>{@link #onCloseApproachDetected(CloseApproach)}, fired only when a
 *       close approach is actually detected - the sparse notification
 *       {@code OrbitalWatch} and {@code HazardAlert} exist for.</li>
 * </ul>
 */
@Events
public interface CloseApproachEvents {

	/**
	 * Fired every simulation step with the full, current body snapshot.
	 *
	 * @param bodies         every body's current state
	 * @param simulationTime the simulation time after this step
	 */
	void onSystemAdvanced(Body[] bodies, double simulationTime);

	/**
	 * Fired once per detected close approach (on the step where the two
	 * bodies' distance first drops below {@code OrbitalController}'s own
	 * baseline threshold).
	 *
	 * @param approach the detected close approach
	 */
	void onCloseApproachDetected(CloseApproach approach);

	/**
	 * Manual subscription port, used by {@code HazardAlert} to subscribe
	 * with a filter (see {@link CloseApproachEventsFilter}). The container
	 * provides the implementation: {@code OrbitalModel} does not implement
	 * this interface, since it has no need to be told about its subscribers.
	 */
	@EventsSubscribe
	interface CloseApproachEventsSubscribe {

		/**
		 * Registers an observer that receives every notification.
		 *
		 * @param observer the observer to register
		 */
		void subscribe(CloseApproachEvents observer);

		/**
		 * Registers an observer that receives only the close approaches the filter lets through.
		 *
		 * @param observer the observer to register
		 * @param filter the filter deciding which close approaches are delivered
		 */
		void subscribe(CloseApproachEvents observer, CloseApproachEventsFilter filter);

		/**
		 * Unregisters an observer.
		 *
		 * @param observer the observer to unregister
		 */
		void unsubscribe(CloseApproachEvents observer);
	}

	/**
	 * Filters {@link #onCloseApproachDetected(CloseApproach)} notifications
	 * only - {@link #onSystemAdvanced(Body[], double)} has no matching filter
	 * method, so it is never filtered (always delivered) regardless of which
	 * filter a subscriber uses. Used by {@code HazardAlert} to keep only
	 * approaches that are both close enough and involve a large-enough body.
	 */
	@EventsFilter
	interface CloseApproachEventsFilter {

		/**
		 * Decides whether a detected close approach is kept out of the delivery to a subscriber.
		 *
		 * @param approach the detected close approach
		 * @return {@code true} to filter this approach out (not delivered),
		 *         {@code false} to let it through
		 */
		boolean filterCloseApproachDetected(CloseApproach approach);
	}
}
