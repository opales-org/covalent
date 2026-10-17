/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.CovalentContainerizedTests;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.ComponentAEvents;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.impl.ComponentA;

/**
 * A focused concurrency test for the events dispatch path - see
 * the concurrency contract documented on {@code EventsManagerServices}:
 * producer/subscriber bookkeeping relies on the caller going through
 * {@code LifecycleServices}, but firing an already-established event is a
 * separate, independently safe concern that this test pins down.
 */
public class EventsConcurrencyContainerizedTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	@Before
	public void setUp() {
		this.rcs.registerComponent(this);
		this.cm.getParameters().setSynchronousEvents(true);
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
	}

	@After
	public void tearDown() {
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.trace);
	}

	@Test
	public void concurrentEventFiringFromMultipleThreadsAllReachTheConsumer() throws Exception {
		ComponentA provider = new ComponentA();
		this.rcs.registerComponent(provider);
		AtomicInteger received = new AtomicInteger();
		ComponentAEvents consumer = new ComponentAEvents() {
			@Override
			public void onCreate(final String data) {
				received.incrementAndGet();
			}

			@Override
			public void onUpdate(final String data) {
			}

			@Override
			public void onDelete(final String data) {
			}

			@Override
			public void onEmpty() {
			}
		};
		this.rcs.subscribe(ComponentAEvents.class, consumer);

		int firingThreads = 20;
		CountDownLatch ready = new CountDownLatch(firingThreads);
		CountDownLatch go = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(firingThreads);
		ExecutorService executor = Executors.newFixedThreadPool(firingThreads);
		try {
			ComponentAEvents producerEvents = provider.getProduceComponentAEvents();
			for (int i = 0; i < firingThreads; i++) {
				executor.submit(() -> {
					ready.countDown();
					try {
						go.await(5, TimeUnit.SECONDS);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					producerEvents.onCreate("concurrent");
					done.countDown();
				});
			}
			Assert.assertTrue(ready.await(2, TimeUnit.SECONDS));
			go.countDown();
			Assert.assertTrue(done.await(5, TimeUnit.SECONDS));
			Assert.assertEquals(firingThreads, received.get());
		} finally {
			executor.shutdownNow();
			this.rcs.unsubscribe(ComponentAEvents.class, consumer);
			this.rcs.unregisterComponent(ComponentA.class);
		}
	}

}
