/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.events.EventsManagerServices;
import org.opales.covalent.container.events.impl.ConsumerMetadata;
import org.opales.covalent.container.events.impl.ProducerSideNotifierInvocationHandler;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.rr.parameters.ParametersManagerServices;
import org.opales.covalent.container.rr.services.ServicesManagerServices;
import org.opales.covalent.container.tests.CovalentContainerizedTests;
import org.opales.covalent.container.tests.unit.ParametersManagerContainerizedTests.ManualParameters;
import org.opales.covalent.container.tests.unit.ParametersManagerContainerizedTests.ManualParametersComponent;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.ComponentAEvents;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.ComponentAServices;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.impl.ComponentA;

/**
 * <p>
 * Audits the collection-returning APIs of {@code covalent-core} outside the
 * {@code registry} scope (events, request/response common bookkeeping) for
 * exposure of mutable internal storage. Each getter hands back a snapshot, not a
 * live reference to internal {@code Map}/{@code Set} state - some of it nested
 * ({@code Map<Class<?>, Set<Class<?>>>}, where copying the outer map alone would
 * still leave the inner sets shared) - so a caller cannot corrupt bookkeeping it
 * should only be able to read. These tests
 * prove a returned snapshot (and, where a nested collection is involved, a
 * value inside it) can be mutated without affecting what a later call
 * returns.
 * </p>
 */
public class MutableInternalStorageAuditTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	@Before
	public void setUp() {
		this.rcs.registerComponent(this);
		this.cm.getParameters().setSynchronousEvents(true);
	}

	@Test
	public void eventsManagerProducerToEventMapIsIsolatedOnRead() {
		ComponentA producer = new ComponentA();
		this.rcs.registerComponent(producer);
		try {
			EventsManagerServices eventsManagerServices = this.rcs.useServices(EventsManagerServices.class);

			Map<Class<?>, Set<Class<?>>> outerSnapshot = eventsManagerServices.getProducerToEvent();
			Assert.assertTrue(outerSnapshot.containsKey(ComponentA.class));

			// Outer map: removing an entry from the snapshot must not affect internal storage.
			outerSnapshot.remove(ComponentA.class);
			Assert.assertTrue(eventsManagerServices.getProducerToEvent().containsKey(ComponentA.class));

			// Inner set (nested collection): clearing it must not affect internal storage.
			Set<Class<?>> innerSnapshot = eventsManagerServices.getProducerToEvent().get(ComponentA.class);
			Assert.assertFalse(innerSnapshot.isEmpty());
			innerSnapshot.clear();
			Assert.assertFalse(eventsManagerServices.getProducerToEvent().get(ComponentA.class).isEmpty());
		} finally {
			this.rcs.unregisterComponent(ComponentA.class);
		}
	}

	@Test
	public void servicesManagerProviderAndUserMapsAreIsolatedOnRead() {
		ComponentA provider = new ComponentA();
		this.rcs.registerComponent(provider);
		try {
			ComponentAServices services = this.rcs.useServices(ComponentA.class, ComponentAServices.class);
			Assert.assertEquals("A data", services.getData());

			ServicesManagerServices servicesManagerServices = this.rcs.useServices(ServicesManagerServices.class);

			Map<Class<?>, Set<Class<?>>> providerToServices = servicesManagerServices.getProviderToServices();
			Assert.assertTrue(providerToServices.get(ComponentA.class).contains(ComponentAServices.class));
			providerToServices.get(ComponentA.class).clear();
			Assert.assertTrue(servicesManagerServices.getProviderToServices().get(ComponentA.class)
					.contains(ComponentAServices.class));

			Map<Class<?>, Set<Class<?>>> serviceToUsers = servicesManagerServices.getServiceToUsers();
			Assert.assertTrue(serviceToUsers.get(ComponentAServices.class)
					.contains(MutableInternalStorageAuditTests.class));
			serviceToUsers.remove(ComponentAServices.class);
			Assert.assertTrue(servicesManagerServices.getServiceToUsers().get(ComponentAServices.class)
					.contains(MutableInternalStorageAuditTests.class));

			this.rcs.unuseServices(ComponentAServices.class);
		} finally {
			this.rcs.unregisterComponent(ComponentA.class);
		}
	}

	@Test
	public void parametersManagerOfferorAndAccessorMapsAreIsolatedOnRead() {
		ManualParametersComponent offeror = new ManualParametersComponent();
		this.rcs.registerComponent(offeror);
		try {
			ManualParameters parameters = this.rcs.accessParameters(ManualParametersComponent.class,
					ManualParameters.class);
			Assert.assertTrue(parameters.getParameter());

			ParametersManagerServices parametersManagerServices = this.rcs
					.useServices(ParametersManagerServices.class);

			Map<Class<?>, Set<Class<?>>> offerorToParameters = parametersManagerServices.getOfferorToParameters();
			Assert.assertTrue(offerorToParameters.get(ManualParametersComponent.class).contains(ManualParameters.class));
			offerorToParameters.get(ManualParametersComponent.class).clear();
			Assert.assertTrue(parametersManagerServices.getOfferorToParameters().get(ManualParametersComponent.class)
					.contains(ManualParameters.class));

			Map<Class<?>, Set<Class<?>>> parametersToAccessor = parametersManagerServices.getParametersToAccessor();
			Assert.assertTrue(parametersToAccessor.get(ManualParameters.class)
					.contains(MutableInternalStorageAuditTests.class));
			parametersToAccessor.remove(ManualParameters.class);
			Assert.assertTrue(parametersManagerServices.getParametersToAccessor().get(ManualParameters.class)
					.contains(MutableInternalStorageAuditTests.class));

			this.rcs.unaccessParameters(ManualParameters.class);
		} finally {
			this.rcs.unregisterComponent(ManualParametersComponent.class);
		}
	}

	@Test
	public void producerNotifierConsumersAndPendingSubscribersAreIsolatedOnRead() {
		ComponentA producer = new ComponentA();
		this.rcs.registerComponent(producer);
		NoOpComponentAEventsConsumer consumer = new NoOpComponentAEventsConsumer();
		try {
			this.rcs.subscribe(MutableInternalStorageAuditTests.class, ComponentAEvents.class, consumer);

			ComponentAEvents producedProxy = producer.getProduceComponentAEvents();
			ProducerSideNotifierInvocationHandler<ComponentAEvents, ?> handler = this.getHandler(producedProxy);

			Set<ConsumerMetadata<ComponentAEvents>> consumersSnapshot = handler.getConsumersMetadata();
			Assert.assertEquals(1, consumersSnapshot.size());
			consumersSnapshot.clear();
			Assert.assertEquals(1, handler.getConsumersMetadata().size());

			// Moves the current consumer's metadata into pendingSubscribers, so we can
			// exercise getPendingSubscribers() the same way, on real internal state.
			handler.parkSubscribers();

			Set<ConsumerMetadata<?>> pendingSnapshot = handler.getPendingSubscribers();
			Assert.assertEquals(1, pendingSnapshot.size());
			pendingSnapshot.clear();
			Assert.assertEquals(1, handler.getPendingSubscribers().size());
		} finally {
			this.rcs.unsubscribe(MutableInternalStorageAuditTests.class, ComponentAEvents.class, consumer);
			this.rcs.unregisterComponent(ComponentA.class);
		}
	}

	@SuppressWarnings("unchecked")
	private ProducerSideNotifierInvocationHandler<ComponentAEvents, ?> getHandler(final ComponentAEvents producedProxy) {
		return (ProducerSideNotifierInvocationHandler<ComponentAEvents, ?>) Proxy.getInvocationHandler(producedProxy);
	}

	private static final class NoOpComponentAEventsConsumer implements ComponentAEvents {
		@Override
		public void onCreate(final String data) {
		}

		@Override
		public void onUpdate(final String data) {
		}

		@Override
		public void onDelete(final String data) {
		}

		@Override
		public void onEmpty() {
		}
	}

}
