/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.solver.controller.impl;

import org.opales.covalent.examples.rubikscubesolver.cube.CubeServices;
import org.opales.covalent.examples.rubikscubesolver.cube.CubeState;
import org.opales.covalent.examples.rubikscubesolver.cube.Move;
import org.opales.covalent.examples.rubikscubesolver.cube.ProgressReporter;
import org.opales.covalent.examples.rubikscubesolver.cube.SolveProgress;
import org.opales.covalent.examples.rubikscubesolver.cube.SolvingAlgorithm;
import org.opales.covalent.examples.rubikscubesolver.solver.SolveSearchExhaustedException;
import org.opales.covalent.examples.rubikscubesolver.solver.controller.SolverController;
import org.opales.covalent.examples.rubikscubesolver.solver.controller.SolverControllerServices;
import org.opales.covalent.examples.rubikscubesolver.solver.human.HumanSolvingServices;
import org.opales.covalent.examples.rubikscubesolver.solver.kociemba.KociembaSolvingServices;
import org.opales.covalent.examples.rubikscubesolver.solver.model.SolverModelServices;
import org.opales.covalent.examples.rubikscubesolver.solver.model.SolverPlaybackParameters;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opales.covalent.container.async.AsyncServices;
import org.opales.covalent.container.component.annotations.AccessedParameters;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;

/**
 * The solver's Controller: drives a solve end to end, off the UI thread,
 * through the container-managed {@link AsyncServices} rather than spawning
 * its own thread.
 * <p>
 * {@link #startSolve(SolvingAlgorithm)} builds a {@link ProgressReporter}
 * lambda that forwards every callback invocation straight into
 * {@link SolverModelServices#setProgress(org.opales.covalent.examples.rubikscubesolver.cube.SolveProgress)}
 * (see {@link ProgressReporter}'s javadoc for why that callback exists
 * outside the Covalent events model at all), dispatches to whichever
 * strategy's {@code solve(...)} was selected through
 * {@link AsyncServices#submit(String, java.util.function.Supplier)} on a
 * dedicated queue key, and on success records the solution
 * ({@link SolverModelServices#setSolution(Move[])}) then plays it out on
 * the cube one move at a time through the external {@link CubeServices},
 * so a view can animate each move individually rather
 * than jumping straight to the solved state.
 * <p>
 * Using a dedicated queue key (rather than the general pool) is what makes
 * {@link #cancelSolve()} meaningful: {@link AsyncServices#releaseQueue(String)}
 * discards a not-yet-finished task on that key outright, interrupting a
 * running one - see {@code doc/managed-components.md}'s Application
 * Utility Services section.
 * <p>
 * {@link #startSolve(SolvingAlgorithm)}'s failure handler tells apart the
 * three ways a solve can end without a solution: a
 * {@link CancellationException} (the user called {@link #cancelSolve()}, or
 * this controller's queue was released for some other reason), a
 * {@link SolveSearchExhaustedException} (the chosen strategy ran out of its
 * own search budget without finding a solution, an honest failure of the
 * search itself, not of the plumbing around it), or anything else (an
 * actual bug). {@code AsyncServices} wraps whatever a submitted task throws
 * in a {@link CompletionException}, so the handler unwraps one before
 * telling these apart.
 */
@Implementation
public class SolverControllerImpl implements SolverController, SolverControllerServices {

	private static final Logger log = Logger.getLogger(SolverControllerImpl.class.getName());

	/** Dedicated {@code AsyncServices} queue key this controller solves on. */
	private static final String SOLVE_QUEUE_KEY = "rubiks-cube-solver";

	@UsedServices
	private SolverModelServices useSolverModelServices;

	@UsedServices
	private KociembaSolvingServices useKociembaSolvingServices;

	@UsedServices
	private HumanSolvingServices useHumanSolvingServices;

	@UsedServices
	private CubeServices useCubeServices;

	@AccessedParameters
	private SolverPlaybackParameters accessPlaybackParameters;

	@UsedServices
	private AsyncServices useAsyncServices;

	/**
	 * Identifies the solve in progress, or {@code null} when there is none. A solve ends only
	 * its own token: a cancelled solve that finishes late cannot mark as ended a newer solve that
	 * started in the meantime.
	 */
	private final AtomicReference<Object> activeSolve = new AtomicReference<>();

	@Override
	public void startSolve(final SolvingAlgorithm algorithm) {
		Object token = new Object();
		if (!this.activeSolve.compareAndSet(null, token)) {
			log.fine("A solve is already in progress, ignoring startSolve(" + algorithm + ")");
			return;
		}
		this.useSolverModelServices.setSelectedAlgorithm(algorithm);
		CubeState cubeState = this.useCubeServices.getState();
		ProgressReporter progressReporter = this.useSolverModelServices::setProgress;

		this.useAsyncServices.submit(SOLVE_QUEUE_KEY, () -> {
			Move[] moves = this.solveWith(algorithm, cubeState, progressReporter);
			this.playSolution(moves, cubeState, token);
			return null;
		})
				.exceptionally(failure -> {
					this.reportSolveFailure(failure);
					this.activeSolve.compareAndSet(token, null);
					return null;
				});
	}

