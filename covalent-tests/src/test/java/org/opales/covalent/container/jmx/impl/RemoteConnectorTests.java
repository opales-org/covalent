/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.jmx.impl;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.jmx.impl.RemoteConnector.ThreadFacts;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;

/**
 * The remote JMX connector Covalent opens itself: opened only when {@code covalent.jmx.port} is set, at the
 * first free port from it, shared by the named containers of the JVM, closed with the last one, and never
 * keeping a finished application's JVM alive.
 */
public class RemoteConnectorTests {

	private static final String FIRST = "remote-jmx-first";

	private static final String SECOND = "remote-jmx-second";

	private CapturingLogHandler jmxLogs;

	/** The test configuration logs warnings only; the connector's INFO messages are part of what is checked. */
	private Level previousLevel;

	private final java.util.ArrayList<ServerSocket> squatters = new java.util.ArrayList<>();

	@Before
	public void setUp() {
		System.clearProperty(JmxManagerImpl.PORT_PROPERTY);
		this.previousLevel = Logger.getLogger(RemoteConnector.class.getName()).getLevel();
		Logger.getLogger(RemoteConnector.class.getName()).setLevel(Level.INFO);
		this.jmxLogs = CapturingLogHandler.install(RemoteConnector.class);
	}

	@After
	public void tearDown() throws IOException {
		System.clearProperty(JmxManagerImpl.PORT_PROPERTY);
		try {
			Covalent.disposeManagedContainer(FIRST);
			Covalent.disposeManagedContainer(SECOND);
		} finally {
			for (ServerSocket squatter : this.squatters) {
				squatter.close();
			}
			this.jmxLogs.restore();
			Logger.getLogger(RemoteConnector.class.getName()).setLevel(this.previousLevel);
		}
	}

	/** A port that is free now, and the next ones as well, as far as can be told. */
	private static int freePort() throws IOException {
		try (ServerSocket probe = new ServerSocket(0)) {
			return probe.getLocalPort();
		}
	}

