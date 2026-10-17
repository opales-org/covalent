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
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.exceptions.ComponentLifecycleException;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.lifecycle.LifecycleParameters;
import org.opales.covalent.container.lifecycle.LifecycleServices.StartMode;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.lifecycle.Removal;
import org.opales.covalent.container.lifecycle.impl.LifecycleManagerImpl;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;

/**
 * Error paths of the lifecycle manager and of the component registry, seen
 * through the public lifecycle and registry services: misuse (wrong
 * argument, wrong state) is reported with an explicit exception, and a
 * failing callback never prevents the rest of the container from going on
 * or from stopping.
 */
public class LifecycleErrorPathsTests {

	private CapturingLogHandler lifecycleManagerLogs;

	private CovalentContainer container;

	@Before
	public void setUp() {
		this.lifecycleManagerLogs = CapturingLogHandler.install(LifecycleManagerImpl.class);
		this.container = Covalent.container();
	}

	@After
	public void tearDown() {
		FailingActivationImpl.fail = false;
		if (this.container != null) {
			this.container.dispose();
		}
		this.lifecycleManagerLogs.restore();
	}

	// --- Registry ---

	@Test
	public void registryRejectsAnUnregisteredComponentType() {
		this.container.rcs().registerComponent(this);
		this.container.start();
		RegistryReadServices registry = this.container.rcs().useServices(RegistryReadServices.class);

		IllegalArgumentException e = Assert.assertThrows("expected IllegalArgumentException",
				IllegalArgumentException.class,
				() -> registry.getComponentInstance(Plain.class));
		Assert.assertTrue(e.getMessage(), e.getMessage().contains("Plain is not registered"));
	}

	@Test
	public void registryRejectsAnImplementationInsteadOfAComponentType() {
		this.container.rcs().registerComponent(this);
		this.container.start();
		RegistryReadServices registry = this.container.rcs().useServices(RegistryReadServices.class);

		IllegalStateException e = Assert.assertThrows("expected IllegalStateException", IllegalStateException.class,
				() -> registry.getComponentInstance(PlainImpl.class));
		Assert.assertTrue(e.getMessage(), e.getMessage().contains("component implementation: PlainImpl"));
	}

	@Test
	public void registryReturnsTheInstanceOfADeployedStartedComponent() {
		this.container.deployComponents(PlainImpl.class);
		this.container.rcs().registerComponent(this);
		this.container.start();
		RegistryReadServices registry = this.container.rcs().useServices(RegistryReadServices.class);

		Assert.assertTrue(registry.getComponentInstance(Plain.class) instanceof PlainImpl);
	}

	// --- Misuse ---

	@Test
	public void undeployingAComponentTypeInsteadOfItsImplementationIsRejected() {
		this.container.deployComponents(PlainImpl.class);
		this.container.start();

		IllegalStateException e = Assert.assertThrows("expected IllegalStateException", IllegalStateException.class,
				() -> this.container.lcs().undeployComponent(Plain.class));
		Assert.assertTrue(e.getMessage(), e.getMessage().contains("Trying to undeploy a component type"));
		Assert.assertTrue(this.container.lcs().isDeployed(Plain.class));
	}

