/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.consumerwithfilteredsubscribe.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.ConsumerAdapter;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.ConsumerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.consumerwithfilteredsubscribe.ConsumerWithFilteredSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents.ProducerEventsSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents.ProducerWithSubscribeEventsFilter;

/**
 * Subscribes manually with a filter that lets through every message except those starting with
 * "hidden".
 */
@Implementation
public class ConsumerWithFilteredSubscribeImpl extends ConsumerAdapter
		implements ConsumerWithFilteredSubscribe, ConsumerServices, ProducerWithSubscribeEvents {

	@UsedEventsSubscribe
	private ProducerEventsSubscribe useProducerEventsSubscribe;

	@Override
	public void subscribe() {
		this.useProducerEventsSubscribe.subscribe(this, new ProducerWithSubscribeEventsFilter() {
			@Override
			public boolean filterEvent(final String message) {
				return message.startsWith("hidden");
			}
		});
	}

	@Override
	public void unsubscribe() {
		this.useProducerEventsSubscribe.unsubscribe(this);
	}
}
