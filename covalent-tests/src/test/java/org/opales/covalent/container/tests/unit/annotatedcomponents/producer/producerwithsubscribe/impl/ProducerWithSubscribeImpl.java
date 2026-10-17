/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithsubscribe.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithsubscribe.ProducerWithSubscribe;

@Implementation
public class ProducerWithSubscribeImpl implements ProducerServices, ProducerWithSubscribe {

	@ProducedEvents
	private ProducerWithSubscribeEvents produceProducerWithSubscribeEvents;

	@Override
	public void fireEvent(final String value) {
		this.produceProducerWithSubscribeEvents.onEvent(value);
	}

	@Override
	public boolean checkDelegatedSubscribe() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public boolean checkDelegatedUnsubscribe() {
		throw new UnsupportedOperationException("Not implemented");
	}

}
