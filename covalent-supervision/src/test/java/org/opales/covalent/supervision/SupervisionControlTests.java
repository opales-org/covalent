/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.logging.Level;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.supervision.testsupport.CapturingLogHandler;

/**
 * {@link SupervisionControl}: the summon of the supervisor (what the shortcut and the JMX operation both do), the
 * state it reports, the stop of JavaFX when the application ends, and its registration as a standard MBean.
 * Driven through a hand-written {@link SupervisionControl.Host}: no JavaFX toolkit, no display.
 */
public class SupervisionControlTests {

	private static final class FakeHost implements SupervisionControl.Host {
		boolean displayAvailable = true;
		boolean ownsToolkit;
		RuntimeException fxFailure;
		RuntimeException startFailure;
		RuntimeException exitFailure;
		int fxRuns;
		int starts;
		int shows;
		int exits;

		@Override
		public boolean isDisplayAvailable() {
			return this.displayAvailable;
		}

		@Override
		public void runOnFxThread(final Runnable action) {
			this.fxRuns++;
			if (this.fxFailure != null) {
				throw this.fxFailure;
			}
			action.run();
		}

		@Override
		public void startSupervisor() {
			this.starts++;
			if (this.startFailure != null) {
				throw this.startFailure;
			}
		}

		@Override
		public void showWindow() {
			this.shows++;
		}

		@Override
		public boolean ownsToolkit() {
			return this.ownsToolkit;
		}

		@Override
		public void exitToolkit() {
			this.exits++;
			if (this.exitFailure != null) {
				throw this.exitFailure;
			}
		}
	}

	private FakeHost host;
	private SupervisionControl control;

	private CapturingLogHandler supervisionControlLogs;

	@Before
	public void createTheControl() {
		this.supervisionControlLogs = CapturingLogHandler.install(SupervisionControl.class);
		this.host = new FakeHost();
		this.control = new SupervisionControl(this.host);
	}

	@After
	public void restoreTheLogs() {
		this.supervisionControlLogs.restore();
	}

	@Test
	public void theSupervisorIsDormantUntilSummoned() {
		Assert.assertEquals("DORMANT", this.control.getState());
		Assert.assertEquals(0, this.host.starts);
	}

	@Test
	public void theFirstSummonStartsTheSupervisor() {
		this.control.openSupervisor();
		Assert.assertEquals(1, this.host.starts);
		Assert.assertEquals(0, this.host.shows);
		Assert.assertEquals("STARTED", this.control.getState());
	}

	@Test
	public void laterSummonsShowTheWindowAgainWithoutRestarting() {
		this.control.openSupervisor();
		this.control.openSupervisor();
		this.control.openSupervisor();
		Assert.assertEquals(1, this.host.starts);
		Assert.assertEquals(2, this.host.shows);
		Assert.assertEquals("STARTED", this.control.getState());
	}

	@Test
	public void withoutDisplayTheSummonIsRefusedCleanly() {
		this.host.displayAvailable = false;
		this.control.openSupervisor();
		Assert.assertEquals(0, this.host.fxRuns);
		Assert.assertEquals(0, this.host.starts);
		Assert.assertEquals(0, this.host.shows);
		Assert.assertEquals("UNAVAILABLE: no display available", this.control.getState());
		Assert.assertEquals(List.of("The supervisor cannot be shown: no display available"), this.supervisionControlLogs.messages(Level.WARNING));
	}

	@Test
	public void aFailureToStartTheToolkitMakesTheSupervisorUnavailable() {
		this.host.fxFailure = new UnsupportedOperationException("Unable to open DISPLAY");
		this.control.openSupervisor();
		Assert.assertEquals("UNAVAILABLE: Unable to open DISPLAY", this.control.getState());
		Assert.assertEquals(0, this.host.starts);
		Assert.assertEquals(List.of("The supervisor cannot be shown: Unable to open DISPLAY"), this.supervisionControlLogs.messages(Level.WARNING));
	}

	@Test
	public void aFailureToStartTheSupervisorIsReportedAndTheNextSummonTriesAgain() {
		this.host.startFailure = new IllegalStateException("boom");
		this.control.openSupervisor();
		Assert.assertEquals("UNAVAILABLE: boom", this.control.getState());

		this.host.startFailure = null;
		this.control.openSupervisor();
		Assert.assertEquals(2, this.host.starts);
		Assert.assertEquals("STARTED", this.control.getState());
		Assert.assertEquals(List.of("The supervisor cannot be shown: boom"), this.supervisionControlLogs.messages(Level.WARNING));
	}

