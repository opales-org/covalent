/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithclone;

import org.opales.covalent.container.component.annotations.Cloned;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.tests.unit.data.mutable.MutableData;

/**
 * <p>
 * Events interface whose sole argument is {@link Cloneable} but has no public
 * {@code clone()} method, used to exercise the
 * {@code NoSuchMethodException} path in
 * {@code ProducerSideNotifierInvocationHandler.replaceWithClones} - as
 * opposed to {@link ProducerWithClonedThrowingEvents}, whose argument's
 * {@code clone()} method is reachable but fails when invoked.
 * </p>
 */
@Events
@Cloned
public interface ProducerWithClonedNoAccessibleCloneMethodEvents {

	void onEvents(MutableData data);

}
