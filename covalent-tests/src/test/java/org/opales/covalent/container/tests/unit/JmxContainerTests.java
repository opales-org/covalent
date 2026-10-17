/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Set;

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.jmx.ContainerMXBean;
import org.opales.covalent.container.jmx.impl.JmxManagerImpl;
import org.opales.covalent.container.jmx.WorkerPoolInfo;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;

/**
 * The container MBean: registered for a named container only, at start,
 * unregistered at stop, switched off by {@code covalent.jmx.enabled=false},
 * and readable through the MBean server.
 */
public class JmxContainerTests {

	private static final String NAME = "jmx-tests";

	private final MBeanServer server = ManagementFactory.getPlatformMBeanServer();

	private CapturingLogHandler jmxLogs;

	private CovalentContainer container;

	@Before
	public void setUp() throws Exception {
		System.clearProperty(JmxManagerImpl.ENABLED_PROPERTY);
		this.jmxLogs = CapturingLogHandler.install(JmxManagerImpl.class);
		this.container = Covalent.managedContainer(NAME);
	}

	@After
	public void tearDown() {
		System.clearProperty(JmxManagerImpl.ENABLED_PROPERTY);
		try {
			Covalent.disposeManagedContainer(NAME);
		} finally {
			this.jmxLogs.restore();
		}
	}

	private static ObjectName objectName(final String containerName) throws Exception {
		return new ObjectName("org.opales.covalent:type=Container,name=" + containerName);
	}

	@Test
	public void aNamedContainerRegistersItsMBeanAtStartAndUnregistersItAtStop() throws Exception {
		Assert.assertFalse(this.server.isRegistered(objectName(NAME)));

		this.container.start();
		Assert.assertTrue(this.server.isRegistered(objectName(NAME)));
		Assert.assertEquals(NAME, this.server.getAttribute(objectName(NAME), "Name"));

		this.container.stop();
		Assert.assertFalse(this.server.isRegistered(objectName(NAME)));
	}

	@Test
	public void aStopStartCycleLeavesExactlyOneRegistration() throws Exception {
		this.container.start();
		this.container.stop();
		this.container.start();

		Set<ObjectName> names = this.server.queryNames(new ObjectName("org.opales.covalent:type=Container,name="
				+ NAME), null);
		Assert.assertEquals(1, names.size());
		Assert.assertTrue(this.jmxLogs.messagesStartingWith("The JMX MBean").isEmpty());
	}

	@Test
	public void anUnnamedContainerRegistersNothing() throws Exception {
		CovalentContainer unnamed = Covalent.container();
		int before = this.server.queryNames(new ObjectName("org.opales.covalent:type=Container,*"), null).size();

		unnamed.start();
		try {
			Assert.assertEquals(before,
					this.server.queryNames(new ObjectName("org.opales.covalent:type=Container,*"), null).size());
		} finally {
			unnamed.stop();
		}
	}

	@Test
	public void thePropertyFalseSwitchesTheMBeanOff() throws Exception {
		System.setProperty(JmxManagerImpl.ENABLED_PROPERTY, "false");

		this.container.start();
		try {
			Assert.assertFalse(this.server.isRegistered(objectName(NAME)));
		} finally {
			this.container.stop();
		}
	}

	@Test
	public void theWorkerPoolsAreReadableThroughTheMBeanServer() throws Exception {
		this.container.getParameters().setSynchronousEvents(false);
		this.container.start();
		try {
			// raw open data: what JConsole reads
			CompositeData[] raw = (CompositeData[]) this.server.getAttribute(objectName(NAME), "WorkerPools");
			Assert.assertTrue(raw.length >= 2);
			Assert.assertTrue(raw[0].containsKey("queuedTasks"));

			// and rebuilt as the typed object, through a proxy
			ContainerMXBean proxy = JMX.newMXBeanProxy(this.server, objectName(NAME), ContainerMXBean.class);
			Assert.assertEquals(NAME, proxy.getName());
			List<WorkerPoolInfo> pools = proxy.getWorkerPools();
			WorkerPoolInfo events = pools.stream().filter(pool -> "EVENTS".equals(pool.getKind())).findFirst()
					.orElseThrow();
			WorkerPoolInfo general = pools.stream().filter(pool -> "GENERAL".equals(pool.getKind())).findFirst()
					.orElseThrow();
			Assert.assertTrue(events.getTargetWorkerCount() >= 1);
			Assert.assertTrue(general.getTargetWorkerCount() >= 1);
			Assert.assertTrue(general.getQueueCapacity() > 0);
			Assert.assertEquals(0, general.getRejectedTasks());
			Assert.assertEquals(0, general.getCancelledTasks());
			Assert.assertFalse(general.isTaskTracking());
		} finally {
			this.container.stop();
		}
	}

	@Test
	public void aRegistrationFailureIsLoggedAndDoesNotPreventTheStart() throws Exception {
		// the name is already taken by another MBean
		ObjectName taken = objectName(NAME);
		this.server.registerMBean(new Squatter(), taken);
		try {
			this.container.start();
			Assert.assertTrue(this.container.isStarted());
			Assert.assertFalse(this.jmxLogs.messagesStartingWith("The JMX MBean of container '" + NAME + "'")
					.isEmpty());
			this.container.stop();
			// the squatter is not ours: it stays
			Assert.assertTrue(this.server.isRegistered(taken));
		} finally {
			if (this.server.isRegistered(taken)) {
				this.server.unregisterMBean(taken);
			}
		}
	}

	@Test
	public void anMBeanUnregisteredByAnotherPartyIsLoggedAtStopAndDoesNotPreventTheStop() throws Exception {
		this.container.start();
		ObjectName name = objectName(NAME);
		Assert.assertTrue(this.server.isRegistered(name));
		this.server.unregisterMBean(name);

		this.container.stop();

		Assert.assertFalse(this.container.isStarted());
		Assert.assertEquals(1, this.jmxLogs.messagesStartingWith(
				"The JMX MBean org.opales.covalent:type=Container,name=" + NAME + " could not be unregistered.")
				.size());
	}

	@Test
	public void aNameWithReservedCharactersIsQuoted() throws Exception {
		CovalentContainer odd = Covalent.managedContainer("jmx,odd=name");
		try {
			odd.start();
			Set<ObjectName> names = this.server.queryNames(new ObjectName("org.opales.covalent:type=Container,*"),
					null);
			Assert.assertTrue(names.stream().anyMatch(n -> n.getKeyProperty("name").contains("jmx,odd=name")));
			odd.stop();
		} finally {
			Covalent.disposeManagedContainer("jmx,odd=name");
		}
	}

	/** A standard MBean taking the name of the container's one. */
	public interface SquatterMBean {
		int getValue();
	}

	/** See {@link SquatterMBean}. */
	public static class Squatter implements SquatterMBean {
		@Override
		public int getValue() {
			return 1;
		}
	}
}
