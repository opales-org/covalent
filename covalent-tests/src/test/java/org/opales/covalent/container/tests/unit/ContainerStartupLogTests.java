/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.ComposedComponent;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.impl.CovalentContainerImpl;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.LifecycleEvents;
import org.opales.covalent.container.lifecycle.LifecycleParameters;
import org.opales.covalent.container.lifecycle.LifecycleServices.StartMode;
import org.opales.covalent.container.lifecycle.impl.LifecycleManagerImpl;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;

@RunWith(Parameterized.class)
public class ContainerStartupLogTests {

	@Parameterized.Parameters(name = "{0}")
	public static Object[] modes() { return StartMode.values(); }

	private final StartMode mode;
	private CovalentContainer container;
	private CapturingLogHandler containerLogs;
	private CapturingLogHandler lifecycleLogs;
	private Level previousLevel;
	// Captured before this.container is constructed. The events worker threads of
	// this.container are created when it starts; every other container sharing the
	// suite (Covalent.main, other throwaway containers) may itself have worker
	// threads alive at any point, so this set is what lets a test isolate
	// this.container's own thread(s) from that unrelated activity.
	private java.util.Set<Thread> preExistingEventWorkerThreads;
	private final List<String> messages = new ArrayList<>();
	private final Handler handler = new Handler() {
		@Override
		public void publish(final LogRecord record) {
			if (record.getMessage().startsWith("Covalent container started in ")) {
				messages.add(record.getMessage());
			}
		}
		@Override public void flush() { }
		@Override public void close() { }
	};

	public ContainerStartupLogTests(final StartMode mode) { this.mode = mode; }

	@Before
	public void setUp() {
		this.previousLevel = Logger.getLogger(CovalentContainerImpl.class.getName()).getLevel();
		Logger.getLogger(CovalentContainerImpl.class.getName()).setLevel(Level.INFO);
		this.containerLogs = CapturingLogHandler.install(CovalentContainerImpl.class);
		this.lifecycleLogs = CapturingLogHandler.install(LifecycleManagerImpl.class);
		Logger.getLogger(CovalentContainerImpl.class.getName()).addHandler(this.handler);
		this.preExistingEventWorkerThreads = eventWorkerThreads();
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		this.container.rcs().registerComponent(this);
		this.container.rcs().accessParameters(LifecycleParameters.class).setStartMode(this.mode);
		this.container.rcs().unregisterComponent(ContainerStartupLogTests.class);
	}

	@After
	public void tearDown() {
		try {
			// dispose(), not stop(): this is a throwaway container
			// (Covalent.container()), so it must release its internal
			// component tree - and its worker threads - for good, rather than
			// leaving them reactivated with nothing left to ever stop them.
			this.container.dispose();
		} finally {
			Logger.getLogger(CovalentContainerImpl.class.getName()).removeHandler(this.handler);
			this.lifecycleLogs.restore();
			this.containerLogs.restore();
			Logger.getLogger(CovalentContainerImpl.class.getName()).setLevel(this.previousLevel);
		}
	}

	@Test
	public void emptyApplicationExcludesInternalComponents() {
		this.container.start();
		Assert.assertEquals(1, this.messages.size());
		Assert.assertTrue(this.messages.get(0), this.messages.get(0).matches(
				"Covalent container started in [0-9]+\\.[0-9]{3} seconds: 0 application components\\."));
	}

	@Test
	public void compositeCountsChildrenOnceAndExcludesItems() {
		this.container.deployComponents(RootImpl.class);
		this.container.start();
		Assert.assertEquals(1, this.messages.size());
		Assert.assertTrue(this.messages.get(0), this.messages.get(0).endsWith(
				"seconds: 2 application components (2 activated)."));
	}