	/**
	 * Tells the three failure outcomes apart for logging, and, in every
	 * case, pushes a final {@link SolveProgress} through
	 * {@link SolverModelServices#setProgress(SolveProgress)} so the view
	 * stops showing its last in-progress status forever: without this, a
	 * search that exhausts its budget (an honest, expected outcome of a
	 * bounded search, not a bug) would leave the status label frozen on "searching", with nothing
	 * telling the user the solve actually ended.
	 */
	private void reportSolveFailure(final Throwable failure) {
		Throwable cause = (failure instanceof CompletionException) ? failure.getCause() : failure;
		String phase;
		if (cause instanceof CancellationException) {
			log.fine("Solve was cancelled");
			phase = "Cancelled";
		} else if (cause instanceof SolveSearchExhaustedException) {
			log.log(Level.INFO, "Solve search ran out of budget without finding a solution", cause);
			phase = "No solution found: " + cause.getMessage();
		} else {
			log.log(Level.WARNING, "Solve failed", cause);
			phase = "Solve failed: " + (cause != null ? cause.getMessage() : "unknown error");
		}
		this.useSolverModelServices.setProgress(new SolveProgress(phase, 0L, 0, 0L));
	}

	private Move[] solveWith(final SolvingAlgorithm algorithm, final CubeState cubeState,
			final ProgressReporter progressReporter) {
		return switch (algorithm) {
			case KOCIEMBA -> this.useKociembaSolvingServices.solve(cubeState, progressReporter);
			case HUMAN -> this.useHumanSolvingServices.solve(cubeState, progressReporter);
		};
	}

	/**
	 * Plays a found solution onto the cube it was computed for, one move at a
	 * time at the speed set through {@link SolverPlaybackParameters}, so the
	 * cube view shows the solve unfolding - unless that cube has since
	 * changed. {@link #startSolve(SolvingAlgorithm)} captures {@code solvedFor}
	 * before handing the search off asynchronously;
	 * {@code CubeControllerImpl} never mutates a {@link CubeState} in place,
	 * it always builds a new instance and replaces the Model's reference
	 * (see that class's javadoc), so a plain reference check is enough to
	 * tell whether anything - a reset, a manual scramble, or any other
	 * driver of the cube - replaced the cube's state, before the first move
	 * and between any two moves. When it has, the plan no longer means
	 * anything, so it is abandoned rather than played onto whatever the cube
	 * became instead. This is the cube's player noticing the board changed
	 * under it. The responsibility sits here, with the player, so that
	 * {@code Cube} stays unaware that a solver, or any other driver of the
	 * cube, exists.
	 * <p>
	 * Runs on the solve queue, so {@link #cancelSolve()} interrupts it like
	 * the search that precedes it.
	 *
	 * @param moves     the solution found, to play one move at a time
	 * @param solvedFor the cube state this solution was computed for
	 * @param token     identifies the solve being played, which this method ends
	 */
	private void playSolution(final Move[] moves, final CubeState solvedFor, final Object token) {
		CubeState expected = solvedFor;
		this.useSolverModelServices.setSolution(moves);
		try {
			for (int i = 0; i < moves.length; i++) {
				if (Thread.interrupted()) {
					throw new CancellationException("playback was cancelled");
				}
				if (this.useCubeServices.getState() != expected) {
					log.fine("Cube changed while solving; abandoning the solution found for its previous state");
					this.useSolverModelServices.setProgress(new SolveProgress("Abandoned: cube changed", 0L, 0, 0L));
					return;
				}
				this.useSolverModelServices.setProgress(
						new SolveProgress("Playing move " + (i + 1) + "/" + moves.length, 0L, 0, 0L));
				this.useCubeServices.applyMove(moves[i]);
				expected = this.useCubeServices.getState();
				Thread.sleep(1000L / Math.max(1, this.accessPlaybackParameters.getMovesPerSecond()));
			}
			this.useSolverModelServices.setProgress(new SolveProgress("Solved in " + moves.length + " moves", 0L, 0, 0L));
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new CancellationException("playback was cancelled");
		} finally {
			this.activeSolve.compareAndSet(token, null);
		}
	}

	@Override
	public void cancelSolve() {
		if (this.activeSolve.getAndSet(null) != null) {
			this.useAsyncServices.releaseQueue(SOLVE_QUEUE_KEY);
		}
	}

	@Override
	public void reset() {
		this.cancelSolve();
		this.useCubeServices.resetToSolved();
	}
}
