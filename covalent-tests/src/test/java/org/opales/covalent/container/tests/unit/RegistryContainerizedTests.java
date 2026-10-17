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
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.registry.RegistryEvents;
import org.opales.covalent.container.registry.RegistryWriteServices;
import org.opales.covalent.container.registry.ComponentDescriptor;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.simpleproducer.SimpleProducer;

public class RegistryContainerizedTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	@Before
	public void setUp() {
		this.rcs.registerComponent(this);
		this.cm.getParameters().setSynchronousEvents(true);
	}

	@Test
	public void registryRejectsDuplicateRegistrationAndMissingUnregister() {
		RegistryWriteServices registryWriteServices = this.rcs.useServices(RegistryWriteServices.class);
		Object instance = new Object();

		registryWriteServices.registerComponent(DirectRegistryComponent.class, instance);

		try {
			Assert.assertSame(instance, registryWriteServices.getComponentInstance(DirectRegistryComponent.class));
			Assert.assertThrows(IllegalStateException.class,
					() -> registryWriteServices.registerComponent(DirectRegistryComponent.class, new Object()));
			registryWriteServices.unregisterComponent(DirectRegistryComponent.class);
			Assert.assertFalse(registryWriteServices.isRegistered(DirectRegistryComponent.class));
			Assert.assertThrows(IllegalStateException.class,
					() -> registryWriteServices.unregisterComponent(DirectRegistryComponent.class));
		} finally {
			if (registryWriteServices.isRegistered(DirectRegistryComponent.class)) {
				registryWriteServices.unregisterComponent(DirectRegistryComponent.class);
			}
		}
	}

	public static class DirectRegistryComponent {
	}
}
