/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.solver;

/**
 * Thrown by a solving strategy when its search budget (node count or
 * iterative deepening bound) is exhausted before a solution is found,
 * rather than let the search run forever.
 * <p>
 * This is a distinct, unchecked failure mode from cancellation
 * ({@code java.util.concurrent.CancellationException}, raised when the
 * user stops a running solve): a solve that ran to the edge of its budget
 * without being asked to stop failed on its own terms, and
 * {@code SolverController} reports the two differently.
 */
public class SolveSearchExhaustedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates the exception for a search that ran out of budget.
	 *
	 * @param message what budget was exhausted and its value, for logs
	 */
	public SolveSearchExhaustedException(final String message) {
		super(message);
	}
}
