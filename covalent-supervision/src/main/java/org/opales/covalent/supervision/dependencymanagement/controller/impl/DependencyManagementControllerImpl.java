/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.dependencymanagement.controller.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.opales.covalent.container.lifecycle.DependencyGraph;
import org.opales.covalent.container.metamodel.metamodel.ReadOnlyComponentMetamodel;
import org.opales.covalent.supervision.containermanager.raw.ObservedContainerControl;
import org.opales.covalent.supervision.observed.ObservedContainerConnectionEvents;
import org.opales.covalent.supervision.observed.ObservedDependencyGraphServices;
import org.opales.covalent.supervision.observed.ObservedMetamodelReadServices;
import org.opales.covalent.supervision.dependencymanagement.controller.DependencyManagementController;
import org.opales.covalent.supervision.dependencymanagement.controller.DependencyManagementControllerServices;
import org.opales.covalent.supervision.dependencymanagement.model.ComponentKind;
import org.opales.covalent.supervision.dependencymanagement.model.DependencyManagementGraph;
import org.opales.covalent.supervision.dependencymanagement.model.DependencyManagementModelServices;
import org.opales.covalent.supervision.exclusionpolicy.ExclusionCascade;
import org.opales.covalent.supervision.exclusionpolicy.controller.ExclusionParameters;
import org.opales.covalent.supervision.exclusionpolicy.model.ExclusionPolicyEvents;

import org.opales.covalent.container.component.annotations.AccessedParameters;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;

/**
 * DependencyManagement's Controller: computes the graph to render from
 * {@code ObservedDependencyGraphServices}, then publishes it to the Model. {@code DependencyGraph} is an
 * immutable one-shot snapshot computed at the observed container's {@code start()}, so the graph is
 * recomputed only at this component's own {@code @Activation}, on a refresh request, when the
 * exclusions change and when the tool connects to or disconnects from an observed container.
 *
 * <p>
 * Exclusion filtering: an excluded component type, or one contained in an excluded composite at any
 * depth, is dropped from the components and from every dependency set; a circular dependency that
 * includes at least one excluded type is dropped entirely from the circular dependencies, since a
 * cycle missing one of its members is not the cycle that was found.
 */
