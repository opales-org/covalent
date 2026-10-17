/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.composite.composedc.subc1;

import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsSubscribe;

@Events
public interface SubC1Events {

	void onEvent(String message);

	@EventsSubscribe
	interface SubC1EventsSubscribe {

		void subscribe(SubC1Events observer);

		void unsubscribe(SubC1Events observer);

	}
}
