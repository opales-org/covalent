/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.impl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.ContainerStatus;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.ShutdownReport;
import org.opales.covalent.container.StartupReport;
import org.opales.covalent.container.async.impl.AsyncManagerImpl;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.deployment.impl.DeployerImpl;
import org.opales.covalent.container.events.impl.EventsManagerImpl;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.lifecycle.LifecycleManager;
import org.opales.covalent.container.lifecycle.impl.LifecycleManagerImpl;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;

/**
 * The status, component counts and failed components of the startup and
 * shutdown reports, and the one-line application summary: an application
 * component failure degrades the container, a failed or inactive internal
 * component fails it, an application component left over after a shutdown
 * degrades it, and a deployed type without a lifecycle state is ignored.
 */
public class ContainerReportBuilderTests {

	private CapturingLogHandler lifecycleManagerLogs;

	private CovalentContainerImpl container;

	private DeployerImpl deployer;

	private LifecycleManagerImpl lifecycle;

	private AsyncManagerImpl async;

	private EventsManagerImpl events;

	@Before
	public void setUp() throws Exception {
		this.lifecycleManagerLogs = CapturingLogHandler.install(LifecycleManagerImpl.class);
		this.container = (CovalentContainerImpl) Covalent.container();
		this.deployer = (DeployerImpl) field("deployerImpl");
		this.lifecycle = (LifecycleManagerImpl) field("lifecycleManagerImpl");
		this.async = (AsyncManagerImpl) field("asyncManagerImpl");
		this.events = (EventsManagerImpl) field("eventsManagerImpl");
	}

	@After
	public void tearDown() {
		this.container.dispose();
		this.lifecycleManagerLogs.restore();
	}

	private Object field(final String name) throws Exception {
		Field field = CovalentContainerImpl.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(this.container);
	}

	private ContainerReportBuilder builder() {
		return new ContainerReportBuilder(this.deployer, this.lifecycle, this.async, this.events);
	}

	@SuppressWarnings("unchecked")
	private Map<Class<?>, ComponentState> states() throws Exception {
		Field field = LifecycleManagerImpl.class.getDeclaredField("componentStates");
		field.setAccessible(true);
		return (Map<Class<?>, ComponentState>) field.get(this.lifecycle);
	}

	@Test
	public void aRunningContainerWithHealthyApplicationComponentsHasAHealthyStartupReport() {
		this.container.deployComponents(OkImpl.class);
		this.container.start();

		StartupReport report = this.builder().buildStartupReport(1.5f);

		Assert.assertEquals(ContainerStatus.healthy, report.getStatus());
		Assert.assertEquals(1.5f, report.getDurationSeconds(), 0f);
		Assert.assertEquals(1, report.getApplicationComponentCount());
		Assert.assertEquals(Integer.valueOf(1), report.getComponentCounts().get(ComponentState.activated));
		Assert.assertTrue(report.getFailedComponents().isEmpty());
	}

	@Test
	public void anApplicationComponentStillActivatedAfterShutdownDegradesTheShutdownReport() {
		this.container.deployComponents(OkImpl.class);
		this.container.start();

		ShutdownReport report = this.builder().buildShutdownReport(0.5f);

		Assert.assertEquals(ContainerStatus.degraded, report.getStatus());
		Assert.assertEquals(1, report.getApplicationComponentCount());
		Assert.assertTrue(report.getFailedComponents().isEmpty());
	}

	@Test
	public void aFailedApplicationComponentIsListedAndDegradesTheShutdownReport() {
		this.container.deployComponents(BrokenImpl.class);
		this.container.start();

		ShutdownReport report = this.builder().buildShutdownReport(0f);

		Assert.assertEquals(ContainerStatus.degraded, report.getStatus());
		Assert.assertEquals(List.of(Broken.class), report.getFailedComponents());
		Assert.assertEquals(Integer.valueOf(1), report.getComponentCounts().get(ComponentState.failed));
		Assert.assertEquals(List.of("Component Broken failed; cleaning composite Broken.",
				"Component Broken failed during vertical start."), this.lifecycleManagerLogs.messages(Level.SEVERE));
	}

