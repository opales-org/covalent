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

/**
 * The component state queries refuse a null component type.
 */
public class LifecycleNullComponentTypeQueryTests {

	private CovalentContainer container;

	@Before
	public void setUp() {
		this.container = Covalent.container();
	}

	@Test
	public void askingWhetherANullComponentTypeIsRemovedIsRefused() {
		Assert.assertThrows(IllegalArgumentException.class, () -> this.container.lcs().isRemoved(null));
	}

	@Test
	public void askingWhetherANullComponentTypeIsUndeployedIsRefused() {
		Assert.assertThrows(IllegalArgumentException.class, () -> this.container.lcs().isUndeployed(null));
	}
}
