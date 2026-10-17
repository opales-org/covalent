/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.dependencymanagement.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable snapshot to render: the observed container's components, their
 * dependencies, circular dependencies and compositions, after {@code
 * ExclusionParameters} filtering, with the container's name and what each
 * component is. The components are held as a set, not as the start order
 * that {@code DependencyGraph.getStartOrder()} gives. See {@code
 * DependencyManagementControllerImpl} for how this is built.
 */
public final class DependencyManagementGraph {

	private final Set<Class<?>> components;
	private final Map<Class<?>, Set<Class<?>>> dependencies;
	private final List<List<Class<?>>> circularDependencies;

	private final Map<Class<?>, Set<Class<?>>> compositions;

	private final boolean available;

	private final String containerName;

	private final Map<Class<?>, ComponentKind> kinds;

	/**
	 * Creates a graph without compositions.
	 *
	 * @param components           the components
	 * @param dependencies         what each component depends on
	 * @param circularDependencies the circular dependencies
	 */
	public DependencyManagementGraph(final Set<Class<?>> components, final Map<Class<?>, Set<Class<?>>> dependencies,
			final List<List<Class<?>>> circularDependencies) {
		this(components, dependencies, circularDependencies, Map.of());
	}

	/**
	 * Creates a graph with its compositions: each composite and its direct
	 * composed children, apart from the dependencies.
	 *
	 * @param components           the components
	 * @param dependencies         what each component depends on
	 * @param circularDependencies the circular dependencies
	 * @param compositions         each composite's direct children
	 */
	public DependencyManagementGraph(final Set<Class<?>> components, final Map<Class<?>, Set<Class<?>>> dependencies,
			final List<List<Class<?>>> circularDependencies, final Map<Class<?>, Set<Class<?>>> compositions) {
		this(components, dependencies, circularDependencies, compositions, "");
	}

	/**
	 * Creates a graph with its compositions and the name of the observed container.
	 *
	 * @param components           the components
	 * @param dependencies         what each component depends on
	 * @param circularDependencies the circular dependencies
	 * @param compositions         each composite's direct children
	 * @param containerName        the name of the observed container, empty when none is known
	 */
	public DependencyManagementGraph(final Set<Class<?>> components, final Map<Class<?>, Set<Class<?>>> dependencies,
			final List<List<Class<?>>> circularDependencies, final Map<Class<?>, Set<Class<?>>> compositions,
			final String containerName) {
		this(components, dependencies, circularDependencies, compositions, containerName, Map.of());
	}

	/**
	 * Creates a graph with its compositions, the name of the observed container and what each component is.
	 *
	 * @param components           the components
	 * @param dependencies         what each component depends on
	 * @param circularDependencies the circular dependencies
	 * @param compositions         each composite's direct children
	 * @param containerName        the name of the observed container, empty when none is known
	 * @param kinds                what each component is; a component absent from it is a plain component
	 */
	public DependencyManagementGraph(final Set<Class<?>> components, final Map<Class<?>, Set<Class<?>>> dependencies,
			final List<List<Class<?>>> circularDependencies, final Map<Class<?>, Set<Class<?>>> compositions,
			final String containerName, final Map<Class<?>, ComponentKind> kinds) {
		this(components, dependencies, circularDependencies, compositions, true, containerName, kinds);
	}

	private DependencyManagementGraph(final Set<Class<?>> components, final Map<Class<?>, Set<Class<?>>> dependencies,
			final List<List<Class<?>>> circularDependencies, final Map<Class<?>, Set<Class<?>>> compositions,
			final boolean available, final String containerName, final Map<Class<?>, ComponentKind> kinds) {
		this.containerName = Objects.requireNonNull(containerName, "containerName");
		this.kinds = Map.copyOf(kinds);
		Map<Class<?>, Set<Class<?>>> copiedCompositions = new LinkedHashMap<>();
		compositions.forEach((type, children) -> copiedCompositions.put(type, Set.copyOf(children)));
		this.compositions = Map.copyOf(copiedCompositions);
		this.components = Set.copyOf(components);
		Map<Class<?>, Set<Class<?>>> copiedDependencies = new LinkedHashMap<>();
		dependencies.forEach((type, dependsOn) -> copiedDependencies.put(type, Set.copyOf(dependsOn)));
		this.dependencies = Map.copyOf(copiedDependencies);
		this.circularDependencies = circularDependencies.stream().map(List::copyOf).collect(Collectors.toUnmodifiableList());
		this.available = available;
	}

	/**
	 * An empty graph reported as unavailable rather than empty: the observed
	 * container simply never computes one - {@code
	 * CovalentContainerParameters#setCircularDependencyChecking(boolean)} is
	 * off - as distinct from a real container that genuinely has no
	 * components. The view uses this to show why, instead of silently
	 * drawing nothing.
	 *
	 * @return an unavailable graph
	 */
	public static DependencyManagementGraph unavailable() {
		return new DependencyManagementGraph(Set.of(), Map.of(), List.of(), Map.of(), false, "", Map.of());
	}

	/**
	 * Returns the component types of the graph, after exclusion filtering.
	 *
	 * @return the components, immutable
	 */
	public Set<Class<?>> getComponents() {
		return this.components;
	}

	/**
	 * Returns what each component depends on.
	 *
	 * @return the dependencies, by component, immutable
	 */
	public Map<Class<?>, Set<Class<?>>> getDependencies() {
		return this.dependencies;
	}

	/**
	 * Returns the circular dependencies found, each as the list of component types that make the cycle.
	 *
	 * @return the cycles, immutable
	 */
	public List<List<Class<?>>> getCircularDependencies() {
		return this.circularDependencies;
	}

	/**
	 * Returns each composite's direct composed children, a structural link
	 * apart from the dependencies.
	 *
	 * @return the children, by composite
	 */
	public Map<Class<?>, Set<Class<?>>> getCompositions() {
		return this.compositions;
	}

	/**
	 * Tells whether the graph was computed at all.
	 *
	 * @return {@code false} when the observed container never computes a
	 *         dependency graph at all (circular dependency checking is off),
	 *         as distinct from a real, genuinely empty graph
	 */
	public boolean isAvailable() {
		return this.available;
	}

	/**
	 * Returns the name of the observed container.
	 *
	 * @return the name, empty when none is known
	 */
	public String getContainerName() {
		return this.containerName;
	}

	/**
	 * Returns what each component is, for the components that are not plain ones.
	 *
	 * @return the kinds, by component, immutable
	 */
	public Map<Class<?>, ComponentKind> getKinds() {
		return this.kinds;
	}

	/**
	 * Returns what a component is.
	 *
	 * @param componentType the component type
	 * @return its kind, {@link ComponentKind#COMPONENT} when none is known
	 */
	public ComponentKind getKind(final Class<?> componentType) {
		return this.kinds.getOrDefault(componentType, ComponentKind.COMPONENT);
	}

	@Override
	public boolean equals(final Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof DependencyManagementGraph)) {
			return false;
		}
		DependencyManagementGraph that = (DependencyManagementGraph) other;
		return this.available == that.available && this.components.equals(that.components)
				&& this.dependencies.equals(that.dependencies)
				&& this.circularDependencies.equals(that.circularDependencies)
				&& this.compositions.equals(that.compositions)
				&& this.containerName.equals(that.containerName) && this.kinds.equals(that.kinds);
	}

	@Override
	public int hashCode() {
		return List.of(this.components, this.dependencies, this.circularDependencies, this.compositions, this.available,
				this.containerName, this.kinds).hashCode();
	}
}
