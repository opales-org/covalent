/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.managedcycle.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.tests.unit.managedcycle.AServices;
import org.opales.covalent.container.tests.unit.managedcycle.BServices;
import org.opales.covalent.container.tests.unit.managedcycle.BType;

/**
 * The other leg of the two-managed-component cycle fixture: provides
 * {@link BServices} and declares a {@code @UsedServices} port on
 * {@link AServices}, provided by {@link AImpl}, closing the cycle
 * (A -&gt; B -&gt; A).
 */
@Implementation
public class BImpl implements BType, BServices {

	@UsedServices
	private AServices useAServices;

	@Override
	public String fromB() {
		return "b";
	}

}
