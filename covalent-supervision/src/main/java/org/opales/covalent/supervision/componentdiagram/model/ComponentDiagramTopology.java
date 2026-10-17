/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A snapshot of the observed container's component topology: every
 * component type, plus the composition/service/parameter/event edges
 * between them. Plain value object, not a Covalent interface.
 */
public final class ComponentDiagramTopology {

	private final Set<Class<?>> componentTypes;
	private final Set<ComponentDiagramEdge> edges;
	private final Set<Class<?>> compositeTypes;
	private final Set<ComponentDiagramExternalPort> externalPorts;
	private final Set<Class<?>> allComponentTypes;
	private final Map<Class<?>, Class<?>> compositionParentOf;
	private final Set<Class<?>> rawTypes;
	private final Map<Class<?>, String> stereotypes;
	private final Map<Class<?>, Class<?>> implementations;
	private final String containerName;

	/**
	 * Creates a topology whose every shown component is managed by the container.
	 *
	 * @param componentTypes the components shown at the current level
	 * @param edges the links between them
	 * @param compositeTypes those of the components that contain children
	 * @param externalPorts the navigable ports standing for dependencies that leave the level
	 * @param allComponentTypes every registered, non-excluded component of the container
	 * @param compositionParentOf the child-to-parent composition map of the whole container
	 */
	public ComponentDiagramTopology(final Set<Class<?>> componentTypes, final Set<ComponentDiagramEdge> edges,
			final Set<Class<?>> compositeTypes, final Set<ComponentDiagramExternalPort> externalPorts,
			final Set<Class<?>> allComponentTypes, final Map<Class<?>, Class<?>> compositionParentOf) {
		this(componentTypes, edges, compositeTypes, externalPorts, allComponentTypes, compositionParentOf, Set.of());
	}

	/**
	 * Creates a topology.
	 *
	 * @param componentTypes the components shown at the current level
	 * @param edges the links between them
	 * @param compositeTypes those of the components that contain children
	 * @param externalPorts the navigable ports standing for dependencies that leave the level
	 * @param allComponentTypes every registered, non-excluded component of the container
	 * @param compositionParentOf the child-to-parent composition map of the whole container
	 * @param rawTypes the shown components the container does not manage: deployed without a metamodel
	 */
	public ComponentDiagramTopology(final Set<Class<?>> componentTypes, final Set<ComponentDiagramEdge> edges,
			final Set<Class<?>> compositeTypes, final Set<ComponentDiagramExternalPort> externalPorts,
			final Set<Class<?>> allComponentTypes, final Map<Class<?>, Class<?>> compositionParentOf,
			final Set<Class<?>> rawTypes) {
		this(componentTypes, edges, compositeTypes, externalPorts, allComponentTypes, compositionParentOf, rawTypes,
				Map.of());
	}

	/**
	 * Creates a topology that also says what each shown component stands for.
	 *
	 * @param componentTypes the components shown at the current level
	 * @param edges the links between them
	 * @param compositeTypes those of the components that contain children
	 * @param externalPorts the navigable ports standing for dependencies that leave the level
	 * @param allComponentTypes every registered, non-excluded component of the container
	 * @param compositionParentOf the child-to-parent composition map of the whole container
	 * @param rawTypes the shown components the container does not manage: deployed without a metamodel
	 * @param stereotypes what each shown component stands for: a stereotype of the table or, when none
	 *        matches its name, one of {@code MetamodelStereotypes}
	 */
	public ComponentDiagramTopology(final Set<Class<?>> componentTypes, final Set<ComponentDiagramEdge> edges,
			final Set<Class<?>> compositeTypes, final Set<ComponentDiagramExternalPort> externalPorts,
			final Set<Class<?>> allComponentTypes, final Map<Class<?>, Class<?>> compositionParentOf,
			final Set<Class<?>> rawTypes, final Map<Class<?>, String> stereotypes) {
		this(componentTypes, edges, compositeTypes, externalPorts, allComponentTypes, compositionParentOf, rawTypes,
				stereotypes, Map.of());
	}

	/**
	 * Creates a topology that also says which implementation stands behind each shown component type.
	 *
	 * @param componentTypes the components shown at the current level
	 * @param edges the links between them
	 * @param compositeTypes those of the components that contain children
	 * @param externalPorts the navigable ports standing for dependencies that leave the level
	 * @param allComponentTypes every registered, non-excluded component of the container
	 * @param compositionParentOf the child-to-parent composition map of the whole container
	 * @param rawTypes the shown components the container does not manage: deployed without a metamodel
	 * @param stereotypes what each shown component stands for: a stereotype of the table or, when none
	 *        matches its name, one of {@code MetamodelStereotypes}
	 * @param implementations the implementation class of each shown component whose implementation is
	 *        not its type itself
	 */
	public ComponentDiagramTopology(final Set<Class<?>> componentTypes, final Set<ComponentDiagramEdge> edges,
			final Set<Class<?>> compositeTypes, final Set<ComponentDiagramExternalPort> externalPorts,
			final Set<Class<?>> allComponentTypes, final Map<Class<?>, Class<?>> compositionParentOf,
			final Set<Class<?>> rawTypes, final Map<Class<?>, String> stereotypes,
			final Map<Class<?>, Class<?>> implementations) {
		this(componentTypes, edges, compositeTypes, externalPorts, allComponentTypes, compositionParentOf, rawTypes,
				stereotypes, implementations, "");
	}

