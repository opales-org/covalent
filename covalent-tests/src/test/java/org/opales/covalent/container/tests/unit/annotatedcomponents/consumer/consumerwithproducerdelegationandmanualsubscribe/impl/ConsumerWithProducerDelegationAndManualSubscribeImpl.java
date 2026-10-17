/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.consumerwithproducerdelegationandmanualsubscribe.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.ConsumerAdapter;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.ConsumerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.consumerwithproducerdelegationandmanualsubscribe.ConsumerWithProducerDelegationAndManualSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents;

@Implementation
public class ConsumerWithProducerDelegationAndManualSubscribeImpl extends ConsumerAdapter
		implements ConsumerWithProducerDelegationAndManualSubscribe, ConsumerServices, ProducerWithSubscribeEvents {

	@UsedEventsSubscribe
	private ProducerEventsSubscribe useProducerEventsSubscribe;

	@Override
	public void subscribe() {
		this.useProducerEventsSubscribe.subscribe(this);
	}

	@Override
	public void unsubscribe() {
		this.useProducerEventsSubscribe.unsubscribe(this);
	}

}
