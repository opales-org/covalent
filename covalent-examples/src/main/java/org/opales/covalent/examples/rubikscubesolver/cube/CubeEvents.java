/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.cube;

import org.opales.covalent.examples.rubikscubesolver.cube.model.impl.CubeModelImpl;
import org.opales.covalent.examples.rubikscubesolver.solver.interaction.impl.SolverInteractionImpl;

import org.opales.covalent.container.component.annotations.Cloned;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsSubscribe;

/**
 * Cube state change notifications, produced by {@code CubeModel} and
 * consumed both internally, automatically, by {@code CubeView}, and
 * externally, manually, by {@code SolverInteraction} (through
 * {@link CubeEventsSubscribe}, only while its move history panel is open - see
 * {@link SolverInteractionImpl}'s javadoc).
 * <p>
 * Unlike this project's other Model events interfaces (which stay internal
 * to their own function), {@code CubeEvents} crosses a function boundary -
 * the same shape as the Solar System demo's own cross-function events
 * interface. The {@link #stateChanged(CubeState)} snapshot argument is
 * {@code @Cloned}: {@link CubeState} is mutable and the instance delivered
 * is the one {@code CubeModel} holds (see {@code CubeModelImpl}), so each
 * subscriber receives its own deep copy and cannot alter the Model's state
 * by changing its snapshot. {@link CubeState} implements {@link Cloneable}
 * for exactly this reason.
 */
@Events
public interface CubeEvents {

	/**
	 * Fired whenever the cube's state changes (a move applied, a scramble,
	 * or a manual rotation).
	 *
	 * @param snapshot a private, immutable-for-the-receiver copy of the
	 *                 cube's new state
	 */
	void stateChanged(@Cloned CubeState snapshot);

	/**
	 * Subscribe interface for {@link CubeEvents}, nested here as Covalent
	 * requires (see {@code DatasetEvents.DatasetEventsSubscribe} in the
	 * Neural Network Trainer demo for the fuller rationale).
	 * {@code CubeModelImpl} implements this to enable event subscription
	 * delegation, so a subscriber that activates after the cube already has
	 * state - true of {@code CubeView} at startup, and of
	 * {@code SolverInteraction} every time its move history panel is
	 * reopened - gets caught up immediately.
	 */
	@EventsSubscribe
	interface CubeEventsSubscribe {

		/**
		 * Called by Covalent when a consumer subscribes to {@link CubeEvents}.
		 *
		 * @param subscriber the new subscriber
		 */
		void subscribe(CubeEvents subscriber);

		/**
		 * Called by Covalent when a subscriber unsubscribes from
		 * {@link CubeEvents}.
		 *
		 * @param subscriber the subscriber that just unsubscribed
		 */
		void unsubscribe(CubeEvents subscriber);
	}
}
