/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.events;

import org.opales.covalent.container.tests.unit.data.ADisplayData;

public interface AComponentEvents {
	
	void onNotification(ADisplayData value);
	
	void onNotification(ADisplayData value, boolean aValue);
	
	public interface AComponentEventsFilter {
		
		boolean filterNotification(ADisplayData value);
		
		boolean filterNotification(ADisplayData value, boolean aValue);
	}
}
