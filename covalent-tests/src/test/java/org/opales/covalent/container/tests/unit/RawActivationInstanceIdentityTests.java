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
import org.opales.covalent.container.raw.RawComponentsServices;

/**
 * Activating a raw component by giving an instance that is not the one registered
 * under its component type is refused, instead of silently activating the
 * registered instance.
 */
public class RawActivationInstanceIdentityTests {

	public static class RawThing {
	}

	private CovalentContainer container;

	private RawComponentsServices rawComponents;

	@Before
	public void setUp() {
		this.container = Covalent.container();
		this.rawComponents = this.container.rcs();
	}

	@Test
	public void activatingWithAnotherInstanceThanTheRegisteredOneIsRefused() {
		RawThing registered = new RawThing();
		this.rawComponents.registerComponent(RawThing.class, registered, ComponentState.initialized);

		IllegalStateException failure = Assert.assertThrows(IllegalStateException.class,
				() -> this.rawComponents.activateComponent(RawThing.class, new RawThing()));

		Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("already registered"));
		Assert.assertFalse(this.container.lcs().isActivated(RawThing.class));
	}

	@Test
	public void activatingWithTheRegisteredInstanceActivatesIt() {
		RawThing registered = new RawThing();
		this.rawComponents.registerComponent(RawThing.class, registered, ComponentState.initialized);

		this.rawComponents.activateComponent(RawThing.class, registered);

		Assert.assertTrue(this.container.lcs().isActivated(RawThing.class));
	}
}
