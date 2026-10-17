/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainerParameters;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.events.EventsManagerServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.metamodel.MetamodelPortReadServices;
import org.opales.covalent.container.raw.RawComponentsParameters;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.directory.NamingServices;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.rr.services.ServicesManagerServices;
import org.opales.covalent.container.tests.CovalentContainerizedTests;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.simpleproducer.SimpleProducer;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.simpleproducer.impl.SimpleProducerImpl;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.ComponentAEvents;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.ComponentAServices;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.impl.ComponentA;
import org.opales.covalent.container.tests.unit.rawcomponents.componentb.impl.ComponentB;
import org.opales.covalent.container.tests.unit.rawcomponents.componentc.impl.ComponentC;

public class CovalentContainerContainerizedTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	@Before
	public void setUp() {
		this.rcs.registerComponent(this);
		// these tests are made with synchronous events to avoid implementing complex
		// thread synchronization
		this.cm.getParameters().setSynchronousEvents(true);
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
	}

	@After
	public void teardown() {
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.trace);
	}

	@Test
	public void testStartStopCovalentContainer() throws InterruptedException {
		RegistryReadServices registryReadServices = this.rcs.useServices(RegistryReadServices.class);

		int initialSize = registryReadServices.getInstances().size();

		// start 3 components
		ComponentA componentA = new ComponentA(true);
		this.rcs.registerComponent(componentA);

		ComponentB componentB = new ComponentB();
		this.rcs.registerComponent(componentB);

		componentB.consumeEventA(true);

		ComponentC componentC = new ComponentC();
		this.rcs.registerComponent(componentC);

		// components A, B, C
		int registeredComponents = registryReadServices.getInstances().size() - initialSize;
		Assert.assertEquals(3, registeredComponents);
		Assert.assertEquals(1, componentB.onCreate);

		// Stop shall clear components in registry
		this.cm.stop();

		Assert.assertEquals(1, componentB.onCreate);

		// Restart the container: internal (technical) components are genuinely
		// passivated by stop() and must be reactivated by start() before any
		// port - including raw registration - can be used again.
		this.cm.start();
		this.rcs.registerComponent(this);

		// Restart then deploy 3 components again
		this.rcs.registerComponent(componentA);

		this.rcs.registerComponent(componentB);

		componentB.consumeEventA(true);

		this.rcs.registerComponent(componentC);

		registryReadServices = this.rcs.useServices(RegistryReadServices.class);
		registeredComponents = registryReadServices.getInstances().size() - initialSize;
		Assert.assertEquals(3, registeredComponents);
		Assert.assertEquals(2, componentB.onCreate);

		this.rcs.unregisterComponent(ComponentA.class);
		this.rcs.unregisterComponent(ComponentB.class);
		this.rcs.unregisterComponent(ComponentC.class);
	}

	@Test
	public void testDefaultStateToReach() throws InterruptedException {
		ComponentA componentA = new ComponentA(true);
		this.rcs.registerComponent(componentA);
		ComponentState state = Covalent.main.lcs().readComponentState(ComponentA.class);

		// default state to reach is activated
		Assert.assertEquals(ComponentState.activated, state);

		this.rcs.unregisterComponent(ComponentA.class);

		RawComponentsParameters rcp = this.rcs.accessParameters(RawComponentsParameters.class);
		ComponentState originalStateToReach = rcp.getDefaultStateToReach();
		rcp.setDefaultStateToReach(ComponentState.instantiated);

		componentA = new ComponentA(true);
		this.rcs.registerComponent(componentA);
		state = Covalent.main.lcs().readComponentState(ComponentA.class);

		Assert.assertEquals(ComponentState.instantiated, state);

		this.rcs.unregisterComponent(ComponentA.class);

		// reset to original state to reach
		rcp.setDefaultStateToReach(originalStateToReach);
	}

	@Test
	public void componentsCanBeUnregisteredAndRegisteredAgain() throws InterruptedException {
		ComponentA componentA = new ComponentA();
		this.rcs.registerComponent(componentA);
		componentA.produceEvents(true);

		ComponentB componentB = new ComponentB();
		this.rcs.registerComponent(componentB);

		// ComponentB consumes ComponentAEvents
		componentB.consumeEventA(true);

		// test event reception
		componentA.getProduceComponentAEvents().onCreate("Test");

		Assert.assertEquals(1, componentB.onCreate);

		// now, passivate components
		this.rcs.unregisterComponent(ComponentA.class);
		this.rcs.unregisterComponent(ComponentB.class);

		// and activate them again
		ComponentA componentA2 = new ComponentA();
		this.rcs.registerComponent(componentA2);
		componentA2.produceEvents(true);

		// consume ComponentAEvents
		ComponentB componentB2 = new ComponentB();
		this.rcs.registerComponent(componentB2);

		// ComponentB consumes ComponentAEvents
		componentB2.consumeEventA(true);

		// test event reception
		componentA2.getProduceComponentAEvents().onCreate("Test");

		Assert.assertEquals(1, componentB2.onCreate);

		this.rcs.unregisterComponent(ComponentA.class);
		this.rcs.unregisterComponent(ComponentB.class);
	}

	@Test
	public void testManualUseServices() throws InterruptedException {
		ComponentA componentA = new ComponentA(true);
		this.rcs.registerComponent(componentA);

		ComponentAServices componentAServices = this.rcs.useServices(ComponentA.class, ComponentAServices.class);
		componentAServices.doSomething();

		Assert.assertTrue(componentA.doSomething);
		Assert.assertTrue(componentAServices.doSomethingWithBoolean(true));

		this.rcs.unregisterComponent(ComponentA.class);
	}

	@Test
	public void testUnprovideServices() throws InterruptedException {
		ComponentA componentA = new ComponentA(true);
		this.rcs.registerComponent(componentA);

		this.rcs.unprovideServices(ComponentA.class, ComponentAServices.class);

		ComponentAServices componentAServices = this.rcs.useServices(ComponentA.class, ComponentAServices.class);
		IllegalStateException failure = Assert.assertThrows(
				"Shall throw an exception because the service is not bound", IllegalStateException.class,
				() -> componentAServices.doSomething());
		Assert.assertTrue(this.isSurrogateException(failure));

		Assert.assertFalse(componentA.doSomething);

		this.rcs.unregisterComponent(ComponentA.class);
	}

	@Test
	public void testReplaceComponentInstance() throws InterruptedException {
		ComponentA componentA = new ComponentA();
		this.rcs.registerComponent(componentA);

		ComponentB componentB = new ComponentB();
		this.rcs.registerComponent(componentB);

		componentB.consumeEventA(true);

		// Replace component B with custom one
		this.rcs.unregisterComponent(ComponentB.class);

		ComponentBReplacement componentBReplacement = new ComponentBReplacement();
		this.rcs.registerComponent(componentBReplacement);

		// Notify A
		componentA.getProduceComponentAEvents().onCreate("data");

		// Event shall be triggered for replacement instance but not for default one
		Assert.assertEquals(40, componentBReplacement.onCreate); // 40 is hard coded in replacement instance
		Assert.assertEquals(0, componentB.onCreate);

		this.rcs.unregisterComponent(ComponentA.class);
	}

	@Test
	public void servicesAreAccessibleAgainAfterTheProvidersAreUnregisteredAndRegisteredAgain() {
		ComponentA componentA = new ComponentA();
		this.rcs.registerComponent(componentA);

		ComponentB componentB = new ComponentB();
		this.rcs.registerComponent(componentB);

		ComponentAServices useComponentAServices = componentB.getUseComponentAServices();
		Assert.assertNotNull(useComponentAServices);

		useComponentAServices.doSomething();
		Assert.assertTrue(componentA.doSomething);

		// Unregister component
		this.rcs.unregisterComponent(ComponentA.class);
		this.rcs.unregisterComponent(ComponentB.class);

		// Then register them again
		componentA = new ComponentA();
		this.rcs.registerComponent(componentA);

		componentB = new ComponentB();
		this.rcs.registerComponent(componentB);

		useComponentAServices = componentB.getUseComponentAServices();
		Assert.assertNotNull(useComponentAServices);

		useComponentAServices.doSomething();
		Assert.assertTrue(componentA.doSomething);

		this.rcs.unregisterComponent(ComponentA.class);
		this.rcs.unregisterComponent(ComponentB.class);
	}

	@Test
	public void testCovalentContainerParametersAreOfferedByContainerItem() {
		MetamodelPortReadServices metamodelPortReadServices = this.rcs.useServices(MetamodelPortReadServices.class);
		NamingServices namingServices = this.rcs.useServices(NamingServices.class);

		Assert.assertTrue(metamodelPortReadServices.getOfferedParameters(CovalentContainer.class)
				.contains(CovalentContainerParameters.class));
		Assert.assertEquals(CovalentContainer.class,
				metamodelPortReadServices.getComponentTypeForOfferedParameters(CovalentContainerParameters.class));
		Assert.assertTrue(namingServices.isParametersBound(CovalentContainerParameters.class));

		CovalentContainerParameters parameters = namingServices.lookupParameters(CovalentContainerParameters.class);
		Assert.assertNotNull(parameters);
		boolean originalSynchronousEvents = this.cm.getParameters().isSynchronousEvents();
		parameters.setSynchronousEvents(!originalSynchronousEvents);
		Assert.assertEquals(!originalSynchronousEvents, this.cm.getParameters().isSynchronousEvents());
		parameters.setSynchronousEvents(originalSynchronousEvents);
	}

	@Test
	public void stoppingAContainerHoldingAnInstantiatedComponentWritesNothingToStderr() {
		LifecycleServices lifecycleServices = Covalent.main.rcs().useServices(LifecycleServices.class);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(baos);
		PrintStream old = System.err;
		System.setErr(ps);
		try {
			this.cm.deployComponents(SimpleProducerImpl.class);
			lifecycleServices.instantiateComponent(SimpleProducer.class);
			this.cm.stop();
			System.err.flush();
			Assert.assertFalse(baos.toString(), baos.toString().contains("Exception"));
		} finally {
			System.setErr(old);
			// This test stops the container itself: start it again, so that the automatic
			// teardown of the base class finds it started.
			this.cm.start();
		}
	}

	class ComponentBReplacement extends ComponentB {

		@Override
		@Activation
		public void activate() {
			this.rcs.subscribe(ComponentBReplacement.class, ComponentAEvents.class, this);
		}

		@Override
		public void onCreate(final String data) {
			this.onCreate = 40;
		}
	}

}
