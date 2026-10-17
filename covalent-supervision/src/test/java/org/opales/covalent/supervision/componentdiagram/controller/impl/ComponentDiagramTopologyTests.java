/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.controller.impl;

import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramEdge;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramExternalPort;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramTopology;
import org.opales.covalent.supervision.componentdiagram.model.ComponentPortKind;
import org.opales.covalent.supervision.componentdiagram.model.ComponentPortRole;
import org.opales.covalent.supervision.testsupport.observed.CompositeAComponent;
import org.opales.covalent.supervision.testsupport.observed.SampleServicesA;
import org.opales.covalent.supervision.testsupport.observed.SampleServicesB;
import org.opales.covalent.supervision.testsupport.observed.CompositeBComponent;
import org.opales.covalent.supervision.testsupport.observed.LeafAProviderComponent;
import org.opales.covalent.supervision.testsupport.observed.LeafAUserComponent;
import org.opales.covalent.supervision.testsupport.observed.LeafBProviderComponent;
import org.opales.covalent.supervision.testsupport.observed.ObservedComponentDiagramTestSupport;
import org.opales.covalent.supervision.testsupport.observed.impl.CompositeAComponentImpl;
import org.opales.covalent.supervision.testsupport.observed.impl.CompositeBComponentImpl;

/**
 * Covers {@code ComponentDiagramControllerImpl.rebuildTopology()}/{@code focusTopology(...)}:
 * the empty-container baseline, the composition/service edges built from a real two-composite
 * deployment, and all three {@code focusTopology} rollup cases (same-ancestor drop,
 * cross-ancestor edge, external port) via drill-down - see {@link
 * ObservedComponentDiagramTestSupport}'s own javadoc for the harness this builds on.
 * <p>
 * Every topology-side assertion below uses the fixtures' {@code @Component}-annotated marker
 * interfaces (e.g. {@link CompositeAComponent}), never their {@code *Impl} classes: the registry
 * and metamodel key everything by component type, and {@code
 * ComponentDiagramControllerImpl.componentType(Class)} resolves an implementation class back to
 * it before an edge is ever recorded - an {@code *Impl} class never actually appears in a
 * published {@link ComponentDiagramTopology}.
 */
public class ComponentDiagramTopologyTests extends ObservedComponentDiagramTestSupport {

	@Test
	public void emptyObservedContainerYieldsEmptyTopologyOnConnect() {
		this.startBoth();

		ComponentDiagramTopology topology = this.lastTopology();
		Assert.assertNotNull(topology);
		Assert.assertTrue(topology.getComponentTypes().isEmpty());
		Assert.assertTrue(topology.getEdges().isEmpty());
		Assert.assertTrue(topology.getCompositeTypes().isEmpty());
		Assert.assertTrue(topology.getExternalPorts().isEmpty());
	}

	@Test
	public void topLevelTopologyShowsCompositesAndRollsUpCrossCompositeServiceEdge() {
		this.observed.deployComponents(CompositeAComponentImpl.class, CompositeBComponentImpl.class);
		this.startBoth();

		ComponentDiagramTopology topology = this.lastTopology();
		Assert.assertEquals(Set.of(CompositeAComponent.class, CompositeBComponent.class),
				topology.getComponentTypes());
		Assert.assertEquals(Set.of(CompositeAComponent.class, CompositeBComponent.class),
				topology.getCompositeTypes());
		Assert.assertTrue("same-ancestor service edge (LeafAUser -> LeafAProvider) must not appear at top level",
				topology.getEdges().stream().noneMatch(edge -> edge.getKind() == ComponentPortKind.SERVICE
						&& edge.getFrom().equals(CompositeAComponent.class)
						&& edge.getTo().equals(CompositeAComponent.class)));
		Assert.assertEquals(
				Set.of(new ComponentDiagramEdge(CompositeAComponent.class, CompositeBComponent.class,
						ComponentPortKind.SERVICE, SampleServicesB.class)),
				topology.getEdges());
		Assert.assertTrue(topology.getExternalPorts().isEmpty());
	}

	@Test
	public void excludingACompositeAlsoExcludesWhatItContains() {
		this.observed.deployComponents(CompositeAComponentImpl.class, CompositeBComponentImpl.class);
		this.startBoth();

		this.exclusionPolicyControllerServices.addExcludedComponent(CompositeAComponent.class);

		ComponentDiagramTopology topology = this.lastTopology();
		Assert.assertEquals("the parts of the excluded composite must not surface at the top level",
				Set.of(CompositeBComponent.class), topology.getComponentTypes());
		Assert.assertTrue(topology.getEdges().isEmpty());
	}

	@Test
	public void enteringCompositeAShowsSameAncestorEdgeAndExternalPortTowardCompositeB() {
		this.observed.deployComponents(CompositeAComponentImpl.class, CompositeBComponentImpl.class);
		this.startBoth();

		this.componentDiagramControllerServices.enterComposite(this.sessionKey(), CompositeAComponent.class);

		ComponentDiagramTopology topology = this.lastTopology();
		Assert.assertEquals(Set.of(LeafAProviderComponent.class, LeafAUserComponent.class),
				topology.getComponentTypes());
		Assert.assertTrue(topology.getCompositeTypes().isEmpty());
		Assert.assertEquals(
				Set.of(new ComponentDiagramEdge(LeafAUserComponent.class, LeafAProviderComponent.class,
						ComponentPortKind.SERVICE, SampleServicesA.class)),
				topology.getEdges());

		Assert.assertEquals(1, topology.getExternalPorts().size());
		ComponentDiagramExternalPort externalPort = topology.getExternalPorts().iterator().next();
		Assert.assertEquals(LeafAUserComponent.class, externalPort.getOwner());
		Assert.assertEquals(ComponentPortKind.SERVICE, externalPort.getKind());
		Assert.assertEquals(ComponentPortRole.USED, externalPort.getRole());
		Assert.assertEquals(Set.of(LeafBProviderComponent.class), externalPort.getTargets());
	}

