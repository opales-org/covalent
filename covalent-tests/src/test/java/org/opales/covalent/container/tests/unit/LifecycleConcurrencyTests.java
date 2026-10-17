/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.extension.impl.CovalentExtensionsManagerImpl;
import org.opales.covalent.container.impl.CovalentContainerImpl;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.lifecycle.Initialization;
import org.opales.covalent.container.lifecycle.LifecycleEvents;
import org.opales.covalent.container.lifecycle.LifecycleParameters;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.lifecycle.LifecycleServices.StartMode;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.lifecycle.RawComponentLifecycleServices;
import org.opales.covalent.container.lifecycle.Removal;
import org.opales.covalent.container.lifecycle.impl.LifecycleManagerImpl;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;

@RunWith(Parameterized.class)
public class LifecycleConcurrencyTests {

	@Parameterized.Parameters(name = "{0}")
	public static Object[] modes() { return StartMode.values(); }

	private final StartMode mode;
	private CovalentContainer container;
	private LifecycleManagerImpl lifecycle;
	private CapturingLogHandler logs;
	private final List<Task<?>> tasks = new ArrayList<>();
	private final List<CovalentContainer> containers = new ArrayList<>();
	private static volatile Gate gate;
	private static final AtomicInteger activations = new AtomicInteger();
	private static final AtomicInteger passivations = new AtomicInteger();
	private static final AtomicInteger removals = new AtomicInteger();

	public LifecycleConcurrencyTests(final StartMode mode) { this.mode = mode; }

	@Before
	public void setUp() {
		gate = null;
		activations.set(0);
		passivations.set(0);
		removals.set(0);
		this.logs = CapturingLogHandler.install(LifecycleManagerImpl.class);
		this.container = Covalent.container();
		this.containers.add(this.container);
		this.container.rcs().registerComponent(this);
		this.container.getParameters().setSynchronousEvents(true);
		this.lifecycle = (LifecycleManagerImpl) this.container.lcs();
		this.lifecycle.setStartMode(this.mode);
	}

	@After
	public void tearDown() throws Exception {
		try {
			if (gate != null) {
				gate.release.countDown();
			}
			for (Task<?> task : this.tasks) {
				task.thread.join(5000);
				Assert.assertFalse("Worker did not finish: " + task.thread.getName(), task.thread.isAlive());
			}
			for (CovalentContainer current : this.containers) {
				try {
					current.stop();
				} catch (IllegalStateException e) {
					// Already stopped by the test itself, or never started at all
					// (a container exercised only for its pre-first-start
					// bootstrap window); either way there is nothing left to
					// release, and this cleanup loop is best-effort, not an
					// assertion about start/stop state.
				}
			}
		} finally {
			gate = null;
			this.logs.restore();
		}
	}

	@Test
	public void concurrentContainerStartsRecheckStartedAfterWaiting() throws Exception {
		this.container.deployComponents(SubjectImpl.class);
		gate = new Gate("activation");
		Task<Void> first = this.run(this.container::start);
		gate.awaitEntry();
		Task<Void> second = this.run(this.container::start);
		second.assertBlocked();
		gate.release.countDown();
		first.get();
		Assert.assertEquals("Already started", second.failure().getMessage());
		Assert.assertEquals(1, activations.get());
	}

	@Test
	public void directLifecycleStartsRecheckStartedAfterWaiting() throws Exception {
		this.container.deployComponents(SubjectImpl.class);
		gate = new Gate("activation");
		Task<Float> first = this.submit(this.lifecycle::start);
		gate.awaitEntry();
		Task<Float> second = this.submit(this.lifecycle::start);
		second.assertBlocked();
		gate.release.countDown();
		first.get();
		Assert.assertEquals("Already started", second.failure().getMessage());
		Assert.assertEquals(1, activations.get());
	}

	@Test
	public void stopWaitsForStartupToComplete() throws Exception {
		this.container.deployComponents(SubjectImpl.class);
		gate = new Gate("activation");
		Task<Void> start = this.run(this.container::start);
		gate.awaitEntry();
		Task<Void> stop = this.run(this.container::stop);
		stop.assertBlocked();
		gate.release.countDown();
		start.get();
		stop.get();
		Assert.assertTrue(this.lifecycle.isUndeployed(Subject.class));
		Assert.assertEquals(1, activations.get());
		Assert.assertEquals(1, passivations.get());
	}

	@Test
	public void startupWaitsForShutdownThenRestarts() throws Exception {
		this.container.deployComponents(SubjectImpl.class);
		this.container.start();
		gate = new Gate("passivation");
		Task<Void> stop = this.run(this.container::stop);
		gate.awaitEntry();
		Task<Void> start = this.run(this.container::start);
		start.assertBlocked();
		gate.release.countDown();
		stop.get();
		start.get();
		Assert.assertTrue(this.lifecycle.isUndeployed(Subject.class));
		Assert.assertThrows(IllegalStateException.class, this.container::start);
		this.container.deployComponents(OtherImpl.class);
		this.lifecycle.activateComponent(Other.class);
		Assert.assertTrue(this.lifecycle.isActivated(Other.class));
	}

