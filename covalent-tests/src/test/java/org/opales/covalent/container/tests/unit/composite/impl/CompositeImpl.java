/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.composite.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.tests.unit.composite.Composite;
import org.opales.covalent.container.tests.unit.composite.CompositeServices;
import org.opales.covalent.container.tests.unit.composite.composeda.impl.ComposedAImpl;
import org.opales.covalent.container.tests.unit.composite.composedb.impl.ComposedBImpl;
import org.opales.covalent.container.tests.unit.composite.composedc.impl.ComposedCImpl;

@Implementation
public class CompositeImpl implements Composite, CompositeServices {

	@Implementation
	private ComposedAImpl composedA;

	@Implementation
	private ComposedBImpl composedB;

	@Implementation
	private ComposedCImpl composedC;

	@Override
	public void compositeService(final String message) {
		this.called = true;
	}

	boolean called;

}
