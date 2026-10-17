/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.impl;

import java.lang.reflect.Field;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * A container registers a single JVM shutdown hook for its whole life: the
 * first {@code start()} registers it, stops and restarts reuse it, and
 * {@code dispose()} removes it. Nothing is registered when the hook is disabled.
 */
public class CovalentContainerShutdownHookRegistrationTests {

	private CovalentContainerImpl container;

	@After
	public void tearDown() {
		if (this.container != null) {
			this.container.dispose();
		}
	}

	private Thread hook() throws Exception {
		Field field = CovalentContainerImpl.class.getDeclaredField("shutdownHook");
		field.setAccessible(true);
		return (Thread) field.get(this.container);
	}

	@Test
	public void restartsReuseTheSameHookAndDisposeRemovesIt() throws Exception {
		this.container = new CovalentContainerImpl();
		Assert.assertNull(this.hook());

		this.container.start();
		Thread first = this.hook();
		Assert.assertNotNull(first);

		this.container.stop();
		this.container.start();
		this.container.stop();
		this.container.start();
		Assert.assertSame(first, this.hook());

		this.container.dispose();
		Assert.assertNull(this.hook());
		// removed from the JVM too: removing it again reports nothing to remove
		Assert.assertFalse(Runtime.getRuntime().removeShutdownHook(first));
	}

	@Test
	public void nothingIsRegisteredWhenTheHookIsDisabled() throws Exception {
		this.container = new CovalentContainerImpl();
		this.container.getParameters().setRegisterShutdownHook(false);

		this.container.start();
		this.container.stop();
		this.container.start();

		Assert.assertNull(this.hook());
	}

	@Test
	public void disposingTwiceIsHarmless() throws Exception {
		this.container = new CovalentContainerImpl();
		this.container.start();

		this.container.dispose();
		this.container.dispose();

		Assert.assertNull(this.hook());
	}
}
