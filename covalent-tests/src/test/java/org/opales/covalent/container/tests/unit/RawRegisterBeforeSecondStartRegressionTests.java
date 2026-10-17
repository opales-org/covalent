/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.ContainerStatus;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.StartupReport;
import org.opales.covalent.container.raw.RawComponentsServices;

/**
 * Registering a raw component before a container's second {@code start()}, after a complete
 * {@code start()}/{@code stop()} cycle, succeeds. This is what an application does when it
 * restarts its container and registers a raw component again before starting it.
 */
public class RawRegisterBeforeSecondStartRegressionTests {

	private CovalentContainer container;

	@After
	public void tearDown() {
		if (this.container != null) {
			this.container.dispose();
		}
	}

	@Test
	public void registerRawComponentBeforeSecondStartAfterAFirstFullCycleSucceeds() {
		this.container = Covalent.container();

		// First, complete cycle.
		this.container.start();
		this.container.stop();

		// Before calling start() again: register a raw component, which must not throw.
		RawComponentsServices rcs = this.container.rcs();
		rcs.registerComponent(this);

		StartupReport report = this.container.start();
		Assert.assertEquals("second start() after a pre-start raw registration must succeed cleanly",
				ContainerStatus.healthy, report.getStatus());
	}
}
