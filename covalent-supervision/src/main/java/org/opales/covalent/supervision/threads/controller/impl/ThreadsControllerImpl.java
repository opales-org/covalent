/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.controller.impl;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opales.covalent.supervision.SupervisionThreads;
import org.opales.covalent.container.component.ComponentAnnotations;
import org.opales.covalent.container.component.annotations.AccessedParameters;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.workers.WorkerPoolKind;
import org.opales.covalent.container.workers.WorkerPoolState;
import org.opales.covalent.container.workers.WorkerState;
import org.opales.covalent.supervision.containermanager.raw.ObservedContainerControl;
import org.opales.covalent.supervision.observed.ObservedCallingThread;
import org.opales.covalent.supervision.observed.ObservedContainerConnectionEvents;
import org.opales.covalent.supervision.observed.ObservedParameters;
import org.opales.covalent.supervision.observed.ObservedRegistryReadServices;
import org.opales.covalent.supervision.observed.ObservedWorkersServices;
import org.opales.covalent.supervision.sequencediagram.controller.SequenceDiagramServices;
import org.opales.covalent.supervision.supervisionhmi.SupervisorSettings;
import org.opales.covalent.supervision.threads.controller.ThreadsController;
import org.opales.covalent.supervision.threads.controller.ThreadsControllerServices;
import org.opales.covalent.supervision.threads.model.Contention;
import org.opales.covalent.supervision.threads.model.Phrase;
import org.opales.covalent.supervision.threads.model.ThreadActivity;
import org.opales.covalent.supervision.threads.model.ThreadStack;
import org.opales.covalent.supervision.threads.model.ThreadsModelServices;
import org.opales.covalent.supervision.threads.model.ThreadsSnapshot;

/**
 * Samples the observed worker pools every quarter of a second while a container is observed:
 * the pools' own figures, and for each of their threads its JVM state, lock and CPU time. Each
 * thread's activity combines the two: the pool knows whether it is in a task, the JVM whether it
 * runs, waits or is blocked. It also notes what happened - rejections, resized pools, blocked
 * threads, long tasks, deadlocks - and how hard each pool is pressed over the last seconds.
 */
