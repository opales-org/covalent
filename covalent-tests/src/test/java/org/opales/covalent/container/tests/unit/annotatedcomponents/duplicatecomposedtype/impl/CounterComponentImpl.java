/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.duplicatecomposedtype.impl;

import org.opales.covalent.container.component.annotations.Implementation;

@Implementation
public class CounterComponentImpl implements CounterComponent {

	private int count;

	void increment() {
		this.count++;
	}

	int getCount() {
		return this.count;
	}
}
