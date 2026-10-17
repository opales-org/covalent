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
import org.opales.covalent.container.raw.RawComponentsParameters;
import org.opales.covalent.container.raw.RawComponentsServices;

/**
 * Registering a raw component with a null component type, instance or state to
 * reach is refused with an {@link IllegalArgumentException} naming the argument,
 * and so is a null default state to reach.
 */
public class RawRegistrationNullArgumentsTests {

	public static class RawThing {
	}

	private RawComponentsServices rawComponents;

	@Before
	public void setUp() {
		CovalentContainer container = Covalent.container();
		this.rawComponents = container.rcs();
	}

	private static void assertRefused(final Runnable call, final String argumentName) {
		IllegalArgumentException failure = Assert.assertThrows(IllegalArgumentException.class, call::run);
		Assert.assertTrue(failure.getMessage(), failure.getMessage().contains(argumentName));
	}

	@Test
	public void aNullInstanceIsRefused() {
		assertRefused(() -> this.rawComponents.registerComponent(RawThing.class, null, ComponentState.instantiated),
				"componentInstance");
	}

	@Test
	public void aNullStateToReachIsRefused() {
		assertRefused(() -> this.rawComponents.registerComponent(RawThing.class, new RawThing(), null),
				"stateToReach");
	}

	@Test
	public void aNullComponentTypeIsRefused() {
		assertRefused(() -> this.rawComponents.registerComponent(null, new RawThing(), ComponentState.instantiated),
				"componentType");
	}

	@Test
	public void aNullStateToReachIsRefusedWhenRegisteringWithAnInferredType() {
		assertRefused(() -> this.rawComponents.registerComponent(new RawThing(), null), "stateToReach");
	}

	@Test
	public void aNullDefaultStateToReachIsRefused() {
		this.rawComponents.registerComponent(this);
		RawComponentsParameters parameters = this.rawComponents.accessParameters(RawComponentsParameters.class);

		assertRefused(() -> parameters.setDefaultStateToReach(null), "defaultStateToReach");
	}
}
