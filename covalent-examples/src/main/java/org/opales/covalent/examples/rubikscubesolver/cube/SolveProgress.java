/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.cube;

/**
 * One progress update from a solving strategy, generic enough to represent
 * any algorithm's honest progress: a search-based strategy naturally reports
 * {@code searchDepth} and {@code nodesExplored}, while a phased approach
 * naturally reports {@code phase} and {@code elapsedMillis} -
 * either strategy leaves the fields it has nothing honest to report at
 * 0 or an empty string, rather than fabricating a value for a field that
 * does not apply to it.
 *
 * @param phase          a short, human-readable description of what the
 *                        solver is currently doing (e.g. "phase 1: reaching
 *                        the G1 subgroup" for Kociemba); empty if not
 *                        meaningful for the reporting algorithm
 * @param elapsedMillis  milliseconds elapsed since the solve started; 0 if
 *                        not tracked
 * @param searchDepth    the current search depth, for a depth-first search
 *                        such as iterative deepening; 0 if not meaningful for the
 *                        reporting algorithm
 * @param nodesExplored  how many search nodes have been explored so far; 0
 *                        if not meaningful for the reporting algorithm
 */
public record SolveProgress(String phase, long elapsedMillis, int searchDepth, long nodesExplored) {
}
