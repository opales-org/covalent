/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.ContainerStatus;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.ShutdownReport;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Parameters;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.Services;

/**
 * Two deployed components that declare the same services, parameters or
 * produced-events interface, below the strict level: the first one owns the
 * interface and the second one is only reported. Passivating or undeploying
 * the second component must leave the first owner's port working, and the
 * container must stop clean.
 */
public class DeployedSecondOwnerTests {

	@Services
	public interface Greeting {
		String greet();
	}

	@Parameters
	public interface Settings {
		String getValue();
	}

	@Events
	public interface Ping {
		void onPing(String message);
	}

	@Services
	public interface Trigger {
		void fire();
	}

	@Component
	public interface First {
	}

	@Component
	public interface Second {
	}

	@Implementation
	public static class FirstImpl implements First, Greeting, Settings, Trigger {

		@ProducedEvents
		private Ping ping;

		@Override
		public String greet() {
			return "first";
		}

		@Override
		public String getValue() {
			return "first";
		}

		@Override
		public void fire() {
			this.ping.onPing("from first");
		}
	}

	@Implementation
	public static class SecondImpl implements Second, Greeting, Settings {

		@ProducedEvents
		private Ping ping;

		@Override
		public String greet() {
			return "second";
		}

		@Override
		public String getValue() {
			return "second";
		}
	}

	public static class Recorder implements Ping {
		final List<String> received = new ArrayList<>();

		@Override
		public void onPing(final String message) {
			this.received.add(message);
		}
	}

	private CovalentContainer container;

	@Before
	public void setUp() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
		this.container.getParameters().setDynamicRulesChecking(VerificationLevel.silent);
		this.container.deployComponents(FirstImpl.class, SecondImpl.class);
		this.container.start();
		this.container.rcs().registerComponent(this);
	}

	@After
	public void tearDown() {
		this.container.dispose();
	}

	@Test
	public void theFirstDeclarerOwnsTheInterfaces() {
		assertEquals("first", this.container.rcs().useServices(Greeting.class).greet());
		assertEquals("first", this.container.rcs().accessParameters(Settings.class).getValue());
	}

	@Test
	public void passivatingTheSecondDeclarerKeepsTheServicesOfTheFirst() {
		this.container.lcs().passivateComponent(Second.class);

		assertEquals("first", this.container.rcs().useServices(Greeting.class).greet());
	}

	@Test
	public void passivatingTheSecondDeclarerKeepsTheParametersOfTheFirst() {
		this.container.lcs().passivateComponent(Second.class);

		assertEquals("first", this.container.rcs().accessParameters(Settings.class).getValue());
	}

	@Test
	public void passivatingTheSecondDeclarerKeepsTheEventsOfTheFirst() {
		Recorder recorder = new Recorder();
		this.container.rcs().subscribe(Ping.class, recorder);

		this.container.lcs().passivateComponent(Second.class);
		this.container.rcs().useServices(Trigger.class).fire();

		assertEquals(List.of("from first"), recorder.received);
	}

	@Test
	public void undeployingTheSecondDeclarerKeepsEveryPortOfTheFirst() {
		Recorder recorder = new Recorder();
		this.container.rcs().subscribe(Ping.class, recorder);

		this.container.lcs().undeployComponent(SecondImpl.class);

		assertEquals("first", this.container.rcs().useServices(Greeting.class).greet());
		assertEquals("first", this.container.rcs().accessParameters(Settings.class).getValue());
		this.container.rcs().useServices(Trigger.class).fire();
		assertEquals(List.of("from first"), recorder.received);
	}

	@Test
	public void theContainerStopsCleanAfterTheSecondDeclarerWasPassivated() {
		this.container.lcs().passivateComponent(Second.class);

		ShutdownReport report = this.container.stop();

		assertTrue(report.getResidues().toString(), report.getResidues().isEmpty());
		assertEquals(ContainerStatus.healthy, report.getStatus());
	}
}
