/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.rawcomponents.componenta;

public interface ComponentAEvents {

	void onCreate(String data);
	
	void onUpdate(String data);
	
	void onDelete(String data);
	
	void onEmpty();
}
