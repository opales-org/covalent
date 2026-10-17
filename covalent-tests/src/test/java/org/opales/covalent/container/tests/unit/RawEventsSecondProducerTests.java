/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.events.EventsManagerServices;
import org.opales.covalent.container.exceptions.ValidationException;

/**
 * Each events interface has exactly one producer. When a second, distinct raw
 * component produces an events interface that is already produced, the first
 * producer is kept and the second one leaves no trace: at the strict level the
 * conflict is rejected, at the other levels it is only reported and the second
 * declaration gets the producer already in place, so that the events manager
 * is left in the state of a new one once everything is torn down.
 */
public class RawEventsSecondProducerTests {

	@Events
	public interface Ping {
		void onPing(String message);
	}

	public static class FirstProducer {
	}

	public static class SecondProducer {
	}

	public static class Recorder implements Ping {
		final List<String> received = new ArrayList<>();

		@Override
		public void onPing(final String message) {
			this.received.add(message);
		}
	}

	private CovalentContainer container;

	@Before
	public void setUp() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		this.container.start();
		this.container.rcs().registerComponent(this);
		this.container.rcs().registerComponent(new FirstProducer());
		this.container.rcs().registerComponent(new SecondProducer());
	}

	@After
	public void tearDown() {
		this.container.dispose();
	}

	/** The producers of the events manager among the two raw components of these tests (not the internal ones). */
	private Set<Class<?>> producers() {
		Map<Class<?>, Set<Class<?>>> all = this.container.rcs().useServices(EventsManagerServices.class)
				.getProducerToEvent();
		Set<Class<?>> producers = new HashSet<>(all.keySet());
		producers.retainAll(Set.of(FirstProducer.class, SecondProducer.class));
		return producers;
	}

	@Test
	public void aSecondProducerGetsTheFirstOneAndDoesNotBecomeAnotherProducer() {
		Ping first = this.container.rcs().produceEvents(FirstProducer.class, Ping.class);
		Recorder recorder = new Recorder();
		this.container.rcs().subscribe(Ping.class, recorder);

		Ping second = this.container.rcs().produceEvents(SecondProducer.class, Ping.class);

		assertNotNull(second);
		assertEquals(Set.of(FirstProducer.class), this.producers());
		second.onPing("through the kept producer");
		assertEquals(List.of("through the kept producer"), recorder.received);
		first.onPing("from the first");
		assertEquals(List.of("through the kept producer", "from the first"), recorder.received);
	}

	@Test
	public void tearingDownEitherProducerLeavesTheEventsManagerClean() {
		this.container.rcs().produceEvents(FirstProducer.class, Ping.class);
		this.container.rcs().produceEvents(SecondProducer.class, Ping.class);

		this.container.rcs().unregisterComponent(SecondProducer.class);

		assertEquals(Set.of(FirstProducer.class), this.producers());

		this.container.rcs().unregisterComponent(FirstProducer.class);

		assertTrue(this.producers().toString(), this.producers().isEmpty());
	}

	@Test
	public void theFirstProducerKeepsWorkingAfterTheRejectedOneWasUnregistered() {
		Ping first = this.container.rcs().produceEvents(FirstProducer.class, Ping.class);
		Recorder recorder = new Recorder();
		this.container.rcs().subscribe(Ping.class, recorder);
		this.container.rcs().produceEvents(SecondProducer.class, Ping.class);

		this.container.rcs().unregisterComponent(SecondProducer.class);
		first.onPing("still there");

		assertEquals(List.of("still there"), recorder.received);
	}

	@Test
	public void aSecondProducerIsRejectedAtTheStrictLevelAndLeavesNoTrace() {
		this.container.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		this.container.rcs().produceEvents(FirstProducer.class, Ping.class);

		assertThrows(ValidationException.class,
				() -> this.container.rcs().produceEvents(SecondProducer.class, Ping.class));

		assertEquals(Set.of(FirstProducer.class), this.producers());
	}
}
