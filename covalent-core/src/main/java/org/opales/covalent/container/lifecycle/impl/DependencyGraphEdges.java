/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.lifecycle.impl;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opales.covalent.container.metamodel.MetamodelReadServices;
import org.opales.covalent.container.metamodel.metamodel.ReadOnlyComponentMetamodel;

/**
 * Builds the directed dependency edge map used by {@link DependencyGraphAnalyzer}'s
 * three-color depth-first traversal.
 * <p>
 * An edge from component A to component B means A depends on B: A declares
 * a used service, a consumed event, or an accessed parameter whose
 * interface B's implementation or items provide, produce, or offer (the
 * union of every port kind - dependency direction does not depend on which
 * kind of port carries it). A self-edge (a component depending on its own
 * provided port) is never recorded: that is normal self-composition, not a
 * cross-component dependency.
 */
final class DependencyGraphEdges {

	private DependencyGraphEdges() {
	}

	/**
	 * Builds the directed edge map: for each root component type, the set of
	 * other root component types it depends on.
	 *
	 * @param rootComponentTypes    deployed root component types to analyze
	 * @param metamodelReadServices metamodel reader used to resolve each
	 *                              component type's declared ports
	 * @return for each root component type, the other root component types
	 *         it depends on
	 */
	static Map<Class<?>, Set<Class<?>>> build(final List<Class<?>> rootComponentTypes,
			final MetamodelReadServices metamodelReadServices) {
		Map<Class<?>, ReadOnlyComponentMetamodel> metamodels = new LinkedHashMap<>();
		for (final Class<?> componentType : rootComponentTypes) {
			metamodels.put(componentType, metamodelReadServices.readMetamodel(componentType));
		}

		Map<Class<?>, Class<?>> serviceOwner = new HashMap<>();
		Map<Class<?>, Class<?>> eventOwner = new HashMap<>();
		Map<Class<?>, Class<?>> parameterOwner = new HashMap<>();
		for (final Map.Entry<Class<?>, ReadOnlyComponentMetamodel> entry : metamodels.entrySet()) {
			for (final Class<?> provided : entry.getValue().getAllProvidedServices()) {
				serviceOwner.putIfAbsent(provided, entry.getKey());
			}
			for (final Class<?> produced : entry.getValue().getAllProducedEvents()) {
				eventOwner.putIfAbsent(produced, entry.getKey());
			}
			for (final Class<?> offered : entry.getValue().getAllOfferedParameters()) {
				parameterOwner.putIfAbsent(offered, entry.getKey());
			}
		}

		Map<Class<?>, Set<Class<?>>> edges = new LinkedHashMap<>();
		for (final Map.Entry<Class<?>, ReadOnlyComponentMetamodel> entry : metamodels.entrySet()) {
			Class<?> componentType = entry.getKey();
			Set<Class<?>> dependencies = new LinkedHashSet<>();
			addDependencies(dependencies, componentType, entry.getValue().getAllUsedServices(), serviceOwner);
			addDependencies(dependencies, componentType, entry.getValue().getAllConsumedEvents(), eventOwner);
			addDependencies(dependencies, componentType, entry.getValue().getAllAccessedParameters(), parameterOwner);
			edges.put(componentType, dependencies);
		}
		return edges;
	}

	private static void addDependencies(final Set<Class<?>> dependencies, final Class<?> componentType,
			final Set<Class<?>> requiredInterfaces, final Map<Class<?>, Class<?>> owners) {
		for (final Class<?> requiredInterface : requiredInterfaces) {
			Class<?> owner = owners.get(requiredInterface);
			if (owner != null && !owner.equals(componentType)) {
				dependencies.add(owner);
			}
		}
	}

}
