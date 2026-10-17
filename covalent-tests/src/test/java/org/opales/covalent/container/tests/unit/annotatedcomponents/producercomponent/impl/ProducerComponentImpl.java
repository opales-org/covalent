/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent.impl;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Assert;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.events.impl.ConsumerInvocationHandler;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent.ProducerComponent;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent.ProducerComponentServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent.ProducerId;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent.ProducerSomeEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent.ProducerSomeEvents.ProducerSomeEventsSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent.SomeEventsWithIdEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent.SomeEventsWithIdEvents.SomeEventsWithIdEventsSubscribe;

@Implementation
public class ProducerComponentImpl implements ProducerComponent, ProducerComponentServices, ProducerSomeEventsSubscribe,
		SomeEventsWithIdEventsSubscribe {

	@ProducedEvents
	protected ProducerSomeEvents produceProducerSomeEvents;

	@ProducedEvents
	protected SomeEventsWithIdEvents produceSomeEventsWithIdEvents;

	private final Map<ProducerId, List<SomeEventsWithIdEvents>> consumersById = new TreeMap<>();

	@Activation
	public void activate() {
		try {
			this.produceProducerSomeEvents.onEvent("activate");
		} catch (Throwable t) {
			Assert.fail();
		}
	}

	@Passivation
	public void componentPassivate() {
		// is received by consumers because new consumer cannot subscribe
		// after pre-passivate, but producer proxy is still functional
		// until preRemove
		// anyway, this event is not received during test, it is
		// fired during tearDown
		this.produceProducerSomeEvents.onEvent("passivate");
	}

	@Override
	public void produceEvents() {
		for (final ProducerId filter : this.consumersById.keySet()) {
			final List<SomeEventsWithIdEvents> observers = this.consumersById.get(filter);
			for (final SomeEventsWithIdEvents observer : observers) {
				observer.onEventWithId(filter, filter.toString());

			}
		}
		this.produceProducerSomeEvents.onEvent("produceEvents");
	}

	@Override
	public void service() {
		this.produceProducerSomeEvents.onEvent("service");
	}

	@Override
	public void subscribe(final ProducerSomeEvents observer) {
		// observer should receive message twice
		String message = this.createSubscribeMessage("subscribe_", observer);
		observer.onEvent(message);

		message += "#Global";
		this.produceProducerSomeEvents.onEvent(message);
	}

	@Override
	public void unsubscribe(final ProducerSomeEvents observer) {
		this.produceProducerSomeEvents.onEvent("unsubscribe");
	}

	@Override
	public void subscribe(final ProducerId id, final SomeEventsWithIdEvents observer) {
		final List<SomeEventsWithIdEvents> consumers = this.consumersById.getOrDefault(id, new ArrayList<>());
		consumers.add(observer);
		this.consumersById.put(id, consumers);
		observer.onEventWithId(id, this.createSubscribeMessage("subscribe_" + id + "_", observer));
	}

	private String createSubscribeMessage(final String prefix, final Object observer) {
		ConsumerInvocationHandler<?> ih = (ConsumerInvocationHandler<?>) Proxy.getInvocationHandler(observer);
		Object consumer = ih.getConsumer();
		String shortName = consumer.getClass().getSimpleName();
		if (shortName.indexOf("$") > 0) {
			shortName = shortName.substring(0, shortName.indexOf("$") + 1);
		}
		String message = prefix + shortName;
		return message;
	}

	@Override
	public void unsubscribe(final ProducerId id, final SomeEventsWithIdEvents observer) {
		final List<SomeEventsWithIdEvents> consumers = this.consumersById.getOrDefault(id, new ArrayList<>());
		if (!consumers.contains(observer)) {
			throw new IllegalArgumentException("Unknown observer: " + observer);
		}
		consumers.remove(observer);
	}

}
