/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.solver.kociemba.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.opales.covalent.examples.rubikscubesolver.cube.CubeColor;
import org.opales.covalent.examples.rubikscubesolver.cube.CubeState;
import org.opales.covalent.examples.rubikscubesolver.cube.Face;
import org.opales.covalent.examples.rubikscubesolver.cube.Move;
import org.opales.covalent.examples.rubikscubesolver.cube.RotationDirection;

/**
 * A cube described by its small cubes instead of its stickers: which of the 8 corner cubies and 12
 * edge cubies sits in each slot, and how it is turned there. This is the form the two-phase search
 * works on, because a move then only shuffles a few small arrays.
 * <p>
 * The cubies are numbered by the slot they occupy when the cube is solved. A corner is oriented
 * {@code 0}, {@code 1} or {@code 2}: the position, among the three stickers of its slot taken in a
 * fixed turning order, of the sticker that shows the up or down color. An edge is oriented {@code 0}
 * when its reference sticker is on the reference face of its slot, {@code 1} otherwise. The
 * reference color of an edge is its up or down color, or its front or back color if it has none
 * (the four middle-layer edges); the reference face of a slot is its up or down face, or its front
 * or back face for the middle layer. With these choices the half turns of every face and the
 * quarter turns of up and down keep every orientation at 0, which is what the first phase of the
 * search aims for.
 * <p>
 * Nothing here knows how the faces turn: the effect of each of the 18 moves is read once from
 * {@link CubeState#applyMove}, so this model cannot disagree with the cube drawn on the screen.
 * Both orientations add: moving a cubie from one slot to another adds a number that depends only
 * on the move and the slot.
 */
final class CubieCube {

	/** Number of moves: three per face, in the order of {@link Face} then of {@link RotationDirection}. */
	static final int MOVE_COUNT = Face.values().length * RotationDirection.values().length;

	static final int CORNERS = 8;

	static final int EDGES = 12;

	/** Edge cubies from this number on belong to the middle layer (the "slice"). */
	static final int FIRST_SLICE_EDGE = 8;

	/** A sticker position: a face, a row and a column of that face. */
	private record Facelet(Face face, int row, int col) {

		CubeColor colorIn(final CubeState state) {
			return state.getFacelet(this.face, this.row, this.col);
		}
	}

	/** Slot positions of the corners, as {@code {x, y, z}}: up layer first, then down layer. */
	private static final int[][] CORNER_POSITIONS = {
			{ 1, 1, 1 }, { -1, 1, 1 }, { -1, 1, -1 }, { 1, 1, -1 },
			{ 1, -1, 1 }, { -1, -1, 1 }, { -1, -1, -1 }, { 1, -1, -1 } };

	/** Slot positions of the edges: up layer, down layer, then the middle layer. */
	private static final int[][] EDGE_POSITIONS = {
			{ 0, 1, 1 }, { 1, 1, 0 }, { 0, 1, -1 }, { -1, 1, 0 },
			{ 0, -1, 1 }, { 1, -1, 0 }, { 0, -1, -1 }, { -1, -1, 0 },
			{ 1, 0, 1 }, { -1, 0, 1 }, { -1, 0, -1 }, { 1, 0, -1 } };

	private static final Facelet[][] CORNER_FACELETS = new Facelet[CORNERS][];

	private static final Facelet[][] EDGE_FACELETS = new Facelet[EDGES][];

	private static final int[] CORNER_ID_BY_COLORS = new int[1 << CubeColor.values().length];

	private static final int[] EDGE_ID_BY_COLORS = new int[1 << CubeColor.values().length];

	private static final CubeColor[] EDGE_REFERENCE_COLOR = new CubeColor[EDGES];

	private static final CubeColor UP_COLOR = CubeState.solvedColor(Face.UP);

	private static final CubeColor DOWN_COLOR = CubeState.solvedColor(Face.DOWN);

	/** For each move and slot, the slot the cubie comes from. */
	private static final int[][] MOVE_CORNER_SOURCE = new int[MOVE_COUNT][CORNERS];

	/** For each move and slot, what the move adds to the orientation of the cubie that arrives. */
	private static final int[][] MOVE_CORNER_TWIST = new int[MOVE_COUNT][CORNERS];

	private static final int[][] MOVE_EDGE_SOURCE = new int[MOVE_COUNT][EDGES];

	private static final int[][] MOVE_EDGE_FLIP = new int[MOVE_COUNT][EDGES];

