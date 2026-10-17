/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.dependencymanagement.controller.impl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.lifecycle.DependencyGraph;
import org.opales.covalent.container.metamodel.metamodel.ReadOnlyComponentMetamodel;
import org.opales.covalent.container.metamodel.metamodel.impl.ComponentMetamodelImpl;
import org.opales.covalent.supervision.containermanager.raw.ObservedContainerControl;
import org.opales.covalent.supervision.dependencymanagement.model.ComponentKind;
import org.opales.covalent.supervision.dependencymanagement.model.DependencyManagementGraph;
import org.opales.covalent.supervision.dependencymanagement.model.DependencyManagementModelServices;
import org.opales.covalent.supervision.exclusionpolicy.controller.ExclusionParameters;
import org.opales.covalent.supervision.observed.ObservedDependencyGraphServices;
import org.opales.covalent.supervision.observed.ObservedMetamodelReadServices;

/**
 * Verifies, with hand-written fakes and no container, how {@link DependencyManagementControllerImpl}
 * turns the observed dependency graph into the published one: unavailable graphs, exclusion
 * filtering of components, dependencies, cycles and compositions, and the recomputation triggered
 * by exclusion changes and by connection events.
 */
public class DependencyManagementControllerImplFilteringTests {

	private static final class A {
	}

	private static final class B {
	}

	private static final class C {
	}

	@Implementation
	private static final class Managed {
	}

	private static final class FakeGraph implements DependencyGraph {
		List<Class<?>> order = new ArrayList<>();
		Map<Class<?>, Set<Class<?>>> dependencies = new LinkedHashMap<>();
		List<List<Class<?>>> cycles = new ArrayList<>();
		Map<Class<?>, Set<Class<?>>> compositions = new LinkedHashMap<>();

		@Override
		public List<Class<?>> getStartOrder() {
			return this.order;
		}

		@Override
		public boolean hasCircularDependencies() {
			return !this.cycles.isEmpty();
		}

		@Override
		public List<List<Class<?>>> getCircularDependencies() {
			return this.cycles;
		}

		@Override
		public Map<Class<?>, Set<Class<?>>> getDependencies() {
			return this.dependencies;
		}

		@Override
		public Map<Class<?>, Set<Class<?>>> getCompositions() {
			return this.compositions;
		}
	}

	private boolean available;
	private String containerName = "observed";
	private final FakeGraph graph = new FakeGraph();
	private final Map<Class<?>, ComponentMetamodelImpl> metamodels = new LinkedHashMap<>();
	private final Set<Class<?>> excluded = new LinkedHashSet<>();
	private final List<DependencyManagementGraph> published = new ArrayList<>();
	private DependencyManagementControllerImpl controller;

	@Before
	public void setUp() throws Exception {
		this.available = true;
		this.controller = new DependencyManagementControllerImpl();
		this.inject("useDependencyGraphServices", new ObservedDependencyGraphServices() {
			@Override
			public DependencyGraph getDependencyGraph() {
				return DependencyManagementControllerImplFilteringTests.this.graph;
			}

			@Override
			public boolean hasDependencyGraph() {
				return DependencyManagementControllerImplFilteringTests.this.available;
			}
		});
		this.inject("useObservedContainerControl", new ObservedContainerControl() {
			@Override
			public void connect(final CovalentContainer target) {
				// Not used.
			}

			@Override
			public void disconnect() {
				// Not used.
			}

			@Override
			public String getObservedContainerName() {
				return DependencyManagementControllerImplFilteringTests.this.containerName;
			}
		});
		this.inject("useMetamodelReadServices", new ObservedMetamodelReadServices() {
			@Override
			public ReadOnlyComponentMetamodel readMetamodel(final Class<?> componentType) {
				return DependencyManagementControllerImplFilteringTests.this.metamodels.get(componentType);
			}

			@Override
			public <CT, CI> Class<CT> getType(final Class<CI> implementation) {
				return null;
			}

			@Override
			public <CT, CI> Class<CI> getImplementation(final Class<CT> componentType) {
				return null;
			}
		});
		this.inject("useModelServices", (DependencyManagementModelServices) this.published::add);
		this.inject("accessExclusionParameters", (ExclusionParameters) this.excluded::contains);
		this.graph.order.add(A.class);
		this.graph.order.add(B.class);
		this.graph.order.add(C.class);
	}

	private void inject(final String name, final Object value) throws Exception {
		Field field = DependencyManagementControllerImpl.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(this.controller, value);
	}

	private DependencyManagementGraph last() {
		return this.published.get(this.published.size() - 1);
	}

	private ComponentMetamodelImpl metamodelOf(final Class<?> type, final Class<?> implementation,
			final Class<?>... composed) {
		ComponentMetamodelImpl metamodel = new ComponentMetamodelImpl();
		metamodel.setComponentType(type);
		metamodel.setImplementation(implementation);
		metamodel.setComposedTypes(List.of(composed));
		this.metamodels.put(type, metamodel);
		return metamodel;
	}

