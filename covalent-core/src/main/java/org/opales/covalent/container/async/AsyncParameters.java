/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.async;

import java.time.Duration;

import org.opales.covalent.container.component.annotations.Parameters;

/**
 * Configures the resource limits and reclaim policies of Covalent's
 * asynchronous worker pools (see {@link AsyncServices} for how tasks are
 * routed onto them and for the general/dedicated pool distinction used
 * throughout this interface).
 *
 * <p><b>When a change takes effect.</b> The general worker count and the queue
 * capacity apply immediately, to the running pools: added workers start at
 * once, surplus workers finish their current task before terminating, and a
 * reduced capacity keeps the tasks already queued while rejecting new
 * submissions until the queue is back under it. The queue capacity applies to
 * the existing dedicated per-{@code queueKey} pools too; a dedicated pool
 * created later picks up the current settings.
 *
 * <p><b>Concurrency contract.</b> Every setter and getter here is
 * independently thread-safe and may be called from any thread at any time,
 * including concurrently with task submission, with container start/stop,
 * and with the automatic reclaim policies described below. A read observes
 * either the previous or the newly set value, never a partial update; there
 * is no ordering guarantee between a setter call and a concurrently running
 * pool creation beyond that single-value atomicity.
 */
@Parameters
public interface AsyncParameters {

	/**
	 * Sets the container-wide async failure handler used when a task has no local
	 * failure callback. Passing {@code null} restores the default logging handler.
	 *
	 * @param unhandledFailureHandler handler invoked for unobserved asynchronous
	 *                                failures
	 */
	void setUnhandledFailureHandler(final AsyncFailureHandler unhandledFailureHandler);

	/**
	 * Returns the container-wide async failure handler.
	 *
	 * @return handler invoked for unobserved asynchronous failures
	 */
	AsyncFailureHandler getUnhandledFailureHandler();

	/**
	 * Sets how many worker threads the shared general pool runs (the pool
	 * backing {@link AsyncServices#submit(Runnable)} and the other
	 * queue-key-less overloads). Never affects dedicated per-{@code queueKey}
	 * pools, which always run exactly one thread each so that tasks submitted
	 * under the same key execute strictly in submission order - use a
	 * dedicated pool, not a larger general pool, when per-key ordering or
	 * isolation is what is actually needed.
	 *
	 * <p>Applies immediately to the running general pool (see the interface
	 * Javadoc). Default: {@code Math.min(Runtime.getRuntime().availableProcessors(), 4)}.
	 *
	 * @param workerCount number of worker threads for the general pool, must be
	 *                    at least 1
	 * @throws IllegalArgumentException if {@code workerCount} is less than 1
	 */
	void setGeneralWorkerCount(final int workerCount);

	/**
	 * Returns the configured worker thread count for the general pool.
	 *
	 * @return number of worker threads configured for the general pool
	 */
	int getGeneralWorkerCount();

	/**
	 * Sets the maximum number of not-yet-started tasks a worker pool's queue
	 * holds before further submissions to that pool are rejected. Applies
	 * uniformly to the general pool and to every dedicated per-{@code queueKey}
	 * pool: each pool owns its own queue at this capacity, so a saturated
	 * dedicated queue for one key never blocks or rejects submissions to
	 * another key or to the general pool.
	 *
	 * <p>A submission whose target queue is already full fails immediately with
	 * a {@link java.util.concurrent.RejectedExecutionException}; it never
	 * blocks the calling thread and never silently drops work. Applies
	 * immediately to the running pools (see the interface Javadoc). Default:
	 * {@code 1024}.
	 *
	 * @param queueCapacity maximum number of queued-but-not-yet-started tasks
	 *                      per pool, must be at least 1
	 * @throws IllegalArgumentException if {@code queueCapacity} is less than 1
	 */
	void setWorkerQueueCapacity(final int queueCapacity);

	/**
	 * Returns the configured queue capacity applied to worker pools.
	 *
	 * @return maximum number of queued-but-not-yet-started tasks per pool
	 */
	int getWorkerQueueCapacity();

	/**
	 * Enables or disables idle-timeout reclaim of dedicated per-{@code
	 * queueKey} pools: a dedicated pool with an empty queue and no task
	 * currently running is stopped and removed once it has been idle for
	 * longer than {@link #getDedicatedWorkerIdleTimeout()}. Combines
	 * independently with {@link #setDedicatedWorkerPoolCapReclaimEnabled(boolean)}
	 * - either, both, or neither may be enabled at once. Disabled by default.
	 *
	 * <p>Reclaiming a key's pool is transparent to later callers: the next
	 * {@code submit}/{@code fireAndForget} under that key simply creates a
	 * fresh one-thread pool, exactly as if the key had never been used before.
	 * A pool that receives a task while it is being evaluated for reclaim is
	 * never reclaimed out from under that task; in the rare case where a
	 * caller resolves a dedicated pool an instant before it is reclaimed and
	 * only then submits to it, that submission may itself observe a {@link
	 * java.util.concurrent.RejectedExecutionException} - this cannot happen to
	 * a key that has had any activity within the configured timeout, since
	 * reclaim only ever targets pools that have been fully idle for that long.
	 *
	 * @param enabled {@code true} to reclaim dedicated pools once they have
	 *                been idle for the configured timeout
	 */
	void setDedicatedWorkerIdleReclaimEnabled(final boolean enabled);

