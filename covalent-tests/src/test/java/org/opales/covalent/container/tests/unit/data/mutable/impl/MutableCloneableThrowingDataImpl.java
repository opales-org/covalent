/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.data.mutable.impl;

/**
 * <p>
 * A {@link Cloneable} implementation whose {@code clone()} method always
 * fails. Used to exercise the strict dynamic verification path when cloning
 * an event argument raises an exception, as opposed to the argument simply
 * not implementing {@link Cloneable}.
 * </p>
 */
public class MutableCloneableThrowingDataImpl extends AbstractMutableData implements Cloneable {

	public MutableCloneableThrowingDataImpl(final String state) {
		this.setState(state);
	}

	@Override
	public MutableCloneableThrowingDataImpl clone() {
		throw new IllegalStateException("clone failure");
	}
}
