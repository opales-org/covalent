/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.solver.human.impl;

import org.opales.covalent.examples.rubikscubesolver.cube.CubeColor;
import org.opales.covalent.examples.rubikscubesolver.cube.CubeState;
import org.opales.covalent.examples.rubikscubesolver.cube.Face;
import org.opales.covalent.examples.rubikscubesolver.cube.Move;
import org.opales.covalent.examples.rubikscubesolver.cube.ProgressReporter;
import org.opales.covalent.examples.rubikscubesolver.cube.RotationDirection;
import org.opales.covalent.examples.rubikscubesolver.cube.SolveProgress;
import org.opales.covalent.examples.rubikscubesolver.solver.SolveSearchExhaustedException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.Predicate;

/**
 * The beginner's layer-by-layer method, run on a {@link CubeState}.
 * <p>
 * The first layer is the {@link Face#DOWN} face and the last layer the
 * {@link Face#UP} face, so the textbook algorithms apply as written. Seven
 * steps follow one another, each leaving the previous ones intact: the
 * first-layer cross, the first-layer corners, the middle-layer edges, the
 * last-layer cross, the last-layer edges, the last-layer corner positions
 * and the last-layer corner orientations.
 * <p>
 * Each step is a short search over a handful of moves or textbook
 * algorithms (a "macro"), looking for the shortest combination that puts
 * the next piece in place without disturbing what is already solved. The
 * textbook algorithms are written for one side of the cube and applied to
 * the other three by turning them around the vertical axis (see
 * {@link #algorithm}). Every move of the result was applied to a copy of
 * the cube, and the final state is checked, so a returned solution is
 * always genuine.
 */
final class LayerByLayerSolver {

	private static final int STEP_COUNT = 7;

	private static final long PROGRESS_INTERVAL = 5_000L;

	private static final Face[] SIDES = { Face.FRONT, Face.RIGHT, Face.BACK, Face.LEFT };

	private static final Face[] ALL_FACES = Face.values();

	/** Brings the corner of the front-right-down slot out of, or into, place. */
	private static final String CORNER_ALGORITHM = "R U R' U'";

	/** Moves an edge from the last layer into the front-right middle slot. */
	private static final String EDGE_RIGHT_ALGORITHM = "U R U' R' U' F' U F";

	/** Moves an edge from the last layer into the front-left middle slot. */
	private static final String EDGE_LEFT_ALGORITHM = "U' L' U L U F U' F'";

	/** Builds the last-layer cross from a dot, an L or a line. */
	private static final String CROSS_ALGORITHM = "F R U R' U' F'";

	/** Cycles three last-layer edges. */
	private static final String EDGE_CYCLE_ALGORITHM = "R U' R U R U R U' R' U' R2";

	/** Moves the last-layer edges around while keeping the last-layer cross. */
	private static final String EDGE_SUNE_ALGORITHM = "R U R' U R U2 R'";

	/** Cycles three last-layer corners. */
	private static final String CORNER_CYCLE_ALGORITHM = "U R U' L' U R' U' L";

	/** Twists the corner at the up-front-right slot. */
	private static final String CORNER_TWIST_ALGORITHM = "R' D' R D";

	private record Facelet(Face face, int row, int col) {
	}

	/** A small cube position: its coordinates and the facelets it carries. */
	private record Slot(int[] position, List<Facelet> facelets) {

		private boolean isSolved(final CubeState state) {
			for (Facelet facelet : this.facelets) {
				if (state.getFacelet(facelet.face(), facelet.row(), facelet.col()) != CubeState
						.solvedColor(facelet.face())) {
					return false;
				}
			}
			return true;
		}

		private Set<CubeColor> currentColors(final CubeState state) {
			Set<CubeColor> colors = EnumSet.noneOf(CubeColor.class);
			for (Facelet facelet : this.facelets) {
				colors.add(state.getFacelet(facelet.face(), facelet.row(), facelet.col()));
			}
			return colors;
		}

		private Set<CubeColor> solvedColors() {
			Set<CubeColor> colors = EnumSet.noneOf(CubeColor.class);
			for (Facelet facelet : this.facelets) {
				colors.add(CubeState.solvedColor(facelet.face()));
			}
			return colors;
		}
	}

	/** A sequence of moves tried as one unit; {@code face} is set for a single turn. */
	private record Macro(Move[] moves, Face face) {
	}

	private static final Map<Integer, Slot> SLOTS = buildSlots();

	private final ProgressReporter progressReporter;

	private final long startMillis = System.currentTimeMillis();

	private long nodes;

	private int currentStep;

	LayerByLayerSolver(final ProgressReporter progressReporter) {
		this.progressReporter = progressReporter;
	}

