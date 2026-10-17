/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.solver.kociemba.impl;

import org.opales.covalent.examples.rubikscubesolver.cube.CubeState;
import org.opales.covalent.examples.rubikscubesolver.cube.Face;
import org.opales.covalent.examples.rubikscubesolver.cube.Move;
import org.opales.covalent.examples.rubikscubesolver.cube.ProgressReporter;
import org.opales.covalent.examples.rubikscubesolver.cube.RotationDirection;
import org.opales.covalent.examples.rubikscubesolver.cube.SolveProgress;
import org.opales.covalent.examples.rubikscubesolver.solver.SolveSearchExhaustedException;
import org.opales.covalent.examples.rubikscubesolver.solver.kociemba.KociembaParameters;
import org.opales.covalent.examples.rubikscubesolver.solver.kociemba.KociembaSolver;
import org.opales.covalent.examples.rubikscubesolver.solver.kociemba.KociembaSolvingServices;

import org.opales.covalent.container.component.annotations.Implementation;

/**
 * The Kociemba two-phase solving strategy: provides {@link KociembaSolvingServices} and offers
 * {@link KociembaParameters}.
 * <p>
 * <b>Overall shape.</b> Herbert Kociemba's two-phase method solves any cube, however it was
 * scrambled. Phase 1 searches, with all 18 moves, for a path into the G1 subgroup (corners and edges
 * correctly oriented, the four middle-layer edges in the middle layer, in any order); phase 2 then
 * searches from there, using only the 10 moves that stay in G1 (quarter and half turns of up and
 * down, half turns only of front, back, left and right), for the way to the solved cube. The two
 * move sequences are concatenated. The search of each phase is done by {@link TwoPhaseSearch}, on the
 * coordinates and tables of {@link TwoPhaseTables}, over a cube described by its small cubes
 * ({@link CubieCube}).
 * <p>
 * <b>What it guarantees.</b> A solvable cube always gets a solution, usually of 20 to 25 moves, in a
 * fraction of a second once the tables are built (the first solve builds them, which takes well under
 * a second). The solution is the first one found, not the shortest possible. Every solution returned
 * has been replayed on a copy of the given {@link CubeState} and checked to solve it.
 * <p>
 * The only way a solve can fail on a legal cube is a {@link KociembaParameters#getMaxDepth()} below
 * what the second phase needs (at most 18), or the time and node budgets, which are far above what a
 * normal solve uses: both are reported as a {@link SolveSearchExhaustedException}. A cube that no
 * sequence of moves can solve (stickers taken off and put back, for instance) is refused with an
 * {@link IllegalArgumentException}. {@link Thread#interrupted()} is checked every few thousand nodes
 * for cancellation, and progress is reported at most ten times a second.
 */
@Implementation
public class KociembaSolverImpl implements KociembaSolver, KociembaSolvingServices, KociembaParameters {

	private static final int DEFAULT_MAX_DEPTH = 24;

	private static final long TIME_BUDGET_MILLIS = 120_000L;

	private static final long NODE_BUDGET = 200_000_000L;

	private volatile int maxDepth = DEFAULT_MAX_DEPTH;

	@Override
	public Move[] solve(final CubeState cubeState, final ProgressReporter progressReporter) {
		long startTime = System.currentTimeMillis();
		if (cubeState.isSolved()) {
			return new Move[0];
		}
		CubieCube cube = CubieCube.fromState(cubeState);
		if (!cube.isSolvable()) {
			throw new IllegalArgumentException("This cube cannot be solved by turning its faces");
		}
		int[] numbers = new TwoPhaseSearch(cube, this.maxDepth, startTime, TIME_BUDGET_MILLIS, NODE_BUDGET,
				progressReporter).solve();

		Face[] faces = Face.values();
		RotationDirection[] directions = RotationDirection.values();
		Move[] moves = new Move[numbers.length];
		CubeState check = cubeState.clone();
		for (int i = 0; i < numbers.length; i++) {
			moves[i] = new Move(faces[numbers[i] / directions.length], directions[numbers[i] % directions.length]);
			check.applyMove(moves[i]);
		}
		if (!check.isSolved()) {
			throw new IllegalStateException("The solution found does not solve the cube");
		}
		progressReporter.reportProgress(new SolveProgress(
				"Solution found, " + moves.length + " moves", System.currentTimeMillis() - startTime, 0, 0L));
		return moves;
	}

	@Override
	public int getMaxDepth() {
		return this.maxDepth;
	}

	@Override
	public void setMaxDepth(final int maxDepth) {
		this.maxDepth = maxDepth;
	}
}
