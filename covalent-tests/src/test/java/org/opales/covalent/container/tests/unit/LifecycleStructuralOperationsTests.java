/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.ArrayList;
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
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.ComposedComponent;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.lifecycle.Initialization;
import org.opales.covalent.container.lifecycle.LifecycleManager;
import org.opales.covalent.container.lifecycle.LifecycleParameters;
import org.opales.covalent.container.lifecycle.LifecycleServices.StartMode;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.lifecycle.Removal;
import org.opales.covalent.container.lifecycle.impl.LifecycleManagerImpl;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;

/**
 * Lifecycle operations applied to components that have already been moved
 * along their lifecycle by hand and to deployments that violate the structural rules: the
 * container-wide start and stop skip what is already done, undeploying an
 * active component tears it down first, and misuse is reported with a message
 * that names the offending component. Run in both start modes.
 */
@RunWith(Parameterized.class)
public class LifecycleStructuralOperationsTests {

	@Parameterized.Parameters(name = "{0}")
	public static Object[] modes() {
		return StartMode.values();
	}

	private static final List<String> calls = new ArrayList<>();

	private final StartMode mode;

	private CovalentContainer container;

	public LifecycleStructuralOperationsTests(final StartMode mode) {
		this.mode = mode;
	}

	private CapturingLogHandler lifecycleManagerLogs;

	@Before
	public void setUp() {
		this.lifecycleManagerLogs = CapturingLogHandler.install(LifecycleManagerImpl.class);
		calls.clear();
		FailingStartImpl.fail = true;
		this.container = Covalent.container();
		this.container.rcs().registerComponent(this);
		((LifecycleParameters) this.container.lcs()).setStartMode(this.mode);
	}

	@After
	public void tearDown() {
		if (this.container != null) {
			this.container.dispose();
		}
		this.lifecycleManagerLogs.restore();
	}

	private LifecycleManagerImpl lifecycleManager() {
		return (LifecycleManagerImpl) this.container.rcs().useServices(RegistryReadServices.class)
				.getComponentInstance(LifecycleManager.class);
	}

	// --- Container-wide operations skip what is already done ---

	@Test
	public void startingAComponentActivatedByHandActivatesItOnlyOnce() {
		this.container.deployComponents(TrackedImpl.class);
		this.container.lcs().activateComponent(Tracked.class);

		this.container.start();

		Assert.assertEquals(List.of("initialize", "activate"), calls);
		Assert.assertEquals(ComponentState.activated, this.container.lcs().readComponentState(Tracked.class));
	}

	@Test
	public void startingAComponentInitializedByHandSkipsItsInitialization() {
		this.container.deployComponents(TrackedImpl.class);
		this.container.lcs().initializeComponent(Tracked.class);

		this.container.start();

		Assert.assertEquals(List.of("initialize", "activate"), calls);
	}

	@Test
	public void startingAComponentInstantiatedByHandStillInitializesAndActivatesIt() {
		this.container.deployComponents(TrackedImpl.class);
		this.container.lcs().instantiateComponent(Tracked.class);

		this.container.start();

		Assert.assertEquals(List.of("initialize", "activate"), calls);
	}

	@Test
	public void stoppingAfterAComponentWasPassivatedByHandPassivatesItOnlyOnce() {
		this.container.deployComponents(TrackedImpl.class);
		this.container.start();
		this.container.lcs().passivateComponent(Tracked.class);

		this.container.stop();

		Assert.assertEquals(List.of("initialize", "activate", "passivate", "remove"), calls);
		Assert.assertFalse(this.container.lcs().isDeployed(Tracked.class));
	}

	@Test
	public void stoppingAfterAComponentWasRemovedByHandRemovesItOnlyOnce() {
		this.container.deployComponents(TrackedImpl.class);
		this.container.start();
		this.container.lcs().removeComponent(Tracked.class);

		this.container.stop();

		Assert.assertEquals(List.of("initialize", "activate", "passivate", "remove"), calls);
		Assert.assertFalse(this.container.lcs().isDeployed(Tracked.class));
	}

