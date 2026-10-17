/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.solver.kociemba.impl;

import java.util.Arrays;

import org.opales.covalent.examples.rubikscubesolver.cube.Face;
import org.opales.covalent.examples.rubikscubesolver.cube.RotationDirection;

/**
 * The numbers the two-phase search works with: for each part of the cube's state a small integer
 * (a coordinate), a table giving the coordinate after each move, and a table of the exact number of
 * moves needed to bring a pair of coordinates to their solved value.
 * <p>
 * The first phase brings the cube into the group G1, where every orientation is 0 and the four
 * middle-layer edges are in the middle layer, using the 18 moves. It uses three coordinates: the
 * twist of the corners (2187 values), the flip of the edges (2048 values) and which 4 of the 12 edge
 * slots hold the middle-layer edges (495 values). The second phase solves the cube using only the
 * 10 moves that keep it in G1 (quarter and half turns of up and down, half turns of the four other
 * faces). It uses the permutation of the corners (40320 values), of the 8 up and down layer edges
 * (40320 values) and of the 4 middle-layer edges (24 values).
 * <p>
 * Each pruning table is the result of a breadth-first search over a pair of coordinates, so its value
 * is the exact distance for that pair, and the larger of two such values never overestimates the
 * distance of the whole cube. The tables are built once, when this class is first used, which takes
 * well under a second.
 */
final class TwoPhaseTables {

	static final int TWIST_COUNT = 2187;

	static final int FLIP_COUNT = 2048;

	static final int SLICE_COUNT = 495;

	static final int CORNER_PERMUTATION_COUNT = 40320;

	static final int EDGE_PERMUTATION_COUNT = 40320;

	static final int SLICE_PERMUTATION_COUNT = 24;

	private static final int DIRECTIONS = RotationDirection.values().length;

	private static final int MOVES = CubieCube.MOVE_COUNT;

	/** The moves of the second phase, numbered as in {@link CubieCube}. */
	static final int[] PHASE_TWO_MOVES = phaseTwoMoves();

	private static final int PHASE_TWO_MOVE_COUNT = PHASE_TWO_MOVES.length;

	/** The slice coordinate of the solved cube: the middle-layer edges are in slots 8 to 11. */
	static final int SOLVED_SLICE;

	private static final int[] SLICE_OF_MASK = new int[1 << CubieCube.EDGES];

	private static final int[] MASK_OF_SLICE = new int[SLICE_COUNT];

	/** The next twist, indexed by {@code twist * 18 + move}. */
	static final int[] TWIST_MOVE = new int[TWIST_COUNT * MOVES];

	static final int[] FLIP_MOVE = new int[FLIP_COUNT * MOVES];

	static final int[] SLICE_MOVE = new int[SLICE_COUNT * MOVES];

	/** The next corner permutation, indexed by {@code coordinate * 10 + index in PHASE_TWO_MOVES}. */
	static final int[] CORNER_PERMUTATION_MOVE = new int[CORNER_PERMUTATION_COUNT * PHASE_TWO_MOVE_COUNT];

	static final int[] EDGE_PERMUTATION_MOVE = new int[EDGE_PERMUTATION_COUNT * PHASE_TWO_MOVE_COUNT];

	static final int[] SLICE_PERMUTATION_MOVE = new int[SLICE_PERMUTATION_COUNT * PHASE_TWO_MOVE_COUNT];

	/** Distance to solved for a twist and a slice, indexed by {@code twist * 495 + slice}. */
	static final byte[] TWIST_SLICE_DISTANCE;

	/** Distance to solved for a flip and a slice, indexed by {@code flip * 495 + slice}. */
	static final byte[] FLIP_SLICE_DISTANCE;

	/** Distance to solved for a corner permutation and a slice permutation, second phase moves. */
	static final byte[] CORNER_SLICE_DISTANCE;

	/** Distance to solved for an edge permutation and a slice permutation, second phase moves. */
	static final byte[] EDGE_SLICE_DISTANCE;

	static {
		buildSliceNumbering();
		SOLVED_SLICE = SLICE_OF_MASK[0xF00];
		buildPhaseOneMoveTables();
		buildPhaseTwoMoveTables();
		TWIST_SLICE_DISTANCE = distances(TWIST_COUNT, SLICE_COUNT, TWIST_MOVE, SLICE_MOVE, MOVES, 0, SOLVED_SLICE);
		FLIP_SLICE_DISTANCE = distances(FLIP_COUNT, SLICE_COUNT, FLIP_MOVE, SLICE_MOVE, MOVES, 0, SOLVED_SLICE);
		CORNER_SLICE_DISTANCE = distances(CORNER_PERMUTATION_COUNT, SLICE_PERMUTATION_COUNT,
				CORNER_PERMUTATION_MOVE, SLICE_PERMUTATION_MOVE, PHASE_TWO_MOVE_COUNT, 0, 0);
		EDGE_SLICE_DISTANCE = distances(EDGE_PERMUTATION_COUNT, SLICE_PERMUTATION_COUNT,
				EDGE_PERMUTATION_MOVE, SLICE_PERMUTATION_MOVE, PHASE_TWO_MOVE_COUNT, 0, 0);
	}

