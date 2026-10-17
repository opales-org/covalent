/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithdelegation.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents.ProducerEventsSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents.ProducerWithSubscribeEventsFilter;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithdelegation.ProducerWithSubscribeAndDelegation;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithdelegation.ProducerWithSubscribeAndDelegationParameters;

@Implementation
public class ProducerWithSubscribeAndDelegationImpl implements ProducerWithSubscribeAndDelegation, ProducerServices,
		ProducerEventsSubscribe, ProducerWithSubscribeAndDelegationParameters {

	@ProducedEvents
	private ProducerWithSubscribeEvents produceProducerWithSubscribeEvents;

	@Override
	public void subscribe(final ProducerWithSubscribeEvents subscriber) {
		subscriber.onEvent("subscribe");
		this.delegatedSubscribe = true;
	}

	@Override
	public void unsubscribe(final ProducerWithSubscribeEvents subscriber) {
		subscriber.onEvent("unsubscribe");
		this.delegatedUnsubscribe = true;
	}

	@Override
	public void fireEvent(final String message) {
		this.produceProducerWithSubscribeEvents.onEvent(message);
	}

	@Override
	public boolean checkDelegatedSubscribe() {
		return this.delegatedSubscribe;
	}

	@Override
	public boolean checkDelegatedUnsubscribe() {
		return this.delegatedUnsubscribe;
	}

	@Override
	public void subscribe(final ProducerWithSubscribeEvents observer, final ProducerWithSubscribeEventsFilter filter) {
		// not used
	}

	@Override
	public void setParameters(final String parameter) {
		this.parameterSet = true;
	}

	private boolean delegatedSubscribe;

	private boolean delegatedUnsubscribe;

	private boolean parameterSet;

}