	@Test
	public void concurrentActivationDoesNotExecuteApplicationMethodTwice() throws Exception {
		this.container.start();
		this.container.deployComponents(SubjectImpl.class);
		this.lifecycle.initializeComponent(Subject.class);
		gate = new Gate("activation");
		Task<Void> first = this.run(() -> this.lifecycle.activateComponent(Subject.class));
		gate.awaitEntry();
		Task<Void> second = this.run(() -> this.lifecycle.activateComponent(Subject.class));
		second.assertBlocked();
		gate.release.countDown();
		first.get();
		Assert.assertTrue(second.failure().getMessage().contains("Already activated"));
		Assert.assertEquals(1, activations.get());
		Assert.assertFalse(this.lifecycle.isFailed(Subject.class));
	}

	@Test
	public void removalWaitsForActivation() throws Exception {
		this.container.start();
		this.container.deployComponents(SubjectImpl.class);
		gate = new Gate("activation");
		Task<Void> activation = this.run(() -> this.lifecycle.activateComponent(Subject.class));
		gate.awaitEntry();
		Task<Void> removal = this.run(() -> this.lifecycle.removeComponent(Subject.class));
		removal.assertBlocked();
		gate.release.countDown();
		activation.get();
		removal.get();
		Assert.assertTrue(this.lifecycle.isRemoved(Subject.class));
		Assert.assertEquals(1, passivations.get());
		Assert.assertEquals(1, removals.get());
	}

	@Test
	public void stateReadWaitsForTransitionCompletion() throws Exception {
		this.container.start();
		this.container.deployComponents(SubjectImpl.class);
		gate = new Gate("activation");
		Task<Void> activation = this.run(() -> this.lifecycle.activateComponent(Subject.class));
		gate.awaitEntry();
		Task<ComponentState> read = this.submit(() -> this.lifecycle.readComponentState(Subject.class));
		read.assertBlocked();
		gate.release.countDown();
		activation.get();
		Assert.assertEquals(ComponentState.activated, read.get());
	}

	@Test
	public void deploymentWaitsForShutdown() throws Exception {
		this.container.deployComponents(SubjectImpl.class);
		this.container.start();
		gate = new Gate("passivation");
		Task<Void> stop = this.run(this.container::stop);
		gate.awaitEntry();
		Task<Void> deploy = this.run(() -> this.container.deployComponents(OtherImpl.class));
		deploy.assertBlocked();
		gate.release.countDown();
		stop.get();
		deploy.get();
		Assert.assertTrue(this.lifecycle.isUndeployed(Subject.class));
		Assert.assertEquals(ComponentState.deployed, this.lifecycle.readComponentState(Other.class));
	}

	@Test
	public void differentComponentsShareTheLifecycleMonitor() throws Exception {
		this.container.start();
		this.container.deployComponents(SubjectImpl.class, OtherImpl.class);
		gate = new Gate("activation");
		Task<Void> first = this.run(() -> this.lifecycle.activateComponent(Subject.class));
		gate.awaitEntry();
		Task<Void> second = this.run(() -> this.lifecycle.activateComponent(Other.class));
		second.assertBlocked();
		gate.release.countDown();
		first.get();
		second.get();
		Assert.assertTrue(this.lifecycle.isActivated(Subject.class));
		Assert.assertTrue(this.lifecycle.isActivated(Other.class));
	}

	@Test
	public void differentContainersDoNotBlockEachOther() throws Exception {
		CovalentContainer other = Covalent.container();
		this.containers.add(other);
		((LifecycleManagerImpl) other.lcs()).setStartMode(this.mode);
		other.deployComponents(OtherImpl.class);
		this.container.deployComponents(SubjectImpl.class);
		gate = new Gate("activation");
		Task<Void> first = this.run(this.container::start);
		gate.awaitEntry();
		this.run(other::start).get();
		Assert.assertFalse(first.result.isDone());
		Assert.assertTrue(other.lcs().isActivated(Other.class));
		gate.release.countDown();
		first.get();
	}

	@Test
	public void synchronousLifecycleEventCanReenterTheManager() throws Exception {
		this.container.start();
		this.container.rcs().subscribe(LifecycleEvents.class, new LifecycleEvents() {
			@Override public void onComponentInstantiated(final Class<?> type) { }
			@Override public void onComponentInitialized(final Class<?> type) { }
			@Override public void onComponentActivated(final Class<?> type) {
				if (type == Subject.class) {
					Assert.assertTrue(lifecycle.isActivated(type));
					lifecycle.passivateComponent(type);
				}
			}
			@Override public void onComponentPassivated(final Class<?> type) { }
			@Override public void onComponentRemoved(final Class<?> type) { }
			@Override public void onComponentFailed(final Class<?> type) { }
		});
		this.container.deployComponents(SubjectImpl.class);
		this.run(() -> this.lifecycle.activateComponent(Subject.class)).get();
		Assert.assertTrue(this.lifecycle.isPassivated(Subject.class));
		Assert.assertEquals(1, passivations.get());
	}

