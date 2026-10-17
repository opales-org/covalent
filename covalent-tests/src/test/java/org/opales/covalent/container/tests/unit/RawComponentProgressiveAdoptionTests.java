/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.lifecycle.Initialization;
import org.opales.covalent.container.raw.RawComponentsServices;

/**
 * <p>
 * {@code RawComponentsImpl.registerComponent} guards each of its steps against
 * being run again on an already-registered component: {@code LifecycleManagerImpl}
 * rejects initializing or activating a component that is already in that state
 * (or a later one) with an {@code IllegalStateException}, so every step
 * (deploy, instantiate, initialize, activate, passivate) is a no-op once the
 * component has already reached (or passed) it.
 * </p>
 * <p>
 * These tests check that the whole method is safe to call repeatedly, even when
 * asking for the exact same {@code stateToReach}, or an earlier one the component
 * has already passed.
 * </p>
 */
public class RawComponentProgressiveAdoptionTests {

	private CovalentContainer container;

	// No tearDown(): these tests register raw components on a fresh container
	// without ever calling start() (exercising the pre-first-start bootstrap
	// window), and stop() requires having been started first; the
	// throwaway container is simply discarded after each test.

	@Test
	public void registeringAgainAtTheSameStateToReachIsANoOp() {
		this.container = Covalent.container();
		RawComponentsServices rcs = this.container.rcs();
		CountingComponent instance = new CountingComponent();

		rcs.registerComponent(CountingComponent.class, instance, ComponentState.activated);
		Assert.assertEquals(1, instance.initializedCount);
		Assert.assertEquals(1, instance.activatedCount);

		rcs.registerComponent(CountingComponent.class, instance, ComponentState.activated);
		Assert.assertEquals("initialization must not run again", 1, instance.initializedCount);
		Assert.assertEquals("activation must not run again", 1, instance.activatedCount);
	}

	@Test
	public void registeringAgainAtAnEarlierAlreadyReachedStateToReachIsANoOp() {
		this.container = Covalent.container();
		RawComponentsServices rcs = this.container.rcs();
		CountingComponent instance = new CountingComponent();

		rcs.registerComponent(CountingComponent.class, instance, ComponentState.activated);

		// Asking to reach only "instantiated" on a component that has already
		// reached "activated" must not throw, and must not touch
		// initialize/activate.
		rcs.registerComponent(CountingComponent.class, instance, ComponentState.instantiated);
		Assert.assertEquals(1, instance.initializedCount);
		Assert.assertEquals(1, instance.activatedCount);
	}

	@Test
	public void progressiveAdoptionRunsEachStepExactlyOnce() {
		this.container = Covalent.container();
		RawComponentsServices rcs = this.container.rcs();
		CountingComponent instance = new CountingComponent();

		rcs.registerComponent(CountingComponent.class, instance, ComponentState.instantiated);
		Assert.assertEquals(0, instance.initializedCount);
		Assert.assertEquals(0, instance.activatedCount);

		rcs.registerComponent(CountingComponent.class, instance, ComponentState.initialized);
		Assert.assertEquals(1, instance.initializedCount);
		Assert.assertEquals(0, instance.activatedCount);

		rcs.registerComponent(CountingComponent.class, instance, ComponentState.activated);
		Assert.assertEquals(1, instance.initializedCount);
		Assert.assertEquals(1, instance.activatedCount);

		// Advancing again to the same already-reached state is a no-op, exactly
		// like the two tests above.
		rcs.registerComponent(CountingComponent.class, instance, ComponentState.activated);
		Assert.assertEquals(1, instance.initializedCount);
		Assert.assertEquals(1, instance.activatedCount);
	}

	@Test
	public void registeringADistinctInstanceUnderAnOccupiedComponentTypeIsRejected() {
		// registerComponent guards its state-advancing steps against running twice on
		// the SAME instance, and must also reject a second, distinct instance
		// registered under an already-occupied componentType: pseudoInstantiateComponent
		// is skipped (the type is already instantiated), so the second instance would
		// never be stored anywhere, and initializeComponent/activateComponent below
		// would silently act on the FIRST instance instead, advancing it while
		// discarding the second one with no error at all. Registering a distinct
		// instance under an occupied component type must be rejected outright, and must
		// leave the first instance's state exactly as it was.
		this.container = Covalent.container();
		RawComponentsServices rcs = this.container.rcs();
		CountingComponent first = new CountingComponent();
		CountingComponent second = new CountingComponent();

		rcs.registerComponent(CountingComponent.class, first, ComponentState.activated);
		Assert.assertEquals(1, first.initializedCount);
		Assert.assertEquals(1, first.activatedCount);

		IllegalStateException expected = Assert.assertThrows("registering a distinct instance under an already-occupied component type must throw",
				IllegalStateException.class,
				() -> rcs.registerComponent(CountingComponent.class, second, ComponentState.activated));

		Assert.assertEquals("the first instance must not be re-initialized or re-activated", 1,
				first.initializedCount);
		Assert.assertEquals(1, first.activatedCount);
		Assert.assertEquals("the second, rejected instance must never be touched", 0, second.initializedCount);
		Assert.assertEquals(0, second.activatedCount);
	}

	@Test
	public void registeringADistinctInstanceIsRejectedEvenWhenOnlyReachingInstantiated() {
		// The identity check must run before the early return for
		// ComponentState.instantiated, not only further down alongside the
		// initialize/activate/passivate steps - otherwise a caller asking only
		// for "instantiated" to be reached (as RawComponentsImpl's own
		// pseudoInstantiateComponent-driven fast path does) would never hit it.
		this.container = Covalent.container();
		RawComponentsServices rcs = this.container.rcs();
		CountingComponent first = new CountingComponent();
		CountingComponent second = new CountingComponent();

		rcs.registerComponent(CountingComponent.class, first, ComponentState.instantiated);

		IllegalStateException expected = Assert.assertThrows("registering a distinct instance under an already-occupied component type must throw",
				IllegalStateException.class,
				() -> rcs.registerComponent(CountingComponent.class, second, ComponentState.instantiated));
	}

	@Test
	public void activateComponentStillWorksAfterPartialRegistration() {
		// Locks in the activateComponent(Class, Object) idiom, which
		// itself calls registerComponent again internally when not yet
		// initialized - this must keep working.
		this.container = Covalent.container();
		RawComponentsServices rcs = this.container.rcs();
		CountingComponent instance = new CountingComponent();

		rcs.registerComponent(CountingComponent.class, instance, ComponentState.instantiated);
		rcs.activateComponent(CountingComponent.class, instance);

		Assert.assertEquals(1, instance.initializedCount);
		Assert.assertEquals(1, instance.activatedCount);
	}

	public static class CountingComponent {

		int initializedCount;

		int activatedCount;

		@Initialization
		public void init() {
			this.initializedCount++;
		}

		@Activation
		public void activate() {
			this.activatedCount++;
		}

	}

}
