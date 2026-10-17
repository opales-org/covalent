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
import org.opales.covalent.container.tests.unit.annotatedcomponents.duplicateitemtype.impl.CounterItem;
import org.opales.covalent.container.tests.unit.annotatedcomponents.duplicateitemtype.impl.DuplicateItemTypeComponentImpl;

/**
 * An {@code @Implementation} cannot declare two {@code @Item} fields of the exact same item type
 * (for example one prey log and one predator log, both typed as the same log item class): items are
 * resolved by their declared class, not by field, so only one of the two fields could receive a live
 * instance. Such a declaration is rejected during introspection, at component deployment, with an
 * error that names the duplicated item type.
 */
public class DuplicateItemTypeFieldsTests {

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
			// Covalent.main is shared by the whole suite: the raw component registered by
			// setUp must not stay behind for the tests that count its components.
			this.rcs.unregisterComponent(DuplicateItemTypeFieldsTests.class);
		}
	}

	@Test
	public void twoItemFieldsOfTheSameTypeAreRejectedDuringIntrospection() {
		RuntimeException exception = Assert.assertThrows(RuntimeException.class,
				() -> this.cm.deployComponents(DuplicateItemTypeComponentImpl.class));

		Assert.assertTrue(
				"the rejection should name the duplicated item type, to help locate the offending field(s)",
				exception.getMessage().contains(CounterItem.class.getSimpleName()));
	}
}
