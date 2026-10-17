/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.exclusionpolicy;

import org.junit.Assert;
import org.junit.Test;

import org.opales.covalent.hmi.HmiCore;

/**
 * {@link BuiltInExclusions}: the supervisor's own observer stays excluded and is never shown, since it has
 * nothing to do with the observed application; the supervisor's action manager stays excluded and is shown.
 */
public class BuiltInExclusionsTests {

	private static final String OBSERVED_CONTAINER_DELEGATE =
			"org.opales.covalent.supervision.containermanager.raw.ObservedContainerDelegate";

	@Test
	public void theObservedContainerDelegateIsExcludedAndHidden() throws ClassNotFoundException {
		Class<?> delegate = Class.forName(OBSERVED_CONTAINER_DELEGATE);

		Assert.assertTrue(BuiltInExclusions.contains(delegate));
		Assert.assertTrue(BuiltInExclusions.isHidden(delegate));
	}

	@Test
	public void theActionManagerIsExcludedAndShown() {
		Assert.assertTrue(BuiltInExclusions.contains(HmiCore.class));
		Assert.assertFalse(BuiltInExclusions.isHidden(HmiCore.class));
	}

	@Test
	public void aComponentThatIsNotBuiltInIsNotHidden() {
		Assert.assertFalse(BuiltInExclusions.isHidden(String.class));
	}
}
