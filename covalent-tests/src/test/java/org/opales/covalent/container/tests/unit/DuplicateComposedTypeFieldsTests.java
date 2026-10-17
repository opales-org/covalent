/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.duplicatecomposedtype.impl.CounterComponent;
import org.opales.covalent.container.tests.unit.annotatedcomponents.duplicatecomposedtype.impl.DuplicateComposedTypeComponentImpl;

/**
 * Composition counterpart of {@link DuplicateItemTypeFieldsTests}: an {@code @Implementation} cannot
 * declare two {@code @ComposedComponent} fields of the exact same implementation type. The
 * declaration is rejected during the parent's introspection, before any composed child is deployed,
 * so nothing is left half-deployed: the composed child type is not deployed afterwards.
 */
public class DuplicateComposedTypeFieldsTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	@Before
	public void setUp() {
		this.rcs.registerComponent(this);
		this.cm.getParameters().setSynchronousEvents(true);
	}

	@After
	public void tearDown() {
		if (this.cm.isStarted()) {
			// stopping clears the raw components registered by setUp
			this.cm.stop();
		} else {
			// Covalent.main is shared by every test of the JVM: the raw component
			// registered by setUp must not stay behind for the tests that count
			// the components of the main container (CovalentContainerContainerizedTests).
			this.rcs.unregisterComponent(DuplicateComposedTypeFieldsTests.class);
		}
	}

	@Test
	public void twoComposedFieldsOfTheSameTypeAreRejectedDuringIntrospectionWithoutPartialDeployment() {
		Assert.assertThrows(RuntimeException.class,
				() -> this.cm.deployComponents(DuplicateComposedTypeComponentImpl.class));

		Assert.assertFalse(
				"a rejected declaration must not leave its first composed child type partially deployed - "
						+ "the whole parent declaration should be rejected at introspection, before any of its "
						+ "composed children are deployed",
				this.cm.lcs().isDeployed(CounterComponent.class));
	}
}
