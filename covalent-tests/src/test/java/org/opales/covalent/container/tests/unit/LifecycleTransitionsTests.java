/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.async.AsyncLifecycleServices;
import org.opales.covalent.container.autowiring.AutoWiringServices;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.events.EventsLifecycleServices;
import org.opales.covalent.container.lifecycle.LifecycleManager;
import org.opales.covalent.container.lifecycle.LifecycleParameters;
import org.opales.covalent.container.lifecycle.LifecycleServices.StartMode;
import org.opales.covalent.container.lifecycle.impl.LifecycleTransitions;
import org.opales.covalent.container.lifecycle.impl.LifecycleManagerImpl;
import org.opales.covalent.container.lifecycle.impl.LifecycleTransitionsImpl;
import org.opales.covalent.container.registry.DescriptorRegistryReadServices;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.registry.ComponentDescriptor;
import org.opales.covalent.container.rr.parameters.ParametersLifecycleServices;
import org.opales.covalent.container.rr.services.ServicesLifecycleServices;
import org.opales.covalent.container.util.Reflection;

public class LifecycleTransitionsTests {

	private final List<String> calls = new ArrayList<>();
	private LifecycleTransitionsImpl transitions;

	@Before
	public void setUp() {
		this.transitions = new LifecycleTransitionsImpl();
		for (Class<?> service : Arrays.asList(AutoWiringServices.class, ServicesLifecycleServices.class,
				EventsLifecycleServices.class, ParametersLifecycleServices.class, AsyncLifecycleServices.class)) {
			Reflection.setField(Reflection.lookupFieldWithAnnotation(LifecycleTransitionsImpl.class, service,
					UsedServices.class), this.transitions, this.recordingService(service));
		}
	}

	@Test
	public void verticalModeDelegatesEveryTransitionInOrder() {
		this.assertLifecycle(StartMode.vertical);
	}

	@Test
	public void horizontalModeDelegatesEveryTransitionInOrder() {
		this.assertLifecycle(StartMode.horizontal);
	}

	private void assertLifecycle(final StartMode mode) {
		this.transitions.initialize(mode);
		Assert.assertTrue("Initialization must not invoke port services", this.calls.isEmpty());
		this.assertCalls(t -> t.postDeploy(Fixture.class));
		this.assertCalls(t -> t.preInstantiate(Fixture.class));
		this.assertCalls(t -> t.postInstantiate(Fixture.class), "createOfferedParameters",
				"activateOfferedParameters", "publishOfferedParameters");
		if (mode == StartMode.vertical) {
			this.assertCalls(t -> t.preInitialize(Fixture.class), "createUsedServices", "createConsumedEvents",
					"createUsedSubscribers", "createAccessedParameters");
		} else {
			this.assertCalls(t -> t.preInitialize(Fixture.class), "createAccessedParameters");
		}
		this.assertCalls(t -> t.postInitialize(Fixture.class), "createProducedEvents", "createProvidedServices");
		if (mode == StartMode.vertical) {
			this.assertCalls(t -> t.preActivate(Fixture.class));
		} else {
			this.assertCalls(t -> t.preActivate(Fixture.class), "createUsedServices", "createConsumedEvents",
					"createUsedSubscribers");
		}
		this.assertCalls(t -> t.postActivate(Fixture.class), "activateProvidedServices", "publishProvidedServices",
				"activateProducedEvents", "publishProvidedSubscribers");
		this.assertCalls(t -> t.prePassivate(Fixture.class), "cancelPeriodicTasks", "unpublishProvidedServices",
				"unpublishProvidedSubscribers");
		this.assertCalls(t -> t.postPassivate(Fixture.class), "passivateProvidedServices", "passivateProducedEvents");
		this.assertCalls(t -> t.preRemove(Fixture.class), "cancelPeriodicTasks", "deleteProvidedServices", "deleteProducedEvents",
				"deleteUsedServices", "deleteConsumedEvents", "deleteAccessedParameters", "unpublishOfferedParameters",
				"passivateOfferedParameters", "deleteOfferedParameters");
		this.assertCalls(t -> t.postRemove(Fixture.class));
		this.assertCalls(t -> t.preUndeploy(Fixture.class));
	}

	@Test
	public void reinitializationSwitchesModesInBothDirections() {
		for (StartMode mode : Arrays.asList(StartMode.vertical, StartMode.horizontal, StartMode.vertical)) {
			this.assertLifecycle(mode);
		}
	}

	@Test
	public void reinitializationUsesNewlyInjectedServices() {
		this.transitions.initialize(StartMode.vertical);
		List<String> replacementCalls = new ArrayList<>();
		Reflection.setField(Reflection.lookupFieldWithAnnotation(LifecycleTransitionsImpl.class,
				ServicesLifecycleServices.class, UsedServices.class), this.transitions,
				ServicesLifecycleServices.class.cast(Proxy.newProxyInstance(
				ServicesLifecycleServices.class.getClassLoader(), new Class<?>[] { ServicesLifecycleServices.class },
				(proxy, method, arguments) -> {
					replacementCalls.add(method.getName());
					Assert.assertSame(Fixture.class, arguments[0]);
					return null;
				})));
		this.transitions.initialize(StartMode.horizontal);
		this.assertCalls(t -> t.postActivate(Fixture.class), "activateProducedEvents", "publishProvidedSubscribers");
		Assert.assertEquals(Arrays.asList("activateProvidedServices", "publishProvidedServices"), replacementCalls);
	}

