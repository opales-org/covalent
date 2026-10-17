/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.composite.composeda.suba1;

import org.opales.covalent.container.component.annotations.Services;

@Services
public interface SubA1Services {
	void subA1Service(String message);

	boolean wasCalled();
}
