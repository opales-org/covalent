/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.async.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opales.covalent.container.async.AsyncFailureHandler;
import org.opales.covalent.container.async.AsyncLifecycleServices;
import org.opales.covalent.container.async.AsyncManager;
import org.opales.covalent.container.async.AsyncParameters;
import org.opales.covalent.container.async.AsyncServices;
import org.opales.covalent.container.async.AsyncWorkersServices;
import org.opales.covalent.container.async.ScheduledTask;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Initialization;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.util.CovalentDiagnostics;
import org.opales.covalent.container.util.RuntimeCheck;
import org.opales.covalent.container.util.StackTraceCleaner;
import org.opales.covalent.container.workers.WorkerPoolKind;
import org.opales.covalent.container.workers.WorkerPoolState;
import org.opales.covalent.container.workers.impl.LabeledTask;
import org.opales.covalent.container.workers.impl.TaskSubmitter;
import org.opales.covalent.container.workers.impl.WorkerPoolTracker;

/**
 * Internal component backing {@link AsyncManager}/{@link AsyncServices}/
 * {@link AsyncParameters}: runs application-submitted asynchronous tasks on
 * a general worker pool, or on a dedicated single-thread pool per queue key
 * for tasks that must run in submission order. Dedicated pools can be
 * reclaimed on idle timeout or under an LRU cap when configured (see
 * {@link AsyncParameters}); a background reaper thread enforces those
 * policies, and runs the periodic tasks as well (see
 * {@link AsyncServices#scheduleWithFixedDelay(Duration, Duration, Runnable)}).
 * On {@link #passivate()}, every worker and the reaper are given
 * a bounded time to terminate; a task that does not honor interruption can
 * leave a thread alive past that bound (see {@link #hadIncompleteShutdown()}).
 */
