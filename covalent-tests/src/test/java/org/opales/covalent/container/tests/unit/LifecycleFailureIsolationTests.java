/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.List;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.exceptions.ComponentLifecycleException;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.ComposedComponent;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.lifecycle.Initialization;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.lifecycle.Removal;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.lifecycle.LifecycleParameters;
import org.opales.covalent.container.lifecycle.LifecycleServices.StartMode;
import org.opales.covalent.container.lifecycle.impl.LifecycleManagerImpl;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;

/**
 * A callback failing in one component is isolated to that component (and to
 * its composite), in both start modes: the other components still reach their
 * target state, stopping the container still tears everything down, and the
 * direct lifecycle operations report the failure with a message naming the
 * phase and the component.
 */
@RunWith(Parameterized.class)
public class LifecycleFailureIsolationTests {

	private CapturingLogHandler lifecycleManagerLogs;

	@Parameterized.Parameters(name = "{0}")
	public static Object[] modes() {
		return StartMode.values();
	}

	private final StartMode mode;

	private CovalentContainer container;

	public LifecycleFailureIsolationTests(final StartMode mode) {
		this.mode = mode;
	}

	@Before
	public void setUp() {
		this.lifecycleManagerLogs = CapturingLogHandler.install(LifecycleManagerImpl.class);
		FailingConstructorImpl.fail = true;
		FailingInitializationImpl.fail = true;
		FailingActivationImpl.fail = true;
		FailingActivationImpl.removals = 0;
		FailingPassivationImpl.passivations = 0;
		FailingRemovalImpl.removals = 0;
		this.container = Covalent.container();
		((LifecycleParameters) this.container.lcs()).setStartMode(this.mode);
	}

	@After
	public void tearDown() {
		if (this.container != null) {
			this.container.dispose();
		}
		this.lifecycleManagerLogs.restore();
	}

	@Test
	public void aFailingConstructorFailsOnlyItsOwnComponent() {
		this.container.deployComponents(FailingConstructorImpl.class, HealthyImpl.class);

		this.container.start();

		Assert.assertEquals(ComponentState.failed, this.container.lcs().readComponentState(FailingConstructor.class));
		Assert.assertEquals(ComponentState.activated, this.container.lcs().readComponentState(Healthy.class));
		Assert.assertTrue(this.container.isStarted());
	}

	@Test
	public void aFailingInitializationFailsOnlyItsOwnComponent() {
		this.container.deployComponents(FailingInitializationImpl.class, HealthyImpl.class);

		this.container.start();

		Assert.assertEquals(ComponentState.failed, this.container.lcs().readComponentState(FailingInitialization.class));
		Assert.assertEquals(ComponentState.activated, this.container.lcs().readComponentState(Healthy.class));
	}

	@Test
	public void aFailingActivationFailsOnlyItsOwnComponent() {
		this.container.deployComponents(FailingActivationImpl.class, HealthyImpl.class);

		this.container.start();

		Assert.assertEquals(ComponentState.failed, this.container.lcs().readComponentState(FailingActivation.class));
		Assert.assertEquals(ComponentState.activated, this.container.lcs().readComponentState(Healthy.class));
		String phase = this.mode == StartMode.vertical ? "vertical start" : "activation";
		Assert.assertEquals(List.of("Component FailingActivation failed; cleaning composite FailingActivation.",
				"Component FailingActivation failed during " + phase + "."), this.lifecycleManagerLogs.messages(Level.SEVERE));
	}

	@Test
	public void aFailingComposedChildFailsTheWholeCompositeAndResetRestoresIt() {
		this.container.deployComponents(CompositeParentImpl.class, HealthyImpl.class);

		this.container.start();

		Assert.assertEquals(ComponentState.failed, this.container.lcs().readComponentState(CompositeParent.class));
		Assert.assertEquals(ComponentState.failed, this.container.lcs().readComponentState(FailingInitialization.class));
		Assert.assertEquals(ComponentState.activated, this.container.lcs().readComponentState(Healthy.class));

		this.container.lcs().resetFailedComponent(FailingInitialization.class);

		Assert.assertEquals(ComponentState.deployed, this.container.lcs().readComponentState(CompositeParent.class));
		Assert.assertEquals(ComponentState.deployed, this.container.lcs().readComponentState(FailingInitialization.class));
	}