	@Test
	public void transitionFailureIsPropagatedWithoutContinuing() {
		RuntimeException failure = new IllegalStateException("Expected service activation failure");
		Reflection.setField(Reflection.lookupFieldWithAnnotation(LifecycleTransitionsImpl.class,
				ServicesLifecycleServices.class, UsedServices.class), this.transitions,
				ServicesLifecycleServices.class.cast(Proxy.newProxyInstance(
				ServicesLifecycleServices.class.getClassLoader(), new Class<?>[] { ServicesLifecycleServices.class },
				(proxy, method, arguments) -> {
					Assert.assertEquals("activateProvidedServices", method.getName());
					throw failure;
				})));
		this.transitions.initialize(StartMode.vertical);
		Assert.assertSame(failure,
				Assert.assertThrows(RuntimeException.class, () -> this.transitions.postActivate(Fixture.class)));
		Assert.assertTrue(this.calls.isEmpty());
	}

	@Test
	public void itemsDoNotShareDependenciesOrSelectedMode() {
		LifecycleTransitionsImpl other = new LifecycleTransitionsImpl();
		List<String> otherCalls = new ArrayList<>();
		Reflection.setField(Reflection.lookupFieldWithAnnotation(LifecycleTransitionsImpl.class,
				AutoWiringServices.class, UsedServices.class), other, AutoWiringServices.class.cast(Proxy.newProxyInstance(
				AutoWiringServices.class.getClassLoader(), new Class<?>[] { AutoWiringServices.class },
				(proxy, method, arguments) -> {
					otherCalls.add(method.getName());
					return null;
				})));
		other.initialize(StartMode.horizontal);
		this.transitions.initialize(StartMode.vertical);
		this.assertCalls(t -> t.preInitialize(Fixture.class), "createUsedServices", "createConsumedEvents",
				"createUsedSubscribers", "createAccessedParameters");
		other.preInitialize(Fixture.class);
		Assert.assertEquals(Arrays.asList("createAccessedParameters"), otherCalls);
	}

	@Test
	public void bootstrapRetainsTheItemForVerticalStartup() throws Exception {
		this.assertBootstrapAndStartup(StartMode.vertical);
	}

	@Test
	public void bootstrapRetainsTheItemForHorizontalStartup() throws Exception {
		this.assertBootstrapAndStartup(StartMode.horizontal);
	}

	private void assertBootstrapAndStartup(final StartMode mode) throws Exception {
		CovalentContainer container = Covalent.container();
		try {
			container.rcs().registerComponent(this);
			Assert.assertTrue(container.lcs().isActivated(LifecycleManager.class));
			RegistryReadServices registry = container.rcs().useServices(RegistryReadServices.class);
			DescriptorRegistryReadServices descriptors = container.rcs().useServices(DescriptorRegistryReadServices.class);
			LifecycleManagerImpl manager = (LifecycleManagerImpl) registry.getComponentInstance(LifecycleManager.class);
			LifecycleTransitionsImpl item = (LifecycleTransitionsImpl) Reflection.lookupFieldWithAnnotation(
					LifecycleManagerImpl.class, LifecycleTransitionsImpl.class, Item.class).get(manager);
			ComponentDescriptor descriptor = descriptors.readComponentDescriptor(LifecycleManager.class);
			// LifecycleManagerImpl has two items: LifecycleTransitionsImpl (this
			// test's concern) and DependencyGraphChecker (the dependency graph
			// tracking item, providing DependencyGraphServices).
			Assert.assertEquals(2, descriptor.getItems().size());
			for (Class<?> service : Arrays.asList(AutoWiringServices.class, ServicesLifecycleServices.class,
					EventsLifecycleServices.class, ParametersLifecycleServices.class, AsyncLifecycleServices.class)) {
				Assert.assertEquals(1, descriptor.getUsedServicesItemAndField(service).size());
				Assert.assertSame(item, descriptor.getUsedServicesItemAndField(service).get(0).getItem());
			}
			container.rcs().accessParameters(LifecycleParameters.class).setStartMode(mode);
			container.start();
			Assert.assertSame(item, Reflection.lookupFieldWithAnnotation(
					LifecycleManagerImpl.class, LifecycleTransitionsImpl.class, Item.class).get(manager));
			Assert.assertTrue(container.lcs().isActivated(LifecycleManager.class));
		} finally {
			container.dispose();
		}
	}

	private void assertCalls(final Consumer<LifecycleTransitions> transition, final String... expected) {
		this.calls.clear();
		transition.accept(this.transitions);
		Assert.assertEquals(Arrays.asList(expected), this.calls);
	}

	private <T> T recordingService(final Class<T> serviceType) {
		return serviceType.cast(Proxy.newProxyInstance(serviceType.getClassLoader(), new Class<?>[] { serviceType },
				(proxy, method, arguments) -> {
					Assert.assertArrayEquals(new Object[] { Fixture.class }, arguments);
					this.calls.add(method.getName());
					return null;
				}));
	}

	private static class Fixture {
	}
}
