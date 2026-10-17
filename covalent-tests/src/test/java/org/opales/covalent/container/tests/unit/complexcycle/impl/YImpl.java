/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.complexcycle.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.tests.unit.complexcycle.YServices;
import org.opales.covalent.container.tests.unit.complexcycle.YType;
import org.opales.covalent.container.tests.unit.complexcycle.ZServices;

/**
 * Middle link of the 3-node cycle X -&gt; Y -&gt; Z -&gt; X.
 */
@Implementation
public class YImpl implements YType, YServices {

	@UsedServices
	private ZServices useZServices;

	@Override
	public String fromY() {
		return "y";
	}

}
