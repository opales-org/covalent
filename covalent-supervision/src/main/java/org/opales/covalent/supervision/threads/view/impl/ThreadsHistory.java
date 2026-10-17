/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.view.impl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opales.covalent.container.workers.DurationHistogram;
import org.opales.covalent.container.workers.WorkerPoolKind;
import org.opales.covalent.supervision.threads.model.ThreadActivity;
import org.opales.covalent.supervision.threads.model.ThreadsSnapshot;

/**
 * The last quarter of an hour of samples, kept in rings: each thread's activity and CPU time,
 * each pool's occupancy, queue and rejections, and once a second each pool's histograms. Sample
 * {@code n} sits at {@code n % CAPACITY}; only the last {@link #CAPACITY} samples are kept.
 */
final class ThreadsHistory {

	/** Fifteen minutes of samples, four per second. */
	static final int CAPACITY = 3600;

	/** The samples per second. */
	static final int PER_SECOND = 4;

	/** No activity recorded: the thread did not exist. */
	static final byte ABSENT = -1;

	private static final ThreadActivity[] ACTIVITIES = ThreadActivity.values();

	/** The name the calling threads are kept under, as if they were a pool. */
	static final String CALLERS = "calling threads";

	private final long[] times = new long[CAPACITY];

	/** The calls of the calling threads per second. */
	private final float[] callRates = new float[CAPACITY];

	private long count;

	private final Map<Long, Track> tracks = new HashMap<>();

	private final Map<String, PoolTrack> pools = new HashMap<>();

	/** The threads stuck now, by thread id: their episode is open. */
	private final Map<Long, Stuck> openStuck = new HashMap<>();

	/** The stretches during which a thread was stuck, oldest first, those still in the window only. */
	private final List<Stuck> stuckEpisodes = new ArrayList<>();

	/** A stretch during which a thread was stuck: busy in a task past the long task threshold, or deadlocked. */
	static final class Stuck {

		final long threadId;

		final String threadName;

		final long startSample;

		/** The last sample it was stuck, {@code -1} while it is. */
		long endSample = -1;

		/** The worst it got. */
		ThreadDot.State state;

		private int severity = -1;

		Stuck(final long threadId, final String threadName, final long startSample) {
			this.threadId = threadId;
			this.threadName = threadName;
			this.startSample = startSample;
		}

		/** @return the name of the palette color that draws it: red once serious, else its state's */
		String color() {
			return this.severity >= 5 ? "blocked" : this.state == ThreadDot.State.LONG_RUN ? "long"
					: this.state == ThreadDot.State.WAITS ? "waits" : "blocked";
		}
	}

	/** One thread's samples. */
	static final class Track {

		final long threadId;

		String name;

		String poolName;

		/** Whether it is a calling thread rather than a pool's worker. */
		boolean caller;

		/** Per sample: the components its tasks or calls worked for in that interval, {@code null} if none. */
		final List<?>[] components = new List<?>[CAPACITY];

		/** The component its tasks or calls worked for last, {@code null} before any. */
		Class<?> lastComponent;

		final byte[] activities = new byte[CAPACITY];

		final long[] cpuNanos = new long[CAPACITY];

		/** The share of each interval spent in tasks, {@code -1} when not known. */
		final float[] busy = new float[CAPACITY];

		long lastSample;

		/** What it did lately, newest first: its task changes and state changes. */
		final Deque<Activity> recent = new ArrayDeque<>();

		ThreadsSnapshot.Worker latest;

		Track(final long threadId) {
			this.threadId = threadId;
			Arrays.fill(this.activities, ABSENT);
		}
	}

	/**
	 * A component a thread worked for.
	 *
	 * @param component the component
	 * @param share     the share of the interval the thread spent working for it, from 0 to 1
	 * @param sample    the last sample in which it worked for it
	 */
	record Work(Class<?> component, double share, long sample) {
	}

	/**
	 * The component a thread worked for most, as a sampling profiler tells it: the samples catch
	 * its tasks in proportion to the time they take, and its measured time in tasks is shared out
	 * in that proportion.
	 *
	 * @param track   a thread
	 * @param last    the last sample looked at
	 * @param samples how many samples back to look
	 * @return the component caught most often over those samples, the latest on a tie, with its
	 *         estimated share of the thread's time; {@code null} if none was caught
	 */
	Work dominantWork(final Track track, final long last, final int samples) {
		return this.dominantWork(track, last, samples, null);
	}

	/**
	 * How much more often another component must be caught than the one shown, for the pill to
	 * change: two components that share a pool about equally would otherwise swap at every sample.
	 */
	static final double DOMINANCE_MARGIN = 1.3;

