/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.cube;

/**
 * A plain Java callback a solving strategy uses to report progress back to
 * {@code SolverController} while it runs, deliberately not a Covalent
 * {@code @Events}/{@code @Services} contract of its own.
 * <p>
 * This is deliberate: Covalent structurally forbids two different component
 * types from producing, or providing, the same {@code @Events} or
 * {@code @Services} interface: the container reports it as a structural
 * verification failure. This
 * demo has two independent solving strategy providers
 * ({@code KociembaSolver}, {@code HumanSolver}), each running its own
 * search and each needing to report progress the same way; if progress
 * reporting were instead a shared {@code @Events} interface, only the first
 * of the two to declare itself its producer would actually be allowed to
 * produce it, and the framework would report the second (or, depending on
 * the configured verification level, silently ignore it). A plain
 * functional interface has no such restriction: each strategy is simply
 * handed one as an ordinary method argument by {@code SolverController},
 * which constructs a fresh lambda per solve that forwards every callback
 * invocation into {@code SolverModelServices#setProgress(SolveProgress)}.
 * This keeps {@code SolverModel} the single, unambiguous producer of
 * {@code SolverEvents}, with neither strategy provider ever touching an
 * events port directly.
 */
@FunctionalInterface
public interface ProgressReporter {

	/**
	 * Called by a solving strategy, as often as it likes while it runs, to
	 * report its current progress.
	 *
	 * @param progress the current progress
	 */
	void reportProgress(SolveProgress progress);
}