	@Test
	public void summaryIncludesFailedAndPassivatedStatesWithoutZeroCounts() {
		// this test, registered as a raw component, owns the subscription
		this.container.rcs().registerComponent(this);
		this.container.rcs().subscribe(ContainerStartupLogTests.class, LifecycleEvents.class, new LifecycleEvents() {
			@Override public void onComponentInstantiated(final Class<?> type) { }
			@Override public void onComponentInitialized(final Class<?> type) { }
			@Override public void onComponentActivated(final Class<?> type) {
				if (type == Child.class) {
					container.lcs().passivateComponent(type);
				}
			}
			@Override public void onComponentPassivated(final Class<?> type) { }
			@Override public void onComponentRemoved(final Class<?> type) { }
			@Override public void onComponentFailed(final Class<?> type) { }
		});
		this.container.deployComponents(RootImpl.class, FailingImpl.class);
		this.container.start();
		Assert.assertEquals(1, this.messages.size());
		Assert.assertTrue(this.messages.get(0), this.messages.get(0).endsWith(
				"seconds: 4 application components (2 activated, 1 passivated, 1 failed)."));
	}

	@Test
	public void stopFullyCleansUpAComponentPassivatedDuringItsOwnActivationCascade() throws InterruptedException {
		// Same setup as summaryIncludesFailedAndPassivatedStatesWithoutZeroCounts
		// (a composed child passivates itself from within its own activation
		// notification, another root fails to activate), but this time driving
		// stop() explicitly and inspecting its report, instead of relying on
		// tearDown()'s unchecked stop(). In vertical start mode, doPassivate's cascade must skip an already-passivated
		// composed child ("Already passivated: Child") instead of rejecting it: a
		// rejection would abort the parent's own passivation (and therefore its
		// removal/undeploy) mid-stop(), leaving the container with residual application
		// components while ShutdownReport still reported healthy and - only with Java
		// assertions enabled - the resulting AssertionError would also prevent internal
		// components (and their event worker pools) from ever being passivated.
		// this test, registered as a raw component, owns the subscription
		this.container.rcs().registerComponent(this);
		this.container.rcs().subscribe(ContainerStartupLogTests.class, LifecycleEvents.class, new LifecycleEvents() {
			@Override public void onComponentInstantiated(final Class<?> type) { }
			@Override public void onComponentInitialized(final Class<?> type) { }
			@Override public void onComponentActivated(final Class<?> type) {
				if (type == Child.class) {
					container.lcs().passivateComponent(type);
				}
			}
			@Override public void onComponentPassivated(final Class<?> type) { }
			@Override public void onComponentRemoved(final Class<?> type) { }
			@Override public void onComponentFailed(final Class<?> type) { }
		});
		this.container.deployComponents(RootImpl.class, FailingImpl.class);
		this.container.start();

		java.util.Set<Thread> ownEventWorkersBeforeStop = eventWorkerThreads();
		ownEventWorkersBeforeStop.removeAll(this.preExistingEventWorkerThreads);

		org.opales.covalent.container.ShutdownReport report = this.container.stop();

		Assert.assertEquals(org.opales.covalent.container.ContainerStatus.healthy, report.getStatus());
		Assert.assertTrue("expected no residual application components, got " + report.getComponentCounts(),
				report.getComponentCounts().isEmpty());

		for (Thread worker : ownEventWorkersBeforeStop) {
			worker.join(5000);
		}
		Assert.assertTrue("expected this container's own event worker thread(s) to terminate after stop()",
				ownEventWorkersBeforeStop.stream().noneMatch(Thread::isAlive));
	}

	@Test
	public void restartRecountsTheCurrentApplication() {
		this.container.deployComponents(ChildImpl.class);
		this.container.start();
		Assert.assertTrue(this.messages.get(0), this.messages.get(0).endsWith(
				"seconds: 1 application component (1 activated)."));
		this.container.stop();
		this.container.start();
		Assert.assertEquals(2, this.messages.size());
		Assert.assertTrue(this.messages.get(1), this.messages.get(1).endsWith(
				"seconds: 0 application components."));
	}

	private static java.util.Set<Thread> eventWorkerThreads() {
		return Thread.getAllStackTraces().keySet().stream()
				.filter(thread -> thread.getName().startsWith("covalent-events-async-") && thread.isAlive())
				.collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
	}

	@Component public interface Root { }
	@Component public interface Child { }
	@Component public interface Failing { }

	@Implementation
	public static class RootImpl implements Root {
		@ComposedComponent private final ChildImpl child = null;
		@Item private final RootItem item = null;
	}

	@Implementation public static class ChildImpl implements Child { }
	@Item public static class RootItem { }

	@Implementation
	public static class FailingImpl implements Failing {
		@Activation
		public void activate() { throw new IllegalStateException("Expected startup failure"); }
	}
}