	@Test
	public void enteringCompositeBShowsExternalPortTowardCompositeAWithProvidedRole() {
		this.observed.deployComponents(CompositeAComponentImpl.class, CompositeBComponentImpl.class);
		this.startBoth();

		this.componentDiagramControllerServices.enterComposite(this.sessionKey(), CompositeBComponent.class);

		ComponentDiagramTopology topology = this.lastTopology();
		Assert.assertEquals(Set.of(LeafBProviderComponent.class), topology.getComponentTypes());
		Assert.assertTrue(topology.getEdges().isEmpty());

		Assert.assertEquals(1, topology.getExternalPorts().size());
		ComponentDiagramExternalPort externalPort = topology.getExternalPorts().iterator().next();
		Assert.assertEquals(LeafBProviderComponent.class, externalPort.getOwner());
		Assert.assertEquals(ComponentPortKind.SERVICE, externalPort.getKind());
		Assert.assertEquals(ComponentPortRole.PROVIDED, externalPort.getRole());
		Assert.assertEquals(Set.of(LeafAUserComponent.class), externalPort.getTargets());
	}

	@Test
	public void exitToDepthZeroFromCompositeAReturnsToTopLevelTopology() {
		this.observed.deployComponents(CompositeAComponentImpl.class, CompositeBComponentImpl.class);
		this.startBoth();

		this.componentDiagramControllerServices.enterComposite(this.sessionKey(), CompositeAComponent.class);
		this.componentDiagramControllerServices.exitToDepth(this.sessionKey(), 0);

		ComponentDiagramTopology topology = this.lastTopology();
		Assert.assertEquals(Set.of(CompositeAComponent.class, CompositeBComponent.class),
				topology.getComponentTypes());
	}

	@Test
	public void exitToDepthClampsAboveCurrentPathLength() {
		this.observed.deployComponents(CompositeAComponentImpl.class, CompositeBComponentImpl.class);
		this.startBoth();

		this.componentDiagramControllerServices.enterComposite(this.sessionKey(), CompositeAComponent.class);
		this.componentDiagramControllerServices.exitToDepth(this.sessionKey(), 99);

		Assert.assertEquals(List.of(CompositeAComponent.class), this.lastFocusPath());
	}

	@Test
	public void exitToDepthClampsBelowZero() {
		this.observed.deployComponents(CompositeAComponentImpl.class, CompositeBComponentImpl.class);
		this.startBoth();

		this.componentDiagramControllerServices.enterComposite(this.sessionKey(), CompositeAComponent.class);
		this.componentDiagramControllerServices.exitToDepth(this.sessionKey(), -5);

		Assert.assertEquals(List.of(), this.lastFocusPath());
	}

	@Test
	public void enterCompositeClearsSelectionAndPublishesFocusPath() {
		this.observed.deployComponents(CompositeAComponentImpl.class, CompositeBComponentImpl.class);
		this.startBoth();

		this.componentDiagramControllerServices.selectComponents(this.sessionKey(), Set.of(CompositeAComponent.class));
		Assert.assertEquals(Set.of(CompositeAComponent.class), this.lastSelection());

		this.componentDiagramControllerServices.enterComposite(this.sessionKey(), CompositeAComponent.class);

		Assert.assertEquals(Set.of(), this.lastSelection());
		Assert.assertEquals(List.of(CompositeAComponent.class), this.lastFocusPath());
	}

	/** A raw marker, registered directly into the observed container to simulate a new
	 * component appearing after the supervision side is already connected and running,
	 * without needing to start {@link #observed} a second time (not idempotent). */
	private static final class LateRawComponent {
	}

	@Test
	public void registeringARawComponentAfterConnectTriggersARebuild() {
		this.startBoth();
		int countBeforeRegistration = this.topologyChangeCount();

		this.observed.rcs().registerComponent(new LateRawComponent());

		Assert.assertTrue("registering a new component type must trigger at least one more topology rebuild",
				this.topologyChangeCount() > countBeforeRegistration);
		Assert.assertEquals(Set.of(LateRawComponent.class), this.lastTopology().getComponentTypes());
	}

	@Test
	public void disconnectingEmptiesTheTopology() {
		this.observed.deployComponents(CompositeAComponentImpl.class, CompositeBComponentImpl.class);
		this.startBoth();
		Assert.assertFalse(this.lastTopology().getComponentTypes().isEmpty());

		this.disconnectObserved();

		// Nothing is observed any longer: the topology is rebuilt empty, not left showing
		// the formerly observed content.
		Assert.assertTrue(this.lastTopology().getComponentTypes().isEmpty());
	}

	@Test
	public void reconnectingRepublishesTheSameTopologyAsANewRebuild() {
		this.observed.deployComponents(CompositeAComponentImpl.class, CompositeBComponentImpl.class);
		this.startBoth();
		this.disconnectObserved();
		int countBeforeReconnect = this.topologyChangeCount();

		this.reconnect();

		// connect() unconditionally fires onContainerConnected(), which always calls
		// rebuildTopology() - a genuine extra rebuild even though the observed container's
		// own content never actually changed in between.
		Assert.assertTrue(this.topologyChangeCount() > countBeforeReconnect);
		Assert.assertEquals(Set.of(CompositeAComponent.class, CompositeBComponent.class),
				this.lastTopology().getComponentTypes());
	}
}
