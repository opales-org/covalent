/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.tests.CovalentContainerizedTests;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Cloned;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsSubscribe;
import org.opales.covalent.container.events.GenericSubscribe;
import org.opales.covalent.container.metamodel.MetamodelWriteServices;
import org.opales.covalent.container.metamodel.MetamodelPortReadServices;
import org.opales.covalent.container.metamodel.MetamodelPortWriteServices;
import org.opales.covalent.container.metamodel.metamodel.ComponentMetamodel;
import org.opales.covalent.container.metamodel.metamodel.EventsMetamodel;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.simpleproducer.SimpleProducer;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.simpleproducer.impl.SimpleProducerImpl;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.ComponentAEvents;

public class MetamodelContainerizedTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	@Before
	public void setUp() {
		this.rcs.registerComponent(this);
		this.cm.getParameters().setSynchronousEvents(true);
	}

	@Test
	public void undeployRemovesMetamodelLookupIndexes() {
		MetamodelWriteServices metamodelWriteServices = this.rcs.useServices(MetamodelWriteServices.class);
		MetamodelPortReadServices metamodelPortReadServices = this.rcs.useServices(MetamodelPortReadServices.class);

		this.cm.deployComponents(SimpleProducerImpl.class);

		Assert.assertNotNull(metamodelWriteServices.readMetamodel(SimpleProducer.class));
		Assert.assertEquals(SimpleProducerImpl.class, metamodelWriteServices.getImplementation(SimpleProducer.class));
		Assert.assertEquals(SimpleProducer.class, metamodelWriteServices.getType(SimpleProducerImpl.class));

		Covalent.main.lcs().undeployComponent(SimpleProducerImpl.class);

		Assert.assertNull(metamodelWriteServices.readMetamodel(SimpleProducer.class));
		Assert.assertNull(metamodelWriteServices.getType(SimpleProducerImpl.class));
	}

	@Test
	public void rawEventsUseGenericSubscribeWhenNoManagedProducerProvidesSubscriber() {
		MetamodelPortReadServices metamodelPortReadServices = this.rcs.useServices(MetamodelPortReadServices.class);

		Class<?> subscribeInterface = metamodelPortReadServices.getSubscribeInterface(ComponentAEvents.class);

		Assert.assertEquals(GenericSubscribe.class, subscribeInterface);
		Assert.assertThrows(IllegalStateException.class,
				() -> metamodelPortReadServices.getEventsForSubscriber(GenericSubscribe.class));
	}

	@Test
	public void rawEventsMetamodelIsAvailableWithoutManagedProducer() throws NoSuchMethodException {
		MetamodelPortReadServices metamodelPortReadServices = this.rcs.useServices(MetamodelPortReadServices.class);

		EventsMetamodel<ComponentAEvents> eventsMetamodel = metamodelPortReadServices.getEventsMetamodel(ComponentAEvents.class);

		Assert.assertEquals(ComponentAEvents.class, eventsMetamodel.getEvents());
		Assert.assertTrue(eventsMetamodel.containsMethod(ComponentAEvents.class.getMethod("onCreate", String.class)));
		Assert.assertFalse(eventsMetamodel.hasCloneableElements());
		Assert.assertFalse(eventsMetamodel.isCloneable());
	}

	@Test
	public void eventsMetamodelIdentifiesClonedInterface() {
		MetamodelPortReadServices metamodelPortReadServices = this.rcs.useServices(MetamodelPortReadServices.class);

		this.cm.deployComponents(ClonedEventsProducerImpl.class);

		EventsMetamodel<ClonedInterfaceEvents> eventsMetamodel =
				metamodelPortReadServices.getEventsMetamodel(ClonedInterfaceEvents.class);
		Assert.assertTrue(eventsMetamodel.hasCloneableElements());
		Assert.assertTrue(eventsMetamodel.isCloneable());
	}

	@Test
	public void eventsMetamodelIdentifiesClonedMethod() throws NoSuchMethodException {
		MetamodelPortReadServices metamodelPortReadServices = this.rcs.useServices(MetamodelPortReadServices.class);

		this.cm.deployComponents(ClonedEventsProducerImpl.class);

		EventsMetamodel<ClonedMethodEvents> eventsMetamodel =
				metamodelPortReadServices.getEventsMetamodel(ClonedMethodEvents.class);
		Assert.assertTrue(eventsMetamodel.hasCloneableElements());
		Assert.assertFalse(eventsMetamodel.isCloneable());
		Assert.assertTrue(eventsMetamodel.isMethodCloneable(ClonedMethodEvents.class.getMethod("changed", String.class)));
	}

	@Test
	public void eventsMetamodelIdentifiesClonedParameter() throws NoSuchMethodException {
		MetamodelPortReadServices metamodelPortReadServices = this.rcs.useServices(MetamodelPortReadServices.class);

		this.cm.deployComponents(ClonedEventsProducerImpl.class);

		EventsMetamodel<ClonedParameterEvents> eventsMetamodel =
				metamodelPortReadServices.getEventsMetamodel(ClonedParameterEvents.class);
		Assert.assertTrue(eventsMetamodel.hasCloneableElements());
		Assert.assertFalse(eventsMetamodel.isCloneable());
		Assert.assertTrue(eventsMetamodel.isMethodParameterClonable(
				ClonedParameterEvents.class.getMethod("changed", String.class).getParameters()[0]));
	}

	@Test
	public void itemProvidedSubscriberIsMappedToItemProducedEvents() {
		MetamodelWriteServices metamodelWriteServices = this.rcs.useServices(MetamodelWriteServices.class);

		this.cm.deployComponents(ItemEventsProducerImpl.class);

		ComponentMetamodel metamodel = metamodelWriteServices.readMetamodel(ItemEventsProducer.class);
		Assert.assertEquals(ItemProducedEvents.class,
				metamodel.getProducedEventsForProvidedSubscriber(ItemProducedEvents.Subscribe.class));
	}

	@Test
	public void itemProducedEventsCanResolveProvidedSubscriberFromMetamodel() {
		MetamodelWriteServices metamodelWriteServices = this.rcs.useServices(MetamodelWriteServices.class);

		this.cm.deployComponents(ItemEventsProducerImpl.class);

		ComponentMetamodel metamodel = metamodelWriteServices.readMetamodel(ItemEventsProducer.class);
		Assert.assertEquals(ItemProducedEvents.Subscribe.class,
				metamodel.getProvidedSubscriberForProducedEvents(ItemProducedEvents.class));
	}

	@Test
	public void itemProducedEventsCanResolveProvidedSubscriberFromMetamodelPortReadServices() {
		MetamodelPortReadServices metamodelPortReadServices = this.rcs.useServices(MetamodelPortReadServices.class);

		this.cm.deployComponents(ItemEventsProducerImpl.class);

		Assert.assertEquals(ItemProducedEvents.Subscribe.class,
				metamodelPortReadServices.getSubscribeInterface(ItemProducedEvents.class));
	}

	@Test
	public void removedProducedEventsClearsCachedEventsMetamodel() {
		MetamodelWriteServices metamodelWriteServices = this.rcs.useServices(MetamodelWriteServices.class);
		MetamodelPortReadServices metamodelPortReadServices = this.rcs.useServices(MetamodelPortReadServices.class);
		MetamodelPortWriteServices metamodelPortWriteServices = this.rcs.useServices(MetamodelPortWriteServices.class);

		this.cm.deployComponents(ClonedEventsProducerImpl.class);

		Assert.assertTrue(metamodelPortReadServices.getEventsMetamodel(ClonedInterfaceEvents.class).isCloneable());

		metamodelPortWriteServices.removeProducedEvents(ClonedEventsProducer.class, ClonedInterfaceEvents.class);

		Assert.assertNull(metamodelWriteServices.readMetamodel(ClonedEventsProducer.class)
				.getEventsMetamodel(ClonedInterfaceEvents.class));
		// The managed component does not produce it, so the cached, component-owned
		// EventsMetamodel is gone (asserted above); but @Cloned is a property of the events
		// interface itself, not of a particular producer, so a freshly rebuilt
		// EventsMetamodel for this interface - the same one a raw producer would get -
		// must still report it as clonable.
		Assert.assertTrue(metamodelPortReadServices.getEventsMetamodel(ClonedInterfaceEvents.class).isCloneable());
	}

	@Test
	public void removedProducedEventsClearsProvidedSubscriberMapping() {
		MetamodelWriteServices metamodelWriteServices = this.rcs.useServices(MetamodelWriteServices.class);
		MetamodelPortReadServices metamodelPortReadServices = this.rcs.useServices(MetamodelPortReadServices.class);
		MetamodelPortWriteServices metamodelPortWriteServices = this.rcs.useServices(MetamodelPortWriteServices.class);

		this.cm.deployComponents(ItemEventsProducerImpl.class);

		ComponentMetamodel metamodel = metamodelWriteServices.readMetamodel(ItemEventsProducer.class);
		Assert.assertEquals(ItemProducedEvents.Subscribe.class,
				metamodel.getProvidedSubscriberForProducedEvents(ItemProducedEvents.class));
		Assert.assertEquals(ItemProducedEvents.class,
				metamodel.getProducedEventsForProvidedSubscriber(ItemProducedEvents.Subscribe.class));

		metamodelPortWriteServices.removeProducedEvents(ItemEventsProducer.class, ItemProducedEvents.class);

		Assert.assertNull(metamodel.getProvidedSubscriberForProducedEvents(ItemProducedEvents.class));
		Assert.assertNull(metamodel.getProducedEventsForProvidedSubscriber(ItemProducedEvents.Subscribe.class));
		Assert.assertEquals(GenericSubscribe.class, metamodelPortReadServices.getSubscribeInterface(ItemProducedEvents.class));
	}

	@Component
	public interface ItemEventsProducer {
	}

	@Implementation
	public static class ItemEventsProducerImpl implements ItemEventsProducer {
		@Item
		private ItemEventsProducerItem item;
	}

	@Item
	public static class ItemEventsProducerItem {
		@ProducedEvents
		private ItemProducedEvents events;
	}

	@Events
	public interface ItemProducedEvents {
		void changed();

		@EventsSubscribe
		interface Subscribe {
			void subscribe(ItemProducedEvents events);
		}
	}

	@Component
	public interface ClonedEventsProducer {
	}

	@Implementation
	public static class ClonedEventsProducerImpl implements ClonedEventsProducer {
		@ProducedEvents
		private ClonedInterfaceEvents clonedInterfaceEvents;

		@ProducedEvents
		private ClonedMethodEvents clonedMethodEvents;

		@ProducedEvents
		private ClonedParameterEvents clonedParameterEvents;
	}

	@Cloned
	@Events
	public interface ClonedInterfaceEvents {
		void changed(String value);
	}

	@Events
	public interface ClonedMethodEvents {
		@Cloned
		void changed(String value);
	}

	@Events
	public interface ClonedParameterEvents {
		void changed(@Cloned String value);
	}
}