	/**
	 * Like {@link #dominantWork(Track, long, int)}, but that keeps the component already shown
	 * unless another is caught clearly more often (by {@link #DOMINANCE_MARGIN}).
	 *
	 * @param track     a thread
	 * @param last      the last sample looked at
	 * @param samples   how many samples back to look
	 * @param incumbent the component shown now, {@code null} if none
	 * @return the component to show, with its estimated share of the thread's time; {@code null}
	 *         if none was caught
	 */
	Work dominantWork(final Track track, final long last, final int samples, final Class<?> incumbent) {
		Map<Class<?>, long[]> caught = new HashMap<>();
		int counted = 0;
		int caughtTotal = 0;
		double busy = 0;
		for (long sample = last; sample > last - samples && sample >= this.oldest(); sample--) {
			counted++;
			int slot = (int) (sample % CAPACITY);
			busy += Math.max(0, track.busy[slot]);
			List<?> components = track.components[slot];
			if (components == null) {
				continue;
			}
			final long at = sample;
			for (Object component : components) {
				caughtTotal++;
				caught.computeIfAbsent((Class<?>) component, key -> new long[] { 0, at })[0]++;
			}
		}
		Map.Entry<Class<?>, long[]> most = null;
		for (Map.Entry<Class<?>, long[]> entry : caught.entrySet()) {
			if (most == null || entry.getValue()[0] > most.getValue()[0]
					|| entry.getValue()[0] == most.getValue()[0] && entry.getValue()[1] > most.getValue()[1]) {
				most = entry;
			}
		}
		if (most == null) {
			return null;
		}
		long[] kept = incumbent == null ? null : caught.get(incumbent);
		if (kept != null && most.getKey() != incumbent && kept[0] * DOMINANCE_MARGIN >= most.getValue()[0]) {
			most = Map.entry(incumbent, kept);
		}
		double share = busy / counted * most.getValue()[0] / caughtTotal;
		return new Work(most.getKey(), share, most.getValue()[1]);
	}

	/**
	 * @param track a thread
	 * @param last  the last sample looked at
	 * @return the last component the thread worked for up to that sample, and when, {@code null}
	 *         if none in the samples kept
	 */
	Work lastWork(final Track track, final long last) {
		for (long sample = last; sample >= this.oldest(); sample--) {
			List<?> components = track.components[(int) (sample % CAPACITY)];
			if (components != null && !components.isEmpty()) {
				return new Work((Class<?>) components.get(components.size() - 1), 0, sample);
			}
		}
		return null;
	}

	/** One line of a thread's recent activity. */
	record Activity(long timeMillis, String text) {
	}

	/** One pool's samples. */
	static final class PoolTrack {

		final String name;

		WorkerPoolKind kind;

		final float[] occupancy = new float[CAPACITY];

		final int[] queued = new int[CAPACITY];

		final long[] rejected = new long[CAPACITY];

		final int[] workers = new int[CAPACITY];

		/** The tracked tasks completed per second. */
		final float[] throughput = new float[CAPACITY];

		/**
		 * Per component, by simple name - {@link #UNKNOWN_COMPONENT} for the tasks of none known:
		 * how many workers worked for it on average over each interval.
		 */
		final Map<String, float[]> load = new HashMap<>();

		/** Once a second: the cumulative histograms, oldest first. */
		final Deque<Histograms> histograms = new ArrayDeque<>();

		long lastSample;

		PoolTrack(final String name) {
			this.name = name;
		}
	}

	/** A pool's cumulative histograms at one sample. */
	record Histograms(long sample, DurationHistogram queueWaits, DurationHistogram durations) {
	}

	/** @return how many samples were recorded in all */
	long count() {
		return this.count;
	}

	/** @return the oldest sample still kept */
	long oldest() {
		return Math.max(0, this.count - CAPACITY);
	}

	long timeOf(final long sample) {
		return this.times[(int) (sample % CAPACITY)];
	}

	Map<Long, Track> tracks() {
		return this.tracks;
	}

	Track track(final long threadId) {
		return this.tracks.get(threadId);
	}

	PoolTrack pool(final String name) {
		return this.pools.get(name);
	}

	Map<String, PoolTrack> pools() {
		return this.pools;
	}

	/** @return the stretches during which a thread was stuck, oldest first */
	List<Stuck> stuckEpisodes() {
		return this.stuckEpisodes;
	}

	/** Forgets everything, as when another container is observed. */
	void clear() {
		this.count = 0;
		this.tracks.clear();
		this.pools.clear();
		this.openStuck.clear();
		this.stuckEpisodes.clear();
	}

