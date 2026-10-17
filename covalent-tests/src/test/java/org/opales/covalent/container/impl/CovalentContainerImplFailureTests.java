/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.impl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.ManagedContainerEvents;
import org.opales.covalent.container.ContainerStatus;
import org.opales.covalent.container.ShutdownReport;
import org.opales.covalent.container.StartupReport;
import org.opales.covalent.container.deployment.impl.DeployerImpl;
import org.opales.covalent.container.events.impl.EventsManagerImpl;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;

/**
 * Unexpected failures of the container's own machinery while starting or
 * stopping (worker pools, report building): the start or stop is always
 * concluded by exactly one notification, a failure of the clean-up is attached
 * to the original failure rather than replacing it, a failure before the
 * business start leaves the container not started, and one after it leaves the
 * container started. The failures are injected by replacing the worker pool
 * or deployer of a managed container.
 */
public class CovalentContainerImplFailureTests {

	private static final String MANAGED = "container-impl-failure-tests";

	/** Records the notifications of the managed container with the report status. */
	public static class Recorder implements ManagedContainerEvents {

		final List<String> notifications = new CopyOnWriteArrayList<>();

		volatile StartupReport startupReport;

		volatile ShutdownReport shutdownReport;

		@Override
		public void onContainerCreated(final String name) {
			// not recorded: only the start and stop notifications matter here
		}

		@Override
		public void onContainerStarting(final String name) {
			this.notifications.add("starting");
		}

		@Override
		public void onContainerStarted(final String name, final StartupReport report) {
			this.startupReport = report;
			this.notifications.add("started " + report.getStatus());
		}

		@Override
		public void onContainerStartFailed(final String name, final Throwable failure) {
			this.notifications.add("startFailed");
		}

		@Override
		public void onContainerStopping(final String name) {
			this.notifications.add("stopping");
		}

		@Override
		public void onContainerStopped(final String name, final ShutdownReport report) {
			this.shutdownReport = report;
			this.notifications.add("stopped " + report.getStatus());
		}

		@Override
		public void onContainerDisposing(final String name) {
			// not recorded: only the start and stop notifications matter here
		}

		@Override
		public void onContainerDisposed(final String name) {
			// not recorded: only the start and stop notifications matter here
		}
	}

	private final Recorder recorder = new Recorder();

	private CovalentContainerImpl container;

	private final List<Runnable> restorations = new ArrayList<>();

	private CapturingLogHandler covalentContainerLogs;

	@Before
	public void setUp() {
		this.covalentContainerLogs = CapturingLogHandler.install(CovalentContainerImpl.class);
		Covalent.subscribe(this.recorder);
		this.container = (CovalentContainerImpl) Covalent.managedContainer(MANAGED);
	}

	@After
	public void tearDown() {
		for (Runnable restoration : this.restorations) {
			restoration.run();
		}
		Covalent.unsubscribe(this.recorder);
		Covalent.disposeManagedContainer(MANAGED);
		this.covalentContainerLogs.restore();
	}