	private static JMXConnector connect(final int port) throws IOException {
		return JMXConnectorFactory.connect(
				new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:" + port + "/jmxrmi"));
	}

	private static String remoteContainerName(final int port, final String containerName) throws Exception {
		try (JMXConnector connector = connect(port)) {
			MBeanServerConnection connection = connector.getMBeanServerConnection();
			return (String) connection.getAttribute(
					new ObjectName("org.opales.covalent:type=Container,name=" + containerName), "Name");
		}
	}

	@Test
	public void withoutThePropertyNothingIsOpened() throws Exception {
		CovalentContainer container = Covalent.managedContainer(FIRST);

		container.start();
		try {
			Assert.assertEquals(-1, RemoteConnector.openPort());
			Assert.assertTrue(this.jmxLogs.messagesStartingWith("JMX remote connector").isEmpty());
		} finally {
			container.stop();
		}
	}

	@Test
	public void aToolCanReadTheContainerThroughTheConnectorWhileItIsStarted() throws Exception {
		int port = freePort();
		System.setProperty(JmxManagerImpl.PORT_PROPERTY, Integer.toString(port));
		CovalentContainer container = Covalent.managedContainer(FIRST);

		container.start();
		try {
			Assert.assertEquals(port, RemoteConnector.openPort());
			Assert.assertEquals(FIRST, remoteContainerName(port, FIRST));
			Assert.assertEquals(1, this.jmxLogs.messagesStartingWith("JMX remote connector listening on port " + port
					+ ": service:jmx:rmi:///jndi/rmi://localhost:" + port + "/jmxrmi").size());
		} finally {
			container.stop();
		}

		Assert.assertEquals(-1, RemoteConnector.openPort());
		Assert.assertThrows(IOException.class, () -> connect(port));
	}

	@Test
	public void aTakenPortMakesTheConnectorTakeTheNextFreeOne() throws Exception {
		int port = freePort();
		this.squatters.add(new ServerSocket(port));
		this.squatters.add(new ServerSocket(port + 1));
		System.setProperty(JmxManagerImpl.PORT_PROPERTY, Integer.toString(port));
		CovalentContainer container = Covalent.managedContainer(FIRST);

		container.start();
		try {
			Assert.assertEquals(port + 2, RemoteConnector.openPort());
			Assert.assertEquals(FIRST, remoteContainerName(port + 2, FIRST));
			Assert.assertEquals(1, this.jmxLogs.messagesStartingWith(
					"JMX remote connector listening on port " + (port + 2) + " (port " + port + " was taken): "
							+ "service:jmx:rmi:///jndi/rmi://localhost:" + (port + 2) + "/jmxrmi").size());
		} finally {
			container.stop();
		}
	}

	@Test
	public void theContainersOfAJvmShareOneConnectorClosedWithTheLastOne() throws Exception {
		int port = freePort();
		System.setProperty(JmxManagerImpl.PORT_PROPERTY, Integer.toString(port));
		CovalentContainer first = Covalent.managedContainer(FIRST);
		CovalentContainer second = Covalent.managedContainer(SECOND);

		first.start();
		second.start();
		try {
			Assert.assertEquals(port, RemoteConnector.openPort());
			Assert.assertEquals(1, this.jmxLogs.messagesStartingWith("JMX remote connector listening").size());
			Assert.assertEquals(FIRST, remoteContainerName(port, FIRST));
			Assert.assertEquals(SECOND, remoteContainerName(port, SECOND));

			first.stop();
			Assert.assertEquals(SECOND, remoteContainerName(port, SECOND));
		} finally {
			second.stop();
			if (first.isStarted()) {
				first.stop();
			}
		}
		Assert.assertEquals(-1, RemoteConnector.openPort());
	}

	@Test
	public void aStopStartCycleReopensTheConnector() throws Exception {
		int port = freePort();
		System.setProperty(JmxManagerImpl.PORT_PROPERTY, Integer.toString(port));
		CovalentContainer container = Covalent.managedContainer(FIRST);

		container.start();
		container.stop();
		Assert.assertEquals(-1, RemoteConnector.openPort());
		container.start();
		try {
			Assert.assertEquals(FIRST, remoteContainerName(RemoteConnector.openPort(), FIRST));
		} finally {
			container.stop();
		}
	}

	@Test
	public void anInvalidPortIsLoggedAndTheContainerStartsAnyway() throws Exception {
		System.setProperty(JmxManagerImpl.PORT_PROPERTY, "not-a-port");
		CovalentContainer container = Covalent.managedContainer(FIRST);

		container.start();
		try {
			Assert.assertTrue(container.isStarted());
			Assert.assertEquals(-1, RemoteConnector.openPort());
			Assert.assertFalse(this.jmxLogs.messagesStartingWith("The system property covalent.jmx.port").isEmpty());
		} finally {
			container.stop();
		}
	}

	@Test
	public void noFreePortInTheRangeIsLoggedAndTheContainerStartsAnyway() throws Exception {
		// The requested port is the last valid one, so the range to search holds that single port.
		int requested = 65535;
		this.squatters.add(new ServerSocket(requested));
		System.setProperty(JmxManagerImpl.PORT_PROPERTY, Integer.toString(requested));
		CovalentContainer container = Covalent.managedContainer(FIRST);

		container.start();
		try {
			Assert.assertTrue(container.isStarted());
			Assert.assertEquals(-1, RemoteConnector.openPort());
			Assert.assertFalse(this.jmxLogs.messagesStartingWith("No free port between 65535 and 65535").isEmpty());
		} finally {
			container.stop();
		}
	}

	/** The JVM of an application whose main returns exits by itself, though the connector is open. */
	@Test
	public void aFinishedApplicationJvmExitsDespiteTheOpenConnector() throws Exception {
		int port = freePort();
		String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
		ProcessBuilder builder = new ProcessBuilder(java, "-D" + JmxManagerImpl.PORT_PROPERTY + "=" + port, "-cp",
				System.getProperty("java.class.path"), ExitProbe.class.getName());
		builder.redirectErrorStream(true);
		builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);

		Process process = builder.start();
		try {
			Assert.assertTrue("the JVM did not exit by itself", process.waitFor(30, TimeUnit.SECONDS));
			Assert.assertEquals(0, process.exitValue());
		} finally {
			process.destroyForcibly();
		}
	}

	/** An application that starts a container and returns from main, nothing else running. */
	public static final class ExitProbe {
		private ExitProbe() {
		}

