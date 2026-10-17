/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.composite.composeda.suba1.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.tests.unit.composite.composeda.suba1.SubA1;
import org.opales.covalent.container.tests.unit.composite.composeda.suba1.SubA1Services;

@Implementation
public class SubA1Impl implements SubA1, SubA1Services {

	public boolean called;

	@Override
	public void subA1Service(final String message) {
		this.called = true;
	}

	@Override
	public boolean wasCalled() {
		return this.called;
	}
}
