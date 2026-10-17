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
import org.opales.covalent.container.tests.CovalentContainerizedTests;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.metamodel.MetamodelReadServices;
import org.opales.covalent.container.metamodel.metamodel.ReadOnlyComponentMetamodel;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.registry.PortRegistryServices;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.registry.ComponentDescriptor;
import org.opales.covalent.container.tests.unit.composite.Composite;
import org.opales.covalent.container.tests.unit.composite.composeda.suba1.SubA1Services;
import org.opales.covalent.container.tests.unit.composite.composedb.ComposedB;
import org.opales.covalent.container.tests.unit.composite.composedb.subb1.SubB1Services;
import org.opales.covalent.container.tests.unit.composite.composedc.ComposedC;
import org.opales.covalent.container.tests.unit.composite.composedc.impl.ComposedCImpl;
import org.opales.covalent.container.tests.unit.composite.composedc.subc1.SubC1Services;
import org.opales.covalent.container.tests.unit.composite.impl.CompositeImpl;

public class CompositionContainerizedTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	private MetamodelReadServices metamodelReadServices;

	private RegistryReadServices registryReadServices;

	private PortRegistryServices portRegistryServices;

	private LifecycleServices lifecycleServices;

	@Before
	public void setup() {
		this.rcs.registerComponent(this);
		this.cm.getParameters().setSynchronousEvents(true);

		// Looked up here, not as field initializers: the base class's own @Before
		// (which runs first) is what starts the container, and these raw lookups
		// need it already running.
		this.metamodelReadServices = this.rcs.useServices(MetamodelReadServices.class);
		this.registryReadServices = this.rcs.useServices(RegistryReadServices.class);
		this.portRegistryServices = this.rcs.useServices(PortRegistryServices.class);
		this.lifecycleServices = this.rcs.useServices(LifecycleServices.class);
	}

	@After
	public void tearDown() {
		this.cm.getParameters().setSynchronousEvents(false);

	}

	@Test
	public void deploymentTest() {
		this.cm.deployComponents(CompositeImpl.class);
		ReadOnlyComponentMetamodel compositeMetamodel = this.metamodelReadServices.readMetamodel(Composite.class);

		Assert.assertEquals(3, compositeMetamodel.getComposedTypes().size());
		ReadOnlyComponentMetamodel compositedA = compositeMetamodel.getComposedMetamodels().get(0);
		Assert.assertNotNull(compositedA);
		Assert.assertEquals(2, compositedA.getComposedTypes().size());
	}

	@Test
	public void instantiationTest() {
		this.cm.deployComponents(CompositeImpl.class);
		this.lifecycleServices.instantiateComponent(Composite.class);

		ComponentDescriptor compositeDescriptor = this.portRegistryServices.getComponentDescriptor(Composite.class);
		Assert.assertEquals(3, compositeDescriptor.getComponents().size());

		ComponentDescriptor composedADescriptor = compositeDescriptor.getComponents().get(0);
		Assert.assertEquals(2, composedADescriptor.getComponents().size());

		ComponentDescriptor composedBDescriptor = compositeDescriptor.getComponents().get(1);
		Assert.assertEquals(1, composedBDescriptor.getComponents().size());

		ComponentDescriptor composedCDescriptor = compositeDescriptor.getComponents().get(2);
		Assert.assertEquals(3, composedCDescriptor.getComponents().size());

	}

	@Test
	public void activationTest() {
		this.cm.deployComponents(CompositeImpl.class);
		this.lifecycleServices.activateComponent(Composite.class);

		ComponentDescriptor compositeDescriptor = this.portRegistryServices.getComponentDescriptor(Composite.class);
		Assert.assertEquals(3, compositeDescriptor.getComponents().size());

		ComponentDescriptor composedADescriptor = compositeDescriptor.getComponents().get(0);
		Assert.assertEquals(2, composedADescriptor.getComponents().size());

		ComponentDescriptor composedBDescriptor = compositeDescriptor.getComponents().get(1);
		Assert.assertEquals(1, composedBDescriptor.getComponents().size());

		ComponentDescriptor composedCDescriptor = compositeDescriptor.getComponents().get(2);
		Assert.assertEquals(3, composedCDescriptor.getComponents().size());

		Assert.assertEquals(ComponentState.activated, this.lifecycleServices.readComponentState(ComposedB.class));
	}

	@Test
	public void servicesTest() {
		this.cm.deployComponents(CompositeImpl.class);
		this.lifecycleServices.activateComponent(Composite.class);
		SubA1Services subA1Services = this.rcs.useServices(SubA1Services.class);
		SubB1Services subB1Services = this.rcs.useServices(SubB1Services.class);
		subB1Services.subB1Service("subB1Service");

		Assert.assertTrue(subA1Services.wasCalled());

	}

	@Test
	public void eventsTest() {
		this.cm.deployComponents(CompositeImpl.class);
		this.lifecycleServices.activateComponent(Composite.class);

		SubC1Services subC1Services = this.rcs.useServices(SubC1Services.class);
		subC1Services.produceEvent("event");

		ComposedCImpl composedCImpl = this.registryReadServices.getComponentInstance(ComposedC.class);
		Assert.assertTrue(composedCImpl.eventReceived);
	}

}
