/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.consumerwithmanualsubscribe.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.ConsumerAdapter;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.ConsumerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.consumerwithmanualsubscribe.ConsumerWithManualSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents;

@Implementation
public class ConsumerWithManualSubscribeImpl extends ConsumerAdapter
		implements ConsumerServices, ProducerWithSubscribeEvents, ConsumerWithManualSubscribe {

	@UsedEventsSubscribe
	private ProducerEventsSubscribe useProducerEventsSubscribe;

	private ProducerWithSubscribeEventsMock eventsConsumer = null;

	@Activation
	public void activate() {
		this.useProducerEventsSubscribe.subscribe(this);

		this.eventsConsumer = new ProducerWithSubscribeEventsMock();
		this.useProducerEventsSubscribe.subscribe(this.eventsConsumer);
	}

	public class ProducerWithSubscribeEventsMock implements ProducerWithSubscribeEvents {

		@Override
		public void onEvent(final String message) {
			this.received = true;
		}

		private boolean received = false;
	}

	@Override
	public boolean checkCustomConsumerEventReceived() {
		return this.eventsConsumer.received;
	}

	@Override
	public void subscribe() {
		this.useProducerEventsSubscribe.subscribe(this);
	}
}
