/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.observed;

import java.util.List;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.workers.WorkerPoolState;

/**
 * What the observed container's worker pools are doing: its asynchronous
 * events pool and its async manager's pools - see
 * {@link ObservedRegistryReadServices} for why a distinct type is needed.
 */
@Services
public interface ObservedWorkersServices {

	/**
	 * Returns the state of the observed container's worker pools.
	 *
	 * @return the events pool first, when events are asynchronous, then the
	 *         async manager's general and dedicated pools; nothing when no
	 *         container is observed
	 */
	List<WorkerPoolState> readWorkerPools();

	/**
	 * The threads that run the observed components' calls - provided services, offered
	 * parameters, event deliveries - the pools' own threads included. Known only while the
	 * observed container is instrumented (data flows on): its calls are then seen on their own
	 * threads.
	 *
	 * @return those threads still alive, nothing when no container is observed
	 */
	List<ObservedCallingThread> readCallingThreads();

	/**
	 * Reports whether the observed container is instrumented.
	 *
	 * @return whether the observed container is instrumented (data flows on), its calls then seen
	 */
	boolean isInstrumented();

}
