/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.componentformetamodeltest;

import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsSubscribe;

@Events
public interface ComponentForMetamodelTestEvents {
	public void onEvent(Object data);
	
	@EventsSubscribe
	public interface ComponentForMetamodelTestEventsSubscribe {
		public void subscribe(ComponentForMetamodelTestEvents someEvent);
		
		public void unsubscribe(ComponentForMetamodelTestEvents someEvent);
	}
}
