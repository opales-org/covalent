/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.cube;

/**
 * The two solving strategies this demo offers, each with its own provider
 * component ({@code KociembaSolver}, {@code HumanSolver}) and its own
 * {@code Services} interface, selected by {@code SolverController} at
 * {@code startSolve(SolvingAlgorithm)}.
 */
public enum SolvingAlgorithm {

	/** Kociemba's two-phase algorithm, tuned through {@code KociembaParameters}. */
	KOCIEMBA,

	/** The layer-by-layer method a person follows, without tunable parameters. */
	HUMAN;
}
