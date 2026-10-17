/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.registry.PortRegistryServices;
import org.opales.covalent.container.registry.ComponentDescriptor;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.FullyConnected;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.FullyConnectedParameters;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.FullyConnectedServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.impl.FullyConnectedImpl;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithdelegation.ProducerWithSubscribeAndDelegationParameters;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithdelegation.impl.ProducerWithSubscribeAndDelegationImpl;

/**
 * Verifies the port membership queries a component descriptor inherits from
 * its item descriptor: whether the component offers a parameters interface,
 * provides or uses a services interface, and accesses a parameters interface,
 * answering true for the ports its metamodel declares and false for ports
 * declared by nobody or only by another component.
 */
public class ItemDescriptorPortQueriesTests {

	private CovalentContainer container;

	private ComponentDescriptor descriptor;

	@Before
	public void setUp() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		this.container.rcs().registerComponent(this);
		this.container.deployComponents(ProducerWithSubscribeAndDelegationImpl.class, FullyConnectedImpl.class);
		this.container.start();
		this.descriptor = this.container.rcs().useServices(PortRegistryServices.class)
				.getComponentDescriptor(FullyConnected.class);
	}

	@After
	public void tearDown() {
		this.container.dispose();
	}

	@Test
	public void descriptorAnswersTrueForPortsItsComponentDeclares() {
		Assert.assertNotNull(this.descriptor);
		Assert.assertTrue(this.descriptor.implementsParameters(FullyConnectedParameters.class));
		Assert.assertTrue(this.descriptor.implementsServices(FullyConnectedServices.class));
		Assert.assertTrue(this.descriptor.usesServices(ProducerServices.class));
		Assert.assertTrue(this.descriptor.accessesParameters(ProducerWithSubscribeAndDelegationParameters.class));
	}

	@Test
	public void descriptorAnswersFalseForPortsItsComponentDoesNotDeclare() {
		Assert.assertFalse(this.descriptor.implementsParameters(ProducerWithSubscribeAndDelegationParameters.class));
		Assert.assertFalse(this.descriptor.implementsServices(ProducerServices.class));
		Assert.assertFalse(this.descriptor.usesServices(FullyConnectedServices.class));
		Assert.assertFalse(this.descriptor.accessesParameters(FullyConnectedParameters.class));
	}
}