	private void replace(final String fieldName, final Object replacement) throws Exception {
		Field field = CovalentContainerImpl.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		final Object original = field.get(this.container);
		field.set(this.container, replacement);
		this.restorations.add(() -> {
			try {
				field.set(this.container, original);
			} catch (final IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		});
	}

	private static class FailingWorkers extends EventsManagerImpl {

		private final boolean failStart;

		private final boolean failStop;

		FailingWorkers(final boolean failStart, final boolean failStop) {
			this.failStart = failStart;
			this.failStop = failStop;
		}

		@Override
		public void startWorkers() {
			if (this.failStart) {
				throw new IllegalStateException("injected start failure");
			}
		}

		@Override
		public List<String> listNonInternalResidues() {
			// the stand-in holds no state to check
			return List.of();
		}

		@Override
		public void stopWorkers() {
			if (this.failStop) {
				throw new IllegalStateException("injected stop failure");
			}
		}
	}

	/** An events manager that claims to hold something left over by a torn-down component. */
	private static class LeavingResidue extends FailingWorkers {

		LeavingResidue() {
			super(false, false);
		}

		@Override
		public List<String> listNonInternalResidues() {
			return List.of("EventsManager: events still produced by some.Leftover");
		}
	}

	private static class FailingDeployer extends DeployerImpl {

		@Override
		public List<Class<?>> getDeployedComponentTypes() {
			throw new IllegalStateException("injected report failure");
		}
	}

	@Test
	public void whatTheTeardownLeavesBehindIsListedInTheShutdownReportWhichIsDegraded() throws Exception {
		this.replace("eventsManagerImpl", new LeavingResidue());
		this.container.start();

		ShutdownReport report = this.container.stop();

		Assert.assertEquals(List.of("EventsManager: events still produced by some.Leftover"), report.getResidues());
		Assert.assertEquals(ContainerStatus.degraded, report.getStatus());
		Assert.assertSame(report, this.recorder.shutdownReport);
		Assert.assertFalse(this.container.isStarted());
		List<String> warnings = this.covalentContainerLogs.messages(Level.WARNING);
		Assert.assertEquals(1, warnings.size());
		Assert.assertTrue(warnings.get(0), warnings.get(0).startsWith("Covalent container stopped with 1 residue(s) left in its internal components: [EventsManager: events still produced by some.Leftover"));
	}

	@Test
	public void aFailureStartingTheWorkersLeavesTheContainerNotStartedAndIsNotified() throws Exception {
		this.replace("eventsManagerImpl", new FailingWorkers(true, false));

		IllegalStateException failure = Assert.assertThrows(IllegalStateException.class, () -> this.container.start());

		Assert.assertEquals("injected start failure", failure.getMessage());
		Assert.assertEquals(0, failure.getSuppressed().length);
		Assert.assertFalse(this.container.isStarted());
		Assert.assertEquals(List.of("starting", "startFailed"), this.recorder.notifications);
	}

	@Test
	public void aFailureStoppingTheWorkersAfterAFailedStartIsAttachedToTheStartFailure() throws Exception {
		this.replace("eventsManagerImpl", new FailingWorkers(true, true));

		IllegalStateException failure = Assert.assertThrows(IllegalStateException.class, () -> this.container.start());

		Assert.assertEquals("injected start failure", failure.getMessage());
		Assert.assertEquals(1, failure.getSuppressed().length);
		Assert.assertEquals("injected stop failure", failure.getSuppressed()[0].getMessage());
		Assert.assertFalse(this.container.isStarted());
		Assert.assertEquals(List.of("starting", "startFailed"), this.recorder.notifications);
	}

	@Test
	public void aFailureBuildingTheStartupReportLeavesTheContainerStartedWithAFailedEmptyReport() throws Exception {
		this.replace("deployerImpl", new FailingDeployer());

		IllegalStateException failure = Assert.assertThrows(IllegalStateException.class, () -> this.container.start());

		Assert.assertEquals("injected report failure", failure.getMessage());
		Assert.assertTrue(this.container.isStarted());
		Assert.assertEquals(List.of("starting", "started failed"), this.recorder.notifications);
		Assert.assertEquals(ContainerStatus.failed, this.recorder.startupReport.getStatus());
		Assert.assertEquals(0, this.recorder.startupReport.getApplicationComponentCount());
		Assert.assertTrue(this.recorder.startupReport.getFailedComponents().isEmpty());
	}

	@Test
	public void aFailureEmptyingTheWorkersWhileStoppingEndsStoppedWithAFailedReportAndIsThrown() throws Exception {
		this.container.start();
		this.replace("eventsManagerImpl", new FailingWorkers(false, true));

		IllegalStateException failure = Assert.assertThrows(IllegalStateException.class, () -> this.container.stop());

		Assert.assertEquals("injected stop failure", failure.getMessage());
		Assert.assertFalse(this.container.isStarted());
		Assert.assertEquals(List.of("starting", "started healthy", "stopping", "stopped failed"),
				this.recorder.notifications);
		Assert.assertEquals(ContainerStatus.failed, this.recorder.shutdownReport.getStatus());
	}

	@Test
	public void aFailureBuildingTheShutdownReportEndsStoppedWithAFailedEmptyReportAndIsThrown() throws Exception {
		this.container.start();
		this.replace("deployerImpl", new FailingDeployer());

		IllegalStateException failure = Assert.assertThrows(IllegalStateException.class, () -> this.container.stop());

		Assert.assertEquals("injected report failure", failure.getMessage());
		Assert.assertFalse(this.container.isStarted());
		Assert.assertEquals(ContainerStatus.failed, this.recorder.shutdownReport.getStatus());
		Assert.assertEquals(0, this.recorder.shutdownReport.getApplicationComponentCount());
		Assert.assertTrue(this.recorder.shutdownReport.getFailedComponents().isEmpty());
	}

	@Test
	public void severalFailuresWhileStoppingAreAttachedToTheFirstOne() throws Exception {
		this.container.start();
		this.replace("eventsManagerImpl", new FailingWorkers(false, true));
		this.replace("deployerImpl", new FailingDeployer());

		IllegalStateException failure = Assert.assertThrows(IllegalStateException.class, () -> this.container.stop());

		Assert.assertEquals("injected stop failure", failure.getMessage());
		Assert.assertEquals(1, failure.getSuppressed().length);
		Assert.assertEquals("injected report failure", failure.getSuppressed()[0].getMessage());
		Assert.assertEquals(1, this.recorder.notifications.stream().filter(n -> n.startsWith("stopped")).count());
	}
}
