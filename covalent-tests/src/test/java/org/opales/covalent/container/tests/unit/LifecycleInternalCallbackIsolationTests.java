/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.lifecycle.LifecycleManager;
import org.opales.covalent.container.lifecycle.impl.LifecycleManagerImpl;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;

/**
 * A failure in the lifecycle callback of an internal (technical) component is
 * isolated and reported for that component alone: activating, passivating or
 * removing the internal component tree goes on with the other internal
 * components and never propagates to the caller. The failure is injected by
 * making the lookup of component instances fail.
 */
public class LifecycleInternalCallbackIsolationTests {

	private CovalentContainer container;

	private CapturingLogHandler logs;

	private LifecycleManagerImpl lifecycle;

	private Field registryField;

	private Object originalRegistry;

	@Before
	public void setUp() throws Exception {
		this.logs = CapturingLogHandler.install(LifecycleManagerImpl.class);
		this.container = Covalent.container();
		this.container.rcs().registerComponent(this);
		this.container.start();
		this.lifecycle = (LifecycleManagerImpl) this.container.rcs().useServices(RegistryReadServices.class)
				.getComponentInstance(LifecycleManager.class);
		this.registryField = LifecycleManagerImpl.class.getDeclaredField("useRegistryReadServices");
		this.registryField.setAccessible(true);
		this.originalRegistry = this.registryField.get(this.lifecycle);
	}

	@After
	public void tearDown() throws Exception {
		if (this.registryField != null && this.originalRegistry != null) {
			this.registryField.set(this.lifecycle, this.originalRegistry);
		}
		if (this.container != null) {
			this.container.dispose();
		}
		this.logs.restore();
	}

	private void breakInstanceLookup() throws Exception {
		final RegistryReadServices original = (RegistryReadServices) this.originalRegistry;
		RegistryReadServices broken = (RegistryReadServices) Proxy.newProxyInstance(
				RegistryReadServices.class.getClassLoader(), new Class<?>[] { RegistryReadServices.class },
				(proxy, method, args) -> {
					if (method.getName().equals("getComponentInstance")) {
						throw new IllegalStateException("injected lookup failure");
					}
					try {
						return method.invoke(original, args);
					} catch (final InvocationTargetException e) {
						throw e.getCause();
					}
				});
		this.registryField.set(this.lifecycle, broken);
	}

	@Test
	public void aFailingInternalActivationCallbackIsReportedForEveryInternalComponentAndNotPropagated()
			throws Exception {
		this.breakInstanceLookup();

		this.lifecycle.activateInternalComponents();

		List<String> failures = this.logs.messagesStartingWith("Internal component ");
		Assert.assertTrue(failures.toString(), failures.contains("Internal component LifecycleManager failed during activation."));
		for (String failure : failures) {
			Assert.assertTrue(failure, failure.endsWith(" failed during activation."));
		}
		Assert.assertTrue(this.container.isStarted());
	}

	@Test
	public void failingInternalPassivationAndRemovalCallbacksDoNotPreventDisposal() throws Exception {
		this.breakInstanceLookup();

		this.container.dispose();

		Assert.assertTrue(this.container.isDisposed());
		List<String> failures = this.logs.messagesStartingWith("Internal component ");
		Assert.assertTrue(failures.toString(),
				failures.contains("Internal component LifecycleManager failed during passivation."));
		Assert.assertTrue(failures.toString(),
				failures.contains("Internal component LifecycleManager failed during removal."));
	}

	@Test
	public void internalCallbacksLookedUpByNameConventionRunWithoutFailureAtActivationAndDisposal() {
		this.lifecycle.setFindLifecycleMethodByNameConvention(true);

		this.lifecycle.activateInternalComponents();
		this.container.dispose();

		Assert.assertTrue(this.container.isDisposed());
		Assert.assertTrue(this.logs.messagesStartingWith("Internal component ").toString(),
				this.logs.messagesStartingWith("Internal component ").isEmpty());
	}
}
