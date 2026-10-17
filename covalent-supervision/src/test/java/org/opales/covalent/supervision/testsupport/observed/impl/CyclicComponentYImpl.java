/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.supervision.testsupport.observed.CyclicComponentY;
import org.opales.covalent.supervision.testsupport.observed.SampleServicesA;
import org.opales.covalent.supervision.testsupport.observed.SampleServicesB;

@Implementation
public class CyclicComponentYImpl implements CyclicComponentY, SampleServicesB {

	@UsedServices
	private SampleServicesA sampleServicesA;

	@Override
	public void pingB() {
	}
}