	static {
		Arrays.fill(CORNER_ID_BY_COLORS, -1);
		Arrays.fill(EDGE_ID_BY_COLORS, -1);
		locateFacelets();
		CubeState solved = new CubeState();
		for (int slot = 0; slot < CORNERS; slot++) {
			CORNER_ID_BY_COLORS[colorMask(CORNER_FACELETS[slot], solved)] = slot;
		}
		for (int slot = 0; slot < EDGES; slot++) {
			EDGE_ID_BY_COLORS[colorMask(EDGE_FACELETS[slot], solved)] = slot;
			EDGE_REFERENCE_COLOR[slot] = EDGE_FACELETS[slot][0].colorIn(solved);
		}
		Face[] faces = Face.values();
		RotationDirection[] directions = RotationDirection.values();
		for (int face = 0; face < faces.length; face++) {
			for (int direction = 0; direction < directions.length; direction++) {
				int move = face * directions.length + direction;
				CubeState turned = new CubeState();
				turned.applyMove(new Move(
						faces[face], directions[direction]));
				CubieCube cube = fromState(turned);
				for (int slot = 0; slot < CORNERS; slot++) {
					MOVE_CORNER_SOURCE[move][slot] = cube.cornerCubie[slot];
					MOVE_CORNER_TWIST[move][slot] = cube.cornerTwist[slot];
				}
				for (int slot = 0; slot < EDGES; slot++) {
					MOVE_EDGE_SOURCE[move][slot] = cube.edgeCubie[slot];
					MOVE_EDGE_FLIP[move][slot] = cube.edgeFlip[slot];
				}
			}
		}
	}

	/** The cubie in each corner slot. */
	final int[] cornerCubie = new int[CORNERS];

	/** The orientation of the cubie in each corner slot, 0 to 2. */
	final int[] cornerTwist = new int[CORNERS];

	/** The cubie in each edge slot. */
	final int[] edgeCubie = new int[EDGES];

	/** The orientation of the cubie in each edge slot, 0 or 1. */
	final int[] edgeFlip = new int[EDGES];

	/** Creates the solved cube. */
	CubieCube() {
		for (int i = 0; i < CORNERS; i++) {
			this.cornerCubie[i] = i;
		}
		for (int i = 0; i < EDGES; i++) {
			this.edgeCubie[i] = i;
		}
	}

	/**
	 * Reads a cube from its stickers.
	 *
	 * @param state the cube to read, not modified
	 * @return the same cube, by cubies
	 * @throws IllegalArgumentException if the stickers do not make up the cubies of a cube
	 */
	static CubieCube fromState(final CubeState state) {
		CubieCube cube = new CubieCube();
		for (int slot = 0; slot < CORNERS; slot++) {
			Facelet[] facelets = CORNER_FACELETS[slot];
			int id = CORNER_ID_BY_COLORS[colorMask(facelets, state)];
			if (id < 0) {
				throw new IllegalArgumentException("Unknown corner at slot " + slot);
			}
			int twist = -1;
			for (int i = 0; i < facelets.length; i++) {
				CubeColor color = facelets[i].colorIn(state);
				if (color == UP_COLOR || color == DOWN_COLOR) {
					twist = i;
				}
			}
			if (twist < 0) {
				throw new IllegalArgumentException("Corner at slot " + slot + " has no up or down color");
			}
			cube.cornerCubie[slot] = id;
			cube.cornerTwist[slot] = twist;
		}
		for (int slot = 0; slot < EDGES; slot++) {
			Facelet[] facelets = EDGE_FACELETS[slot];
			int id = EDGE_ID_BY_COLORS[colorMask(facelets, state)];
			if (id < 0) {
				throw new IllegalArgumentException("Unknown edge at slot " + slot);
			}
			cube.edgeCubie[slot] = id;
			cube.edgeFlip[slot] = facelets[0].colorIn(state) == EDGE_REFERENCE_COLOR[id] ? 0 : 1;
		}
		return cube;
	}

	/**
	 * Returns this cube after a move.
	 *
	 * @param move the move, numbered as {@link #MOVE_COUNT} says
	 * @return a new cube, this one is not modified
	 */
	CubieCube after(final int move) {
		CubieCube next = new CubieCube();
		for (int slot = 0; slot < CORNERS; slot++) {
			int source = MOVE_CORNER_SOURCE[move][slot];
			next.cornerCubie[slot] = this.cornerCubie[source];
			next.cornerTwist[slot] = (this.cornerTwist[source] + MOVE_CORNER_TWIST[move][slot]) % 3;
		}
		for (int slot = 0; slot < EDGES; slot++) {
			int source = MOVE_EDGE_SOURCE[move][slot];
			next.edgeCubie[slot] = this.edgeCubie[source];
			next.edgeFlip[slot] = (this.edgeFlip[source] + MOVE_EDGE_FLIP[move][slot]) & 1;
		}
		return next;
	}

