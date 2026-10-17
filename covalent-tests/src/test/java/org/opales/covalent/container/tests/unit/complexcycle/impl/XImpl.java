/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.complexcycle.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.tests.unit.complexcycle.XServices;
import org.opales.covalent.container.tests.unit.complexcycle.XType;
import org.opales.covalent.container.tests.unit.complexcycle.YServices;

/**
 * Entry point of the 3-node cycle X -&gt; Y -&gt; Z -&gt; X.
 */
@Implementation
public class XImpl implements XType, XServices {

	@UsedServices
	private YServices useYServices;

	@Override
	public String fromX() {
		return "x";
	}

}
