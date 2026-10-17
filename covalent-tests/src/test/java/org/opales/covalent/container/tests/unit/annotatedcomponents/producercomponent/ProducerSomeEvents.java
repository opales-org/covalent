/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent;

import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsSubscribe;

@Events
public interface ProducerSomeEvents {

	public void onEvent(String message);

	@EventsSubscribe
	public interface ProducerSomeEventsSubscribe {

		public void subscribe(ProducerSomeEvents observer);

		public void unsubscribe(ProducerSomeEvents observer);
	}
}
