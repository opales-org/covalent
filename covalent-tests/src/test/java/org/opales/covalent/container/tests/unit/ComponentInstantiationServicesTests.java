/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.metamodel.MetamodelReadServices;
import org.opales.covalent.container.metamodel.metamodel.ReadOnlyComponentMetamodel;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.registry.ComponentInstantiationServices;
import org.opales.covalent.container.registry.DescriptorRegistryReadServices;
import org.opales.covalent.container.registry.DescriptorRegistryWriteServices;
import org.opales.covalent.container.registry.ComponentDescriptor;
import org.opales.covalent.container.registry.RegistryReadServices;

public class ComponentInstantiationServicesTests {

	private CovalentContainer container;
	private ComponentInstantiationServices componentInstantiationServices;
	private DescriptorRegistryReadServices descriptorRegistryReadServices;
	private DescriptorRegistryWriteServices descriptorRegistryWriteServices;
	private LifecycleServices lifecycleServices;
	private MetamodelReadServices metamodelReadServices;
	private RegistryReadServices registryReadServices;

	@Before
	public void setUp() {
		this.container = Covalent.container();
		RawComponentsServices rawComponentsServices = this.container.rcs();
		rawComponentsServices.registerComponent(this);
		this.componentInstantiationServices = rawComponentsServices.useServices(ComponentInstantiationServices.class);
		this.descriptorRegistryReadServices = rawComponentsServices.useServices(DescriptorRegistryReadServices.class);
		this.descriptorRegistryWriteServices = rawComponentsServices.useServices(DescriptorRegistryWriteServices.class);
		this.lifecycleServices = rawComponentsServices.useServices(LifecycleServices.class);
		this.metamodelReadServices = rawComponentsServices.useServices(MetamodelReadServices.class);
		this.registryReadServices = rawComponentsServices.useServices(RegistryReadServices.class);
		ManagedFixtureImpl.constructions = 0;
		CompositeFixtureImpl.constructions = 0;
		ComposedFixtureImpl.constructions = 0;
	}

	// No tearDown(): these tests never call start() on their fresh container
	// (exercising the pre-first-start bootstrap window), and stop()
	// requires having been started first; the throwaway container is simply
	// discarded after each test.

	@Test
	public void instantiateComponentCreatesAndRegistersOneInstance() {
		this.container.deployComponents(ManagedFixtureImpl.class);

		this.componentInstantiationServices.instantiateComponent(ManagedFixture.class);

		Assert.assertEquals(1, ManagedFixtureImpl.constructions);
		Assert.assertTrue(this.registryReadServices.isRegistered(ManagedFixture.class));
		Assert.assertNotNull(this.descriptorRegistryReadServices.readComponentDescriptor(ManagedFixture.class));

		this.componentInstantiationServices.removeComponent(ManagedFixture.class);
	}

		@Test
	public void deployingANullImplementationIsRefusedAsAnIllegalArgument() {
		Assert.assertThrows(IllegalArgumentException.class, () -> this.lifecycleServices.deployComponent(null));
	}

	@Test
	public void instantiatingANullComponentTypeIsRefusedAsAnIllegalArgument() {
		Assert.assertThrows(IllegalArgumentException.class, () -> this.lifecycleServices.instantiateComponent(null));
	}

	@Test
	public void componentifyRegistersTheSuppliedInstance() {
		this.container.deployComponents(ManagedFixtureImpl.class);
		ManagedFixtureImpl suppliedInstance = new ManagedFixtureImpl();

		this.componentInstantiationServices.componentify(ManagedFixture.class, suppliedInstance);

		Assert.assertEquals(1, ManagedFixtureImpl.constructions);
		Assert.assertSame(suppliedInstance, this.registryReadServices.getComponentInstance(ManagedFixture.class));
		Assert.assertNotNull(this.descriptorRegistryReadServices.readComponentDescriptor(ManagedFixture.class));

		this.componentInstantiationServices.removeComponent(ManagedFixture.class);
	}

	@Test
	public void removeComponentRemovesInstanceAndDescriptor() {
		this.container.deployComponents(ManagedFixtureImpl.class);
		this.componentInstantiationServices.instantiateComponent(ManagedFixture.class);

		this.componentInstantiationServices.removeComponent(ManagedFixture.class);

		Assert.assertFalse(this.registryReadServices.isRegistered(ManagedFixture.class));
		Assert.assertFalse(this.descriptorRegistryReadServices.containsComponentDescriptor(ManagedFixture.class));
	}

