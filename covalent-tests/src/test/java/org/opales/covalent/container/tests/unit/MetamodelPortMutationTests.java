/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.ComposedComponent;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsSubscribe;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.Parameters;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.exceptions.ValidationException;
import org.opales.covalent.container.metamodel.MetamodelPortReadServices;
import org.opales.covalent.container.metamodel.MetamodelPortWriteServices;
import org.opales.covalent.container.metamodel.MetamodelWriteServices;
import org.opales.covalent.container.metamodel.impl.MetamodelManagerImpl;
import org.opales.covalent.container.metamodel.metamodel.ComponentItemMetamodel;
import org.opales.covalent.container.metamodel.metamodel.ComponentMetamodel;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.impl.FullyConnectedImpl;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;
import org.opales.covalent.container.validation.impl.ComponentValidatorImpl;

/**
 * Verifies the port-ownership bookkeeping of the metamodel manager through
 * {@link MetamodelPortWriteServices}: adding and removing provided services,
 * offered parameters, produced events and consumed events updates the
 * component's own metamodel entry, its item entries and the global
 * port-to-owner index; a port already owned by another component is rejected
 * (strict level) or only reported (warn level) without changing ownership;
 * removing a port on behalf of a component that does not own it is refused.
 * Also verifies composed-children reordering, the human-readable rendering of
 * a metamodel, and the count and factory accessors of a stand-alone manager.
 */
public class MetamodelPortMutationTests {

	private CapturingLogHandler componentValidatorLogs;

	@Services
	public interface RichServices {
	}

	@Services
	public interface RichItemServices {
	}

	@Parameters
	public interface RichParameters {
	}

	@Parameters
	public interface RichItemParameters {
	}

	@Events
	public interface RichComponentConsumedEvents {
		void onComponentConsumed();
	}

	@Events
	public interface RichItemConsumedEvents {
		void onItemConsumed();
	}

	@Component
	public interface ChildA {
	}

	@Component
	public interface ChildB {
	}

	@Implementation
	public static class ChildAImpl implements ChildA {
	}

	@Implementation
	public static class ChildBImpl implements ChildB {
	}

	@Component
	public interface Rich {
	}

	@Implementation
	public static class RichImpl implements Rich, RichServices, RichParameters, RichComponentConsumedEvents {
		@Item
		private RichItem item;

		@ComposedComponent
		private ChildAImpl childA;

		@ComposedComponent
		private ChildBImpl childB;

		@Override
		public void onComponentConsumed() {
		}
	}

	@Item
	public static class RichItem implements RichItemServices, RichItemParameters, RichItemConsumedEvents {
		@Override
		public void onItemConsumed() {
		}
	}

	@Services
	public interface SharedServices {
	}

	@Parameters
	public interface SharedParameters {
	}

	@Events
	public interface SharedEvents {
		void onShared();
	}

	@Component
	public interface ItemProducer {
	}

	@Implementation
	public static class ItemProducerImpl implements ItemProducer {
		@Item
		private ItemProducerItem item;
	}

	@Item
	public static class ItemProducerItem {
		@ProducedEvents
		private ItemProducedEvents produceItemProducedEvents;
	}

	@Events
	public interface ItemProducedEvents {
		void onItemProduced();

		@EventsSubscribe
		interface ItemProducedEventsSubscribe {
			void subscribe(ItemProducedEvents observer);
		}
	}

	public static class OwnerA {
	}

	public static class OwnerB {
	}

	private CovalentContainer container;

	private RawComponentsServices rcs;

	private MetamodelPortWriteServices portWrite;

	private MetamodelPortReadServices portRead;

	private MetamodelWriteServices metamodelWrite;

	@Before
	public void setUp() {
		this.componentValidatorLogs = CapturingLogHandler.install(ComponentValidatorImpl.class);
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		this.rcs = this.container.rcs();
		this.rcs.registerComponent(this);
		this.portWrite = this.rcs.useServices(MetamodelPortWriteServices.class);
		this.portRead = this.rcs.useServices(MetamodelPortReadServices.class);
		this.metamodelWrite = this.rcs.useServices(MetamodelWriteServices.class);
	}

	@After
	public void restoreTheLogs() {
		this.componentValidatorLogs.restore();
	}

	private void registerOwners() {
		this.rcs.registerComponent(new OwnerA());
		this.rcs.registerComponent(new OwnerB());
	}

