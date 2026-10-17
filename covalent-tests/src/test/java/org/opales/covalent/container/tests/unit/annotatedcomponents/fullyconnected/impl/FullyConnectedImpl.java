/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Parameters;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.FullyConnected;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.FullyConnectedEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.FullyConnectedEvents.FullyConnectedEventsSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.FullyConnectedParameters;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.FullyConnectedServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithdelegation.ProducerWithSubscribeAndDelegationParameters;;

@Implementation
public class FullyConnectedImpl implements FullyConnected, FullyConnectedServices, ProducerWithSubscribeEvents,
		FullyConnectedEventsSubscribe, FullyConnectedParameters {

	@ProducedEvents
	FullyConnectedEvents produceFullyConnectedEvents;

	@UsedEventsSubscribe
	ProducerEventsSubscribe useProducerEventsSubscribe;

	// testing @Services in used services field
	@Services
	ProducerServices useProducerServices;

	@Parameters
	private final ProducerWithSubscribeAndDelegationParameters accessProducerWithSubscribeAndDelegationParameters = null;

	private boolean eventReceived = false;

	private boolean subscribeReceived = false;

	@Override
	public void onEvent(final String message) {
		this.eventReceived = true;
	}

	@Override
	public boolean checkProvidedServices() {
		return true;
	}

	@Override
	public boolean checkUsedServices() {
		boolean result = false;
		try {
			@SuppressWarnings("unused")
			boolean ignored = this.useProducerServices.checkDelegatedSubscribe();
			result = true;
		} catch (Throwable t) {
			result = false;
		}
		return result;
	}

	@Override
	public boolean checkProducedEvents() {
		boolean result = false;
		try {
			this.produceFullyConnectedEvents.onEvent("event");
			result = true;
		} catch (Throwable t) {
			result = false;
			t.printStackTrace();
		}
		return result;
	}

	@Override
	public boolean checkConsumedEvents() {
		return this.eventReceived;
	}

	@Override
	public boolean checkProvidedSubscribe() {
		return this.subscribeReceived;
	}

	@Override
	public boolean checkUsedSubscribe() {
		boolean result = false;
		try {
			this.useProducerEventsSubscribe.subscribe(this);
			result = true;
		} catch (Throwable t) {
			result = false;
		}
		return result;
	}

	@Override
	public void subscribe(final FullyConnectedEvents observer) {
		this.subscribeReceived = true;
	}

	@Override
	public void unsubscribe(final FullyConnectedEvents observer) {
		// currently ignored
	}

	@Override
	public void reset() {
		this.eventReceived = false;
		this.subscribeReceived = false;
	}

	public boolean checkAccessedParameters() {
		boolean result = false;
		try {
			this.accessProducerWithSubscribeAndDelegationParameters.setParameters("parameters");
			result = true;
		} catch (Throwable t) {
			result = false;
		}
		return result;
	}

	@Override
	public boolean getParameter() {
		return true;
	}

}
