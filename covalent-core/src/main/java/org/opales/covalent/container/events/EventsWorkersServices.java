/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.events;

import java.util.List;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.workers.WorkerPoolState;

/**
 * What the asynchronous events pool is doing, for supervision tools.
 * Reading is cheap and meant to be repeated a few times per second. Task
 * tracking, off by default, costs a little on every delivery: it is turned
 * on only while someone watches.
 */
@Services
public interface EventsWorkersServices {

	/**
	 * Reads the state of the asynchronous events pool.
	 *
	 * @return the asynchronous events pool, or nothing when events are
	 *         delivered synchronously, on the producer's thread
	 */
	List<WorkerPoolState> readWorkerPools();
}
