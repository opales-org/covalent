/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.solver.kociemba.impl;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Random;

import org.junit.Test;
import org.opales.covalent.examples.rubikscubesolver.cube.CubeState;
import org.opales.covalent.examples.rubikscubesolver.cube.Face;
import org.opales.covalent.examples.rubikscubesolver.cube.Move;
import org.opales.covalent.examples.rubikscubesolver.cube.RotationDirection;

/**
 * The Kociemba strategy solves any cube, whatever its scramble, and the cubie model it searches on
 * follows the cube drawn on the screen.
 */
public class KociembaSolverImplTest {

	private static final Face[] FACES = Face.values();

	private static final RotationDirection[] DIRECTIONS = RotationDirection.values();

	private static Move randomMove(final Random random) {
		return new Move(FACES[random.nextInt(FACES.length)], DIRECTIONS[random.nextInt(DIRECTIONS.length)]);
	}

	private static CubeState scrambled(final Random random, final int moves) {
		CubeState state = new CubeState();
		for (int i = 0; i < moves; i++) {
			state.applyMove(randomMove(random));
		}
		return state;
	}

	private static void assertSameCubies(final CubieCube expected, final CubieCube actual, final String message) {
		assertArrayEquals(message + " (corners)", expected.cornerCubie, actual.cornerCubie);
		assertArrayEquals(message + " (corner twists)", expected.cornerTwist, actual.cornerTwist);
		assertArrayEquals(message + " (edges)", expected.edgeCubie, actual.edgeCubie);
		assertArrayEquals(message + " (edge flips)", expected.edgeFlip, actual.edgeFlip);
	}

	@Test
	public void theCubieModelFollowsTheStickers() {
		Random random = new Random(11L);
		for (int trial = 0; trial < 300; trial++) {
			CubeState state = scrambled(random, random.nextInt(40));
			CubieCube cubies = CubieCube.fromState(state);
			assertTrue(cubies.isSolvable());
			Move move = randomMove(random);
			int number = move.face().ordinal() * DIRECTIONS.length + move.direction().ordinal();
			CubieCube predicted = cubies.after(number);
			state.applyMove(move);
			assertSameCubies(CubieCube.fromState(state), predicted, "trial " + trial + " " + move);
		}
	}

	@Test
	public void theSecondPhaseMovesKeepTheFirstPhaseGoal() {
		Random random = new Random(12L);
		int[] allowed = TwoPhaseTables.PHASE_TWO_MOVES;
		CubieCube cube = new CubieCube();
		for (int step = 0; step < 500; step++) {
			cube = cube.after(allowed[random.nextInt(allowed.length)]);
			assertEquals(0, TwoPhaseTables.twist(cube));
			assertEquals(0, TwoPhaseTables.flip(cube));
			assertEquals(TwoPhaseTables.SOLVED_SLICE, TwoPhaseTables.slice(cube));
		}
	}

	@Test
	public void anInconsistentCubeIsNotSolvable() {
		CubieCube cube = new CubieCube();
		cube.cornerTwist[0] = 1;
		assertFalse(cube.isSolvable());
		cube = new CubieCube();
		cube.edgeFlip[3] = 1;
		assertFalse(cube.isSolvable());
		cube = new CubieCube();
		cube.edgeCubie[0] = 1;
		cube.edgeCubie[1] = 0;
		assertFalse(cube.isSolvable());
	}

	@Test
	public void aSolvedCubeNeedsNoMove() {
		assertEquals(0, new KociembaSolverImpl().solve(new CubeState(), progress -> { }).length);
	}

	private static CubieCube randomCube(final Random random) {
		CubieCube cube = new CubieCube();
		shuffle(cube.cornerCubie, random);
		shuffle(cube.edgeCubie, random);
		if (parityOf(cube.cornerCubie) != parityOf(cube.edgeCubie)) {
			int swap = cube.edgeCubie[0];
			cube.edgeCubie[0] = cube.edgeCubie[1];
			cube.edgeCubie[1] = swap;
		}
		int twists = 0;
		for (int i = 0; i < CubieCube.CORNERS - 1; i++) {
			cube.cornerTwist[i] = random.nextInt(3);
			twists += cube.cornerTwist[i];
		}
		cube.cornerTwist[CubieCube.CORNERS - 1] = (3 - twists % 3) % 3;
		int flips = 0;
		for (int i = 0; i < CubieCube.EDGES - 1; i++) {
			cube.edgeFlip[i] = random.nextInt(2);
			flips += cube.edgeFlip[i];
		}
		cube.edgeFlip[CubieCube.EDGES - 1] = flips % 2;
		return cube;
	}

	private static void shuffle(final int[] values, final Random random) {
		for (int i = values.length - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			int swap = values[i];
			values[i] = values[j];
			values[j] = swap;
		}
	}

	private static int parityOf(final int[] permutation) {
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

	private static void assertSolves(final CubieCube cube, final String message) {
		assertTrue(message + " is not solvable", cube.isSolvable());
		int[] solution = new TwoPhaseSearch(cube, 24, System.currentTimeMillis(), 60_000L, 200_000_000L,
				progress -> { }).solve();
		CubieCube result = cube;
		for (int move : solution) {
			result = result.after(move);
		}
		assertSameCubies(new CubieCube(), result, message);
		assertTrue(message + " took " + solution.length + " moves", solution.length <= 30);
	}

	@Test
	public void uniformlyRandomCubesAreSolved() {
		Random random = new Random(14L);
		long begin = System.nanoTime();
		for (int trial = 0; trial < 300; trial++) {
			assertSolves(randomCube(random), "random cube " + trial);
		}
		long total = (System.nanoTime() - begin) / 1_000_000;
		System.out.println("Kociemba: 300 uniformly random cubes in " + total + " ms");
	}

	@Test
	public void theSuperflipIsSolved() {
		CubieCube superflip = new CubieCube();
		Arrays.fill(superflip.edgeFlip, 1);
		assertSolves(superflip, "superflip");
	}

	@Test
	public void aSingleTwistedCornerPairIsSolved() {
		CubieCube cube = new CubieCube();
		cube.cornerTwist[0] = 1;
		cube.cornerTwist[1] = 2;
		assertSolves(cube, "two twisted corners");
	}

	@Test
	public void everyScrambleIsSolved() {
		KociembaSolverImpl solver = new KociembaSolverImpl();
		Random random = new Random(13L);
		int[] depths = { 1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 25, 30, 50, 100, 200 };
		int solves = 0;
		int longest = 0;
		long totalLength = 0;
		long slowest = 0;
		long begin = System.nanoTime();
		for (int depth : depths) {
			for (int trial = 0; trial < 25; trial++) {
				CubeState state = scrambled(random, depth);
				long t = System.nanoTime();
				Move[] solution = solver.solve(state, progress -> { });
				slowest = Math.max(slowest, (System.nanoTime() - t) / 1_000_000);
				CubeState check = state.clone();
				for (Move move : solution) {
					check.applyMove(move);
				}
				assertTrue("scramble of " + depth + " moves, trial " + trial, check.isSolved());
				solves++;
				longest = Math.max(longest, solution.length);
				totalLength += solution.length;
			}
		}
		long total = (System.nanoTime() - begin) / 1_000_000;
		System.out.println("Kociemba: " + solves + " solves, average length " + (totalLength / (double) solves)
				+ ", longest " + longest + ", slowest " + slowest + " ms, total " + total + " ms");
		assertTrue("a solution was longer than 12 + 18 moves", longest <= 30);
		assertTrue("the slowest solve took " + slowest + " ms", slowest < 20_000);
	}
}
