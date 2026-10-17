/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.duplicateitemtype.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.tests.unit.annotatedcomponents.duplicateitemtype.DuplicateItemTypeComponent;
import org.opales.covalent.container.tests.unit.annotatedcomponents.duplicateitemtype.DuplicateItemTypeComponentServices;

/**
 * Declares two {@code @Item} fields of the exact same item type (CounterItem), as used by
 * DuplicateItemTypeFieldsTests: such a declaration is rejected, because Covalent resolves an
 * {@code @Item} field by the item's declared class.
 */
@Implementation
public class DuplicateItemTypeComponentImpl implements DuplicateItemTypeComponent, DuplicateItemTypeComponentServices {

	@Item
	private CounterItem firstCounter;

	@Item
	private CounterItem secondCounter;

	@Override
	public void incrementFirst() {
		this.firstCounter.increment();
	}

	@Override
	public void incrementSecond() {
		this.secondCounter.increment();
	}

	@Override
	public int getFirstCount() {
		return this.firstCounter.getCount();
	}

	@Override
	public int getSecondCount() {
		return this.secondCounter.getCount();
	}
}
