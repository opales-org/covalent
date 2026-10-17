/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.cube.model.impl;

import org.opales.covalent.examples.rubikscubesolver.cube.CubeEvents;
import org.opales.covalent.examples.rubikscubesolver.cube.CubeState;
import org.opales.covalent.examples.rubikscubesolver.cube.model.CubeModel;
import org.opales.covalent.examples.rubikscubesolver.cube.model.CubeModelServices;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;

/**
 * The cube's Model: holds the current {@link CubeState} and notifies
 * {@link CubeEvents} whenever it changes. Carries no logic of its own -
 * {@code CubeController} is what actually calls
 * {@link CubeState#applyMove(org.opales.covalent.examples.rubikscubesolver.cube.Move)}
 * before pushing the result here.
 * <p>
 * Implements event subscription delegation (see {@code DatasetModelImpl} in
 * the Neural Network Trainer demo for the full rationale): both
 * {@code CubeView} (automatically, at startup) and
 * {@code SolverInteraction} (manually, each time its move history
 * panel reopens) need to start from the cube's actual current state rather
 * than waiting for the next move to fire {@link CubeEvents#stateChanged(CubeState)}.
 * <p>
 * Starts already solved: a fresh {@link CubeState}'s constructor already
 * produces a solved cube, so no explicit {@code @Initialization} is needed
 * here to seed it.
 */
@Implementation
public class CubeModelImpl implements CubeModel, CubeModelServices, CubeEvents.CubeEventsSubscribe {

	@ProducedEvents
	private CubeEvents produceCubeEvents;

	private volatile CubeState state = new CubeState();

	@Override
	public CubeState getState() {
		return this.state;
	}

	@Override
	public void setState(final CubeState state) {
		this.state = state;
		this.produceCubeEvents.stateChanged(state);
	}

	/**
	 * Called by Covalent the moment a new subscriber's subscription to
	 * {@link CubeEvents} is registered, whether automatic ({@code CubeView}
	 * at startup) or manual ({@code SolverInteraction}, through
	 * {@link CubeEvents.CubeEventsSubscribe}, each time its move history panel opens).
	 * Replays the current state so the new subscriber starts consistent.
	 *
	 * @param subscriber the new subscriber
	 */
	@Override
	public void subscribe(final CubeEvents subscriber) {
		subscriber.stateChanged(this.state);
	}

	/**
	 * Called by Covalent when a subscriber unsubscribes. Nothing to react to
	 * here: this Model keeps no per-subscriber state.
	 *
	 * @param subscriber the subscriber that just unsubscribed
	 */
	@Override
	public void unsubscribe(final CubeEvents subscriber) {
		// Nothing to do: no per-subscriber state is kept.
	}
}
