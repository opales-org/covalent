/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.lifecycle.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opales.covalent.container.metamodel.MetamodelReadServices;

/**
 * Builds a {@link DependencyGraphImpl} from the metamodel of a set of
 * deployed root component types, via a three-color depth-first traversal.
 * Port dependencies are built by the shared {@link DependencyGraphEdges};
 * the traversal walks them combined with composition (see
 * {@link #hierarchicalEdges}), so that start order and cycles take into
 * account that a composite is started after its composed children.
 * <p>
 * A cycle is reported only when the traversal revisits a node that is still
 * <em>gray</em> - an ancestor currently on the recursion path. Revisiting a
 * <em>black</em> node - one whose whole subtree already finished, reached
 * again through an unrelated, independent path (a diamond-shaped shared
 * dependency, or two unrelated components both depending on the same third
 * one) - is not a cycle and is silently ignored; this is the exact
 * distinction the naive "already seen once, so it is a cycle" approach gets
 * wrong.
 * <p>
 * Because a node's dependencies always finish (turn black) before the node
 * itself does, appending each node to the result list at the moment it
 * finishes already yields components in dependency order with no reversal
 * needed - including components that belong to a discovered cycle, which are
 * still appended (in the order the traversal happens to finish them) rather
 * than dropped: see {@link org.opales.covalent.container.lifecycle.DependencyGraph#getStartOrder()}.
 * This class only builds and reports the graph; deciding what to do about a
 * found cycle - report it, or fail outright - is
 * {@link LifecycleManagerImpl}'s job, driven by
 * {@link org.opales.covalent.container.CovalentContainerParameters#getStructuralRulesChecking()}.
 * <p>
 * Finding an ancestor's position on the current path, to know which
 * suffix of it forms the cycle, is a {@code Map} lookup ({@code pathIndex}
 * in {@link #visit}), not a linear scan of the path: on an adversarial graph
 * with many long-distance back-edges into a long dependency chain, scanning
 * the path for each one would degrade towards O(V*E); this keeps the whole
 * traversal at O(V+E). Recursion depth is this traversal's one structural
 * limit: an extremely long, purely linear dependency chain (tens of
 * thousands of components deep) could exhaust the JVM call stack; ordinary
 * deployments never come close to that depth.
 */
final class DependencyGraphAnalyzer {

	DependencyGraphImpl analyze(final List<Class<?>> rootComponentTypes,
			final MetamodelReadServices metamodelReadServices) {
		// Root types are not the whole story: a composite's composed children
		// (Model/Controller/View/Interaction, say) have their own ports and
		// their own dependency relationships to each other, invisible to the
		// root type's own aggregated ports (ComponentMetamodel#getAllXxx()
		// folds in only Item metamodels, never ComposedMetamodels). Flattening
		// the whole tree into one node list - root types plus every composed
		// descendant, recursively - lets the same DFS below both order and
		// cycle-check them uniformly, at every nesting level at once.
		Map<Class<?>, Set<Class<?>>> compositions = new LinkedHashMap<>();
		List<Class<?>> allComponentTypes = flatten(rootComponentTypes, metamodelReadServices, compositions);

		Map<Class<?>, Set<Class<?>>> dependencies = DependencyGraphEdges.build(allComponentTypes,
				metamodelReadServices);
		Map<Class<?>, Set<Class<?>>> edges = hierarchicalEdges(allComponentTypes, dependencies, compositions);

		Map<Class<?>, Color> colors = new HashMap<>();
		for (final Class<?> componentType : allComponentTypes) {
			colors.put(componentType, Color.white);
		}

		List<Class<?>> finishOrder = new ArrayList<>();
		List<List<Class<?>>> cycles = new ArrayList<>();
		List<Class<?>> path = new ArrayList<>();
		Map<Class<?>, Integer> pathIndex = new HashMap<>();

		for (final Class<?> componentType : allComponentTypes) {
			if (Color.white.equals(colors.get(componentType))) {
				visit(componentType, edges, colors, path, pathIndex, finishOrder, cycles);
			}
		}

		// finishOrder already covers every component, dependency-first, except
		// among components that share a circular dependency (see visit(): a
		// component finishes, so is appended, only after every dependency it can
		// reach has already finished - including a dependency it shares a cycle
		// with, since a gray revisit does not recurse further but does not block
		// the rest of the traversal either). Components in a cycle are kept here,
		// not dropped: Covalent reports circular dependencies through
		// hasCircularDependencies()/getCircularDependencies() and, at strict
		// verification, fails start() outright - it does not silently omit those
		// components from an otherwise best-effort order.
		return new DependencyGraphImpl(finishOrder, cycles, dependencies, compositions);
	}

