/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.complexcycle.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.tests.unit.complexcycle.AServices;
import org.opales.covalent.container.tests.unit.complexcycle.AType;
import org.opales.covalent.container.tests.unit.complexcycle.BServices;

/**
 * Entry point of the long gray-path chain: A -&gt; B -&gt; X -&gt; Y -&gt; Z
 * -&gt; X. Not part of the cycle itself.
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
