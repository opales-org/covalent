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
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.events.EventsManagerServices;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.rr.services.ServicesManagerServices;
import org.opales.covalent.container.tests.CovalentTests;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.impl.ComponentA;
import org.opales.covalent.container.tests.unit.rawcomponents.componentb.impl.ComponentB;

public class CovalentContainerTests extends CovalentTests {

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
		this.cm.stop();
	}

	@Test
	public void aRawComponentRegisteredAfterStartIsTheInstanceKnownByTheRegistry() {
		// Raw registration needs the container started (technical services are
		// locked while stopped); start first, then register manually.
		this.cm.start();
		ComponentA componentA = new ComponentA();
		this.rcs.registerComponent(componentA);

		RegistryReadServices registryReadServices = this.rcs.useServices(RegistryReadServices.class);
		Assert.assertSame(componentA, registryReadServices.getComponentInstance(ComponentA.class));
	}

	@Test
	public void testCovalentContainerItemsServicesInjection() {
		this.cm.deployComponents(ComponentUsingCovalentServicesImpl.class);
		this.cm.start();

		RegistryReadServices registryReadServices = this.rcs.useServices(RegistryReadServices.class);
		ComponentUsingCovalentServicesImpl componentUsingCovalentServicesImpl = registryReadServices
				.getComponentInstance(ComponentUsingCovalentServices.class);

		Assert.assertTrue(componentUsingCovalentServicesImpl.checkInjectedRegistryServices());
		Assert.assertTrue(componentUsingCovalentServicesImpl.checkInjectedEventsManagerServices());
		Assert.assertTrue(componentUsingCovalentServicesImpl.checkInjectedServicesManagerServices());
		Assert.assertTrue(componentUsingCovalentServicesImpl.checkInjectedLifecycleServices());
	}

	@Component
	public interface ComponentUsingCovalentServices {
	}

	@Implementation
	public static class ComponentUsingCovalentServicesImpl implements ComponentUsingCovalentServices {
		@UsedServices
		private RegistryReadServices registryReadServices;

		@UsedServices
		private EventsManagerServices eventsManagerServices;

		@UsedServices
		private ServicesManagerServices servicesManagerServices;

		@UsedServices
		private LifecycleServices lifecycleServices;

		public boolean checkInjectedRegistryServices() {
			return this.registryReadServices != null;
		}

		public boolean checkInjectedEventsManagerServices() {
			return this.eventsManagerServices != null;
		}

		public boolean checkInjectedServicesManagerServices() {
			return this.servicesManagerServices != null;
		}

		public boolean checkInjectedLifecycleServices() {
			return this.lifecycleServices != null;
		}

	}

}
