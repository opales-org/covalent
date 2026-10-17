/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.tests.unit.rawmanagedcircular.CircularManagedType;
import org.opales.covalent.container.tests.unit.rawmanagedcircular.impl.CircularManagedImpl;
import org.opales.covalent.container.tests.unit.rawmanagedcircular.impl.CircularRawComponent;

/**
 * A raw component and a managed component that mutually depend on each other's
 * services, each side's declared or requested port only resolvable through
 * the other component.
 * <p>
 * The dependency graph used for circular-dependency checking only covers managed
 * components: raw components are never part of it, so a cycle through a raw component is
 * not detected. Both use-services resolution paths
 * ({@code RRManagerImpl.createClientProxy}, used by both
 * {@code RawComponentsServices.useServices} and a managed
 * {@code @UsedServices} field) never require the provider to already be
 * published: when the requested service is not yet bound, a surrogate proxy
 * is created instead and later rebound to the real provider-side proxy once
 * it is published. This makes the resolution order-independent, and these
 * tests exercise both possible registration orders to confirm it holds in
 * both directions.
 * </p>
 */
public class RawManagedCircularDependencyTests {

	private CovalentContainer container;

	@After
	public void tearDown() {
		if (this.container != null) {
			this.container.dispose();
		}
	}

	@Test
	public void managedComponentActivatedBeforeItsRawServiceProviderStillResolvesBothDirections() {
		this.container = Covalent.container();
		RawComponentsServices rawComponentsServices = this.container.rcs();
		rawComponentsServices.registerComponent(this);
		LifecycleServices lifecycleServices = this.container.lcs();
		RegistryReadServices registryReadServices = rawComponentsServices.useServices(RegistryReadServices.class);

		// The managed component is deployed and started first: its @UsedServices
		// port for RawSideServices is created while no raw component provides that
		// service yet.
		lifecycleServices.deployComponent(CircularManagedImpl.class);
		this.container.start();

		// The raw component registers second, closing the dependency loop: it
		// provides the service the managed component already depends on, and it
		// itself depends on a service only the managed component provides.
		CircularRawComponent rawComponent = new CircularRawComponent(rawComponentsServices);
		rawComponentsServices.registerComponent(rawComponent);

		Assert.assertEquals("pong", rawComponent.callManaged());

		CircularManagedImpl managedInstance = (CircularManagedImpl) registryReadServices
				.getComponentInstance(CircularManagedType.class);
		Assert.assertEquals("ping", managedInstance.callRaw());

		rawComponentsServices.unregisterComponent(CircularRawComponent.class);
	}

	@Test
	public void rawComponentRegisteredBeforeItsManagedServiceConsumerStillResolvesBothDirections() {
		this.container = Covalent.container();
		RawComponentsServices rawComponentsServices = this.container.rcs();
		rawComponentsServices.registerComponent(this);
		LifecycleServices lifecycleServices = this.container.lcs();
		RegistryReadServices registryReadServices = rawComponentsServices.useServices(RegistryReadServices.class);

		// The raw component registers first: its own useServices(ManagedSideServices)
		// call happens before any managed component provides that service, and
		// before the managed component's metamodel is even deployed.
		CircularRawComponent rawComponent = new CircularRawComponent(rawComponentsServices);
		rawComponentsServices.registerComponent(rawComponent);

		lifecycleServices.deployComponent(CircularManagedImpl.class);
		this.container.start();

		Assert.assertEquals("pong", rawComponent.callManaged());

		CircularManagedImpl managedInstance = (CircularManagedImpl) registryReadServices
				.getComponentInstance(CircularManagedType.class);
		Assert.assertEquals("ping", managedInstance.callRaw());

		rawComponentsServices.unregisterComponent(CircularRawComponent.class);
	}

}
