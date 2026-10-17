/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.solver.kociemba;

import org.opales.covalent.container.component.annotations.Parameters;

/**
 * Tuning for the Kociemba two-phase strategy, owned internally by
 * {@code KociembaSolver} and accessed externally by {@code UserConfiguration}
 * (see the Functions Overview architecture diagram). It is modeled as
 * {@code @Parameters} rather than {@code @Services} because it is a plain
 * tunable value read and written from outside, not an operation.
 */
@Parameters
public interface KociembaParameters {

	/**
	 * Returns the depth beyond which a phase 2 search gives up.
	 *
	 * @return the current maximum search depth for phase 2
	 */
	int getMaxDepth();

	/**
	 * Sets the maximum search depth for phase 2 (finding a solution within
	 * the G1 subgroup): a hard cap on how long a phase 2 search is allowed
	 * to run before giving up with a search-exhausted error.
	 *
	 * @param maxDepth the new maximum depth, expected to be positive
	 */
	void setMaxDepth(int maxDepth);
}
