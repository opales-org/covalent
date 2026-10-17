/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.dependencymanagement.view.impl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opales.covalent.supervision.dependencymanagement.model.ComponentKind;
import org.opales.covalent.supervision.dependencymanagement.model.DependencyManagementGraph;

/**
 * The part of the dependency graph the component tree scopes it to, as plain functions: a
 * composite and all its descendants down to the leaf components, the dependencies between them
 * and, on demand, the components outside that subtree linked to it, both ways.
 */
final class DependencyScope {

	private DependencyScope() {
	}

	/**
	 * Returns a component and all its descendants.
	 *
	 * @param compositions each composite's direct children
	 * @param root         the component the graph is scoped to
	 * @return {@code root} and every component it composes, directly or not
	 */
	static Set<Class<?>> subtree(final Map<Class<?>, Set<Class<?>>> compositions, final Class<?> root) {
		Set<Class<?>> subtree = new LinkedHashSet<>();
		Deque<Class<?>> pending = new ArrayDeque<>(List.of(root));
		while (!pending.isEmpty()) {
			Class<?> type = pending.removeFirst();
			if (subtree.add(type)) {
				pending.addAll(compositions.getOrDefault(type, Set.of()));
			}
		}
		return subtree;
	}

	/**
	 * Returns the components outside a subtree that one of its components depends on, or that
	 * depend on one of them.
	 *
	 * @param graph   the whole graph
	 * @param subtree what {@link #subtree} returned
	 * @return the outside neighbors, in no particular order
	 */
	static Set<Class<?>> outsideNeighbors(final DependencyManagementGraph graph, final Set<Class<?>> subtree) {
		Set<Class<?>> neighbors = new LinkedHashSet<>();
		graph.getDependencies().forEach((from, dependsOn) -> {
			for (Class<?> to : dependsOn) {
				if (subtree.contains(from) && !subtree.contains(to)) {
					neighbors.add(to);
				} else if (!subtree.contains(from) && subtree.contains(to)) {
					neighbors.add(from);
				}
			}
		});
		return neighbors;
	}

	/**
	 * Counts the dependencies with one end in a subtree and the other outside it.
	 *
	 * @param graph   the whole graph
	 * @param subtree what {@link #subtree} returned
	 * @return how many dependencies cross the subtree's border, both ways
	 */
	static int outsideLinks(final DependencyManagementGraph graph, final Set<Class<?>> subtree) {
		int count = 0;
		for (Map.Entry<Class<?>, Set<Class<?>>> entry : graph.getDependencies().entrySet()) {
			for (Class<?> to : entry.getValue()) {
				if (subtree.contains(entry.getKey()) != subtree.contains(to)) {
					count++;
				}
			}
		}
		return count;
	}

	/**
	 * Scopes a graph to a component and its descendants.
	 *
	 * @param graph      the whole graph
	 * @param root       the component the graph is scoped to, {@code null} for the whole graph
	 * @param neighbors whether the components outside the subtree linked to it are kept too
	 * @param showRoot   whether {@code root} itself, and the composition links from it to its children, are kept;
	 *                   without it only its content is shown, and no outside neighbor is kept for a link it alone had
	 * @return the subtree's components, the dependencies and compositions between them, and the
	 *         cycles shown whole; with {@code neighbors}, also the outside neighbors and
	 *         their dependencies with the subtree - never those between two neighbors
	 */
	static DependencyManagementGraph scope(final DependencyManagementGraph graph, final Class<?> root,
			final boolean neighbors, final boolean showRoot) {
		if (root == null || !graph.getComponents().contains(root)) {
			return graph;
		}
		Set<Class<?>> subtree = subtree(graph.getCompositions(), root);
		subtree.retainAll(graph.getComponents());
		if (!showRoot) {
			subtree.remove(root);
		}
		Set<Class<?>> shown = new LinkedHashSet<>(subtree);
		if (neighbors) {
			Set<Class<?>> outside = outsideNeighbors(graph, subtree);
			outside.remove(root);
			shown.addAll(outside);
		}
		Map<Class<?>, Set<Class<?>>> dependencies = new LinkedHashMap<>();
		graph.getDependencies().forEach((from, dependsOn) -> {
			Set<Class<?>> kept = new LinkedHashSet<>();
			for (Class<?> to : dependsOn) {
				if (shown.contains(from) && shown.contains(to) && (subtree.contains(from) || subtree.contains(to))) {
					kept.add(to);
				}
			}
			if (!kept.isEmpty()) {
				dependencies.put(from, kept);
			}
		});
		Map<Class<?>, Set<Class<?>>> compositions = new LinkedHashMap<>();
		graph.getCompositions().forEach((composite, children) -> {
			if (!subtree.contains(composite)) {
				return;
			}
			Set<Class<?>> kept = new LinkedHashSet<>(children);
			kept.retainAll(subtree);
			if (!kept.isEmpty()) {
				compositions.put(composite, kept);
			}
		});
		List<List<Class<?>>> cycles = new ArrayList<>();
		for (List<Class<?>> cycle : graph.getCircularDependencies()) {
			if (shown.containsAll(cycle) && !hasStepBetweenNeighbors(cycle, subtree)) {
				cycles.add(cycle);
			}
		}
		Map<Class<?>, ComponentKind> kinds = new LinkedHashMap<>();
		graph.getKinds().forEach((type, kind) -> {
			if (shown.contains(type)) {
				kinds.put(type, kind);
			}
		});
		return new DependencyManagementGraph(shown, dependencies, cycles, compositions, graph.getContainerName(), kinds);
	}

	/** Whether a step of the cycle goes from one outside neighbor to another: that link is not shown. */
	private static boolean hasStepBetweenNeighbors(final List<Class<?>> cycle, final Set<Class<?>> subtree) {
		for (int i = 0; i < cycle.size(); i++) {
			if (!subtree.contains(cycle.get(i)) && !subtree.contains(cycle.get((i + 1) % cycle.size()))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns the composites holding a component, from the outermost one.
	 *
	 * @param compositions each composite's direct children
	 * @param type         a component
	 * @return its ancestors, the outermost first, then {@code type} itself
	 */
	static List<Class<?>> path(final Map<Class<?>, Set<Class<?>>> compositions, final Class<?> type) {
		Map<Class<?>, Class<?>> parents = new HashMap<>();
		compositions.forEach((composite, children) -> children.forEach(child -> parents.putIfAbsent(child, composite)));
		List<Class<?>> path = new ArrayList<>();
		Set<Class<?>> seen = new LinkedHashSet<>();
		for (Class<?> current = type; current != null && seen.add(current); current = parents.get(current)) {
			path.add(0, current);
		}
		return path;
	}
}
