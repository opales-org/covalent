/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.consumerwithgenericsubscribe.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.events.GenericSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.ConsumerAdapter;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.consumerwithgenericsubscribe.ConsumerWithGenericSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerEvents;

@Implementation
public class ConsumerWithGenericSubscribeImpl extends ConsumerAdapter
		implements ConsumerWithGenericSubscribe, ProducerEvents {

	@UsedEventsSubscribe
	GenericSubscribe useGenericSubscribe;

	@Override
	public void subscribe() {
		this.useGenericSubscribe.subscribe(this);
	}

}
