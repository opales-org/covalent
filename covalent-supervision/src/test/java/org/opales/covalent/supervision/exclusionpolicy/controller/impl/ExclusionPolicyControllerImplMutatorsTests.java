/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.exclusionpolicy.controller.impl;

import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.hmi.HmiCore;
import org.opales.covalent.supervision.exclusionpolicy.controller.ExclusionParameters;
import org.opales.covalent.supervision.exclusionpolicy.controller.ExclusionPolicyControllerServices;
import org.opales.covalent.supervision.exclusionpolicy.model.ExclusionPolicyEvents;
import org.opales.covalent.supervision.exclusionpolicy.model.impl.ExclusionPolicyModelImpl;
import org.opales.covalent.supervision.testsupport.observed.FlatParametersOfferorComponent;

/**
 * Covers {@code ExclusionPolicyControllerImpl}'s three mutators - which, unlike {@code
 * isExcluded(Class)} (see {@link ExclusionPolicyControllerImplTests}), call through to {@code
 * ExclusionPolicyModelServices} and so need a real deployment. A minimal one: just the
 * Controller and Model pair, no {@code ComponentDiagram}/observed-container harness needed at
 * all for this.
 */
public class ExclusionPolicyControllerImplMutatorsTests {

	private CovalentContainer container;

	private RawComponentsServices rcs;

	private ExclusionParameters exclusionParameters;

	private ExclusionPolicyControllerServices controllerServices;

	private final List<List<Class<?>>> excludedComponentsHistory = new java.util.concurrent.CopyOnWriteArrayList<>();

	private final List<Boolean> excludeInternalHistory = new java.util.concurrent.CopyOnWriteArrayList<>();

	@Before
	public void setUp() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		this.rcs = this.container.rcs();
		this.rcs.registerComponent(ExclusionPolicyControllerImplMutatorsTests.class, this);
		this.rcs.subscribe(ExclusionPolicyControllerImplMutatorsTests.class, ExclusionPolicyEvents.class,
				new ExclusionPolicyEvents() {
					@Override
					public void onExcludedComponentsChanged(final List<Class<?>> componentTypes) {
						ExclusionPolicyControllerImplMutatorsTests.this.excludedComponentsHistory.add(componentTypes);
					}

					@Override
					public void onExcludeInternalComponentsChanged(final boolean exclude) {
						ExclusionPolicyControllerImplMutatorsTests.this.excludeInternalHistory.add(exclude);
					}
				});

		this.container.deployComponents(ExclusionPolicyModelImpl.class, ExclusionPolicyControllerImpl.class);
		this.container.start();

		this.exclusionParameters = this.rcs.accessParameters(ExclusionParameters.class);
		this.controllerServices = this.rcs.useServices(ExclusionPolicyControllerServices.class);
	}

	@After
	public void tearDown() {
		if (this.container != null) {
			this.container.dispose();
		}
	}

	@Test
	public void setExcludedComponentsIsReflectedByIsExcludedAndForwardedToTheModel() {
		this.controllerServices.setExcludedComponents(List.of(FlatParametersOfferorComponent.class));

		Assert.assertTrue(this.exclusionParameters.isExcluded(FlatParametersOfferorComponent.class));
		Assert.assertFalse("setExcludedComponents replaces the whole list, dropping the default HmiCore entry",
				this.exclusionParameters.isExcluded(HmiCore.class));
		Assert.assertEquals(List.of(List.of(FlatParametersOfferorComponent.class)),
				this.excludedComponentsHistory);
	}

	@Test
	public void addExcludedComponentAppendsAndForwardsTheWholeResultingList() {
		this.controllerServices.addExcludedComponent(FlatParametersOfferorComponent.class);

		Assert.assertTrue(this.exclusionParameters.isExcluded(FlatParametersOfferorComponent.class));
		Assert.assertTrue("addExcludedComponent appends - the default HmiCore entry survives",
				this.exclusionParameters.isExcluded(HmiCore.class));
		Assert.assertEquals(1, this.excludedComponentsHistory.size());
		Assert.assertTrue(this.excludedComponentsHistory.get(0).contains(FlatParametersOfferorComponent.class));
		Assert.assertTrue(this.excludedComponentsHistory.get(0).contains(HmiCore.class));
	}

	@Test
	public void addingAComponentAlreadyExcludedChangesNothingAndForwardsNothing() {
		this.controllerServices.addExcludedComponent(FlatParametersOfferorComponent.class);
		this.controllerServices.addExcludedComponent(FlatParametersOfferorComponent.class);
		this.controllerServices.addExcludedComponent(HmiCore.class);

		Assert.assertEquals(1, this.excludedComponentsHistory.size());
		Assert.assertTrue(this.exclusionParameters.isExcluded(FlatParametersOfferorComponent.class));
		Assert.assertTrue(this.exclusionParameters.isExcluded(HmiCore.class));
	}

	@Test
	public void setExcludeInternalComponentsForwardsToTheModel() {
		this.controllerServices.setExcludeInternalComponents(false);

		Assert.assertEquals(List.of(false), this.excludeInternalHistory);
	}

	@Test
	public void typesAddedAtTheSameTimeAreAllKept() throws Exception {
		List<Class<?>> types = List.of(Integer.class, Long.class, Short.class, Byte.class, Double.class, Float.class,
				Character.class, Boolean.class, String.class, StringBuilder.class, java.util.ArrayList.class,
				java.util.HashMap.class, java.util.LinkedList.class, java.util.HashSet.class,
				java.util.TreeMap.class, java.util.TreeSet.class, java.util.ArrayDeque.class,
				java.util.Vector.class, java.util.Stack.class, java.util.Hashtable.class,
				java.util.LinkedHashMap.class, java.util.LinkedHashSet.class, java.util.BitSet.class,
				java.util.Date.class, java.util.Random.class, java.util.Timer.class, java.util.UUID.class,
				java.util.Locale.class, java.util.Currency.class, java.util.Scanner.class);
		for (int round = 0; round < 20; round++) {
			this.controllerServices.setExcludedComponents(List.of());
			java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
			java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(types.size());
			try {
				List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
				for (Class<?> type : types) {
					futures.add(pool.submit(() -> {
						go.await();
						this.controllerServices.addExcludedComponent(type);
						return null;
					}));
				}
				go.countDown();
				for (java.util.concurrent.Future<?> future : futures) {
					future.get(10, java.util.concurrent.TimeUnit.SECONDS);
				}
			} finally {
				pool.shutdownNow();
			}

			List<Class<?>> kept = this.excludedComponentsHistory.get(this.excludedComponentsHistory.size() - 1);
			Assert.assertEquals("round " + round, types.size(), kept.size());
		}
	}
}
