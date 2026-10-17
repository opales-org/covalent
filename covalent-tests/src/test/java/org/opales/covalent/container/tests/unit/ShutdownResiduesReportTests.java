/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.ContainerStatus;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.ShutdownReport;
import org.opales.covalent.container.component.annotations.Events;

/**
 * What a shutdown leaves behind in the internal components on behalf of
 * torn-down components is part of the {@link ShutdownReport}, whatever the JVM
 * assertion setting. A clean container reports nothing, and so does one whose
 * raw components were still registered at stop: the stop tears them down. The
 * degraded case is covered by {@code CovalentContainerImplFailureTests}.
 */
public class ShutdownResiduesReportTests {

	@Events
	public interface Ping {
		void onPing(String message);
	}

	public static class RawProducer {
	}

	private CovalentContainer container;

	@After
	public void tearDown() {
		if (this.container != null) {
			this.container.dispose();
		}
	}

	@Test
	public void aCleanContainerReportsNoResidue() {
		this.container = Covalent.container();
		this.container.start();

		ShutdownReport report = this.container.stop();

		assertTrue(report.getResidues().toString(), report.getResidues().isEmpty());
		assertEquals(ContainerStatus.healthy, report.getStatus());
	}

	@Test
	public void aRawEventsProducerStillRegisteredAtStopIsTornDownAndLeavesNothing() {
		this.container = Covalent.container();
		this.container.start();
		this.container.rcs().registerComponent(new RawProducer());
		this.container.rcs().produceEvents(RawProducer.class, Ping.class);

		ShutdownReport report = this.container.stop();

		assertTrue(report.getResidues().toString(), report.getResidues().isEmpty());
		assertEquals(ContainerStatus.healthy, report.getStatus());
	}
}
