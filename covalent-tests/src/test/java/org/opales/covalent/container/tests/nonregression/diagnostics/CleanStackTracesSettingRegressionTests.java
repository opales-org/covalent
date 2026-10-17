/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.nonregression.diagnostics;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;

/**
 * Stack trace cleaning is a setting of the whole JVM: every container reports
 * the setting actually in effect, whichever container changed it last.
 */
public class CleanStackTracesSettingRegressionTests {

	private CovalentContainer first;

	private CovalentContainer second;

	private boolean originalSetting;

	@Before
	public void setUp() {
		this.first = Covalent.container();
		this.second = Covalent.container();
		this.originalSetting = this.first.getParameters().isCleanStackTraces();
	}

	@After
	public void tearDown() {
		try {
			this.first.getParameters().setCleanStackTraces(this.originalSetting);
		} finally {
			this.first.dispose();
			this.second.dispose();
		}
	}

	@Test
	public void everyContainerReportsTheSettingLastChangedByAnyOfThem() {
		this.first.getParameters().setCleanStackTraces(true);
		this.second.getParameters().setCleanStackTraces(false);

		Assert.assertFalse(this.first.getParameters().isCleanStackTraces());
		Assert.assertFalse(this.second.getParameters().isCleanStackTraces());

		this.second.getParameters().setCleanStackTraces(true);

		Assert.assertTrue(this.first.getParameters().isCleanStackTraces());
		Assert.assertTrue(this.second.getParameters().isCleanStackTraces());
	}
}