	/**
	 * Reports whether idle-timeout reclaim of dedicated pools is enabled.
	 *
	 * @return {@code true} when idle dedicated pools are reclaimed automatically
	 */
	boolean isDedicatedWorkerIdleReclaimEnabled();

	/**
	 * Sets how long a dedicated per-{@code queueKey} pool may sit idle (empty
	 * queue, no task running) before it becomes eligible for reclaim, when
	 * {@link #setDedicatedWorkerIdleReclaimEnabled(boolean)} is {@code true}.
	 * Stored and returned by {@link #getDedicatedWorkerIdleTimeout()} even
	 * while that policy is disabled, but has no effect until it is enabled.
	 *
	 * <p>Accepts a duration text combining one or more {@code <amount><unit>}
	 * tokens, in any order, optionally separated by whitespace (also allowed
	 * between an amount and its own unit): {@code s} (seconds), {@code mn}
	 * (minutes), {@code h} (hours), {@code d} (days), {@code w} (weeks) - for
	 * example {@code "30 mn"}, {@code "1h"}, or {@code "3w 1d 6h"}. Amounts
	 * add up; repeating a unit adds to it (e.g. {@code "90 mn 90 mn"} is the
	 * same as {@code "3h"}).
	 *
	 * @param durationText duration text in the grammar described above
	 * @throws IllegalArgumentException if the text does not parse
	 */
	void setDedicatedWorkerIdleTimeout(final String durationText);

	/**
	 * Sets how long a dedicated per-{@code queueKey} pool may sit idle before
	 * it becomes eligible for reclaim. See
	 * {@link #setDedicatedWorkerIdleTimeout(String)} for when this applies.
	 *
	 * @param idleTimeout idle duration after which a dedicated pool is eligible
	 *                    for reclaim, must not be {@code null} or negative
	 * @throws IllegalArgumentException if {@code idleTimeout} is {@code null}
	 *                                  or negative
	 */
	void setDedicatedWorkerIdleTimeout(final Duration idleTimeout);

	/**
	 * Returns the configured idle timeout for dedicated pool reclaim.
	 *
	 * @return idle duration after which a dedicated pool becomes eligible for
	 *         reclaim
	 */
	Duration getDedicatedWorkerIdleTimeout();

	/**
	 * Enables or disables least-recently-used reclaim of dedicated
	 * per-{@code queueKey} pools: once more than
	 * {@link #getMaxDedicatedWorkerPools()} dedicated pools exist, the
	 * least-recently-active ones among those currently idle (empty queue, no
	 * task running) are stopped and removed until the count is back at or
	 * below the configured maximum. Combines independently with
	 * {@link #setDedicatedWorkerIdleReclaimEnabled(boolean)}. Disabled by
	 * default.
	 *
	 * <p>This is a best-effort, soft limit: a dedicated pool with pending or
	 * running work is never evicted to enforce the cap, so the live count can
	 * temporarily exceed the configured maximum while every pool is busy.
	 *
	 * @param enabled {@code true} to reclaim the least-recently-used idle
	 *                dedicated pools once the configured maximum is exceeded
	 */
	void setDedicatedWorkerPoolCapReclaimEnabled(final boolean enabled);

	/**
	 * Reports whether least-recently-used reclaim of dedicated pools is
	 * enabled.
	 *
	 * @return {@code true} when the dedicated pool count is capped
	 *         automatically
	 */
	boolean isDedicatedWorkerPoolCapReclaimEnabled();

	/**
	 * Sets the maximum number of dedicated per-{@code queueKey} pools kept
	 * alive at once, enforced when
	 * {@link #setDedicatedWorkerPoolCapReclaimEnabled(boolean)} is
	 * {@code true}. Stored and returned by
	 * {@link #getMaxDedicatedWorkerPools()} even while that policy is
	 * disabled, but has no effect until it is enabled. Default: {@code 256}.
	 *
	 * @param maxDedicatedWorkerPools maximum number of live dedicated pools,
	 *                                must be at least 1
	 * @throws IllegalArgumentException if {@code maxDedicatedWorkerPools} is
	 *                                  less than 1
	 */
	void setMaxDedicatedWorkerPools(final int maxDedicatedWorkerPools);

	/**
	 * Returns the configured maximum number of live dedicated pools.
	 *
	 * @return maximum number of live dedicated pools enforced by the
	 *         least-recently-used cap policy
	 */
	int getMaxDedicatedWorkerPools();

	/**
	 * Turns task tracking on or off: which task each worker runs, and how
	 * long tasks wait and run (see {@link AsyncWorkersServices}). Rejections
	 * and cancellations are always counted. Task tracking costs a little on
	 * every task, so it is only useful while someone watches. Default:
	 * {@code false}.
	 *
	 * @param tracking whether to track the tasks
	 */
	void setTaskTracking(final boolean tracking);

	/**
	 * Tells whether the tasks are tracked.
	 *
	 * @return whether the tasks are tracked
	 */
	boolean isTaskTracking();

}
