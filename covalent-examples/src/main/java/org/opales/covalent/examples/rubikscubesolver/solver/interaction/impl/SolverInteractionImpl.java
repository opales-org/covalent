/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.solver.interaction.impl;

import org.opales.covalent.examples.rubikscubesolver.cube.CubeEvents;
import org.opales.covalent.examples.rubikscubesolver.cube.CubeState;
import org.opales.covalent.examples.rubikscubesolver.cube.SolvingAlgorithm;
import org.opales.covalent.examples.rubikscubesolver.solver.controller.SolverControllerServices;
import org.opales.covalent.examples.rubikscubesolver.solver.interaction.SolverActionKey;
import org.opales.covalent.examples.rubikscubesolver.solver.interaction.SolverInteraction;
import org.opales.covalent.examples.rubikscubesolver.solver.view.SolverViewServices;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionProviderServices;

/**
 * The solver's Interaction Controller: registers this function's actions with the HMI's
 * {@code ActionManager} at activation, exactly the pivot role {@code DatasetInteractionImpl}
 * documents in full for the Neural Network Trainer demo.
 * <p>
 * <b>This is the one place in this whole demo that demonstrates manual event subscription.</b>
 * {@link CubeEvents} declares its own {@code @EventsSubscribe} interface,
 * {@link CubeEvents.CubeEventsSubscribe} (implemented by {@code CubeModelImpl} on the producer side,
 * for subscription delegation/catch-up), so this class subscribes to {@link CubeEvents} manually, only
 * while its move history panel is open, through a {@code @UsedEventsSubscribe} field of that type (see
 * {@code doc/managed-components.md}'s "Subscribe Interface Provided by an Events Interface" section),
 * calling {@link CubeEvents.CubeEventsSubscribe#subscribe(CubeEvents)} and
 * {@link CubeEvents.CubeEventsSubscribe#unsubscribe(CubeEvents)} directly with {@code this} as the
 * subscriber (this class still implements {@link CubeEvents} to receive
 * {@link #stateChanged(CubeState)} calls; only the way it subscribes is manual).
 * <p>
 * <b>{@code GenericSubscribe} does not work here</b>, and Covalent refuses it outright:
 * {@code GenericSubscribe} is only for an events interface that declares no {@code @EventsSubscribe}
 * interface of its own (the container throws {@code IllegalStateException} when a consumer's used
 * subscriber is {@code GenericSubscribe} but the target events interface already provides its own
 * subscribe interface) - {@link CubeEvents} is exactly that case, since {@code CubeModelImpl} needs its own
 * nested subscribe interface for subscription delegation. Declaring a {@code @UsedEventsSubscribe}
 * field at all - whichever subscribe interface it is typed to - also suppresses this class's automatic
 * subscription to {@link CubeEvents} at startup (Covalent only auto-subscribes a consumer that
 * declares no subscriber field for that events interface): without this field, this class would be
 * subscribed unconditionally from startup instead of only while the panel is open.
 * <p>
 * {@link CubeEvents#stateChanged(CubeState)} only carries the resulting {@code CubeState} snapshot,
 * not the {@code Move} that produced it, so this class can only trace that the cube's state changed,
 * not decode which move caused it - see {@link SolverViewServices#appendMoveToHistory(String)}.
 */
@Implementation
public class SolverInteractionImpl implements SolverInteraction, CubeEvents {

	@UsedServices
	private ActionProviderServices actionProviderServices;

	@UsedServices
	private SolverViewServices useSolverViewServices;

	@UsedServices
	private SolverControllerServices useSolverControllerServices;

	/**
	 * See this class's javadoc: {@link CubeEvents} provides its own
	 * subscribe interface, so manual subscription here goes through it
	 * directly rather than through {@code GenericSubscribe}. Declaring this
	 * field is also what suppresses this class's automatic subscription to
	 * {@link CubeEvents} at startup.
	 */
	@UsedEventsSubscribe
	private CubeEvents.CubeEventsSubscribe useCubeEventsSubscribe;

	private volatile boolean historyVisible;

	/**
	 * Registers the solver actions (algorithm selection, start, cancel, move history toggle, reset) with
	 * the action provider.
	 */
	@Activation
	public void registerActions() {
		this.actionProviderServices.registerActionExecution(
				SolverActionKey.SelectAlgorithm, this::selectAlgorithm, SolvingAlgorithm.class);
		this.actionProviderServices.registerActionExecution(
				SolverActionKey.StartSolve, this::startSolve, SolvingAlgorithm.class);
		this.actionProviderServices.registerActionExecution(
				SolverActionKey.CancelSolve, this::cancelSolve);
		this.actionProviderServices.registerActionExecution(
				SolverActionKey.ToggleMoveHistory, this::toggleMoveHistory, Boolean.class);
		this.actionProviderServices.registerActionExecution(
				SolverActionKey.Reset, this::reset);
	}

	/** Unregisters the solver actions and, if the move history is open, unsubscribes from the cube events. */
	@Passivation
	public void unregisterActions() {
		this.actionProviderServices.unregisterActionExecution(SolverActionKey.SelectAlgorithm);
		this.actionProviderServices.unregisterActionExecution(SolverActionKey.StartSolve);
		this.actionProviderServices.unregisterActionExecution(SolverActionKey.CancelSolve);
		this.actionProviderServices.unregisterActionExecution(SolverActionKey.ToggleMoveHistory);
		this.actionProviderServices.unregisterActionExecution(SolverActionKey.Reset);
		if (this.historyVisible) {
			this.useCubeEventsSubscribe.unsubscribe(this);
			this.historyVisible = false;
		}
	}

	private void selectAlgorithm(final SolvingAlgorithm algorithm) {
		// Selection alone does not start a solve; SolverController records
		// the chosen algorithm the moment startSolve is actually called.
	}

	private void startSolve(final SolvingAlgorithm algorithm) {
		this.useSolverControllerServices.startSolve(algorithm);
	}

	private void cancelSolve() {
		this.useSolverControllerServices.cancelSolve();
	}

	private void reset() {
		this.useSolverControllerServices.reset();
	}

	private void toggleMoveHistory(final Boolean visible) {
		this.useSolverViewServices.setMoveHistoryVisible(visible);
		if (visible && !this.historyVisible) {
			this.useCubeEventsSubscribe.subscribe(this);
		} else if (!visible && this.historyVisible) {
			this.useCubeEventsSubscribe.unsubscribe(this);
		}
		this.historyVisible = visible;
	}

	@Override
	public void stateChanged(final CubeState snapshot) {
		if (this.historyVisible) {
			this.useSolverViewServices.appendMoveToHistory(
					snapshot.isSolved() ? "cube state changed (solved)" : "cube state changed");
		}
	}
}
