/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.ManagedContainerEvents;
import org.opales.covalent.ManagedContainerRelay;
import org.opales.covalent.container.ContainerStatus;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.ShutdownReport;
import org.opales.covalent.container.StartupReport;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.events.EventsManagerServices;
import org.opales.covalent.container.extension.impl.CovalentExtensionsManagerImpl;
import org.opales.covalent.container.impl.CovalentContainerImpl;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.tests.CovalentTests;
import org.opales.covalent.container.tests.unit.extensions.TestFailingExtension;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;

/**
 * {@link Covalent} reports the lifecycle of its managed containers to its
 * {@link ManagedContainerEvents} observers: creation, the container's own
 * lifecycle events relayed with its name, and disposal. The relay is kept
 * across stop, so an observer, unlike a business subscriber, receives
 * {@code stopped}. Every accepted start or stop is concluded by one
 * notification, even when an unexpected failure is thrown.
 */
public class ManagedContainerEventsTests extends CovalentTests {

	private static final String MANAGED = "managed-container-events-tests";

	/** An observer recording the notifications, with the report status. */
	public static class Recorder implements ManagedContainerEvents {

		final List<String> notifications = new CopyOnWriteArrayList<>();

		@Override
		public void onContainerCreated(final String name) {
			this.notifications.add("created " + name);
		}

		@Override
		public void onContainerStarting(final String name) {
			this.notifications.add("starting " + name);
		}

		@Override
		public void onContainerStarted(final String name, final StartupReport report) {
			this.notifications.add("started " + name + " " + report.getStatus());
		}

		@Override
		public void onContainerStartFailed(final String name, final Throwable failure) {
			this.notifications.add("startFailed " + name);
		}

		@Override
		public void onContainerStopping(final String name) {
			this.notifications.add("stopping " + name);
		}

		@Override
		public void onContainerStopped(final String name, final ShutdownReport report) {
			this.notifications.add("stopped " + name + " " + report.getStatus());
		}

		@Override
		public void onContainerDisposing(final String name) {
			this.notifications.add("disposing " + name);
		}

		@Override
		public void onContainerDisposed(final String name) {
			this.notifications.add("disposed " + name);
		}
	}

	/** An observer failing on every notification. */
	public static class FailingObserver extends Recorder {

		@Override
		public void onContainerCreated(final String name) {
			throw new IllegalStateException("failing observer");
		}

		@Override
		public void onContainerStarting(final String name) {
			throw new IllegalStateException("failing observer");
		}
	}

	/** Events used to check that a stop leaves no unexpected subscription behind. */
	@Events
	public interface ProbeEvents {
		void onProbe();
	}

	private final Recorder recorder = new Recorder();

	private CapturingLogHandler covalentLogs;
	private CapturingLogHandler covalentContainerLogs;

	@Before
	public void captureTheLogs() {
		this.covalentLogs = CapturingLogHandler.install(Covalent.class);
		this.covalentContainerLogs = CapturingLogHandler.install(CovalentContainerImpl.class);
	}

	@After
	public void cleanUp() {
		Covalent.unsubscribe(this.recorder);
		System.clearProperty("covalent.extensions");
		Covalent.disposeManagedContainer(MANAGED);
		this.covalentLogs.restore();
		this.covalentContainerLogs.restore();
	}

	@Test
	public void theWholeLifecycleIsNotifiedWithTheName() {
		Covalent.subscribe(this.recorder);

		CovalentContainer container = Covalent.managedContainer(MANAGED);
		container.start();
		container.stop();
		container.start();
		Assert.assertTrue(Covalent.disposeManagedContainer(MANAGED));

		Assert.assertEquals(List.of("created " + MANAGED, "starting " + MANAGED, "started " + MANAGED + " healthy",
				"stopping " + MANAGED, "stopped " + MANAGED + " healthy", "starting " + MANAGED,
				"started " + MANAGED + " healthy", "disposing " + MANAGED, "disposed " + MANAGED),
				this.recorder.notifications);
		Assert.assertNull(Covalent.getManagedContainer(MANAGED));
	}

	@Test
	public void aDirectDisposeEndsTheManagedContainerTheSameWay() {
		CovalentContainer container = Covalent.managedContainer(MANAGED);
		Covalent.subscribe(this.recorder);

		container.dispose();

		Assert.assertEquals(List.of("disposing " + MANAGED, "disposed " + MANAGED), this.recorder.notifications);
		Assert.assertFalse(Covalent.getManagedContainerNames().contains(MANAGED));
		Assert.assertNotSame(container, Covalent.managedContainer(MANAGED));
	}

