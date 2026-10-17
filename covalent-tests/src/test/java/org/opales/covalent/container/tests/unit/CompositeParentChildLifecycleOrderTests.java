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
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.lifecycle.Removal;

/**
 * A composite is torn down before its own composed child: the composite's {@code @Passivation}
 * and {@code @Removal} callbacks can still use or observe the child one last time, as its
 * {@code @Activation} callback can use an already-active child. Same contract as
 * {@link CompositeSiblingLifecycleOrderTests}, between a composite and its child instead of
 * between two siblings.
 * <p>
 * The passivation test calls a service of the child through an ordinary {@code @UsedServices}
 * port. The removal and undeployment tests read a flag directly off the child instance that the
 * parent holds through {@code @ComposedComponent}, because a component's own used services are
 * released right before its {@code @Removal} callback runs.
 * </p>
 */
public class CompositeParentChildLifecycleOrderTests {

	private CovalentContainer container;

	@Before
	public void resetFixtures() {
		ParentImpl.passivationCalled = false;
		ParentImpl.passivationFailure = null;
		RemovalParentImpl.childWasAlreadyRemoved = null;
		UndeployParentImpl.childWasAlreadyRemoved = null;
	}

	@After
	public void tearDown() {
		if (this.container != null) {
			this.container.dispose();
		}
	}

	@Test
	public void parentCanStillCallItsOwnChildsServiceFromItsOwnPassivationCallback() {
		this.container = Covalent.container();
		this.container.deployComponents(ParentImpl.class);
		this.container.start();

		this.container.stop();

		Assert.assertTrue("ParentImpl's @Passivation callback was not called.", ParentImpl.passivationCalled);
		Assert.assertNull(
				"ParentImpl's own @Passivation callback called ChildServices.ping() - a service provided by "
						+ "its own composed child - and got an exception back. That means the child was already "
						+ "passivated by the time the parent's own teardown callback ran, even though the parent "
						+ "should be able to call its child one last time there, the same way its own "
						+ "@Activation callback can call an already-active child.",
				ParentImpl.passivationFailure);
	}

	@Test
	public void parentSeesItsOwnChildNotYetRemovedFromItsOwnRemovalCallback() {
		this.container = Covalent.container();
		this.container.deployComponents(RemovalParentImpl.class);
		this.container.start();

		this.container.lcs().removeComponent(RemovalParent.class);

		Assert.assertEquals(
				"By the time RemovalParentImpl's own @Removal callback ran, RemovalChildImpl - its own composed "
						+ "child - had already set its own 'removed' flag: the child was torn down before the "
						+ "parent, even though the parent should be able to observe it one last time there.",
				Boolean.FALSE, RemovalParentImpl.childWasAlreadyRemoved);
	}

	@Test
	public void parentSeesItsOwnChildNotYetRemovedFromItsOwnRemovalCallbackDuringUndeploy() {
		this.container = Covalent.container();
		this.container.deployComponents(UndeployParentImpl.class);
		this.container.start();

		// Undeployment has no callback annotation of its own, but it first removes a
		// component that is not removed yet, so undeployComponent() reaches the same
		// @Removal callback as above.
		this.container.lcs().undeployComponent(UndeployParentImpl.class);

		Assert.assertEquals(
				"By the time UndeployParentImpl's own @Removal callback ran (triggered by undeployComponent()'s "
						+ "own remove-before-undeploy precondition), UndeployChildImpl had already set its own "
						+ "'removed' flag - the child was already torn down before the parent.",
				Boolean.FALSE, UndeployParentImpl.childWasAlreadyRemoved);
	}

	// --- Fixture for parentCanStillCallItsOwnChildsServiceFromItsOwnPassivationCallback() ---

	@Component
	public interface Parent {
	}

	@Component
	public interface Child {
	}

	@Services
	public interface ChildServices {
		void ping();
	}

	@Implementation
	public static class ParentImpl implements Parent {

		static boolean passivationCalled;

		static RuntimeException passivationFailure;

		@ComposedComponent
		private final ChildImpl child = null;

		@UsedServices
		private ChildServices childServices;

		@Passivation
		public void passivate() {
			try {
				this.childServices.ping();
			} catch (final RuntimeException e) {
				passivationFailure = e;
			}
			passivationCalled = true;
		}
	}

	@Implementation
	public static class ChildImpl implements Child, ChildServices {
		@Override
		public void ping() {
			// no-op: only reachability (not passivated) matters for this test
		}
	}

	// --- Fixture for parentSeesItsOwnChildNotYetRemovedFromItsOwnRemovalCallback() ---

	@Component
	public interface RemovalParent {
	}

	@Component
	public interface RemovalChild {
	}

	@Implementation
	public static class RemovalParentImpl implements RemovalParent {

		static Boolean childWasAlreadyRemoved;

		@ComposedComponent
		private final RemovalChildImpl child = null;

		@Removal
		public void remove() {
			childWasAlreadyRemoved = this.child.removed;
		}
	}

	@Implementation
	public static class RemovalChildImpl implements RemovalChild {
		volatile boolean removed;

		@Removal
		public void remove() {
			this.removed = true;
		}
	}

	// --- Fixture for parentSeesItsOwnChildNotYetRemovedFromItsOwnRemovalCallbackDuringUndeploy() ---

	@Component
	public interface UndeployParent {
	}

	@Component
	public interface UndeployChild {
	}

	@Implementation
	public static class UndeployParentImpl implements UndeployParent {

		static Boolean childWasAlreadyRemoved;

		@ComposedComponent
		private final UndeployChildImpl child = null;

		@Removal
		public void remove() {
			childWasAlreadyRemoved = this.child.removed;
		}
	}

	@Implementation
	public static class UndeployChildImpl implements UndeployChild {
		volatile boolean removed;

		@Removal
		public void remove() {
			this.removed = true;
		}
	}
}