@Implementation
public class AsyncManagerImpl
		implements AsyncManager, AsyncServices, AsyncParameters, AsyncWorkersServices, AsyncLifecycleServices {

	private static final Logger log = Logger.getLogger(AsyncManagerImpl.class.getName());

	private static final String DEFAULT_MESSAGE_KEY = "DEFAULT_MESSAGE_KEY";

	private static final int DEFAULT_QUEUE_CAPACITY = 1024;

	private static final int DEFAULT_MAX_DEDICATED_WORKER_POOLS = 256;

	private static final Duration DEFAULT_DEDICATED_WORKER_IDLE_TIMEOUT = Duration.ofMinutes(30);

	/** How often the reclaim reaper re-evaluates the dedicated pools. Not
	 * configurable: it is an implementation detail of when the configured
	 * idle timeout / LRU cap are enforced, not the policies themselves. */
	private static final long RECLAIM_TICK_MILLIS = 1000L;

	private static final String POOL_STOPPED_MESSAGE = "Async worker pool stopped before task execution.";

	/** Bound on how long the reclaim reaper thread is given to terminate
	 * after being interrupted during passivate(). It only ever sleeps in
	 * short ticks (see RECLAIM_TICK_MILLIS) and always honors interruption,
	 * so it is expected to terminate almost immediately; this bound only
	 * guards against an unforeseen hang, and exceeding it is logged rather
	 * than thrown so container shutdown can still complete. */
	private static final long REAPER_TERMINATION_TIMEOUT_MILLIS = 2000L;

	private static final Pattern DURATION_TOKEN = Pattern.compile("(\\d+)\\s*(mn|s|h|d|w)");

	private final Object lifecycleLock = new Object();

	private CovalentWorkerPool generalWorkerPool;

	private volatile boolean acceptingTasks = true;

	private final Map<String, CovalentWorkerPool> dedicatedWorkerPools = new HashMap<>();

	private volatile AsyncFailureHandler unhandledFailureHandler = this::logUnhandledFailure;

	// Scales with the host and is capped: 4 threads are far more than a typical container's
	// asynchronous workload needs at once, and larger machines gain nothing from more.
	private volatile int generalWorkerCount = Math.min(Runtime.getRuntime().availableProcessors(), 4);

	private volatile int workerQueueCapacity = DEFAULT_QUEUE_CAPACITY;

	private volatile boolean dedicatedWorkerIdleReclaimEnabled = false;

	private volatile Duration dedicatedWorkerIdleTimeout = DEFAULT_DEDICATED_WORKER_IDLE_TIMEOUT;

	private volatile boolean dedicatedWorkerPoolCapReclaimEnabled = false;

	private volatile int maxDedicatedWorkerPools = DEFAULT_MAX_DEDICATED_WORKER_POOLS;

	private volatile boolean reclaimReaperRunning;

	private volatile Thread reclaimReaper;

	/** Whether the pools' tasks are tracked - see {@link AsyncWorkersServices}. */
	private volatile boolean taskTracking = false;

	/** The periodic tasks not cancelled yet; changed rarely, read by the timer on every wake-up. */
	private final List<PeriodicTask> periodicTasks = new CopyOnWriteArrayList<>();

	/** Wakes the timer thread when a periodic task is scheduled or finishes a run. */
	private final Object timerLock = new Object();

	/**
	 * Creates a new, unconfigured async manager.
	 */
	public AsyncManagerImpl() {
	}

	/**
	 * Whether the most recent passivate() left one or more worker/reaper
	 * threads still alive past their termination bound (see
	 * CovalentWorkerPool.awaitWorkersTerminated(List) and
	 * REAPER_TERMINATION_TIMEOUT_MILLIS above). Queried by
	 * CovalentContainerImpl to fold an incomplete-but-non-fatal async
	 * shutdown into ShutdownReport as degraded, rather than as an internal
	 * component failure - a task that does not honor interruption is a
	 * caller bug, not a Covalent internal breakdown.
	 */
	private volatile boolean lastPassivationLeftThreadsAlive = false;

	/**
	 * Whether at least one task has been refused by a full queue since the last {@link #startWorkers()}. Reset
	 * on every {@link #startWorkers()}: like the events manager's rejections, it can be set at any point during
	 * a run, so it needs a per-run reset. A refusal is reported to the caller by an exception, but a
	 * fire-and-forget caller may ignore it: this flag lets the container report the loss as degraded.
	 */
	private volatile boolean hadRejectedTasks = false;

	/**
	 * Creates the general worker pool, without any thread yet: its threads are
	 * started by {@link #startWorkers()} or on demand, at the first task.
	 */
	@Initialization
	public void initialize() {
		synchronized (this.lifecycleLock) {
			this.generalWorkerPool = this.createGeneralWorkerPool();
			this.acceptingTasks = true;
		}
	}

	/**
	 * Opens the pools to tasks, creating the general pool if a previous
	 * passivation released it. Idempotent.
	 */
	@Activation
	public void activate() {
		synchronized (this.lifecycleLock) {
			if (this.generalWorkerPool == null || !this.generalWorkerPool.isRunning()) {
				this.generalWorkerPool = this.createGeneralWorkerPool();
			}
			this.acceptingTasks = true;
		}
	}

	/**
	 * Starts the general pool's threads and the reclaim reaper ahead of the
	 * business components' activity: called by the container's
	 * {@code start()}. Threads already started on demand are kept.
	 */
	public void startWorkers() {
		synchronized (this.lifecycleLock) {
			this.hadRejectedTasks = false;
			this.generalWorkerPool.startWorkers();
			this.startReclaimReaperIfStopped();
		}
	}

	/**
	 * Empties the pools of the business components' work, at the container's
	 * {@code stop()}: pending tasks cancelled, running ones interrupted, every
	 * thread stopped with a bounded wait. The general pool is replaced by a new
	 * one without threads, and the dedicated queues - business links - are
	 * discarded. Records whether every thread terminated, read by
	 * {@link #hadIncompleteShutdown()}.
	 */
	public void stopWorkers() {
		CovalentWorkerPool generalWorkerPoolToStop;
		synchronized (this.lifecycleLock) {
			generalWorkerPoolToStop = this.generalWorkerPool;
			this.generalWorkerPool = this.createGeneralWorkerPool();
		}
		this.lastPassivationLeftThreadsAlive = !this.stopPools(generalWorkerPoolToStop);
	}

	private CovalentWorkerPool createGeneralWorkerPool() {
		return new CovalentWorkerPool(this.generalWorkerCount, "covalent-async-general", this.workerQueueCapacity,
				null);
	}

	/**
	 * Passivates the manager: new submissions are rejected, then the pools and the reclaim reaper are
	 * stopped, waiting (bounded) for their threads to terminate. Records whether every thread terminated.
	 */
	@Passivation
	public void passivate() {
		CovalentWorkerPool generalWorkerPoolToStop;
		synchronized (this.lifecycleLock) {
			// New submissions must be rejected before pending tasks are cancelled.
			this.acceptingTasks = false;
			generalWorkerPoolToStop = this.generalWorkerPool;
			this.generalWorkerPool = null;
		}
		// A container is never truly passivated while its async worker threads
		// (or the reclaim reaper) are still alive - this blocks (bounded, see
		// CovalentWorkerPool.awaitWorkersTerminated(List)) until they actually
		// have terminated. The flag records whether that fully succeeded.
		this.lastPassivationLeftThreadsAlive = !this.stopPools(generalWorkerPoolToStop);
	}

	/**
	 * Stops the given general pool, every dedicated pool and the reclaim
	 * reaper, waiting (bounded) for their threads to terminate.
	 *
	 * @return {@code true} if every thread terminated within its bound
	 */
	private boolean stopPools(final CovalentWorkerPool generalWorkerPoolToStop) {
		// No periodic run may start on the pools being stopped.
		this.cancelAllPeriodicTasks();
		List<CovalentWorkerPool> dedicatedWorkerPoolsToStop;
		Thread reaperToStop;
		synchronized (this.lifecycleLock) {
			dedicatedWorkerPoolsToStop = new ArrayList<>(this.dedicatedWorkerPools.values());
			this.dedicatedWorkerPools.clear();
			this.reclaimReaperRunning = false;
			reaperToStop = this.reclaimReaper;
			this.reclaimReaper = null;
		}

		boolean allTerminated = true;
		if (generalWorkerPoolToStop != null) {
			allTerminated &= generalWorkerPoolToStop.stop();
		}
		for (CovalentWorkerPool dedicatedWorkerPool : dedicatedWorkerPoolsToStop) {
			allTerminated &= dedicatedWorkerPool.stop();
		}
		if (reaperToStop != null) {
			// interrupt() targets this specific thread instance only: a reaper started
			// later has its own interrupted status and is unaffected.
			reaperToStop.interrupt();
			try {
				reaperToStop.join(REAPER_TERMINATION_TIMEOUT_MILLIS);
			} catch (InterruptedException e) {
				// Preserve the calling thread's own interrupted status rather than
				// swallowing it; still fall through to the isAlive() check below.
				Thread.currentThread().interrupt();
			}
			if (reaperToStop.isAlive()) {
				allTerminated = false;
				CovalentDiagnostics.severe(log, "Async reclaim reaper did not terminate within "
						+ REAPER_TERMINATION_TIMEOUT_MILLIS + "ms of being stopped.", null);
			}
		}
		return allTerminated;
	}

	/**
	 * Not part of {@link AsyncServices}/{@link AsyncParameters}: this is an
	 * implementation-detail query used only by {@code CovalentContainerImpl}
	 * (a composed component, in a different package, holding a direct
	 * reference to this concrete class) to fold an incomplete async shutdown
	 * into {@code ShutdownReport}. Public only for that cross-package access,
	 * not meant as public API.
	 *
	 * @see #lastPassivationLeftThreadsAlive
	 * @return {@code true} if the most recent {@link #passivate()} left one
	 *         or more worker/reaper threads alive past their termination
	 *         bound
	 */
	public boolean hadIncompleteShutdown() {
		return this.lastPassivationLeftThreadsAlive;
	}

	/**
	 * Not part of {@link AsyncServices}/{@link AsyncParameters}: same cross-package implementation-detail query
	 * as {@link #hadIncompleteShutdown()}, but for a pool having refused at least one task since the last
	 * {@link #startWorkers()} because its queue was full, read by the container's report builder like
	 * {@code EventsManagerImpl.hadRejectedEvents()}. Public only for that cross-package access, not meant as
	 * public API.
	 *
	 * @return {@code true} if at least one task has been refused because a queue was full since the last
	 *         {@link #startWorkers()}
	 */
	public boolean hadRejectedTasks() {
		return this.hadRejectedTasks;
	}

	@Override
	public void setUnhandledFailureHandler(final AsyncFailureHandler unhandledFailureHandler) {
		if (unhandledFailureHandler == null) {
			this.unhandledFailureHandler = this::logUnhandledFailure;
			return;
		}
		this.unhandledFailureHandler = unhandledFailureHandler;
	}

	@Override
	public AsyncFailureHandler getUnhandledFailureHandler() {
		return this.unhandledFailureHandler;
	}

	@Override
	public void setGeneralWorkerCount(final int workerCount) {
		if (workerCount < 1) {
			throw new IllegalArgumentException("workerCount must be at least 1.");
		}
		synchronized (this.lifecycleLock) {
			this.generalWorkerCount = workerCount;
			if (this.generalWorkerPool != null) {
				this.generalWorkerPool.setWorkerCount(workerCount);
			}
		}
	}

	@Override
	public int getGeneralWorkerCount() {
		return this.generalWorkerCount;
	}

	@Override
	public void setWorkerQueueCapacity(final int queueCapacity) {
		if (queueCapacity < 1) {
			throw new IllegalArgumentException("queueCapacity must be at least 1.");
		}
		synchronized (this.lifecycleLock) {
			this.workerQueueCapacity = queueCapacity;
			if (this.generalWorkerPool != null) {
				this.generalWorkerPool.setQueueCapacity(queueCapacity);
			}
			for (CovalentWorkerPool dedicatedWorkerPool : this.dedicatedWorkerPools.values()) {
				dedicatedWorkerPool.setQueueCapacity(queueCapacity);
			}
		}
	}

	@Override
	public int getWorkerQueueCapacity() {
		return this.workerQueueCapacity;
	}

	@Override
	public void setDedicatedWorkerIdleReclaimEnabled(final boolean enabled) {
		this.dedicatedWorkerIdleReclaimEnabled = enabled;
	}

	@Override
	public boolean isDedicatedWorkerIdleReclaimEnabled() {
		return this.dedicatedWorkerIdleReclaimEnabled;
	}

	@Override
	public void setDedicatedWorkerIdleTimeout(final String durationText) {
		this.setDedicatedWorkerIdleTimeout(parseDurationText(durationText));
	}

	@Override
	public void setDedicatedWorkerIdleTimeout(final Duration idleTimeout) {
		RuntimeCheck.notNull(idleTimeout, "idleTimeout");
		if (idleTimeout.isNegative()) {
			throw new IllegalArgumentException("idleTimeout must not be negative.");
		}
		this.dedicatedWorkerIdleTimeout = idleTimeout;
	}

	@Override
	public Duration getDedicatedWorkerIdleTimeout() {
		return this.dedicatedWorkerIdleTimeout;
	}

	@Override
	public void setDedicatedWorkerPoolCapReclaimEnabled(final boolean enabled) {
		this.dedicatedWorkerPoolCapReclaimEnabled = enabled;
	}

	@Override
	public boolean isDedicatedWorkerPoolCapReclaimEnabled() {
		return this.dedicatedWorkerPoolCapReclaimEnabled;
	}

	@Override
	public void setMaxDedicatedWorkerPools(final int maxDedicatedWorkerPools) {
		if (maxDedicatedWorkerPools < 1) {
			throw new IllegalArgumentException("maxDedicatedWorkerPools must be at least 1.");
		}
		this.maxDedicatedWorkerPools = maxDedicatedWorkerPools;
	}

	@Override
	public int getMaxDedicatedWorkerPools() {
		return this.maxDedicatedWorkerPools;
	}

	@Override
	public CompletionStage<Void> submit(final Runnable task) {
		return this.submitVoid(this.getGeneralWorkerPool(), DEFAULT_MESSAGE_KEY, task, false, false);
	}

	@Override
	public <T> CompletionStage<T> submit(final Supplier<T> task) {
		return this.submitTask(this.getGeneralWorkerPool(), DEFAULT_MESSAGE_KEY, task, false, false);
	}

	@Override
	public CompletionStage<Void> submit(final String queueKey, final Runnable task) {
		return this.submitVoid(this.getDedicatedWorkerPool(queueKey), DEFAULT_MESSAGE_KEY, task, false, false);
	}

	@Override
	public <T> CompletionStage<T> submit(final String queueKey, final Supplier<T> task) {
		return this.submitTask(this.getDedicatedWorkerPool(queueKey), DEFAULT_MESSAGE_KEY, task, false, false);
	}

	@Override
	public CompletionStage<Void> submit(final String queueKey, final String messageKey, final Runnable task,
			final boolean flushPending) {
		return this.submitVoid(this.getDedicatedWorkerPool(queueKey), messageKey, task, flushPending, false);
	}

	@Override
	public <T> CompletionStage<T> submit(final String queueKey, final String messageKey, final Supplier<T> task,
			final boolean flushPending) {
		return this.submitTask(this.getDedicatedWorkerPool(queueKey), messageKey, task, flushPending, false);
	}

	@Override
	public void fireAndForget(final Runnable task) {
		this.submitVoid(this.getGeneralWorkerPool(), DEFAULT_MESSAGE_KEY, task, false, true);
	}

	@Override
	public void fireAndForget(final String queueKey, final Runnable task) {
		this.submitVoid(this.getDedicatedWorkerPool(queueKey), DEFAULT_MESSAGE_KEY, task, false, true);
	}

	@Override
	public void fireAndForget(final String queueKey, final String messageKey, final Runnable task,
			final boolean flushPending) {
		this.submitVoid(this.getDedicatedWorkerPool(queueKey), messageKey, task, flushPending, true);
	}

	@Override
	public ScheduledTask scheduleWithFixedDelay(final Duration initialDelay, final Duration delay,
			final Runnable task) {
		return this.schedulePeriodic(null, initialDelay, delay, task);
	}

	@Override
	public ScheduledTask scheduleWithFixedDelay(final String queueKey, final Duration initialDelay,
			final Duration delay, final Runnable task) {
		RuntimeCheck.notNull(queueKey, "queueKey");
		return this.schedulePeriodic(queueKey, initialDelay, delay, task);
	}

	@Override
	public void cancelPeriodicTasks(final Class<?> componentType) {
		RuntimeCheck.notNull(componentType, "componentType");
		for (PeriodicTask periodic : this.periodicTasks) {
			if (periodic.owner == componentType) {
				periodic.cancel();
			}
		}
	}

	private ScheduledTask schedulePeriodic(final String queueKey, final Duration initialDelay, final Duration delay,
			final Runnable task) {
		RuntimeCheck.notNull(initialDelay, "initialDelay");
		RuntimeCheck.notNull(delay, "delay");
		RuntimeCheck.notNull(task, "task");
		if (initialDelay.isNegative()) {
			throw new IllegalArgumentException("initialDelay must not be negative: " + initialDelay);
		}
		if (delay.isNegative() || delay.isZero()) {
			throw new IllegalArgumentException("delay must be strictly positive: " + delay);
		}
		long delayNanos = saturatedNanos(delay);
		PeriodicTask periodic = new PeriodicTask(TaskSubmitter.current(), queueKey, delayNanos, task,
				System.nanoTime() + saturatedNanos(initialDelay));
		synchronized (this.lifecycleLock) {
			this.checkAcceptingTasks();
			this.periodicTasks.add(periodic);
			// The reclaim thread is also the timer of periodic tasks.
			this.startReclaimReaperIfStopped();
		}
		this.wakeTimer();
		return periodic;
	}

	private static long saturatedNanos(final Duration duration) {
		try {
			return duration.toNanos();
		} catch (ArithmeticException tooLong) {
			// About 36 years: never reached in practice, and safe against overflow when added to nanoTime().
			return Long.MAX_VALUE / 8;
		}
	}

	private void cancelAllPeriodicTasks() {
		for (PeriodicTask periodic : this.periodicTasks) {
			periodic.cancel();
		}
	}

	private void wakeTimer() {
		synchronized (this.timerLock) {
			this.timerLock.notifyAll();
		}
	}

	@Override
	public void releaseQueue(final String queueKey) {
		RuntimeCheck.notNull(queueKey, "queueKey");
		// Before the pool stops: a periodic run still due would otherwise create a fresh pool for the key, and a
		// run already queued, cancelled by the stop without running, would leave its task never scheduled again.
		for (PeriodicTask periodic : this.periodicTasks) {
			if (queueKey.equals(periodic.queueKey)) {
				periodic.cancel();
			}
		}
		CovalentWorkerPool toStop;
		synchronized (this.lifecycleLock) {
			toStop = this.dedicatedWorkerPools.remove(queueKey);
		}
		if (toStop != null) {
			toStop.stop();
		}
	}

	@Override
	public List<WorkerPoolState> readWorkerPools() {
		List<CovalentWorkerPool> pools = new ArrayList<>();
		synchronized (this.lifecycleLock) {
			if (this.generalWorkerPool != null && this.generalWorkerPool.isRunning()) {
				pools.add(this.generalWorkerPool);
			}
			this.dedicatedWorkerPools.entrySet().stream().sorted(Map.Entry.comparingByKey())
					.filter(entry -> entry.getValue().isRunning()).forEach(entry -> pools.add(entry.getValue()));
		}
		List<WorkerPoolState> states = new ArrayList<>();
		for (CovalentWorkerPool pool : pools) {
			states.add(pool.state());
		}
		return states;
	}

	@Override
	public void setTaskTracking(final boolean tracking) {
		this.taskTracking = tracking;
	}

	@Override
	public boolean isTaskTracking() {
		return this.taskTracking;
	}

	private CompletionStage<Void> submitVoid(final CovalentWorkerPool workerPool, final String messageKey,
			final Runnable task, final boolean flushPending, final boolean fireAndForget) {
		RuntimeCheck.notNull(task, "task");
		return this.submitTask(workerPool, messageKey, () -> {
			task.run();
			return null;
		}, flushPending, fireAndForget);
	}

	private <T> CompletionStage<T> submitTask(final CovalentWorkerPool workerPool, final String messageKey,
			final Supplier<T> task, final boolean flushPending, final boolean fireAndForget) {
		RuntimeCheck.notNull(task, "task");
		RuntimeCheck.notNull(messageKey, "messageKey");

		AsyncTask<T> asyncTask = new AsyncTask<>(task, fireAndForget, messageKey);
		workerPool.submit(messageKey, asyncTask, flushPending);
		return asyncTask.getStage();
	}

	private CovalentWorkerPool getGeneralWorkerPool() {
		synchronized (this.lifecycleLock) {
			this.checkAcceptingTasks();
			if (this.generalWorkerPool == null || !this.generalWorkerPool.isRunning()) {
				throw new RejectedExecutionException("Async manager is stopped.");
			}
			return this.generalWorkerPool;
		}
	}

	private CovalentWorkerPool getDedicatedWorkerPool(final String queueKey) {
		RuntimeCheck.notNull(queueKey, "queueKey");
		synchronized (this.lifecycleLock) {
			this.checkAcceptingTasks();
			CovalentWorkerPool dedicatedWorkerPool = this.dedicatedWorkerPools.get(queueKey);
			if (dedicatedWorkerPool == null || !dedicatedWorkerPool.isRunning()) {
				dedicatedWorkerPool = new CovalentWorkerPool(1, "covalent-async-" + queueKey, this.workerQueueCapacity,
						queueKey);
				this.dedicatedWorkerPools.put(queueKey, dedicatedWorkerPool);
				this.startReclaimReaperIfStopped();
			}
			return dedicatedWorkerPool;
		}
	}

	private void checkAcceptingTasks() {
		if (!this.acceptingTasks) {
			throw new RejectedExecutionException("Async manager is stopped.");
		}
	}

	private void startReclaimReaperIfStopped() {
		if (this.reclaimReaper != null) {
			return;
		}
		this.reclaimReaperRunning = true;
		Thread reaper = new Thread(this::runReclaimReaper, "covalent-async-reclaim");
		reaper.setDaemon(true);
		reaper.start();
		this.reclaimReaper = reaper;
	}

	private void runReclaimReaper() {
		long nextReclaimNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RECLAIM_TICK_MILLIS);
		while (this.reclaimReaperRunning && !Thread.currentThread().isInterrupted()) {
			try {
				this.awaitTimer(nextReclaimNanos);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				break;
			}
			try {
				this.runDuePeriodicTasks();
				if (System.nanoTime() - nextReclaimNanos >= 0) {
					nextReclaimNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RECLAIM_TICK_MILLIS);
					this.reclaimIdleDedicatedPools();
				}
			} catch (RuntimeException failure) {
				// The reaper also dispatches the periodic tasks and is never restarted: it must survive a failed pass.
				CovalentDiagnostics.severe(log, "Async reclaim reaper pass failed; it will run again.", failure);
			}
		}
	}

	/** Waits until the next reclaim pass or the next periodic task is due, or until woken. */
	private void awaitTimer(final long reclaimAtNanos) throws InterruptedException {
		synchronized (this.timerLock) {
			long now = System.nanoTime();
			long waitNanos = Math.min(reclaimAtNanos - now, this.nanosUntilNextPeriodicTask(now));
			if (waitNanos > 0) {
				this.timerLock.wait(waitNanos / 1_000_000L, (int) (waitNanos % 1_000_000L));
			}
		}
	}

	private long nanosUntilNextPeriodicTask(final long now) {
		long nearest = Long.MAX_VALUE;
		for (PeriodicTask periodic : this.periodicTasks) {
			if (!periodic.cancelled && !periodic.running) {
				nearest = Math.min(nearest, periodic.dueNanos - now);
			}
		}
		return nearest;
	}

	private void runDuePeriodicTasks() {
		long now = System.nanoTime();
		for (PeriodicTask periodic : this.periodicTasks) {
			if (!periodic.cancelled && !periodic.running && now - periodic.dueNanos >= 0) {
				this.dispatchPeriodicRun(periodic);
			}
		}
	}

	/** Queues one run of a periodic task on its pool, as if its owner had submitted it. */
	private void dispatchPeriodicRun(final PeriodicTask periodic) {
		periodic.running = true;
		Class<?> previous = TaskSubmitter.enter(periodic.owner);
		try {
			CovalentWorkerPool pool = periodic.queueKey == null ? this.getGeneralWorkerPool()
					: this.getDedicatedWorkerPool(periodic.queueKey);
			this.submitTask(pool, DEFAULT_MESSAGE_KEY, () -> {
				periodic.runOnce();
				return null;
			}, false, true);
		} catch (RejectedExecutionException rejected) {
			// Queue full (already counted as a rejected task) or manager stopping: skip this run, try the next.
			periodic.runFinished();
		} finally {
			TaskSubmitter.exit(previous);
		}
	}

	/**
	 * Runs both dedicated-pool reclaim policies (idle timeout, least-recently-used
	 * cap) in a single pass, holding {@code lifecycleLock} throughout so a reclaim
	 * decision can never interleave with {@link #getDedicatedWorkerPool} handing
	 * out a reference to the same pool.
	 */
	private void reclaimIdleDedicatedPools() {
		synchronized (this.lifecycleLock) {
			if (!this.reclaimReaperRunning) {
				return;
			}
			if (this.dedicatedWorkerIdleReclaimEnabled) {
				long cutoffMillis = System.currentTimeMillis() - toMillisSaturated(this.dedicatedWorkerIdleTimeout);
				this.dedicatedWorkerPools.entrySet()
						.removeIf(entry -> entry.getValue().tryReclaimIfIdleSince(cutoffMillis));
			}
			if (this.dedicatedWorkerPoolCapReclaimEnabled
					&& this.dedicatedWorkerPools.size() > this.maxDedicatedWorkerPools) {
				List<Map.Entry<String, CovalentWorkerPool>> byLastActivity = new ArrayList<>(
						this.dedicatedWorkerPools.entrySet());
				byLastActivity.sort(Comparator.comparingLong(entry -> entry.getValue().getLastActivityAt()));
				long now = System.currentTimeMillis();
				for (Map.Entry<String, CovalentWorkerPool> candidate : byLastActivity) {
					if (this.dedicatedWorkerPools.size() <= this.maxDedicatedWorkerPools) {
						break;
					}
					if (candidate.getValue().tryReclaimIfIdleSince(now)) {
						this.dedicatedWorkerPools.remove(candidate.getKey());
					}
				}
			}
		}
	}

	private void handleUnhandledFailure(final String message, final Throwable failure) {
		try {
			this.unhandledFailureHandler.handleFailure(message, failure);
		} catch (RuntimeException handlerFailure) {
			CovalentDiagnostics.severe(log, "Unhandled async failure handler failed.", handlerFailure);
			this.logUnhandledFailure(message, failure);
		}
	}

	private void logUnhandledFailure(final String message, final Throwable failure) {
		CovalentDiagnostics.severe(log, message, failure);
	}

	/**
	 * Parses the duration text grammar documented on
	 * {@link AsyncParameters#setDedicatedWorkerIdleTimeout(String)}: one or more
	 * {@code <amount><unit>} tokens (unit one of {@code s}, {@code mn}, {@code h},
	 * {@code d}, {@code w}), combinable in any order, with whitespace optionally
	 * allowed both between tokens and between an amount and its own unit.
	 */
	private static long toMillisSaturated(final Duration duration) {
		try {
			return duration.toMillis();
		} catch (ArithmeticException tooLarge) {
			return Long.MAX_VALUE;
		}
	}

	private static Duration parseDurationText(final String durationText) {
		RuntimeCheck.notNull(durationText, "durationText");
		String trimmed = durationText.trim();
		if (trimmed.isEmpty()) {
			throw invalidDuration(durationText);
		}
		Duration total = Duration.ZERO;
		Matcher matcher = DURATION_TOKEN.matcher(trimmed);
		int position = 0;
		while (position < trimmed.length()) {
			while (position < trimmed.length() && Character.isWhitespace(trimmed.charAt(position))) {
				position++;
			}
			if (position >= trimmed.length()) {
				break;
			}
			if (!matcher.find(position) || matcher.start() != position) {
				throw invalidDuration(durationText);
			}
			try {
				long amount = Long.parseLong(matcher.group(1));
				total = total.plus(unitToDuration(matcher.group(2), amount));
			} catch (NumberFormatException | ArithmeticException tooLarge) {
				throw new IllegalArgumentException("Duration text \"" + durationText + "\" is too large.", tooLarge);
			}
			position = matcher.end();
		}
		return total;
	}

	private static Duration unitToDuration(final String unit, final long amount) {
		switch (unit) {
			case "s":
				return Duration.ofSeconds(amount);
			case "mn":
				return Duration.ofMinutes(amount);
			case "h":
				return Duration.ofHours(amount);
			case "d":
				return Duration.ofDays(amount);
			case "w":
				return Duration.ofDays(amount * 7);
			default:
				throw new IllegalStateException("Unreachable: unknown duration unit \"" + unit + "\".");
		}
	}

	private static IllegalArgumentException invalidDuration(final String durationText) {
		return new IllegalArgumentException("Invalid duration text \"" + durationText
				+ "\"; expected one or more \"<amount><unit>\" tokens (unit one of s, mn, h, d, w), for example "
				+ "\"30 mn\" or \"3w 1d 6h\".");
	}

	/** A task run again and again; see {@link AsyncServices#scheduleWithFixedDelay(Duration, Duration, Runnable)}. */
	private final class PeriodicTask implements ScheduledTask {

		/** The component that scheduled it, {@code null} when scheduled outside any component. */
		private final Class<?> owner;

		/** The dedicated pool it runs on, {@code null} for the general pool. */
		private final String queueKey;

		private final long delayNanos;

		private final Runnable task;

		private volatile boolean cancelled;

		/** Whether a run is queued or in progress: the next one is due only once it finishes. */
		private volatile boolean running;

		/** When the next run is due, on the {@link System#nanoTime()} clock. */
		private volatile long dueNanos;

		private PeriodicTask(final Class<?> owner, final String queueKey, final long delayNanos, final Runnable task,
				final long dueNanos) {
			this.owner = owner;
			this.queueKey = queueKey;
			this.delayNanos = delayNanos;
			this.task = task;
			this.dueNanos = dueNanos;
		}

		@Override
		public void cancel() {
			this.cancelled = true;
			AsyncManagerImpl.this.periodicTasks.remove(this);
		}

		@Override
		public boolean isCancelled() {
			return this.cancelled;
		}

		/** Runs the task on a pool thread; a failure is reported and never stops the repetition. */
		private void runOnce() {
			try {
				if (!this.cancelled) {
					this.task.run();
				}
			} catch (Throwable e) {
				if (!this.cancelled) {
					AsyncManagerImpl.this.handleUnhandledFailure("Periodic async task failed.",
							StackTraceCleaner.cleanIfEnabled(e));
				}
			} finally {
				this.runFinished();
			}
		}

		/** Schedules the next run one delay from now, unless cancelled. */
		private void runFinished() {
			this.dueNanos = System.nanoTime() + this.delayNanos;
			this.running = false;
			AsyncManagerImpl.this.wakeTimer();
		}
	}

	private class AsyncTask<T> implements Runnable, LabeledTask {

		private final Supplier<T> task;

		private final CompletableFuture<T> future = new CompletableFuture<>();

		private final boolean fireAndForget;

		private final String messageKey;

		/** When it was queued, {@link WorkerPoolTracker#UNKNOWN} when tracking was off. */
		private long enqueuedNanos = WorkerPoolTracker.UNKNOWN;

		/** The component that submitted it, known only while tracking is on. */
		private final Class<?> submitter = AsyncManagerImpl.this.taskTracking ? TaskSubmitter.current() : null;

		private AsyncTask(final Supplier<T> task, final boolean fireAndForget, final String messageKey) {
			this.task = task;
			this.fireAndForget = fireAndForget;
			this.messageKey = messageKey;
		}

		@Override
		public void run() {
			if (this.future.isDone()) {
				return;
			}
			try {
				this.future.complete(this.task.get());
			} catch (Throwable e) {
				Throwable failure = StackTraceCleaner.cleanIfEnabled(e);
				this.future.completeExceptionally(failure);
				if (this.fireAndForget) {
					AsyncManagerImpl.this.handleUnhandledFailure("Async task failed.", failure);
				}
			}
		}

		/** @return its message key, when one was given */
		@Override
		public String taskLabel() {
			return DEFAULT_MESSAGE_KEY.equals(this.messageKey) ? null : this.messageKey;
		}

		@Override
		public Class<?> taskComponent() {
			return this.submitter;
		}

		private CompletionStage<T> getStage() {
			return this.future.minimalCompletionStage();
		}

		private void cancel(final String reason) {
			this.future.completeExceptionally(new CancellationException(reason));
		}
	}

	private class CovalentWorkerPool {

		/** Bound on how long stop() waits for each worker thread to actually
		 * terminate after being interrupted - see awaitWorkersTerminated(List).
		 * Same order of magnitude as REAPER_TERMINATION_TIMEOUT_MILLIS: a
		 * well-behaved worker terminates within microseconds of being
		 * interrupted, so this bound is only ever spent on a task that does
		 * not honor interruption. */
		private static final long WORKER_TERMINATION_TIMEOUT_MILLIS = 2000L;

		/** Unbounded: its capacity is checked by submit(), so that it can change on the live pool. */
		private final BlockingDeque<AsyncTask<?>> queue = new LinkedBlockingDeque<>();

		private volatile int queueCapacity;

		private final String workerNamePrefix;

		/** How many workers this pool aims at: surplus workers retire once their current task is done. */
		private int targetWorkerCount;

		private int nextWorkerIndex;

		/** Whether the workers are started: by startWorkers(), or on demand by the first submission. */
		private boolean workersStarted;

		private final Map<String, List<AsyncTask<?>>> pendingTasksByMessageKey = new HashMap<>();

		private final List<Thread> workers = new ArrayList<>();

		private final AtomicInteger activeTaskCount = new AtomicInteger();

		private volatile long lastActivityAt = System.currentTimeMillis();

		private boolean running = true;

		/** The queue key of a dedicated pool, {@code null} for the general pool. */
		private final String queueKey;

		private final WorkerPoolTracker tracker = new WorkerPoolTracker(() -> AsyncManagerImpl.this.taskTracking);

		private CovalentWorkerPool(final int workerCount, final String workerNamePrefix, final int queueCapacity,
				final String queueKey) {
			this.queueCapacity = queueCapacity;
			this.workerNamePrefix = workerNamePrefix;
			this.queueKey = queueKey;
			this.setWorkerCount(workerCount);
		}

		/**
		 * Grows or shrinks this pool, live: new workers start at once, while
		 * surplus workers finish their current task, then retire.
		 */
		private synchronized void setWorkerCount(final int workerCount) {
			this.targetWorkerCount = workerCount;
			while (this.running && this.workersStarted && this.workers.size() < this.targetWorkerCount) {
				Thread worker = new Thread(this::runWorker, this.workerNamePrefix + "-" + this.nextWorkerIndex++);
				// Daemon, like the reclaim reaper (see startReclaimReaperIfStopped()): a
				// caller that starts a container and lets its own thread run to
				// completion without ever calling stop() must not have the JVM
				// hang forever waiting on these worker threads. A non-daemon worker would do exactly that: the JVM cannot exit normally until every non-daemon thread terminates, and nothing
				// terminates one of these on its own short of an explicit
				// stop()/interrupt().
				worker.setDaemon(true);
				this.workers.add(worker);
				worker.start();
			}
			// wakes idle workers, so that surplus ones retire right away
			this.notifyAll();
		}

		/** Starts this pool's workers, if not started yet. */
		private synchronized void startWorkers() {
			this.workersStarted = true;
			this.setWorkerCount(this.targetWorkerCount);
		}

		/**
		 * Applies a new queue capacity, live. Tasks already queued are kept even
		 * beyond it; submissions are rejected until the queue is back under it.
		 */
		private synchronized void setQueueCapacity(final int queueCapacity) {
			this.queueCapacity = queueCapacity;
		}

		/** Removes the calling worker from this pool when it has more workers than it aims at. */
		private boolean retireIfSurplus() {
			if (this.workers.size() > this.targetWorkerCount) {
				this.workers.remove(Thread.currentThread());
				return true;
			}
			return false;
		}

		private void submit(final String messageKey, final AsyncTask<?> task, final boolean flushPending) {
			boolean queueFull = false;
			List<AsyncTask<?>> replacedTasks = List.of();
			synchronized (this) {
				if (!this.running) {
					throw new RejectedExecutionException(POOL_STOPPED_MESSAGE);
				}
				List<AsyncTask<?>> pendingTasks = this.pendingTasksByMessageKey.getOrDefault(messageKey,
						new ArrayList<>());
				if (flushPending) {
					replacedTasks = this.removePendingTasks(pendingTasks);
				}
				task.enqueuedNanos = this.tracker.enqueueTime();
				if (this.queue.size() >= this.queueCapacity || !this.queue.offerLast(task)) {
					this.tracker.taskRejected();
					queueFull = true;
				} else {
					pendingTasks.add(task);
					this.pendingTasksByMessageKey.put(messageKey, pendingTasks);
					if (!this.workersStarted) {
						// on demand: the first task starts the workers
						this.startWorkers();
					}
					this.lastActivityAt = System.currentTimeMillis();
					// wake a worker parked in takeNextTask()'s wait() - see there for why
					// this pool does not rely on the queue's own blocking take().
					this.notifyAll();
				}
			}
			// Outside the pool's lock: cancelling a task completes its future, which runs the callbacks attached
			// to it on this thread, and a callback calling back into the manager takes lifecycleLock, which
			// setWorkerQueueCapacity and setGeneralWorkerCount hold while they take this pool's lock.
			for (AsyncTask<?> replacedTask : replacedTasks) {
				replacedTask.cancel("Async task was replaced by a newer pending task with the same message key.");
			}
			if (queueFull) {
				// Outside the pool's lock: the caller is told by the exception, but a fire-and-forget caller
				// may ignore it, so the loss is also logged and recorded for the container's report.
				AsyncManagerImpl.this.hadRejectedTasks = true;
				CovalentDiagnostics.warning(log, "Async task refused: the worker pool queue is full (capacity "
						+ this.queueCapacity + ", pool " + (this.queueKey == null ? "general" : this.queueKey)
						+ "). A producer is outpacing its consumers, or a task is stalled.");
				throw new RejectedExecutionException(
						"Async worker pool queue is full (capacity " + this.queueCapacity + ").");
			}
		}

		/**
		 * Removes the given pending tasks from the queue, counting them as cancelled, without completing their
		 * futures: the caller cancels the returned tasks once it no longer holds this pool's lock.
		 *
		 * @param pendingTasks the pending tasks of one message key, cleared on return
		 * @return the tasks actually removed from the queue
		 */
		private List<AsyncTask<?>> removePendingTasks(final List<AsyncTask<?>> pendingTasks) {
			List<AsyncTask<?>> removedTasks = new ArrayList<>();
			for (AsyncTask<?> pendingTask : pendingTasks) {
				if (this.queue.remove(pendingTask)) {
					this.tracker.taskCancelled();
					removedTasks.add(pendingTask);
				}
			}
			pendingTasks.clear();
			return removedTasks;
		}

		private void runWorker() {
			WorkerPoolTracker.Worker tracked = this.tracker.join();
			try {
				this.runTasks(tracked);
			} finally {
				this.tracker.leave(tracked);
			}
		}

		private void runTasks(final WorkerPoolTracker.Worker tracked) {
			while (this.isRunning()) {
				try {
					AsyncTask<?> task = this.takeNextTask();
					if (task == null && !this.isWorker(Thread.currentThread())) {
						// retired: the pool shrank
						return;
					}
					if (task == null) {
						// Pool was stopped while this worker was parked in takeNextTask();
						// isRunning() will be false and the outer loop exits.
						continue;
					}
					this.removeFromPendingIndex(task);
					this.tracker.taskStarted(tracked, task, task.enqueuedNanos);
					try {
						task.run();
					} finally {
						this.tracker.taskFinished(tracked);
						this.onTaskCompleted();
					}
				} catch (InterruptedException e) {
					if (this.isRunning()) {
						CovalentDiagnostics.severe(log, "Async worker was interrupted unexpectedly.", e);
					}
				} catch (Throwable e) {
					// AsyncTask handles user task failures. This protects the worker loop itself.
					CovalentDiagnostics.severe(log, "Async worker failed unexpectedly.", e);
				}
			}
		}

		/**
		 * Atomically dequeues the next task and marks it active, both under this
		 * pool's own monitor - the same lock {@link #tryReclaimIfIdleSince} takes
		 * for its check. This pool deliberately does not rely on
		 * {@code queue.takeFirst()}'s own blocking: that would dequeue the task
		 * and return to the caller before {@code activeTaskCount} could be
		 * incremented, leaving a window in which a concurrent reclaim sees an
		 * empty queue and zero active tasks - even though a worker has already
		 * claimed a task and is about to run it - and reclaims (interrupting)
		 * the pool out from under it. Doing the dequeue-and-claim as one
		 * operation under {@code this} closes that window entirely. Returns
		 * {@code null} once the pool has been stopped and its queue has nothing
		 * left to drain.
		 */
		private synchronized AsyncTask<?> takeNextTask() throws InterruptedException {
			while (this.running && this.queue.isEmpty()) {
				if (this.retireIfSurplus()) {
					return null;
				}
				this.wait();
			}
			if (this.retireIfSurplus()) {
				return null;
			}
			AsyncTask<?> task = this.queue.pollFirst();
			if (task != null) {
				this.activeTaskCount.incrementAndGet();
			}
			return task;
		}

		/**
		 * Reports whether {@code thread} is one of this pool's workers.
		 *
		 * @param thread the thread to test
		 * @return {@code true} if {@code thread} is a worker of this pool
		 */
		private synchronized boolean isWorker(final Thread thread) {
			return this.workers.contains(thread);
		}

		/**
		 * Marks the just-finished task as no longer active and refreshes
		 * {@code lastActivityAt} to the completion time - not just the
		 * submission time - so a long-running task does not leave the pool
		 * immediately eligible for idle reclaim the instant it finishes.
		 */
		private synchronized void onTaskCompleted() {
			this.activeTaskCount.decrementAndGet();
			this.lastActivityAt = System.currentTimeMillis();
		}

		/**
		 * Removes a just-dequeued task from the pending-message-key index, and
		 * prunes the index entry entirely once it no longer references any task -
		 * otherwise a distinct, one-shot message key used once and never reused
		 * would leave an empty list behind forever.
		 */
		private synchronized void removeFromPendingIndex(final AsyncTask<?> task) {
			List<AsyncTask<?>> pendingTasks = this.pendingTasksByMessageKey.get(task.messageKey);
			if (pendingTasks == null) {
				return;
			}
			pendingTasks.remove(task);
			if (pendingTasks.isEmpty()) {
				this.pendingTasksByMessageKey.remove(task.messageKey);
			}
		}

		/**
		 * @return {@code true} if every worker thread actually terminated
		 *         before {@link #WORKER_TERMINATION_TIMEOUT_MILLIS} was
		 *         exceeded
		 */
		private boolean stop() {
			List<AsyncTask<?>> pendingTasks = new ArrayList<>();
			List<Thread> workersToStop;
			synchronized (this) {
				this.running = false;
				this.queue.drainTo(pendingTasks);
				this.pendingTasksByMessageKey.clear();
				workersToStop = new ArrayList<>(this.workers);
			}

			if (!pendingTasks.isEmpty()) {
				// Their callers only see a CancellationException on a future they may not keep: log the loss.
				CovalentDiagnostics.warning(log, pendingTasks.size() + " pending async task(s) cancelled: the "
						+ (this.queueKey == null ? "general" : this.queueKey) + " worker pool is stopping.");
			}
			for (AsyncTask<?> pendingTask : pendingTasks) {
				pendingTask.cancel(POOL_STOPPED_MESSAGE);
			}
			for (Thread worker : workersToStop) {
				// Wakes a worker parked in takeNextTask()'s wait() too, since
				// interrupt() interrupts Object.wait() the same way it
				// interrupts a blocking queue take.
				worker.interrupt();
			}
			return this.awaitWorkersTerminated(workersToStop);
		}

		/**
		 * Blocks (bounded) until every worker thread has actually terminated,
		 * instead of only requesting interruption and returning immediately -
		 * so a container is never observed as stopped while its async worker
		 * threads are still alive. A worker between tasks - or running a task
		 * that honors interruption - terminates within microseconds of being
		 * interrupted; exceeding the bound is a sign of a task that does not
		 * honor interruption (a CPU-bound loop that never checks
		 * Thread.interrupted(), an uninterruptible blocking call), logged
		 * rather than thrown so a single misbehaving task cannot hang
		 * container shutdown forever.
		 *
		 * @return {@code true} if every worker terminated within the bound
		 */
		private boolean awaitWorkersTerminated(final List<Thread> workersToStop) {
			boolean allTerminated = true;
			for (Thread worker : workersToStop) {
				try {
					worker.join(WORKER_TERMINATION_TIMEOUT_MILLIS);
				} catch (InterruptedException e) {
					// Preserve the calling thread's own interrupted status rather
					// than swallowing it; still fall through to the isAlive()
					// check below, and keep awaiting the remaining workers.
					Thread.currentThread().interrupt();
				}
				if (worker.isAlive()) {
					allTerminated = false;
					CovalentDiagnostics.severe(log, "Async worker " + worker.getName() + " did not terminate within "
							+ WORKER_TERMINATION_TIMEOUT_MILLIS
							+ "ms of stop(); it is likely running a task that does not honor interruption.", null);
				}
			}
			return allTerminated;
		}

		/**
		 * Stops this pool, atomically, only if it is currently idle (empty queue,
		 * no task running) and has been idle since at or before
		 * {@code cutoffMillis}. Used by both reclaim policies: the idle-timeout
		 * policy passes {@code now - idleTimeout}, the least-recently-used cap
		 * policy passes {@code now} (i.e. "idle right now, any duration").
		 *
		 * @return {@code true} if this call stopped the pool
		 */
		private boolean tryReclaimIfIdleSince(final long cutoffMillis) {
			synchronized (this) {
				if (!this.running || !this.queue.isEmpty() || this.activeTaskCount.get() > 0
						|| this.lastActivityAt > cutoffMillis) {
					return false;
				}
				this.running = false;
				for (Thread worker : this.workers) {
					worker.interrupt();
				}
			}
			return true;
		}

		/** @return this pool, as read now */
		private WorkerPoolState state() {
			int queued;
			int capacity;
			int target;
			synchronized (this) {
				queued = this.queue.size();
				capacity = this.queueCapacity;
				target = this.targetWorkerCount;
			}
			return new WorkerPoolState(this.queueKey == null ? "general" : this.queueKey,
					this.queueKey == null ? WorkerPoolKind.GENERAL : WorkerPoolKind.DEDICATED, target, queued, capacity,
					this.tracker.rejectedTasks(), this.tracker.cancelledTasks(), AsyncManagerImpl.this.taskTracking,
					this.tracker.completedTasks(),
					this.tracker.queueWaits(), this.tracker.durations(), this.tracker.componentBusyNanos(),
					this.tracker.workers());
		}

		private long getLastActivityAt() {
			return this.lastActivityAt;
		}

		private synchronized boolean isRunning() {
			return this.running;
		}

	}

}
