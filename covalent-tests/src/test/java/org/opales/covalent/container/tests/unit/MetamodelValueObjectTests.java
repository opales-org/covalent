/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.container.events.GenericSubscribe;
import org.opales.covalent.container.metamodel.metamodel.EventsMetamodel;
import org.opales.covalent.container.metamodel.metamodel.impl.ComponentItemMetamodelImpl;
import org.opales.covalent.container.metamodel.metamodel.impl.ComponentMetamodelImpl;
import org.opales.covalent.container.metamodel.metamodel.impl.EventsMetamodelImpl;

public class MetamodelValueObjectTests {

	@Test
	public void itemMetamodelStoresPortsMappingsAndFields() throws Exception {
		ComponentItemMetamodelImpl metamodel = new ComponentItemMetamodelImpl();
		Field servicesField = FieldHolder.class.getDeclaredField("services");
		Field eventsField = FieldHolder.class.getDeclaredField("events");
		Field subscriberField = FieldHolder.class.getDeclaredField("subscriber");
		Field parametersField = FieldHolder.class.getDeclaredField("parameters");

		metamodel.setImplementation(ItemImplementation.class);
		metamodel.setProvidedServices(Collections.singletonList(ServiceA.class));
		metamodel.setUsedServices(Collections.singletonList(ServiceB.class));
		metamodel.setProducedEvents(Collections.singletonList(EventA.class));
		metamodel.setConsumedEvents(Collections.singletonList(EventB.class));
		metamodel.setProvidedSubscribers(Collections.singletonList(SubscribeA.class));
		metamodel.setImplementedSubscribers(Collections.singletonList(SubscribeB.class));
		metamodel.setUsedSubscribers(Collections.singletonList(SubscribeC.class));
		metamodel.setOfferedParameters(Collections.singletonList(ParametersA.class));
		metamodel.setAccessedParameters(Collections.singletonList(ParametersB.class));
		metamodel.mapUsedSubscriberAndConsumedEvents(SubscribeC.class, EventB.class);
		metamodel.mapProvidedSubscriberAndProducedEvents(SubscribeA.class, EventA.class);
		metamodel.addUsedServicesField(ServiceB.class, servicesField);
		metamodel.addProducedEventsField(EventA.class, eventsField);
		metamodel.addUsedSubscriberField(SubscribeC.class, subscriberField);
		metamodel.addAccessedParametersField(ParametersB.class, parametersField);

		Assert.assertEquals(ItemImplementation.class, metamodel.getImplementation());
		Assert.assertEquals(Collections.singletonList(ServiceA.class), metamodel.getProvidedServices());
		Assert.assertEquals(Collections.singletonList(ServiceB.class), metamodel.getUsedServices());
		Assert.assertEquals(Collections.singletonList(EventA.class), metamodel.getProducedEvents());
		Assert.assertEquals(Collections.singletonList(EventB.class), metamodel.getConsumedEvents());
		Assert.assertEquals(Collections.singletonList(SubscribeA.class), metamodel.getProvidedSubscribers());
		Assert.assertEquals(Collections.singletonList(SubscribeB.class), metamodel.getImplementedSubscribers());
		Assert.assertEquals(Collections.singletonList(SubscribeC.class), metamodel.getUsedSubscribers());
		Assert.assertEquals(Collections.singletonList(ParametersA.class), metamodel.getOfferedParameters());
		Assert.assertEquals(Collections.singletonList(ParametersB.class), metamodel.getAccessedParameters());
		Assert.assertEquals(EventB.class, metamodel.getItemConsumedEventsForUsedSubscriber(SubscribeC.class));
		Assert.assertEquals(SubscribeA.class, metamodel.getProvidedSubscriberForItemProducedEvents(EventA.class));
		Assert.assertEquals(EventA.class, metamodel.getItemProducedEventsForProvidedSubscriber(SubscribeA.class));
		Assert.assertEquals(Collections.singletonList(servicesField), metamodel.getUsedServicesFields());
		Assert.assertSame(servicesField, metamodel.getUsedServicesField(ServiceB.class));
		Assert.assertEquals(Collections.singletonList(eventsField), metamodel.getProducedEventsFields());
		Assert.assertSame(eventsField, metamodel.getProducedEventsField(EventA.class));
		Assert.assertEquals(Collections.singletonList(subscriberField), metamodel.getUsedSubscriberFields());
		Assert.assertSame(subscriberField, metamodel.getUsedSubscriberField(SubscribeC.class));
		Assert.assertEquals(Collections.singletonList(parametersField), metamodel.getAccessedParametersFields());
		Assert.assertSame(parametersField, metamodel.getAccessedParametersField(ParametersB.class));

		String text = metamodel.toString();
		Assert.assertTrue(text.contains("Provided services"));
		Assert.assertTrue(text.contains("Used services"));
		Assert.assertTrue(text.contains("Produced events"));
		Assert.assertTrue(text.contains("Consumed events"));
		Assert.assertTrue(text.contains("Provided events subscribes"));
		Assert.assertTrue(text.contains("Used events subscribes"));
	}