	/** Records a sample. */
	void add(final ThreadsSnapshot snapshot) {
		long sample = this.count;
		int slot = (int) (sample % CAPACITY);
		this.times[slot] = snapshot.timeMillis();
		for (Track track : this.tracks.values()) {
			track.activities[slot] = ABSENT;
			track.cpuNanos[slot] = -1;
			track.busy[slot] = -1;
			track.components[slot] = null;
		}
		for (ThreadsSnapshot.Pool pool : snapshot.pools()) {
			PoolTrack poolTrack = this.pools.computeIfAbsent(pool.name(), PoolTrack::new);
			poolTrack.kind = pool.kind();
			poolTrack.occupancy[slot] = (float) pool.occupancy();
			poolTrack.queued[slot] = pool.queuedTasks();
			poolTrack.rejected[slot] = pool.rejectedTasks();
			poolTrack.workers[slot] = pool.workers().size();
			poolTrack.throughput[slot] = (float) pool.tasksPerSecond();
			for (float[] ring : poolTrack.load.values()) {
				ring[slot] = 0;
			}
			pool.componentLoad().forEach((component, load) -> poolTrack.load
					.computeIfAbsent(component.getSimpleName(), name -> new float[CAPACITY])[slot] += load.floatValue());
			if (pool.otherLoad() > 0) {
				poolTrack.load.computeIfAbsent(UNKNOWN_COMPONENT, name -> new float[CAPACITY])[slot] =
						(float) pool.otherLoad();
			}
			poolTrack.lastSample = sample;
			if (sample % PER_SECOND == 0) {
				poolTrack.histograms.addLast(new Histograms(sample, pool.queueWaits(), pool.durations()));
				while (poolTrack.histograms.size() > CAPACITY / PER_SECOND) {
					poolTrack.histograms.removeFirst();
				}
			}
			for (ThreadsSnapshot.Worker worker : pool.workers()) {
				this.record(worker, pool.name(), false, slot, sample, snapshot);
			}
		}
		for (ThreadsSnapshot.Worker worker : snapshot.callers().threads()) {
			this.record(worker, CALLERS, true, slot, sample, snapshot);
		}
		this.callRates[slot] = (float) snapshot.callers().callsPerSecond();
		this.noteStuck(snapshot, sample);
		this.count++;
		long oldest = this.oldest();
		this.stuckEpisodes.removeIf(episode -> episode.endSample >= 0 && episode.endSample < oldest);
		this.tracks.values().removeIf(track -> track.lastSample < oldest);
		this.pools.values().removeIf(pool -> pool.lastSample < oldest);
	}

	/** Opens the episodes of the threads that just got stuck, and closes those of the threads that no longer are. */
	private void noteStuck(final ThreadsSnapshot snapshot, final long sample) {
		Set<String> deadlocked = new HashSet<>();
		snapshot.deadlocks().forEach(deadlock -> deadlocked.addAll(deadlock.threadNames()));
		long threshold = ThreadDot.thresholdNanos();
		Set<Long> stuckNow = new HashSet<>();
		List<ThreadsSnapshot.Worker> workers = new ArrayList<>();
		snapshot.pools().forEach(pool -> workers.addAll(pool.workers()));
		workers.addAll(snapshot.callers().threads());
		for (ThreadsSnapshot.Worker worker : workers) {
			ThreadDot dot = ThreadDot.of(worker, deadlocked.contains(worker.name()), threshold);
			if (!dot.stuck()) {
				continue;
			}
			stuckNow.add(worker.threadId());
			Stuck episode = this.openStuck.get(worker.threadId());
			if (episode == null) {
				episode = new Stuck(worker.threadId(), worker.name(), sample);
				this.openStuck.put(worker.threadId(), episode);
				this.stuckEpisodes.add(episode);
			}
			if (dot.severity() > episode.severity) {
				episode.severity = dot.severity();
				episode.state = dot.state();
			}
		}
		for (Iterator<Stuck> iterator = this.openStuck.values().iterator(); iterator.hasNext();) {
			Stuck episode = iterator.next();
			if (!stuckNow.contains(episode.threadId)) {
				episode.endSample = Math.max(episode.startSample, sample - 1);
				iterator.remove();
			}
		}
	}

	private void record(final ThreadsSnapshot.Worker worker, final String poolName, final boolean caller, final int slot,
			final long sample, final ThreadsSnapshot snapshot) {
		Track track = this.tracks.computeIfAbsent(worker.threadId(), Track::new);
		this.noteActivity(track, worker, snapshot.timeMillis());
		track.name = worker.name();
		track.poolName = poolName;
		track.caller = caller;
		track.activities[slot] = (byte) worker.activity().ordinal();
		track.cpuNanos[slot] = worker.cpuNanos();
		track.busy[slot] = (float) worker.busyShare();
		track.lastSample = sample;
		track.latest = worker;
		List<Class<?>> components = snapshot.components().get(worker.threadId());
		if (components != null && !components.isEmpty()) {
			track.components[slot] = components;
			track.lastComponent = components.get(components.size() - 1);
		}
	}