		public static void main(final String[] args) throws Exception {
			Covalent.managedContainer("exit-probe").start();
		}
	}

	@Test
	public void aFailureToScheduleTheExitGuardIsLoggedAndTheConnectorStaysUsable() throws Exception {
		int port = freePort();
		System.setProperty(JmxManagerImpl.PORT_PROPERTY, Integer.toString(port));
		// no async manager is given: scheduling the exit guard fails
		JmxManagerImpl manager = new JmxManagerImpl();
		manager.setContainer("remote-jmx-direct", null, null);

		CapturingLogHandler managerLogs = CapturingLogHandler.install(JmxManagerImpl.class);
		manager.registerContainerMBean();
		try {
			Assert.assertEquals(port, RemoteConnector.openPort());
			Assert.assertEquals(1, managerLogs.messagesStartingWith("The check that the JMX remote connector does "
					+ "not keep the JVM alive could not be scheduled: the JVM may not exit by itself once the "
					+ "application is over.").size());
		} finally {
			managerLogs.restore();
			manager.unregisterContainerMBean();
		}

		Assert.assertEquals(-1, RemoteConnector.openPort());
	}

	@Test
	public void aPortOutsideTheValidRangeIsLoggedAndNothingIsOpened() throws Exception {
		for (String invalid : new String[] { "0", "-5", "65536", "70000" }) {
			System.setProperty(JmxManagerImpl.PORT_PROPERTY, invalid);
			CovalentContainer container = Covalent.managedContainer(FIRST);

			container.start();
			try {
				Assert.assertTrue(container.isStarted());
				Assert.assertEquals(-1, RemoteConnector.openPort());
			} finally {
				container.stop();
			}
		}

		Assert.assertEquals(4, this.jmxLogs.messagesStartingWith("The system property covalent.jmx.port").size());
		Assert.assertEquals(1, this.jmxLogs.messagesStartingWith("The system property covalent.jmx.port is '70000', "
				+ "not a port number between 1 and 65535: no JMX remote connector is opened.").size());
	}

	@Test
	public void theExitGuardDoesNothingWhileTheConnectorIsClosed() {
		RemoteConnector.checkJvmExit();
		RemoteConnector.checkJvmExit();

		Assert.assertEquals(-1, RemoteConnector.openPort());
	}

	@Test
	public void theExitGuardKeepsTheConnectorOpenWhileAnApplicationThreadIsAlive() throws Exception {
		int port = freePort();
		System.setProperty(JmxManagerImpl.PORT_PROPERTY, Integer.toString(port));
		CovalentContainer container = Covalent.managedContainer(FIRST);

		container.start();
		try {
			// the test thread itself is a live non-daemon thread of the application
			RemoteConnector.checkJvmExit();
			RemoteConnector.checkJvmExit();
			RemoteConnector.checkJvmExit();

			Assert.assertEquals(port, RemoteConnector.openPort());
			Assert.assertEquals(FIRST, remoteContainerName(port, FIRST));
		} finally {
			container.stop();
		}
	}

	@Test
	public void onlyRmiThreadsLeftMeansTheApplicationIsOver() {
		List<ThreadFacts> threads = List.of(new ThreadFacts("RMI Reaper", false),
				new ThreadFacts("RMI TCP Accept-9010", false), new ThreadFacts("DestroyJavaVM", false),
				new ThreadFacts("covalent-async-general-0", true), new ThreadFacts("Reference Handler", true));

		Assert.assertTrue(RemoteConnector.onlyRmiThreadsRemain(threads));
	}

	@Test
	public void anyOtherNonDaemonThreadKeepsTheConnectorOpen() {
		List<ThreadFacts> threads = List.of(new ThreadFacts("RMI Reaper", false), new ThreadFacts("main", false));

		Assert.assertFalse(RemoteConnector.onlyRmiThreadsRemain(threads));
	}

	@Test
	public void aDaemonThreadNeverKeepsTheConnectorOpen() {
		Assert.assertTrue(RemoteConnector.onlyRmiThreadsRemain(List.of(new ThreadFacts("worker", true))));
		Assert.assertTrue(RemoteConnector.onlyRmiThreadsRemain(List.of()));
	}
}
