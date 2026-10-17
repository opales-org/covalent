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
import org.opales.covalent.container.tests.CovalentTests;
import org.opales.covalent.container.tests.unit.extensions.TestFailingExtension;
import org.opales.covalent.container.tests.unit.extensions.TestRecordingExtension;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;
import org.opales.covalent.container.extension.impl.CovalentExtensionsManagerImpl;
import java.util.logging.Level;

public class RuntimeExtensionsTests extends CovalentTests {

	@After
	public void tearDown() {
		System.clearProperty("covalent.extensions");
		TestRecordingExtension.reset();
	}

	@Test
	public void testRequestedRuntimeExtensionStartsOnce() {
		CovalentContainer container = Covalent.container();
		System.setProperty("covalent.extensions",
				TestRecordingExtension.EXTENSION_ID + "," + TestRecordingExtension.EXTENSION_ID);

		container.start();

		Assert.assertEquals(1, TestRecordingExtension.getStartCount());
		Assert.assertSame(container, TestRecordingExtension.getLastContainer());

		container.dispose();
	}

	@Test
	public void testUnknownExtensionDoesNotPreventKnownExtensionStartup() {
		CapturingLogHandler logHandler = CapturingLogHandler.install(CovalentExtensionsManagerImpl.class);
		CovalentContainer container = Covalent.container();
		System.setProperty("covalent.extensions",
				"missing-extension, " + TestRecordingExtension.EXTENSION_ID + ", another-missing-extension");

		try {
			container.start();

			Assert.assertEquals(1, TestRecordingExtension.getStartCount());
			Assert.assertSame(container, TestRecordingExtension.getLastContainer());
			Assert.assertTrue(logHandler.containsWarning("Requested Covalent extension not found on classpath: missing-extension."));
			Assert.assertTrue(
					logHandler.containsWarning("Requested Covalent extension not found on classpath: another-missing-extension."));

			container.dispose();
		} finally {
			logHandler.restore();
		}
	}

	@Test
	public void testFailingExtensionDoesNotPreventTheOthersNorTheStart() {
		CapturingLogHandler logHandler = CapturingLogHandler.install(CovalentExtensionsManagerImpl.class);
		CovalentContainer container = Covalent.container();
		System.setProperty("covalent.extensions",
				TestFailingExtension.EXTENSION_ID + "," + TestRecordingExtension.EXTENSION_ID);

		try {
			StartupReport report = container.start();

			Assert.assertTrue(container.isStarted());
			Assert.assertEquals(1, TestRecordingExtension.getStartCount());
			Assert.assertEquals(ContainerStatus.degraded, report.getStatus());
			Assert.assertEquals(1, logHandler.messages(Level.SEVERE).size());
			Assert.assertTrue(logHandler.messages(Level.SEVERE).get(0),
					logHandler.messages(Level.SEVERE).get(0).contains(TestFailingExtension.EXTENSION_ID));

			container.dispose();
		} finally {
			logHandler.restore();
		}
	}

}
