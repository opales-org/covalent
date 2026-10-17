/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent;

import org.opales.covalent.container.ContainerLifecycleEvents;

/**
 * Reports the lifecycle of every managed container (see
 * {@link Covalent#managedContainer(String)}): its creation, its disposal, and
 * in between the {@link ContainerLifecycleEvents} of the container itself,
 * relayed with its name. Unmanaged containers are not reported.
 * <p>
 * Observers subscribe through {@link Covalent#subscribe(ManagedContainerEvents)}.
 * Notifications are delivered synchronously, on the thread acting on the
 * container, while the container holds its lifecycle lock: an observer must
 * neither block nor wait for another thread acting on the container. Each
 * observer is isolated: a failing observer does not deprive the others.
 * <p>
 * Unlike a component subscribed to a container's own events, an observer is
 * not torn down by {@link org.opales.covalent.container.CovalentContainer#stop()}:
 * it receives every notification, {@code onContainerStopped} included.
 */
public interface ManagedContainerEvents extends ContainerLifecycleEvents {

	/**
	 * Notifies that a managed container has been created.
	 *
	 * @param name the container's name
	 */
	void onContainerCreated(String name);

	/**
	 * Notifies that a managed container has been disposed, whether through
	 * {@link Covalent#disposeManagedContainer(String)} or by its own
	 * {@link org.opales.covalent.container.CovalentContainer#dispose()}: it no
	 * longer has any business component, is no longer usable, and is no longer
	 * one of the managed containers.
	 *
	 * @param name the container's name
	 */
	void onContainerDisposed(String name);
}
