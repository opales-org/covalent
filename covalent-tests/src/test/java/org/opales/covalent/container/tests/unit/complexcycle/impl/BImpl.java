/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.complexcycle.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.tests.unit.complexcycle.BServices;
import org.opales.covalent.container.tests.unit.complexcycle.BType;
import org.opales.covalent.container.tests.unit.complexcycle.XServices;

/**
 * Second link of the gray-path chain (A -&gt; B -&gt; X...), and also used
 * directly by {@code SiblingImpl} - a second, independent reference reached
 * only after this branch of the DFS has already fully processed B.
 */
@Implementation
public class BImpl implements BType, BServices {

	@UsedServices
	private XServices useXServices;

	@Override
	public String fromB() {
		return "b";
	}

}