	@Test
	public void theSupervisorIsStartedOnTheFxThread() {
		this.control.openSupervisor();
		Assert.assertEquals(1, this.host.fxRuns);
		this.control.openSupervisor();
		Assert.assertEquals(2, this.host.fxRuns);
	}

	@Test
	public void theToolkitIsStoppedWhenTheApplicationEndsAndTheSupervisorOwnsIt() {
		this.host.ownsToolkit = true;
		this.control.openSupervisor();
		this.control.onApplicationDisposed();
		Assert.assertEquals(1, this.host.exits);
	}

	@Test
	public void theToolkitIsLeftAloneWhenTheSupervisorDoesNotOwnIt() {
		this.host.ownsToolkit = false;
		this.control.openSupervisor();
		this.control.onApplicationDisposed();
		Assert.assertEquals(0, this.host.exits);
	}

	@Test
	public void aDormantSupervisorHasNothingToStopWhenTheApplicationEnds() {
		this.host.ownsToolkit = false;
		this.control.onApplicationDisposed();
		Assert.assertEquals(0, this.host.exits);
	}

	@Test
	public void theControlRegistersAsTheDocumentedStandardMBean() throws Exception {
		MBeanServer server = MBeanServerFactory.newMBeanServer();
		ObjectName name = new ObjectName("org.opales.covalent:type=Supervision");
		Assert.assertEquals(name, SupervisionControl.OBJECT_NAME);

		Assert.assertTrue(SupervisionControl.register(server, this.control));
		Assert.assertTrue(server.isRegistered(name));
		Assert.assertEquals("DORMANT", server.getAttribute(name, "State"));

		MBeanInfo info = server.getMBeanInfo(name);
		MBeanAttributeInfo[] attributes = info.getAttributes();
		Assert.assertEquals(1, attributes.length);
		Assert.assertEquals("State", attributes[0].getName());
		Assert.assertTrue(attributes[0].isReadable());
		Assert.assertFalse(attributes[0].isWritable());
		MBeanOperationInfo[] operations = info.getOperations();
		Assert.assertEquals(1, operations.length);
		Assert.assertEquals("openSupervisor", operations[0].getName());
	}

	@Test
	public void invokingTheMBeanOperationSummonsTheSupervisor() throws Exception {
		MBeanServer server = MBeanServerFactory.newMBeanServer();
		ObjectName name = SupervisionControl.OBJECT_NAME;
		SupervisionControl.register(server, this.control);

		server.invoke(name, "openSupervisor", null, null);
		Assert.assertEquals(1, this.host.starts);
		Assert.assertEquals("STARTED", server.getAttribute(name, "State"));
	}

	@Test
	public void registeringTwiceKeepsTheFirstRegistration() {
		MBeanServer server = MBeanServerFactory.newMBeanServer();
		Assert.assertTrue(SupervisionControl.register(server, this.control));
		Assert.assertFalse(SupervisionControl.register(server, new SupervisionControl(new FakeHost())));
		Assert.assertTrue(server.isRegistered(SupervisionControl.OBJECT_NAME));
	}

	@Test
	public void aFailureWithoutAMessageIsReportedByItsClassName() {
		this.host.startFailure = new IllegalStateException();

		this.control.openSupervisor();

		Assert.assertEquals("UNAVAILABLE: IllegalStateException", this.control.getState());
		Assert.assertEquals(List.of("The supervisor cannot be shown: IllegalStateException"),
				this.supervisionControlLogs.messages(Level.WARNING));
	}

	@Test
	public void aToolkitAlreadyStoppedWhenTheApplicationEndsIsTolerated() {
		this.host.ownsToolkit = true;
		this.host.exitFailure = new IllegalStateException("Toolkit has exited");
		this.control.openSupervisor();

		this.control.onApplicationDisposed();

		Assert.assertEquals(1, this.host.exits);
		Assert.assertEquals(0, this.supervisionControlLogs.count(Level.WARNING));
	}

	@Test
	public void aRegistrationThatFailsIsLoggedAndNeverReachesTheApplication() {
		for (Exception failure : List.of(new NotCompliantMBeanException("not compliant"),
				new IllegalArgumentException("bad name"))) {
			MBeanServer failing = (MBeanServer) Proxy.newProxyInstance(MBeanServer.class.getClassLoader(),
					new Class<?>[] { MBeanServer.class }, (proxy, method, arguments) -> {
						throw failure;
					});

			Assert.assertFalse(SupervisionControl.register(failing, this.control));
		}

		Assert.assertEquals(2, this.supervisionControlLogs.count(Level.WARNING));
		Assert.assertEquals(List.of("The supervision MBean could not be registered.",
				"The supervision MBean could not be registered."), this.supervisionControlLogs.messages(Level.WARNING));
	}
}
