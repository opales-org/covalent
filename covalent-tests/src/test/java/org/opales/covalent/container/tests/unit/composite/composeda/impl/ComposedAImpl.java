/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.composite.composeda.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.tests.unit.composite.composeda.ComposedA;
import org.opales.covalent.container.tests.unit.composite.composeda.ComposedAServices;
import org.opales.covalent.container.tests.unit.composite.composeda.suba1.impl.SubA1Impl;
import org.opales.covalent.container.tests.unit.composite.composeda.suba2.impl.SubA2Impl;

@Implementation
public class ComposedAImpl implements ComposedA, ComposedAServices {

	@Implementation
	private SubA1Impl subA1;

	@Implementation
	private SubA2Impl subA2;

	public boolean called;

	@Override
	public void composedAService(final String message) {
		this.called = true;
	}

}
