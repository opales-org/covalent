/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.ArrayList;
import java.util.List;

import org.opales.covalent.container.lifecycle.impl.LifecycleManagerImpl;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.lifecycle.Initialization;
import org.opales.covalent.container.lifecycle.LifecycleEvents;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.lifecycle.Removal;
import org.opales.covalent.container.tests.CovalentContainerizedTests;

public class LifecycleFailureContainerizedTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	private final LifecycleServices lifecycleServices = Covalent.main.lcs();

	private CapturingLogHandler lifecycleLog;

	@Before
	public void setUp() {
		this.cm.rcs().registerComponent(this);
		this.lifecycleLog = CapturingLogHandler.install(LifecycleManagerImpl.class);
		this.cm.getParameters().setSynchronousEvents(true);
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
	}

	@After
	public void tearDown() {
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.trace);
		this.lifecycleLog.restore();
	}

	@Test
	public void passivationFailureCleansComponentAndMarksItFailed() {
		FailingPassivationComponentImpl.failPassivation = true;
		this.lifecycleServices.deployComponent(FailingPassivationComponentImpl.class);
		this.lifecycleServices.activateComponent(FailingPassivationComponent.class);

		RuntimeException e = Assert.assertThrows("Passivation failure should be propagated.", RuntimeException.class,
				() -> this.lifecycleServices.passivateComponent(FailingPassivationComponent.class));
		Assert.assertTrue(this.isExceptionIncludingText(e, "passivation failure"));

		Assert.assertEquals(ComponentState.failed,
				this.lifecycleServices.readComponentState(FailingPassivationComponent.class));
		Assert.assertTrue(this.lifecycleServices.isFailed(FailingPassivationComponent.class));

		FailingPassivationComponentImpl.failPassivation = false;
	}

	@Test
	public void removalFailureCleansComponentAndMarksItFailed() {
		FailingRemovalComponentImpl.failRemoval = true;
		this.lifecycleServices.deployComponent(FailingRemovalComponentImpl.class);
		this.lifecycleServices.activateComponent(FailingRemovalComponent.class);

		RuntimeException e = Assert.assertThrows("Removal failure should be propagated.", RuntimeException.class,
				() -> this.lifecycleServices.removeComponent(FailingRemovalComponent.class));
		Assert.assertTrue(this.isExceptionIncludingText(e, "removal failure"));

		Assert.assertEquals(ComponentState.failed,
				this.lifecycleServices.readComponentState(FailingRemovalComponent.class));
		Assert.assertTrue(this.lifecycleServices.isFailed(FailingRemovalComponent.class));

		FailingRemovalComponentImpl.failRemoval = false;
	}

	@Test
	public void initializationFailureMarksComponentFailedAndStopSafe() {
		this.lifecycleServices.deployComponent(FailingInitializationComponentImpl.class);

		RuntimeException e = Assert.assertThrows("Initialization failure should be propagated.", RuntimeException.class,
				() -> this.lifecycleServices.initializeComponent(FailingInitializationComponent.class));
		Assert.assertTrue(this.isExceptionIncludingText(e, "initialization failure"));

		Assert.assertEquals(ComponentState.failed,
				this.lifecycleServices.readComponentState(FailingInitializationComponent.class));
		Assert.assertFalse(this.lifecycleServices.isInitialized(FailingInitializationComponent.class));

		this.cm.stop();
		Assert.assertNull(this.lifecycleServices.readComponentState(FailingInitializationComponent.class));

		// This test exercises stop() itself; restart so the base class's own,
		// unconditional stop() below does not run on an already-stopped
		// container.
		this.cm.start();
	}

	@Test
	public void activationFailureMarksComponentFailedAndDoesNotPublishServices() {
		this.lifecycleServices.deployComponent(FailingActivationComponentImpl.class);
		this.lifecycleServices.initializeComponent(FailingActivationComponent.class);

		RuntimeException e = Assert.assertThrows("Activation failure should be propagated.", RuntimeException.class,
				() -> this.lifecycleServices.activateComponent(FailingActivationComponent.class));
		Assert.assertTrue(this.isExceptionIncludingText(e, "activation failure"));

		Assert.assertEquals(ComponentState.failed,
				this.lifecycleServices.readComponentState(FailingActivationComponent.class));
		Assert.assertFalse(this.lifecycleServices.isActivated(FailingActivationComponent.class));

		FailureServices services = Covalent.main.rcs().useServices(FailingActivationComponent.class,
				FailureServices.class);
		IllegalStateException laterFailure = Assert.assertThrows("Services should not be callable after failed activation.",
				IllegalStateException.class,
				() -> services.isAvailable());
		Assert.assertTrue(this.isSurrogateException(laterFailure));
	}

	@Test
	public void stopRemovesFailedComponentAfterActivationFailure() {
		this.lifecycleServices.deployComponent(FailingActivationComponentImpl.class);
		this.lifecycleServices.initializeComponent(FailingActivationComponent.class);

		RuntimeException e = Assert.assertThrows("Activation failure should be propagated.", RuntimeException.class,
				() -> this.lifecycleServices.activateComponent(FailingActivationComponent.class));
		Assert.assertTrue(this.isExceptionIncludingText(e, "activation failure"));

		this.cm.stop();

		Assert.assertNull(this.lifecycleServices.readComponentState(FailingActivationComponent.class));

		// This test exercises stop() itself; restart so the base class's own,
		// unconditional stop() below does not run on an already-stopped
		// container.
		this.cm.start();
	}

	@Test
	public void initializationFailureEmitsInstantiationThenFailure() {
		LifecycleEventRecorder recorder = new LifecycleEventRecorder();
		Covalent.main.rcs().subscribe(LifecycleEvents.class, recorder);
		this.lifecycleServices.deployComponent(FailingInitializationComponentImpl.class);

		RuntimeException e = Assert.assertThrows("Initialization failure should be propagated.", RuntimeException.class,
				() -> this.lifecycleServices.initializeComponent(FailingInitializationComponent.class));
		Assert.assertTrue(this.isExceptionIncludingText(e, "initialization failure"));

		Assert.assertEquals(2, recorder.events.size());
		Assert.assertEquals("instantiated:" + FailingInitializationComponent.class.getSimpleName(), recorder.events.get(0));
		Assert.assertEquals("failed:" + FailingInitializationComponent.class.getSimpleName(), recorder.events.get(1));
	}

	@Test
	public void activationFailureEmitsCompletedTransitionsThenFailure() {
		LifecycleEventRecorder recorder = new LifecycleEventRecorder();
		Covalent.main.rcs().subscribe(LifecycleEvents.class, recorder);
		this.lifecycleServices.deployComponent(FailingActivationComponentImpl.class);

		RuntimeException e = Assert.assertThrows("Activation failure should be propagated.", RuntimeException.class,
				() -> this.lifecycleServices.activateComponent(FailingActivationComponent.class));
		Assert.assertTrue(this.isExceptionIncludingText(e, "activation failure"));

		Assert.assertEquals(3, recorder.events.size());
		Assert.assertEquals("instantiated:" + FailingActivationComponent.class.getSimpleName(), recorder.events.get(0));
		Assert.assertEquals("initialized:" + FailingActivationComponent.class.getSimpleName(), recorder.events.get(1));
		Assert.assertEquals("failed:" + FailingActivationComponent.class.getSimpleName(), recorder.events.get(2));
	}

	@Component
	public interface FailingInitializationComponent {
	}

	@Implementation
	public static class FailingInitializationComponentImpl implements FailingInitializationComponent {
		@Initialization
		public void initialize() {
			throw new IllegalStateException("initialization failure");
		}
	}

	@Component
	public interface FailingActivationComponent {
	}

	@Services
	public interface FailureServices {
		boolean isAvailable();
	}

	@Implementation
	public static class FailingActivationComponentImpl implements FailingActivationComponent, FailureServices {
		@Activation
		public void activate() {
			throw new IllegalStateException("activation failure");
		}

		@Override
		public boolean isAvailable() {
			return true;
		}
	}

	@Component
	public interface FailingPassivationComponent {
	}

	@Implementation
	public static class FailingPassivationComponentImpl implements FailingPassivationComponent {
		static boolean failPassivation;

		@Passivation
		public void passivate() {
			if (failPassivation) {
				throw new IllegalStateException("passivation failure");
			}
		}
	}

	@Component
	public interface FailingRemovalComponent {
	}

	@Implementation
	public static class FailingRemovalComponentImpl implements FailingRemovalComponent {
		static boolean failRemoval;

		@Removal
		public void remove() {
			if (failRemoval) {
				throw new IllegalStateException("removal failure");
			}
		}
	}

	public static class LifecycleEventRecorder implements LifecycleEvents {
		final List<String> events = new ArrayList<>();

		@Override
		public void onComponentInstantiated(final Class<?> componentType) {
			this.events.add("instantiated:" + componentType.getSimpleName());
		}

		@Override
		public void onComponentInitialized(final Class<?> componentType) {
			this.events.add("initialized:" + componentType.getSimpleName());
		}

		@Override
		public void onComponentActivated(final Class<?> componentType) {
			this.events.add("activated:" + componentType.getSimpleName());
		}

		@Override
		public void onComponentPassivated(final Class<?> componentType) {
			this.events.add("passivated:" + componentType.getSimpleName());
		}

		@Override
		public void onComponentRemoved(final Class<?> componentType) {
			this.events.add("removed:" + componentType.getSimpleName());
		}

		@Override
		public void onComponentFailed(final Class<?> componentType) {
			this.events.add("failed:" + componentType.getSimpleName());
		}
	}
}
