/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.producer;

import org.opales.covalent.container.component.annotations.Events;

@Events
public interface ProducerEvents {
	public void onEvent(final String message);

}
