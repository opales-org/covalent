/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.composite.composedc.subc1;

import org.opales.covalent.container.component.annotations.Services;

@Services
public interface SubC1Services {
	void subC1Service(String message);

	void produceEvent(String message);

	boolean isDelegated();
}
