/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.jmx.impl;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.BindException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnectorServer;

import org.opales.covalent.container.util.CovalentDiagnostics;

/**
 * The remote JMX connector of the JVM, opened by Covalent itself so that a
 * tool such as JConsole or the Supervisor can connect to the containers of
 * this JVM, without any JVM flag. There is one connector per JVM, shared by
 * all its named containers: the first container to start opens it, the last
 * one to stop closes it.
 * <p>
 * It is opened only when the system property {@value JmxManagerImpl#PORT_PROPERTY} is
 * set. It starts at that port and, when the port is taken - typically by
 * another Covalent application of the same machine - tries the next ones, up to
 * {@value #MAX_ATTEMPTS} ports, so that several applications can run side by side. The port
 * retained is logged at {@code INFO}. The connector serves this machine only
 * and has no authentication.
 * <p>
 * An open connector keeps the JVM alive on its own (RMI's reaper thread is not
 * a daemon). {@link #checkJvmExit()}, called about once a second by the
 * containers, closes the connector when nothing else keeps the JVM alive,
 * so that a finished application exits normally.
 */
final class RemoteConnector {

	/** How many consecutive ports are tried, the requested one included. */
	static final int MAX_ATTEMPTS = 100;

	/** How long only RMI threads must remain before the connector is closed. */
	private static final long EXIT_GRACE_NANOS = TimeUnit.SECONDS.toNanos(2);

	private static final Logger log = Logger.getLogger(RemoteConnector.class.getName());

	private static final Object LOCK = new Object();

	/** How many started containers hold the connector. */
	private static int users;

	private static Registry registry;

	private static JMXConnectorServer connectorServer;

	/** Whether only RMI threads remained at the last check. */
	private static boolean onlyRmiThreads;

	/** When only RMI threads were first seen, on the {@link System#nanoTime()} clock. */
	private static long onlyRmiThreadsSinceNanos;

	private RemoteConnector() {
	}

	/**
	 * Takes a reference on the connector, opening it if no container holds it yet.
	 *
	 * @return {@code true} if the connector is open and this caller must {@link #release()} it, {@code false}
	 *         if there is none: the property is absent or invalid, or the connector could not be opened
	 */
	static boolean acquire() {
		String text = System.getProperty(JmxManagerImpl.PORT_PROPERTY);
		if (text == null || text.isBlank()) {
			return false;
		}
		int requestedPort = parsePort(text);
		if (requestedPort < 0) {
			CovalentDiagnostics.warning(log,
					"The system property " + JmxManagerImpl.PORT_PROPERTY + " is '" + text.trim()
							+ "', not a port number between 1 and 65535: no JMX remote connector is opened.");
			return false;
		}
		synchronized (LOCK) {
			if (connectorServer == null && !open(requestedPort)) {
				return false;
			}
			users++;
			return true;
		}
	}

	/** Gives back a reference taken by {@link #acquire()}; the last one closes the connector. */
	static void release() {
		synchronized (LOCK) {
			if (users > 0) {
				users--;
				if (users == 0) {
					close();
				}
			}
		}
	}

	/**
	 * Closes the connector when only RMI threads keep the JVM alive, for two seconds in a row: the application
	 * is over, and the connector must not prevent the JVM from exiting. A no-op while the connector is closed.
	 */
	static void checkJvmExit() {
		synchronized (LOCK) {
			if (connectorServer == null) {
				return;
			}
			long now = System.nanoTime();
			if (!onlyRmiThreadsRemain(liveThreads())) {
				onlyRmiThreads = false;
				return;
			}
			if (!onlyRmiThreads) {
				onlyRmiThreads = true;
				onlyRmiThreadsSinceNanos = now;
				return;
			}
			if (now - onlyRmiThreadsSinceNanos >= EXIT_GRACE_NANOS) {
				CovalentDiagnostics.info(log,
						"No application thread is left: the JMX remote connector is closed so that the JVM can exit.");
				close();
			}
		}
	}

	/** @return the port the connector listens on, {@code -1} when it is closed */
	static int openPort() {
		synchronized (LOCK) {
			return connectorServer == null ? -1 : connectorServer.getAddress().getPort();
		}
	}

	/** What the exit guard needs to know of a thread. */
	static final class ThreadFacts {

		private final String name;

		private final boolean daemon;

		ThreadFacts(final String name, final boolean daemon) {
			this.name = name;
			this.daemon = daemon;
		}
	}

	/**
	 * Tells whether the connector is the only thing keeping the JVM alive: every thread that prevents the JVM
	 * from exiting, the non-daemon ones, belongs to RMI or to the JMX connector, or is the one the JVM itself
	 * runs to shut down.
	 *
	 * @param threads the live threads of the JVM
	 * @return {@code true} if no application thread keeps the JVM alive
	 */
	static boolean onlyRmiThreadsRemain(final List<ThreadFacts> threads) {
		for (ThreadFacts thread : threads) {
			if (thread.daemon) {
				continue;
			}
			boolean connectorThread = thread.name.startsWith("RMI ") || thread.name.startsWith("JMX server connection")
					|| thread.name.equals("DestroyJavaVM");
			if (!connectorThread) {
				return false;
			}
		}
		return true;
	}