	@Test
	public void stoppingTearsDownEveryComponentWhateverFailsDuringTeardown() {
		this.container.deployComponents(FailingPassivationImpl.class, FailingRemovalImpl.class, HealthyImpl.class);
		this.container.start();
		Assert.assertEquals(ComponentState.activated, this.container.lcs().readComponentState(FailingPassivation.class));

		this.container.stop();

		Assert.assertFalse(this.container.isStarted());
		Assert.assertFalse(this.container.lcs().isDeployed(FailingPassivation.class));
		Assert.assertFalse(this.container.lcs().isDeployed(FailingRemoval.class));
		Assert.assertFalse(this.container.lcs().isDeployed(Healthy.class));
		Assert.assertEquals(1, FailingPassivationImpl.passivations);
		Assert.assertEquals(1, FailingRemovalImpl.removals);
	}

	@Test
	public void stoppingAfterAFailedStartRemovesTheFailedComponent() {
		this.container.deployComponents(FailingActivationImpl.class, HealthyImpl.class);
		this.container.start();

		this.container.stop();

		Assert.assertFalse(this.container.lcs().isDeployed(FailingActivation.class));
		Assert.assertFalse(this.container.lcs().isDeployed(Healthy.class));
	}

	@Test
	public void instantiatingAFailedComponentIsRejectedUntilItIsReset() {
		this.container.deployComponents(FailingConstructorImpl.class);
		this.container.start();

		IllegalStateException e = Assert.assertThrows("expected IllegalStateException", IllegalStateException.class,
				() -> this.container.lcs().instantiateComponent(FailingConstructor.class));
		Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("Component failed; reset it explicitly"));
	}

	@Test
	public void aDirectInstantiationFailureNamesTheInstantiationPhase() {
		this.container.deployComponents(FailingConstructorImpl.class);

		RuntimeException e = Assert.assertThrows("expected RuntimeException", RuntimeException.class,
				() -> this.container.lcs().instantiateComponent(FailingConstructor.class));
		Assert.assertTrue(e.toString(), e instanceof ComponentLifecycleException);
		Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("During instantiation of component FailingConstructor"));
		Assert.assertTrue(this.container.lcs().isFailed(FailingConstructor.class));
	}

	@Test
	public void initializingAComponentWhoseConstructorFailsReportsTheInstantiationFailureUnchanged() {
		this.container.deployComponents(FailingConstructorImpl.class);

		RuntimeException e = Assert.assertThrows("expected RuntimeException", RuntimeException.class,
				() -> this.container.lcs().initializeComponent(FailingConstructor.class));
		Assert.assertTrue(e.toString(), e instanceof ComponentLifecycleException);
		Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("During instantiation of component FailingConstructor"));
	}

	@Test
	public void activatingAComponentWhoseConstructorFailsReportsTheInstantiationFailureUnchanged() {
		this.container.deployComponents(FailingConstructorImpl.class);

		RuntimeException e = Assert.assertThrows("expected RuntimeException", RuntimeException.class,
				() -> this.container.lcs().activateComponent(FailingConstructor.class));
		Assert.assertTrue(e.toString(), e instanceof ComponentLifecycleException);
		Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("During instantiation of component FailingConstructor"));
	}

	@Test
	public void aDirectInitializationFailureNamesTheInitializationPhase() {
		this.container.deployComponents(FailingInitializationImpl.class);

		RuntimeException e = Assert.assertThrows("expected RuntimeException", RuntimeException.class,
				() -> this.container.lcs().initializeComponent(FailingInitialization.class));
		Assert.assertTrue(e.toString(), e instanceof ComponentLifecycleException);
		Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("During initialization of component FailingInitialization"));
		Assert.assertTrue(e.getMessage(), e.getMessage().contains("Exception in lifecycle callback"));
	}

	@Test
	public void aDirectActivationFailureNamesTheActivationPhase() {
		this.container.deployComponents(FailingActivationImpl.class);

		RuntimeException e = Assert.assertThrows("expected RuntimeException", RuntimeException.class,
				() -> this.container.lcs().activateComponent(FailingActivation.class));
		Assert.assertTrue(e.toString(), e instanceof ComponentLifecycleException);
		Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("During activation of component FailingActivation"));
	}

	@Test
	public void aDirectPassivationFailureNamesThePassivationPhase() {
		this.container.deployComponents(FailingPassivationImpl.class);
		this.container.start();

		RuntimeException e = Assert.assertThrows("expected RuntimeException", RuntimeException.class,
				() -> this.container.lcs().passivateComponent(FailingPassivation.class));
		Assert.assertTrue(e.toString(), e instanceof ComponentLifecycleException);
		Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("During passivation of component FailingPassivation"));
		Assert.assertTrue(this.container.lcs().isFailed(FailingPassivation.class));
	}

	@Test
	public void aDirectRemovalFailureNamesTheRemovalPhase() {
		this.container.deployComponents(FailingRemovalImpl.class);
		this.container.start();

		RuntimeException e = Assert.assertThrows("expected RuntimeException", RuntimeException.class,
				() -> this.container.lcs().removeComponent(FailingRemoval.class));
		Assert.assertTrue(e.toString(), e instanceof ComponentLifecycleException);
		Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("During removal of component FailingRemoval"));
		Assert.assertTrue(this.container.lcs().isFailed(FailingRemoval.class));
	}

	@Test
	public void removingAFailedComponentCleansItAndMarksItRemoved() {
		this.container.deployComponents(FailingActivationImpl.class);
		this.container.start();
		Assert.assertTrue(this.container.lcs().isFailed(FailingActivation.class));

		this.container.lcs().removeComponent(FailingActivation.class);

		Assert.assertTrue(this.container.lcs().isRemoved(FailingActivation.class));
		Assert.assertFalse(this.container.lcs().isFailed(FailingActivation.class));
		Assert.assertEquals(0, FailingActivationImpl.removals);
	}

	@Test
	public void passivatingAComponentThatWasNeverInitializedLeavesItDeployed() {
		this.container.deployComponents(HealthyImpl.class);

		this.container.lcs().passivateComponent(Healthy.class);

		Assert.assertEquals(ComponentState.deployed, this.container.lcs().readComponentState(Healthy.class));
	}

	@Test
	public void removingAComponentThatWasNeverInstantiatedLeavesItDeployed() {
		this.container.deployComponents(HealthyImpl.class);

		this.container.lcs().removeComponent(Healthy.class);

		Assert.assertEquals(ComponentState.deployed, this.container.lcs().readComponentState(Healthy.class));
	}

	@Test
	public void removingAComponentTwiceIsAStrictDynamicVerificationFailure() {
		this.container.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		this.container.deployComponents(HealthyImpl.class);
		this.container.start();
		this.container.lcs().removeComponent(Healthy.class);

		RuntimeException e = Assert.assertThrows("expected RuntimeException", RuntimeException.class,
				() -> this.container.lcs().removeComponent(Healthy.class));
		Assert.assertTrue(e.getMessage(), e.getMessage().contains("already removed"));
	}

	@Test
	public void removingAComponentTwiceIsIgnoredWhenDynamicVerificationIsSilent() {
		this.container.getParameters().setDynamicRulesChecking(VerificationLevel.silent);
		this.container.deployComponents(HealthyImpl.class);
		this.container.start();
		this.container.lcs().removeComponent(Healthy.class);

		this.container.lcs().removeComponent(Healthy.class);

		Assert.assertTrue(this.container.lcs().isRemoved(Healthy.class));
	}

	// --- Fixtures ---

	@Component
	public interface Healthy {
	}

	@Implementation
	public static class HealthyImpl implements Healthy {
	}

	@Component
	public interface FailingConstructor {
	}

	@Implementation
	public static class FailingConstructorImpl implements FailingConstructor {

		static volatile boolean fail;

		public FailingConstructorImpl() {
			if (fail) {
				throw new IllegalStateException("constructor failure");
			}
		}
	}

	@Component
	public interface FailingInitialization {
	}

	@Implementation
	public static class FailingInitializationImpl implements FailingInitialization {

		static volatile boolean fail;

		@Initialization
		public void initialize() {
			if (fail) {
				throw new IllegalStateException("initialization failure");
			}
		}
	}

	@Component
	public interface FailingActivation {
	}

	@Implementation
	public static class FailingActivationImpl implements FailingActivation {

		static volatile boolean fail;

		static int removals;

		@Activation
		public void activate() {
			if (fail) {
				throw new IllegalStateException("activation failure");
			}
		}

		@Removal
		public void remove() {
			removals++;
		}
	}

	@Component
	public interface FailingPassivation {
	}

	@Implementation
	public static class FailingPassivationImpl implements FailingPassivation {

		static int passivations;

		@Passivation
		public void passivate() {
			passivations++;
			throw new IllegalStateException("passivation failure");
		}
	}

	@Component
	public interface FailingRemoval {
	}

	@Implementation
	public static class FailingRemovalImpl implements FailingRemoval {

		static int removals;

		@Removal
		public void remove() {
			removals++;
			throw new IllegalStateException("removal failure");
		}
	}

	@Component
	public interface CompositeParent {
	}

	@Implementation
	public static class CompositeParentImpl implements CompositeParent {

		@ComposedComponent
		private final FailingInitializationImpl child = null;
	}
}