	// ------------------------------------------------------------------
	// Geometry.
	// ------------------------------------------------------------------

	private static int key(final int x, final int y, final int z) {
		return (x + 1) * 9 + (y + 1) * 3 + (z + 1);
	}

	private static Map<Integer, Slot> buildSlots() {
		Map<Integer, Slot> slots = new HashMap<>();
		for (Face face : ALL_FACES) {
			for (int row = 0; row < 3; row++) {
				for (int col = 0; col < 3; col++) {
					if (row == 1 && col == 1) {
						continue;
					}
					int[] p = CubeState.cubieOf(face, row, col);
					Slot slot = slots.computeIfAbsent(key(p[0], p[1], p[2]),
							k -> new Slot(p, new ArrayList<>()));
					slot.facelets().add(new Facelet(face, row, col));
				}
			}
		}
		return slots;
	}

	/** The slot at the sum of the outward directions of the given faces. */
	private static Slot slot(final Face... faces) {
		int[] sum = new int[3];
		for (Face face : faces) {
			int[] n = CubeState.normalOf(face);
			for (int i = 0; i < 3; i++) {
				sum[i] += n[i];
			}
		}
		return SLOTS.get(key(sum[0], sum[1], sum[2]));
	}

	private static Face side(final int index) {
		return SIDES[Math.floorMod(index, SIDES.length)];
	}

	private static List<Slot> edgeSlots() {
		List<Slot> edges = new ArrayList<>();
		for (Slot slot : SLOTS.values()) {
			if (slot.facelets().size() == 2) {
				edges.add(slot);
			}
		}
		return edges;
	}

	private static final List<Slot> EDGES = edgeSlots();

	// ------------------------------------------------------------------
	// Moves and algorithms.
	// ------------------------------------------------------------------

	private static Face turned(final Face face, final int quarterTurns) {
		for (int i = 0; i < SIDES.length; i++) {
			if (SIDES[i] == face) {
				return side(i + quarterTurns);
			}
		}
		return face;
	}

	private static Face faceOfLetter(final char letter) {
		return switch (letter) {
			case 'U' -> Face.UP;
			case 'D' -> Face.DOWN;
			case 'F' -> Face.FRONT;
			case 'B' -> Face.BACK;
			case 'L' -> Face.LEFT;
			case 'R' -> Face.RIGHT;
			default -> throw new IllegalArgumentException("unknown face " + letter);
		};
	}

	/** Parses textbook notation and turns it around the vertical axis. */
	private static Move[] algorithm(final String notation, final int quarterTurns) {
		List<Move> moves = new ArrayList<>();
		for (String token : notation.split(" ")) {
			Face face = turned(faceOfLetter(token.charAt(0)), quarterTurns);
			RotationDirection direction = RotationDirection.CLOCKWISE;
			if (token.endsWith("'")) {
				direction = RotationDirection.COUNTERCLOCKWISE;
			} else if (token.endsWith("2")) {
				direction = RotationDirection.HALF_TURN;
			}
			moves.add(new Move(face, direction));
		}
		return moves.toArray(new Move[0]);
	}

	private static Move[] repeated(final Move[] moves, final int times) {
		Move[] result = new Move[moves.length * times];
		for (int i = 0; i < result.length; i++) {
			result[i] = moves[i % moves.length];
		}
		return result;
	}

	private static List<Macro> singleTurns(final Face... faces) {
		List<Macro> macros = new ArrayList<>();
		for (Face face : faces) {
			for (RotationDirection direction : RotationDirection.values()) {
				macros.add(new Macro(new Move[] { new Move(face, direction) }, face));
			}
		}
		return macros;
	}

	private static List<Macro> algorithmsOnAllSides(final String notation, final int minRepeat, final int maxRepeat) {
		List<Macro> macros = new ArrayList<>();
		for (int turns = 0; turns < SIDES.length; turns++) {
			Move[] base = algorithm(notation, turns);
			for (int times = minRepeat; times <= maxRepeat; times++) {
				macros.add(new Macro(repeated(base, times), null));
			}
		}
		return macros;
	}

	private static List<Macro> concat(final List<Macro> first, final List<Macro> second) {
		List<Macro> all = new ArrayList<>(first);
		all.addAll(second);
		return all;
	}

	// ------------------------------------------------------------------
	// Search.
	// ------------------------------------------------------------------

	private void checkCancelled() {
		if (Thread.interrupted()) {
			throw new CancellationException("solve was cancelled");
		}
	}

	private void report(final String text, final int depth) {
		this.progressReporter.reportProgress(new SolveProgress(
				"Step " + this.currentStep + "/" + STEP_COUNT + ": " + text,
				System.currentTimeMillis() - this.startMillis, depth, this.nodes));
	}

