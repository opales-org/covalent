/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.supervision.testsupport.observed.CyclicComponentX;
import org.opales.covalent.supervision.testsupport.observed.SampleServicesA;
import org.opales.covalent.supervision.testsupport.observed.SampleServicesB;

@Implementation
public class CyclicComponentXImpl implements CyclicComponentX, SampleServicesA {

	@UsedServices
	private SampleServicesB sampleServicesB;

	@Override
	public void pingA() {
	}
}
