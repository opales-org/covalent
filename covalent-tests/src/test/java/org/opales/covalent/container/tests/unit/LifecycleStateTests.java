/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.opales.covalent.container.lifecycle.impl.LifecycleManagerImpl;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.ComposedComponent;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.lifecycle.Initialization;
import org.opales.covalent.container.lifecycle.LifecycleManager;
import org.opales.covalent.container.lifecycle.LifecycleParameters;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.lifecycle.LifecycleServices.StartMode;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.lifecycle.Removal;

@RunWith(Parameterized.class)
public class LifecycleStateTests {

	@Parameterized.Parameters(name = "{0}")
	public static Object[] modes() {
		return StartMode.values();
	}

	private final StartMode mode;
	private CovalentContainer container;
	private LifecycleServices lifecycle;
	private static final List<Class<?>> removals = new ArrayList<>();
	private static ComponentState failAt;

	public LifecycleStateTests(final StartMode mode) {
		this.mode = mode;
	}

	private CapturingLogHandler lifecycleLog;

	@Before
	public void setUp() {
		this.lifecycleLog = CapturingLogHandler.install(LifecycleManagerImpl.class);
		failAt = null;
		removals.clear();
		this.container = Covalent.container();
		this.lifecycle = this.container.lcs();
		this.container.rcs().registerComponent(this);
		this.container.rcs().accessParameters(LifecycleParameters.class).setStartMode(this.mode);
		this.container.start();
	}

	@After
	public void tearDown() {
		failAt = null;
		this.container.dispose();
		this.lifecycleLog.restore();
	}

	@Test
	public void queriesDescribeEveryStateAndUndeployment() {
		this.assertState(First.class, null);
		this.lifecycle.deployComponent(FirstImpl.class);
		this.assertState(First.class, ComponentState.deployed);
		this.lifecycle.instantiateComponent(First.class);
		this.assertState(First.class, ComponentState.instantiated);
		this.lifecycle.initializeComponent(First.class);
		this.assertState(First.class, ComponentState.initialized);
		this.lifecycle.activateComponent(First.class);
		this.assertState(First.class, ComponentState.activated);
		this.lifecycle.passivateComponent(First.class);
		this.assertState(First.class, ComponentState.passivated);
		this.lifecycle.removeComponent(First.class);
		this.assertState(First.class, ComponentState.removed);
		this.lifecycle.undeployComponent(FirstImpl.class);
		this.assertState(First.class, null);
	}

	@Test
	public void repeatedActivationAndPassivationKeepPrerequisiteStates() {
		this.lifecycle.deployComponent(FirstImpl.class);
		for (int cycle = 0; cycle < 3; cycle++) {
			this.lifecycle.activateComponent(First.class);
			this.assertState(First.class, ComponentState.activated);
			this.lifecycle.passivateComponent(First.class);
			this.assertState(First.class, ComponentState.passivated);
		}
		this.lifecycle.removeComponent(First.class);
		this.assertState(First.class, ComponentState.removed);
		Assert.assertEquals(Arrays.asList(First.class), removals);
	}

	@Test
	public void removalUsesInstantiationOrderRatherThanDeploymentOrActivationOrder() {
		this.lifecycle.deployComponents(FirstImpl.class, SecondImpl.class);
		this.lifecycle.instantiateComponent(Second.class);
		this.lifecycle.instantiateComponent(First.class);
		this.lifecycle.activateComponent(First.class);
		this.lifecycle.activateComponent(Second.class);
		this.lifecycle.remove();
		Assert.assertEquals(Arrays.asList(First.class, Second.class), removals);
		this.assertState(First.class, ComponentState.removed);
		this.assertState(Second.class, ComponentState.removed);
	}

	@Test
	public void redeploymentRecordsANewInstantiationOrder() {
		this.lifecycle.deployComponents(FirstImpl.class, SecondImpl.class);
		this.lifecycle.activateComponent(First.class);
		this.lifecycle.activateComponent(Second.class);
		this.lifecycle.removeComponent(First.class);
		this.lifecycle.undeployComponent(FirstImpl.class);
		this.assertState(First.class, null);
		this.lifecycle.deployComponent(FirstImpl.class);
		this.lifecycle.activateComponent(First.class);
		removals.clear();
		this.lifecycle.remove();
		Assert.assertEquals(Arrays.asList(First.class, Second.class), removals);
	}

	@Test
	public void compositeTransitionsUpdateEveryComponent() {
		this.lifecycle.deployComponent(ParentImpl.class);
		this.assertTreeState(ComponentState.deployed);
		this.lifecycle.instantiateComponent(Parent.class);
		this.assertTreeState(ComponentState.instantiated);
		this.lifecycle.initializeComponent(Parent.class);
		this.assertTreeState(ComponentState.initialized);
		this.lifecycle.activateComponent(Parent.class);
		this.assertTreeState(ComponentState.activated);
		this.lifecycle.passivateComponent(Parent.class);
		this.assertTreeState(ComponentState.passivated);
		this.lifecycle.removeComponent(Parent.class);
		this.assertTreeState(ComponentState.removed);
		this.lifecycle.undeployComponent(ParentImpl.class);
		this.assertTreeState(null);
	}

