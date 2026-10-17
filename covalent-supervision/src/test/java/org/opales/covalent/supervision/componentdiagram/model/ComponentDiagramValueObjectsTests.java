/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.model;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

/**
 * Verifies the immutable value objects of the component diagram model: edge and external port equality, defensive
 * copies and null rejection, and the topology accessors with their defaults.
 */
public class ComponentDiagramValueObjectsTests {

	@Test
	public void edgesWithTheSameEndsKindAndRelationAreEqual() {
		ComponentDiagramEdge a = new ComponentDiagramEdge(String.class, Integer.class, ComponentPortKind.SERVICE, Runnable.class);
		ComponentDiagramEdge b = new ComponentDiagramEdge(String.class, Integer.class, ComponentPortKind.SERVICE, Runnable.class);
		Assert.assertEquals(a, b);
		Assert.assertEquals(a.hashCode(), b.hashCode());
		Assert.assertEquals(a, a);
		Assert.assertEquals(String.class, a.getFrom());
		Assert.assertEquals(Integer.class, a.getTo());
		Assert.assertEquals(ComponentPortKind.SERVICE, a.getKind());
		Assert.assertEquals(Runnable.class, a.getRelation());
	}

	@Test
	public void edgesDifferingByAnyPartAreDistinct() {
		ComponentDiagramEdge base = new ComponentDiagramEdge(String.class, Integer.class, ComponentPortKind.SERVICE, Runnable.class);
		Assert.assertNotEquals(base, new ComponentDiagramEdge(Long.class, Integer.class, ComponentPortKind.SERVICE, Runnable.class));
		Assert.assertNotEquals(base, new ComponentDiagramEdge(String.class, Long.class, ComponentPortKind.SERVICE, Runnable.class));
		Assert.assertNotEquals(base, new ComponentDiagramEdge(String.class, Integer.class, ComponentPortKind.EVENT, Runnable.class));
		Assert.assertNotEquals(base, new ComponentDiagramEdge(String.class, Integer.class, ComponentPortKind.SERVICE, Comparable.class));
		Assert.assertNotEquals(base, new ComponentDiagramEdge(String.class, Integer.class, ComponentPortKind.SERVICE, null));
		Assert.assertNotEquals(base, "not an edge");
		Assert.assertNotEquals(base, null);
	}

	@Test
	public void compositionEdgeHasNoRelationAndStaysEqualToItself() {
		ComponentDiagramEdge a = new ComponentDiagramEdge(String.class, Integer.class, ComponentPortKind.COMPONENT_TYPE, null);
		ComponentDiagramEdge b = new ComponentDiagramEdge(String.class, Integer.class, ComponentPortKind.COMPONENT_TYPE, null);
		Assert.assertNull(a.getRelation());
		Assert.assertEquals(a, b);
		Assert.assertEquals(a.hashCode(), b.hashCode());
	}

	@Test
	public void edgeRejectsNullEndsAndKind() {
		try {
			new ComponentDiagramEdge(null, Integer.class, ComponentPortKind.SERVICE, null);
			Assert.fail();
		} catch (NullPointerException expected) {
			Assert.assertEquals("from", expected.getMessage());
		}
		try {
			new ComponentDiagramEdge(String.class, null, ComponentPortKind.SERVICE, null);
			Assert.fail();
		} catch (NullPointerException expected) {
			Assert.assertEquals("to", expected.getMessage());
		}
		try {
			new ComponentDiagramEdge(String.class, Integer.class, null, null);
			Assert.fail();
		} catch (NullPointerException expected) {
			Assert.assertEquals("kind", expected.getMessage());
		}
	}

	private static Set<Class<?>> set(final Class<?>... types) {
		return new LinkedHashSet<>(java.util.Arrays.asList(types));
	}

	@Test
	public void externalPortsWithTheSameContentAreEqual() {
		ComponentDiagramExternalPort a = new ComponentDiagramExternalPort(String.class, ComponentPortKind.SERVICE,
				ComponentPortRole.USED, set(Integer.class));
		ComponentDiagramExternalPort b = new ComponentDiagramExternalPort(String.class, ComponentPortKind.SERVICE,
				ComponentPortRole.USED, set(Integer.class));
		Assert.assertEquals(a, b);
		Assert.assertEquals(a, a);
		Assert.assertEquals(a.hashCode(), b.hashCode());
		Assert.assertEquals(String.class, a.getOwner());
		Assert.assertEquals(ComponentPortKind.SERVICE, a.getKind());
		Assert.assertEquals(ComponentPortRole.USED, a.getRole());
		Assert.assertEquals(set(Integer.class), a.getTargets());
	}

	@Test
	public void externalPortsDifferingByAnyPartAreDistinct() {
		ComponentDiagramExternalPort base = new ComponentDiagramExternalPort(String.class, ComponentPortKind.SERVICE,
				ComponentPortRole.USED, set(Integer.class));
		Assert.assertNotEquals(base, new ComponentDiagramExternalPort(Long.class, ComponentPortKind.SERVICE,
				ComponentPortRole.USED, set(Integer.class)));
		Assert.assertNotEquals(base, new ComponentDiagramExternalPort(String.class, ComponentPortKind.EVENT,
				ComponentPortRole.USED, set(Integer.class)));
		Assert.assertNotEquals(base, new ComponentDiagramExternalPort(String.class, ComponentPortKind.SERVICE,
				ComponentPortRole.PROVIDED, set(Integer.class)));
		Assert.assertNotEquals(base, new ComponentDiagramExternalPort(String.class, ComponentPortKind.SERVICE,
				ComponentPortRole.USED, set(Long.class)));
		Assert.assertNotEquals(base, "not a port");
	}

