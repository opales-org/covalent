/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.impl;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;

/**
 * The runnable registered as JVM shutdown hook disposes the container it
 * was created for, but only while the container still exists and still has
 * the shutdown hook enabled, and never lets a disposal failure escape.
 */
public class BootstrapperShutdownHookTests {

	/** A container whose disposal fails once, then behaves normally. */
	private static final class FailingDisposalContainer extends CovalentContainerImpl {

		private boolean failNextDisposal = true;

		@Override
		public void dispose() {
			if (this.failNextDisposal) {
				this.failNextDisposal = false;
				throw new IllegalStateException("injected disposal failure");
			}
			super.dispose();
		}
	}

	private CovalentContainerImpl container;

	private CapturingLogHandler logs;

	@Before
	public void setUp() {
		this.logs = CapturingLogHandler.install(Bootstrapper.class);
	}

	@After
	public void tearDown() {
		if (this.container != null) {
			this.container.dispose();
		}
		this.logs.restore();
	}

	@Test
	public void runningTheHookDisposesTheContainer() {
		this.container = new CovalentContainerImpl();
		this.container.start();

		new Bootstrapper.ShutdownHookRunnable(this.container).run();

		Assert.assertTrue(this.container.isDisposed());
		Assert.assertFalse(this.container.isStarted());
	}

	@Test
	public void runningTheHookDisposesAContainerThatWasNeverStarted() {
		this.container = new CovalentContainerImpl();

		new Bootstrapper.ShutdownHookRunnable(this.container).run();

		Assert.assertTrue(this.container.isDisposed());
	}

	@Test
	public void runningTheHookLeavesTheContainerAloneWhenTheHookIsDisabled() {
		this.container = new CovalentContainerImpl();
		this.container.getParameters().setRegisterShutdownHook(false);
		this.container.start();

		new Bootstrapper.ShutdownHookRunnable(this.container).run();

		Assert.assertFalse(this.container.isDisposed());
		Assert.assertTrue(this.container.isStarted());
	}

	@Test
	public void runningTheHookOfACollectedContainerDoesNothing() {
		new Bootstrapper.ShutdownHookRunnable(null).run();

		Assert.assertTrue(this.logs.messagesStartingWith("Uncaught error").isEmpty());
	}

	@Test
	public void aDisposalFailureIsLoggedAndNeverEscapesTheHook() {
		this.container = new FailingDisposalContainer();

		new Bootstrapper.ShutdownHookRunnable(this.container).run();

		Assert.assertTrue(this.logs.containsSevereMessageWithFailure(
				"Uncaught error while disposing container from its shutdown hook.", "injected disposal failure"));
		Assert.assertFalse(this.container.isDisposed());
	}
}