	@Test
	public void removingProvidedServicesClearsComponentItemAndGlobalIndex() {
		this.container.deployComponents(RichImpl.class);
		ComponentMetamodel metamodel = this.metamodelWrite.readMetamodel(Rich.class);
		ComponentItemMetamodel itemMetamodel = metamodel.getItemMetamodels().get(0);
		Assert.assertTrue(metamodel.getProvidedServices().contains(RichServices.class));
		Assert.assertTrue(itemMetamodel.getProvidedServices().contains(RichItemServices.class));
		Assert.assertEquals(Rich.class, this.portRead.getComponentTypeForProvidedServices(RichServices.class));

		this.portWrite.removeProvidedServices(Rich.class, RichServices.class);
		this.portWrite.removeProvidedServices(Rich.class, RichItemServices.class);

		Assert.assertFalse(metamodel.getProvidedServices().contains(RichServices.class));
		Assert.assertFalse(itemMetamodel.getProvidedServices().contains(RichItemServices.class));
		Assert.assertNull(this.portRead.getComponentTypeForProvidedServices(RichServices.class));
		Assert.assertNull(this.portRead.getComponentTypeForProvidedServices(RichItemServices.class));
	}

	@Test
	public void removingOfferedParametersClearsComponentItemAndGlobalIndex() {
		this.container.deployComponents(RichImpl.class);
		ComponentMetamodel metamodel = this.metamodelWrite.readMetamodel(Rich.class);
		ComponentItemMetamodel itemMetamodel = metamodel.getItemMetamodels().get(0);
		Assert.assertTrue(metamodel.getOfferedParameters().contains(RichParameters.class));
		Assert.assertTrue(itemMetamodel.getOfferedParameters().contains(RichItemParameters.class));

		this.portWrite.removeOfferedParameters(Rich.class, RichParameters.class);
		this.portWrite.removeOfferedParameters(Rich.class, RichItemParameters.class);

		Assert.assertFalse(metamodel.getOfferedParameters().contains(RichParameters.class));
		Assert.assertFalse(itemMetamodel.getOfferedParameters().contains(RichItemParameters.class));
		Assert.assertNull(this.portRead.getComponentTypeForOfferedParameters(RichParameters.class));
		Assert.assertNull(this.portRead.getComponentTypeForOfferedParameters(RichItemParameters.class));
	}

	@Test
	public void removingConsumedEventsClearsComponentAndItemEntries() {
		this.container.deployComponents(RichImpl.class);
		ComponentMetamodel metamodel = this.metamodelWrite.readMetamodel(Rich.class);
		ComponentItemMetamodel itemMetamodel = metamodel.getItemMetamodels().get(0);
		Assert.assertTrue(metamodel.getConsumedEvents().contains(RichComponentConsumedEvents.class));
		Assert.assertTrue(itemMetamodel.getConsumedEvents().contains(RichItemConsumedEvents.class));

		this.portWrite.removeConsumedEvents(Rich.class, RichComponentConsumedEvents.class);
		this.portWrite.removeConsumedEvents(Rich.class, RichItemConsumedEvents.class);

		Assert.assertFalse(metamodel.getConsumedEvents().contains(RichComponentConsumedEvents.class));
		Assert.assertFalse(itemMetamodel.getConsumedEvents().contains(RichItemConsumedEvents.class));
		Assert.assertFalse(this.portRead.getConsumedEvents(Rich.class).contains(RichComponentConsumedEvents.class));
		Assert.assertFalse(this.portRead.getConsumedEvents(Rich.class).contains(RichItemConsumedEvents.class));
	}

	@Test
	public void addedConsumedEventsAreVisibleThroughReadServices() {
		this.registerOwners();

		this.portWrite.addConsumedEvents(OwnerA.class, SharedEvents.class);
		this.portWrite.addConsumedEvents(OwnerA.class, SharedEvents.class);

		Assert.assertTrue(this.portRead.getConsumedEvents(OwnerA.class).contains(SharedEvents.class));
		Assert.assertEquals(1, this.metamodelWrite.readMetamodel(OwnerA.class).getConsumedEvents().size());
		this.portWrite.removeConsumedEvents(OwnerA.class, SharedEvents.class);
		Assert.assertFalse(this.portRead.getConsumedEvents(OwnerA.class).contains(SharedEvents.class));
	}

	@Test
	public void addedUsedServicesAreVisibleThroughReadServices() {
		this.registerOwners();

		this.portWrite.addUsedServices(OwnerA.class, SharedServices.class);
		this.portWrite.addUsedServices(OwnerA.class, SharedServices.class);

		Assert.assertTrue(this.portRead.getUsedServices(OwnerA.class).contains(SharedServices.class));
		Assert.assertEquals(1, this.metamodelWrite.readMetamodel(OwnerA.class).getUsedServices().size());
		this.portWrite.removeUsedServices(OwnerA.class, SharedServices.class);
		Assert.assertFalse(this.portRead.getUsedServices(OwnerA.class).contains(SharedServices.class));
	}

