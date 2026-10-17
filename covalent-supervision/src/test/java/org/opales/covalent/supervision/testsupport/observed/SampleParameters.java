/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed;

import org.opales.covalent.container.component.annotations.Parameters;

/** A trivial parameters interface, offered by {@code FlatParametersOfferorComponentImpl}. */
@Parameters
public interface SampleParameters {

	int getValue();

	void setValue(int value);
}
