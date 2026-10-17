/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.data.mutable.impl;

public class MutableCloneableDataImpl extends AbstractMutableData implements Cloneable {
	public MutableCloneableDataImpl(final String state) {
		this.setState(state);
	}

	@Override
	public MutableCloneableDataImpl clone() {
		MutableCloneableDataImpl clone = new MutableCloneableDataImpl(this.state);
		return clone;
	}
}