	@Test
	public void addedAccessedParametersAreVisibleThroughReadServices() {
		this.registerOwners();

		this.portWrite.addAccessedParameters(OwnerA.class, SharedParameters.class);
		this.portWrite.addAccessedParameters(OwnerA.class, SharedParameters.class);

		Assert.assertTrue(this.portRead.getAccessedParameters(OwnerA.class).contains(SharedParameters.class));
		Assert.assertEquals(1, this.metamodelWrite.readMetamodel(OwnerA.class).getAccessedParameters().size());
		this.portWrite.removeAccessedParameters(OwnerA.class, SharedParameters.class);
		Assert.assertFalse(this.portRead.getAccessedParameters(OwnerA.class).contains(SharedParameters.class));
	}

	@Test
	public void addedPortsAreOwnedByTheirComponentUntilRemoved() {
		this.registerOwners();

		this.portWrite.addProvidedServices(OwnerA.class, SharedServices.class);
		this.portWrite.addOfferedParameters(OwnerA.class, SharedParameters.class);
		this.portWrite.addProducedEvents(OwnerA.class, SharedEvents.class);

		Assert.assertEquals(OwnerA.class, this.portRead.getComponentTypeForProvidedServices(SharedServices.class));
		Assert.assertEquals(OwnerA.class, this.portRead.getComponentTypeForOfferedParameters(SharedParameters.class));
		Assert.assertEquals(OwnerA.class, this.portRead.getComponentTypeForProducedEvents(SharedEvents.class));

		this.portWrite.removeProvidedServices(OwnerA.class, SharedServices.class);
		this.portWrite.removeOfferedParameters(OwnerA.class, SharedParameters.class);
		this.portWrite.removeProducedEvents(OwnerA.class, SharedEvents.class);

		Assert.assertNull(this.portRead.getComponentTypeForProvidedServices(SharedServices.class));
		Assert.assertNull(this.portRead.getComponentTypeForOfferedParameters(SharedParameters.class));
		Assert.assertNull(this.portRead.getComponentTypeForProducedEvents(SharedEvents.class));
	}

	@Test
	public void portAlreadyOwnedByAnotherComponentIsRejectedUnderStrictLevel() {
		this.registerOwners();
		this.container.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		this.portWrite.addProvidedServices(OwnerA.class, SharedServices.class);
		this.portWrite.addOfferedParameters(OwnerA.class, SharedParameters.class);
		this.portWrite.addProducedEvents(OwnerA.class, SharedEvents.class);

		ValidationException services = Assert.assertThrows(ValidationException.class,
				() -> this.portWrite.addProvidedServices(OwnerB.class, SharedServices.class));
		ValidationException parameters = Assert.assertThrows(ValidationException.class,
				() -> this.portWrite.addOfferedParameters(OwnerB.class, SharedParameters.class));
		ValidationException events = Assert.assertThrows(ValidationException.class,
				() -> this.portWrite.addProducedEvents(OwnerB.class, SharedEvents.class));

		Assert.assertTrue(services.getMessage().contains("SharedServices"));
		Assert.assertTrue(parameters.getMessage().contains("SharedParameters"));
		Assert.assertTrue(events.getMessage().contains("SharedEvents"));
		Assert.assertEquals(OwnerA.class, this.portRead.getComponentTypeForProvidedServices(SharedServices.class));
		Assert.assertEquals(OwnerA.class, this.portRead.getComponentTypeForOfferedParameters(SharedParameters.class));
		Assert.assertEquals(OwnerA.class, this.portRead.getComponentTypeForProducedEvents(SharedEvents.class));
	}

