/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.lifecycle;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Exposes the managed component dependency graph, provided by the
 * dependency graph tracking item of the container's lifecycle manager.
 * Kept as its own
 * focused service, separate from the broader {@link LifecycleServices},
 * so this concern stays independently discoverable and consumable -
 * including from outside the managed component model, through
 * {@link org.opales.covalent.container.raw.RawComponentsServices#useServices(Class)}.
 */
@Services
public interface DependencyGraphServices {

	/**
	 * Returns the dependency graph of the container's current structure.
	 * <p>
	 * Throws while the container is not started (before its first start, or
	 * after it has been stopped), rather than silently returning an empty
	 * graph: there is nothing to report, and calling outside a started
	 * container is almost always a mistake - wait until the container is
	 * started. While started, analyzes the container's current structure on
	 * every call, so components deployed or removed since {@code start()} are
	 * reflected. Independent of
	 * {@link org.opales.covalent.container.CovalentContainerParameters#isCircularDependencyChecking()},
	 * which only governs the verification performed at {@code start()}.
	 *
	 * @return the dependency graph of the container's current structure
	 * @throws IllegalStateException if the container is not started
	 */
	DependencyGraph getDependencyGraph();

	/**
	 * Reports whether {@link #getDependencyGraph()} can be called without
	 * throwing, so a caller can check first instead of calling it
	 * speculatively.
	 *
	 * @return {@code true} while the container is started
	 */
	boolean hasDependencyGraph();

}
