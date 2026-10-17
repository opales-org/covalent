/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.solver.model.impl;

import org.opales.covalent.examples.rubikscubesolver.cube.Move;
import org.opales.covalent.examples.rubikscubesolver.cube.SolveProgress;
import org.opales.covalent.examples.rubikscubesolver.cube.SolvingAlgorithm;
import org.opales.covalent.examples.rubikscubesolver.solver.model.SolverEvents;
import org.opales.covalent.examples.rubikscubesolver.solver.model.SolverModel;
import org.opales.covalent.examples.rubikscubesolver.solver.model.SolverModelServices;
import org.opales.covalent.examples.rubikscubesolver.solver.model.SolverPlaybackParameters;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;

/**
 * The solver's Model: holds the currently selected algorithm, the last
 * known progress and the last found solution, and notifies
 * {@link SolverEvents} whenever they change. Carries no solving logic of
 * its own; see {@link SolverModelServices} for how {@code SolverController}
 * drives it.
 * <p>
 * No subscription delegation here, unlike {@code CubeModelImpl}: see
 * {@link SolverEvents}'s javadoc for why {@code SolverView} does not need
 * catch-up state on activation.
 */
@Implementation
public class SolverModelImpl implements SolverModel, SolverModelServices, SolverPlaybackParameters {

	private static final SolvingAlgorithm DEFAULT_ALGORITHM = SolvingAlgorithm.HUMAN;

	private static final int DEFAULT_MOVES_PER_SECOND = 10;

	private static final Move[] NO_SOLUTION = new Move[0];

	@ProducedEvents
	private SolverEvents produceSolverEvents;

	private volatile SolvingAlgorithm selectedAlgorithm = DEFAULT_ALGORITHM;

	private volatile Move[] solution = NO_SOLUTION;

	private volatile int movesPerSecond = DEFAULT_MOVES_PER_SECOND;

	@Override
	public void setProgress(final SolveProgress progress) {
		this.produceSolverEvents.progressUpdated(progress);
	}

	@Override
	public void setSolution(final Move[] moves) {
		this.solution = moves;
		this.produceSolverEvents.solveCompleted(moves);
	}

	@Override
	public Move[] getSolution() {
		return this.solution;
	}

	@Override
	public SolvingAlgorithm getSelectedAlgorithm() {
		return this.selectedAlgorithm;
	}

	@Override
	public void setSelectedAlgorithm(final SolvingAlgorithm algorithm) {
		this.selectedAlgorithm = algorithm;
	}

	@Override
	public int getMovesPerSecond() {
		return this.movesPerSecond;
	}

	@Override
	public void setMovesPerSecond(final int movesPerSecond) {
		this.movesPerSecond = movesPerSecond;
	}
}
