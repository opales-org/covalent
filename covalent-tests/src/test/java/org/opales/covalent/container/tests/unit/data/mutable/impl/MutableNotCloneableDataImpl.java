/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.data.mutable.impl;

public class MutableNotCloneableDataImpl extends AbstractMutableData {

	public MutableNotCloneableDataImpl(final String state) {
		this.setState(state);
	}

}
