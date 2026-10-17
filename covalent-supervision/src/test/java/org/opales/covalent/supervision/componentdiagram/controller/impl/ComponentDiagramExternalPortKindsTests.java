/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.controller.impl;

import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramEdge;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramExternalPort;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramTopology;
import org.opales.covalent.supervision.componentdiagram.model.ComponentPortKind;
import org.opales.covalent.supervision.componentdiagram.model.ComponentPortRole;
import org.opales.covalent.supervision.testsupport.observed.EventsConsumerHostComponent;
import org.opales.covalent.supervision.testsupport.observed.EventsProducerHostComponent;
import org.opales.covalent.supervision.testsupport.observed.FlatEventsConsumerComponent;
import org.opales.covalent.supervision.testsupport.observed.FlatEventsProducerComponent;
import org.opales.covalent.supervision.testsupport.observed.FlatParametersAccessorComponent;
import org.opales.covalent.supervision.testsupport.observed.FlatParametersOfferorComponent;
import org.opales.covalent.supervision.testsupport.observed.ObservedComponentDiagramTestSupport;
import org.opales.covalent.supervision.testsupport.observed.ParametersAccessorHostComponent;
import org.opales.covalent.supervision.testsupport.observed.ParametersOfferorHostComponent;
import org.opales.covalent.supervision.testsupport.observed.SampleEvents;
import org.opales.covalent.supervision.testsupport.observed.SampleParameters;
import org.opales.covalent.supervision.testsupport.observed.impl.EventsConsumerHostComponentImpl;
import org.opales.covalent.supervision.testsupport.observed.impl.EventsProducerHostComponentImpl;
import org.opales.covalent.supervision.testsupport.observed.impl.ParametersAccessorHostComponentImpl;
import org.opales.covalent.supervision.testsupport.observed.impl.ParametersOfferorHostComponentImpl;

/**
 * Covers the roll-up of event and parameter links that cross a composite's boundary: at the top level they are an
 * edge between the two composites, and inside a composite they are an external port on the inner component with the
 * role its side of the link plays.
 */
public class ComponentDiagramExternalPortKindsTests extends ObservedComponentDiagramTestSupport {

	private ComponentDiagramExternalPort onlyExternalPort() {
		ComponentDiagramTopology topology = this.lastTopology();
		Assert.assertEquals(1, topology.getExternalPorts().size());
		return topology.getExternalPorts().iterator().next();
	}

	private void deployEvents() {
		this.observed.deployComponents(EventsProducerHostComponentImpl.class, EventsConsumerHostComponentImpl.class);
		this.startBoth();
	}

	private void deployParameters() {
		this.observed.deployComponents(ParametersAccessorHostComponentImpl.class,
				ParametersOfferorHostComponentImpl.class);
		this.startBoth();
	}

	@Test
	public void anEventLinkBetweenCompositesIsAnEdgeBetweenThemAtTheTopLevel() {
		this.deployEvents();

		Assert.assertEquals(
				Set.of(new ComponentDiagramEdge(EventsProducerHostComponent.class, EventsConsumerHostComponent.class,
						ComponentPortKind.EVENT, SampleEvents.class)),
				this.lastTopology().getEdges());
	}

	@Test
	public void insideTheProducingCompositeTheProducerHasAProducedPortTowardTheConsumer() {
		this.deployEvents();

		this.componentDiagramControllerServices.enterComposite(this.sessionKey(), EventsProducerHostComponent.class);

		ComponentDiagramExternalPort port = this.onlyExternalPort();
		Assert.assertEquals(FlatEventsProducerComponent.class, port.getOwner());
		Assert.assertEquals(ComponentPortKind.EVENT, port.getKind());
		Assert.assertEquals(ComponentPortRole.PRODUCED, port.getRole());
		Assert.assertEquals(Set.of(FlatEventsConsumerComponent.class), port.getTargets());
	}

	@Test
	public void insideTheConsumingCompositeTheConsumerHasAConsumedPortTowardTheProducer() {
		this.deployEvents();

		this.componentDiagramControllerServices.enterComposite(this.sessionKey(), EventsConsumerHostComponent.class);

		ComponentDiagramExternalPort port = this.onlyExternalPort();
		Assert.assertEquals(FlatEventsConsumerComponent.class, port.getOwner());
		Assert.assertEquals(ComponentPortKind.EVENT, port.getKind());
		Assert.assertEquals(ComponentPortRole.CONSUMED, port.getRole());
		Assert.assertEquals(Set.of(FlatEventsProducerComponent.class), port.getTargets());
	}

	@Test
	public void aParameterLinkBetweenCompositesIsAnEdgeBetweenThemAtTheTopLevel() {
		this.deployParameters();

		Assert.assertEquals(
				Set.of(new ComponentDiagramEdge(ParametersAccessorHostComponent.class,
						ParametersOfferorHostComponent.class, ComponentPortKind.PARAMETER, SampleParameters.class)),
				this.lastTopology().getEdges());
	}

	@Test
	public void insideTheAccessingCompositeTheAccessorHasAnAccessedPortTowardTheOfferor() {
		this.deployParameters();

		this.componentDiagramControllerServices.enterComposite(this.sessionKey(), ParametersAccessorHostComponent.class);

		ComponentDiagramExternalPort port = this.onlyExternalPort();
		Assert.assertEquals(FlatParametersAccessorComponent.class, port.getOwner());
		Assert.assertEquals(ComponentPortKind.PARAMETER, port.getKind());
		Assert.assertEquals(ComponentPortRole.ACCESSED, port.getRole());
		Assert.assertEquals(Set.of(FlatParametersOfferorComponent.class), port.getTargets());
	}

	@Test
	public void insideTheOfferingCompositeTheOfferorHasAnOfferedPortTowardTheAccessor() {
		this.deployParameters();

		this.componentDiagramControllerServices.enterComposite(this.sessionKey(), ParametersOfferorHostComponent.class);

		ComponentDiagramExternalPort port = this.onlyExternalPort();
		Assert.assertEquals(FlatParametersOfferorComponent.class, port.getOwner());
		Assert.assertEquals(ComponentPortKind.PARAMETER, port.getKind());
		Assert.assertEquals(ComponentPortRole.OFFERED, port.getRole());
		Assert.assertEquals(Set.of(FlatParametersAccessorComponent.class), port.getTargets());
	}
}
