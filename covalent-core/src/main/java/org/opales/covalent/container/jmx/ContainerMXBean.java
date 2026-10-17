/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.jmx;

import java.util.List;

/**
 * The JMX view of a named container, registered as
 * {@code org.opales.covalent:type=Container,name=<container name>}. Read-only:
 * it reads the counters that exist whether the Supervisor runs or not.
 */
public interface ContainerMXBean {

	/**
	 * The name of the container.
	 *
	 * @return the container's name
	 */
	String getName();

	/**
	 * The counters of every worker pool the container runs now: the events
	 * pool, the general async pool and the dedicated async pools.
	 *
	 * @return one entry per pool
	 */
	List<WorkerPoolInfo> getWorkerPools();
}
