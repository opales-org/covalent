/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.events.EventsManagerParameters;
import org.opales.covalent.container.events.EventsWorkersServices;
import org.opales.covalent.container.exceptions.NamingException;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.unit.events.WrongNamedEvents;
import org.opales.covalent.container.validation.ValidationParameters;
import org.opales.covalent.container.workers.WorkerPoolKind;
import org.opales.covalent.container.workers.WorkerPoolState;

/**
 * Verifies the settings of the events manager through a container of its own: rejected worker count and queue
 * capacity, the dispatch pool reported only while events are asynchronous and its follow-up of setting changes,
 * and the optional check of the naming convention of events methods.
 */
public class EventsManagerSettingsContainerizedTests {

	private CovalentContainer container;

	private RawComponentsServices rcs;

	private EventsManagerParameters parameters;

	private EventsWorkersServices workers;

	@Before
	public void setUp() {
		this.container = Covalent.container();
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
		this.container.start();
		this.rcs = this.container.rcs();
		this.rcs.registerComponent(this);
		this.parameters = this.rcs.accessParameters(EventsManagerParameters.class);
		this.workers = this.rcs.useServices(EventsWorkersServices.class);
	}

	@After
	public void tearDown() {
		this.container.dispose();
	}

	@Test
	public void workerCountBelowOneIsRejectedAndKeepsTheCurrentValue() {
		int current = this.parameters.getWorkerCount();

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> this.parameters.setWorkerCount(0));

		assertEquals("workerCount must be at least 1.", failure.getMessage());
		assertEquals(current, this.parameters.getWorkerCount());
	}

	@Test
	public void queueCapacityBelowOneIsRejectedAndKeepsTheCurrentValue() {
		int current = this.parameters.getWorkerQueueCapacity();

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> this.parameters.setWorkerQueueCapacity(0));

		assertEquals("queueCapacity must be at least 1.", failure.getMessage());
		assertEquals(current, this.parameters.getWorkerQueueCapacity());
	}

	@Test
	public void noDispatchPoolIsReportedWhileEventsAreSynchronous() {
		this.parameters.setSynchronous(true);

		assertTrue(this.parameters.isSynchronous());
		assertTrue(this.workers.readWorkerPools().isEmpty());
	}

	@Test
	public void theAsynchronousDispatchPoolIsReportedWithItsSettings() {
		this.parameters.setSynchronous(false);
		this.parameters.setWorkerCount(2);
		this.parameters.setWorkerQueueCapacity(7);

		List<WorkerPoolState> pools = this.workers.readWorkerPools();

		assertEquals(1, pools.size());
		WorkerPoolState pool = pools.get(0);
		assertEquals("events-async", pool.name());
		assertEquals(WorkerPoolKind.EVENTS, pool.kind());
		assertEquals(2, pool.targetWorkerCount());
		assertEquals(7, pool.queueCapacity());
		assertEquals(0, pool.queuedTasks());
		assertEquals(0, pool.rejectedTasks());
	}

	@Test
	public void workerCountAndQueueCapacityChangesApplyToTheRunningPool() {
		this.parameters.setSynchronous(false);
		this.parameters.setWorkerCount(1);

		this.parameters.setWorkerCount(4);
		this.parameters.setWorkerQueueCapacity(3);
		assertEquals(4, this.workers.readWorkerPools().get(0).targetWorkerCount());
		assertEquals(3, this.workers.readWorkerPools().get(0).queueCapacity());

		this.parameters.setWorkerCount(2);
		assertEquals(2, this.workers.readWorkerPools().get(0).targetWorkerCount());
		assertEquals(2, this.parameters.getWorkerCount());
		assertEquals(3, this.parameters.getWorkerQueueCapacity());
	}

	@Test
	public void methodsOfEventsInterfacesMustFollowTheNamingConventionByDefault() {
		ValidationParameters validation = this.rcs.accessParameters(ValidationParameters.class);
		assertTrue(validation.isCheckEventsMethodNamingConvention());
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.strict);

		assertThrows(NamingException.class, () -> this.rcs.produceEvents(WrongNamedEvents.class));
	}

	@Test
	public void theNamingConventionOfEventsMethodsCanBeUnchecked() {
		ValidationParameters validation = this.rcs.accessParameters(ValidationParameters.class);
		validation.setCheckEventsMethodNamingConvention(false);
		assertFalse(validation.isCheckEventsMethodNamingConvention());

		this.parameters.setSynchronous(true);
		WrongNamedEvents events = this.rcs.produceEvents(WrongNamedEvents.class);
		List<String> received = new java.util.ArrayList<>();
		this.rcs.subscribe(WrongNamedEvents.class, new WrongNamedEvents() {
			@Override
			public void badNamedonNotification(final String value) {
				received.add(value);
			}
		});
		events.badNamedonNotification("delivered");

		assertEquals(List.of("delivered"), received);
	}

	@Test
	public void asynchronousDeliveryWorksAgainAfterARestart() throws Exception {
		this.parameters.setSynchronous(false);
		this.container.stop();
		this.container.start();
		this.rcs.registerComponent(this);

		Probe producer = this.rcs.produceEvents(Probe.class);
		CountDownLatch delivered = new CountDownLatch(1);
		this.rcs.subscribe(Probe.class, (Probe) message -> delivered.countDown());
		producer.onPing("after restart");

		assertTrue(delivered.await(10, TimeUnit.SECONDS));
		List<WorkerPoolState> pools = this.rcs.useServices(EventsWorkersServices.class).readWorkerPools();
		assertEquals(1, pools.size());
		assertEquals("events-async", pools.get(0).name());
	}

	/** Events produced by the restart fixture. */
	public interface Probe {
		void onPing(String message);
	}
}