	@Test
	public void theRelayIsKeptAcrossStop() {
		CovalentContainer container = Covalent.managedContainer(MANAGED);
		container.start();
		container.stop();

		RegistryReadServices registry = this.registerProbe(container).useRegistry();
		Assert.assertTrue(registry.isRegistered(ManagedContainerRelay.class));
	}

	@Test
	public void unmanagedContainersAreNotReported() {
		Covalent.subscribe(this.recorder);
		CovalentContainer container = Covalent.container();

		container.start();
		container.stop();
		container.dispose();

		Assert.assertEquals(List.of(), this.recorder.notifications);
	}

	@Test
	public void aFailingObserverDoesNotDepriveTheOthers() {
		FailingObserver failing = new FailingObserver();
		Covalent.subscribe(failing);
		Covalent.subscribe(this.recorder);
		try {
			CovalentContainer container = Covalent.managedContainer(MANAGED);
			container.start();
		} finally {
			Covalent.unsubscribe(failing);
		}

		Assert.assertEquals(List.of("created " + MANAGED, "starting " + MANAGED, "started " + MANAGED + " healthy"),
				this.recorder.notifications);
		Assert.assertEquals(List.of("A managed container observer failed.", "A managed container observer failed."),
				this.covalentLogs.messages(Level.SEVERE));
	}

	@Test
	public void anUnexpectedFailureAfterTheBusinessStartStillConcludesTheStart() throws Exception {
		CovalentContainer container = Covalent.managedContainer(MANAGED);
		Covalent.subscribe(this.recorder);
		// The extensions manager isolates the extensions it starts: make the manager itself fail.
		Field extensionsManager = CovalentContainerImpl.class.getDeclaredField("extensionsManagerImpl");
		extensionsManager.setAccessible(true);
		extensionsManager.set(container, new CovalentExtensionsManagerImpl() {
			@Override
			public ExtensionsStartup startRequestedExtensions(final CovalentContainer current) {
				throw new IllegalStateException(TestFailingExtension.MESSAGE);
			}
		});

		IllegalStateException failure = Assert.assertThrows(IllegalStateException.class, () -> container.start());

		Assert.assertEquals(TestFailingExtension.MESSAGE, failure.getMessage());
		Assert.assertTrue(container.isStarted());
		Assert.assertEquals(List.of("starting " + MANAGED, "started " + MANAGED + " failed"),
				this.recorder.notifications);
	}

	@Test
	public void aSubscriptionLeftBehindIsReportedAsAResidueAndTheStopStillConcludes() {
		CovalentContainer container = Covalent.managedContainer(MANAGED);
		container.start();
		Probe probe = this.registerProbe(container);
		// A subscription made directly on the events manager, owned by no component:
		// the stop-time checks find it left behind.
		EventsManagerServices events = probe.useEvents();
		ProbeEvents leftBehind = () -> { };
		events.subscribe(ProbeEvents.class, leftBehind, null);
		Covalent.subscribe(this.recorder);
		try {
			ShutdownReport report = container.stop();

			Assert.assertEquals(ContainerStatus.degraded, report.getStatus());
			Assert.assertTrue(report.getResidues().toString(),
					report.getResidues().stream().anyMatch(r -> r.startsWith("EventsManager:")));
			Assert.assertFalse(container.isStarted());
			Assert.assertEquals(List.of("stopping " + MANAGED, "stopped " + MANAGED + " degraded"),
					this.recorder.notifications);
			List<String> warnings = this.covalentContainerLogs.messages(Level.WARNING);
			Assert.assertEquals(1, warnings.size());
			Assert.assertTrue(warnings.get(0), warnings.get(0)
					.startsWith("Covalent container stopped with 1 residue(s) left in its internal components: [EventsManager:"));
		} finally {
			// the probe is torn down by the stop: a new one cleans up
			this.registerProbe(container).useEvents().unsubscribe(ProbeEvents.class, leftBehind);
		}
	}

	/** A raw component reaching the container's internal services. */
	public static class Probe {

		private final CovalentContainer container;

		Probe(final CovalentContainer container) {
			this.container = container;
		}

		RegistryReadServices useRegistry() {
			return this.container.rcs().useServices(RegistryReadServices.class);
		}

		EventsManagerServices useEvents() {
			return this.container.rcs().useServices(EventsManagerServices.class);
		}
	}

	private Probe registerProbe(final CovalentContainer container) {
		Probe probe = new Probe(container);
		container.rcs().registerComponent(probe);
		return probe;
	}
}
