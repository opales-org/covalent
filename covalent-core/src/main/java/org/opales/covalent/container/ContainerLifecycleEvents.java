/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container;

import org.opales.covalent.container.component.annotations.Events;

/**
 * Reports a container's own lifecycle: its starts, stops and disposal.
 * Produced by the container itself, so delivered synchronously, on the
 * thread calling {@link CovalentContainer#start()},
 * {@link CovalentContainer#stop()} or {@link CovalentContainer#dispose()},
 * while the container holds its lifecycle lock: a consumer must neither
 * block nor wait for another thread acting on the container. Each
 * notification carries the container's name, {@code null} for an unmanaged
 * container (see {@link CovalentContainer#getName()}).
 * <p>
 * Every accepted transition is concluded by exactly one notification: a start
 * by {@link #onContainerStarted(String, StartupReport)} or
 * {@link #onContainerStartFailed(String, Throwable)}, a stop by
 * {@link #onContainerStopped(String, ShutdownReport)}. A start can be refused
 * as a whole; a stop cannot, it always ends with the container stopped.
 * Component failures do not fail a transition: they are isolated and reported
 * in the startup or shutdown report. A call refused before its transition is
 * accepted ({@code start()} on a started container, {@code stop()} on a
 * stopped one) notifies nothing.
 * <p>
 * A business component subscribed to these events is itself torn down by
 * the stop cascade: it is notified up to {@link #onContainerStopping(String)}.
 */
@Events
public interface ContainerLifecycleEvents {

	/**
	 * Notifies that the container is about to start its business components.
	 *
	 * @param name the container's name, {@code null} for an unmanaged one
	 */
	void onContainerStarting(String name);

	/**
	 * Notifies that the container has started. Components that failed to
	 * start are isolated and reported in {@code report}; they do not fail the
	 * start.
	 *
	 * @param name   the container's name, {@code null} for an unmanaged one
	 * @param report the startup report
	 */
	void onContainerStarted(String name, StartupReport report);

	/**
	 * Notifies that the container's start was refused as a whole, without
	 * starting anything: a strict circular dependency verification in
	 * particular. The container stays not started.
	 *
	 * @param name    the container's name, {@code null} for an unmanaged one
	 * @param failure the failure, also thrown to the caller of
	 *                {@link CovalentContainer#start()}
	 */
	void onContainerStartFailed(String name, Throwable failure);

	/**
	 * Notifies that the container is about to stop its business components,
	 * every one of them still in place.
	 *
	 * @param name the container's name, {@code null} for an unmanaged one
	 */
	void onContainerStopping(String name);

	/**
	 * Notifies that the container has stopped: it is in the state of a new
	 * one. A stop always ends this way; components that failed to stop are
	 * isolated and reported in {@code report}, whose status tells healthy,
	 * degraded or failed.
	 *
	 * @param name   the container's name, {@code null} for an unmanaged one
	 * @param report the shutdown report
	 */
	void onContainerStopped(String name, ShutdownReport report);

	/**
	 * Notifies that the container is about to be disposed.
	 *
	 * @param name the container's name, {@code null} for an unmanaged one
	 */
	void onContainerDisposing(String name);
}
