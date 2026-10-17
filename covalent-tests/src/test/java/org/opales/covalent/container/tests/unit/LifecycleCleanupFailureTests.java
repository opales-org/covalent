/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.lifecycle.LifecycleManager;
import org.opales.covalent.container.lifecycle.LifecycleParameters;
import org.opales.covalent.container.lifecycle.LifecycleServices.StartMode;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.lifecycle.impl.LifecycleTransitions;
import org.opales.covalent.container.lifecycle.impl.LifecycleManagerImpl;
import org.opales.covalent.container.lifecycle.impl.LifecycleTransitionsImpl;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;
import org.opales.covalent.container.util.Reflection;

/**
 * When the technical clean-up of a failed component itself fails, stopping
 * reports it for that component and carries on with the others, in both start
 * modes; when the transition of the internal component tree fails while the
 * container is disposed, the disposal is still completed. The failures are
 * injected into the lifecycle transitions of the container.
 */
@RunWith(Parameterized.class)
public class LifecycleCleanupFailureTests {

	@Parameterized.Parameters(name = "{0}")
	public static Object[] modes() {
		return StartMode.values();
	}

	private final StartMode mode;

	private CovalentContainer container;

	private LifecycleManagerImpl lifecycle;

	private CapturingLogHandler logs;

	private LifecycleTransitionsImpl transitionsItem;

	private Field transitionsField;

	private Object originalTransitions;

	private volatile String failingHook;

	private volatile Class<?> failingType;

	public LifecycleCleanupFailureTests(final StartMode mode) {
		this.mode = mode;
	}

	@Before
	public void setUp() throws Exception {
		this.logs = CapturingLogHandler.install(LifecycleManagerImpl.class);
		this.container = Covalent.container();
		this.container.rcs().registerComponent(this);
		((LifecycleParameters) this.container.lcs()).setStartMode(this.mode);
		this.container.start();
		this.lifecycle = (LifecycleManagerImpl) this.container.rcs().useServices(RegistryReadServices.class)
				.getComponentInstance(LifecycleManager.class);
		this.transitionsField = LifecycleTransitionsImpl.class.getDeclaredField("transitions");
		this.transitionsField.setAccessible(true);
		this.transitionsItem = (LifecycleTransitionsImpl) Reflection
				.lookupFieldWithAnnotation(LifecycleManagerImpl.class, LifecycleTransitionsImpl.class, Item.class)
				.get(this.lifecycle);
		this.originalTransitions = this.transitionsField.get(this.transitionsItem);
		final LifecycleTransitions original = (LifecycleTransitions) this.originalTransitions;
		LifecycleTransitions injected = (LifecycleTransitions) Proxy.newProxyInstance(
				LifecycleTransitions.class.getClassLoader(), new Class<?>[] { LifecycleTransitions.class },
				(proxy, method, args) -> {
					if (method.getName().equals(this.failingHook)
							&& (this.failingType == null || this.failingType == args[0])) {
						throw new IllegalStateException("injected " + method.getName() + " failure");
					}
					try {
						return method.invoke(original, args);
					} catch (final InvocationTargetException e) {
						throw e.getCause();
					}
				});
		this.transitionsField.set(this.transitionsItem, injected);
	}

	@After
	public void tearDown() throws Exception {
		this.failingHook = null;
		this.transitionsField.set(this.transitionsItem, this.originalTransitions);
		if (this.container != null) {
			this.container.dispose();
		}
		this.logs.restore();
	}

	@Test
	public void aFailingCleanUpOfOneFailedComponentDoesNotPreventTearingDownTheOthers() {
		this.container.deployComponents(HealthyImpl.class, FailingPassivationImpl.class);
		this.container.lcs().activateComponent(Healthy.class);
		this.container.lcs().activateComponent(FailingPassivation.class);
		this.failingHook = "preFail";

		this.lifecycle.stop();

		Assert.assertFalse(this.container.lcs().isDeployed(Healthy.class));
		if (this.mode == StartMode.vertical) {
			Assert.assertEquals(1, this.logs.messagesStartingWith("Component FailingPassivation failed during vertical stop.").size());
		} else {
			Assert.assertEquals(1, this.logs.messagesStartingWith("Component FailingPassivation failed during removal.").size());
			Assert.assertEquals(1, this.logs.messagesStartingWith("Component FailingPassivation failed during undeployment.").size());
		}
		Assert.assertTrue(this.container.lcs().isFailed(FailingPassivation.class));
		Assert.assertEquals(1, this.logs.messagesStartingWith("Failure cleanup incomplete for FailingPassivation.").size());
	}

	@Test
	public void aFailingTransitionOfTheInternalTreeDoesNotPreventDisposal() {
		this.failingHook = "prePassivate";
		this.failingType = CovalentContainer.class;

		this.container.dispose();

		Assert.assertTrue(this.container.isDisposed());
		Assert.assertEquals(1, this.logs.messagesStartingWith("Internal component tree failed to passivate.").size());
	}

	// --- Fixtures ---

	@Component
	public interface Healthy {
	}

	@Implementation
	public static class HealthyImpl implements Healthy {
	}

	@Component
	public interface FailingPassivation {
	}

	@Implementation
	public static class FailingPassivationImpl implements FailingPassivation {

		@Passivation
		public void passivate() {
			throw new IllegalStateException("passivation failure");
		}
	}
}
