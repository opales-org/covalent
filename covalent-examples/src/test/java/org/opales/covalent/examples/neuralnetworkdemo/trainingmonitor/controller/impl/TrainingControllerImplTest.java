/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Array;
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
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetServices;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkServices;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.model.TrainingModelServices;

/**
 * The training controller's lifecycle against its epochs, which run on their own thread:
 * passivation waits for the epoch in progress, so that no epoch reaches a provider passivated
 * right after the controller, and training can run again once reactivated.
 */
public class TrainingControllerImplTest {

	private TrainingControllerImpl controller;

	private final AtomicInteger epochs = new AtomicInteger();

	/** Holds the first epoch inside {@code recordEpochStarted} until released. */
	private final CountDownLatch firstEpochEntered = new CountDownLatch(1);
	private final CountDownLatch releaseFirstEpoch = new CountDownLatch(1);

	@Before
	public void setUp() throws Exception {
		this.controller = new TrainingControllerImpl();
		TrainingModelServices model = (TrainingModelServices) Proxy.newProxyInstance(
				TrainingModelServices.class.getClassLoader(), new Class<?>[] { TrainingModelServices.class },
				(proxy, method, args) -> {
					if (method.getName().equals("recordEpochStarted")) {
						if (this.epochs.incrementAndGet() == 1) {
							this.firstEpochEntered.countDown();
							this.releaseFirstEpoch.await(5, TimeUnit.SECONDS);
						}
					}
					return defaultValue(method);
				});
		this.inject(TrainingModelServices.class, model);
		this.inject(DatasetServices.class, idle(DatasetServices.class));
		this.inject(NetworkServices.class, idle(NetworkServices.class));
		this.invoke(Initialization.class);
	}

	@After
	public void tearDown() throws Exception {
		this.releaseFirstEpoch.countDown();
		this.controller.pause();
		this.invoke(Removal.class);
	}

	@Test
	public void passivationWaitsForTheEpochInProgressAndStopsTraining() throws Exception {
		this.controller.start();
		assertTrue(this.firstEpochEntered.await(5, TimeUnit.SECONDS));

		Thread passivation = new Thread(() -> this.invokeUnchecked(Passivation.class));
		passivation.start();
		passivation.join(300);
		assertTrue("passivation returned while an epoch was in progress", passivation.isAlive());

		this.releaseFirstEpoch.countDown();
		passivation.join(5000);
		assertFalse(passivation.isAlive());
		int afterPassivation = this.epochs.get();
		Thread.sleep(300);
		assertEquals(afterPassivation, this.epochs.get());
	}

	@Test
	public void trainingRunsAgainOnceReactivated() throws Exception {
		this.releaseFirstEpoch.countDown();
		this.invoke(Passivation.class);

		this.controller.start();
		long deadline = System.currentTimeMillis() + 5000;
		// With the idle fakes, the first epoch converges and pauses training.
		while (this.epochs.get() < 1 && System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}
		assertEquals(1, this.epochs.get());
	}

	@Test
	public void resetWaitsForTheEpochInProgress() throws Exception {
		this.controller.start();
		assertTrue(this.firstEpochEntered.await(5, TimeUnit.SECONDS));

		Thread reset = new Thread(this.controller::reset);
		reset.start();
		reset.join(300);
		assertTrue("reset returned while an epoch was in progress", reset.isAlive());

		this.releaseFirstEpoch.countDown();
		reset.join(5000);
		assertFalse(reset.isAlive());
	}

	@Test
	public void aBatchSizeBelowOneIsRaisedToOne() {
		this.controller.setBatchSize(0);
		assertEquals(1, this.controller.getBatchSize());
		this.controller.setBatchSize(-5);
		assertEquals(1, this.controller.getBatchSize());
		this.controller.setBatchSize(64);
		assertEquals(64, this.controller.getBatchSize());
	}

	@Test
	public void aPaceThatCannotBeScheduledIsIgnored() {
		this.controller.setEpochsPerSecond(10.0);
		this.controller.setEpochsPerSecond(0.0);
		this.controller.setEpochsPerSecond(-3.0);
		this.controller.setEpochsPerSecond(Double.NaN);
		this.controller.setEpochsPerSecond(Double.POSITIVE_INFINITY);
		assertEquals(10.0, this.controller.getEpochsPerSecond(), 0.0);
	}

	private static <T> T idle(final Class<T> type) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
				(proxy, method, args) -> defaultValue(method)));
	}

	private static Object defaultValue(final Method method) {
		Class<?> type = method.getReturnType();
		if (type.isArray()) {
			return Array.newInstance(type.getComponentType(), 0);
		}
		if (type == double.class) {
			return 0.0;
		}
		if (type == int.class) {
			return 0;
		}
		if (type == boolean.class) {
			return false;
		}
		return null;
	}

	private void inject(final Class<?> type, final Object value) throws Exception {
		for (Field field : TrainingControllerImpl.class.getDeclaredFields()) {
			if (field.getType() == type) {
				field.setAccessible(true);
				field.set(this.controller, value);
			}
		}
	}

	private void invoke(final Class<? extends Annotation> lifecycle) throws Exception {
		for (Method method : TrainingControllerImpl.class.getDeclaredMethods()) {
			if (method.isAnnotationPresent(lifecycle)) {
				method.setAccessible(true);
				method.invoke(this.controller);
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
