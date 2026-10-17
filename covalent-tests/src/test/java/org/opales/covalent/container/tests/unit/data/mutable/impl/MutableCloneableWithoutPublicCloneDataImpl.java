/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.data.mutable.impl;

/**
 * <p>
 * A {@link Cloneable} implementation that never overrides {@code clone()}.
 * {@code Object.clone()} is {@code protected}, so
 * {@code getClass().getMethod("clone")} on an instance of this class raises
 * {@code NoSuchMethodException} - used to exercise
 * {@code ProducerSideNotifierInvocationHandler.replaceWithClones}'s
 * {@code NoSuchMethodException} path, as opposed to
 * {@link MutableCloneableThrowingDataImpl}, whose {@code clone()} method is
 * reachable but fails when invoked.
 * </p>
 */
public class MutableCloneableWithoutPublicCloneDataImpl extends AbstractMutableData implements Cloneable {

	public MutableCloneableWithoutPublicCloneDataImpl(final String state) {
		this.setState(state);
	}

}
