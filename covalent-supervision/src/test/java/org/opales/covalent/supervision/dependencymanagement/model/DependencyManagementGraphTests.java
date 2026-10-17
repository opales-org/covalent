/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.dependencymanagement.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

/**
 * Verifies the immutability, the availability flag and the value equality of the dependency graph
 * published to the view.
 */
public class DependencyManagementGraphTests {

	private static DependencyManagementGraph graph() {
		return new DependencyManagementGraph(Set.of(String.class, Integer.class),
				Map.of(Integer.class, Set.of(String.class)), List.of(List.of(String.class, Integer.class)),
				Map.of(String.class, Set.of(Integer.class)));
	}

	@Test
	public void graphWithoutCompositionsHasNone() {
		DependencyManagementGraph graph = new DependencyManagementGraph(Set.of(String.class), Map.of(), List.of());
		Assert.assertTrue(graph.getCompositions().isEmpty());
		Assert.assertTrue(graph.isAvailable());
	}

	@Test
	public void unavailableGraphIsEmptyAndFlagged() {
		DependencyManagementGraph graph = DependencyManagementGraph.unavailable();
		Assert.assertFalse(graph.isAvailable());
		Assert.assertTrue(graph.getComponents().isEmpty());
		Assert.assertTrue(graph.getDependencies().isEmpty());
		Assert.assertTrue(graph.getCircularDependencies().isEmpty());
		Assert.assertNotEquals(graph, new DependencyManagementGraph(Set.of(), Map.of(), List.of()));
	}

	@Test
	public void graphIsACopyOfItsInputs() {
		Set<Class<?>> components = new HashSet<>(Set.of(String.class));
		List<List<Class<?>>> cycles = new ArrayList<>();
		DependencyManagementGraph graph = new DependencyManagementGraph(components, Map.of(), cycles);
		components.add(Integer.class);
		cycles.add(List.of(String.class));
		Assert.assertEquals(Set.of(String.class), graph.getComponents());
		Assert.assertTrue(graph.getCircularDependencies().isEmpty());
	}

	@Test(expected = UnsupportedOperationException.class)
	public void componentsCannotBeModified() {
		graph().getComponents().add(Long.class);
	}

	@Test
	public void equalGraphsHaveTheSameHash() {
		Assert.assertEquals(graph(), graph());
		Assert.assertEquals(graph().hashCode(), graph().hashCode());
	}

	@Test
	public void graphsDifferingByAnyPartAreNotEqual() {
		DependencyManagementGraph base = graph();
		Assert.assertEquals(base, base);
		Assert.assertNotEquals(base, null);
		Assert.assertNotEquals(base, "graph");
		Assert.assertNotEquals(base, new DependencyManagementGraph(Set.of(String.class), base.getDependencies(),
				base.getCircularDependencies(), base.getCompositions()));
		Assert.assertNotEquals(base, new DependencyManagementGraph(base.getComponents(), Map.of(),
				base.getCircularDependencies(), base.getCompositions()));
		Assert.assertNotEquals(base, new DependencyManagementGraph(base.getComponents(), base.getDependencies(),
				List.of(), base.getCompositions()));
		Assert.assertNotEquals(base, new DependencyManagementGraph(base.getComponents(), base.getDependencies(),
				base.getCircularDependencies(), Map.of()));
	}

	@Test
	public void graphCarriesTheContainerNameAndIsEmptyNamedByDefault() {
		DependencyManagementGraph named = new DependencyManagementGraph(Set.of(String.class), Map.of(), List.of(), Map.of(),
				"main");
		Assert.assertEquals("main", named.getContainerName());
		Assert.assertEquals("", new DependencyManagementGraph(Set.of(), Map.of(), List.of()).getContainerName());
		Assert.assertEquals("", DependencyManagementGraph.unavailable().getContainerName());
		Assert.assertNotEquals(named,
				new DependencyManagementGraph(Set.of(String.class), Map.of(), List.of(), Map.of(), "other"));
	}

	@Test
	public void graphCarriesWhatEachComponentIsAndTakesAnUnknownOneForAPlainComponent() {
		DependencyManagementGraph graph = new DependencyManagementGraph(Set.of(String.class, Integer.class, Long.class),
				Map.of(), List.of(), Map.of(), "main",
				Map.of(String.class, ComponentKind.COMPOSITE, Integer.class, ComponentKind.RAW));
		Assert.assertEquals(ComponentKind.COMPOSITE, graph.getKind(String.class));
		Assert.assertEquals(ComponentKind.RAW, graph.getKind(Integer.class));
		Assert.assertEquals(ComponentKind.COMPONENT, graph.getKind(Long.class));
		Assert.assertEquals(ComponentKind.COMPONENT, DependencyManagementGraph.unavailable().getKind(String.class));
		Assert.assertNotEquals(graph, new DependencyManagementGraph(graph.getComponents(), Map.of(), List.of(), Map.of(),
				"main", Map.of(String.class, ComponentKind.COMPOSITE)));
	}
}
