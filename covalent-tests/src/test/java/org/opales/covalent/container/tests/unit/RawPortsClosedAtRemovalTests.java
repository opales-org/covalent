/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.async.AsyncServices;
import org.opales.covalent.container.lifecycle.LifecycleEvents;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.tests.CovalentTests;


/**
 * A raw component's ports follow its own lifecycle: they are closed when the component is removed,
 * by the stop cascade in particular. The internal components outlive the stop, so a port of a raw
 * component to their services or events would otherwise survive it.
 */
public class RawPortsClosedAtRemovalTests extends CovalentTests {

	/**
	 * A raw component recording the components activated: implementing an events interface, it is
	 * subscribed to it by its registration.
	 */
	public static class Listener implements LifecycleEvents {

		final List<Class<?>> activated = new CopyOnWriteArrayList<>();

		@Override
		public void onComponentInstantiated(final Class<?> componentType) {
		}

		@Override
		public void onComponentInitialized(final Class<?> componentType) {
		}

		@Override
		public void onComponentActivated(final Class<?> componentType) {
			this.activated.add(componentType);
		}

		@Override
		public void onComponentPassivated(final Class<?> componentType) {
		}

		@Override
		public void onComponentRemoved(final Class<?> componentType) {
		}

		@Override
		public void onComponentFailed(final Class<?> componentType) {
		}
	}

	/** A raw component registered to trigger lifecycle events. */
	public static class Other {
	}

	private final CovalentContainer container = Covalent.container();

	@Before
	public void setUp() {
		this.container.getParameters().setSynchronousEvents(true);
	}

	@After
	public void disposeContainer() {
		this.container.dispose();
	}

	@Test
	public void aRawComponentRemovedByStopNoLongerReceivesInternalEvents() {
		Listener listener = new Listener();
		this.container.rcs().registerComponent(Listener.class, listener);
		this.container.start();

		this.container.stop();
		listener.activated.clear();
		this.container.rcs().registerComponent(Other.class, new Other());

		Assert.assertEquals("a removed raw component must not receive anything", List.of(), listener.activated);
	}

	@Test
	public void aRawComponentRemovedByStopNoLongerUsesInternalServices() {
		this.container.rcs().registerComponent(this);
		AsyncServices asyncServices = this.container.rcs().useServices(AsyncServices.class);
		this.container.start();

		this.container.stop();

		Assert.assertThrows(IllegalStateException.class, () -> asyncServices.submit((Runnable) () -> {
		}));
	}

	@Test
	public void aRawComponentRemovedByStopReopensTheSamePortsOnceRegisteredAgain() {
		// a port left open would make reopening it fail
		this.container.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		Listener listener = new Listener();
		this.container.rcs().registerComponent(Listener.class, listener);
		this.container.rcs().registerComponent(this);
		this.container.rcs().useServices(RegistryReadServices.class);
		this.container.start();
		this.container.stop();

		this.container.rcs().registerComponent(Listener.class, listener);
		this.container.rcs().registerComponent(this);
		RegistryReadServices registry = this.container.rcs().useServices(RegistryReadServices.class);

		Assert.assertTrue(registry.isRegistered(Listener.class));
		listener.activated.clear();
		this.container.rcs().registerComponent(Other.class, new Other());
		Assert.assertTrue("the subscription opened again must receive", listener.activated.contains(Other.class));
	}
}
