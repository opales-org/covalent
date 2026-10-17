/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.controller.impl;

import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramEdge;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramTopology;
import org.opales.covalent.supervision.componentdiagram.model.ComponentPortKind;
import org.opales.covalent.supervision.testsupport.observed.FlatEventsConsumerComponent;
import org.opales.covalent.supervision.testsupport.observed.FlatEventsProducerComponent;
import org.opales.covalent.supervision.testsupport.observed.FlatParametersAccessorComponent;
import org.opales.covalent.supervision.testsupport.observed.FlatParametersOfferorComponent;
import org.opales.covalent.supervision.testsupport.observed.ObservedComponentDiagramTestSupport;
import org.opales.covalent.supervision.testsupport.observed.SampleEvents;
import org.opales.covalent.supervision.testsupport.observed.SampleParameters;
import org.opales.covalent.supervision.testsupport.observed.impl.FlatEventsConsumerComponentImpl;
import org.opales.covalent.supervision.testsupport.observed.impl.FlatEventsProducerComponentImpl;
import org.opales.covalent.supervision.testsupport.observed.impl.FlatParametersAccessorComponentImpl;
import org.opales.covalent.supervision.testsupport.observed.impl.FlatParametersOfferorComponentImpl;

/**
 * Covers the PARAMETER and EVENT edge collection ({@code collectParameters}/{@code
 * collectEvents}) that {@link ComponentDiagramTopologyTests} does not, plus the default
 * exclusion-policy filtering ({@code excludeInternal}) applied before any edge is even
 * considered.
 */
public class ComponentDiagramParametersAndEventsTopologyTests extends ObservedComponentDiagramTestSupport {

	@Test
	public void parameterEdgeRunsFromAccessorToOfferor() {
		this.observed.deployComponents(FlatParametersOfferorComponentImpl.class,
				FlatParametersAccessorComponentImpl.class);
		this.startBoth();

		ComponentDiagramTopology topology = this.lastTopology();
		Assert.assertEquals(Set.of(FlatParametersOfferorComponent.class, FlatParametersAccessorComponent.class),
				topology.getComponentTypes());
		Assert.assertEquals(
				Set.of(new ComponentDiagramEdge(FlatParametersAccessorComponent.class,
						FlatParametersOfferorComponent.class, ComponentPortKind.PARAMETER, SampleParameters.class)),
				topology.getEdges());
	}

	@Test
	public void eventEdgeRunsFromProducerToConsumer() {
		this.observed.deployComponents(FlatEventsProducerComponentImpl.class, FlatEventsConsumerComponentImpl.class);
		this.startBoth();

		ComponentDiagramTopology topology = this.lastTopology();
		Assert.assertEquals(Set.of(FlatEventsProducerComponent.class, FlatEventsConsumerComponent.class),
				topology.getComponentTypes());
		Assert.assertEquals(
				Set.of(new ComponentDiagramEdge(FlatEventsProducerComponent.class, FlatEventsConsumerComponent.class,
						ComponentPortKind.EVENT, SampleEvents.class)),
				topology.getEdges());
	}

	@Test
	public void internalComponentsAreExcludedByDefault() {
		this.startBoth();

		Assert.assertTrue(this.lastTopology().getComponentTypes().isEmpty());
	}

	@Test
	public void disablingExcludeInternalImmediatelyRebuildsTheTopology() {
		this.startBoth();
		Assert.assertTrue(this.lastTopology().getComponentTypes().isEmpty());

		this.exclusionPolicyControllerServices.setExcludeInternalComponents(false);

		// ComponentDiagramControllerImpl implements ExclusionPolicyEvents and rebuilds on
		// both onExcludedComponentsChanged and onExcludeInternalComponentsChanged, so the
		// policy flip alone is enough - no unrelated registry/services/parameters/events
		// notification is needed to see the observed container's own components appear.
		Assert.assertTrue("the policy change itself must trigger a rebuild",
				this.lastTopology().getComponentTypes().contains(CovalentContainer.class));
	}

	@Test
	public void explicitlyExcludingAComponentImmediatelyRebuildsTheTopology() {
		this.observed.deployComponents(FlatParametersOfferorComponentImpl.class,
				FlatParametersAccessorComponentImpl.class);
		this.startBoth();
		Assert.assertEquals(Set.of(FlatParametersOfferorComponent.class, FlatParametersAccessorComponent.class),
				this.lastTopology().getComponentTypes());

		// addExcludedComponent(), not setExcludedComponents(): the latter replaces the whole
		// list wholesale, which would also lift the default exclusion of ObservedContainerDelegate
		// itself and let it show up as a newly-unexcluded component in the topology.
		this.exclusionPolicyControllerServices.addExcludedComponent(FlatParametersOfferorComponent.class);

		Assert.assertEquals("onExcludedComponentsChanged() must trigger a rebuild on its own, same as "
				+ "onExcludeInternalComponentsChanged()", Set.of(FlatParametersAccessorComponent.class),
				this.lastTopology().getComponentTypes());
	}

	@Test
	public void explicitlyExcludedComponentIsFilteredOutEvenWithoutTheInternalAnnotation() {
		this.observed.deployComponents(FlatParametersOfferorComponentImpl.class);
		this.startBoth();
		Assert.assertEquals(Set.of(FlatParametersOfferorComponent.class), this.lastTopology().getComponentTypes());

		this.exclusionPolicyControllerServices.setExcludedComponents(List.of(FlatParametersOfferorComponent.class));
		this.observed.rcs().registerComponent(new Object());

		Assert.assertFalse(this.lastTopology().getComponentTypes().contains(FlatParametersOfferorComponent.class));
	}
}
