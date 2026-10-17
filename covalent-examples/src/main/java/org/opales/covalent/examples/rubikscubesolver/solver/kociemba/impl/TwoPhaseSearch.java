/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.solver.kociemba.impl;

import java.util.concurrent.CancellationException;

import org.opales.covalent.examples.rubikscubesolver.cube.ProgressReporter;
import org.opales.covalent.examples.rubikscubesolver.cube.RotationDirection;
import org.opales.covalent.examples.rubikscubesolver.cube.SolveProgress;
import org.opales.covalent.examples.rubikscubesolver.solver.SolveSearchExhaustedException;

/**
 * One two-phase search, on one cube. The first phase is an iterative-deepening depth-first search
 * over the 18 moves that stops each time the cube is in G1 (see {@link TwoPhaseTables}); for each
 * such position, the second phase searches, with the same method and only the 10 moves that stay in
 * G1, for the way to the solved cube. The first complete answer is returned: it is not the shortest
 * possible one, but it is found in a fraction of a second whatever the scramble.
 * <p>
 * To keep the second phase cheap, the search is repeated with a growing limit on its length (10,
 * then 14, then the largest it can need, 18): a first phase solution whose second phase is too long
 * is dropped and the next one is tried. Every cube reaches G1 in at most 12 moves and then needs at
 * most 18, so with the last limit an answer always exists; a smaller limit given by the caller can
 * make the search fail instead, which is reported as {@link SolveSearchExhaustedException}.
 * <p>
 * An instance is used by one thread, for one search. It checks the time and the node budget, and
 * whether the thread was interrupted, every few thousand nodes.
 */
final class TwoPhaseSearch {

	/** Longest second phase there can be, so the largest useful limit on it. */
	static final int MAX_PHASE_TWO_DEPTH = 18;

	private static final int MAX_PHASE_ONE_DEPTH = 20;

	private static final int[] PHASE_TWO_LIMITS = { 10, 14, MAX_PHASE_TWO_DEPTH };

	private static final int DIRECTIONS = RotationDirection.values().length;

	private static final int MOVES = CubieCube.MOVE_COUNT;

	private static final int PHASE_TWO_MOVES = TwoPhaseTables.PHASE_TWO_MOVES.length;

	private static final long REPORT_INTERVAL_MILLIS = 100L;

	private static final long CHECK_MASK = 0xFFF;

	private final CubieCube start;

	private final int maxPhaseTwoDepth;

	private final long startTime;

	private final long timeBudgetMillis;

	private final long nodeBudget;

	private final ProgressReporter reporter;

	private final int[] phaseOne = new int[MAX_PHASE_ONE_DEPTH + 1];

	private final int[] phaseTwo = new int[MAX_PHASE_TWO_DEPTH + 1];

	private int phaseOneLength;

	private int phaseTwoLength;

	private int phaseTwoLimit;

	private long nodes;

	private long lastReport;

	/**
	 * Prepares a search.
	 *
	 * @param start the cube to solve, which can be solved
	 * @param maxPhaseTwoDepth the longest second phase to accept, at most {@link #MAX_PHASE_TWO_DEPTH}
	 * @param startTime when the solve started, as {@link System#currentTimeMillis()} says
	 * @param timeBudgetMillis how long the search may run, counted from {@code startTime}
	 * @param nodeBudget how many nodes the search may explore
	 * @param reporter receives the progress
	 */
	TwoPhaseSearch(final CubieCube start, final int maxPhaseTwoDepth, final long startTime,
			final long timeBudgetMillis, final long nodeBudget, final ProgressReporter reporter) {
		this.start = start;
		this.maxPhaseTwoDepth = Math.max(0, Math.min(maxPhaseTwoDepth, MAX_PHASE_TWO_DEPTH));
		this.startTime = startTime;
		this.timeBudgetMillis = timeBudgetMillis;
		this.nodeBudget = nodeBudget;
		this.reporter = reporter;
	}

	/**
	 * Searches for a solution.
	 *
	 * @return the moves, each numbered as {@link CubieCube} says; empty if the cube is solved
	 * @throws SolveSearchExhaustedException if no solution is found within the limits
	 * @throws CancellationException if the thread is interrupted
	 */
	int[] solve() {
		int twist = TwoPhaseTables.twist(this.start);
		int flip = TwoPhaseTables.flip(this.start);
		int slice = TwoPhaseTables.slice(this.start);
		int previousLimit = -1;
		for (int limit : PHASE_TWO_LIMITS) {
			int capped = Math.min(limit, this.maxPhaseTwoDepth);
			if (capped == previousLimit) {
				continue;
			}
			previousLimit = capped;
			this.phaseTwoLimit = capped;
			for (int depth = 0; depth <= MAX_PHASE_ONE_DEPTH; depth++) {
				this.report("Phase 1: reaching the G1 subgroup, depth " + depth, depth);
				this.phaseOneLength = 0;
				if (this.searchPhaseOne(depth, twist, flip, slice, -1)) {
					int[] result = new int[this.phaseOneLength + this.phaseTwoLength];
					System.arraycopy(this.phaseOne, 0, result, 0, this.phaseOneLength);
					System.arraycopy(this.phaseTwo, 0, result, this.phaseOneLength, this.phaseTwoLength);
					return result;
				}
			}
		}
		throw new SolveSearchExhaustedException("Kociemba found no solution whose phase 2 has at most "
				+ this.maxPhaseTwoDepth + " moves, after exploring " + this.nodes + " nodes");
	}

