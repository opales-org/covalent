/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.lifecycle.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opales.covalent.container.lifecycle.DependencyGraph;

/**
 * Immutable {@link DependencyGraph} snapshot produced by
 * {@link DependencyGraphAnalyzer}. Defensively copies on construction and on
 * every read, matching the module's established defensive-copy convention
 * for collection-returning getters over internal storage.
 */
final class DependencyGraphImpl implements DependencyGraph {

	private final List<Class<?>> startOrder;

	private final List<List<Class<?>>> circularDependencies;

	private final Map<Class<?>, Set<Class<?>>> dependencies;

	private final Map<Class<?>, Set<Class<?>>> compositions;

	/**
	 * Creates a new immutable snapshot, defensively (and, for
	 * {@code dependencies}, deeply) copying every collection.
	 *
	 * @param startOrder            covered component types in dependency
	 *                              order, wherever one can be established
	 *                              (see {@link DependencyGraph#getStartOrder()})
	 * @param circularDependencies  every circular dependency found, each as
	 *                              its component types in traversal order
	 * @param dependencies          for each covered component type, the other
	 *                              covered component types it directly
	 *                              depends on ({@link DependencyGraphEdges#build})
	 * @param compositions          for each covered composite, its direct
	 *                              composed children
	 */
	DependencyGraphImpl(final List<Class<?>> startOrder, final List<List<Class<?>>> circularDependencies,
			final Map<Class<?>, Set<Class<?>>> dependencies, final Map<Class<?>, Set<Class<?>>> compositions) {
		this.compositions = copy(compositions);
		this.startOrder = new ArrayList<>(startOrder);
		this.circularDependencies = new ArrayList<>();
		for (final List<Class<?>> cycle : circularDependencies) {
			this.circularDependencies.add(new ArrayList<>(cycle));
		}
		this.dependencies = new HashMap<>();
		for (final Map.Entry<Class<?>, Set<Class<?>>> entry : dependencies.entrySet()) {
			this.dependencies.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
		}
	}

	@Override
	public List<Class<?>> getStartOrder() {
		return new ArrayList<>(this.startOrder);
	}

	@Override
	public boolean hasCircularDependencies() {
		return !this.circularDependencies.isEmpty();
	}

	@Override
	public List<List<Class<?>>> getCircularDependencies() {
		List<List<Class<?>>> copy = new ArrayList<>();
		for (final List<Class<?>> cycle : this.circularDependencies) {
			copy.add(new ArrayList<>(cycle));
		}
		return copy;
	}

	@Override
	public Map<Class<?>, Set<Class<?>>> getCompositions() {
		return copy(this.compositions);
	}

	private static Map<Class<?>, Set<Class<?>>> copy(final Map<Class<?>, Set<Class<?>>> relations) {
		Map<Class<?>, Set<Class<?>>> copy = new LinkedHashMap<>();
		for (final Map.Entry<Class<?>, Set<Class<?>>> entry : relations.entrySet()) {
			copy.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
		}
		return copy;
	}

	@Override
	public Map<Class<?>, Set<Class<?>>> getDependencies() {
		Map<Class<?>, Set<Class<?>>> copy = new HashMap<>();
		for (final Map.Entry<Class<?>, Set<Class<?>>> entry : this.dependencies.entrySet()) {
			copy.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
		}
		return copy;
	}

}