	private TwoPhaseTables() {
	}

	// --- coordinates of a cube ---------------------------------------------------------------

	static int twist(final CubieCube cube) {
		int value = 0;
		for (int i = CubieCube.CORNERS - 2; i >= 0; i--) {
			value = value * 3 + cube.cornerTwist[i];
		}
		return value;
	}

	static int flip(final CubieCube cube) {
		int value = 0;
		for (int i = CubieCube.EDGES - 2; i >= 0; i--) {
			value = value * 2 + cube.edgeFlip[i];
		}
		return value;
	}

	static int slice(final CubieCube cube) {
		int mask = 0;
		for (int slot = 0; slot < CubieCube.EDGES; slot++) {
			if (cube.edgeCubie[slot] >= CubieCube.FIRST_SLICE_EDGE) {
				mask |= 1 << slot;
			}
		}
		return SLICE_OF_MASK[mask];
	}

	/** The permutation of the corners. */
	static int cornerPermutation(final CubieCube cube) {
		return rank(cube.cornerCubie, 0, CubieCube.CORNERS);
	}

	/** The permutation of the 8 up and down layer edges, valid once the middle-layer edges are in place. */
	static int edgePermutation(final CubieCube cube) {
		return rank(cube.edgeCubie, 0, CubieCube.FIRST_SLICE_EDGE);
	}

	/** The permutation of the 4 middle-layer edges, valid once they are in place. */
	static int slicePermutation(final CubieCube cube) {
		int[] values = new int[4];
		for (int i = 0; i < 4; i++) {
			values[i] = cube.edgeCubie[CubieCube.FIRST_SLICE_EDGE + i] - CubieCube.FIRST_SLICE_EDGE;
		}
		return rank(values, 0, 4);
	}

	// --- construction ---------------------------------------------------------------------------

	private static int[] phaseTwoMoves() {
		int[] moves = new int[10];
		int count = 0;
		for (int face = 0; face < Face.values().length; face++) {
			boolean upOrDown = face == Face.UP.ordinal() || face == Face.DOWN.ordinal();
			for (int direction = 0; direction < DIRECTIONS; direction++) {
				if (upOrDown || direction == RotationDirection.HALF_TURN.ordinal()) {
					moves[count++] = face * DIRECTIONS + direction;
				}
			}
		}
		return moves;
	}

	private static void buildSliceNumbering() {
		int rank = 0;
		for (int mask = 0; mask < SLICE_OF_MASK.length; mask++) {
			if (Integer.bitCount(mask) == 4) {
				SLICE_OF_MASK[mask] = rank;
				MASK_OF_SLICE[rank] = mask;
				rank++;
			}
		}
	}

	private static void buildPhaseOneMoveTables() {
		for (int move = 0; move < MOVES; move++) {
			for (int value = 0; value < TWIST_COUNT; value++) {
				CubieCube cube = new CubieCube();
				int rest = value;
				int sum = 0;
				for (int i = 0; i < CubieCube.CORNERS - 1; i++) {
					cube.cornerTwist[i] = rest % 3;
					sum += cube.cornerTwist[i];
					rest /= 3;
				}
				cube.cornerTwist[CubieCube.CORNERS - 1] = (3 - sum % 3) % 3;
				TWIST_MOVE[value * MOVES + move] = twist(cube.after(move));
			}
			for (int value = 0; value < FLIP_COUNT; value++) {
				CubieCube cube = new CubieCube();
				int rest = value;
				int sum = 0;
				for (int i = 0; i < CubieCube.EDGES - 1; i++) {
					cube.edgeFlip[i] = rest & 1;
					sum += cube.edgeFlip[i];
					rest >>= 1;
				}
				cube.edgeFlip[CubieCube.EDGES - 1] = sum & 1;
				FLIP_MOVE[value * MOVES + move] = flip(cube.after(move));
			}
			for (int value = 0; value < SLICE_COUNT; value++) {
				CubieCube cube = new CubieCube();
				int mask = MASK_OF_SLICE[value];
				int nextSlice = CubieCube.FIRST_SLICE_EDGE;
				int other = 0;
				for (int slot = 0; slot < CubieCube.EDGES; slot++) {
					cube.edgeCubie[slot] = (mask & (1 << slot)) != 0 ? nextSlice++ : other++;
				}
				SLICE_MOVE[value * MOVES + move] = slice(cube.after(move));
			}
		}
	}