@Implementation
public class ThreadsControllerImpl implements ThreadsController, ThreadsControllerServices,
		ObservedContainerConnectionEvents {

	/** Creates the Controller; the container injects the services it uses. */
	public ThreadsControllerImpl() {
		// Nothing to initialize.
	}

	private static final Logger log = Logger.getLogger(ThreadsControllerImpl.class.getName());

	/** How often the pools are sampled. */
	static final long PERIOD_MILLIS = 250;

	/** The samples a pool's contention is judged on: the last 5 seconds. */
	private static final int CONTENTION_SAMPLES = 20;

	/** The samples of the minute's occupancy. */
	private static final int MINUTE_SAMPLES = 240;

	/** Deadlocks are looked for once every that many samples: once a second. */
	private static final int DEADLOCK_EVERY = 4;

	/** The log entries kept. */
	private static final int LOG = 60;

	/** Above this average occupancy, a pool is under strain. */
	private static final double STRAIN = 0.7;

	/** Above this average occupancy with tasks queued, a pool is saturated. */
	private static final double SATURATION = 0.9;

	private static final int STACK_DEPTH = 64;

	@UsedServices
	private ObservedWorkersServices useObservedWorkersServices;

	@AccessedParameters
	private ObservedParameters accessObservedParameters;

	@UsedServices
	private ThreadsModelServices useModelServices;

	@UsedServices
	private ObservedRegistryReadServices useObservedRegistryReadServices;

	@UsedServices
	private ObservedContainerControl useObservedContainerControl;

	@UsedServices
	private SequenceDiagramServices useSequenceDiagramServices;

	private final ThreadMXBean jvmThreads = ManagementFactory.getThreadMXBean();

	private ScheduledExecutorService sampler;

	private boolean connected;

	/** Changes at every connection and disconnection: a sample made under an earlier one is stale. */
	private volatile long connectionGeneration;

	/** Serializes the publication of a sample with the one of the empty snapshot at a disconnection. */
	private final Object publicationLock = new Object();

	private long sampleCount;

	/** Per pool: its last occupancies, queue sizes and rejection totals, oldest first. */
	private final Map<String, Deque<PoolSample>> poolHistory = new HashMap<>();

	private final Map<String, WorkerPoolState> previousPools = new HashMap<>();

	/** Per pool: its rejected tasks total at each of the last minute's samples, oldest first. */
	private final Map<String, Deque<Long>> rejectedMinute = new HashMap<>();

	/** Per pool under contention: when it began, and whether it reached saturation - logged once. */
	private final Map<String, Episode> episodes = new HashMap<>();

	private final Map<Long, ThreadsSnapshot.Worker> previousWorkers = new HashMap<>();

	/** The busy and total workers of the last minute's samples. */
	private final Deque<double[]> minute = new ArrayDeque<>();

	/** Per tunable pool kind: its workers and queue capacity when first sampled since connected. */
	private final Map<WorkerPoolKind, ThreadsSnapshot.Tuning> tuningAtConnection = new EnumMap<>(WorkerPoolKind.class);

	/** Each thread's busy time at the previous sample - see {@link WorkerState#busyNanos()}. */
	private final Map<Long, Long> previousBusy = new HashMap<>();

	/** The calls the calling threads had run at the previous sample, {@code -1} when unknown. */
	private long previousCalls = -1;

	/** When the previous sample was taken, {@code 0} before the first one. */
	private long previousSampleNanos;

	private final LinkedList<ThreadsSnapshot.LogEntry> logEntries = new LinkedList<>();

	/** The long tasks running now, by thread: their log entry is updated while they last. */
	private final Map<Long, LongTask> longTasks = new HashMap<>();

	private List<ThreadsSnapshot.Deadlock> deadlocks = List.of();

	private Set<Long> deadlockedIds = Set.of();

	private Boolean previousTracking;

	/** A pool's contention, from leaving fluid until fluid again. */
	private static final class Episode {

		final long sinceMillis;

		boolean saturated;

		Episode(final long sinceMillis) {
			this.sinceMillis = sinceMillis;
		}
	}

	private record PoolSample(double occupancy, int queued, long rejected) {
	}

	/** A task past the long task threshold: its log entry says how long it lasts, until it ends. */
	private static final class LongTask {

		final Phrase subject;

		final String label;

		ThreadsSnapshot.LogEntry entry;

		long nanos;

		LongTask(final Phrase subject, final String label, final ThreadsSnapshot.LogEntry entry, final long nanos) {
			this.subject = subject;
			this.label = label;
			this.entry = entry;
			this.nanos = nanos;
		}
	}

	/**
	 * Starts sampling at once when a container is already observed: the supervisor connects to
	 * its first container before this component activates, so that connection is never notified
	 * to it.
	 */
	@Activation
	public void start() {
		if (this.useObservedContainerControl.getObservedContainerName() != null) {
			this.onContainerConnected();
		}
	}

	// --- ObservedContainerConnectionEvents ---

	@Override
	public void onContainerConnected() {
		synchronized (this) {
			this.clear();
			this.connected = true;
			this.connectionGeneration++;
			this.startSampler();
		}
		if (SupervisorSettings.getBoolean(SupervisorSettings.THREADS_TRACKING_ON_CONNECT, false)) {
			this.accessObservedParameters.setTaskTracking(true);
		}
	}

	@Override
	public void onContainerDisconnected() {
		synchronized (this) {
			this.stopSampler();
			this.connected = false;
			this.connectionGeneration++;
			this.clear();
		}
		// A call to another component: made outside this instance's lock. A sample being published
		// finishes first, so that the empty snapshot is the last one the model receives.
		synchronized (this.publicationLock) {
			this.useModelServices.setSnapshot(ThreadsSnapshot.EMPTY);
		}
	}

	/** Stops the sampling of the observed pools. */
	@Passivation
	public synchronized void stop() {
		this.stopSampler();
	}

	// --- ThreadsControllerServices ---

	@Override
	public void setTaskTracking(final boolean tracking) {
		this.accessObservedParameters.setTaskTracking(tracking);
	}

	@Override
	public void setWorkerCount(final WorkerPoolKind kind, final int count) {
		if (ThreadsSnapshot.Tuning.tunable(kind)) {
			this.accessObservedParameters.setWorkerCount(kind,
					Math.clamp(count, ThreadsSnapshot.Tuning.MIN_WORKERS, ThreadsSnapshot.Tuning.MAX_WORKERS));
		}
	}

	@Override
	public void setQueueCapacity(final WorkerPoolKind kind, final int capacity) {
		if (ThreadsSnapshot.Tuning.tunable(kind)) {
			this.accessObservedParameters.setQueueCapacity(kind, Math.clamp(capacity,
					ThreadsSnapshot.Tuning.MIN_QUEUE_CAPACITY, ThreadsSnapshot.Tuning.MAX_QUEUE_CAPACITY));
		}
	}

	@Override
	public void readStack(final long threadId) {
		ThreadInfo info = this.jvmThreads.getThreadInfo(threadId, STACK_DEPTH);
		List<String> frames = new ArrayList<>();
		List<String> calls = new ArrayList<>();
		if (info != null) {
			Map<String, Class<?>> components = this.componentsByImplementation();
			for (StackTraceElement frame : info.getStackTrace()) {
				frames.add(frame.toString());
				String call = componentCall(frame, components);
				if (call != null && (calls.isEmpty() || !calls.get(calls.size() - 1).equals(call))) {
					calls.add(call);
				}
			}
		}
		this.useModelServices.setStack(new ThreadStack(threadId, frames, calls));
	}

	@Override
	public void toggleRecording(final Set<Class<?>> components) {
		if (this.useSequenceDiagramServices.isRecording()) {
			this.useSequenceDiagramServices.stopRecording();
		} else if (!components.isEmpty()) {
			this.useSequenceDiagramServices.startRecording(Set.copyOf(components));
		}
	}

	@Override
	public boolean isRecording() {
		return this.useSequenceDiagramServices.isRecording();
	}

	/** The observed application's components, by the name of the class implementing them. */
	private Map<String, Class<?>> componentsByImplementation() {
		Map<String, Class<?>> components = new HashMap<>();
		try {
			for (Map.Entry<Class<?>, Object> entry : this.useObservedRegistryReadServices.getInstances().entrySet()) {
				// Covalent's own components are the plumbing every task runs through: left out.
				if (entry.getValue() != null && !ComponentAnnotations.isInternalComponent(entry.getKey())) {
					components.put(entry.getValue().getClass().getName(), entry.getKey());
				}
			}
		} catch (RuntimeException e) {
			// Nothing observed any more: no component to name.
		}
		return components;
	}

	/**
	 * A frame as a component call, {@code Component.method()}, when it runs in one of the observed
	 * components' classes - their inner classes and lambdas included; {@code null} otherwise.
	 */
	static String componentCall(final StackTraceElement frame, final Map<String, Class<?>> components) {
		Class<?> component = componentOf(frame, components);
		if (component == null) {
			return null;
		}
		String method = frame.getMethodName();
		if (method.startsWith("lambda$")) {
			String[] parts = method.split("\\$");
			method = parts.length > 1 ? parts[1] : method;
		}
		return component.getSimpleName() + "." + method + "()";
	}

	/** The observed component whose class - or inner class, or lambda - runs a frame, {@code null} if none. */
	private static Class<?> componentOf(final StackTraceElement frame, final Map<String, Class<?>> components) {
		String className = frame.getClassName();
		int inner = className.indexOf('$');
		return components.get(inner < 0 ? className : className.substring(0, inner));
	}

	// --- Sampling ---

	private void startSampler() {
		this.stopSampler();
		this.sampler = Executors.newSingleThreadScheduledExecutor(SupervisionThreads.factory("threads-sampler"));
		this.sampler.scheduleAtFixedRate(this::sampleSafely, 0, PERIOD_MILLIS, TimeUnit.MILLISECONDS);
	}

	private void stopSampler() {
		if (this.sampler != null) {
			this.sampler.shutdownNow();
			this.sampler = null;
		}
	}

	private void clear() {
		this.sampleCount = 0;
		this.poolHistory.clear();
		this.previousPools.clear();
		this.episodes.clear();
		this.rejectedMinute.clear();
		this.previousWorkers.clear();
		this.minute.clear();
		this.previousBusy.clear();
		this.previousSampleNanos = 0;
		this.previousCalls = -1;
		this.logEntries.clear();
		this.longTasks.clear();
		this.deadlocks = List.of();
		this.deadlockedIds = Set.of();
		this.previousTracking = null;
		this.tuningAtConnection.clear();
	}

	private void sampleSafely() {
		try {
			long generation = this.connectionGeneration;
			ThreadsSnapshot snapshot = this.sample();
			if (snapshot != null) {
				// A call to another component: made outside this instance's lock.
				synchronized (this.publicationLock) {
					if (generation == this.connectionGeneration) {
						this.useModelServices.setSnapshot(snapshot);
					}
				}
			}
		} catch (RuntimeException e) {
			// A sample lost, not the sampler: the observed container may be stopping.
			log.log(Level.FINE, "Threads sample failed", e);
		}
	}

	/** One sample, {@code null} once disconnected. */
	ThreadsSnapshot sample() {
		List<WorkerPoolState> states = this.useObservedWorkersServices.readWorkerPools();
		boolean tracking = this.accessObservedParameters.isTaskTracking();
		boolean dataFlows = this.useObservedWorkersServices.isInstrumented();
		List<ObservedCallingThread> calling = dataFlows ? this.useObservedWorkersServices.readCallingThreads() : List.of();
		long now = System.currentTimeMillis();
		synchronized (this) {
			if (!this.connected) {
				return null;
			}
			this.sampleCount++;
			long nowNanos = System.nanoTime();
			long elapsed = this.previousSampleNanos == 0 ? 0 : nowNanos - this.previousSampleNanos;
			this.previousSampleNanos = nowNanos;
			Map<Long, Long> busyNow = new HashMap<>();
			if (this.previousTracking != null && this.previousTracking != tracking) {
				this.log(now, Phrase.of("log.subject.tracking"), Phrase.of(tracking ? "log.trackingOn" : "log.trackingOff"),
						ThreadsSnapshot.Severity.INFO);
			}
			this.previousTracking = tracking;
			Set<Long> poolThreads = new HashSet<>();
			states.forEach(state -> state.workers().forEach(worker -> poolThreads.add(worker.threadId())));
			List<ObservedCallingThread> callers = new ArrayList<>();
			for (ObservedCallingThread caller : calling) {
				if (!poolThreads.contains(caller.threadId())) {
					callers.add(caller);
				}
			}
			Map<Long, ThreadInfo> infos = this.threadInfos(states, callers);
			if (this.sampleCount % DEADLOCK_EVERY == 1) {
				this.findDeadlocks(now, infos.keySet());
			}
			List<ThreadsSnapshot.Pool> pools = new ArrayList<>();
			double busy = 0;
			int total = 0;
			Map<Long, ThreadsSnapshot.Worker> workersNow = new HashMap<>();
			Map<Long, List<Class<?>>> components = new HashMap<>();
			for (WorkerPoolState state : states) {
				List<ThreadsSnapshot.Worker> workers = new ArrayList<>();
				double poolBusy = 0;
				for (WorkerState workerState : state.workers()) {
					double share = this.busyShare(workerState, tracking, elapsed);
					Class<?> finished = this.componentFinishedSince(workerState, tracking);
					if (finished != null) {
						components.put(workerState.threadId(), List.of(finished));
					}
					busyNow.put(workerState.threadId(), workerState.busyNanos());
					ThreadsSnapshot.Worker worker = this.worker(workerState, infos.get(workerState.threadId()), tracking,
							share);
					workers.add(worker);
					workersNow.put(worker.threadId(), worker);
					this.noteWorker(now, worker);
					poolBusy += worker.occupancy();
				}
				busy += poolBusy;
				total += workers.size();
				double occupancy = workers.isEmpty() ? 0 : poolBusy / workers.size();
				WorkerPoolState previous = this.previousPools.get(state.name());
				double tasksPerSecond = previous == null || elapsed <= 0 ? 0
						: Math.max(0, state.completedTasks() - previous.completedTasks()) * 1e9 / elapsed;
				Contention lately = this.contention(state, occupancy);
				long rejectedLastMinute = this.rejectedLastMinute(state);
				// The pool's verdict spans the minute, as its rejections do: a burst seconds ago still counts.
				Contention contention = rejectedLastMinute > 0 ? Contention.SATURATED : lately;
				boolean calmerNow = contention == Contention.SATURATED && lately != Contention.SATURATED;
				this.notePool(now, state, contention);
				ThreadsSnapshot.Tuning tuning = !ThreadsSnapshot.Tuning.tunable(state.kind()) ? null
						: this.tuningAtConnection.computeIfAbsent(state.kind(),
								kind -> new ThreadsSnapshot.Tuning(state.targetWorkerCount(), state.queueCapacity()));
				Map<Class<?>, Double> componentLoad = componentLoad(state, previous, tracking, elapsed);
				// The rest of the workers' time in tasks: tasks of no component known.
				double otherLoad = !tracking || previous == null || elapsed <= 0 ? 0
						: Math.max(0, poolBusy - componentLoad.values().stream().mapToDouble(Double::doubleValue).sum());
				pools.add(new ThreadsSnapshot.Pool(state.name(), state.kind(), state.targetWorkerCount(),
						state.queuedTasks(), state.queueCapacity(), state.rejectedTasks(), state.completedTasks(),
						state.queueWaits(), state.durations(), occupancy, contention, tasksPerSecond, workers, tuning,
						rejectedLastMinute, calmerNow, componentLoad, otherLoad));
			}
			// The calling threads: a call is their task, the observed container's calls their tracking.
			List<ThreadsSnapshot.Worker> callerWorkers = new ArrayList<>();
			long callsNow = 0;
			for (ObservedCallingThread caller : callers) {
				WorkerState asWorker = new WorkerState(caller.threadId(), caller.threadName(), caller.inCall(),
						caller.callNanos(), caller.call(), caller.component(), caller.busyNanos());
				double share = this.busyShare(asWorker, true, elapsed);
				busyNow.put(caller.threadId(), caller.busyNanos());
				ThreadsSnapshot.Worker worker = this.worker(asWorker, infos.get(caller.threadId()), true, share);
				if (!caller.inCall() && worker.activity() != ThreadActivity.BLOCKED) {
					// Outside the components' calls, what the thread does is none of their work.
					worker = new ThreadsSnapshot.Worker(worker.threadId(), worker.name(), ThreadActivity.IDLE,
							worker.jvmState(), -1, null, null, null, worker.cpuNanos(), null, worker.busyShare());
				}
				callerWorkers.add(worker);
				workersNow.put(worker.threadId(), worker);
				if (!caller.components().isEmpty()) {
					List<Class<?>> entered = new ArrayList<>(caller.components());
					if (entered.remove(caller.lastComponent())) {
						entered.add(caller.lastComponent());
					}
					components.put(caller.threadId(), List.copyOf(entered));
				}
				this.noteWorker(now, worker);
				callsNow += caller.calls();
			}
			for (Long threadId : List.copyOf(this.longTasks.keySet())) {
				if (!workersNow.containsKey(threadId)) {
					this.endLongTask(now, threadId);
				}
			}
			this.previousWorkers.clear();
			this.previousWorkers.putAll(workersNow);
			for (ThreadsSnapshot.Pool pool : pools) {
				for (ThreadsSnapshot.Worker worker : pool.workers()) {
					Class<?> component = worker.taskComponent();
					if (component != null && !ComponentAnnotations.isInternalComponent(component)) {
						components.put(worker.threadId(), List.of(component));
					}
				}
			}
			this.previousBusy.clear();
			this.previousBusy.putAll(busyNow);
			Set<String> names = new HashSet<>();
			states.forEach(state -> names.add(state.name()));
			this.poolHistory.keySet().retainAll(names);
			this.previousPools.keySet().retainAll(names);
			this.episodes.keySet().retainAll(names);
			this.rejectedMinute.keySet().retainAll(names);
			this.minute.addLast(new double[] { busy, total });
			while (this.minute.size() > MINUTE_SAMPLES) {
				this.minute.removeFirst();
			}
			double minuteBusy = 0;
			double minuteTotal = 0;
			for (double[] counts : this.minute) {
				minuteBusy += counts[0];
				minuteTotal += counts[1];
			}
			double callsPerSecond = this.previousCalls < 0 || elapsed <= 0 ? 0
					: Math.max(0, callsNow - this.previousCalls) * 1e9 / elapsed;
			this.previousCalls = dataFlows ? callsNow : -1;
			return new ThreadsSnapshot(now, true, tracking, pools,
					minuteTotal == 0 ? 0 : minuteBusy / minuteTotal, this.deadlocks, List.copyOf(this.logEntries),
					new ThreadsSnapshot.Callers(dataFlows, callsPerSecond, callerWorkers), Map.copyOf(components));
		}
	}

	private Map<Long, ThreadInfo> threadInfos(final List<WorkerPoolState> states,
			final List<ObservedCallingThread> callers) {
		List<Long> ids = new ArrayList<>();
		for (WorkerPoolState state : states) {
			for (WorkerState worker : state.workers()) {
				ids.add(worker.threadId());
			}
		}
		for (ObservedCallingThread caller : callers) {
			ids.add(caller.threadId());
		}
		long[] idArray = ids.stream().mapToLong(Long::longValue).toArray();
		Map<Long, ThreadInfo> infos = new HashMap<>();
		if (idArray.length > 0) {
			for (ThreadInfo info : this.jvmThreads.getThreadInfo(idArray)) {
				if (info != null) {
					infos.put(info.getThreadId(), info);
				}
			}
		}
		return infos;
	}

	/**
	 * The average load each component puts on a pool since the previous sample.
	 *
	 * @param state    the pool now
	 * @param previous the pool at the previous sample, {@code null} if none
	 * @param tracking whether tasks are tracked
	 * @param elapsed  the time since the previous sample, in nanoseconds
	 * @return how many of the pool's workers worked for each component on average since the
	 *         previous sample, however short its tasks; Covalent's own components left out
	 */
	static Map<Class<?>, Double> componentLoad(final WorkerPoolState state, final WorkerPoolState previous,
			final boolean tracking, final long elapsed) {
		if (!tracking || previous == null || elapsed <= 0) {
			return Map.of();
		}
		Map<Class<?>, Double> load = new HashMap<>();
		state.componentBusyNanos().forEach((component, busy) -> {
			long spent = busy - previous.componentBusyNanos().getOrDefault(component, 0L);
			if (spent > 0 && !ComponentAnnotations.isInternalComponent(component)) {
				load.put(component, Math.min(state.workers().size(), (double) spent / elapsed));
			}
		});
		return load;
	}

	/**
	 * The component the tasks a worker finished since the previous sample worked for, which
	 * sampling would miss when they are short: known when its busy time grew while it runs no
	 * task now - one still running is caught by the sample itself.
	 *
	 * @param state    the worker now
	 * @param tracking whether tasks are tracked
	 * @return the component the last task it finished worked for, {@code null} if none finished
	 *         since the previous sample, if it is in a task, or if that component is unknown or
	 *         Covalent's own
	 */
	private Class<?> componentFinishedSince(final WorkerState state, final boolean tracking) {
		Long before = this.previousBusy.get(state.threadId());
		Class<?> component = state.lastTaskComponent();
		return tracking && before != null && !state.inTask() && state.busyNanos() > before && component != null
				&& !ComponentAnnotations.isInternalComponent(component) ? component : null;
	}

	/**
	 * The share of the last interval a thread spent in tasks, from its busy time: tasks far
	 * shorter than the interval count too. {@code -1} when not known: tracking off, or no
	 * previous sample of it.
	 */
	private double busyShare(final WorkerState state, final boolean tracking, final long elapsed) {
		Long before = this.previousBusy.get(state.threadId());
		if (!tracking || before == null || elapsed <= 0) {
			return -1;
		}
		return Math.max(0, Math.min(1, (double) (state.busyNanos() - before) / elapsed));
	}

	private ThreadsSnapshot.Worker worker(final WorkerState state, final ThreadInfo info, final boolean tracking,
			final double busyShare) {
		Thread.State jvmState = info == null ? Thread.State.TERMINATED : info.getThreadState();
		ThreadActivity activity = activity(jvmState, state.inTask(), tracking);
		boolean onLock = activity == ThreadActivity.BLOCKED || activity == ThreadActivity.WAITS;
		long cpu = -1;
		if (this.jvmThreads.isThreadCpuTimeSupported() && this.jvmThreads.isThreadCpuTimeEnabled()) {
			cpu = this.jvmThreads.getThreadCpuTime(state.threadId());
		}
		return new ThreadsSnapshot.Worker(state.threadId(), state.threadName(), activity, jvmState, state.taskNanos(),
				state.taskLabel(), onLock && info != null ? info.getLockName() : null,
				onLock && info != null ? info.getLockOwnerName() : null, cpu, state.taskComponent(), busyShare);
	}

	/**
	 * A thread's activity: blocked on a monitor and running are told by the JVM alone; waiting
	 * is told apart from idle by the pool, which knows whether it is in a task.
	 */
	static ThreadActivity activity(final Thread.State jvmState, final boolean inTask, final boolean tracking) {
		return switch (jvmState) {
			case BLOCKED -> ThreadActivity.BLOCKED;
			case RUNNABLE -> ThreadActivity.RUNS;
			case WAITING, TIMED_WAITING -> !tracking ? ThreadActivity.UNTRACKED
					: inTask ? ThreadActivity.WAITS : ThreadActivity.IDLE;
			default -> ThreadActivity.IDLE;
		};
	}

	/**
	 * How hard a pool is pressed over the last seconds: saturated when it rejected tasks, or when
	 * nearly all its workers stayed busy while tasks queue; under strain when most of them stayed
	 * busy, or when tasks queue.
	 */
	private Contention contention(final WorkerPoolState state, final double occupancy) {
		Deque<PoolSample> history = this.poolHistory.computeIfAbsent(state.name(), ignored -> new ArrayDeque<>());
		history.addLast(new PoolSample(occupancy, state.queuedTasks(), state.rejectedTasks()));
		while (history.size() > CONTENTION_SAMPLES) {
			history.removeFirst();
		}
		double average = history.stream().mapToDouble(PoolSample::occupancy).average().orElse(0);
		boolean rejected = state.rejectedTasks() > history.getFirst().rejected();
		// Over the window as the occupancy: a queue emptied for an instant does not change the verdict.
		boolean queueing = history.stream().filter(sample -> sample.queued() > 0).count() * 2 > history.size();
		return contention(average, queueing, rejected);
	}

	private long rejectedLastMinute(final WorkerPoolState state) {
		Deque<Long> totals = this.rejectedMinute.computeIfAbsent(state.name(), ignored -> new ArrayDeque<>());
		totals.addLast(state.rejectedTasks());
		while (totals.size() > MINUTE_SAMPLES) {
			totals.removeFirst();
		}
		return Math.max(0, state.rejectedTasks() - totals.getFirst());
	}

	/**
	 * @param averageOccupancy the pool's occupancy over the window, from 0 to 1
	 * @param queueing         whether tasks queued in most of the window's samples
	 * @param rejected         whether it rejected tasks within the window
	 * @return how hard the pool is pressed
	 */
	static Contention contention(final double averageOccupancy, final boolean queueing, final boolean rejected) {
		if (rejected || averageOccupancy >= SATURATION && queueing) {
			return Contention.SATURATED;
		}
		if (averageOccupancy >= STRAIN || queueing) {
			return Contention.UNDER_STRAIN;
		}
		return Contention.FLUID;
	}

	private void notePool(final long now, final WorkerPoolState state, final Contention contention) {
		WorkerPoolState previous = this.previousPools.put(state.name(), state);
		Phrase subject = Phrase.of("log.subject.pool", state.name());
		if (previous != null) {
			long rejected = state.rejectedTasks() - previous.rejectedTasks();
			if (rejected > 0) {
				this.log(now, subject, Phrase.of(rejected == 1 ? "log.rejected.one" : "log.rejected.many", rejected,
						state.queuedTasks(), state.queueCapacity()), ThreadsSnapshot.Severity.ERROR);
			}
			// However made - tuned from here or set by the application - a change shows here.
			if (state.targetWorkerCount() != previous.targetWorkerCount()) {
				this.log(now, subject, Phrase.of("log.workers", previous.targetWorkerCount(), state.targetWorkerCount()),
						ThreadsSnapshot.Severity.INFO);
			}
			if (state.queueCapacity() != previous.queueCapacity()) {
				this.log(now, subject, Phrase.of("log.queueCapacity", previous.queueCapacity(), state.queueCapacity()),
						ThreadsSnapshot.Severity.INFO);
			}
		}
		// One entry when a pool saturates, one when it is fluid again: nothing while it wavers between.
		Episode episode = this.episodes.get(state.name());
		if (contention == Contention.FLUID) {
			if (episode != null) {
				this.episodes.remove(state.name());
				if (episode.saturated) {
					this.log(now, subject, Phrase.of("log.fluidAgain", lasted(now - episode.sinceMillis)),
							ThreadsSnapshot.Severity.INFO);
				}
			}
			return;
		}
		if (episode == null) {
			episode = new Episode(now);
			this.episodes.put(state.name(), episode);
		}
		if (contention == Contention.SATURATED && !episode.saturated) {
			episode.saturated = true;
			this.log(now, subject, Phrase.of("log.saturated"), ThreadsSnapshot.Severity.WARNING);
		}
	}

	/** @return how long something lasted, such as {@code 12 s} or {@code 2 min 5 s} */
	static String lasted(final long millis) {
		long seconds = Math.max(1, Math.round(millis / 1000.0));
		return seconds < 60 ? seconds + " s" : seconds / 60 + " min " + seconds % 60 + " s";
	}

	private void noteWorker(final long now, final ThreadsSnapshot.Worker worker) {
		ThreadsSnapshot.Worker before = this.previousWorkers.get(worker.threadId());
		if (before == null) {
			return;
		}
		if (worker.activity() == ThreadActivity.BLOCKED && before.activity() != ThreadActivity.BLOCKED) {
			this.log(now, Phrase.name(worker.name()), worker.lockOwnerName() == null
					? Phrase.of("log.blockedOn", worker.lockName())
					: Phrase.of("log.blockedOnHeld", worker.lockName(), worker.lockOwnerName()),
					ThreadsSnapshot.Severity.WARNING);
		}
		long threshold = TimeUnit.SECONDS.toNanos(Math.max(1,
				(long) SupervisorSettings.getDouble(SupervisorSettings.THREADS_LONG_TASK_SECONDS, 10)));
		LongTask running = this.longTasks.get(worker.threadId());
		if (running != null && (worker.taskNanos() < running.nanos || worker.taskNanos() < threshold)) {
			// Another task, or none: the long one ended.
			this.endLongTask(now, worker.threadId());
			running = null;
		}
		if (worker.taskNanos() >= threshold && running == null && before.taskNanos() >= 0
				&& before.taskNanos() < threshold) {
			long seconds = TimeUnit.NANOSECONDS.toSeconds(threshold);
			Phrase subject = Phrase.name(worker.name());
			ThreadsSnapshot.LogEntry entry = this.log(now, subject, worker.taskLabel() == null
					? Phrase.of("log.longTask", seconds) : Phrase.of("log.longTaskNamed", worker.taskLabel(), seconds),
					ThreadsSnapshot.Severity.WARNING);
			this.longTasks.put(worker.threadId(), new LongTask(subject, worker.taskLabel(), entry, worker.taskNanos()));
		} else if (running != null) {
			// Still running: the entry follows, and turns to an error as the delay grows far past the threshold.
			running.nanos = worker.taskNanos();
			Phrase text = running.label == null ? Phrase.of("log.longTaskLive", lasted(TimeUnit.NANOSECONDS.toMillis(running.nanos)))
					: Phrase.of("log.longTaskLiveNamed", running.label, lasted(TimeUnit.NANOSECONDS.toMillis(running.nanos)));
			boolean far = running.nanos >= 10 * threshold;
			this.replaceEntry(running, text, far ? ThreadsSnapshot.Severity.ERROR : ThreadsSnapshot.Severity.WARNING);
		}
	}

	/** The long task of a thread ended: its entry says how long it lasted. */
	private void endLongTask(final long now, final long threadId) {
		LongTask ended = this.longTasks.remove(threadId);
		if (ended != null) {
			String lasted = lasted(TimeUnit.NANOSECONDS.toMillis(ended.nanos));
			this.replaceEntry(ended, ended.label == null ? Phrase.of("log.longTaskEnded", lasted)
					: Phrase.of("log.longTaskEndedNamed", ended.label, lasted), ThreadsSnapshot.Severity.INFO);
		}
	}

	/** Rewrites a long task's entry where it stands in the log, with the same time. */
	private void replaceEntry(final LongTask task, final Phrase text, final ThreadsSnapshot.Severity severity) {
		ListIterator<ThreadsSnapshot.LogEntry> iterator = this.logEntries.listIterator();
		while (iterator.hasNext()) {
			if (iterator.next() == task.entry) {
				task.entry = new ThreadsSnapshot.LogEntry(task.entry.timeMillis(), task.subject, text, severity);
				iterator.set(task.entry);
				return;
			}
		}
	}

	/** Looks for deadlocks that involve one of the pools' threads. */
	private void findDeadlocks(final long now, final Set<Long> poolThreads) {
		long[] found = this.jvmThreads.findDeadlockedThreads();
		Set<Long> ids = new HashSet<>();
		boolean involved = false;
		if (found != null) {
			for (long id : found) {
				ids.add(id);
				involved |= poolThreads.contains(id);
			}
		}
		if (!involved) {
			this.deadlocks = List.of();
			this.deadlockedIds = Set.of();
			return;
		}
		List<String> names = new ArrayList<>();
		List<Phrase> waits = new ArrayList<>();
		List<Long> threadIds = new ArrayList<>();
		StringBuilder report = new StringBuilder("Covalent deadlock report, ")
				.append(Instant.ofEpochMilli(now)).append("\n");
		for (ThreadInfo info : this.jvmThreads.getThreadInfo(found, true, true)) {
			if (info == null) {
				continue;
			}
			names.add(info.getThreadName());
			threadIds.add(info.getThreadId());
			waits.add(Phrase.of("deadlock.wait", info.getThreadName(), info.getLockName(), info.getLockOwnerName()));
			describe(report, info);
		}
		this.deadlocks = List.of(new ThreadsSnapshot.Deadlock(List.copyOf(names), List.copyOf(waits),
				List.copyOf(threadIds), report.toString()));
		if (!ids.equals(this.deadlockedIds)) {
			this.log(now, Phrase.of("log.subject.deadlock"), Phrase.of("log.deadlock", String.join(", ", names)),
					ThreadsSnapshot.Severity.ERROR);
		}
		this.deadlockedIds = ids;
	}

	/** Describes a deadlocked thread for the report: what it waits for, what it holds, its stack. */
	private static void describe(final StringBuilder report, final ThreadInfo info) {
		report.append('\n').append('"').append(info.getThreadName()).append("\" #").append(info.getThreadId()).append(' ')
				.append(info.getThreadState()).append('\n');
		if (info.getLockName() != null) {
			report.append("  waits for ").append(info.getLockName());
			if (info.getLockOwnerName() != null) {
				report.append(", held by \"").append(info.getLockOwnerName()).append('"');
			}
			report.append('\n');
		}
		for (MonitorInfo monitor : info.getLockedMonitors()) {
			report.append("  holds monitor ").append(monitor).append(" (taken in ")
					.append(monitor.getLockedStackFrame()).append(")\n");
		}
		for (LockInfo lock : info.getLockedSynchronizers()) {
			report.append("  holds ").append(lock).append('\n');
		}
		for (StackTraceElement frame : info.getStackTrace()) {
			report.append("    at ").append(frame).append('\n');
		}
	}

	@Override
	public void interruptThreads(final Set<Long> threadIds) {
		int interrupted = 0;
		for (Thread thread : Thread.getAllStackTraces().keySet()) {
			if (threadIds.contains(thread.threadId())) {
				thread.interrupt();
				interrupted++;
			}
		}
		synchronized (this) {
			this.log(System.currentTimeMillis(), Phrase.of("log.subject.deadlock"), Phrase.of("log.interrupted", interrupted),
					ThreadsSnapshot.Severity.INFO);
		}
	}

	private ThreadsSnapshot.LogEntry log(final long now, final Phrase subject, final Phrase text,
			final ThreadsSnapshot.Severity severity) {
		ThreadsSnapshot.LogEntry entry = new ThreadsSnapshot.LogEntry(now, subject, text, severity);
		this.logEntries.addFirst(entry);
		while (this.logEntries.size() > LOG) {
			this.logEntries.removeLast();
		}
		return entry;
	}
}
