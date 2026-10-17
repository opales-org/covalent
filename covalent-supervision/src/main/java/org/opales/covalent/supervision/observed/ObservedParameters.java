/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.observed;

import org.opales.covalent.container.component.annotations.Parameters;
import org.opales.covalent.container.workers.WorkerPoolKind;

/**
 * The settings of the observed container that the supervisor changes: the task tracking and the size of
 * its worker pools, and the per-call notifications - see {@link ObservedRegistryReadServices} for why a
 * distinct type is needed. What the observed container is doing is read through
 * {@link ObservedWorkersServices} and the other {@code Observed...Services}.
 */
@Parameters
public interface ObservedParameters {

	/**
	 * Turns the observed pools' task tracking on or off. It is turned off when the container is left.
	 *
	 * @param tracking whether to track the tasks
	 */
	void setTaskTracking(boolean tracking);

	/**
	 * Reports whether the observed pools' tasks are tracked.
	 *
	 * @return whether the observed pools' tasks are tracked
	 */
	boolean isTaskTracking();

	/**
	 * Sets how many workers a pool runs, at once and until the observed container restarts:
	 * its configuration then applies again. Only the events pool and the general pool are
	 * tuned; a dedicated pool keeps its single worker, which keeps its tasks in order.
	 *
	 * @param kind  {@link WorkerPoolKind#EVENTS} or {@link WorkerPoolKind#GENERAL}
	 * @param count the worker count, at least 1
	 */
	void setWorkerCount(WorkerPoolKind kind, int count);

	/**
	 * Sets a pool's queue capacity, at once and until the observed container restarts. The
	 * general pool's applies to the dedicated pools too: the async manager has one setting
	 * for all of them.
	 *
	 * @param kind     {@link WorkerPoolKind#EVENTS} or {@link WorkerPoolKind#GENERAL}
	 * @param capacity the queue capacity, at least 1
	 */
	void setQueueCapacity(WorkerPoolKind kind, int capacity);

	/**
	 * Switches the per-call notifications of {@link ObservedServicesManagerEvents},
	 * {@link ObservedParametersManagerEvents} and {@link ObservedEventsManagerEvents} on or off. They
	 * are off by default: only a consumer that needs every single call - a sequence diagram recording -
	 * turns them on, for as long as it needs them. {@link ObservedPortActivityEvents} is unaffected.
	 *
	 * @param enabled whether every call of the observed container is notified
	 */
	void setCallStreamEnabled(boolean enabled);

}
