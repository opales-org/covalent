/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.managedcycle.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.tests.unit.managedcycle.AServices;
import org.opales.covalent.container.tests.unit.managedcycle.AType;
import org.opales.covalent.container.tests.unit.managedcycle.BServices;

/**
 * One leg of the two-managed-component cycle fixture: provides
 * {@link AServices} and declares a {@code @UsedServices} port on
 * {@link BServices}, provided by {@link BImpl} - which in turn depends back
 * on {@link AServices}, closing the cycle.
 */
@Implementation
public class AImpl implements AType, AServices {

	@UsedServices
	private BServices useBServices;

	@Override
	public String fromA() {
		return "a";
	}

}
