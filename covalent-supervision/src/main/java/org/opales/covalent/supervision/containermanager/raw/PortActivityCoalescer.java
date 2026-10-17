/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.containermanager.raw;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.opales.covalent.supervision.SupervisionThreads;
import org.opales.covalent.supervision.observed.ObservedPortActivityEvents;
import org.opales.covalent.supervision.observed.ObservedPortKind;

/**
 * Turns the observed container's per-call start/finish notifications into
 * the coalesced {@link ObservedPortActivityEvents}: per component and port
 * kind, it counts the calls in flight, reports the ports active on the
 * first one, and inactive only once none has been in flight for
 * {@link #LINGER_MILLIS}. A call starting meanwhile cancels that pending
 * report, so a steady stream of calls keeps the ports active with no
 * notification at all.
 * <p>
 * Called on the observed calls' own threads, concurrently: each component
 * and port kind has its own lock.
 */
final class PortActivityCoalescer {

	/** How long ports stay reported active after their last call finished. */
	static final long LINGER_MILLIS = 150;

	private final ObservedPortActivityEvents sink;

	private final Map<ActivityKey, ActivityState> states = new ConcurrentHashMap<>();

	private ScheduledExecutorService scheduler;

	/**
	 * @param sink where activity changes are reported
	 */
	PortActivityCoalescer(final ObservedPortActivityEvents sink) {
		this.sink = sink;
	}

	void callStarted(final Class<?> componentType, final Class<?> portType, final ObservedPortKind portKind) {
		if (componentType == null) {
			return;
		}
		ActivityKey key = new ActivityKey(componentType, portType, portKind);
		ActivityState state = this.states.computeIfAbsent(key, ignored -> new ActivityState());
		synchronized (state) {
			state.inFlight++;
			if (state.pendingInactive != null) {
				state.pendingInactive.cancel(false);
				state.pendingInactive = null;
			}
			if (!state.reportedActive) {
				state.reportedActive = true;
				this.sink.onPortActivityChanged(componentType, portType, portKind, true);
			}
		}
	}

	void callFinished(final Class<?> componentType, final Class<?> portType, final ObservedPortKind portKind) {
		if (componentType == null) {
			return;
		}
		ActivityKey key = new ActivityKey(componentType, portType, portKind);
		ActivityState state = this.states.get(key);
		if (state == null) {
			return;
		}
		synchronized (state) {
			if (state.inFlight > 0) {
				state.inFlight--;
			}
			if (state.inFlight == 0 && state.reportedActive && state.pendingInactive == null) {
				state.pendingInactive = this.scheduler().schedule(() -> this.reportInactive(key, state),
						LINGER_MILLIS, TimeUnit.MILLISECONDS);
			}
		}
	}

	private void reportInactive(final ActivityKey key, final ActivityState state) {
		synchronized (state) {
			state.pendingInactive = null;
			if (state.inFlight == 0 && state.reportedActive) {
				state.reportedActive = false;
				this.sink.onPortActivityChanged(key.componentType(), key.portType(), key.portKind(), false);
			}
		}
	}

	/** Forgets every count and pending report, without notifying: the observed container is changing. */
	void reset() {
		for (ActivityState state : this.states.values()) {
			synchronized (state) {
				if (state.pendingInactive != null) {
					state.pendingInactive.cancel(false);
				}
			}
		}
		this.states.clear();
	}

	/** Stops the timer thread, if it was ever started. */
	void shutdown() {
		this.reset();
		synchronized (this) {
			if (this.scheduler != null) {
				this.scheduler.shutdownNow();
				this.scheduler = null;
			}
		}
	}

	private synchronized ScheduledExecutorService scheduler() {
		if (this.scheduler == null) {
			this.scheduler = Executors.newSingleThreadScheduledExecutor(SupervisionThreads.factory("port-activity"));
		}
		return this.scheduler;
	}

	private record ActivityKey(Class<?> componentType, Class<?> portType, ObservedPortKind portKind) {
	}

	private static final class ActivityState {
		private int inFlight;
		private boolean reportedActive;
		private ScheduledFuture<?> pendingInactive;
	}
}
