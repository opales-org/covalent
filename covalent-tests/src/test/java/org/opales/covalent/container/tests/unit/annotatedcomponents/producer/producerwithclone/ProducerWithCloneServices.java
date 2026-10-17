/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithclone;

import org.opales.covalent.container.component.annotations.Services;

@Services
public interface ProducerWithCloneServices {

	void fireProducerWithClonedEvents();

	void fireProducerWithClonedMethodEvents();

	void fireProducerWithClonedAttributeEvents();

	void fireProducerWithClonedThrowingEvents();

	void fireProducerWithClonedNoAccessibleCloneMethodEvents();

	boolean isMutatedCloneableOnClonedEvents();

	boolean isMutatedNotCloneableOnClonedEvents();

	boolean isMutatedCloneableOnClonedMethodEvents();

	boolean isMutatedNotCloneableOnClonedMethodEvents();

	boolean isMutatedCloneableOnClonedAttributeEvents();

	boolean isMutatedNotCloneableOnClonedAttributeEvents();

	boolean isMutatedCloneableOnNotClonedAttributeEvents();

	boolean isMutatedNotCloneableOnNotClonedAttributeEvents();

	void reset();

}
