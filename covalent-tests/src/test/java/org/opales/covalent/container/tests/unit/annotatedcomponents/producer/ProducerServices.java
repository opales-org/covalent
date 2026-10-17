/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.producer;

import org.opales.covalent.container.component.annotations.Services;

@Services
public interface ProducerServices {
	public void fireEvent(final String value);

	public boolean checkDelegatedSubscribe();

	public boolean checkDelegatedUnsubscribe();
}
