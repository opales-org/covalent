/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.composite.composedb.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.tests.unit.composite.composedb.ComposedB;
import org.opales.covalent.container.tests.unit.composite.composedb.ComposedBServices;
import org.opales.covalent.container.tests.unit.composite.composedb.subb1.impl.SubB1Impl;

@Implementation
public class ComposedBImpl implements ComposedB, ComposedBServices {

	public boolean called;

	@Implementation
	private SubB1Impl subB1;

	@Override
	public void composedBService(final String message) {
		this.called = true;
	}

}
