/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.supervision.testsupport.observed.FlatParametersOfferorComponent;
import org.opales.covalent.supervision.testsupport.observed.SampleParameters;

@Implementation
public class FlatParametersOfferorComponentImpl
		implements FlatParametersOfferorComponent, SampleParameters {

	private int value;

	@Override
	public int getValue() {
		return this.value;
	}

	@Override
	public void setValue(final int value) {
		this.value = value;
	}
}
