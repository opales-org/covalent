/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.complexcycle.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.tests.unit.complexcycle.XServices;
import org.opales.covalent.container.tests.unit.complexcycle.ZServices;
import org.opales.covalent.container.tests.unit.complexcycle.ZType;

/**
 * Closes the 3-node cycle back to {@link XServices} (Z -&gt; X), the
 * back-edge that a gray-node revisit must catch.
 */
@Implementation
public class ZImpl implements ZType, ZServices {

	@UsedServices
	private XServices useXServices;

	@Override
	public String fromZ() {
		return "z";
	}

}
