/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.data.mutable.impl;

import org.opales.covalent.container.tests.unit.data.mutable.MutableData;

public abstract class AbstractMutableData implements MutableData {
	@Override
	public void setState(final String state) {
		this.state = state;
	}

	@Override
	public String getState() {
		return this.state;
	}

	protected String state;

}