	@Test
	public void itemMetamodelReturnsPortCopies() {
		ComponentItemMetamodelImpl metamodel = new ComponentItemMetamodelImpl();
		metamodel.setProvidedServices(Collections.singletonList(ServiceA.class));
		metamodel.setUsedServices(Collections.singletonList(ServiceB.class));
		metamodel.setProducedEvents(Collections.singletonList(EventA.class));
		metamodel.setConsumedEvents(Collections.singletonList(EventB.class));
		metamodel.setOfferedParameters(Collections.singletonList(ParametersA.class));
		metamodel.setAccessedParameters(Collections.singletonList(ParametersB.class));
		metamodel.setProvidedSubscribers(Collections.singletonList(SubscribeA.class));
		metamodel.setImplementedSubscribers(Collections.singletonList(SubscribeB.class));
		metamodel.setUsedSubscribers(Collections.singletonList(SubscribeC.class));

		metamodel.getProvidedServices().add(ServiceC.class);
		metamodel.getUsedServices().add(ServiceD.class);
		metamodel.getProducedEvents().add(EventC.class);
		metamodel.getConsumedEvents().add(EventD.class);
		metamodel.getOfferedParameters().add(ParametersC.class);
		metamodel.getAccessedParameters().add(ParametersD.class);
		metamodel.getProvidedSubscribers().add(SubscribeD.class);
		metamodel.getImplementedSubscribers().add(SubscribeD.class);
		metamodel.getUsedSubscribers().add(SubscribeD.class);

		Assert.assertEquals(Collections.singletonList(ServiceA.class), metamodel.getProvidedServices());
		Assert.assertEquals(Collections.singletonList(ServiceB.class), metamodel.getUsedServices());
		Assert.assertEquals(Collections.singletonList(EventA.class), metamodel.getProducedEvents());
		Assert.assertEquals(Collections.singletonList(EventB.class), metamodel.getConsumedEvents());
		Assert.assertEquals(Collections.singletonList(ParametersA.class), metamodel.getOfferedParameters());
		Assert.assertEquals(Collections.singletonList(ParametersB.class), metamodel.getAccessedParameters());
		Assert.assertEquals(Collections.singletonList(SubscribeA.class), metamodel.getProvidedSubscribers());
		Assert.assertEquals(Collections.singletonList(SubscribeB.class), metamodel.getImplementedSubscribers());
		Assert.assertEquals(Collections.singletonList(SubscribeC.class), metamodel.getUsedSubscribers());
	}

	@Test
	public void componentMetamodelAggregatesPortsFromComponentAndItems() {
		ComponentMetamodelImpl metamodel = new ComponentMetamodelImpl();
		ComponentItemMetamodelImpl itemMetamodel = new ComponentItemMetamodelImpl();

		metamodel.setComponentType(ComponentType.class);
		metamodel.setImplementation(ComponentImplementation.class);
		metamodel.setProvidedServices(Collections.singletonList(ServiceA.class));
		metamodel.setUsedServices(Collections.singletonList(ServiceB.class));
		metamodel.setProducedEvents(Collections.singletonList(EventA.class));
		metamodel.setConsumedEvents(Collections.singletonList(EventB.class));
		metamodel.setOfferedParameters(Collections.singletonList(ParametersA.class));
		metamodel.setAccessedParameters(Collections.singletonList(ParametersB.class));
		metamodel.setProvidedSubscribers(Collections.singletonList(SubscribeA.class));
		metamodel.setUsedSubscribers(Collections.singletonList(SubscribeB.class));

		itemMetamodel.setImplementation(ItemImplementation.class);
		itemMetamodel.setProvidedServices(Collections.singletonList(ServiceC.class));
		itemMetamodel.setUsedServices(Collections.singletonList(ServiceD.class));
		itemMetamodel.setProducedEvents(Collections.singletonList(EventC.class));
		itemMetamodel.setConsumedEvents(Collections.singletonList(EventD.class));
		itemMetamodel.setOfferedParameters(Collections.singletonList(ParametersC.class));
		itemMetamodel.setAccessedParameters(Collections.singletonList(ParametersD.class));
		itemMetamodel.setProvidedSubscribers(Collections.singletonList(SubscribeC.class));
		itemMetamodel.setUsedSubscribers(Collections.singletonList(SubscribeD.class));
		metamodel.setItemMetamodels(Collections.singletonList(itemMetamodel));

		assertSameElements(metamodel.getAllProvidedServices(), ServiceA.class, ServiceC.class);
		assertSameElements(metamodel.getAllUsedServices(), ServiceB.class, ServiceD.class);
		assertSameElements(metamodel.getAllProducedEvents(), EventA.class, EventC.class);
		assertSameElements(metamodel.getAllConsumedEvents(), EventB.class, EventD.class);
		assertSameElements(metamodel.getAllOfferedParameters(), ParametersA.class, ParametersC.class);
		assertSameElements(metamodel.getAllAccessedParameters(), ParametersB.class, ParametersD.class);
		assertSameElements(metamodel.getAllProvidedSubscribers(), SubscribeA.class, SubscribeC.class);
		assertSameElements(metamodel.getAllUsedSubscribers(), SubscribeB.class, SubscribeD.class);
	}

