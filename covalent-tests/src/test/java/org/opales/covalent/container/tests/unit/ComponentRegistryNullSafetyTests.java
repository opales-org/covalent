/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.opales.covalent.container.registry.impl.ComponentRegistryImpl;

/**
 * The component registry refuses a {@code null} component type, which its map would otherwise accept.
 */
public class ComponentRegistryNullSafetyTests {

	@Test
	public void registeringANullComponentTypeIsRefused() {
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> new ComponentRegistryImpl().registerComponent(null, new Object()));

		assertTrue(failure.getMessage(), failure.getMessage().contains("componentType"));
	}

	@Test
	public void unregisteringANullComponentTypeIsRefused() {
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> new ComponentRegistryImpl().unregisterComponent(null));

		assertTrue(failure.getMessage(), failure.getMessage().contains("componentType"));
	}
}
