/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.consumercomponent.impl;

import java.util.ArrayList;
import java.util.List;

import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent.ProducerId;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent.ProducerSomeEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent.SomeEventsWithIdEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent.SomeEventsWithIdEvents.SomeEventsWithIdEventsSubscribe;

@Item
public class ConsumerItemB implements ProducerSomeEvents {

	@UsedEventsSubscribe
	private SomeEventsWithIdEventsSubscribe useSomeEventsWithIdEventsSubscribe;

	@UsedEventsSubscribe
	private ProducerSomeEventsSubscribe useProducerSomeEventsSubscribe;

	private final SomeEventsWithIdEvents someEventsWithIdConsumer;

	private final List<String> observerMessages = new ArrayList<>();

	private final List<String> itemMessages = new ArrayList<>();

	public ConsumerItemB() {
		this.someEventsWithIdConsumer = this.createSomeEventsWithIdConsumer();
	}

	protected void subscribe() {
		this.useSomeEventsWithIdEventsSubscribe.subscribe(ProducerId.idB, this.someEventsWithIdConsumer);
		this.useProducerSomeEventsSubscribe.subscribe(this);
	}

	void unsubscribe() {
		this.useSomeEventsWithIdEventsSubscribe.unsubscribe(ProducerId.idB, this.someEventsWithIdConsumer);
		this.useProducerSomeEventsSubscribe.unsubscribe(this);
	}

	SomeEventsWithIdEvents createSomeEventsWithIdConsumer() {
		return (id, message) -> {
			ConsumerItemB.this.observerMessages.add(message);
		};
	}

	public List<String> getObserverMessages() {
		return this.observerMessages;
	}

	public List<String> getItemMessages() {
		return this.itemMessages;
	}

	@Override
	public void onEvent(final String message) {
		this.itemMessages.add(message);
	}
}
