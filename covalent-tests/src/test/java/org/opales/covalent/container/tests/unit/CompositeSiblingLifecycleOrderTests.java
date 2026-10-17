/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.ComposedComponent;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.lifecycle.Removal;

/**
 * Teardown visits consumers before providers. Two composed children declared in the same
 * composite are activated in declaration order and passivated, removed and undeployed in the
 * reverse order, so a second sibling that depends on the first can still use it from its own
 * {@code @Passivation} and {@code @Removal} callbacks; the composite's own operation comes last.
 * <p>
 * The passivation test calls a service of the first sibling through an ordinary
 * {@code @UsedServices} port. The removal and undeployment tests cannot use a port, because a
 * component's own used services are released right before its {@code @Removal} callback runs: the
 * composite root hands the first sibling to the second one as a plain object reference, and the
 * second sibling reads a flag set by the first sibling's own removal callback.
 * </p>
 */
public class CompositeSiblingLifecycleOrderTests {

	private CovalentContainer container;

	@Before
	public void resetFixtures() {
		SecondImpl.passivationCalled = false;
		SecondImpl.passivationFailure = null;
		RemovalSecondImpl.firstWasAlreadyRemoved = null;
		UndeploySecondImpl.firstWasAlreadyRemoved = null;
	}

	@After
	public void tearDown() {
		if (this.container != null) {
			this.container.dispose();
		}
	}

	@Test
	public void secondSiblingCanStillCallFirstSiblingsServiceFromItsOwnPassivationCallback() {
		this.container = Covalent.container();
		this.container.deployComponents(RootImpl.class);
		this.container.start();

		this.container.stop();

		Assert.assertTrue("SecondImpl's @Passivation callback was not called.", SecondImpl.passivationCalled);
		Assert.assertNull(
				"SecondImpl's own @Passivation callback called FirstServices.ping() - a service "
						+ "provided by its sibling, declared and activated before it in the same composite - "
						+ "and got an exception back. That means First was already passivated by the time "
						+ "Second's own teardown callback ran, even though Second (the consumer of First's "
						+ "service) should be torn down before First (the provider), not after.",
				SecondImpl.passivationFailure);
	}

	@Test
	public void secondSiblingSeesFirstSiblingNotYetRemovedFromItsOwnRemovalCallback() {
		this.container = Covalent.container();
		this.container.deployComponents(RemovalRootImpl.class);
		this.container.start();

		this.container.lcs().removeComponent(RemovalRoot.class);

		Assert.assertEquals(
				"By the time RemovalSecondImpl's own @Removal callback ran, RemovalFirstImpl - its sibling, "
						+ "declared and activated before it in the same composite - had already set its own "
						+ "'removed' flag. Second depends on First, so Second (the consumer) should be torn "
						+ "down before First (the provider), not after.",
				Boolean.FALSE, RemovalSecondImpl.firstWasAlreadyRemoved);
	}

	@Test
	public void secondSiblingSeesFirstSiblingNotYetRemovedFromItsOwnRemovalCallbackDuringUndeploy() {
		this.container = Covalent.container();
		this.container.deployComponents(UndeployRootImpl.class);
		this.container.start();

		// Undeployment has no callback annotation of its own, but it first removes a
		// component that is not removed yet, so undeployComponent() reaches the same
		// @Removal callback as above.
		this.container.lcs().undeployComponent(UndeployRootImpl.class);

		Assert.assertEquals(
				"By the time UndeploySecondImpl's own @Removal callback ran (triggered by undeployComponent()'s "
						+ "own remove-before-undeploy precondition), UndeployFirstImpl had already set its own "
						+ "'removed' flag - it was already torn down before Second, even though Second depends on it.",
				Boolean.FALSE, UndeploySecondImpl.firstWasAlreadyRemoved);
	}

	@Component
	public interface Root {
	}

	@Component
	public interface First {
	}

	@Component
	public interface Second {
	}

	@Services
	public interface FirstServices {
		void ping();
	}

	@Implementation
	public static class RootImpl implements Root {
		@ComposedComponent
		private final FirstImpl first = null;
		@ComposedComponent
		private final SecondImpl second = null;
	}

	@Implementation
	public static class FirstImpl implements First, FirstServices {
		@Override
		public void ping() {
			// no-op: only reachability (not passivated) matters for this test
		}
	}

	@Implementation
	public static class SecondImpl implements Second {

		static boolean passivationCalled;

		static RuntimeException passivationFailure;

		@UsedServices
		private FirstServices firstServices;

		@Passivation
		public void passivate() {
			try {
				this.firstServices.ping();
			} catch (final RuntimeException e) {
				passivationFailure = e;
			}
			passivationCalled = true;
		}
	}

	// --- Fixture for secondSiblingSeesFirstSiblingNotYetRemovedFromItsOwnRemovalCallback() ---

	@Component
	public interface RemovalRoot {
	}

	@Component
	public interface RemovalFirst {
	}

	@Component
	public interface RemovalSecond {
	}

	@Implementation
	public static class RemovalRootImpl implements RemovalRoot {
		@ComposedComponent
		private final RemovalFirstImpl first = null;
		@ComposedComponent
		private final RemovalSecondImpl second = null;

		// Wires the plain (non-ported) direct reference once both children are
		// known to be constructed and active - see the class javadoc for why
		// this bypasses the port system entirely instead of using @UsedServices.
		@Activation
		public void wireDirectSiblingReference() {
			this.second.firstDirectReference = this.first;
		}
	}

	@Implementation
	public static class RemovalFirstImpl implements RemovalFirst {
		volatile boolean removed;

		@Removal
		public void remove() {
			this.removed = true;
		}
	}

	@Implementation
	public static class RemovalSecondImpl implements RemovalSecond {

		static Boolean firstWasAlreadyRemoved;

		RemovalFirstImpl firstDirectReference;

		@Removal
		public void remove() {
			firstWasAlreadyRemoved = this.firstDirectReference.removed;
		}
	}

	// --- Fixture for secondSiblingSeesFirstSiblingNotYetRemovedFromItsOwnRemovalCallbackDuringUndeploy() ---

	@Component
	public interface UndeployRoot {
	}

	@Component
	public interface UndeployFirst {
	}

	@Component
	public interface UndeploySecond {
	}

	@Implementation
	public static class UndeployRootImpl implements UndeployRoot {
		@ComposedComponent
		private final UndeployFirstImpl first = null;
		@ComposedComponent
		private final UndeploySecondImpl second = null;

		@Activation
		public void wireDirectSiblingReference() {
			this.second.firstDirectReference = this.first;
		}
	}

	@Implementation
	public static class UndeployFirstImpl implements UndeployFirst {
		volatile boolean removed;

		@Removal
		public void remove() {
			this.removed = true;
		}
	}

	@Implementation
	public static class UndeploySecondImpl implements UndeploySecond {

		static Boolean firstWasAlreadyRemoved;

		UndeployFirstImpl firstDirectReference;

		@Removal
		public void remove() {
			firstWasAlreadyRemoved = this.firstDirectReference.removed;
		}
	}
}