	private static void assertSameElements(final Collection<Class<?>> actual, final Class<?>... expected) {
		Assert.assertEquals(Set.of(expected), new HashSet<>(actual));
		Assert.assertEquals("duplicated element in " + actual, expected.length, actual.size());
	}

	@Test
	public void componentMetamodelResolvesSubscriberMappingsAndAutomaticEvents() {
		ComponentMetamodelImpl metamodel = new ComponentMetamodelImpl();
		ComponentItemMetamodelImpl itemMetamodel = new ComponentItemMetamodelImpl();

		metamodel.setConsumedEvents(Arrays.asList(EventA.class, EventB.class));
		metamodel.setUsedSubscribers(Collections.singletonList(SubscribeA.class));
		metamodel.mapUsedSubscriberAndConsumedEvents(SubscribeA.class, EventA.class);
		itemMetamodel.setConsumedEvents(Collections.singletonList(EventC.class));
		itemMetamodel.setUsedSubscribers(Collections.singletonList(SubscribeC.class));
		itemMetamodel.mapUsedSubscriberAndConsumedEvents(SubscribeC.class, EventC.class);
		itemMetamodel.mapProvidedSubscriberAndProducedEvents(SubscribeD.class, EventD.class);
		metamodel.setItemMetamodels(Collections.singletonList(itemMetamodel));

		Assert.assertEquals(EventA.class, metamodel.getConsumedEventsForUsedSubscriber(SubscribeA.class));
		Assert.assertEquals(EventC.class, metamodel.getConsumedEventsForUsedSubscriber(SubscribeC.class));
		Assert.assertEquals(EventD.class, metamodel.getProducedEventsForProvidedSubscriber(SubscribeD.class));
		Assert.assertNull(metamodel.getConsumedEventsForUsedSubscriber(SubscribeB.class));
		Assert.assertNull(metamodel.getProducedEventsForProvidedSubscriber(SubscribeB.class));
		Assert.assertTrue(metamodel.getAllConsumedEventsWithSubscriber().containsAll(Arrays.asList(EventA.class, EventC.class)));
		Assert.assertEquals(Collections.singleton(EventB.class), metamodel.getAllConsumedEventsWithoutSubscriber());

		ComponentMetamodelImpl genericMetamodel = new ComponentMetamodelImpl();
		genericMetamodel.setConsumedEvents(Arrays.asList(EventA.class, EventB.class));
		genericMetamodel.setUsedSubscribers(Collections.singletonList(GenericSubscribe.class));

		Assert.assertTrue(genericMetamodel.getAllConsumedEventsWithoutSubscriber().isEmpty());
	}