@Implementation
public class DependencyManagementControllerImpl implements DependencyManagementController, DependencyManagementControllerServices,
		ExclusionPolicyEvents, ObservedContainerConnectionEvents {

	/** Creates the Controller; the container injects the services and parameters it uses. */
	public DependencyManagementControllerImpl() {
		// Nothing to initialize.
	}

	@UsedServices
	private ObservedDependencyGraphServices useDependencyGraphServices;

	@UsedServices
	private ObservedContainerControl useObservedContainerControl;

	@UsedServices
	private ObservedMetamodelReadServices useMetamodelReadServices;

	@UsedServices
	private DependencyManagementModelServices useModelServices;

	@AccessedParameters
	private ExclusionParameters accessExclusionParameters;

	/** Computes the first graph when the component activates. */
	@Activation
	public void activateAndRefresh() {
		this.refreshGraph();
	}

	@Override
	public synchronized void refreshGraph() {
		// Synchronized: a refresh started before another cannot write its older graph after it.
		if (!this.useDependencyGraphServices.hasDependencyGraph()) {
			this.useModelServices.setGraph(DependencyManagementGraph.unavailable());
			return;
		}

		DependencyGraph dependencyGraph = this.useDependencyGraphServices.getDependencyGraph();
		Map<Class<?>, Class<?>> containers = new LinkedHashMap<>();
		dependencyGraph.getCompositions()
				.forEach((composite, children) -> children.forEach(child -> containers.put(child, composite)));
		UnaryOperator<Class<?>> containerOf = containers::get;

		Set<Class<?>> components = new LinkedHashSet<>();
		for (Class<?> componentType : dependencyGraph.getStartOrder()) {
			if (!this.isExcluded(componentType, containerOf)) {
				components.add(componentType);
			}
		}

		Map<Class<?>, Set<Class<?>>> dependencies = new LinkedHashMap<>();
		dependencyGraph.getDependencies().forEach((componentType, dependsOn) -> {
			if (this.isExcluded(componentType, containerOf)) {
				return;
			}
			Set<Class<?>> filteredDependsOn = new LinkedHashSet<>();
			for (Class<?> dependency : dependsOn) {
				if (!this.isExcluded(dependency, containerOf)) {
					filteredDependsOn.add(dependency);
				}
			}
			dependencies.put(componentType, filteredDependsOn);
		});

		List<List<Class<?>>> circularDependencies = new ArrayList<>();
		for (List<Class<?>> cycle : dependencyGraph.getCircularDependencies()) {
			boolean cycleExcluded = cycle.stream().anyMatch(member -> this.isExcluded(member, containerOf));
			if (!cycleExcluded) {
				circularDependencies.add(cycle);
			}
		}

		Map<Class<?>, Set<Class<?>>> compositions = new LinkedHashMap<>();
		dependencyGraph.getCompositions().forEach((composite, children) -> {
			if (this.isExcluded(composite, containerOf)) {
				return;
			}
			Set<Class<?>> shownChildren = new LinkedHashSet<>();
			for (Class<?> child : children) {
				if (!this.isExcluded(child, containerOf)) {
					shownChildren.add(child);
				}
			}
			if (!shownChildren.isEmpty()) {
				compositions.put(composite, shownChildren);
			}
		});

		Map<Class<?>, ComponentKind> kinds = new LinkedHashMap<>();
		for (Class<?> componentType : components) {
			ComponentKind kind = this.kindOf(componentType);
			if (kind != ComponentKind.COMPONENT) {
				kinds.put(componentType, kind);
			}
		}

		this.useModelServices.setGraph(
				new DependencyManagementGraph(components, dependencies, circularDependencies, compositions,
						this.observedContainerName(), kinds));
	}

	/** What the observed container's metamodel says the component is; a plain component when it has none. */
	private ComponentKind kindOf(final Class<?> componentType) {
		ReadOnlyComponentMetamodel metamodel = this.useMetamodelReadServices.readMetamodel(componentType);
		if (metamodel == null) {
			return ComponentKind.COMPONENT;
		}
		if (metamodel.isRaw()) {
			return ComponentKind.RAW;
		}
		return metamodel.isComposite() ? ComponentKind.COMPOSITE : ComponentKind.COMPONENT;
	}

	private String observedContainerName() {
		String name = this.useObservedContainerControl.getObservedContainerName();
		return name == null ? "" : name;
	}

	private boolean isExcluded(final Class<?> componentType, final UnaryOperator<Class<?>> containerOf) {
		return componentType == null
				|| ExclusionCascade.isExcluded(componentType, this.accessExclusionParameters, containerOf);
	}

	// --- ExclusionPolicyEvents: either change can add or remove nodes from the
	// cached graph, so both trigger a full recompute from the dependency
	// graph snapshot, same as refreshGraph()'s own @Activation call. ---

	@Override
	public void onExcludedComponentsChanged(final List<Class<?>> componentTypes) {
		this.refreshGraph();
	}

	@Override
	public void onExcludeInternalComponentsChanged(final boolean exclude) {
		this.refreshGraph();
	}

	// --- ObservedContainerConnectionEvents: the dependency graph snapshot is
	// recomputed at the observed container's own start(), so this Controller
	// must resynchronize whenever the tool (re)connects to an observed
	// container - otherwise it keeps serving whatever snapshot (or empty
	// state) it captured at its own @Activation, before any connection
	// existed. Same resync as ComponentDiagramControllerImpl's own
	// onContainerConnected(). ---

	@Override
	public void onContainerConnected() {
		this.refreshGraph();
	}

	@Override
	public void onContainerDisconnected() {
		this.refreshGraph();
	}
}
