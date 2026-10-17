/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests;

import org.junit.After;
import org.junit.Before;
import org.opales.covalent.Covalent;

/**
 * Base class for test classes whose test methods all assume the container is
 * already started and populated: they use components deployed once, ambient
 * to the whole suite, rather than deploying and activating their own. The
 * container is started before each test method and stopped after, so a test
 * class extending this one never depends on whatever state a previously-run
 * test class happened to leave behind.
 * <p>
 * Do not call {@code Covalent.main.start()} yourself in a subclass: the
 * container is already running by the time the test body executes, and
 * {@code CovalentContainer.start()} is not idempotent (it throws
 * {@code IllegalStateException("Already started")} if called while running).
 * Likewise, do not call {@code Covalent.main.stop()} unless the test
 * specifically means to leave the container stopped for the rest of the
 * test method; the automatic teardown here calls {@code stop()}
 * unconditionally, so a test that already stopped the container without
 * restarting it will fail its teardown.
 * <p>
 * A test that deploys and activates its own components, or that exercises
 * start()/stop() directly (for example: verifying behavior right after a
 * restart, or verifying that a stopped container rejects an operation),
 * does not belong here: extend {@link CovalentTests} instead and manage the
 * container's lifecycle explicitly within the test method.
 */
public abstract class CovalentContainerizedTests extends CovalentTests {

	@Before
	public void startContainer() {
		Covalent.main.start();
	}

	@After
	public void stopContainer() {
		Covalent.main.stop();
	}

}
