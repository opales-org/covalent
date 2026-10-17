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
import org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent.SomeEventsWithIdEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent.SomeEventsWithIdEvents.SomeEventsWithIdEventsSubscribe;

@Item
public class ConsumerItemA {

	@UsedEventsSubscribe
	private SomeEventsWithIdEventsSubscribe useSomeEventsWithIdEventsSubscribe;

	private final SomeEventsWithIdEvents someEventsWithIdConsumer;

	private final List<String> messages = new ArrayList<>();

	public ConsumerItemA() {
		this.someEventsWithIdConsumer = this.createSomeEventsWithIdConsumer();
	}

	void subscribe() {
		this.useSomeEventsWithIdEventsSubscribe.subscribe(ProducerId.idA, this.someEventsWithIdConsumer);
		this.useSomeEventsWithIdEventsSubscribe.subscribe(ProducerId.idB, this.someEventsWithIdConsumer);
	}

	void unsubscribe() {
		this.useSomeEventsWithIdEventsSubscribe.unsubscribe(ProducerId.idA, this.someEventsWithIdConsumer);
		this.useSomeEventsWithIdEventsSubscribe.unsubscribe(ProducerId.idB, this.someEventsWithIdConsumer);
	}

	SomeEventsWithIdEvents createSomeEventsWithIdConsumer() {
		return (id, message) -> {
			ConsumerItemA.this.messages.add(message);
		};
	}

	public List<String> getMessages() {
		return this.messages;
	}
}
