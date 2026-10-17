/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumercomponent.ConsumerComponent;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumercomponent.ConsumerComponentServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumercomponent.impl.ConsumerComponentImpl;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent.ProducerComponent;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent.ProducerComponentServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent.impl.ProducerComponentImpl;

/**
 * <p>
 * Testing component items, custom subscribe with parameters, subscribe multiple
 * times with different parameter values, multiple component items of the same
 * type...
 * </p>
 */
public class ComponentsProducerAndConsumerTests {
	private final static CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	// A direct container facade, not a raw useServices() proxy: it does not
	// require the container to already be running, unlike
	// Covalent.main.rcs().useServices(LifecycleServices.class).
	private final LifecycleServices lifecycleServices = Covalent.main.lcs();

	@Before
	public void setup() {
		this.rcs.registerComponent(this);
		cm.getParameters().setSynchronousEvents(true);
		cm.start();
		this.lifecycleServices.deployComponents(ProducerComponentImpl.class, ConsumerComponentImpl.class);
		// The container is already started above; activate the two components
		// just deployed instead of calling start() again.
		this.lifecycleServices.activate();
	}

	@Test
	public void doEventsTest() {
		final ConsumerComponentServices consumerComponentServices = this.rcs.useServices(ConsumerComponent.class,
				ConsumerComponentServices.class);
		final ProducerComponentServices producerComponentServices = this.rcs.useServices(ProducerComponent.class,
				ProducerComponentServices.class);
		producerComponentServices.produceEvents();
		producerComponentServices.service();

		final List<String> itemAMessages = consumerComponentServices.getEventsReceivedByFirstItemA();
		// first item subscribed twice, once for each ID, and with a lambda observer
		// (hence the trailing "$")
		Assert.assertEquals(4, itemAMessages.size());
		Assert.assertEquals("subscribe_idA_ConsumerItemA$", itemAMessages.get(0));
		Assert.assertEquals("subscribe_idB_ConsumerItemA$", itemAMessages.get(1));
		Assert.assertEquals("idA", itemAMessages.get(2));
		Assert.assertEquals("idB", itemAMessages.get(3));

		final List<String> itemA2Messages = consumerComponentServices.getEventsReceivedBySecondItemA();
		// this item did not subscribe to any event
		Assert.assertEquals(0, itemA2Messages.size());

		final List<String> itemBObserverMessages = consumerComponentServices.getEventsReceivedByItemBObserver();
		Assert.assertEquals(2, itemBObserverMessages.size());
		Assert.assertEquals("subscribe_idB_ConsumerItemB$", itemBObserverMessages.get(0));
		Assert.assertEquals("idB", itemBObserverMessages.get(1));

		final List<String> itemBMessages = consumerComponentServices.getEventsReceivedByItemB();
		Assert.assertEquals(4, itemBMessages.size());
		Assert.assertEquals("subscribe_ConsumerItemB", itemBMessages.get(0));
		Assert.assertEquals("subscribe_ConsumerItemB#Global", itemBMessages.get(1));
		Assert.assertEquals("produceEvents", itemBMessages.get(2));
		Assert.assertEquals("service", itemBMessages.get(3));

		final List<String> implMessages = consumerComponentServices.getEventsReceivedByImpl();
		Assert.assertEquals(5, implMessages.size());
		Assert.assertEquals("subscribe_ConsumerComponentImpl$", implMessages.get(0));
		Assert.assertEquals("subscribe_ConsumerComponentImpl$#Global", implMessages.get(1));
		Assert.assertEquals("subscribe_ConsumerItemB#Global", implMessages.get(2));
		Assert.assertEquals("produceEvents", implMessages.get(3));
		Assert.assertEquals("service", implMessages.get(4));

	}

	@After
	public void teardown() {
		cm.stop();
	}

}
