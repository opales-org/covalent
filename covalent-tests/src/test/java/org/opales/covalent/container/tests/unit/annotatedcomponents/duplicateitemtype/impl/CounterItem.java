/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.duplicateitemtype.impl;

import org.opales.covalent.container.component.annotations.Item;

/**
 * A plain, stateful item type meant to be held by two independent fields
 * of the same owning component - see DuplicateItemTypeComponentImpl,
 * whose two {@code @Item} fields (firstCounter, secondCounter) are both
 * declared with this exact same type.
 */
@Item
public class CounterItem {

	private int count;

	void increment() {
		this.count++;
	}

	int getCount() {
		return this.count;
	}
}