	/**
	 * The key, in {@link #componentShares}, of the workers' time in tasks of no component known: a
	 * task submitted outside a component, or by Covalent itself.
	 */
	static final String UNKNOWN_COMPONENT = "";

	/**
	 * @param first the first sample
	 * @param last  the last sample
	 * @return for each component, by simple name, and each kind of pool it worked in: the share of
	 *         that kind's workers that worked for it at each sample from first to last, from 0 to 1
	 *         - the dedicated pools taken together, as the occupancy chart takes them
	 */
	Map<String, Map<WorkerPoolKind, float[]>> componentShares(final long first, final long last) {
		int size = (int) Math.max(0, last - first + 1);
		long start = Math.max(first, this.oldest());
		long end = Math.min(last, this.count - 1);
		Map<WorkerPoolKind, int[]> workers = new EnumMap<>(WorkerPoolKind.class);
		Map<String, Map<WorkerPoolKind, float[]>> shares = new HashMap<>();
		for (PoolTrack pool : this.pools.values()) {
			if (pool.kind == null) {
				continue;
			}
			int[] kindWorkers = workers.computeIfAbsent(pool.kind, kind -> new int[size]);
			for (long sample = start; sample <= Math.min(end, pool.lastSample); sample++) {
				kindWorkers[(int) (sample - first)] += pool.workers[(int) (sample % CAPACITY)];
			}
			pool.load.forEach((component, ring) -> {
				float[] values = null;
				for (long sample = start; sample <= Math.min(end, pool.lastSample); sample++) {
					float value = ring[(int) (sample % CAPACITY)];
					if (value > 0) {
						if (values == null) {
							values = shares.computeIfAbsent(component, name -> new EnumMap<>(WorkerPoolKind.class))
									.computeIfAbsent(pool.kind, kind -> new float[size]);
						}
						values[(int) (sample - first)] += value;
					}
				}
			});
		}
		// The workers working for the component, over the kind's workers.
		shares.values().forEach(byKind -> byKind.forEach((kind, values) -> {
			int[] kindWorkers = workers.get(kind);
			for (int index = 0; index < size; index++) {
				values[index] = kindWorkers[index] <= 0 ? 0 : Math.min(1, values[index] / kindWorkers[index]);
			}
		}));
		return shares;
	}

	/**
	 * @param component a component's simple name, or {@link #UNKNOWN_COMPONENT}
	 * @param sample    the sample
	 * @return per kind of pool, the share of its workers that worked for that component then
	 */
	Map<WorkerPoolKind, Double> componentSharesAt(final String component, final long sample) {
		Map<WorkerPoolKind, Double> at = new EnumMap<>(WorkerPoolKind.class);
		if (sample < this.oldest() || sample >= this.count) {
			return at;
		}
		Map<WorkerPoolKind, float[]> shares = this.componentShares(sample, sample).get(component);
		if (shares != null) {
			shares.forEach((kind, values) -> {
				if (values[0] > 0) {
					at.put(kind, (double) values[0]);
				}
			});
		}
		return at;
	}

	/** @return the calls of the calling threads per second, averaged over the second up to that sample */
	double callRate(final long sample) {
		double sum = 0;
		int counted = 0;
		for (long earlier = sample; earlier > sample - PER_SECOND && earlier >= this.oldest(); earlier--) {
			sum += this.callRates[(int) (earlier % CAPACITY)];
			counted++;
		}
		return counted == 0 ? 0 : sum / counted;
	}

	private static String taskName(final String label) {
		return label == null ? ThreadsTexts.get("task.a") : label;
	}