	/**
	 * Builds the edges the traversal walks: each composite points to its
	 * direct composed children, and each port dependency {@code S -> T} is
	 * lifted on its target side to {@code S -> T'}, where {@code T'} is the
	 * ancestor of {@code T} (or {@code T} itself) that is a direct child of
	 * the lowest common ancestor of {@code S} and {@code T} - or the root
	 * holding {@code T} when they share no ancestor. A dependency whose target
	 * is {@code S} itself or one of its ancestors adds no edge: a composed
	 * component using a port of the composite that holds it does not make
	 * that composite wait for anything else.
	 * <p>
	 * Lifting expresses that a composite is started only once all of its
	 * composed children are, so depending on any component inside a composite
	 * means waiting for that whole composite. A cycle is therefore found even
	 * when it only appears through composition: with {@code A} composing
	 * {@code A1} and {@code A2}, {@code B} composing {@code B1} and
	 * {@code B2}, {@code A1} depending on {@code B2} and {@code B1} depending
	 * on {@code A2}, the traversal reports {@code A -> A1 -> B -> B1 -> A}.
	 */
	private static Map<Class<?>, Set<Class<?>>> hierarchicalEdges(final List<Class<?>> allComponentTypes,
			final Map<Class<?>, Set<Class<?>>> dependencies, final Map<Class<?>, Set<Class<?>>> compositions) {
		Map<Class<?>, Class<?>> parents = new HashMap<>();
		for (final Map.Entry<Class<?>, Set<Class<?>>> entry : compositions.entrySet()) {
			for (final Class<?> child : entry.getValue()) {
				parents.putIfAbsent(child, entry.getKey());
			}
		}

		Map<Class<?>, Set<Class<?>>> edges = new LinkedHashMap<>();
		for (final Class<?> componentType : allComponentTypes) {
			Set<Class<?>> targets = edges.computeIfAbsent(componentType, key -> new LinkedHashSet<>());
			targets.addAll(compositions.getOrDefault(componentType, Set.of()));

			Set<Class<?>> selfAndAncestors = new HashSet<>();
			for (Class<?> current = componentType; current != null; current = parents.get(current)) {
				selfAndAncestors.add(current);
			}
			for (final Class<?> dependency : dependencies.getOrDefault(componentType, Set.of())) {
				if (selfAndAncestors.contains(dependency)) {
					continue;
				}
				Class<?> lifted = dependency;
				while (parents.get(lifted) != null && !selfAndAncestors.contains(parents.get(lifted))) {
					lifted = parents.get(lifted);
				}
				targets.add(lifted);
			}
		}
		return edges;
	}

	/**
	 * Three-color depth-first visit of one node, recording a cycle whenever
	 * an edge lands back on a still-gray ancestor, and appending the node to
	 * {@code finishOrder} only once every dependency it can reach has
	 * finished.
	 *
	 * @param pathIndex each gray ancestor's current position in {@code path},
	 *                  kept in lockstep with it (put on push, removed on
	 *                  pop) so an ancestor's position is an O(1) lookup
	 *                  instead of an O(path length) {@code path.indexOf}
	 *                  scan - the difference that keeps this traversal at
	 *                  O(V+E) instead of degrading towards O(V*E) on a graph
	 *                  with many long-distance back-edges
	 */
	private static void visit(final Class<?> componentType, final Map<Class<?>, Set<Class<?>>> edges,
			final Map<Class<?>, Color> colors, final List<Class<?>> path, final Map<Class<?>, Integer> pathIndex,
			final List<Class<?>> finishOrder, final List<List<Class<?>>> cycles) {
		colors.put(componentType, Color.gray);
		pathIndex.put(componentType, path.size());
		path.add(componentType);

		for (final Class<?> dependency : edges.getOrDefault(componentType, Set.of())) {
			Color dependencyColor = colors.get(dependency);
			if (Color.white.equals(dependencyColor)) {
				visit(dependency, edges, colors, path, pathIndex, finishOrder, cycles);
			} else if (Color.gray.equals(dependencyColor)) {
				int ancestorIndex = pathIndex.get(dependency);
				cycles.add(new ArrayList<>(path.subList(ancestorIndex, path.size())));
			}
			// Color.black: already fully processed via an unrelated path, not a cycle.
		}

		path.remove(path.size() - 1);
		pathIndex.remove(componentType);
		colors.put(componentType, Color.black);
		finishOrder.add(componentType);
	}

	/**
	 * Recursively collects a root component type and every composed
	 * descendant beneath it (its composed children, their own composed
	 * children, and so on), in encounter order (a parent always precedes its
	 * own children - irrelevant to the DFS below, which re-orders by
	 * dependency anyway, but keeps this list's own order deterministic and
	 * easy to reason about).
	 */
	private static List<Class<?>> flatten(final List<Class<?>> rootComponentTypes,
			final MetamodelReadServices metamodelReadServices, final Map<Class<?>, Set<Class<?>>> compositions) {
		List<Class<?>> allComponentTypes = new ArrayList<>();
		for (final Class<?> rootComponentType : rootComponentTypes) {
			collectWithDescendants(rootComponentType, metamodelReadServices, allComponentTypes, compositions);
		}
		return allComponentTypes;
	}

	/** Also records, in {@code compositions}, each composite's direct composed children. */
	private static void collectWithDescendants(final Class<?> componentType,
			final MetamodelReadServices metamodelReadServices, final List<Class<?>> accumulator,
			final Map<Class<?>, Set<Class<?>>> compositions) {
		accumulator.add(componentType);
		var metamodel = metamodelReadServices.readMetamodel(componentType);
		if (metamodel == null) {
			// Deployed but not yet metamodel-ed (shouldn't happen for a type
			// handed to us by LifecycleManagerImpl, which only calls this once
			// deployment - and therefore metamodel construction - is done; kept
			// defensive rather than assumed).
			return;
		}
		for (final var composed : metamodel.getComposedMetamodels()) {
			compositions.computeIfAbsent(componentType, key -> new LinkedHashSet<>()).add(composed.getComponentType());
			collectWithDescendants(composed.getComponentType(), metamodelReadServices, accumulator, compositions);
		}
	}

	/** Three-color DFS marking: unvisited, on the current path, fully processed. */
	private enum Color {
		white, gray, black;
	}

}
