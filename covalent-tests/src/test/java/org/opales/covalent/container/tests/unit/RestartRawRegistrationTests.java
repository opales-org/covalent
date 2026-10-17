/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.raw.RawComponentsServices;

/**
 * Registering a raw component right after a container restart succeeds: the internal components are
 * active again once {@code start()} returns, so the registration finds them ready.
 */
public class RestartRawRegistrationTests {

	private CovalentContainer container;

	@After
	public void tearDown() {
		this.container.dispose();
	}

	@Test
	public void registeringARawComponentRightAfterARestartSucceeds() {
		this.container = Covalent.container();
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
		RawComponentsServices rcs = this.container.rcs();

		this.container.start();
		this.container.stop();
		this.container.start();

		rcs.registerComponent(new RawPojo(), ComponentState.activated);

		Assert.assertEquals(ComponentState.activated, this.container.lcs().readComponentState(RawPojo.class));
	}

	public static class RawPojo {
	}
}