	@Test
	public void undeployingAnImplementationThatWasNeverDeployedIsRejected() {
		this.container.start();

		IllegalStateException e = Assert.assertThrows("expected IllegalStateException", IllegalStateException.class,
				() -> this.container.lcs().undeployComponent(PlainImpl.class));
		Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("Not deployed"));
	}

	@Test
	public void undeployingTwiceIsRejected() {
		this.container.deployComponents(PlainImpl.class);
		this.container.start();
		this.container.lcs().undeployComponent(PlainImpl.class);

		IllegalStateException e = Assert.assertThrows("expected IllegalStateException", IllegalStateException.class,
				() -> this.container.lcs().undeployComponent(PlainImpl.class));
		Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("Not deployed"));
	}

	@Test
	public void instantiatingAnAlreadyInstantiatedComponentIsRejected() {
		this.container.deployComponents(PlainImpl.class);
		this.container.start();

		IllegalStateException e = Assert.assertThrows("expected IllegalStateException", IllegalStateException.class,
				() -> this.container.lcs().instantiateComponent(Plain.class));
		Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("Already instantiated"));
	}

	@Test
	public void activatingAnAlreadyActivatedComponentIsRejected() {
		this.container.deployComponents(PlainImpl.class);
		this.container.start();

		RuntimeException e = Assert.assertThrows("expected an exception", RuntimeException.class,
				() -> this.container.lcs().activateComponent(Plain.class));
		Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("During activation of component Plain"));
		Assert.assertTrue(e.getMessage(), e.getMessage().contains("Already activated"));
	}

	@Test
	public void initializingAnAlreadyInitializedComponentIsRejected() {
		this.container.deployComponents(PlainImpl.class);
		this.container.start();

		RuntimeException e = Assert.assertThrows("expected an exception", RuntimeException.class,
				() -> this.container.lcs().initializeComponent(Plain.class));
		Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("During initialization of component Plain"));
		Assert.assertTrue(e.getMessage(), e.getMessage().contains("Already initialized"));
	}

	@Test
	public void passivatingAnAlreadyPassivatedComponentIsRejected() {
		this.container.deployComponents(PlainImpl.class);
		this.container.start();
		this.container.lcs().passivateComponent(Plain.class);

		RuntimeException e = Assert.assertThrows("expected an exception", RuntimeException.class,
				() -> this.container.lcs().passivateComponent(Plain.class));
		Assert.assertTrue(e.getMessage(), e.getMessage().contains("Already passivated"));
	}

	@Test
	public void activatingAComponentThatIsNotDeployedIsRejected() {
		this.container.start();

		RuntimeException e = Assert.assertThrows("expected an exception", RuntimeException.class,
				() -> this.container.lcs().activateComponent(Plain.class));
		Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("During activation of component Plain"));
	}

	@Test
	public void deployingTheSameComponentTwiceIsRejected() {
		this.container.deployComponents(PlainImpl.class);

		ComponentLifecycleException failure = Assert.assertThrows(ComponentLifecycleException.class,
				() -> this.container.deployComponents(PlainImpl.class));
		Assert.assertTrue(failure.getMessage(), failure.getMessage().startsWith("During deployment of PlainImpl"));
		Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("is already deployed"));
	}

	@Test
	public void deployingAComponentTypeInsteadOfAnImplementationIsRejected() {
		ComponentLifecycleException failure = Assert.assertThrows(ComponentLifecycleException.class,
				() -> this.container.deployComponents(Plain.class));
		Assert.assertTrue(failure.getMessage(), failure.getMessage().startsWith("During deployment of Plain"));
		Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("should be an implementation"));
	}

	@Test
	public void aNullStartModeIsRejectedAndTheCurrentOneIsKept() {
		this.container.rcs().registerComponent(this);
		LifecycleParameters parameters = this.container.rcs().accessParameters(LifecycleParameters.class);
		parameters.setStartMode(StartMode.horizontal);

		IllegalArgumentException e = Assert.assertThrows("expected IllegalArgumentException",
				IllegalArgumentException.class,
				() -> parameters.setStartMode(null));
		Assert.assertTrue(e.getMessage(), e.getMessage().contains("Start mode must not be null"));

		Assert.assertEquals(StartMode.horizontal, parameters.getStartMode());
	}

	// --- Failing callbacks ---

	@Test
	public void aFailingActivationMarksTheComponentFailedAndBlocksFurtherTransitionsUntilReset() {
		this.container.deployComponents(FailingActivationImpl.class);
		FailingActivationImpl.fail = true;
		// the failure is isolated to the component: start() itself does not throw
		this.container.start();
		Assert.assertTrue(this.container.lcs().isFailed(FailingActivation.class));
		Assert.assertEquals(ComponentState.failed, this.container.lcs().readComponentState(FailingActivation.class));

		IllegalStateException e = Assert.assertThrows("expected IllegalStateException", IllegalStateException.class,
				() -> this.container.lcs().activateComponent(FailingActivation.class));
		Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("Component failed; reset it explicitly"));

		FailingActivationImpl.fail = false;
		this.container.lcs().resetFailedComponent(FailingActivation.class);

		Assert.assertFalse(this.container.lcs().isFailed(FailingActivation.class));
		Assert.assertEquals(ComponentState.deployed, this.container.lcs().readComponentState(FailingActivation.class));
		Assert.assertEquals(List.of("Component FailingActivation failed; cleaning composite FailingActivation.",
				"Component FailingActivation failed during vertical start."), this.lifecycleManagerLogs.messages(Level.SEVERE));
	}

	@Test
	public void resettingAComponentThatIsNotFailedIsRejected() {
		this.container.deployComponents(PlainImpl.class);
		this.container.start();

		IllegalStateException e = Assert.assertThrows("expected IllegalStateException", IllegalStateException.class,
				() -> this.container.lcs().resetFailedComponent(Plain.class));
		Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("Composite is not failed"));
	}

	@Test
	public void failingPassivationAndRemovalCallbacksDoNotPreventStopOrDispose() {
		this.container.deployComponents(FailingTeardownImpl.class, PlainImpl.class);
		this.container.start();

		// the failures are isolated to the component: stop() itself does not throw
		this.container.stop();

		Assert.assertFalse(this.container.isStarted());
		Assert.assertEquals(List.of("Component FailingTeardown failed; cleaning composite FailingTeardown."),
				this.lifecycleManagerLogs.messages(Level.SEVERE));
		this.container.dispose();
		Assert.assertTrue(this.container.isDisposed());
		this.container = null;
	}

	// --- Fixtures ---

	@Component
	public interface Plain {
	}

	@Implementation
	public static class PlainImpl implements Plain {
	}

	@Component
	public interface FailingActivation {
	}

	@Implementation
	public static class FailingActivationImpl implements FailingActivation {

		static volatile boolean fail;

		@Activation
		public void activate() {
			if (fail) {
				throw new IllegalStateException("activation failure");
			}
		}
	}

	@Component
	public interface FailingTeardown {
	}

	@Implementation
	public static class FailingTeardownImpl implements FailingTeardown {

		@Passivation
		public void passivate() {
			throw new IllegalStateException("passivation failure");
		}

		@Removal
		public void remove() {
			throw new IllegalStateException("removal failure");
		}
	}
}