	@Test
	public void aRawRegistrationFailureReportsTheOriginalCauseAndLeavesTheComponentUndeployed() throws Exception {
		this.container.start();
		RawFailure subject = new RawFailure();
		Task<Void> registration = this.run(() -> this.container.rcs().registerComponent(subject));
		Throwable failure = registration.failure();
		while (failure.getCause() != null) {
			failure = failure.getCause();
		}
		Assert.assertSame(subject.failure, failure);
		Assert.assertTrue(this.lifecycle.isUndeployed(RawFailure.class));
	}

	@Test
	public void directLifecycleStopWaitsForContainerExtensions() throws Exception {
		gate = new Gate("extension");
		Field field = CovalentContainerImpl.class.getDeclaredField("extensionsManagerImpl");
		field.setAccessible(true);
		field.set(this.container, new CovalentExtensionsManagerImpl() {
			@Override
			public ExtensionsStartup startRequestedExtensions(final CovalentContainer current) {
				gate.enter("extension");
				Assert.assertTrue(Thread.holdsLock(current.lcs()));
				return new ExtensionsStartup(0f, List.of());
			}
		});
		Task<Void> start = this.run(this.container::start);
		gate.awaitEntry();
		Task<Void> stop = this.run(this.lifecycle::stop);
		stop.assertBlocked();
		gate.release.countDown();
		start.get();
		stop.get();
	}

	@Test
	public void serviceEntryPointsAreSynchronized() throws Exception {
		for (Class<?> services : List.of(LifecycleServices.class, LifecycleParameters.class,
				RawComponentLifecycleServices.class)) {
			for (Method method : services.getMethods()) {
				Method implementation = LifecycleManagerImpl.class.getMethod(method.getName(), method.getParameterTypes());
				Assert.assertTrue(implementation.toString(), Modifier.isSynchronized(implementation.getModifiers()));
			}
		}
	}

	private Task<Void> run(final Runnable operation) {
		return this.submit(() -> { operation.run(); return null; });
	}

	private <T> Task<T> submit(final Callable<T> operation) {
		Task<T> task = new Task<>(operation);
		this.tasks.add(task);
		task.thread.start();
		return task;
	}

	private static final class Task<T> {
		private final FutureTask<T> result;
		private final Thread thread;

		private Task(final Callable<T> operation) {
			this.result = new FutureTask<>(operation);
			this.thread = new Thread(this.result, "lifecycle-concurrency-test");
			this.thread.setDaemon(true);
		}

		private T get() throws Exception { return this.result.get(5, TimeUnit.SECONDS); }

		private Throwable failure() throws Exception {
			try {
				this.get();
				throw new AssertionError("Operation should have failed");
			} catch (ExecutionException expected) {
				return expected.getCause();
			}
		}

		private void assertBlocked() {
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (this.thread.getState() != Thread.State.BLOCKED && !this.result.isDone()
					&& System.nanoTime() < deadline) {
				LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
			}
			Assert.assertEquals("Concurrent request must wait for the lifecycle monitor",
					Thread.State.BLOCKED, this.thread.getState());
		}
	}

	private static final class Gate {
		private final String phase;
		private final AtomicBoolean first = new AtomicBoolean(true);
		private final CountDownLatch entered = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);

		private Gate(final String phase) { this.phase = phase; }

		private void enter(final String currentPhase) {
			if (!this.phase.equals(currentPhase) || !this.first.compareAndSet(true, false)) {
				return;
			}
			this.entered.countDown();
			try {
				Assert.assertTrue("Test did not release the lifecycle operation", this.release.await(10, TimeUnit.SECONDS));
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new AssertionError(interrupted);
			}
		}

		private void awaitEntry() throws InterruptedException {
			Assert.assertTrue("Lifecycle operation did not reach the test gate", this.entered.await(5, TimeUnit.SECONDS));
		}
	}

	@Component public interface Subject { }
	@Component public interface Other { }
	@Implementation public static class OtherImpl implements Other { }

	@Implementation
	public static class SubjectImpl implements Subject {
		@Initialization public void initialize() { }
		@Activation
		public void activate() {
			activations.incrementAndGet();
			if (gate != null) { gate.enter("activation"); }
		}
		@Passivation
		public void passivate() {
			passivations.incrementAndGet();
			if (gate != null) { gate.enter("passivation"); }
		}
		@Removal public void remove() { removals.incrementAndGet(); }
	}

	public static class RawFailure {
		private final IllegalStateException failure = new IllegalStateException("Expected RAW failure");
		@Activation public void activate() { throw this.failure; }
	}
}
