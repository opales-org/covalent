/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.registry.PortRegistryServices;
import org.opales.covalent.container.registry.ComponentDescriptor;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.FullyConnected;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.FullyConnectedEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.impl.FullyConnectedImpl;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithdelegation.ProducerWithSubscribeAndDelegationParameters;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithdelegation.impl.ProducerWithSubscribeAndDelegationImpl;

/**
 * <p>
 * {@link ComponentDescriptor} inherits from the package-private
 * {@code ItemDescriptor} a set of accessors returning the declared fields for
 * each port kind (produced events, used services, accessed parameters, used
 * subscribers). These are exercised while building the descriptor and while
 * validating components, but were never read back directly by a test. This
 * deploys the existing {@code FullyConnectedImpl} fixture, which declares
 * exactly one field of each kind, and checks each accessor.
 * </p>
 */
public class ItemDescriptorFieldsTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	@Before
	public void setUp() {
		this.rcs.registerComponent(this);
		this.cm.getParameters().setSynchronousEvents(true);
	}

	@After
	public void tearDown() {
		this.cm.stop();
	}

	@Test
	public void componentDescriptorExposesItsPortFieldsByKind() {
		this.cm.deployComponents(ProducerWithSubscribeAndDelegationImpl.class, FullyConnectedImpl.class);
		this.cm.start();

		PortRegistryServices portRegistryServices = this.rcs.useServices(PortRegistryServices.class);
		ComponentDescriptor descriptor = portRegistryServices.getComponentDescriptor(FullyConnected.class);
		Assert.assertNotNull(descriptor);

		List<Field> producedEventsFields = descriptor.getProducedEventsFields();
		Assert.assertEquals(1, producedEventsFields.size());
		Assert.assertEquals(FullyConnectedEvents.class, producedEventsFields.get(0).getType());

		List<Field> usedServicesFields = descriptor.getUsedServicesFields();
		Assert.assertEquals(1, usedServicesFields.size());
		Assert.assertEquals(ProducerServices.class, usedServicesFields.get(0).getType());

		List<Field> accessedParametersFields = descriptor.getAccessedParametersFields();
		Assert.assertEquals(1, accessedParametersFields.size());
		Assert.assertEquals(ProducerWithSubscribeAndDelegationParameters.class,
				accessedParametersFields.get(0).getType());

		List<Field> usedSubscriberFields = descriptor.getUsedSubscriberFields();
		Assert.assertEquals(1, usedSubscriberFields.size());
		Assert.assertEquals(ProducerWithSubscribeEvents.ProducerEventsSubscribe.class,
				usedSubscriberFields.get(0).getType());
	}
}
