/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.observed;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.lifecycle.DependencyGraph;

/**
 * Mirror of {@link org.opales.covalent.container.lifecycle.DependencyGraphServices},
 * rooted in the <em>observed</em> container - see {@link ObservedRegistryReadServices}
 * for why a distinct type is needed instead of reusing the original. The
 * returned {@link DependencyGraph} itself is an immutable value snapshot,
 * not a port, so it is reused as-is rather than mirrored.
 */
@Services
public interface ObservedDependencyGraphServices {

	/**
	 * Returns the dependency graph computed when the observed container last started.
	 *
	 * @return the last computed dependency graph, or {@code null} when
	 *         checking was disabled at the last {@code start()} call
	 * @throws IllegalStateException if the observed container has never been
	 *                                started
	 */
	DependencyGraph getDependencyGraph();

	/**
	 * Reports whether the observed container's last start computed a dependency graph.
	 *
	 * @return {@code true} when a dependency graph was computed at the last
	 *         {@code start()} call
	 */
	boolean hasDependencyGraph();

}