	@Test
	public void theGraphCarriesWhatEachComponentIs() {
		this.metamodelOf(A.class, Managed.class);
		this.metamodelOf(B.class, B.class);
		this.metamodelOf(C.class, Managed.class, A.class);
		this.controller.refreshGraph();
		Assert.assertEquals(ComponentKind.COMPONENT, this.last().getKind(A.class));
		Assert.assertEquals(ComponentKind.RAW, this.last().getKind(B.class));
		Assert.assertEquals(ComponentKind.COMPOSITE, this.last().getKind(C.class));
	}

	@Test
	public void aComponentWithoutMetamodelIsAPlainComponent() {
		this.controller.refreshGraph();
		Assert.assertEquals(Map.of(), this.last().getKinds());
	}

	@Test
	public void theGraphCarriesTheObservedContainerName() {
		this.controller.refreshGraph();
		Assert.assertEquals("observed", this.last().getContainerName());
	}

	@Test
	public void aMissingContainerNameBecomesEmpty() {
		this.containerName = null;
		this.controller.refreshGraph();
		Assert.assertEquals("", this.last().getContainerName());
	}

	@Test
	public void missingDependencyGraphPublishesAnUnavailableGraph() {
		this.available = false;
		this.controller.refreshGraph();
		Assert.assertEquals(DependencyManagementGraph.unavailable(), this.last());
		Assert.assertFalse(this.last().isAvailable());
	}

	@Test
	public void activationPublishesTheGraph() {
		this.graph.dependencies.put(B.class, Set.of(A.class));
		this.controller.activateAndRefresh();
		Assert.assertEquals(1, this.published.size());
		Assert.assertTrue(this.last().isAvailable());
		Assert.assertEquals(Set.of(A.class, B.class, C.class), this.last().getComponents());
		Assert.assertEquals(Set.of(A.class), this.last().getDependencies().get(B.class));
	}

	@Test
	public void excludedTypesAreDroppedFromComponentsAndDependencies() {
		this.graph.dependencies.put(A.class, Set.of(B.class, C.class));
		this.graph.dependencies.put(B.class, Set.of(C.class));
		this.excluded.add(B.class);
		this.controller.refreshGraph();
		Assert.assertEquals(Set.of(A.class, C.class), this.last().getComponents());
		Assert.assertEquals(Set.of(C.class), this.last().getDependencies().get(A.class));
		Assert.assertFalse(this.last().getDependencies().containsKey(B.class));
	}

	@Test
	public void cycleContainingAnExcludedTypeIsDroppedEntirely() {
		this.graph.cycles.add(List.of(A.class, B.class));
		this.graph.cycles.add(List.of(B.class, C.class));
		this.excluded.add(C.class);
		this.controller.refreshGraph();
		Assert.assertEquals(List.of(List.of(A.class, B.class)), this.last().getCircularDependencies());
	}

	@Test
	public void compositionKeepsOnlyShownChildrenAndShownComposites() {
		this.graph.compositions.put(A.class, Set.of(B.class, C.class));
		this.graph.compositions.put(B.class, Set.of(C.class));
		this.excluded.add(C.class);
		this.controller.refreshGraph();
		Assert.assertEquals(Map.of(A.class, Set.of(B.class)), this.last().getCompositions());
	}

	@Test
	public void excludingACompositeExcludesEverythingItContains() {
		this.graph.compositions.put(A.class, Set.of(B.class));
		this.graph.compositions.put(B.class, Set.of(C.class));
		this.graph.dependencies.put(C.class, Set.of());
		this.excluded.add(A.class);
		this.controller.refreshGraph();
		Assert.assertTrue(this.last().getComponents().isEmpty());
		Assert.assertTrue(this.last().getDependencies().isEmpty());
		Assert.assertTrue(this.last().getCompositions().isEmpty());
	}

	@Test
	public void excludingAPartLeavesItsContainerShown() {
		this.graph.compositions.put(A.class, Set.of(B.class));
		this.excluded.add(B.class);
		this.controller.refreshGraph();
		Assert.assertEquals(Set.of(A.class, C.class), this.last().getComponents());
	}

	@Test
	public void compositeWhoseChildrenAreAllExcludedIsNotListed() {
		this.graph.compositions.put(A.class, Set.of(B.class));
		this.excluded.add(B.class);
		this.controller.refreshGraph();
		Assert.assertTrue(this.last().getCompositions().isEmpty());
	}

	@Test
	public void exclusionChangesRecomputeTheGraph() {
		this.controller.onExcludedComponentsChanged(List.of(A.class));
		Assert.assertEquals(1, this.published.size());
		this.controller.onExcludeInternalComponentsChanged(true);
		Assert.assertEquals(2, this.published.size());
	}

	@Test
	public void connectionEventsRecomputeTheGraph() {
		this.controller.onContainerConnected();
		Assert.assertEquals(1, this.published.size());
		this.available = false;
		this.controller.onContainerDisconnected();
		Assert.assertEquals(2, this.published.size());
		Assert.assertFalse(this.last().isAvailable());
	}
}
