/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.supervision.testsupport.observed.LeafBProviderComponent;
import org.opales.covalent.supervision.testsupport.observed.SampleServicesB;

@Implementation
public class LeafBProviderComponentImpl implements LeafBProviderComponent, SampleServicesB {

	public boolean called;

	@Override
	public void pingB() {
		this.called = true;
	}
}
