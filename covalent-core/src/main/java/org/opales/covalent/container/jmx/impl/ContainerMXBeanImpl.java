/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.jmx.impl;

import java.util.ArrayList;
import java.util.List;

import org.opales.covalent.container.async.impl.AsyncManagerImpl;
import org.opales.covalent.container.events.impl.EventsManagerImpl;
import org.opales.covalent.container.jmx.ContainerMXBean;
import org.opales.covalent.container.jmx.WorkerPoolInfo;
import org.opales.covalent.container.workers.WorkerPoolState;

/**
 * Adapter between the container's managers and {@link ContainerMXBean}. It
 * keeps no state of its own: every read goes to the managers.
 */
final class ContainerMXBeanImpl implements ContainerMXBean {

	private final String name;

	private final AsyncManagerImpl asyncManagerImpl;

	private final EventsManagerImpl eventsManagerImpl;

	ContainerMXBeanImpl(final String name, final AsyncManagerImpl asyncManagerImpl,
			final EventsManagerImpl eventsManagerImpl) {
		this.name = name;
		this.asyncManagerImpl = asyncManagerImpl;
		this.eventsManagerImpl = eventsManagerImpl;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public List<WorkerPoolInfo> getWorkerPools() {
		List<WorkerPoolState> states = new ArrayList<>(this.eventsManagerImpl.readWorkerPools());
		states.addAll(this.asyncManagerImpl.readWorkerPools());
		List<WorkerPoolInfo> pools = new ArrayList<>(states.size());
		for (WorkerPoolState state : states) {
			pools.add(new WorkerPoolInfo(state.name(), state.kind().name(), state.targetWorkerCount(),
					state.queuedTasks(), state.queueCapacity(), state.rejectedTasks(), state.cancelledTasks(),
					state.completedTasks(), state.taskTracking()));
		}
		return pools;
	}
}
