/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.composite.composedc.subc3.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.tests.unit.composite.composedc.subc3.SubC3;
import org.opales.covalent.container.tests.unit.composite.composedc.subc3.SubC3Services;

@Implementation
public class SubC3Impl implements SubC3, SubC3Services {

	public boolean called;

	@Override
	public void subC3Service(final String message) {
		this.called = true;
	}

}
