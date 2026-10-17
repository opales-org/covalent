/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected;

import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsSubscribe;

@Events
public interface FullyConnectedEvents {

	void onEvent(String string);

	@EventsSubscribe
	public interface FullyConnectedEventsSubscribe {
		public void subscribe(FullyConnectedEvents observer);

		public void unsubscribe(FullyConnectedEvents observer);
	}
}