	/** Notes what changed for a thread since its last sample. */
	private void noteActivity(final Track track, final ThreadsSnapshot.Worker worker, final long now) {
		ThreadsSnapshot.Worker before = track.latest;
		String text = null;
		if (before == null) {
			text = worker.taskNanos() < 0 ? ThreadsTexts.get("activity.started")
					: ThreadsTexts.get("activity.startedIn", taskName(worker.taskLabel()));
		} else if (worker.activity() == ThreadActivity.BLOCKED && before.activity() != ThreadActivity.BLOCKED) {
			text = worker.lockOwnerName() == null ? ThreadsTexts.get("lock.blocked", worker.lockName())
					: ThreadsTexts.get("lock.blockedHeld", worker.lockName(), worker.lockOwnerName());
		} else if (worker.taskNanos() >= 0 && (before.taskNanos() < 0 || worker.taskNanos() < before.taskNanos())) {
			text = ThreadsTexts.get("activity.took", taskName(worker.taskLabel()));
		} else if (worker.taskNanos() < 0 && before.taskNanos() >= 0) {
			text = ThreadsTexts.get("activity.finished", before.taskLabel() == null
					? ThreadsTexts.get("activity.itsTask") : before.taskLabel(), ThreadsFormat.duration(before.taskNanos()));
		}
		if (text != null) {
			track.recent.addFirst(new Activity(now, text));
			while (track.recent.size() > 12) {
				track.recent.removeLast();
			}
		}
	}

	/**
	 * @return the components a thread's tasks or calls worked for between two samples, both
	 *         included, in the order met
	 */
	Set<Class<?>> components(final Track track, final long from, final long to) {
		Set<Class<?>> components = new LinkedHashSet<>();
		for (long sample = Math.max(from, this.oldest()); sample <= to && sample < this.count; sample++) {
			List<?> inSample = track.components[(int) (sample % CAPACITY)];
			if (inSample != null) {
				inSample.forEach(component -> components.add((Class<?>) component));
			}
		}
		return components;
	}

	/** @return a pool's average occupancy over its last samples, from 0 to 1 */
	double occupancy(final PoolTrack pool, final int samples) {
		double sum = 0;
		int counted = 0;
		for (long sample = pool.lastSample; sample > pool.lastSample - samples && sample >= this.oldest(); sample--) {
			sum += pool.occupancy[(int) (sample % CAPACITY)];
			counted++;
		}
		return counted == 0 ? 0 : sum / counted;
	}

	/** @return a thread's share of that interval spent in tasks, {@code -1} when not known */
	float busy(final Track track, final long sample) {
		return track.busy[(int) (sample % CAPACITY)];
	}

	/**
	 * @param threadId a thread
	 * @param samples  how many of its last samples to look at
	 * @return its share of that time spent in tasks, from 0 to 1, however short they were: 0
	 *         for a thread not seen
	 */
	double recentBusy(final long threadId, final int samples) {
		Track track = this.tracks.get(threadId);
		if (track == null) {
			return 0;
		}
		double sum = 0;
		int counted = 0;
		for (long sample = track.lastSample; sample > track.lastSample - samples && sample >= this.oldest(); sample--) {
			ThreadActivity activity = this.activity(track, sample);
			if (activity == null) {
				continue;
			}
			float busy = this.busy(track, sample);
			sum += busy >= 0 ? busy : activity == ThreadActivity.RUNS ? 1 : 0;
			counted++;
		}
		return counted == 0 ? 0 : sum / counted;
	}

	/** @return a pool's tracked tasks per second, averaged over the second up to that sample */
	double throughput(final PoolTrack pool, final long sample) {
		double sum = 0;
		int counted = 0;
		for (long earlier = sample; earlier > sample - PER_SECOND && earlier >= this.oldest(); earlier--) {
			sum += pool.throughput[(int) (earlier % CAPACITY)];
			counted++;
		}
		return counted == 0 ? 0 : sum / counted;
	}

	ThreadActivity activity(final Track track, final long sample) {
		byte value = track.activities[(int) (sample % CAPACITY)];
		return value < 0 ? null : ACTIVITIES[value];
	}

	/**
	 * How long a thread has been in its current activity, in samples, as far back as kept.
	 */
	int runLength(final Track track, final long last) {
		ThreadActivity current = this.activity(track, last);
		int length = 0;
		for (long sample = last; sample >= this.oldest() && this.activity(track, sample) == current; sample--) {
			length++;
		}
		return length;
	}

	/**
	 * A pool's histograms counted between two samples, from those kept once a second.
	 *
	 * @return the queue waits then the durations, empty when not enough was kept
	 */
	List<DurationHistogram> histogramsBetween(final PoolTrack pool, final long from) {
		if (pool.histograms.isEmpty()) {
			return List.of(DurationHistogram.EMPTY, DurationHistogram.EMPTY);
		}
		Histograms last = pool.histograms.getLast();
		Histograms first = pool.histograms.getFirst();
		Iterator<Histograms> iterator = pool.histograms.iterator();
		while (iterator.hasNext()) {
			Histograms candidate = iterator.next();
			if (candidate.sample() >= from) {
				first = candidate;
				break;
			}
		}
		return List.of(last.queueWaits().since(first.queueWaits()), last.durations().since(first.durations()));
	}
}
