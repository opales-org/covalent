/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent;

import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsSubscribe;

@Events
public interface SomeEventsWithIdEvents {

	public void onEventWithId(ProducerId id, String message);

	@EventsSubscribe
	public interface SomeEventsWithIdEventsSubscribe {

		public void subscribe(ProducerId id, SomeEventsWithIdEvents observer);

		public void unsubscribe(ProducerId id, SomeEventsWithIdEvents observer);
	}
}
