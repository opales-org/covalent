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
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.lifecycle.RawComponentLifecycleServices;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.registry.RegistryReadServices;

public class RawComponentLifecycleServicesTests {

	private CovalentContainer container;
	private RawComponentsServices rawComponentsServices;
	private LifecycleServices lifecycleServices;
	private RegistryReadServices registryReadServices;

	@Before
	public void setUp() {
		this.container = Covalent.container();
		this.rawComponentsServices = this.container.rcs();
		this.lifecycleServices = this.container.lcs();
		this.rawComponentsServices.registerComponent(this);
		this.registryReadServices = this.rawComponentsServices.useServices(RegistryReadServices.class);
		RawFixture.constructions = 0;
	}

	// No tearDown(): this container is never started (these tests exercise the
	// pre-first-start bootstrap window only), and stop() requires having
	// been started first; the throwaway container is simply discarded after
	// each test.

	@Test
	public void rawRegistrationUsesPseudoInstantiationWithTheSuppliedInstance() {
		Assert.assertNotNull(this.rawComponentsServices.useServices(RawComponentLifecycleServices.class));
		RawFixture suppliedInstance = new RawFixture();

		this.rawComponentsServices.registerComponent(RawFixture.class, suppliedInstance, ComponentState.instantiated);

		Assert.assertEquals(1, RawFixture.constructions);
		Assert.assertSame(suppliedInstance, this.registryReadServices.getComponentInstance(RawFixture.class));
		Assert.assertEquals(ComponentState.instantiated,
				this.lifecycleServices.readComponentState(RawFixture.class));

		this.rawComponentsServices.unregisterComponent(RawFixture.class);
	}

	@Test
	public void initializingAComponentifiedRawInstanceDoesNotInstantiateAgain() {
		RawFixture suppliedInstance = new RawFixture();
		this.rawComponentsServices.registerComponent(RawFixture.class, suppliedInstance, ComponentState.instantiated);

		this.lifecycleServices.initializeComponent(RawFixture.class);

		Assert.assertEquals(1, RawFixture.constructions);
		Assert.assertSame(suppliedInstance, this.registryReadServices.getComponentInstance(RawFixture.class));
		Assert.assertEquals(ComponentState.initialized, this.lifecycleServices.readComponentState(RawFixture.class));

		this.rawComponentsServices.unregisterComponent(RawFixture.class);
	}

	public static class RawFixture {
		private static int constructions;

		public RawFixture() {
			constructions++;
		}
	}
}
