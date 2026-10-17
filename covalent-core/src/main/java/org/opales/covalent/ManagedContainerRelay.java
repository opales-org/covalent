/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent;

import org.opales.covalent.container.ContainerLifecycleEvents;
import org.opales.covalent.container.ShutdownReport;
import org.opales.covalent.container.StartupReport;
import org.opales.covalent.container.component.internals.Internal;
import org.opales.covalent.container.lifecycle.Removal;

/**
 * Relays a managed container's lifecycle events to {@link Covalent}, which
 * dispatches them to its {@link ManagedContainerEvents} observers.
 * <p>
 * Registered by {@link Covalent} as a raw component of each managed container
 * it creates, and only by it: the constructor is not accessible outside this
 * package. It is the only raw component allowed to be {@link Internal}: as
 * such, it is kept across {@code stop()}, with its subscription, and its
 * removal callback is invoked by {@code dispose()}, once the business
 * components are torn down.
 */
@Internal
public final class ManagedContainerRelay implements ContainerLifecycleEvents {

	private final String name;

	ManagedContainerRelay(final String name) {
		this.name = name;
	}

	@Override
	public void onContainerStarting(final String containerName) {
		Covalent.dispatch(observer -> observer.onContainerStarting(this.name));
	}

	@Override
	public void onContainerStarted(final String containerName, final StartupReport report) {
		Covalent.dispatch(observer -> observer.onContainerStarted(this.name, report));
	}

	@Override
	public void onContainerStartFailed(final String containerName, final Throwable failure) {
		Covalent.dispatch(observer -> observer.onContainerStartFailed(this.name, failure));
	}

	@Override
	public void onContainerStopping(final String containerName) {
		Covalent.dispatch(observer -> observer.onContainerStopping(this.name));
	}

	@Override
	public void onContainerStopped(final String containerName, final ShutdownReport report) {
		Covalent.dispatch(observer -> observer.onContainerStopped(this.name, report));
	}

	@Override
	public void onContainerDisposing(final String containerName) {
		Covalent.dispatch(observer -> observer.onContainerDisposing(this.name));
	}

	/**
	 * Invoked by the container's {@code dispose()}, once its business
	 * components are torn down: the container leaves the managed containers
	 * and {@code onContainerDisposed} is notified.
	 */
	@Removal
	void removed() {
		Covalent.containerDisposed(this.name);
	}
}
