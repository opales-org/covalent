/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.view.impl;

import java.util.concurrent.TimeUnit;

import org.opales.covalent.supervision.supervisionhmi.SupervisorSettings;
import org.opales.covalent.supervision.threads.model.ThreadActivity;
import org.opales.covalent.supervision.threads.model.ThreadsSnapshot;

/**
 * How one thread reads at a glance: its dot. The color says what kind of state it is in, the
 * blinking says for how long. Delays are multiples of the long task threshold: the dot
 * blinks past {@link #BLINK_FACTOR} times it, and a task running past {@link #CRITICAL_FACTOR}
 * times it turns red, as a deadlock does.
 *
 * @param state      the kind of state, hence the color
 * @param blink      whether the dot blinks
 * @param taskNanos  how long the thread's current task has run, {@code -1} when unknown
 * @param deadlocked whether the thread is in a deadlock
 * @param stuck      whether it is deadlocked, or has been busy in a task for more than the threshold
 * @param worker     the thread
 * @param recent     its share of the last seconds spent in tasks, from 0 to 1
 */
record ThreadDot(State state, boolean blink, long taskNanos, boolean deadlocked, boolean stuck,
		ThreadsSnapshot.Worker worker, double recent) {

	/** Past this many times the threshold, a dot blinks. */
	static final int BLINK_FACTOR = 3;

	/** Past this many times the threshold, a task's dot is red and blinking. */
	static final int CRITICAL_FACTOR = 10;

	/** The kinds of state a dot shows. */
	enum State {
		/** Nothing to report. */
		IDLE("idle"),
		/** Running a task, for no longer than the threshold. */
		OK("ok"),
		/** Waiting in a task. */
		WAITS("waits"),
		/** Running past the threshold, without ever waiting: an endless loop, perhaps. */
		LONG_RUN("long"),
		/** Blocked on a monitor, deadlocked, or past the critical delay. */
		BAD("bad");

		private final String style;

		State(final String style) {
			this.style = style;
		}

		/** @return the suffix of its style class */
		String style() {
			return this.style;
		}
	}

	/** @return the long task threshold, in nanoseconds */
	static long thresholdNanos() {
		return TimeUnit.SECONDS.toNanos(Math.max(1,
				(long) SupervisorSettings.getDouble(SupervisorSettings.THREADS_LONG_TASK_SECONDS, 10)));
	}

	/**
	 * @param worker     a thread
	 * @param deadlocked whether it is in a deadlock
	 * @param threshold  the long task threshold, in nanoseconds
	 * @return how it reads
	 */
	static ThreadDot of(final ThreadsSnapshot.Worker worker, final boolean deadlocked, final long threshold) {
		return of(worker, deadlocked, threshold, 0);
	}

	/**
	 * Blocked or waiting shows at once; otherwise a thread that worked at all over the last
	 * seconds reads as running, however idle at the sample, so that short tasks show.
	 *
	 * @param worker     a thread
	 * @param deadlocked whether it is in a deadlock
	 * @param threshold  the long task threshold, in nanoseconds
	 * @param recent     its share of the last seconds spent in tasks, from 0 to 1
	 * @return how it reads
	 */
	static ThreadDot of(final ThreadsSnapshot.Worker worker, final boolean deadlocked, final long threshold,
			final double recent) {
		long duration = worker.taskNanos();
		if (deadlocked) {
			return new ThreadDot(State.BAD, true, duration, true, true, worker, recent);
		}
		ThreadActivity activity = worker.activity();
		if (!activity.isBusy()) {
			return new ThreadDot(recent > 0 ? State.OK : State.IDLE, false, duration, false, false, worker, recent);
		}
		boolean stuck = duration >= threshold;
		boolean blink = duration >= BLINK_FACTOR * threshold;
		if (duration >= CRITICAL_FACTOR * threshold) {
			return new ThreadDot(State.BAD, true, duration, false, true, worker, recent);
		}
		State state = switch (activity) {
			case BLOCKED -> State.BAD;
			case WAITS -> State.WAITS;
			default -> stuck ? State.LONG_RUN : State.OK;
		};
		return new ThreadDot(state, blink, duration, false, stuck, worker, recent);
	}

	/** @return whether it is red, and blinking: a deadlock, or a task far past the threshold */
	boolean critical() {
		return this.state == State.BAD && this.blink;
	}

	/** @return how much it matters, the worst first once sorted by decreasing severity */
	int severity() {
		if (this.deadlocked) {
			return 6;
		}
		if (this.critical()) {
			return 5;
		}
		if (this.blink) {
			return 4;
		}
		if (this.state == State.BAD || this.state == State.LONG_RUN) {
			return 3;
		}
		return this.state == State.IDLE ? 0 : 1;
	}
}