	/** Shortest macro sequence, up to {@code maxDepth} macros, reaching the goal. */
	private List<Move> search(final CubeState start, final List<Macro> macros, final Predicate<CubeState> goal,
			final int maxDepth, final String text) {
		for (int depth = 0; depth <= maxDepth; depth++) {
			List<Move> path = new ArrayList<>();
			if (descend(start, macros, goal, depth, null, path, text)) {
				return path;
			}
		}
		throw new SolveSearchExhaustedException("Layer-by-layer step " + this.currentStep + " (" + text
				+ ") found no solution within " + maxDepth + " moves or algorithms");
	}

	private boolean descend(final CubeState state, final List<Macro> macros, final Predicate<CubeState> goal,
			final int remaining, final Face lastFace, final List<Move> path, final String text) {
		this.nodes++;
		if (this.nodes % PROGRESS_INTERVAL == 0) {
			checkCancelled();
			report(text, remaining);
		}
		if (remaining == 0) {
			return goal.test(state);
		}
		for (Macro macro : macros) {
			if (macro.face() != null && macro.face() == lastFace) {
				continue;
			}
			CubeState next = state.clone();
			for (Move move : macro.moves()) {
				next.applyMove(move);
			}
			int size = path.size();
			path.addAll(List.of(macro.moves()));
			if (descend(next, macros, goal, remaining - 1, macro.face(), path, text)) {
				return true;
			}
			while (path.size() > size) {
				path.remove(path.size() - 1);
			}
		}
		return false;
	}

	private CubeState current;

	private final List<Move> solution = new ArrayList<>();

	private void apply(final List<Move> moves) {
		for (Move move : moves) {
			this.current.applyMove(move);
			this.solution.add(move);
		}
	}

	private void step(final int number, final String text) {
		checkCancelled();
		this.currentStep = number;
		report(text, 0);
	}

