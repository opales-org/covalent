/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.rawmanagedcircular.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.tests.unit.rawmanagedcircular.CircularManagedType;
import org.opales.covalent.container.tests.unit.rawmanagedcircular.ManagedSideServices;
import org.opales.covalent.container.tests.unit.rawmanagedcircular.RawSideServices;

/**
 * Managed side of the raw&lt;-&gt;managed mutual dependency fixture: provides
 * {@link ManagedSideServices} and depends, through a declarative
 * {@code @UsedServices} port, on {@link RawSideServices}, which is provided by
 * a raw component instead of another managed component.
 */
@Implementation
public class CircularManagedImpl implements CircularManagedType, ManagedSideServices {

	@UsedServices
	private RawSideServices useRawSideServices;

	@Override
	public String pong() {
		return "pong";
	}

	public String callRaw() {
		return this.useRawSideServices.ping();
	}

}
