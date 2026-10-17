/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.lifecycle.impl;

import java.util.List;
import java.util.function.Supplier;

import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.lifecycle.DependencyGraph;
import org.opales.covalent.container.lifecycle.DependencyGraphServices;
import org.opales.covalent.container.metamodel.MetamodelReadServices;
import org.opales.covalent.container.validation.ValidationServices;

/**
 * Owns every piece of state and logic behind circular dependency detection,
 * so that
 * {@link LifecycleManagerImpl} only has to hold one instance of this class
 * and delegate to it, instead of carrying the enable flag, the computed
 * graph, and the cycle-reporting logic directly as its own fields and
 * methods.
 * <p>
 * A component item of {@code LifecycleManagerImpl}, providing
 * {@link DependencyGraphServices} - the same segmented-component pattern as
 * {@link LifecycleTransitionsImpl}, one step further: this item also carries
 * its own service port rather than only being called directly by its owner,
 * so the dependency graph stays independently discoverable (including from
 * outside the managed component model, through
 * {@link org.opales.covalent.container.raw.RawComponentsServices#useServices(Class)})
 * instead of being one more method on the much broader
 * {@link org.opales.covalent.container.lifecycle.LifecycleServices}.
 * <p>
 * Otherwise self-contained: besides its own {@link #enabled} state,
 * {@link #computeIfEnabled} needs nothing from {@code LifecycleManagerImpl}
 * beyond what it is handed as parameters, and {@link #getDependencyGraph()}
 * tracks its own {@link #computed} flag rather than reaching back into
 * {@code LifecycleManagerImpl}'s container-started state - equivalent, since
 * {@link #computeIfEnabled} only ever runs once, at the very start of every
 * {@code start()} call.
 */
@Item
public class DependencyGraphChecker implements DependencyGraphServices {

	/** Creates a checker with circular dependency detection turned off. */
	public DependencyGraphChecker() {
		// Detection is turned on by setEnabled(boolean).
	}

	private volatile boolean enabled = false;

	private volatile boolean orderedActivation = false;

	/** The graph analyzed by the last {@code start()}; drives ordered activation only. */
	private volatile DependencyGraph startGraph;

	/** Analyzes the container's current structure, on every call. */
	private Supplier<DependencyGraph> liveGraphSource;

	/** {@code true} once {@link #computeIfEnabled} has run at least once. */
	private volatile boolean computed = false;

	/**
	 * Enables or disables circular dependency detection.
	 *
	 * @param enabled {@code true} enables circular dependency detection
	 */
	void setEnabled(final boolean enabled) {
		this.enabled = enabled;
	}

	/**
	 * Reports whether circular dependency detection is enabled.
	 *
	 * @return {@code true} when circular dependency detection is enabled
	 */
	boolean isEnabled() {
		return this.enabled;
	}

	/**
	 * Enables or disables ordering component activation by the computed
	 * dependency graph's {@link DependencyGraph#getStartOrder()}, instead of
	 * raw declaration/deployment order. Independent of {@link #enabled}:
	 * either flag alone is enough for {@link #computeIfEnabled} to compute
	 * the graph, since this one needs it just as much as cycle detection
	 * does.
	 *
	 * @param orderedActivation {@code true} orders activation by computed
	 *                           dependency order
	 */
	void setOrderedActivation(final boolean orderedActivation) {
		this.orderedActivation = orderedActivation;
	}

	/**
	 * Reports whether activation is ordered by the computed dependency
	 * graph.
	 *
	 * @return {@code true} when dependency-ordered activation is enabled
	 */
	boolean isOrderedActivation() {
		return this.orderedActivation;
	}

	/**
	 * Sets the analysis run by {@link #getDependencyGraph()}.
	 *
	 * @param liveGraphSource analyzes the container's current structure
	 */
	void setLiveGraphSource(final Supplier<DependencyGraph> liveGraphSource) {
		this.liveGraphSource = liveGraphSource;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Analyzes the container's current structure on every call, whether or
	 * not circular dependency checking is enabled.
	 */
	@Override
	public DependencyGraph getDependencyGraph() {
		if (!this.computed) {
			throw new IllegalStateException("Not started");
		}
		return this.liveGraphSource.get();
	}

	@Override
	public boolean hasDependencyGraph() {
		return this.computed;
	}

	/**
	 * Reports whether the last {@code start()} analyzed the dependency graph.
	 *
	 * @return {@code true} when {@link #getStartGraph()} returns a graph
	 */
	boolean hasStartGraph() {
		return this.computed && this.startGraph != null;
	}

	/**
	 * The graph analyzed by the last {@code start()}, which dependency-ordered
	 * activation follows.
	 *
	 * @return the start-time graph
	 */
	DependencyGraph getStartGraph() {
		return this.startGraph;
	}

	/**
	 * Resets {@link #computed} so {@link #getDependencyGraph()} throws again
	 * until the next {@code start()}, mirroring
	 * {@link LifecycleManagerImpl}'s own {@code started} flag being cleared
	 * on {@code stop()}. Without this, a container that was started, then
	 * stopped, would keep returning the graph computed by its last
	 * {@code start()} instead of correctly reporting that it is not
	 * currently started.
	 */
	void stopped() {
		this.computed = false;
	}

	/**
	 * Computes and stores the dependency graph for the given deployed root
	 * component types when {@link #isEnabled()}; clears it to {@code null}
	 * otherwise. Any circular dependency found is reported once, per cycle,
	 * through {@link ValidationServices#handleStructuralVerification(String)}
	 * - the same structural rules verification level as every other
	 * structural rule, so a {@code strict} level throws here, propagating to
	 * the caller.
	 *
	 * @param rootComponentTypes    deployed root component types to analyze
	 * @param metamodelReadServices metamodel reader used to resolve each
	 *                              component type's declared ports
	 * @param validationServices    used to report a found cycle at the
	 *                              configured structural rules verification
	 *                              level
	 */
	void computeIfEnabled(final List<Class<?>> rootComponentTypes, final MetamodelReadServices metamodelReadServices,
			final ValidationServices validationServices) {
		if (!this.enabled && !this.orderedActivation) {
			this.startGraph = null;
			this.computed = true;
			return;
		}
		DependencyGraph graph = new DependencyGraphAnalyzer().analyze(rootComponentTypes, metamodelReadServices);
		// startGraph is published before computed, which readers test first.
		this.startGraph = graph;
		this.computed = true;
		for (final List<Class<?>> cycle : graph.getCircularDependencies()) {
			validationServices.handleStructuralVerification(formatCycleMessage(cycle));
		}
	}

	private static String formatCycleMessage(final List<Class<?>> cycle) {
		StringBuilder cycleDescription = new StringBuilder();
		for (final Class<?> componentType : cycle) {
			if (cycleDescription.length() > 0) {
				cycleDescription.append(" -> ");
			}
			cycleDescription.append(componentType.getSimpleName());
		}
		cycleDescription.append(" -> ").append(cycle.get(0).getSimpleName());
		return "Circular Dependency: " + cycleDescription + "\n"
				+ "Each component type in this cycle either composes the next one, or depends on it "
				+ "or on one of its composed components through a service, event, or parameter port, "
				+ "so no valid start order exists for them.";
	}

}