	@Test
	public void stoppingRemovesAComponentThatFailedAtStartWithoutRunningItsRemovalCallback() {
		this.container.deployComponents(FailingStartImpl.class);
		this.container.start();
		Assert.assertTrue(this.container.lcs().isFailed(FailingStart.class));

		this.container.stop();

		Assert.assertFalse(this.container.lcs().isDeployed(FailingStart.class));
		Assert.assertFalse(calls.contains("remove"));
	}

	@Test
	public void startingDoesNotRetryAComponentThatAlreadyFailed() {
		this.container.deployComponents(FailingStartImpl.class);
		RuntimeException e = Assert.assertThrows("expected RuntimeException", RuntimeException.class,
				() -> this.container.lcs().activateComponent(FailingStart.class));
		Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("During activation"));
		FailingStartImpl.fail = false;

		this.container.start();

		Assert.assertTrue(this.container.lcs().isFailed(FailingStart.class));
		Assert.assertEquals(List.of("attempt"), calls);
	}

	@Test
	public void aComponentRedeployedAfterAFailedStartIsActivatedByTheNextStart() {
		this.container.deployComponents(FailingStartImpl.class);
		this.container.start();
		this.container.stop();
		FailingStartImpl.fail = false;
		this.container.deployComponents(FailingStartImpl.class);

		this.container.start();

		Assert.assertEquals(ComponentState.activated, this.container.lcs().readComponentState(FailingStart.class));
		String phase = this.mode == StartMode.vertical ? "vertical start" : "activation";
		Assert.assertEquals(List.of("Component FailingStart failed; cleaning composite FailingStart.",
				"Component FailingStart failed during " + phase + "."), this.lifecycleManagerLogs.messages(Level.SEVERE));
	}

	// --- Start and stop guards ---

	@Test
	public void startingAnAlreadyStartedContainerIsRejected() {
		this.container.start();

		IllegalStateException e = Assert.assertThrows("expected IllegalStateException", IllegalStateException.class,
				() -> this.lifecycleManager().start());
		Assert.assertEquals("Already started", e.getMessage());
		Assert.assertTrue(this.container.isStarted());
	}

	@Test
	public void stoppingAnAlreadyStoppedContainerIsRejected() {
		this.container.start();
		LifecycleManagerImpl manager = this.lifecycleManager();
		this.container.stop();

		IllegalStateException e = Assert.assertThrows("expected IllegalStateException", IllegalStateException.class,
				() -> manager.stop());
		Assert.assertEquals("Not started", e.getMessage());
	}

	@Test
	public void startModeIsReadBack() {
		Assert.assertEquals(this.mode, ((LifecycleParameters) this.container.lcs()).getStartMode());
	}

	@Test
	public void theComponentTypeOfAnImplementationIsItsComponentInterface() {
		this.container.deployComponents(TrackedImpl.class);

		Assert.assertEquals(Tracked.class, this.container.lcs().readComponentType(TrackedImpl.class));
	}

	// --- Undeploying ---

	@Test
	public void undeployingAnActiveComponentPassivatesRemovesThenForgetsIt() {
		this.container.deployComponents(TrackedImpl.class);
		this.container.start();

		this.container.lcs().undeployComponent(TrackedImpl.class);

		Assert.assertEquals(List.of("initialize", "activate", "passivate", "remove"), calls);
		Assert.assertFalse(this.container.lcs().isDeployed(Tracked.class));
		Assert.assertTrue(this.container.lcs().isUndeployed(Tracked.class));
		Assert.assertNull(this.container.lcs().readComponentState(Tracked.class));
	}

	@Test
	public void undeployingAFailedComponentCleansItWithoutRunningItsRemovalCallback() {
		this.container.deployComponents(FailingStartImpl.class);
		this.container.start();
		Assert.assertTrue(this.container.lcs().isFailed(FailingStart.class));

		this.container.lcs().undeployComponent(FailingStartImpl.class);

		Assert.assertFalse(this.container.lcs().isDeployed(FailingStart.class));
		Assert.assertFalse(calls.contains("remove"));
	}

	@Test
	public void undeployingAComponentThatWasOnlyDeployedLeavesNoCallbackTrace() {
		this.container.deployComponents(TrackedImpl.class);

		this.container.lcs().undeployComponent(TrackedImpl.class);

		Assert.assertTrue(calls.isEmpty());
		Assert.assertFalse(this.container.lcs().isDeployed(Tracked.class));
	}

	@Test
	public void undeployingACompositeUndeploysItsChildren() {
		this.container.deployComponents(ParentImpl.class);
		this.container.start();

		this.container.lcs().undeployComponent(ParentImpl.class);

		Assert.assertFalse(this.container.lcs().isDeployed(Parent.class));
		Assert.assertFalse(this.container.lcs().isDeployed(Child.class));
	}

	// --- Deployment structure ---

	@Test
	public void deployingAnImplementationWhoseInterfaceIsNotAComponentIsAStrictStructuralFailure() {
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.strict);

		RuntimeException e = Assert.assertThrows("expected RuntimeException", RuntimeException.class,
				() -> this.container.deployComponents(UntypedImpl.class));
		Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("During deployment of UntypedImpl"));
		Assert.assertTrue(e.getMessage(), e.getMessage().contains("has no component type"));
	}

	@Test
	public void deployingAnExplicitTypeAndImplementationRegistersThem() {
		this.lifecycleManager().deployComponent(Tracked.class, TrackedImpl.class);

		Assert.assertTrue(this.container.lcs().isDeployed(Tracked.class));
		Assert.assertEquals(ComponentState.deployed, this.container.lcs().readComponentState(Tracked.class));
	}

	@Test
	public void deployingAComponentTypeAsImplementationIsRejected() {
		IllegalArgumentException e = Assert.assertThrows("expected IllegalArgumentException",
				IllegalArgumentException.class,
				() -> this.lifecycleManager().deployComponent(Tracked.class, Tracked.class));
		Assert.assertTrue(e.getMessage(), e.getMessage().contains("is indeed a component type"));
	}

	@Test
	public void pseudoInstantiationFromOutsideTheContainerIsRejected() {
		this.container.start();

		IllegalStateException e = Assert.assertThrows("expected IllegalStateException", IllegalStateException.class,
				() -> this.lifecycleManager().pseudoInstantiateComponent(Tracked.class, new TrackedImpl()));
		Assert.assertTrue(e.getMessage(), e.getMessage().contains("cannot be called from outside the container"));
	}

	// --- Fixtures ---

	@Component
	public interface Tracked {
	}

	@Implementation
	public static class TrackedImpl implements Tracked {

		@Initialization
		public void initialize() {
			calls.add("initialize");
		}

		@Activation
		public void activate() {
			calls.add("activate");
		}

		@Passivation
		public void passivate() {
			calls.add("passivate");
		}

		@Removal
		public void remove() {
			calls.add("remove");
		}
	}

	@Component
	public interface FailingStart {
	}

	@Implementation
	public static class FailingStartImpl implements FailingStart {

		static volatile boolean fail = true;

		@Activation
		public void activate() {
			calls.add("attempt");
			if (fail) {
				throw new IllegalStateException("activation failure");
			}
		}

		@Removal
		public void remove() {
			calls.add("remove");
		}
	}

	@Component
	public interface Parent {
	}

	@Component
	public interface Child {
	}

	@Implementation
	public static class ParentImpl implements Parent {

		@ComposedComponent
		private final ChildImpl child = null;
	}

	@Implementation
	public static class ChildImpl implements Child {
	}

	public interface NotAComponent {
	}

	@Implementation
	public static class UntypedImpl implements NotAComponent {
	}
}
