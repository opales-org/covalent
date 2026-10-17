/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed;

import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsSubscribe;

/** A trivial events interface, produced by {@code FlatEventsProducerComponentImpl}. */
@Events
public interface SampleEvents {

	void fired();

	@EventsSubscribe
	interface SampleEventsSubscribe {

		void subscribe(SampleEvents consumer);

		void unsubscribe(SampleEvents consumer);
	}
}
