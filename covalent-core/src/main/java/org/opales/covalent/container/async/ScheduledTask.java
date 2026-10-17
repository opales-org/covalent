/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.async;

/**
 * Handle on a periodic task scheduled through
 * {@link AsyncServices#scheduleWithFixedDelay(java.time.Duration, java.time.Duration, Runnable)}.
 *
 * <p>A periodic task stops for good, never to resume, in three cases: its
 * owner calls {@link #cancel()}; the component that scheduled it is
 * passivated (or fails, or is removed); the container stops. After the
 * second and third cases, {@link #isCancelled()} answers {@code true} and a
 * component reactivated later must schedule its task again, typically in
 * its activation method.
 */
public interface ScheduledTask {

	/**
	 * Stops the task: no further run starts. A run already in progress is
	 * not interrupted and finishes normally. Calling this more than once, or
	 * after the task stopped by itself, has no effect.
	 */
	void cancel();

	/**
	 * Tells whether the task was stopped, by {@link #cancel()}, by the
	 * passivation of the component that scheduled it, or by the container
	 * stopping.
	 *
	 * @return {@code true} if no further run will start
	 */
	boolean isCancelled();
}