	private static void buildPhaseTwoMoveTables() {
		for (int index = 0; index < PHASE_TWO_MOVE_COUNT; index++) {
			int move = PHASE_TWO_MOVES[index];
			for (int value = 0; value < CORNER_PERMUTATION_COUNT; value++) {
				CubieCube cube = new CubieCube();
				unrank(value, cube.cornerCubie, 0, CubieCube.CORNERS);
				CORNER_PERMUTATION_MOVE[value * PHASE_TWO_MOVE_COUNT + index] = cornerPermutation(cube.after(move));
			}
			for (int value = 0; value < EDGE_PERMUTATION_COUNT; value++) {
				CubieCube cube = new CubieCube();
				unrank(value, cube.edgeCubie, 0, CubieCube.FIRST_SLICE_EDGE);
				EDGE_PERMUTATION_MOVE[value * PHASE_TWO_MOVE_COUNT + index] = edgePermutation(cube.after(move));
			}
			for (int value = 0; value < SLICE_PERMUTATION_COUNT; value++) {
				CubieCube cube = new CubieCube();
				int[] values = new int[4];
				unrank(value, values, 0, 4);
				for (int i = 0; i < 4; i++) {
					cube.edgeCubie[CubieCube.FIRST_SLICE_EDGE + i] = CubieCube.FIRST_SLICE_EDGE + values[i];
				}
				SLICE_PERMUTATION_MOVE[value * PHASE_TWO_MOVE_COUNT + index] = slicePermutation(cube.after(move));
			}
		}
	}

	/**
	 * Breadth-first search from the solved pair of coordinates.
	 *
	 * @param firstCount number of values of the first coordinate
	 * @param secondCount number of values of the second coordinate
	 * @param firstMove move table of the first coordinate
	 * @param secondMove move table of the second coordinate
	 * @param moveCount number of columns of both move tables
	 * @param solvedFirst the solved value of the first coordinate
	 * @param solvedSecond the solved value of the second coordinate
	 * @return for each pair, indexed by {@code first * secondCount + second}, the number of moves to solve it
	 */
	private static byte[] distances(final int firstCount, final int secondCount, final int[] firstMove,
			final int[] secondMove, final int moveCount, final int solvedFirst, final int solvedSecond) {
		byte[] distance = new byte[firstCount * secondCount];
		Arrays.fill(distance, (byte) -1);
		distance[solvedFirst * secondCount + solvedSecond] = 0;
		int filled = 1;
		boolean grew = true;
		for (int depth = 0; grew && filled < distance.length; depth++) {
			grew = false;
			for (int index = 0; index < distance.length; index++) {
				if (distance[index] != depth) {
					continue;
				}
				int first = index / secondCount;
				int second = index % secondCount;
				for (int column = 0; column < moveCount; column++) {
					int next = firstMove[first * moveCount + column] * secondCount
							+ secondMove[second * moveCount + column];
					if (distance[next] < 0) {
						distance[next] = (byte) (depth + 1);
						filled++;
						grew = true;
					}
				}
			}
		}
		return distance;
	}

	// --- permutations ---------------------------------------------------------------------------

	/** Number of a permutation of {@code 0..length-1} stored in {@code values[offset..]}, in lexicographic order. */
	static int rank(final int[] values, final int offset, final int length) {
		int rank = 0;
		for (int i = 0; i < length; i++) {
			int smaller = 0;
			for (int j = i + 1; j < length; j++) {
				if (values[offset + j] < values[offset + i]) {
					smaller++;
				}
			}
			rank = rank * (length - i) + smaller;
		}
		return rank;
	}

	/** The inverse of {@link #rank}. */
	static void unrank(final int rank, final int[] values, final int offset, final int length) {
		int[] digits = new int[length];
		int rest = rank;
		for (int i = length - 1; i >= 0; i--) {
			int base = length - i;
			digits[i] = rest % base;
			rest /= base;
		}
		boolean[] used = new boolean[length];
		for (int i = 0; i < length; i++) {
			int skip = digits[i];
			for (int candidate = 0; candidate < length; candidate++) {
				if (!used[candidate]) {
					if (skip == 0) {
						values[offset + i] = candidate;
						used[candidate] = true;
						break;
					}
					skip--;
				}
			}
		}
	}
}
