/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.events.EventsManagerServices;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.lifecycle.LifecycleParameters;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.lifecycle.LifecycleServices.StartMode;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.tests.CovalentContainerizedTests;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.FullyConnected;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.FullyConnectedEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.FullyConnectedEvents.FullyConnectedEventsSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.FullyConnectedParameters;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.FullyConnectedServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.impl.FullyConnectedImpl;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithdelegation.ProducerWithSubscribeAndDelegation;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithdelegation.impl.ProducerWithSubscribeAndDelegationImpl;
import org.opales.covalent.container.tests.unit.rawcomponents.componentwithlifecycleannotations.ComponentWithLifecycleAnnotation;

public class LifecycleContainerizedTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	private LifecycleServices lifecycleServices;

	private LifecycleParameters lifecycleParameters;

	@Before
	public void setUp() {
		this.rcs.registerComponent(this);
		this.cm.getParameters().setSynchronousEvents(true);
		// Looked up here, not as field initializers: the base class's own
		// @Before (which runs first) is what starts the container, and these
		// raw lookups need it already running.
		this.lifecycleServices = this.cm.rcs().useServices(LifecycleServices.class);
		this.lifecycleParameters = this.cm.rcs().accessParameters(LifecycleParameters.class);
	}

	@Test
	public void lifecycleParametersExposeStartModeAndNameConvention() {
		Assert.assertEquals(StartMode.vertical, this.lifecycleParameters.getStartMode());
		this.lifecycleParameters.setFindLifecycleMethodByNameConvention(false);
		Assert.assertFalse(this.lifecycleParameters.isFindLifecycleMethodByNameConvention());

		this.lifecycleParameters.setStartMode(StartMode.horizontal);
		this.lifecycleParameters.setFindLifecycleMethodByNameConvention(true);

		Assert.assertEquals(StartMode.horizontal, this.lifecycleParameters.getStartMode());
		Assert.assertTrue(this.lifecycleParameters.isFindLifecycleMethodByNameConvention());

		this.lifecycleParameters.setStartMode(StartMode.vertical);
		this.lifecycleParameters.setFindLifecycleMethodByNameConvention(false);
	}

	@Test
	public void testRegister() {
		ComponentWithLifecycleAnnotation cwlfa = new ComponentWithLifecycleAnnotation();

		this.rcs.registerComponent(cwlfa.getClass(), cwlfa, ComponentState.instantiated);

		Assert.assertTrue("state:" + this.lifecycleServices.readComponentState(cwlfa.getClass()),
				ComponentState.instantiated == this.lifecycleServices.readComponentState(cwlfa.getClass()));

		this.rcs.unregisterComponent(cwlfa.getClass());
	}

	@Test
	public void testInitializationAnnotation() {
		ComponentWithLifecycleAnnotation cwlfa = new ComponentWithLifecycleAnnotation();

		this.rcs.registerComponent(cwlfa.getClass(), cwlfa, ComponentState.initialized);

		Assert.assertTrue(ComponentState.initialized == cwlfa.state);

		this.rcs.unregisterComponent(cwlfa.getClass());
	}

	@Test
	public void testActivationAnnotation() {
		ComponentWithLifecycleAnnotation cwlfa = new ComponentWithLifecycleAnnotation();

		this.rcs.registerComponent(cwlfa.getClass(), cwlfa, ComponentState.activated);

		Assert.assertTrue(ComponentState.activated == cwlfa.state);

		this.rcs.unregisterComponent(cwlfa.getClass());
	}

	@Test
	public void testPassivationAnnotation() {
		ComponentWithLifecycleAnnotation cwlfa = new ComponentWithLifecycleAnnotation();

		this.rcs.registerComponent(cwlfa.getClass(), cwlfa, ComponentState.activated);
		this.lifecycleServices.passivateComponent(cwlfa.getClass());

		Assert.assertTrue(ComponentState.passivated == cwlfa.state);

		this.rcs.unregisterComponent(cwlfa.getClass());
	}

	@Test
	public void testRemove() {
		ComponentWithLifecycleAnnotation cwlfa = new ComponentWithLifecycleAnnotation();

		this.rcs.registerComponent(cwlfa.getClass(), cwlfa, ComponentState.activated);
		this.lifecycleServices.removeComponent(cwlfa.getClass());

		Assert.assertTrue("state:" + this.lifecycleServices.readComponentState(cwlfa.getClass()),
				ComponentState.removed == this.lifecycleServices.readComponentState(cwlfa.getClass()));

		this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		RuntimeException failure = Assert.assertThrows(RuntimeException.class,
				() -> this.lifecycleServices.removeComponent(cwlfa.getClass()));
		Assert.assertTrue(this.isExceptionIncludingText(failure, "already removed"));
	}

	@Test
	public void testUndeploy() {
		ComponentWithLifecycleAnnotation cwlfa = new ComponentWithLifecycleAnnotation();

		this.rcs.registerComponent(cwlfa.getClass(), cwlfa, ComponentState.activated);
		this.lifecycleServices.undeployComponent(cwlfa.getClass());

		Assert.assertTrue("state:" + this.lifecycleServices.readComponentState(cwlfa.getClass()),
				null == this.lifecycleServices.readComponentState(cwlfa.getClass()));

	}

	@Test
	public void testUnregister() {
		ComponentWithLifecycleAnnotation cwlfa = new ComponentWithLifecycleAnnotation();

		this.rcs.registerComponent(cwlfa.getClass(), cwlfa, ComponentState.activated);
		this.rcs.unregisterComponent(cwlfa.getClass());

		Assert.assertTrue("state:" + this.lifecycleServices.readComponentState(cwlfa.getClass()),
				null == this.lifecycleServices.readComponentState(cwlfa.getClass()));

	}

	@Test
	public void testPortsOnInitialization() {
		EventsManagerServices eventsManagerServices = Covalent.main.rcs().useServices(EventsManagerServices.class);
		this.lifecycleServices.deployComponents(ProducerWithSubscribeAndDelegationImpl.class, FullyConnectedImpl.class);

		FullyConnectedEventsMock eventConsumerMock = new FullyConnectedEventsMock();

		this.lifecycleServices.activateComponent(ProducerWithSubscribeAndDelegation.class);
		this.lifecycleServices.initializeComponent(FullyConnected.class);
		RegistryReadServices registryReadServices = this.rcs.useServices(RegistryReadServices.class);
		FullyConnectedImpl instance = registryReadServices.getComponentInstance(FullyConnected.class);

		ProducerServices producerServices = this.rcs.useServices(ProducerWithSubscribeAndDelegation.class,
				ProducerServices.class);

		FullyConnectedServices services = this.rcs.useServices(FullyConnected.class, FullyConnectedServices.class);
		FullyConnectedParameters parameters = this.rcs.accessParameters(FullyConnected.class,
				FullyConnectedParameters.class);
		// manually consume events as used subscriber may not work
		FullyConnectedEventsSubscribe subscriber = eventsManagerServices
				.createUsedSubscriber(FullyConnectedEvents.class, FullyConnectedEventsSubscribe.class);

		// provided services should not work
		IllegalStateException servicesFailure = Assert.assertThrows(IllegalStateException.class,
				() -> services.checkConsumedEvents());
		Assert.assertTrue(this.isSurrogateException(servicesFailure));
		instance.reset();

		// provided parameters should work
		Assert.assertTrue(parameters.getParameter());
		instance.reset();

		// used subscriber should work
		boolean success = instance.checkUsedSubscribe();
		Assert.assertTrue(success);
		instance.reset();

		// now that the component has subscribed, we can check events consumption
		// consumed events should work
		producerServices.fireEvent("event");
		success = instance.checkConsumedEvents();
		Assert.assertTrue(success);
		instance.reset();

		// used services should work
		Assert.assertTrue(instance.checkUsedServices());
		instance.reset();

		// accesses parameters should work
		Assert.assertTrue(instance.checkAccessedParameters());
		instance.reset();

		// provided subscribe should not work
		Assert.assertThrows(IllegalStateException.class, () -> subscriber.subscribe(eventConsumerMock));
		Assert.assertFalse(instance.checkProvidedSubscribe());
		instance.reset();

		// produced events should not work (as notifier is passivated)
		Assert.assertTrue(instance.checkProducedEvents());
		Assert.assertFalse(eventConsumerMock.received);
		eventConsumerMock.received = false;
		instance.reset();
	}

	@Test
	public void testPortsOnActivation() {
		EventsManagerServices eventsManagerServices = Covalent.main.rcs().useServices(EventsManagerServices.class);
		this.lifecycleServices.deployComponents(ProducerWithSubscribeAndDelegationImpl.class, FullyConnectedImpl.class);
		FullyConnectedEventsMock eventConsumerMock = new FullyConnectedEventsMock();

		this.lifecycleServices.activateComponent(ProducerWithSubscribeAndDelegation.class);
		this.lifecycleServices.activateComponent(FullyConnected.class);

		RegistryReadServices registryReadServices = this.rcs.useServices(RegistryReadServices.class);
		FullyConnectedImpl instance = registryReadServices.getComponentInstance(FullyConnected.class);

		ProducerServices producerServices = this.rcs.useServices(ProducerWithSubscribeAndDelegation.class,
				ProducerServices.class);

		FullyConnectedServices services = this.rcs.useServices(FullyConnected.class, FullyConnectedServices.class);
		FullyConnectedParameters parameters = this.rcs.accessParameters(FullyConnected.class,
				FullyConnectedParameters.class);
		FullyConnectedEventsSubscribe subscriber = eventsManagerServices
				.createUsedSubscriber(FullyConnectedEvents.class, FullyConnectedEventsSubscribe.class);

		// provided services should work
		Assert.assertFalse(services.checkConsumedEvents());
		instance.reset();

		// provided parameters should work
		Assert.assertTrue(parameters.getParameter());
		instance.reset();

		// used subscriber should work
		boolean success = instance.checkUsedSubscribe();
		Assert.assertTrue(success);
		instance.reset();

		// now that the component is subscribed, we can check events consumption
		// consumed events should work
		producerServices.fireEvent("event");
		success = instance.checkConsumedEvents();
		Assert.assertTrue(success);
		instance.reset();

		// used services should work
		Assert.assertTrue(instance.checkUsedServices());
		instance.reset();

		// accesses parameters should work
		Assert.assertTrue(instance.checkAccessedParameters());
		instance.reset();

		// provided subscribe should work
		subscriber.subscribe(eventConsumerMock);
		Assert.assertTrue(instance.checkProvidedSubscribe());
		instance.reset();

		// produced events should work
		Assert.assertTrue(instance.checkProducedEvents());
		Assert.assertTrue(eventConsumerMock.received);
		eventConsumerMock.received = false;
		instance.reset();

	}

	@Test
	public void testPortsOnPassivation() {
		EventsManagerServices eventsManagerServices = Covalent.main.rcs().useServices(EventsManagerServices.class);
		this.lifecycleServices.deployComponents(ProducerWithSubscribeAndDelegationImpl.class, FullyConnectedImpl.class);

		FullyConnectedEventsMock eventConsumerMock = new FullyConnectedEventsMock();

		this.lifecycleServices.activateComponent(ProducerWithSubscribeAndDelegation.class);
		this.lifecycleServices.activateComponent(FullyConnected.class);

		RegistryReadServices registryReadServices = this.rcs.useServices(RegistryReadServices.class);
		FullyConnectedImpl instance = registryReadServices.getComponentInstance(FullyConnected.class);

		ProducerServices producerServices = this.rcs.useServices(ProducerWithSubscribeAndDelegation.class,
				ProducerServices.class);

		FullyConnectedServices services = this.rcs.useServices(FullyConnected.class, FullyConnectedServices.class);
		FullyConnectedParameters parameters = this.rcs.accessParameters(FullyConnected.class,
				FullyConnectedParameters.class);
		// manually consume events as used subscriber may not work after passivation
		FullyConnectedEventsSubscribe subscriber = eventsManagerServices
				.createUsedSubscriber(FullyConnectedEvents.class, FullyConnectedEventsSubscribe.class);

		this.lifecycleServices.passivateComponent(FullyConnected.class);

		// provided services should throw an exception
		IllegalStateException passivatedFailure = Assert.assertThrows(IllegalStateException.class,
				() -> services.checkConsumedEvents());
		Assert.assertTrue(this.isPassivatedException(passivatedFailure));
		instance.reset();

		// provided parameters should work
		Assert.assertTrue(parameters.getParameter());
		instance.reset();

		// used subscriber should work
		boolean success = instance.checkUsedSubscribe();
		Assert.assertTrue(success);
		instance.reset();

		// now that the component is subscribed, we can check events consumption
		// consumed events should work
		producerServices.fireEvent("event");
		success = instance.checkConsumedEvents();
		Assert.assertTrue(success);
		instance.reset();

		// used services should work
		Assert.assertTrue(instance.checkUsedServices());
		instance.reset();

		// accesses parameters should work
		Assert.assertTrue(instance.checkAccessedParameters());
		instance.reset();

		// provided subscribe should be silent (consumer registered as pending)
		subscriber.subscribe(eventConsumerMock);
		Assert.assertFalse(instance.checkProvidedSubscribe());
		instance.reset();

		// produced events should be silent (ignored)
		Assert.assertTrue(instance.checkProducedEvents());
		Assert.assertFalse(eventConsumerMock.received);
		eventConsumerMock.received = false;
		instance.reset();

	}

	private static class FullyConnectedEventsMock implements FullyConnectedEvents {
		boolean received = false;

		@Override
		public void onEvent(final String string) {
			this.received = true;
		}

	}
}
