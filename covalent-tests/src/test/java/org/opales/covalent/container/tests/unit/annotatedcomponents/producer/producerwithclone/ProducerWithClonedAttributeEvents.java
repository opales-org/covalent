/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithclone;

import org.opales.covalent.container.component.annotations.Cloned;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.tests.unit.data.mutable.MutableData;

@Events
public interface ProducerWithClonedAttributeEvents {

	void onEvents(@Cloned MutableData cloneableData1, @Cloned MutableData notCloneableData1, MutableData cloneableData2,
			MutableData notCloneableData2);
}
