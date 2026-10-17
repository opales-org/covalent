/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.registry.PortRegistryServices;
import org.opales.covalent.container.registry.impl.ComponentDescriptorImpl;
import org.opales.covalent.container.registry.ItemAndField;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.FullyConnected;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.FullyConnectedEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.impl.FullyConnectedImpl;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithdelegation.ProducerWithSubscribeAndDelegationParameters;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithdelegation.impl.ProducerWithSubscribeAndDelegationImpl;

/**
 * The item/field index of {@link ComponentDescriptorImpl} hands out snapshots and copies what it
 * stores: for each port kind (used services, produced events, accessed parameters, used
 * subscribers), mutating a returned list does not alter the descriptor, a later change in the
 * descriptor does not alter a list returned earlier, and mutating the list passed to an
 * {@code add*ItemAndField} call after the call does not alter what the descriptor stored.
 */
public class RegistryItemAndFieldDefensiveCopyTests {

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
	public void usedServicesItemAndFieldIsIsolatedOnReadAndWrite() {
		ComponentDescriptorImpl descriptor = this.deployAndGetFullyConnectedDescriptor();

		List<ItemAndField> snapshot = descriptor.getUsedServicesItemAndField(ProducerServices.class);
		Assert.assertEquals(1, snapshot.size());
		ItemAndField original = snapshot.get(0);

		// Read-side: mutating the returned snapshot must not touch internal storage.
		snapshot.clear();
		List<ItemAndField> afterSnapshotMutation = descriptor.getUsedServicesItemAndField(ProducerServices.class);
		Assert.assertEquals(1, afterSnapshotMutation.size());

		// Read-side: a later owner-side change must not corrupt a previously returned snapshot.
		List<ItemAndField> previouslyReturned = descriptor.getUsedServicesItemAndField(ProducerServices.class);
		List<ItemAndField> twoEntries = new ArrayList<>();
		twoEntries.add(original);
		twoEntries.add(original);
		descriptor.addUsedServicesItemAndField(ProducerServices.class, twoEntries);
		Assert.assertEquals(1, previouslyReturned.size());
		Assert.assertEquals(2, descriptor.getUsedServicesItemAndField(ProducerServices.class).size());

		// Write-side: mutating the caller's list after ingest must not touch stored state.
		List<ItemAndField> callerList = new ArrayList<>();
		callerList.add(original);
		descriptor.addUsedServicesItemAndField(ProducerServices.class, callerList);
		callerList.clear();
		Assert.assertEquals(1, descriptor.getUsedServicesItemAndField(ProducerServices.class).size());
	}

	@Test
	public void producedEventsItemAndFieldIsIsolatedOnReadAndWrite() {
		ComponentDescriptorImpl descriptor = this.deployAndGetFullyConnectedDescriptor();

		List<ItemAndField> snapshot = descriptor.getProducedEventsItemAndField(FullyConnectedEvents.class);
		Assert.assertEquals(1, snapshot.size());
		ItemAndField original = snapshot.get(0);

		snapshot.clear();
		Assert.assertEquals(1, descriptor.getProducedEventsItemAndField(FullyConnectedEvents.class).size());

		List<ItemAndField> callerList = new ArrayList<>();
		callerList.add(original);
		descriptor.addProducedEventsItemAndField(FullyConnectedEvents.class, callerList);
		callerList.clear();
		Assert.assertEquals(1, descriptor.getProducedEventsItemAndField(FullyConnectedEvents.class).size());
	}

	@Test
	public void accessedParametersItemAndFieldIsIsolatedOnReadAndWrite() {
		ComponentDescriptorImpl descriptor = this.deployAndGetFullyConnectedDescriptor();

		List<ItemAndField> snapshot = descriptor
				.getAccessedParametersItemAndField(ProducerWithSubscribeAndDelegationParameters.class);
		Assert.assertEquals(1, snapshot.size());
		ItemAndField original = snapshot.get(0);

		snapshot.clear();
		Assert.assertEquals(1,
				descriptor.getAccessedParametersItemAndField(ProducerWithSubscribeAndDelegationParameters.class)
						.size());

		List<ItemAndField> callerList = new ArrayList<>();
		callerList.add(original);
		descriptor.addAccessedParametersItemAndField(ProducerWithSubscribeAndDelegationParameters.class, callerList);
		callerList.clear();
		Assert.assertEquals(1,
				descriptor.getAccessedParametersItemAndField(ProducerWithSubscribeAndDelegationParameters.class)
						.size());
	}