	@Test
	public void externalPortCopiesItsTargetsAndExposesThemReadOnly() {
		Set<Class<?>> targets = set(Integer.class);
		ComponentDiagramExternalPort port = new ComponentDiagramExternalPort(String.class, ComponentPortKind.SERVICE,
				ComponentPortRole.USED, targets);
		targets.add(Long.class);
		Assert.assertEquals(set(Integer.class), port.getTargets());
		try {
			port.getTargets().add(Long.class);
			Assert.fail();
		} catch (UnsupportedOperationException expected) {
			// read-only
		}
	}

	@Test
	public void externalPortRejectsNullArguments() {
		Set<Class<?>> t = set(Integer.class);
		try {
			new ComponentDiagramExternalPort(null, ComponentPortKind.SERVICE, ComponentPortRole.USED, t);
			Assert.fail();
		} catch (NullPointerException expected) {
			Assert.assertEquals("owner", expected.getMessage());
		}
		try {
			new ComponentDiagramExternalPort(String.class, null, ComponentPortRole.USED, t);
			Assert.fail();
		} catch (NullPointerException expected) {
			Assert.assertEquals("kind", expected.getMessage());
		}
		try {
			new ComponentDiagramExternalPort(String.class, ComponentPortKind.SERVICE, null, t);
			Assert.fail();
		} catch (NullPointerException expected) {
			Assert.assertEquals("role", expected.getMessage());
		}
		try {
			new ComponentDiagramExternalPort(String.class, ComponentPortKind.SERVICE, ComponentPortRole.USED, null);
			Assert.fail();
		} catch (NullPointerException expected) {
			Assert.assertEquals("targets", expected.getMessage());
		}
	}

	@Test
	public void topologyShortConstructorsDefaultRawTypesAndStereotypesToEmpty() {
		Map<Class<?>, Class<?>> parents = new LinkedHashMap<>();
		parents.put(Integer.class, String.class);
		ComponentDiagramEdge edge = new ComponentDiagramEdge(String.class, Integer.class, ComponentPortKind.COMPONENT_TYPE, null);
		ComponentDiagramExternalPort port = new ComponentDiagramExternalPort(String.class, ComponentPortKind.SERVICE,
				ComponentPortRole.USED, set(Long.class));
		ComponentDiagramTopology six = new ComponentDiagramTopology(set(String.class), Set.of(edge), set(String.class),
				Set.of(port), set(String.class, Integer.class), parents);
		Assert.assertEquals(set(String.class), six.getComponentTypes());
		Assert.assertEquals(Set.of(edge), six.getEdges());
		Assert.assertEquals(set(String.class), six.getCompositeTypes());
		Assert.assertEquals(Set.of(port), six.getExternalPorts());
		Assert.assertEquals(set(String.class, Integer.class), six.getAllComponentTypes());
		Assert.assertEquals(parents, six.getCompositionParentOf());
		Assert.assertTrue(six.getRawTypes().isEmpty());
		Assert.assertNull(six.getStereotype(String.class));

		ComponentDiagramTopology seven = new ComponentDiagramTopology(set(String.class), Set.of(), Set.of(), Set.of(),
				set(String.class), Map.of(), set(String.class));
		Assert.assertEquals(set(String.class), seven.getRawTypes());
		Assert.assertNull(seven.getStereotype(String.class));
	}

	@Test
	public void topologyStereotypeLookupAndImmutability() {
		Map<Class<?>, String> stereotypes = new LinkedHashMap<>();
		stereotypes.put(String.class, "Service");
		ComponentDiagramTopology t = new ComponentDiagramTopology(set(String.class), Set.of(), Set.of(), Set.of(),
				set(String.class), Map.of(), Set.of(), stereotypes);
		stereotypes.put(Integer.class, "Other");
		Assert.assertEquals("Service", t.getStereotype(String.class));
		Assert.assertNull(t.getStereotype(Integer.class));
		try {
			t.getComponentTypes().add(Integer.class);
			Assert.fail();
		} catch (UnsupportedOperationException expected) {
			// read-only
		}
	}

	@Test
	public void topologyRejectsNullCollections() {
		try {
			new ComponentDiagramTopology(null, Set.of(), Set.of(), Set.of(), Set.of(), Map.of());
			Assert.fail();
		} catch (NullPointerException expected) {
			Assert.assertEquals("componentTypes", expected.getMessage());
		}
		try {
			new ComponentDiagramTopology(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Map.of(), Set.of(), null);
			Assert.fail();
		} catch (NullPointerException expected) {
			Assert.assertEquals("stereotypes", expected.getMessage());
		}
	}

	@Test
	public void topologyCarriesTheContainerNameAndIsEmptyNamedByDefault() {
		ComponentDiagramTopology named = new ComponentDiagramTopology(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
				Map.of(), Set.of(), Map.of(), Map.of(), "main");
		Assert.assertEquals("main", named.getContainerName());
		Assert.assertEquals("", new ComponentDiagramTopology(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Map.of())
				.getContainerName());
	}
}
