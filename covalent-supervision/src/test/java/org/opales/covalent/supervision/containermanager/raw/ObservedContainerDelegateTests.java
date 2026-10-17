/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.containermanager.raw;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.ProducedEvents;

/**
 * The observation of a managed container: connecting to another one leaves
 * the first, which no longer holds the delegate; a container left before it
 * stops can be connected to again once restarted; an unmanaged container
 * cannot be observed.
 * <p>
 * The bridge is built directly here rather than deployed as a component, so
 * its {@code @ProducedEvents} fields are never injected: they are given
 * no-op producers, as the container would inject real ones. Without them,
 * every notification the observed container delivers to the bridge fails on a
 * {@code null} producer and is logged at {@code SEVERE}.
 */
public class ObservedContainerDelegateTests {

	private static final String FIRST = "observed-container-delegate-tests-first";

	private static final String SECOND = "observed-container-delegate-tests-second";

	private final ObservedContainerBridge bridge = new ObservedContainerBridge();

	private final ObservedContainerDelegate delegate = new ObservedContainerDelegate(this.bridge);

	@Before
	public void wireNoOpProducers() throws ReflectiveOperationException {
		for (Field field : ObservedContainerBridge.class.getDeclaredFields()) {
			if (field.isAnnotationPresent(ProducedEvents.class)) {
				Object noOp = Proxy.newProxyInstance(field.getType().getClassLoader(),
						new Class<?>[] { field.getType() }, (proxy, method, args) -> null);
				field.setAccessible(true);
				field.set(this.bridge, noOp);
			}
		}
	}

	@After
	public void tearDown() {
		this.delegate.disconnect();
		Covalent.disposeManagedContainer(FIRST);
		Covalent.disposeManagedContainer(SECOND);
	}

	@Test
	public void connectingToAnotherContainerLeavesTheFirst() {
		CovalentContainer first = Covalent.managedContainer(FIRST);
		first.start();
		this.delegate.connect(first);
		CovalentContainer second = Covalent.managedContainer(SECOND);
		second.start();

		this.delegate.connect(second);

		assertFalse(first.lcs().isActivated(ObservedContainerDelegate.class));
		assertTrue(second.lcs().isActivated(ObservedContainerDelegate.class));
		assertNotNull(this.delegate.observedLifecycleServices);
	}

	@Test
	public void aContainerLeftBeforeItsStopIsConnectedToAgainOnceRestarted() {
		CovalentContainer container = Covalent.managedContainer(FIRST);
		container.start();
		this.delegate.connect(container);
		this.delegate.disconnect();
		container.stop();
		container.start();

		this.delegate.connect(container);

		assertTrue(container.lcs().isActivated(ObservedContainerDelegate.class));
		assertNotNull(this.delegate.observedLifecycleServices);
	}

	@Test
	public void anUnmanagedContainerCannotBeObserved() {
		CovalentContainer unmanaged = Covalent.container();
		try {
			assertThrows(IllegalArgumentException.class, () -> this.bridge.connect(unmanaged));
		} finally {
			unmanaged.dispose();
		}
	}
}
