/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.rawmanagedcircular.impl;

import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.unit.rawmanagedcircular.ManagedSideServices;
import org.opales.covalent.container.tests.unit.rawmanagedcircular.RawSideServices;

/**
 * Raw side of the raw&lt;-&gt;managed mutual dependency fixture: provides
 * {@link RawSideServices} imperatively and imperatively uses
 * {@link ManagedSideServices}, which is provided by the managed component
 * fixture instead of another raw component.
 */
public class CircularRawComponent implements RawSideServices {

	private final RawComponentsServices rcs;

	private ManagedSideServices useManagedSideServices;

	public CircularRawComponent(final RawComponentsServices rcs) {
		this.rcs = rcs;
	}

	@Activation
	public void activate() {
		this.rcs.provideServices(CircularRawComponent.class, RawSideServices.class);
		this.useManagedSideServices = this.rcs.useServices(ManagedSideServices.class);
	}

	@Passivation
	public void passivate() {
		this.rcs.unuseServices(ManagedSideServices.class);
		this.rcs.unprovideServices(CircularRawComponent.class, RawSideServices.class);
	}

	@Override
	public String ping() {
		return "ping";
	}

	public String callManaged() {
		return this.useManagedSideServices.pong();
	}

}
