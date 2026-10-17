/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.simulationrunner.controller.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.container.lifecycle.Initialization;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.lifecycle.Removal;
import org.opales.covalent.examples.ecosystem.ecosystem.controller.EcosystemServices;
import org.opales.covalent.examples.ecosystem.simulationrunner.model.RunnerModelServices;

/**
 * The runner's lifecycle against its ticks, which run on their own thread: passivation waits
 * for the tick in progress, so that no tick reaches a provider passivated right after the
 * runner, and the runner can run again once reactivated.
 */
public class RunnerControllerImplTest {

	private RunnerControllerImpl runner;

	private final AtomicInteger advances = new AtomicInteger();

	/** Makes every tick fail while set. */
	private volatile boolean failing;

	/** Holds the first tick inside {@code advance} until released. */
	private final CountDownLatch firstTickEntered = new CountDownLatch(1);
	private final CountDownLatch releaseFirstTick = new CountDownLatch(1);

	@Before
	public void setUp() throws Exception {
		this.runner = new RunnerControllerImpl();
		EcosystemServices ecosystem = (EcosystemServices) Proxy.newProxyInstance(
				EcosystemServices.class.getClassLoader(), new Class<?>[] { EcosystemServices.class },
				(proxy, method, args) -> {
					if (method.getName().equals("advance")) {
						if (this.failing) {
							throw new IllegalStateException("simulated failure");
						}
						if (this.advances.incrementAndGet() == 1) {
							this.firstTickEntered.countDown();
							this.releaseFirstTick.await(5, TimeUnit.SECONDS);
						}
					}
					return null;
				});
		RunnerModelServices model = (RunnerModelServices) Proxy.newProxyInstance(
				RunnerModelServices.class.getClassLoader(), new Class<?>[] { RunnerModelServices.class },
				(proxy, method, args) -> method.getReturnType() == double.class ? 0.0
						: method.getReturnType() == boolean.class ? false : null);
		this.inject(EcosystemServices.class, ecosystem);
		this.inject(RunnerModelServices.class, model);
		this.invoke(Initialization.class);
	}

	@After
	public void tearDown() throws Exception {
		this.releaseFirstTick.countDown();
		this.runner.pause();
		this.invoke(Removal.class);
	}

	@Test
	public void passivationWaitsForTheTickInProgressAndStopsTicking() throws Exception {
		this.runner.start();
		assertTrue(this.firstTickEntered.await(5, TimeUnit.SECONDS));

		Thread passivation = new Thread(() -> this.invokeUnchecked(Passivation.class));
		passivation.start();
		passivation.join(300);
		assertTrue("passivation returned while a tick was in progress", passivation.isAlive());

		this.releaseFirstTick.countDown();
		passivation.join(5000);
		assertFalse(passivation.isAlive());
		int afterPassivation = this.advances.get();
		Thread.sleep(300);
		assertEquals(afterPassivation, this.advances.get());
	}

	@Test
	public void runnerRunsAgainOnceReactivated() throws Exception {
		this.releaseFirstTick.countDown();
		this.invoke(Passivation.class);

		this.runner.start();
		long deadline = System.currentTimeMillis() + 5000;
		while (this.advances.get() < 2 && System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}
		assertTrue(this.advances.get() >= 2);
	}

	@Test
	public void resetWaitsForTheTickInProgress() throws Exception {
		this.runner.start();
		assertTrue(this.firstTickEntered.await(5, TimeUnit.SECONDS));

		Thread reset = new Thread(this.runner::reset);
		reset.start();
		reset.join(300);
		assertTrue("reset returned while a tick was in progress", reset.isAlive());

		this.releaseFirstTick.countDown();
		reset.join(5000);
		assertFalse(reset.isAlive());
	}

	@Test
	public void aFailingTickStopsTheRunner() throws Exception {
		this.failing = true;
		this.runner.start();
		long deadline = System.currentTimeMillis() + 5000;
		while (this.runner.isRunning() && System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}
		assertFalse(this.runner.isRunning());
	}

	private void inject(final Class<?> type, final Object value) throws Exception {
		for (Field field : RunnerControllerImpl.class.getDeclaredFields()) {
			if (field.getType() == type) {
				field.setAccessible(true);
				field.set(this.runner, value);
			}
		}
	}

	private void invoke(final Class<? extends Annotation> lifecycle) throws Exception {
		for (Method method : RunnerControllerImpl.class.getDeclaredMethods()) {
			if (method.isAnnotationPresent(lifecycle)) {
				method.setAccessible(true);
				method.invoke(this.runner);
			}
		}
	}

	private void invokeUnchecked(final Class<? extends Annotation> lifecycle) {
		try {
			this.invoke(lifecycle);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