	@Test
	public void aFailedInternalComponentFailsTheShutdownReportAndIsListed() throws Exception {
		this.container.start();
		Map<Class<?>, ComponentState> states = this.states();
		ComponentState previous = states.put(LifecycleManager.class, ComponentState.failed);
		try {
			ShutdownReport report = this.builder().buildShutdownReport(0f);

			Assert.assertEquals(ContainerStatus.failed, report.getStatus());
			Assert.assertEquals(List.of(LifecycleManager.class), report.getFailedComponents());
			Assert.assertEquals(0, report.getApplicationComponentCount());
		} finally {
			states.put(LifecycleManager.class, previous);
		}
	}

	@Test
	public void aFailedInternalComponentIsListedOnlyOnceInTheStartupReport() throws Exception {
		this.container.start();
		Map<Class<?>, ComponentState> states = this.states();
		ComponentState previous = states.put(LifecycleManager.class, ComponentState.failed);
		try {
			StartupReport report = this.builder().buildStartupReport(0f);

			Assert.assertEquals(ContainerStatus.failed, report.getStatus());
			Assert.assertEquals(List.of(LifecycleManager.class), report.getFailedComponents());
		} finally {
			states.put(LifecycleManager.class, previous);
		}
	}

	@Test
	public void anInternalComponentThatIsNotActivatedAtStartupFailsTheStartupReport() throws Exception {
		this.container.start();
		Map<Class<?>, ComponentState> states = this.states();
		ComponentState previous = states.put(LifecycleManager.class, ComponentState.deployed);
		try {
			StartupReport report = this.builder().buildStartupReport(0f);

			Assert.assertEquals(ContainerStatus.failed, report.getStatus());
			Assert.assertEquals(List.of(LifecycleManager.class), report.getFailedComponents());
			Assert.assertEquals(0, report.getApplicationComponentCount());
		} finally {
			states.put(LifecycleManager.class, previous);
		}
	}

	@Test
	public void aDeployedTypeWithoutLifecycleStateIsIgnored() {
		this.container.start();
		DeployerImpl unknownTypeDeployer = new DeployerImpl() {
			@Override
			public List<Class<?>> getDeployedComponentTypes() {
				List<Class<?>> types = new ArrayList<>(super.getDeployedComponentTypes());
				types.add(Ok.class);
				return types;
			}
		};
		// the stand-in deployer lists no deployed types of its own, only the unknown one
		ContainerReportBuilder builder = new ContainerReportBuilder(unknownTypeDeployer, this.lifecycle, this.async,
				this.events);

		StartupReport report = builder.buildStartupReport(0f);

		Assert.assertEquals(ContainerStatus.healthy, report.getStatus());
		Assert.assertEquals(0, report.getApplicationComponentCount());
		Assert.assertTrue(report.getFailedComponents().isEmpty());
	}

	@Test
	public void theSummaryOfNoApplicationComponentIsJustTheCount() {
		StartupReport report = new StartupReport(ContainerStatus.healthy, 0f, new EnumMap<>(ComponentState.class),
				List.of());

		Assert.assertEquals("0 application components", ContainerReportBuilder.formatApplicationSummary(report));
	}

	@Test
	public void theSummaryOfASingleApplicationComponentIsSingularWithItsState() {
		Map<ComponentState, Integer> counts = new EnumMap<>(ComponentState.class);
		counts.put(ComponentState.activated, 1);
		StartupReport report = new StartupReport(ContainerStatus.healthy, 0f, counts, List.of());

		Assert.assertEquals("1 application component (1 activated)",
				ContainerReportBuilder.formatApplicationSummary(report));
	}

	@Test
	public void theSummaryListsEveryStateWithItsCount() {
		Map<ComponentState, Integer> counts = new EnumMap<>(ComponentState.class);
		counts.put(ComponentState.activated, 2);
		counts.put(ComponentState.failed, 1);
		StartupReport report = new StartupReport(ContainerStatus.degraded, 0f, counts, List.of(Ok.class));

		String summary = ContainerReportBuilder.formatApplicationSummary(report);

		Assert.assertTrue(summary, summary.startsWith("3 application components ("));
		Assert.assertTrue(summary, summary.contains("2 activated"));
		Assert.assertTrue(summary, summary.contains("1 failed"));
	}

	// --- Fixtures ---

	@Component
	public interface Ok {
	}

	@Implementation
	public static class OkImpl implements Ok {
	}

	@Component
	public interface Broken {
	}

	@Implementation
	public static class BrokenImpl implements Broken {

		@Activation
		public void activate() {
			throw new IllegalStateException("activation failure");
		}
	}
}