	@Test
	public void stopAndRestartTearsDownBusinessButLeavesInternalComponentsActivated() {
		this.lifecycle.deployComponent(FirstImpl.class);
		this.lifecycle.activateComponent(First.class);
		this.container.stop();
		this.assertState(First.class, null);
		// stop() tears down the business cascade, then immediately reactivates
		// the internal (technical) component tree so the container is usable
		// right away - unlike business components, internal ones do not stay
		// passivated between stop() and the next start().
		this.assertState(LifecycleManager.class, ComponentState.activated);
		this.lifecycle.deployComponent(FirstImpl.class);
		this.container.start();
		this.assertState(First.class, ComponentState.activated);
		this.assertState(LifecycleManager.class, ComponentState.activated);
	}

	@Test
	public void failedTransitionsRequireExplicitResetBeforeRestart() {
		this.lifecycle.deployComponent(FirstImpl.class);
		this.lifecycle.instantiateComponent(First.class);
		this.assertFailure(ComponentState.initialized, ComponentState.instantiated,
				() -> this.lifecycle.initializeComponent(First.class));
		this.assertFailure(ComponentState.activated, ComponentState.initialized,
				() -> this.lifecycle.activateComponent(First.class));
		this.assertFailure(ComponentState.passivated, ComponentState.activated,
				() -> this.lifecycle.passivateComponent(First.class));
		this.assertFailure(ComponentState.removed, ComponentState.passivated,
				() -> this.lifecycle.removeComponent(First.class));
		Assert.assertEquals(Arrays.asList(First.class), removals);
	}

	@Test
	public void failedRemovalDoesNotCallApplicationRemovalAgain() {
		this.lifecycle.deployComponents(FirstImpl.class, SecondImpl.class);
		this.lifecycle.activateComponent(First.class);
		this.lifecycle.activateComponent(Second.class);
		failAt = ComponentState.removed;
		Assert.assertThrows(RuntimeException.class, () -> this.lifecycle.removeComponent(First.class));
		this.assertState(First.class, ComponentState.failed);
		failAt = null;
		this.lifecycle.remove();
		Assert.assertEquals(Arrays.asList(Second.class), removals);
	}

	private void assertFailure(final ComponentState target, final ComponentState previous, final Runnable operation) {
		failAt = target;
		Assert.assertThrows(RuntimeException.class, operation::run);
		this.assertState(First.class, ComponentState.failed);
		failAt = null;
		Assert.assertThrows(IllegalStateException.class, () -> this.lifecycle.activateComponent(First.class));
		this.lifecycle.resetFailedComponent(First.class);
		this.assertState(First.class, ComponentState.deployed);
		if (previous == ComponentState.activated || previous == ComponentState.passivated) {
			this.lifecycle.activateComponent(First.class);
		}
		operation.run();
		this.assertState(First.class, target);
	}

	private void assertTreeState(final ComponentState state) {
		this.assertState(Parent.class, state);
		this.assertState(Second.class, state);
	}

	private void assertState(final Class<?> type, final ComponentState state) {
		Assert.assertEquals(state, this.lifecycle.readComponentState(type));
		boolean hasInstance = state == ComponentState.instantiated || state == ComponentState.initialized
				|| state == ComponentState.activated || state == ComponentState.passivated;
		boolean hasInitialized = state == ComponentState.initialized || state == ComponentState.activated
				|| state == ComponentState.passivated;
		Assert.assertEquals("isDeployed", state != null, this.lifecycle.isDeployed(type));
		Assert.assertEquals("isInstantiated", hasInstance, this.lifecycle.isInstantiated(type));
		Assert.assertEquals("isInitialized", hasInitialized, this.lifecycle.isInitialized(type));
		Assert.assertEquals("isActivated", state == ComponentState.activated, this.lifecycle.isActivated(type));
		Assert.assertEquals("isPassivated", state == ComponentState.passivated, this.lifecycle.isPassivated(type));
		Assert.assertEquals("isFailed", state == ComponentState.failed, this.lifecycle.isFailed(type));
		Assert.assertEquals("isRemoved", state == ComponentState.removed, this.lifecycle.isRemoved(type));
		Assert.assertEquals("isUndeployed", state == null, this.lifecycle.isUndeployed(type));
	}

	@Component
	public interface First { }
	@Component
	public interface Second { }
	@Component
	public interface Parent { }

	@Implementation
	public static class FirstImpl implements First {
		@Initialization
		public void initialize() { failIfRequested(ComponentState.initialized); }
		@Activation
		public void activate() { failIfRequested(ComponentState.activated); }
		@Passivation
		public void passivate() { failIfRequested(ComponentState.passivated); }
		@Removal
		public void remove() {
			failIfRequested(ComponentState.removed);
			removals.add(First.class);
		}
		private static void failIfRequested(final ComponentState state) {
			if (failAt == state) {
				throw new IllegalStateException("Expected failure reaching " + state);
			}
		}
	}

	@Implementation
	public static class SecondImpl implements Second {
		@Removal
		public void remove() { removals.add(Second.class); }
	}

	@Implementation
	public static class ParentImpl implements Parent {
		@ComposedComponent
		private final SecondImpl second = null;
	}
}
