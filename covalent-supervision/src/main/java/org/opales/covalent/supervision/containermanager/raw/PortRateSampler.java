/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.containermanager.raw;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import org.opales.covalent.supervision.SupervisionThreads;
import org.opales.covalent.supervision.observed.ObservedPortKind;
import org.opales.covalent.supervision.observed.ObservedPortRate;
import org.opales.covalent.supervision.observed.ObservedPortRateEvents;

/**
 * Counts what goes through each port - calls started, failures, durations - and reports it
 * once per period, then starts counting again. Counting a call is a map lookup and an
 * increment on the calling thread; a finished call also keeps its duration, up to a bound
 * per period.
 */
final class PortRateSampler {

	/** The sampling period. */
	static final long PERIOD_MILLIS = 1_000;

	/** How many durations a port keeps per period for its percentile; later ones are dropped. */
	private static final int MAX_DURATIONS = 512;

	private final ObservedPortRateEvents sink;

	private final Map<PortKey, PortCounts> counts = new ConcurrentHashMap<>();

	private ScheduledExecutorService scheduler;

	/**
	 * @param sink where each period's counts are reported
	 */
	PortRateSampler(final ObservedPortRateEvents sink) {
		this.sink = sink;
	}

	void count(final Class<?> componentType, final Class<?> portType, final ObservedPortKind portKind,
			final boolean receiving) {
		PortCounts portCounts = this.countsOf(componentType, portType, portKind, receiving);
		if (portCounts != null) {
			portCounts.calls.increment();
		}
	}

	void finished(final Class<?> componentType, final Class<?> portType, final ObservedPortKind portKind,
			final boolean receiving, final long durationNanos, final Throwable failure, final String methodName) {
		PortCounts portCounts = this.countsOf(componentType, portType, portKind, receiving);
		if (portCounts == null) {
			return;
		}
		synchronized (portCounts) {
			if (durationNanos >= 0 && portCounts.durationCount < MAX_DURATIONS) {
				portCounts.durations[portCounts.durationCount++] = durationNanos;
			}
			if (failure != null) {
				portCounts.failures++;
				portCounts.lastFailedMethod = methodName;
			}
		}
	}

	private PortCounts countsOf(final Class<?> componentType, final Class<?> portType, final ObservedPortKind portKind,
			final boolean receiving) {
		if (componentType == null || portType == null) {
			return null;
		}
		return this.counts.computeIfAbsent(new PortKey(componentType, portType, portKind, receiving),
				ignored -> new PortCounts());
	}

	/** Starts reporting, from an empty count. */
	synchronized void start() {
		this.counts.clear();
		if (this.scheduler == null) {
			this.scheduler = Executors.newSingleThreadScheduledExecutor(SupervisionThreads.factory("port-rates"));
			this.scheduler.scheduleAtFixedRate(this::report, PERIOD_MILLIS, PERIOD_MILLIS, TimeUnit.MILLISECONDS);
		}
	}

	/** Stops reporting and forgets the counts. */
	synchronized void stop() {
		if (this.scheduler != null) {
			this.scheduler.shutdownNow();
			this.scheduler = null;
		}
		this.counts.clear();
	}

	private void report() {
		List<ObservedPortRate> rates = new ArrayList<>();
		for (Map.Entry<PortKey, PortCounts> entry : this.counts.entrySet()) {
			PortCounts portCounts = entry.getValue();
			int calls = (int) portCounts.calls.sumThenReset();
			int failures;
			long p95;
			String lastFailedMethod;
			synchronized (portCounts) {
				failures = portCounts.failures;
				lastFailedMethod = portCounts.lastFailedMethod;
				p95 = percentile95(portCounts.durations, portCounts.durationCount);
				portCounts.failures = 0;
				portCounts.lastFailedMethod = null;
				portCounts.durationCount = 0;
			}
			if (calls > 0 || failures > 0 || p95 >= 0) {
				PortKey key = entry.getKey();
				rates.add(new ObservedPortRate(key.componentType(), key.portType(), key.portKind(), key.receiving(),
						calls, failures, p95, lastFailedMethod));
			}
		}
		this.sink.onPortRatesSampled(System.currentTimeMillis(), PERIOD_MILLIS, rates);
	}

	private static long percentile95(final long[] durations, final int count) {
		if (count == 0) {
			return -1;
		}
		long[] sorted = Arrays.copyOf(durations, count);
		Arrays.sort(sorted);
		return sorted[Math.min(count - 1, (int) Math.ceil(count * 0.95) - 1)];
	}

	private record PortKey(Class<?> componentType, Class<?> portType, ObservedPortKind portKind, boolean receiving) {
	}

	private static final class PortCounts {
		private final LongAdder calls = new LongAdder();
		private final long[] durations = new long[MAX_DURATIONS];
		private int durationCount;
		private int failures;
		private String lastFailedMethod;
	}
}