	@Test
	public void usedSubscribeItemAndFieldIsIsolatedOnReadAndWrite() {
		ComponentDescriptorImpl descriptor = this.deployAndGetFullyConnectedDescriptor();

		List<ItemAndField> snapshot = descriptor
				.getUsedSubscribeItemAndField(ProducerWithSubscribeEvents.ProducerEventsSubscribe.class);
		Assert.assertEquals(1, snapshot.size());
		ItemAndField original = snapshot.get(0);

		snapshot.clear();
		Assert.assertEquals(1,
				descriptor.getUsedSubscribeItemAndField(ProducerWithSubscribeEvents.ProducerEventsSubscribe.class)
						.size());

		List<ItemAndField> callerList = new ArrayList<>();
		callerList.add(original);
		descriptor.addUsedSubscribeItemAndField(ProducerWithSubscribeEvents.ProducerEventsSubscribe.class,
				callerList);
		callerList.clear();
		Assert.assertEquals(1,
				descriptor.getUsedSubscribeItemAndField(ProducerWithSubscribeEvents.ProducerEventsSubscribe.class)
						.size());
	}

	/**
	 * Deploys the {@code ProducerWithSubscribeAndDelegationImpl} /
	 * {@code FullyConnectedImpl} fixture pair (which declares exactly one
	 * field of each port kind) and returns the descriptor for
	 * {@code FullyConnected}.
	 *
	 * @return the started fixture's {@code FullyConnected} component descriptor
	 */
	@Test
	public void aDescriptorReadsNoItemAndFieldAsAnEmptyList() {
		ComponentDescriptorImpl descriptor = this.deployAndGetFullyConnectedDescriptor();

		Assert.assertEquals(List.of(), descriptor.getUsedServicesItemAndField(Runnable.class));
		Assert.assertEquals(List.of(), descriptor.getProducedEventsItemAndField(Runnable.class));
		Assert.assertEquals(List.of(), descriptor.getAccessedParametersItemAndField(Runnable.class));
		Assert.assertEquals(List.of(), descriptor.getUsedSubscribeItemAndField(Runnable.class));
	}

	@Test
	public void thePortRegistryAnswersNothingForAnUnknownComponentTypeOrInterface() {
		this.cm.start();
		PortRegistryServices registry = this.rcs.useServices(PortRegistryServices.class);

		Assert.assertEquals(List.of(), registry.getUsedServicesItemAndField(String.class, Runnable.class));
		Assert.assertEquals(List.of(), registry.getProducedEventsItemAndField(String.class, Runnable.class));
		Assert.assertEquals(List.of(), registry.getAccessedParametersItemAndField(String.class, Runnable.class));
		Assert.assertEquals(List.of(), registry.getUsedSubscribeItemAndField(String.class, Runnable.class));
		Assert.assertNull(registry.getServicesProvider(Runnable.class));
		Assert.assertNull(registry.getParametersOfferor(Runnable.class));
		Assert.assertNull(registry.getEventsConsumer(String.class, Runnable.class));
		Assert.assertNull(registry.getSubscribeDelegate(Runnable.class));
	}

	private ComponentDescriptorImpl deployAndGetFullyConnectedDescriptor() {
		this.cm.deployComponents(ProducerWithSubscribeAndDelegationImpl.class, FullyConnectedImpl.class);
		this.cm.start();
		PortRegistryServices portRegistryServices = this.rcs.useServices(PortRegistryServices.class);
		ComponentDescriptorImpl descriptor = (ComponentDescriptorImpl) portRegistryServices
				.getComponentDescriptor(FullyConnected.class);
		Assert.assertNotNull(descriptor);
		return descriptor;
	}
}
