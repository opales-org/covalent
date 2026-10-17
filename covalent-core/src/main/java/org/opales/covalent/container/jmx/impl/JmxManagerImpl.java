/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.jmx.impl;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.logging.Logger;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.opales.covalent.container.async.ScheduledTask;
import org.opales.covalent.container.async.impl.AsyncManagerImpl;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.events.impl.EventsManagerImpl;
import org.opales.covalent.container.jmx.JmxManager;
import org.opales.covalent.container.jmx.JmxServices;
import org.opales.covalent.container.util.CovalentDiagnostics;

/**
 * Takes charge of everything JMX in the core: the other managers stay free of
 * JMX. When the container starts it registers the {@link org.opales.covalent.container.jmx.ContainerMXBean} of
 * the container in the platform MBean server, when it stops it unregisters it,
 * so a stop / start cycle leaves exactly one registration. Like the worker
 * pools it exposes, the MBean lives as long as the container is started: the
 * container calls {@link #registerContainerMBean()} and {@link #unregisterContainerMBean()} itself, from
 * its {@code start()} and {@code stop()}, as it does for the worker pools'
 * threads.
 * <p>
 * Nothing is registered for an unnamed container (an unmanaged one), nor when
 * the system property {@value #ENABLED_PROPERTY} is {@code false}. A failure is
 * logged at {@code WARNING} and never prevents the container from starting.
 * <p>
 * When the system property {@value #PORT_PROPERTY} is set, the first container to start also opens the JVM's
 * remote JMX connector (see {@code RemoteConnector}), the last one to stop closes it. While a container holds
 * the connector, a periodic task checks once a second that the connector does not keep a finished
 * application's JVM alive.
 */
@Implementation
public class JmxManagerImpl implements JmxManager, JmxServices {

	private static final Logger log = Logger.getLogger(JmxManagerImpl.class.getName());

	/**
	 * System property that switches the container MBean off when it is
	 * {@code false}; on by default.
	 */
	public static final String ENABLED_PROPERTY = "covalent.jmx.enabled";

	/**
	 * System property that opens the remote JMX connector, at the port it gives or the first free one after it.
	 * Absent by default: nothing is opened.
	 */
	public static final String PORT_PROPERTY = "covalent.jmx.port";

	/** The JMX domain of the Covalent MBeans. */
	static final String DOMAIN = "org.opales.covalent";

	private String containerName;

	private AsyncManagerImpl asyncManagerImpl;

	private EventsManagerImpl eventsManagerImpl;

	/** The name under which the MBean is registered now, {@code null} when it is not. */
	private ObjectName registeredName;

	/** Whether this container holds a reference on the remote connector. */
	private boolean holdsConnector;

	/** The periodic check that the connector does not keep the JVM alive, {@code null} when none. */
	private ScheduledTask exitGuard;

	/** Creates the manager; it is given what it reads by the bootstrap. */
	public JmxManagerImpl() {
	}

	/**
	 * Gives the manager what it exposes.
	 *
	 * @param name              the container's name, {@code null} for an unmanaged container
	 * @param asyncManager      the async manager, whose pools are exposed
	 * @param eventsManager     the events manager, whose pool is exposed
	 */
	public void setContainer(final String name, final AsyncManagerImpl asyncManager,
			final EventsManagerImpl eventsManager) {
		this.containerName = name;
		this.asyncManagerImpl = asyncManager;
		this.eventsManagerImpl = eventsManager;
	}

	@Override
	public synchronized void registerContainerMBean() {
		if (this.containerName == null || !isEnabled() || this.registeredName != null) {
			return;
		}
		try {
			ObjectName objectName = objectNameOf(this.containerName);
			MBeanServer server = ManagementFactory.getPlatformMBeanServer();
			server.registerMBean(new ContainerMXBeanImpl(this.containerName, this.asyncManagerImpl,
					this.eventsManagerImpl), objectName);
			this.registeredName = objectName;
			this.openRemoteConnector();
		} catch (Exception | LinkageError failure) {
			CovalentDiagnostics.warning(log, "The JMX MBean of container '" + this.containerName
					+ "' could not be registered: the worker pool counters are not available through JMX.", failure);
		}
	}

	@Override
	public synchronized void unregisterContainerMBean() {
		ObjectName objectName = this.registeredName;
		if (objectName == null) {
			return;
		}
		this.registeredName = null;
		this.closeRemoteConnector();
		try {
			ManagementFactory.getPlatformMBeanServer().unregisterMBean(objectName);
		} catch (Exception | LinkageError failure) {
			CovalentDiagnostics.warning(log,
					"The JMX MBean " + objectName + " could not be unregistered.", failure);
		}
	}

	private void openRemoteConnector() {
		if (!RemoteConnector.acquire()) {
			return;
		}
		this.holdsConnector = true;
		try {
			this.exitGuard = this.asyncManagerImpl.scheduleWithFixedDelay(Duration.ofSeconds(1), Duration.ofSeconds(1),
					RemoteConnector::checkJvmExit);
		} catch (RuntimeException failure) {
			CovalentDiagnostics.warning(log, "The check that the JMX remote connector does not keep the JVM alive"
					+ " could not be scheduled: the JVM may not exit by itself once the application is over.", failure);
		}
	}

	private void closeRemoteConnector() {
		if (this.exitGuard != null) {
			this.exitGuard.cancel();
			this.exitGuard = null;
		}
		if (this.holdsConnector) {
			this.holdsConnector = false;
			RemoteConnector.release();
		}
	}

	/**
	 * The name of the MBean of a named container:
	 * {@code org.opales.covalent:type=Container,name=<name>}, the name being
	 * quoted when it holds a character JMX reserves.
	 *
	 * @param containerName the container's name
	 * @return the MBean's name
	 * @throws javax.management.MalformedObjectNameException if the name cannot be used
	 */
	static ObjectName objectNameOf(final String containerName) throws javax.management.MalformedObjectNameException {
		String value = containerName;
		if (value.chars().anyMatch(c -> ",=:\"*?\n".indexOf(c) >= 0)) {
			value = ObjectName.quote(value);
		}
		return new ObjectName(DOMAIN + ":type=Container,name=" + value);
	}

	private static boolean isEnabled() {
		String value = System.getProperty(ENABLED_PROPERTY);
		return value == null || !"false".equalsIgnoreCase(value.trim());
	}
}
