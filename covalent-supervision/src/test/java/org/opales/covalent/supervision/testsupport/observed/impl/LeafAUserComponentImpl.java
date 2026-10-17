/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.supervision.testsupport.observed.LeafAUserComponent;
import org.opales.covalent.supervision.testsupport.observed.SampleServicesA;
import org.opales.covalent.supervision.testsupport.observed.SampleServicesB;

/**
 * Composed inside {@code CompositeAComponentImpl}. Uses {@link SampleServicesA} - provided by
 * its own composite sibling {@code LeafAProviderComponentImpl} - and {@link SampleServicesB} -
 * provided by a leaf of the separate {@code CompositeBComponentImpl} - so that the resulting
 * topology carries one same-composite service edge and one cross-composite service edge from
 * the same component type.
 */
@Implementation
public class LeafAUserComponentImpl implements LeafAUserComponent {

	@UsedServices
	private SampleServicesA sampleServicesA;

	@UsedServices
	private SampleServicesB sampleServicesB;
}