	private static List<ThreadFacts> liveThreads() {
		ThreadGroup root = Thread.currentThread().getThreadGroup();
		while (root.getParent() != null) {
			root = root.getParent();
		}
		Thread[] threads = new Thread[root.activeCount() + 16];
		int count = root.enumerate(threads, true);
		List<ThreadFacts> facts = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			if (threads[i].isAlive()) {
				facts.add(new ThreadFacts(threads[i].getName(), threads[i].isDaemon()));
			}
		}
		return facts;
	}

	private static int parsePort(final String text) {
		try {
			int port = Integer.parseInt(text.trim());
			return port >= 1 && port <= 65535 ? port : -1;
		} catch (NumberFormatException notANumber) {
			return -1;
		}
	}

	/** Opens the connector on the first free port from the requested one. Called with {@link #LOCK} held. */
	private static boolean open(final int requestedPort) {
		LocalOnlySocketFactory socketFactory = new LocalOnlySocketFactory();
		int lastPort = Math.min(65535, requestedPort + MAX_ATTEMPTS - 1);
		for (int port = requestedPort; port <= lastPort; port++) {
			Registry created;
			try {
				created = LocateRegistry.createRegistry(port, null, socketFactory);
			} catch (RemoteException failure) {
				if (isPortTaken(failure)) {
					continue;
				}
				CovalentDiagnostics.warning(log,
						"The JMX remote connector could not be opened on port " + port + ".", failure);
				return false;
			}
			return start(created, port, requestedPort, socketFactory);
		}
		CovalentDiagnostics.warning(log, "No free port between " + requestedPort + " and " + lastPort
				+ ": no JMX remote connector is opened.");
		return false;
	}

	private static boolean start(final Registry created, final int port, final int requestedPort,
			final RMIServerSocketFactory socketFactory) {
		try {
			// The registry and the server share one port, so a single port has to be reachable.
			JMXServiceURL url = new JMXServiceURL(
					"service:jmx:rmi://localhost:" + port + "/jndi/rmi://localhost:" + port + "/jmxrmi");
			Map<String, Object> environment = new HashMap<>();
			environment.put(RMIConnectorServer.RMI_SERVER_SOCKET_FACTORY_ATTRIBUTE, socketFactory);
			JMXConnectorServer server = JMXConnectorServerFactory.newJMXConnectorServer(url, environment,
					ManagementFactory.getPlatformMBeanServer());
			server.start();
			registry = created;
			connectorServer = server;
			onlyRmiThreads = false;
			String taken = port == requestedPort ? "" : " (port " + requestedPort + " was taken)";
			CovalentDiagnostics.info(log, "JMX remote connector listening on port " + port + taken + ": "
					+ "service:jmx:rmi:///jndi/rmi://localhost:" + port + "/jmxrmi");
			return true;
		} catch (IOException | RuntimeException | LinkageError failure) {
			unexport(created);
			CovalentDiagnostics.warning(log,
					"The JMX remote connector could not be started on port " + port + ".", failure);
			return false;
		}
	}

	/** Closes the connector. Called with {@link #LOCK} held. */
	private static void close() {
		JMXConnectorServer server = connectorServer;
		Registry closing = registry;
		connectorServer = null;
		registry = null;
		onlyRmiThreads = false;
		if (server != null) {
			try {
				server.stop();
			} catch (IOException failure) {
				CovalentDiagnostics.warning(log, "The JMX remote connector could not be stopped cleanly.", failure);
			}
		}
		unexport(closing);
	}

	private static void unexport(final Registry toUnexport) {
		if (toUnexport == null) {
			return;
		}
		try {
			UnicastRemoteObject.unexportObject(toUnexport, true);
		} catch (NoSuchObjectException alreadyGone) {
			// nothing to release
		}
	}

	private static boolean isPortTaken(final Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof BindException) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Server sockets that serve this machine only: a connection from anywhere else is closed at once. The
	 * socket is bound to every interface, as RMI needs the address it advertises to be reachable, but the
	 * connector is meant for tools running next to the application.
	 */
	private static final class LocalOnlySocketFactory implements RMIServerSocketFactory {

		@Override
		public ServerSocket createServerSocket(final int port) throws IOException {
			return new ServerSocket(port) {
				@Override
				public Socket accept() throws IOException {
					while (true) {
						Socket socket = super.accept();
						if (isLocal(socket.getInetAddress())) {
							return socket;
						}
						socket.close();
					}
				}
			};
		}

		private static boolean isLocal(final InetAddress address) throws IOException {
			return address.isLoopbackAddress() || address.isAnyLocalAddress()
					|| NetworkInterface.getByInetAddress(address) != null;
		}

		// RMI shares a port between exports only for equal socket factories.
		@Override
		public boolean equals(final Object other) {
			return other instanceof LocalOnlySocketFactory;
		}

		@Override
		public int hashCode() {
			return LocalOnlySocketFactory.class.hashCode();
		}
	}
}