	private static boolean allSolved(final CubeState state, final List<Slot> slots) {
		for (Slot slot : slots) {
			if (!slot.isSolved(state)) {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------
	// The seven steps.
	// ------------------------------------------------------------------

	Move[] solve(final CubeState cubeState) {
		this.current = cubeState.clone();
		if (this.current.isSolved()) {
			return new Move[0];
		}
		List<Slot> done = new ArrayList<>();
		firstLayerCross(done);
		firstLayerCorners(done);
		middleLayer(done);
		lastLayerCross(done);
		lastLayerEdges(done);
		lastLayerCornerPositions(done);
		lastLayerCornerOrientations();
		if (!this.current.isSolved()) {
			throw new IllegalStateException("layer-by-layer method ended on an unsolved cube");
		}
		return simplify(this.solution);
	}

	private void firstLayerCross(final List<Slot> done) {
		step(1, "first-layer cross");
		List<Macro> turns = singleTurns(ALL_FACES);
		for (Face side : SIDES) {
			Slot target = slot(Face.DOWN, side);
			Set<CubeColor> colors = target.solvedColors();
			if (!allSolved(this.current, done) || !target.isSolved(this.current)) {
				List<Slot> placed = new ArrayList<>(done);
				Predicate<CubeState> pieceInLastLayer = s -> allSolved(s, placed)
						&& EDGES.stream().anyMatch(e -> e.position()[1] == 1 && e.currentColors(s).equals(colors));
				apply(search(this.current, turns, pieceInLastLayer, 3, "first-layer cross"));
				List<Slot> goal = new ArrayList<>(done);
				goal.add(target);
				apply(search(this.current, turns, s -> allSolved(s, goal), 6, "first-layer cross"));
			}
			done.add(target);
		}
	}

	private void firstLayerCorners(final List<Slot> done) {
		step(2, "first-layer corners");
		List<Macro> macros = concat(singleTurns(Face.UP), algorithmsOnAllSides(CORNER_ALGORITHM, 1, 5));
		for (int i = 0; i < SIDES.length; i++) {
			Slot target = slot(Face.DOWN, side(i), side(i + 1));
			done.add(target);
			List<Slot> goal = new ArrayList<>(done);
			if (!allSolved(this.current, goal)) {
				apply(search(this.current, macros, s -> allSolved(s, goal), 4, "first-layer corners"));
			}
		}
	}

	private void middleLayer(final List<Slot> done) {
		step(3, "middle-layer edges");
		List<Macro> macros = concat(singleTurns(Face.UP), concat(algorithmsOnAllSides(EDGE_RIGHT_ALGORITHM, 1, 1),
				algorithmsOnAllSides(EDGE_LEFT_ALGORITHM, 1, 1)));
		for (int i = 0; i < SIDES.length; i++) {
			done.add(slot(side(i), side(i + 1)));
			List<Slot> goal = new ArrayList<>(done);
			if (!allSolved(this.current, goal)) {
				apply(search(this.current, macros, s -> allSolved(s, goal), 5, "middle-layer edges"));
			}
		}
	}

	private static boolean lastLayerCrossDone(final CubeState state) {
		for (Slot edge : EDGES) {
			if (edge.position()[1] == 1) {
				for (Facelet facelet : edge.facelets()) {
					if (facelet.face() == Face.UP
							&& state.getFacelet(Face.UP, facelet.row(), facelet.col()) != CubeState
									.solvedColor(Face.UP)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private void lastLayerCross(final List<Slot> done) {
		step(4, "last-layer cross");
		List<Macro> macros = concat(singleTurns(Face.UP), algorithmsOnAllSides(CROSS_ALGORITHM, 1, 1));
		apply(search(this.current, macros, s -> allSolved(s, done) && lastLayerCrossDone(s), 6, "last-layer cross"));
	}

	private void lastLayerEdges(final List<Slot> done) {
		step(5, "last-layer edges");
		List<Slot> lastLayerEdges = new ArrayList<>();
		for (Face side : SIDES) {
			lastLayerEdges.add(slot(Face.UP, side));
		}
		List<Macro> macros = concat(singleTurns(Face.UP), concat(algorithmsOnAllSides(EDGE_CYCLE_ALGORITHM, 1, 1),
				algorithmsOnAllSides(EDGE_SUNE_ALGORITHM, 1, 1)));
		apply(search(this.current, macros, s -> allSolved(s, done) && allSolved(s, lastLayerEdges), 5,
				"last-layer edges"));
		done.addAll(lastLayerEdges);
	}

	private void lastLayerCornerPositions(final List<Slot> done) {
		step(6, "last-layer corner positions");
		List<Slot> corners = new ArrayList<>();
		for (int i = 0; i < SIDES.length; i++) {
			corners.add(slot(Face.UP, side(i), side(i + 1)));
		}
		List<Macro> macros = algorithmsOnAllSides(CORNER_CYCLE_ALGORITHM, 1, 1);
		Predicate<CubeState> goal = s -> allSolved(s, done) && corners.stream()
				.allMatch(c -> c.currentColors(s).equals(c.solvedColors()));
		apply(search(this.current, macros, goal, 3, "last-layer corner positions"));
	}

	private void lastLayerCornerOrientations() {
		step(7, "last-layer corner orientations");
		Move[] twist = algorithm(CORNER_TWIST_ALGORITHM, 0);
		Move u = new Move(Face.UP, RotationDirection.CLOCKWISE);
		for (int corner = 0; corner < SIDES.length; corner++) {
			int guard = 0;
			while (this.current.getFacelet(Face.UP, 2, 2) != CubeState.solvedColor(Face.UP)) {
				if (guard++ > 6) {
					throw new IllegalStateException("corner twist did not converge");
				}
				checkCancelled();
				apply(List.of(twist));
			}
			apply(List.of(u));
		}
		int turns = 0;
		while (!this.current.isSolved() && turns++ < 4) {
			apply(List.of(u));
		}
	}

	// ------------------------------------------------------------------
	// Output.
	// ------------------------------------------------------------------

	/** Merges consecutive turns of the same face and drops those that cancel out. */
	static Move[] simplify(final List<Move> moves) {
		List<Move> result = new ArrayList<>();
		for (Move move : moves) {
			if (!result.isEmpty() && result.get(result.size() - 1).face() == move.face()) {
				Move previous = result.remove(result.size() - 1);
				int total = quarterTurns(previous.direction()) + quarterTurns(move.direction());
				Move merged = fromQuarterTurns(move.face(), total);
				if (merged != null) {
					result.add(merged);
				}
			} else {
				result.add(move);
			}
		}
		return result.toArray(new Move[0]);
	}

	private static int quarterTurns(final RotationDirection direction) {
		return switch (direction) {
			case CLOCKWISE -> 1;
			case HALF_TURN -> 2;
			case COUNTERCLOCKWISE -> 3;
		};
	}

	private static Move fromQuarterTurns(final Face face, final int quarterTurns) {
		return switch (Math.floorMod(quarterTurns, 4)) {
			case 1 -> new Move(face, RotationDirection.CLOCKWISE);
			case 2 -> new Move(face, RotationDirection.HALF_TURN);
			case 3 -> new Move(face, RotationDirection.COUNTERCLOCKWISE);
			default -> null;
		};
	}
}