	/**
	 * Tells whether this cube can be solved: the cubies are a permutation, the orientations add up to
	 * a multiple of their modulus, and the corners and the edges are permuted with the same parity.
	 * A cube reached by turning faces always is.
	 *
	 * @return {@code true} if some sequence of moves solves this cube
	 */
	boolean isSolvable() {
		int twists = 0;
		int flips = 0;
		for (int i = 0; i < CORNERS; i++) {
			twists += this.cornerTwist[i];
		}
		for (int i = 0; i < EDGES; i++) {
			flips += this.edgeFlip[i];
		}
		return isPermutation(this.cornerCubie) && isPermutation(this.edgeCubie) && twists % 3 == 0
				&& flips % 2 == 0 && parity(this.cornerCubie) == parity(this.edgeCubie);
	}

	private static boolean isPermutation(final int[] values) {
		boolean[] seen = new boolean[values.length];
		for (int value : values) {
			if (value < 0 || value >= values.length || seen[value]) {
				return false;
			}
			seen[value] = true;
		}
		return true;
	}

	/** Parity of a permutation: the number of inversions, modulo 2. */
	private static int parity(final int[] permutation) {
		int inversions = 0;
		for (int i = 0; i < permutation.length; i++) {
			for (int j = i + 1; j < permutation.length; j++) {
				if (permutation[i] > permutation[j]) {
					inversions++;
				}
			}
		}
		return inversions & 1;
	}

	private static int colorMask(final Facelet[] facelets, final CubeState state) {
		int mask = 0;
		for (Facelet facelet : facelets) {
			mask |= 1 << facelet.colorIn(state).ordinal();
		}
		return mask;
	}

	/** Groups the 54 stickers by the small cube they belong to, and orders them as the class comment says. */
	private static void locateFacelets() {
		List<List<Facelet>> corners = new ArrayList<>();
		List<List<Facelet>> edges = new ArrayList<>();
		for (int i = 0; i < CORNERS; i++) {
			corners.add(new ArrayList<>());
		}
		for (int i = 0; i < EDGES; i++) {
			edges.add(new ArrayList<>());
		}
		for (Face face : Face.values()) {
			for (int row = 0; row < 3; row++) {
				for (int col = 0; col < 3; col++) {
					int[] position = CubeState.cubieOf(face, row, col);
					Facelet facelet = new Facelet(face, row, col);
					int corner = indexOf(CORNER_POSITIONS, position);
					if (corner >= 0) {
						corners.get(corner).add(facelet);
					}
					int edge = indexOf(EDGE_POSITIONS, position);
					if (edge >= 0) {
						edges.get(edge).add(facelet);
					}
				}
			}
		}
		for (int slot = 0; slot < CORNERS; slot++) {
			CORNER_FACELETS[slot] = orderCorner(corners.get(slot));
		}
		for (int slot = 0; slot < EDGES; slot++) {
			EDGE_FACELETS[slot] = orderEdge(edges.get(slot));
		}
	}

	private static int indexOf(final int[][] positions, final int[] position) {
		for (int i = 0; i < positions.length; i++) {
			if (Arrays.equals(positions[i], position)) {
				return i;
			}
		}
		return -1;
	}

	/** Puts the up or down sticker first, then the two others in a fixed turning direction. */
	private static Facelet[] orderCorner(final List<Facelet> stickers) {
		Facelet first = null;
		List<Facelet> others = new ArrayList<>();
		for (Facelet facelet : stickers) {
			if (CubeState.normalOf(facelet.face())[1] != 0) {
				first = facelet;
			} else {
				others.add(facelet);
			}
		}
		int[] a = CubeState.normalOf(first.face());
		int[] b = CubeState.normalOf(others.get(0).face());
		int[] c = CubeState.normalOf(others.get(1).face());
		int determinant = a[0] * (b[1] * c[2] - b[2] * c[1]) - a[1] * (b[0] * c[2] - b[2] * c[0])
				+ a[2] * (b[0] * c[1] - b[1] * c[0]);
		if (determinant < 0) {
			return new Facelet[] { first, others.get(1), others.get(0) };
		}
		return new Facelet[] { first, others.get(0), others.get(1) };
	}

	/** Puts the reference sticker first: the one on an up or down face, or else on a front or back face. */
	private static Facelet[] orderEdge(final List<Facelet> stickers) {
		boolean upOrDownLayer = false;
		for (Facelet facelet : stickers) {
			upOrDownLayer |= CubeState.normalOf(facelet.face())[1] != 0;
		}
		int axis = upOrDownLayer ? 1 : 2;
		Facelet first = CubeState.normalOf(stickers.get(0).face())[axis] != 0 ? stickers.get(0) : stickers.get(1);
		Facelet second = first == stickers.get(0) ? stickers.get(1) : stickers.get(0);
		return new Facelet[] { first, second };
	}
}