	@Test
	public void componentMetamodelStoresFieldsEventsMetamodelsAndNestedMetamodels() throws Exception {
		ComponentMetamodelImpl metamodel = new ComponentMetamodelImpl();
		ComponentMetamodelImpl composedMetamodel = new ComponentMetamodelImpl();
		ComponentItemMetamodelImpl itemMetamodel = new ComponentItemMetamodelImpl();
		EventsMetamodel<EventA> eventsMetamodel = new EventsMetamodelImpl<>(EventA.class);
		Field itemField = FieldHolder.class.getDeclaredField("item");
		Field composedField = FieldHolder.class.getDeclaredField("component");

		metamodel.setComponentType(ComponentType.class);
		metamodel.setImplementation(ComponentImplementation.class);
		composedMetamodel.setComponentType(ComposedType.class);
		composedMetamodel.setImplementation(ComposedImplementation.class);
		itemMetamodel.setImplementation(ItemImplementation.class);

		metamodel.setItemTypes(Collections.singletonList(ItemImplementation.class));
		metamodel.setComposedTypes(Collections.singletonList(ComposedType.class));
		metamodel.setItemMetamodels(Collections.singletonList(itemMetamodel));
		metamodel.setComposedMetamodels(Collections.singletonList(composedMetamodel));
		metamodel.addItemField(ItemImplementation.class, itemField);
		metamodel.addComposedComponentField(ComposedType.class, composedField);
		metamodel.addEventsMetamodel(eventsMetamodel);

		Assert.assertEquals(ComponentType.class, metamodel.getComponentType());
		Assert.assertEquals(Collections.singletonList(ItemImplementation.class), metamodel.getItemTypes());
		Assert.assertEquals(Collections.singletonList(ComposedType.class), metamodel.getComposedTypes());
		Assert.assertEquals(Collections.singletonList(itemMetamodel), metamodel.getItemMetamodels());
		Assert.assertEquals(Collections.singletonList(composedMetamodel), metamodel.getComposedMetamodels());
		Assert.assertEquals(Collections.singletonList(itemField), metamodel.getItemsFields());
		Assert.assertSame(itemField, metamodel.getItemField(ItemImplementation.class));
		Assert.assertEquals(Collections.singletonList(composedField), metamodel.getComposedComponentsFields());
		Assert.assertSame(composedField, metamodel.getComposedComponentField(ComposedType.class));
		Assert.assertSame(eventsMetamodel, metamodel.getEventsMetamodel(EventA.class));

		String text = metamodel.toString();
		Assert.assertTrue(text.contains("Items"));
		Assert.assertTrue(text.contains("Composed components"));
		Assert.assertTrue(text.contains("ComposedImplementation"));
	}

	@Test
	public void componentMetamodelReturnsNestedStructureCopies() {
		ComponentMetamodelImpl metamodel = new ComponentMetamodelImpl();
		ComponentItemMetamodelImpl itemMetamodel = new ComponentItemMetamodelImpl();
		ComponentMetamodelImpl composedMetamodel = new ComponentMetamodelImpl();
		metamodel.setItemTypes(Collections.singletonList(ItemImplementation.class));
		metamodel.setComposedTypes(Collections.singletonList(ComposedType.class));
		metamodel.setItemMetamodels(Collections.singletonList(itemMetamodel));
		metamodel.setComposedMetamodels(Collections.singletonList(composedMetamodel));

		List<Class<?>> itemTypes = metamodel.getItemTypes();
		List<Class<?>> composedTypes = metamodel.getComposedTypes();
		List<?> itemMetamodels = metamodel.getItemMetamodels();
		List<?> composedMetamodels = metamodel.getComposedMetamodels();
		itemTypes.add(OtherItemImplementation.class);
		composedTypes.add(OtherComposedType.class);
		itemMetamodels.clear();
		composedMetamodels.clear();

		Assert.assertEquals(Collections.singletonList(ItemImplementation.class), metamodel.getItemTypes());
		Assert.assertEquals(Collections.singletonList(ComposedType.class), metamodel.getComposedTypes());
		Assert.assertEquals(Collections.singletonList(itemMetamodel), metamodel.getItemMetamodels());
		Assert.assertEquals(Collections.singletonList(composedMetamodel), metamodel.getComposedMetamodels());
	}

	interface ComponentType {
	}

	static class ComponentImplementation implements ComponentType {
	}

	interface ComposedType {
	}

	interface OtherComposedType {
	}

	static class ComposedImplementation implements ComposedType {
	}

	static class ItemImplementation {
	}

	static class OtherItemImplementation {
	}

	interface ServiceA {
	}

	interface ServiceB {
	}

	interface ServiceC {
	}

	interface ServiceD {
	}

	interface EventA {
	}

	interface EventB {
	}

	interface EventC {
	}

	interface EventD {
	}

	interface SubscribeA {
	}

	interface SubscribeB {
	}

	interface SubscribeC {
	}

	interface SubscribeD {
	}

	interface ParametersA {
	}

	interface ParametersB {
	}

	interface ParametersC {
	}

	interface ParametersD {
	}

	static class FieldHolder {
		private ServiceB services;
		private EventA events;
		private SubscribeC subscriber;
		private ParametersB parameters;
		private ItemImplementation item;
		private ComposedType component;
	}
}
