/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.async;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Executes application tasks through Covalent-managed asynchronous workers.
 *
 * <p><b>Two kinds of worker pool.</b> Every submission without a
 * {@code queueKey} runs on the shared <em>general pool</em>, sized by
 * {@link AsyncParameters#setGeneralWorkerCount(int)} (default:
 * {@code Math.min(availableProcessors(), 4)} threads); its threads compete for tasks
 * from one shared queue, so tasks generally do not run in submission order
 * and several may run concurrently. Every submission with a {@code queueKey}
 * runs on a <em>dedicated pool</em> for that key, created on first use and
 * running exactly one thread: this guarantees that all tasks submitted under
 * the same {@code queueKey} execute strictly in submission order, one at a
 * time, never concurrently with each other. This is the pattern to use
 * whenever per-key ordering matters, or to isolate a slow or blocking task
 * family from the general pool ("bulkhead" isolation) - use a dedicated
 * {@code queueKey}, not a second general-purpose pool, for that. Different
 * keys are fully independent: work under one key never waits on, or competes
 * for threads with, work under another key or the general pool. Dedicated
 * pools that go unused can be reclaimed automatically (see
 * {@link AsyncParameters}) or released explicitly (see
 * {@link #releaseQueue(String)}).
 *
 * <p><b>Backpressure.</b> Each pool (general or dedicated) owns its own
 * bounded queue, sized by {@link AsyncParameters#setWorkerQueueCapacity(int)}
 * (default {@code 1024}). A submission whose target queue is already full
 * fails immediately with a {@link RejectedExecutionException}; it never
 * blocks the caller and never silently drops work. The same exception is
 * thrown when the async manager, or the specific dedicated pool targeted by
 * the call, is stopped.
 *
 * <p><b>Message-key coalescing.</b> The four-argument overloads accept a
 * {@code messageKey} distinct from the routing {@code queueKey}: passing
 * {@code flushPending = true} cancels (with a {@link CancellationException})
 * every not-yet-started task previously submitted under the same
 * {@code queueKey} and {@code messageKey}, before the new task is queued.
 * This lets a producer replace a stale pending task ("only the latest update
 * for this entity matters") without leaving obsolete work queued behind newer
 * work. Tasks already running are never cancelled by this mechanism. The
 * two/three-argument overloads use a single default message key internally,
 * so {@code flushPending} is not applicable to them.
 *
 * <p><b>Failure handling.</b> A task submitted through {@code submit(...)}
 * reports its failure on the returned {@link CompletionStage} and never
 * reaches the unhandled-failure handler. A task submitted through
 * {@code fireAndForget(...)} has no stage for the caller to observe, so its
 * failure - if any - is reported to
 * {@link AsyncParameters#setUnhandledFailureHandler(AsyncFailureHandler)}
 * instead. Either way, one task's failure never prevents the pool's other
 * tasks, including later tasks under the same {@code queueKey}, from running.
 *
 * <p><b>Periodic tasks.</b> {@code scheduleWithFixedDelay(...)} runs a task
 * again and again, each run starting a fixed delay after the previous one
 * finished, on the general pool or on the dedicated pool of a
 * {@code queueKey}. Runs of one task never overlap. A run that fails is
 * reported to
 * {@link AsyncParameters#setUnhandledFailureHandler(AsyncFailureHandler)} and
 * the task goes on; a run refused by a full queue is skipped - counted as a
 * rejected task - and the task goes on too. A periodic task is owned by the
 * component that scheduled it: it stops by itself when that component is
 * passivated, fails or is removed, and when the container stops, without any
 * cleanup code on the component's side; the returned {@link ScheduledTask}
 * lets the component stop it earlier. A component reactivated after a
 * passivation schedules its tasks again, typically in its activation method.
 * Timing is approximate: it is checked about once a second at worst and
 * delayed when the pool is busy, so it fits housekeeping and polling, not
 * precise timing.
 *
 * <p><b>Concurrency contract.</b> Every method here is independently
 * thread-safe and may be called concurrently from any number of threads,
 * including concurrently with container start/stop and with
 * {@link AsyncParameters} changes. Submitting is never serialized across
 * callers: the ordering guarantee described above is scoped to a single
 * {@code queueKey}, not to the manager as a whole. This is unlike, for
 * example, {@link org.opales.covalent.container.registry.RegistryReadServices}
 * or {@link org.opales.covalent.container.events.EventsManagerServices},
 * which rely on the caller going through
 * {@link org.opales.covalent.container.lifecycle.LifecycleServices}'s
 * per-container lock for safe concurrent access - async task submission is
 * meant to be called from arbitrary application threads outside any
 * lifecycle operation.
 */
@Services
public interface AsyncServices {

	/**
	 * Schedules a runnable task on the general pool and exposes its completion.
	 *
	 * @param task task to execute
	 * @return completion stage completed when the task finishes
	 * @throws RejectedExecutionException if the general pool's queue is full or
	 *                                    the async manager is stopped
	 */
	CompletionStage<Void> submit(final Runnable task);

	/**
	 * Schedules a supplier task on the general pool and exposes its result.
	 *
	 * @param <T>  supplied result type
	 * @param task task to execute
	 * @return completion stage completed with the supplied result
	 * @throws RejectedExecutionException if the general pool's queue is full or
	 *                                    the async manager is stopped
	 */
	<T> CompletionStage<T> submit(final Supplier<T> task);

	/**
	 * Schedules a runnable task on the dedicated worker pool for the given
	 * queue key, creating that pool on first use. Tasks submitted under the
	 * same {@code queueKey} run strictly in submission order, one at a time.
	 *
	 * @param queueKey dedicated pool identifier
	 * @param task     task to execute
	 * @return completion stage completed when the task finishes
	 * @throws RejectedExecutionException if that pool's queue is full or the
	 *                                    async manager is stopped
	 */
	CompletionStage<Void> submit(final String queueKey, final Runnable task);

	/**
	 * Schedules a supplier task on the dedicated worker pool for the given
	 * queue key, creating that pool on first use. Tasks submitted under the
	 * same {@code queueKey} run strictly in submission order, one at a time.
	 *
	 * @param <T>      supplied result type
	 * @param queueKey dedicated pool identifier
	 * @param task     task to execute
	 * @return completion stage completed with the supplied result
	 * @throws RejectedExecutionException if that pool's queue is full or the
	 *                                    async manager is stopped
	 */
	<T> CompletionStage<T> submit(final String queueKey, final Supplier<T> task);

	/**
	 * Schedules a runnable task on the dedicated worker pool for the given
	 * queue key, and optionally cancels not-yet-started tasks previously
	 * submitted under the same queue key and message key.
	 *
	 * @param queueKey     dedicated pool identifier
	 * @param messageKey   coalescing key: identifies which pending tasks
	 *                     {@code flushPending} cancels
	 * @param task         task to execute
	 * @param flushPending {@code true} cancels pending tasks with the same
	 *                     queue key and message key before the new task is
	 *                     queued
	 * @return completion stage completed when the task finishes
	 * @throws RejectedExecutionException if that pool's queue is full or the
	 *                                    async manager is stopped
	 */
	CompletionStage<Void> submit(final String queueKey, final String messageKey, final Runnable task,
			final boolean flushPending);

	/**
	 * Schedules a supplier task on the dedicated worker pool for the given
	 * queue key, and optionally cancels not-yet-started tasks previously
	 * submitted under the same queue key and message key.
	 *
	 * @param <T>          supplied result type
	 * @param queueKey     dedicated pool identifier
	 * @param messageKey   coalescing key: identifies which pending tasks
	 *                     {@code flushPending} cancels
	 * @param task         task to execute
	 * @param flushPending {@code true} cancels pending tasks with the same
	 *                     queue key and message key before the new task is
	 *                     queued
	 * @return completion stage completed with the supplied result
	 * @throws RejectedExecutionException if that pool's queue is full or the
	 *                                    async manager is stopped
	 */
	<T> CompletionStage<T> submit(final String queueKey, final String messageKey, final Supplier<T> task,
			final boolean flushPending);

	/**
	 * Schedules a runnable task on the general pool without exposing a
	 * completion stage; a failure is reported to the unhandled-failure handler
	 * (see {@link AsyncParameters}).
	 *
	 * @param task task to execute
	 * @throws RejectedExecutionException if the general pool's queue is full or
	 *                                    the async manager is stopped
	 */
	void fireAndForget(final Runnable task);

	/**
	 * Schedules a runnable task on the dedicated worker pool for the given
	 * queue key without exposing a completion stage; a failure is reported to
	 * the unhandled-failure handler (see {@link AsyncParameters}).
	 *
	 * @param queueKey dedicated pool identifier
	 * @param task     task to execute
	 * @throws RejectedExecutionException if that pool's queue is full or the
	 *                                    async manager is stopped
	 */
	void fireAndForget(final String queueKey, final Runnable task);

	/**
	 * Schedules a runnable task on the dedicated worker pool for the given
	 * queue key without exposing a completion stage, and optionally cancels
	 * not-yet-started tasks previously submitted under the same queue key and
	 * message key. A failure is reported to the unhandled-failure handler (see
	 * {@link AsyncParameters}).
	 *
	 * @param queueKey     dedicated pool identifier
	 * @param messageKey   coalescing key: identifies which pending tasks
	 *                     {@code flushPending} cancels
	 * @param task         task to execute
	 * @param flushPending {@code true} cancels pending tasks with the same
	 *                     queue key and message key before the new task is
	 *                     queued
	 * @throws RejectedExecutionException if that pool's queue is full or the
	 *                                    async manager is stopped
	 */
	void fireAndForget(final String queueKey, final String messageKey, final Runnable task,
			final boolean flushPending);

	/**
	 * Schedules a task to run repeatedly on the general pool, each run
	 * starting {@code delay} after the previous one finished.
	 *
	 * @param initialDelay time to wait before the first run, zero or more
	 * @param delay        time between the end of a run and the start of the
	 *                     next one, strictly positive
	 * @param task         task to execute
	 * @return a handle to stop the task
	 * @throws IllegalArgumentException   if a delay is negative or {@code delay}
	 *                                    is zero
	 * @throws RejectedExecutionException if the async manager is stopped
	 */
	ScheduledTask scheduleWithFixedDelay(final Duration initialDelay, final Duration delay, final Runnable task);

	/**
	 * Schedules a task to run repeatedly on the dedicated worker pool for the
	 * given queue key, each run starting {@code delay} after the previous one
	 * finished. Its runs are ordered with the other tasks of that queue key.
	 *
	 * @param queueKey     dedicated pool identifier
	 * @param initialDelay time to wait before the first run, zero or more
	 * @param delay        time between the end of a run and the start of the
	 *                     next one, strictly positive
	 * @param task         task to execute
	 * @return a handle to stop the task
	 * @throws IllegalArgumentException   if a delay is negative or {@code delay}
	 *                                    is zero
	 * @throws RejectedExecutionException if the async manager is stopped
	 */
	ScheduledTask scheduleWithFixedDelay(final String queueKey, final Duration initialDelay, final Duration delay,
			final Runnable task);

	/**
	 * Immediately stops and removes the dedicated worker pool for a queue key,
	 * if one currently exists, and cancels the periodic tasks scheduled under
	 * that key, whether or not it has a live pool: their
	 * {@link ScheduledTask#isCancelled()} becomes {@code true}. Any
	 * not-yet-started task queued under that key is cancelled (with a
	 * {@link CancellationException}), and any task currently running on that
	 * pool's thread is interrupted - exactly what happens to every dedicated
	 * pool when the async manager itself stops. Without a live pool nor a
	 * periodic task under that key, this is a no-op.
	 *
	 * <p>This is the explicit counterpart to the automatic reclaim policies in
	 * {@link AsyncParameters} (idle timeout, least-recently-used cap): use it
	 * when the caller knows a given key is done for good (for example, an
	 * entity it identifies was deleted) instead of waiting for automatic
	 * reclaim. Submitting again under the same key afterwards simply creates a
	 * fresh dedicated pool, exactly as if the key had never been used before.
	 *
	 * @param queueKey dedicated pool identifier to release
	 */
	void releaseQueue(final String queueKey);

}
