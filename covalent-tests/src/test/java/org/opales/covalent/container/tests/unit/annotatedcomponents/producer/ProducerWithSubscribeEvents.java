/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.producer;

import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsFilter;
import org.opales.covalent.container.component.annotations.EventsSubscribe;

@Events
public interface ProducerWithSubscribeEvents {
	public void onEvent(String message);

	@EventsSubscribe
	public interface ProducerEventsSubscribe {

		public void subscribe(ProducerWithSubscribeEvents observer);

		public void subscribe(ProducerWithSubscribeEvents observer, ProducerWithSubscribeEventsFilter filter);

		public void unsubscribe(ProducerWithSubscribeEvents observer);
	}

	@EventsFilter
	public interface ProducerWithSubscribeEventsFilter {
		public boolean filterEvent(final String message);
	}
}
