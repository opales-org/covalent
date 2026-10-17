/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.events.SubscribeParameters;
import org.opales.covalent.container.events.impl.ProducerSideNotifierInvocationHandler;
import org.opales.covalent.container.events.impl.SubscribeParametersImpl;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents.ProducerEventsSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents.ProducerWithSubscribeEventsFilter;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;

/**
 * Verifies the producer-side notifier and the events manager with a managed producer whose subscribe and
 * unsubscribe delegates throw: the failure is contained and the consumer stays unsubscribed, and a subscription
 * whose parameters no delegate overload accepts is reported to the caller.
 */
public class ManagedEventsDelegationFailuresTests {

	private CapturingLogHandler producerSideNotifierInvocationHandlerLogs;

	/** Component type of the producer whose delegates fail. */
	@Component
	public interface RefusingProducer {
	}

	/** Producer whose subscribe and unsubscribe delegates both throw. */
	@Implementation
	public static class RefusingProducerImpl
			implements RefusingProducer, ProducerServices, ProducerEventsSubscribe {

		@ProducedEvents
		private ProducerWithSubscribeEvents events;

		@Override
		public void subscribe(final ProducerWithSubscribeEvents observer) {
			throw new IllegalStateException("subscribe refused");
		}

		@Override
		public void subscribe(final ProducerWithSubscribeEvents observer,
				final ProducerWithSubscribeEventsFilter filter) {
			throw new IllegalStateException("filtered subscribe refused");
		}

		@Override
		public void unsubscribe(final ProducerWithSubscribeEvents observer) {
			throw new IllegalStateException("unsubscribe refused");
		}

		@Override
		public void fireEvent(final String value) {
			this.events.onEvent(value);
		}

		@Override
		public boolean checkDelegatedSubscribe() {
			return false;
		}

		@Override
		public boolean checkDelegatedUnsubscribe() {
			return false;
		}
	}

	/** Records the events it receives. */
	public static class Recorder implements ProducerWithSubscribeEvents {
		final List<String> received = Collections.synchronizedList(new ArrayList<>());

		@Override
		public void onEvent(final String message) {
			this.received.add(message);
		}
	}

	private CovalentContainer container;

	private RawComponentsServices rcs;

	private ProducerServices producer;

	@Before
	public void setUp() {
		this.producerSideNotifierInvocationHandlerLogs = CapturingLogHandler.install(ProducerSideNotifierInvocationHandler.class);
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
		this.container.deployComponents(RefusingProducerImpl.class);
		this.container.start();
		this.rcs = this.container.rcs();
		this.rcs.registerComponent(this);
		this.producer = this.rcs.useServices(RefusingProducer.class, ProducerServices.class);
	}

	@After
	public void tearDown() {
		this.container.dispose();
		this.producerSideNotifierInvocationHandlerLogs.restore();
	}

	@Test
	public void subscribeDelegateFailureLeavesTheConsumerUnsubscribed() {
		Recorder recorder = new Recorder();

		this.rcs.subscribe(ProducerWithSubscribeEvents.class, recorder);
		this.producer.fireEvent("not delivered");

		assertTrue(recorder.received.isEmpty());
		assertEquals(List.of("Subscribe delegate failed."),
				this.producerSideNotifierInvocationHandlerLogs.messages(Level.SEVERE));
	}

	@Test
	public void unsubscribeDelegateFailureStillStopsDeliveries() {
		Recorder recorder = new Recorder();
		this.rcs.subscribe(ProducerWithSubscribeEvents.class, recorder);

		this.rcs.unsubscribe(ProducerWithSubscribeEvents.class, recorder);
		this.producer.fireEvent("not delivered");

		assertTrue(recorder.received.isEmpty());
		assertEquals(List.of("Subscribe delegate failed.", "Unsubscribe delegate failed."),
				this.producerSideNotifierInvocationHandlerLogs.messages(Level.SEVERE));
	}

	@Test
	public void subscribeParametersNoDelegateOverloadAcceptsAreReportedToTheCaller() {
		SubscribeParameters parameters = new SubscribeParametersImpl();
		parameters.setEventFilter(new Object());
		Recorder recorder = new Recorder();

		RuntimeException failure = assertThrows(RuntimeException.class,
				() -> this.rcs.subscribe(ProducerWithSubscribeEvents.class, recorder, parameters));

		assertEquals("Exception while subscribing", failure.getMessage());
		assertTrue(failure.getCause() instanceof IllegalStateException);
	}
}