	/**
	 * Creates a topology that also names the observed container.
	 *
	 * @param componentTypes the components shown at the current level
	 * @param edges the links between them
	 * @param compositeTypes those of the components that contain children
	 * @param externalPorts the navigable ports standing for dependencies that leave the level
	 * @param allComponentTypes every registered, non-excluded component of the container
	 * @param compositionParentOf the child-to-parent composition map of the whole container
	 * @param rawTypes the shown components the container does not manage: deployed without a metamodel
	 * @param stereotypes what each shown component stands for: a stereotype of the table or, when none
	 *        matches its name, one of {@code MetamodelStereotypes}
	 * @param implementations the implementation class of each shown component whose implementation is
	 *        not its type itself
	 * @param containerName the name of the observed container, empty when none is known
	 */
	public ComponentDiagramTopology(final Set<Class<?>> componentTypes, final Set<ComponentDiagramEdge> edges,
			final Set<Class<?>> compositeTypes, final Set<ComponentDiagramExternalPort> externalPorts,
			final Set<Class<?>> allComponentTypes, final Map<Class<?>, Class<?>> compositionParentOf,
			final Set<Class<?>> rawTypes, final Map<Class<?>, String> stereotypes,
			final Map<Class<?>, Class<?>> implementations, final String containerName) {
		this.containerName = Objects.requireNonNull(containerName, "containerName");
		this.implementations = Collections.unmodifiableMap(
				new LinkedHashMap<>(Objects.requireNonNull(implementations, "implementations")));
		this.stereotypes = Collections.unmodifiableMap(
				new LinkedHashMap<>(Objects.requireNonNull(stereotypes, "stereotypes")));
		this.rawTypes = Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(rawTypes, "rawTypes")));
		this.componentTypes = Collections.unmodifiableSet(
				new LinkedHashSet<>(Objects.requireNonNull(componentTypes, "componentTypes")));
		this.edges = Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(edges, "edges")));
		this.compositeTypes = Collections.unmodifiableSet(
				new LinkedHashSet<>(Objects.requireNonNull(compositeTypes, "compositeTypes")));
		this.externalPorts = Collections.unmodifiableSet(
				new LinkedHashSet<>(Objects.requireNonNull(externalPorts, "externalPorts")));
		this.allComponentTypes = Collections.unmodifiableSet(
				new LinkedHashSet<>(Objects.requireNonNull(allComponentTypes, "allComponentTypes")));
		this.compositionParentOf = Collections.unmodifiableMap(
				new LinkedHashMap<>(Objects.requireNonNull(compositionParentOf, "compositionParentOf")));
	}

	/**
	 * Returns the components shown at the current level.
	 *
	 * @return the component types shown at the current drill-down level
	 */
	public Set<Class<?>> getComponentTypes() {
		return this.componentTypes;
	}

	/**
	 * Returns the links between the shown components.
	 *
	 * @return every composition/service/parameter/event edge between them
	 */
	public Set<ComponentDiagramEdge> getEdges() {
		return this.edges;
	}

	/**
	 * Returns the shown components that a drill-down view can enter.
	 *
	 * @return the subset of {@link #getComponentTypes()} that have at least
	 *         one child in the full observed topology - i.e. those a
	 *         drill-down view can navigate into. Empty when none of the
	 *         shown components has a child.
	 */
	public Set<Class<?>> getCompositeTypes() {
		return this.compositeTypes;
	}

	/**
	 * Returns the external ports of the current level.
	 *
	 * @return every navigable port standing in for a dependency that
	 *         crosses this level's focus boundary - see {@link
	 *         ComponentDiagramExternalPort}'s own javadoc
	 */
	public Set<ComponentDiagramExternalPort> getExternalPorts() {
		return this.externalPorts;
	}

	/**
	 * Returns every component of the observed container.
	 *
	 * @return every registered, non-excluded component type in the whole
	 *         observed container - unlike {@link #getComponentTypes()},
	 *         never narrowed to the current drill-down focus level. Backs
	 *         the component tree panel, which shows the full composition
	 *         hierarchy regardless of where the canvas is drilled into.
	 */
	public Set<Class<?>> getAllComponentTypes() {
		return this.allComponentTypes;
	}

	/**
	 * Returns the composition hierarchy of the observed container.
	 *
	 * @return the full composition hierarchy as a child-to-parent map
	 *         (every {@code COMPONENT_TYPE} edge in the whole observed
	 *         container, {@code to -> from}) - a type with no entry here
	 *         is a root. Companion to {@link #getAllComponentTypes()} for
	 *         the component tree panel.
	 */
	public Map<Class<?>, Class<?>> getCompositionParentOf() {
		return this.compositionParentOf;
	}

	/**
	 * Returns the shown components that the container does not manage.
	 *
	 * @return the shown components the container does not manage, deployed without a metamodel
	 */
	public Set<Class<?>> getRawTypes() {
		return this.rawTypes;
	}

	/**
	 * Returns what a shown component stands for.
	 *
	 * @param componentType a shown component
	 * @return a stereotype of the table or, when none matches its name, one of
	 *         {@code MetamodelStereotypes}; {@code null} when none is known
	 */
	public String getStereotype(final Class<?> componentType) {
		return this.stereotypes.get(componentType);
	}

	/**
	 * Returns the implementation behind a shown component type.
	 *
	 * @param componentType a component type
	 * @return its implementation class; {@code null} when it is the type itself or none is known
	 */
	public Class<?> getImplementation(final Class<?> componentType) {
		return this.implementations.get(componentType);
	}

	/**
	 * Returns the name of the observed container.
	 *
	 * @return the name, empty when none is known
	 */
	public String getContainerName() {
		return this.containerName;
	}
}
