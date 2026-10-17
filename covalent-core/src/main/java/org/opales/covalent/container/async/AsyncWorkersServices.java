/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.async;

import java.util.List;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.workers.WorkerPoolState;

/**
 * What the async manager's worker pools are doing, for supervision tools:
 * the general pool and the dedicated ones. Reading is cheap and meant to be
 * repeated a few times per second. Task tracking, off by default, costs a
 * little on every task: it is turned on only while someone watches.
 */
@Services
public interface AsyncWorkersServices {

	/**
	 * Reads the state of the worker pools.
	 *
	 * @return the general pool, then the dedicated pools by queue key
	 */
	List<WorkerPoolState> readWorkerPools();
}
