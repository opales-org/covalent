/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.producer.simpleproducer.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerParameters;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.simpleproducer.SimpleProducer;

@Implementation
public class SimpleProducerImpl implements SimpleProducer, ProducerServices, ProducerParameters {
	@ProducedEvents
	private ProducerEvents produceProducerEvents;

	@Override
	public void fireEvent(final String value) {
		this.produceProducerEvents.onEvent(value);
	}

	@Override
	public boolean checkDelegatedSubscribe() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public boolean checkDelegatedUnsubscribe() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public void setParameter(final String parameter) {
		// ignored
	}

}
