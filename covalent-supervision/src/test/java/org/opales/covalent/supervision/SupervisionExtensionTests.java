/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import org.opales.covalent.container.ShutdownReport;

/**
 * {@link SupervisionExtension} as the extension mechanism identifies it: the identifier is the value given to
 * {@code -Dcovalent.extensions}, and the property naming the container to open on is the one the container manager
 * reads. Starting the tool itself needs the JavaFX toolkit and is not exercised here.
 */
public class SupervisionExtensionTests {

	@Test
	public void theExtensionIsIdentifiedByTheComponentViewerName() {
		Assert.assertEquals("component-viewer", new SupervisionExtension().getId());
		Assert.assertEquals(SupervisionExtension.EXTENSION_ID, new SupervisionExtension().getId());
	}

	@Test
	public void theDefaultContainerIsNamedByTheDocumentedSystemProperty() {
		Assert.assertEquals("covalent.supervision.defaultContainer", SupervisionExtension.DEFAULT_CONTAINER_PROPERTY);
	}

	@Test
	public void theDisposalTrackingIsSetUpForTheFirstContainerThatHasANameAndOnlyOnce() {
		Assert.assertFalse("a container without a name does not use the tracking up",
				SupervisionExtension.trackDisposal(null));
		Assert.assertTrue(SupervisionExtension.trackDisposal("first-named"));
		Assert.assertFalse(SupervisionExtension.trackDisposal("second-named"));
		Assert.assertFalse(SupervisionExtension.trackDisposal(null));
	}

	@Test
	public void theDisposalOfTheEnablingContainerIsReported() {
		AtomicInteger disposals = new AtomicInteger();
		SupervisionExtension.DisposalObserver observer = new SupervisionExtension.DisposalObserver("app",
				disposals::incrementAndGet);

		observer.onContainerDisposed("app");

		Assert.assertEquals(1, disposals.get());
	}

	@Test
	public void theOtherNotificationsAndTheOtherContainersAreIgnored() {
		AtomicInteger disposals = new AtomicInteger();
		SupervisionExtension.DisposalObserver observer = new SupervisionExtension.DisposalObserver("app",
				disposals::incrementAndGet);

		observer.onContainerDisposed("other");
		observer.onContainerCreated("app");
		observer.onContainerStopping("app");
		observer.onContainerStopped("app", (ShutdownReport) null);
		observer.onContainerDisposing("app");

		Assert.assertEquals(0, disposals.get());
	}
}
