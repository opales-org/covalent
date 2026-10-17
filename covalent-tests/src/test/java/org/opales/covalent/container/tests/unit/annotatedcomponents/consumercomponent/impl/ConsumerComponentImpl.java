/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.consumercomponent.impl;

import java.util.ArrayList;
import java.util.List;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumercomponent.ConsumerComponent;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumercomponent.ConsumerComponentServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent.ProducerSomeEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent.ProducerSomeEvents.ProducerSomeEventsSubscribe;

@Implementation
public class ConsumerComponentImpl implements ConsumerComponent, ConsumerComponentServices {

	@Item
	private ConsumerItemA consumerItemA;

	@Item
	private ConsumerItemB consumerItemB;

	@UsedEventsSubscribe
	private ProducerSomeEventsSubscribe useProducerSomeEventsSubscribe;

	private ProducerSomeEvents someEventsConsumer;

	private final List<String> messages = new ArrayList<>();

	@Activation
	public void activate() {
		this.someEventsConsumer = this.createSomeEventsConsumer();
		this.useProducerSomeEventsSubscribe.subscribe(this.someEventsConsumer);

		this.consumerItemA.subscribe();
		this.consumerItemB.subscribe();
	}

	@Passivation
	public void componentPassivate() {
		this.useProducerSomeEventsSubscribe.unsubscribe(this.someEventsConsumer);
		this.consumerItemB.unsubscribe();
		this.consumerItemA.unsubscribe();
	}

	private ProducerSomeEvents createSomeEventsConsumer() {
		return message -> {
			ConsumerComponentImpl.this.messages.add(message);
		};
	}

	@Override
	public List<String> getEventsReceivedByFirstItemA() {
		return this.consumerItemA.getMessages();
	}

	@Override
	public List<String> getEventsReceivedBySecondItemA() {
		return new ArrayList<>();// this.consumerItemA2.getMessages();
	}

	@Override
	public List<String> getEventsReceivedByItemBObserver() {
		return this.consumerItemB.getObserverMessages();
	}

	@Override
	public List<String> getEventsReceivedByItemB() {
		return this.consumerItemB.getItemMessages();
	}

	@Override
	public List<String> getEventsReceivedByImpl() {
		return this.messages;
	}
}
