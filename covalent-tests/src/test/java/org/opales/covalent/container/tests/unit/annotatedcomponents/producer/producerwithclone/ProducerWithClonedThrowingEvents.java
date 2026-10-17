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
 * Events interface whose sole argument is {@link Cloneable} but always fails
 * to clone, used to exercise the strict dynamic verification failure path in
 * {@code ProducerSideNotifierInvocationHandler.replaceWithClones}.
 * </p>
 */
@Events
@Cloned
public interface ProducerWithClonedThrowingEvents {

	void onEvents(MutableData data);

}
