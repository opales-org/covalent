/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.ContainerLifecycleEvents;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.ShutdownReport;
import org.opales.covalent.container.StartupReport;
import org.opales.covalent.container.tests.CovalentTests;
import org.opales.covalent.container.tests.unit.managedcycle.impl.AImpl;
import org.opales.covalent.container.tests.unit.managedcycle.impl.BImpl;

/**
 * A container notifies its own lifecycle - starting, started, start failed, stopping, stopped,
 * disposing - synchronously, with its name. A business subscriber is removed by the stop cascade,
 * so it observes everything up to {@code stopping}.
 */
public class ContainerLifecycleEventsTests extends CovalentTests {

	private static final String MANAGED = "container-lifecycle-events-tests";

	/** A raw component recording the notifications, subscribed by its registration. */
	public static class Recorder implements ContainerLifecycleEvents {

		final List<String> notifications = new CopyOnWriteArrayList<>();

		@Override
		public void onContainerStarting(final String name) {
			this.notifications.add("starting " + name);
		}

		@Override
		public void onContainerStarted(final String name, final StartupReport report) {
			this.notifications.add("started " + name + (report == null ? " without report" : ""));
		}

		@Override
		public void onContainerStartFailed(final String name, final Throwable failure) {
			this.notifications.add("startFailed " + name + (failure == null ? " without failure" : ""));
		}

		@Override
		public void onContainerStopping(final String name) {
			this.notifications.add("stopping " + name);
		}

		@Override
		public void onContainerStopped(final String name, final ShutdownReport report) {
			this.notifications.add("stopped " + name + (report == null ? " without report" : ""));
		}

		@Override
		public void onContainerDisposing(final String name) {
			this.notifications.add("disposing " + name);
		}
	}

	private CovalentContainer container;

	@After
	public void disposeContainer() {
		if (this.container != null) {
			this.container.dispose();
		}
		Covalent.disposeManagedContainer(MANAGED);
	}

	@Test
	public void startAndStopAreNotifiedWithTheContainerName() {
		this.container = Covalent.managedContainer(MANAGED);
		Recorder recorder = this.register(new Recorder());

		this.container.start();
		Assert.assertEquals(List.of("starting " + MANAGED, "started " + MANAGED), recorder.notifications);

		this.container.stop();
		Assert.assertEquals(List.of("starting " + MANAGED, "started " + MANAGED, "stopping " + MANAGED),
				recorder.notifications);
	}

	@Test
	public void anUnmanagedContainerNotifiesWithoutName() {
		this.container = Covalent.container();
		Recorder recorder = this.register(new Recorder());

		this.container.start();

		Assert.assertEquals(List.of("starting null", "started null"), recorder.notifications);
	}

	@Test
	public void aStrictlyFailedStartIsNotified() {
		this.container = Covalent.container();
		this.container.getParameters().setCircularDependencyChecking(true);
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.strict);
		this.container.lcs().deployComponents(AImpl.class, BImpl.class);
		Recorder recorder = this.register(new Recorder());

		Assert.assertThrows(RuntimeException.class, () -> this.container.start());

		Assert.assertEquals(List.of("starting null", "startFailed null"), recorder.notifications);
	}

	@Test
	public void disposeIsNotified() {
		this.container = Covalent.container();
		this.container.start();
		this.container.stop();
		Recorder recorder = this.register(new Recorder());

		this.container.dispose();

		Assert.assertEquals(List.of("disposing null"), recorder.notifications);
	}

	@Test
	public void noNotificationForAStartOrAStopRefused() {
		this.container = Covalent.container();
		Recorder recorder = this.register(new Recorder());

		Assert.assertThrows(IllegalStateException.class, () -> this.container.stop());
		this.container.start();
		recorder.notifications.clear();
		Assert.assertThrows(IllegalStateException.class, () -> this.container.start());

		Assert.assertEquals(List.of(), recorder.notifications);
	}

	private Recorder register(final Recorder recorder) {
		this.container.rcs().registerComponent(Recorder.class, recorder);
		return recorder;
	}
}