	@Test
	public void portAlreadyOwnedByAnotherComponentKeepsItsOwnerUnderWarnLevel() {
		this.registerOwners();
		this.container.getParameters().setDynamicRulesChecking(VerificationLevel.warn);
		this.portWrite.addProvidedServices(OwnerA.class, SharedServices.class);
		this.portWrite.addOfferedParameters(OwnerA.class, SharedParameters.class);
		this.portWrite.addProducedEvents(OwnerA.class, SharedEvents.class);

		this.portWrite.addProvidedServices(OwnerB.class, SharedServices.class);
		this.portWrite.addOfferedParameters(OwnerB.class, SharedParameters.class);
		this.portWrite.addProducedEvents(OwnerB.class, SharedEvents.class);

		Assert.assertEquals(OwnerA.class, this.portRead.getComponentTypeForProvidedServices(SharedServices.class));
		Assert.assertEquals(OwnerA.class, this.portRead.getComponentTypeForOfferedParameters(SharedParameters.class));
		Assert.assertEquals(OwnerA.class, this.portRead.getComponentTypeForProducedEvents(SharedEvents.class));
		Assert.assertFalse(this.portRead.getProvidedServices(OwnerB.class).contains(SharedServices.class));
		Assert.assertFalse(this.portRead.getOfferedParameters(OwnerB.class).contains(SharedParameters.class));
		Assert.assertFalse(this.portRead.getProducedEvents(OwnerB.class).contains(SharedEvents.class));
		List<String> warnings = this.componentValidatorLogs.messages(Level.WARNING);
		Assert.assertEquals(3, warnings.size());
		Assert.assertTrue(warnings.get(0), warnings.get(0)
				.startsWith("Services SharedServices is already provided by component OwnerA; OwnerB cannot also provide it."));
		Assert.assertTrue(warnings.get(1), warnings.get(1)
				.startsWith("Parameters SharedParameters is already offered by component OwnerA; OwnerB cannot also offer it."));
		Assert.assertTrue(warnings.get(2), warnings.get(2)
				.startsWith("Events SharedEvents is already produced by component OwnerA; OwnerB cannot also produce it."));
	}

	@Test
	public void removingAPortOnBehalfOfANonOwnerIsRefused() {
		this.registerOwners();
		this.portWrite.addProvidedServices(OwnerA.class, SharedServices.class);
		this.portWrite.addOfferedParameters(OwnerA.class, SharedParameters.class);
		this.portWrite.addProducedEvents(OwnerA.class, SharedEvents.class);

		Assert.assertThrows(IllegalArgumentException.class,
				() -> this.portWrite.removeProvidedServices(OwnerB.class, SharedServices.class));
		Assert.assertThrows(IllegalArgumentException.class,
				() -> this.portWrite.removeOfferedParameters(OwnerB.class, SharedParameters.class));
		Assert.assertThrows(IllegalArgumentException.class,
				() -> this.portWrite.removeProducedEvents(OwnerB.class, SharedEvents.class));

		Assert.assertEquals(OwnerA.class, this.portRead.getComponentTypeForProvidedServices(SharedServices.class));
		Assert.assertEquals(OwnerA.class, this.portRead.getComponentTypeForOfferedParameters(SharedParameters.class));
		Assert.assertEquals(OwnerA.class, this.portRead.getComponentTypeForProducedEvents(SharedEvents.class));
	}

		@Test
	public void removingAPortNobodyOwnsIsRefusedWithAClearMessage() {
		this.registerOwners();

		IllegalArgumentException services = Assert.assertThrows(IllegalArgumentException.class,
				() -> this.portWrite.removeProvidedServices(OwnerA.class, SharedServices.class));
		Assert.assertTrue(services.getMessage(), services.getMessage().contains("no component provides it"));
		IllegalArgumentException parameters = Assert.assertThrows(IllegalArgumentException.class,
				() -> this.portWrite.removeOfferedParameters(OwnerA.class, SharedParameters.class));
		Assert.assertTrue(parameters.getMessage(), parameters.getMessage().contains("no component offers it"));
		IllegalArgumentException events = Assert.assertThrows(IllegalArgumentException.class,
				() -> this.portWrite.removeProducedEvents(OwnerA.class, SharedEvents.class));
		Assert.assertTrue(events.getMessage(), events.getMessage().contains("no component produces it"));
	}

		@Test
	public void componentMetamodelResolvesSubscriberOfAnItemProducedEvents() {
		this.container.deployComponents(ItemProducerImpl.class);
		ComponentMetamodel metamodel = this.metamodelWrite.readMetamodel(ItemProducer.class);

		Assert.assertEquals(ItemProducedEvents.ItemProducedEventsSubscribe.class,
				metamodel.getProvidedSubscriberForProducedEvents(ItemProducedEvents.class));
		Assert.assertEquals(ItemProducedEvents.class, metamodel
				.getProducedEventsForProvidedSubscriber(ItemProducedEvents.ItemProducedEventsSubscribe.class));
	}

