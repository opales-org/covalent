/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.containermanager.raw;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.opales.covalent.supervision.observed.ObservedRegistryEvents;

/**
 * The bridge republishes every observed notification through its own
 * produced-events proxies. Those proxies are invalidated while the bridge
 * itself is torn down, whereas the observed container can still be
 * delivering notifications: the republish must then fail silently instead
 * of throwing into the observed container's delivery code.
 */
public class ObservedContainerBridgePublishTests {

	private final ObservedContainerBridge bridge = new ObservedContainerBridge();

	private final AtomicInteger published = new AtomicInteger();

	private void produceWith(final boolean invalidated) throws ReflectiveOperationException {
		ObservedRegistryEvents producer = (ObservedRegistryEvents) Proxy.newProxyInstance(
				ObservedRegistryEvents.class.getClassLoader(), new Class<?>[] { ObservedRegistryEvents.class },
				(proxy, method, args) -> {
					if (invalidated) {
						throw new IllegalStateException("Proxy[ProducerSideNotifierInvocationHandler] is invalidated");
					}
					this.published.incrementAndGet();
					return null;
				});
		Field field = ObservedContainerBridge.class.getDeclaredField("produceObservedRegistryEvents");
		field.setAccessible(true);
		field.set(this.bridge, producer);
	}

	@Test
	public void aRepublishReachesTheProducerWhileTheBridgeIsLive() throws ReflectiveOperationException {
		this.produceWith(false);
		this.bridge.onComponentRegistered(String.class, "instance");
		assertEquals(1, this.published.get());
	}

	@Test
	public void aRepublishOnAnInvalidatedProducerIsAbsorbed() throws ReflectiveOperationException {
		this.produceWith(true);
		this.bridge.onComponentRegistered(String.class, "instance");
	}

	@Test
	public void noRepublishReachesTheProducerOnceTheBridgeIsPassivated() throws ReflectiveOperationException {
		this.produceWith(false);
		this.bridge.stopPortActivity();
		this.bridge.onComponentRegistered(String.class, "instance");
		assertEquals(0, this.published.get());
	}
}
