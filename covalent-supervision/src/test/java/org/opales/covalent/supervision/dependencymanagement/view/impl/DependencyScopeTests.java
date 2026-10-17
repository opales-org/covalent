/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.dependencymanagement.view.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.supervision.dependencymanagement.model.ComponentKind;
import org.opales.covalent.supervision.dependencymanagement.model.DependencyManagementGraph;

/**
 * Verifies how the component tree scopes the dependency graph: a composite and all its
 * descendants, their dependencies, and on demand the outside neighbors.
 */
public class DependencyScopeTests {

	// App composes Watchers and Store; Watchers composes Cross, which composes CrossModel and CrossView.
	private interface App {
	}

	private interface Watchers {
	}

	private interface Cross {
	}

	private interface CrossModel {
	}

	private interface CrossView {
	}

	private interface Store {
	}

	private interface Hmi {
	}

	private static DependencyManagementGraph graph() {
		Set<Class<?>> components = Set.of(App.class, Watchers.class, Cross.class, CrossModel.class, CrossView.class,
				Store.class, Hmi.class);
		Map<Class<?>, Set<Class<?>>> dependencies = Map.of(CrossView.class, Set.of(CrossModel.class, Hmi.class),
				CrossModel.class, Set.of(Store.class), Store.class, Set.of(Hmi.class), Hmi.class, Set.of(CrossView.class));
		Map<Class<?>, Set<Class<?>>> compositions = Map.of(App.class, Set.of(Watchers.class, Store.class),
				Watchers.class, Set.of(Cross.class), Cross.class, Set.of(CrossModel.class, CrossView.class));
		List<List<Class<?>>> cycles = List.of(List.of(CrossView.class, Hmi.class), List.of(Store.class, Hmi.class,
				CrossView.class, CrossModel.class));
		return new DependencyManagementGraph(components, dependencies, cycles, compositions);
	}

	@Test
	public void theScopeKeepsTheKindsOfTheComponentsItShows() {
		DependencyManagementGraph whole = graph();
		DependencyManagementGraph kinded = new DependencyManagementGraph(whole.getComponents(), whole.getDependencies(),
				whole.getCircularDependencies(), whole.getCompositions(), "main",
				Map.of(Watchers.class, ComponentKind.COMPOSITE, Hmi.class, ComponentKind.RAW, Store.class, ComponentKind.RAW));

		Assert.assertEquals(Map.of(Watchers.class, ComponentKind.COMPOSITE),
				DependencyScope.scope(kinded, Watchers.class, false, true).getKinds());
		Assert.assertEquals(
				Map.of(Watchers.class, ComponentKind.COMPOSITE, Hmi.class, ComponentKind.RAW, Store.class, ComponentKind.RAW),
				DependencyScope.scope(kinded, Watchers.class, true, true).getKinds());
	}

	@Test
	public void theSubtreeIsTheCompositeAndAllItsDescendants() {
		Assert.assertEquals(Set.of(Watchers.class, Cross.class, CrossModel.class, CrossView.class),
				DependencyScope.subtree(graph().getCompositions(), Watchers.class));
		Assert.assertEquals(Set.of(CrossModel.class), DependencyScope.subtree(graph().getCompositions(), CrossModel.class));
	}

	@Test
	public void withoutNeighborsOnlyTheDependenciesInsideAreKept() {
		DependencyManagementGraph scoped = DependencyScope.scope(graph(), Watchers.class, false, true);

		Assert.assertEquals(Set.of(Watchers.class, Cross.class, CrossModel.class, CrossView.class), scoped.getComponents());
		Assert.assertEquals(Map.of(CrossView.class, Set.of(CrossModel.class)), scoped.getDependencies());
		Assert.assertEquals(Map.of(Watchers.class, Set.of(Cross.class), Cross.class,
				Set.of(CrossModel.class, CrossView.class)), scoped.getCompositions());
		Assert.assertTrue("no cycle lies inside", scoped.getCircularDependencies().isEmpty());
	}

	@Test
	public void outsideNeighborsAreLinkedBothWaysButNotToEachOther() {
		DependencyManagementGraph scoped = DependencyScope.scope(graph(), Watchers.class, true, true);

		Assert.assertEquals(Set.of(Watchers.class, Cross.class, CrossModel.class, CrossView.class, Store.class,
				Hmi.class), scoped.getComponents());
		Assert.assertEquals(Map.of(CrossView.class, Set.of(CrossModel.class, Hmi.class), CrossModel.class,
				Set.of(Store.class), Hmi.class, Set.of(CrossView.class)), scoped.getDependencies());
		Assert.assertEquals("the cycle through two neighbors stays out: their own link is not shown",
				List.of(List.of(CrossView.class, Hmi.class)), scoped.getCircularDependencies());
	}

	@Test
	public void withoutTheRootOnlyItsContentIsKept() {
		DependencyManagementGraph scoped = DependencyScope.scope(graph(), Watchers.class, false, false);

		Assert.assertEquals(Set.of(Cross.class, CrossModel.class, CrossView.class), scoped.getComponents());
		Assert.assertEquals(Map.of(CrossView.class, Set.of(CrossModel.class)), scoped.getDependencies());
		Assert.assertEquals("no composition link from the hidden root",
				Map.of(Cross.class, Set.of(CrossModel.class, CrossView.class)), scoped.getCompositions());
	}

	@Test
	public void aNeighborLinkedOnlyToTheHiddenRootIsNotKept() {
		// Parent composes Child; the Parent itself depends on Hmi, and Child depends on Store.
		Set<Class<?>> components = Set.of(Watchers.class, Cross.class, Hmi.class, Store.class);
		Map<Class<?>, Set<Class<?>>> dependencies = Map.of(Watchers.class, Set.of(Hmi.class), Cross.class,
				Set.of(Store.class));
		Map<Class<?>, Set<Class<?>>> compositions = Map.of(Watchers.class, Set.of(Cross.class));
		DependencyManagementGraph whole = new DependencyManagementGraph(components, dependencies, List.of(),
				compositions);

		Assert.assertEquals(Set.of(Watchers.class, Cross.class, Hmi.class, Store.class),
				DependencyScope.scope(whole, Watchers.class, true, true).getComponents());
		DependencyManagementGraph withoutRoot = DependencyScope.scope(whole, Watchers.class, true, false);
		Assert.assertEquals("Hmi had no link left", Set.of(Cross.class, Store.class), withoutRoot.getComponents());
		Assert.assertEquals(Map.of(Cross.class, Set.of(Store.class)), withoutRoot.getDependencies());
	}

	@Test
	public void theLinksWithTheOutsideAreCountedBothWays() {
		Set<Class<?>> subtree = DependencyScope.subtree(graph().getCompositions(), Watchers.class);

		Assert.assertEquals(3, DependencyScope.outsideLinks(graph(), subtree));
		Assert.assertEquals(Set.of(Store.class, Hmi.class), DependencyScope.outsideNeighbors(graph(), subtree));
	}

	@Test
	public void noScopeOrAnUnknownOneIsTheWholeGraph() {
		Assert.assertEquals(graph(), DependencyScope.scope(graph(), null, true, true));
		Assert.assertEquals(graph(), DependencyScope.scope(graph(), Long.class, false, true));
	}

	@Test
	public void thePathGoesFromTheOutermostCompositeDownToTheComponent() {
		Assert.assertEquals(List.of(App.class, Watchers.class, Cross.class, CrossView.class),
				DependencyScope.path(graph().getCompositions(), CrossView.class));
		Assert.assertEquals(List.of(App.class), DependencyScope.path(graph().getCompositions(), App.class));
	}
}