	@Test
	public void metamodelRecordsComposedComponentParent() {
		this.container.deployComponents(CompositeFixtureImpl.class);

		ReadOnlyComponentMetamodel childMetamodel = this.metamodelReadServices.readMetamodel(ComposedFixture.class);

		Assert.assertSame(CompositeFixture.class, childMetamodel.getParentComponentType());
		Assert.assertSame(this.metamodelReadServices.readMetamodel(CompositeFixture.class),
				childMetamodel.getParentMetamodel());
	}

	@Test
	public void lifecycleInstantiatesCompositeTreeWithParentDescriptor() {
		this.container.deployComponents(CompositeFixtureImpl.class);

		this.lifecycleServices.instantiateComponent(CompositeFixture.class);

		Assert.assertEquals(1, CompositeFixtureImpl.constructions);
		Assert.assertEquals(1, ComposedFixtureImpl.constructions);
		Assert.assertTrue(this.registryReadServices.isRegistered(CompositeFixture.class));
		Assert.assertTrue(this.registryReadServices.isRegistered(ComposedFixture.class));

		ComponentDescriptor parentDescriptor = this.descriptorRegistryReadServices
				.readComponentDescriptor(CompositeFixture.class);
		ComponentDescriptor childDescriptor = this.descriptorRegistryReadServices
				.readComponentDescriptor(ComposedFixture.class);

		Assert.assertSame(parentDescriptor, childDescriptor.getParent());
		Assert.assertTrue(parentDescriptor.getComponents().contains(childDescriptor));
	}

	@Test
	public void removeComponentDetachesDescriptorFromParent() {
		this.container.deployComponents(CompositeFixtureImpl.class);
		this.lifecycleServices.instantiateComponent(CompositeFixture.class);

		ComponentDescriptor parentDescriptor = this.descriptorRegistryReadServices
				.readComponentDescriptor(CompositeFixture.class);
		ComponentDescriptor childDescriptor = this.descriptorRegistryReadServices
				.readComponentDescriptor(ComposedFixture.class);

		this.descriptorRegistryWriteServices.deleteComponentDescriptor(ComposedFixture.class);

		Assert.assertFalse(parentDescriptor.getComponents().contains(childDescriptor));
		Assert.assertFalse(this.descriptorRegistryReadServices.containsComponentDescriptor(ComposedFixture.class));
	}

	@Test
	public void descriptorComponentListIsADefensiveCopy() {
		this.container.deployComponents(CompositeFixtureImpl.class);
		this.lifecycleServices.instantiateComponent(CompositeFixture.class);

		ComponentDescriptor parentDescriptor = this.descriptorRegistryReadServices
				.readComponentDescriptor(CompositeFixture.class);
		List<ComponentDescriptor> components = parentDescriptor.getComponents();

		components.clear();

		Assert.assertEquals(1, parentDescriptor.getComponents().size());
	}

	@Test
	public void descriptorItemListIsADefensiveCopy() {
		this.container.deployComponents(CompositeFixtureImpl.class);
		this.lifecycleServices.instantiateComponent(CompositeFixture.class);

		ComponentDescriptor parentDescriptor = this.descriptorRegistryReadServices
				.readComponentDescriptor(CompositeFixture.class);
		List<?> items = parentDescriptor.getItems();

		items.clear();

		Assert.assertEquals(1, parentDescriptor.getItems().size());
	}

	@Test
	public void registryInstancesAreADefensiveCopy() {
		this.container.deployComponents(ManagedFixtureImpl.class);
		this.componentInstantiationServices.instantiateComponent(ManagedFixture.class);

		Map<Class<?>, Object> instances = this.registryReadServices.getInstances();

		instances.clear();

		Assert.assertTrue(this.registryReadServices.isRegistered(ManagedFixture.class));
		this.componentInstantiationServices.removeComponent(ManagedFixture.class);
	}

	@Component
	public interface ManagedFixture {
	}

	@Implementation
	public static class ManagedFixtureImpl implements ManagedFixture {
		private static int constructions;

		public ManagedFixtureImpl() {
			constructions++;
		}
	}

	@Component
	public interface CompositeFixture {
	}

	@Component
	public interface ComposedFixture {
	}

	@Implementation
	public static class CompositeFixtureImpl implements CompositeFixture {
		private static int constructions;

		@Item
		private CompositeItem item;

		@Implementation
		private ComposedFixtureImpl composedFixture;

		public CompositeFixtureImpl() {
			constructions++;
		}
	}

	@Implementation
	public static class ComposedFixtureImpl implements ComposedFixture {
		private static int constructions;

		public ComposedFixtureImpl() {
			constructions++;
		}
	}

	@Item
	public static class CompositeItem {
	}
}
