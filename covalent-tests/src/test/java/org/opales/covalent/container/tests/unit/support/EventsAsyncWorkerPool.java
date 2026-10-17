/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.support;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;

import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.events.EventsManager;
import org.opales.covalent.container.registry.RegistryReadServices;

/**
 * Test-only introspection helper: reflects into a container's own
 * {@code EventsManagerImpl} instance to read the {@code ExecutorService}
 * currently backing its asynchronous worker pool.
 * <p>
 * Returning the pool itself, rather than its individual threads, is what
 * lets a test rely on {@link ExecutorService}'s own public API -
 * {@code isTerminated()}, reference identity across a restart - instead of
 * needing any additional introspection surface on {@code EventsManagerImpl}
 * itself: a thread name alone cannot scope a query to one container (the
 * naming index inside {@code EventsManagerImpl#createAsynchronousWorker}
 * restarts at zero for every pool, so distinct containers routinely have
 * worker threads with identical names), but the pool reference this returns
 * is specific to the container passed in, with no such ambiguity.
 */
public final class EventsAsyncWorkerPool {

	private static final EventsAsyncWorkerPool INSTANCE = new EventsAsyncWorkerPool();

	private EventsAsyncWorkerPool() {
	}

	/**
	 * Returns the {@code ExecutorService} currently backing the given
	 * container's own events manager, as (re)created by its most recent
	 * activation.
	 *
	 * @param container the container to introspect
	 * @return that container's own current asynchronous worker pool
	 */
	public static ExecutorService currentFor(final CovalentContainer container) {
		try {
			container.rcs().registerComponent(EventsAsyncWorkerPool.class, INSTANCE);
			RegistryReadServices registryReadServices = container.rcs().useServices(RegistryReadServices.class);
			Object eventsManager = registryReadServices.getComponentInstance(EventsManager.class);
			Field field = eventsManager.getClass().getDeclaredField("asynchronousWorker");
			field.setAccessible(true);
			return (ExecutorService) field.get(eventsManager);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not introspect the events async worker pool for " + container, e);
		}
	}

}
