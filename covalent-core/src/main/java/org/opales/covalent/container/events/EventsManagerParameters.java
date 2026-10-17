/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.events;

import org.opales.covalent.container.component.annotations.Parameters;

/**
 * Configures event dispatch behavior.
 *
 * <p>Same concurrency contract as {@link EventsManagerServices}: not
 * independently synchronized, relies on the caller going through
 * {@link org.opales.covalent.container.lifecycle.LifecycleServices}'s
 * per-container lock.
 */
@Parameters
public interface EventsManagerParameters {

	/**
	 * Creates subscription parameters for a manual event subscription.
	 *
	 * @return new mutable subscription parameters
	 */
	SubscribeParameters newSubscribeParameters();

	/**
	 * Sets whether event dispatch runs synchronously on the producer call stack.
	 * The setting is read when a producer obtains its notifier, so set it before
	 * the producers are created.
	 *
	 * @param synchronous {@code true} dispatches events synchronously
	 */
	void setSynchronous(final boolean synchronous);

	/**
	 * Reports whether event dispatch runs synchronously on the producer call stack.
	 *
	 * @return {@code true} when event dispatch is synchronous
	 */
	boolean isSynchronous();

	/**
	 * Sets how many threads back the asynchronous event dispatch pool.
	 * Applies immediately to the running pool: added threads start at once,
	 * surplus threads finish their current delivery before terminating.
	 * Defaults to {@code Math.min(Runtime.getRuntime().availableProcessors(), 4)}.
	 *
	 * @param workerCount the worker thread count, at least 1
	 */
	void setWorkerCount(final int workerCount);

	/**
	 * Reports how many threads back the asynchronous event dispatch pool.
	 *
	 * @return the worker thread count
	 */
	int getWorkerCount();

	/**
	 * Sets the capacity of the asynchronous event dispatch queue. Once
	 * full, a notification for a consumer is dropped (logged, never
	 * thrown back to the producer - event delivery stays strictly
	 * fire-and-forget) instead of growing the queue without limit.
	 * Applies immediately to the running queue: a reduced capacity keeps the
	 * notifications already queued and drops new ones until the queue is
	 * back under it. Defaults to {@code 1024}.
	 *
	 * @param queueCapacity the queue capacity, at least 1
	 */
	void setWorkerQueueCapacity(final int queueCapacity);

	/**
	 * Reports the capacity of the asynchronous event dispatch queue.
	 *
	 * @return the queue capacity
	 */
	int getWorkerQueueCapacity();

	/**
	 * Turns task tracking on or off: which delivery each worker runs, and
	 * how long deliveries wait and run (see {@link EventsWorkersServices}).
	 * Rejections are always counted. Task tracking costs a little on every
	 * delivery, so it is only useful while someone watches. Default:
	 * {@code false}.
	 *
	 * @param tracking whether to track the deliveries
	 */
	void setTaskTracking(final boolean tracking);

	/**
	 * Tells whether the deliveries are tracked.
	 *
	 * @return whether the deliveries are tracked
	 */
	boolean isTaskTracking();

}