	@Test
	public void removingEventsProducedByAnItemClearsTheItemEntryAndItCanBeAddedBack() {
		this.container.deployComponents(ItemProducerImpl.class);
		ComponentMetamodel metamodel = this.metamodelWrite.readMetamodel(ItemProducer.class);
		ComponentItemMetamodel itemMetamodel = metamodel.getItemMetamodels().get(0);
		Assert.assertTrue(itemMetamodel.getProducedEvents().contains(ItemProducedEvents.class));

		this.portWrite.removeProducedEvents(ItemProducer.class, ItemProducedEvents.class);

		Assert.assertFalse(itemMetamodel.getProducedEvents().contains(ItemProducedEvents.class));
		Assert.assertNull(this.portRead.getComponentTypeForProducedEvents(ItemProducedEvents.class));

		this.portWrite.addProducedEvents(ItemProducer.class, ItemProducedEvents.class);

		Assert.assertEquals(ItemProducer.class,
				this.portRead.getComponentTypeForProducedEvents(ItemProducedEvents.class));
		Assert.assertEquals(ItemProducedEvents.ItemProducedEventsSubscribe.class,
				this.portRead.getSubscribeInterface(ItemProducedEvents.class));
	}

	@Test
	public void reorderComposedChildrenAppliesTheGivenPermutation() {
		this.container.deployComponents(RichImpl.class);
		ComponentMetamodel metamodel = this.metamodelWrite.readMetamodel(Rich.class);
		List<Class<?>> original = new ArrayList<>(metamodel.getComposedTypes());
		Assert.assertEquals(2, original.size());
		List<Class<?>> reversed = new ArrayList<>(original);
		Collections.reverse(reversed);

		this.metamodelWrite.reorderComposedChildren(Rich.class, reversed);

		Assert.assertEquals(reversed, metamodel.getComposedTypes());
		List<Class<?>> implementations = new ArrayList<>();
		for (ComponentMetamodel child : metamodel.getComposedMetamodels()) {
			implementations.add(child.getImplementation());
		}
		Assert.assertEquals(reversed, implementations);
	}

	@Test
	public void reorderComposedChildrenRejectsAnOrderThatIsNotAPermutation() {
		this.container.deployComponents(RichImpl.class);
		ComponentMetamodel metamodel = this.metamodelWrite.readMetamodel(Rich.class);
		List<Class<?>> original = new ArrayList<>(metamodel.getComposedTypes());

		Assert.assertThrows(IllegalArgumentException.class,
				() -> this.metamodelWrite.reorderComposedChildren(Rich.class, Arrays.asList(ChildAImpl.class)));
		Assert.assertThrows(IllegalArgumentException.class, () -> this.metamodelWrite
				.reorderComposedChildren(Rich.class, Arrays.asList(ChildAImpl.class, ChildAImpl.class)));
		Assert.assertThrows(IllegalArgumentException.class,
				() -> this.metamodelWrite.reorderComposedChildren(OwnerA.class, original));

		Assert.assertEquals(original, metamodel.getComposedTypes());
	}

	@Test
	public void metamodelRenderingListsItsPortsItemsAndComposedComponents() {
		this.container.deployComponents(RichImpl.class, FullyConnectedImpl.class);

		String rich = this.metamodelWrite.readMetamodel(Rich.class).toString();
		Assert.assertTrue(rich, rich.contains("RichImpl"));
		Assert.assertTrue(rich, rich.contains("> Items:"));
		Assert.assertTrue(rich, rich.contains("- RichItem"));
		Assert.assertTrue(rich, rich.contains("> Provided services:"));
		Assert.assertTrue(rich, rich.contains("- RichServices"));
		Assert.assertTrue(rich, rich.contains("> Composed components:"));
		Assert.assertTrue(rich, rich.contains("- ChildAImpl"));
		Assert.assertTrue(rich, rich.contains("- ChildBImpl"));

		String connected = this.metamodelWrite
				.readMetamodel(org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.FullyConnected.class)
				.toString();
		Assert.assertTrue(connected, connected.contains("> Used services:"));
		Assert.assertTrue(connected, connected.contains("- ProducerServices"));
		Assert.assertTrue(connected, connected.contains("> Produced events:"));
		Assert.assertTrue(connected, connected.contains("- FullyConnectedEvents"));
		Assert.assertTrue(connected, connected.contains("> Provided events subscribes:"));
		Assert.assertTrue(connected, connected.contains("- FullyConnectedEventsSubscribe"));
		Assert.assertTrue(connected, connected.contains("> Used events subscribes:"));
		Assert.assertTrue(connected, connected.contains("- ProducerEventsSubscribe"));
	}

	@Test
	public void standaloneManagerExposesItsFactoryAndStartsEmpty() {
		MetamodelManagerImpl manager = new MetamodelManagerImpl();

		Assert.assertNotNull(manager.getMetamodelFactory());
		Assert.assertEquals(0, manager.getCount());
		Assert.assertNull(manager.readMetamodel(OwnerA.class));
	}
}