	private boolean searchPhaseOne(final int remaining, final int twist, final int flip, final int slice,
			final int lastFace) {
		int bound = Math.max(TwoPhaseTables.TWIST_SLICE_DISTANCE[twist * TwoPhaseTables.SLICE_COUNT + slice],
				TwoPhaseTables.FLIP_SLICE_DISTANCE[flip * TwoPhaseTables.SLICE_COUNT + slice]);
		if (bound > remaining) {
			return false;
		}
		this.countNode();
		if (remaining == 0) {
			if (this.phaseOneLength > 0 && this.keepsG1(this.phaseOne[this.phaseOneLength - 1])) {
				return false;
			}
			return this.searchPhaseTwo();
		}
		for (int move = 0; move < MOVES; move++) {
			int face = move / DIRECTIONS;
			if (this.redundant(face, lastFace)) {
				continue;
			}
			this.phaseOne[this.phaseOneLength++] = move;
			if (this.searchPhaseOne(remaining - 1, TwoPhaseTables.TWIST_MOVE[twist * MOVES + move],
					TwoPhaseTables.FLIP_MOVE[flip * MOVES + move], TwoPhaseTables.SLICE_MOVE[slice * MOVES + move],
					face)) {
				return true;
			}
			this.phaseOneLength--;
		}
		return false;
	}

	private boolean searchPhaseTwo() {
		CubieCube cube = this.start;
		for (int i = 0; i < this.phaseOneLength; i++) {
			cube = cube.after(this.phaseOne[i]);
		}
		int corners = TwoPhaseTables.cornerPermutation(cube);
		int edges = TwoPhaseTables.edgePermutation(cube);
		int slice = TwoPhaseTables.slicePermutation(cube);
		int lastFace = this.phaseOneLength > 0 ? this.phaseOne[this.phaseOneLength - 1] / DIRECTIONS : -1;
		this.report("Phase 2: solving from the G1 subgroup, after " + this.phaseOneLength + " moves",
				this.phaseOneLength);
		for (int depth = 0; depth <= this.phaseTwoLimit; depth++) {
			this.phaseTwoLength = 0;
			if (this.searchPhaseTwo(depth, corners, edges, slice, lastFace)) {
				return true;
			}
		}
		return false;
	}

	private boolean searchPhaseTwo(final int remaining, final int corners, final int edges, final int slice,
			final int lastFace) {
		int bound = Math.max(
				TwoPhaseTables.CORNER_SLICE_DISTANCE[corners * TwoPhaseTables.SLICE_PERMUTATION_COUNT + slice],
				TwoPhaseTables.EDGE_SLICE_DISTANCE[edges * TwoPhaseTables.SLICE_PERMUTATION_COUNT + slice]);
		if (bound > remaining) {
			return false;
		}
		this.countNode();
		if (remaining == 0) {
			return true;
		}
		for (int index = 0; index < PHASE_TWO_MOVES; index++) {
			int move = TwoPhaseTables.PHASE_TWO_MOVES[index];
			int face = move / DIRECTIONS;
			if (this.redundant(face, lastFace)) {
				continue;
			}
			this.phaseTwo[this.phaseTwoLength++] = move;
			if (this.searchPhaseTwo(remaining - 1,
					TwoPhaseTables.CORNER_PERMUTATION_MOVE[corners * PHASE_TWO_MOVES + index],
					TwoPhaseTables.EDGE_PERMUTATION_MOVE[edges * PHASE_TWO_MOVES + index],
					TwoPhaseTables.SLICE_PERMUTATION_MOVE[slice * PHASE_TWO_MOVES + index], face)) {
				return true;
			}
			this.phaseTwoLength--;
		}
		return false;
	}

	/**
	 * Tells whether a face turn is pointless after the previous one: the same face again (one move would
	 * do), or the opposite face in the order that is already covered by the other one (up then down, never
	 * down then up, since the two commute).
	 */
	private boolean redundant(final int face, final int lastFace) {
		return lastFace >= 0 && (face == lastFace || (face / 2 == lastFace / 2 && face < lastFace));
	}

	/** Tells whether a move is one of the second phase: it keeps the cube in G1. */
	private boolean keepsG1(final int move) {
		for (int allowed : TwoPhaseTables.PHASE_TWO_MOVES) {
			if (allowed == move) {
				return true;
			}
		}
		return false;
	}

	private void countNode() {
		this.nodes++;
		if ((this.nodes & CHECK_MASK) != 0) {
			return;
		}
		if (this.nodes > this.nodeBudget) {
			throw new SolveSearchExhaustedException(
					"Kociemba exceeded its " + this.nodeBudget + "-node search budget without finding a solution");
		}
		if (System.currentTimeMillis() - this.startTime > this.timeBudgetMillis) {
			throw new SolveSearchExhaustedException("Kociemba exceeded its " + (this.timeBudgetMillis / 1000)
					+ "-second search budget without finding a solution, after exploring " + this.nodes
					+ " nodes");
		}
		if (Thread.interrupted()) {
			throw new CancellationException("solve was cancelled");
		}
	}

	private void report(final String phase, final int depth) {
		long now = System.currentTimeMillis();
		if (now - this.lastReport < REPORT_INTERVAL_MILLIS) {
			return;
		}
		this.lastReport = now;
		this.reporter.reportProgress(new SolveProgress(phase, now - this.startTime, depth, this.nodes));
	}
}
