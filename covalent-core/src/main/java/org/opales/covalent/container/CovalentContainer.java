/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container;

import java.util.List;

import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.internals.Internal;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.metamodel.metamodel.ComponentMetamodel;
import org.opales.covalent.container.raw.RawComponentsServices;

/**
 * Coordinates deployment, lifecycle startup and shutdown, and access to
 * container-level Covalent services.
 */
@Internal
@Component
public interface CovalentContainer extends AutoCloseable {

	/**
	 * Returns this container's name, given by {@code Covalent} to the
	 * containers it manages.
	 *
	 * @return the managed container's name, or {@code null} for an unmanaged
	 *         container
	 */
	String getName();

	/**
	 * Returns raw component services for this container.
	 *
	 * @return raw component services
	 */
	RawComponentsServices rcs();

	/**
	 * Returns lifecycle services for this container.
	 *
	 * @return lifecycle services
	 */
	LifecycleServices lcs();

	/**
	 * Deploys component implementations through the lifecycle manager.
	 *
	 * @param implementations implementation classes to deploy
	 * @return metamodels produced for the deployed components
	 */
	List<ComponentMetamodel> deployComponents(Class<?>... implementations);

	/**
	 * Starts the business components. The internal components are available
	 * before and after, whether the container is started or not.
	 *
	 * @return startup report
	 * @throws IllegalStateException if the container is already started
	 */
	StartupReport start();

	/**
	 * Stops the business components, and leaves the container in the state
	 * of a new one: its internal components stay available.
	 *
	 * @return shutdown report
	 * @throws IllegalStateException if the container is not started
	 */
	ShutdownReport stop();

	/**
	 * Reports whether this container is currently started.
	 *
	 * @return {@code true} when the container is started, i.e. when calling
	 *         {@link #stop()} would succeed rather than throw
	 */
	boolean isStarted();

	/**
	 * Definitively releases this container: stops it first if still started,
	 * then passivates its internal (technical) component tree for good,
	 * without re-activating it afterward - unlike {@link #stop()}, which
	 * always leaves the container usable right away. Lenient and
	 * idempotent: safe to call on a container that is not started, or
	 * already disposed.
	 */
	void dispose();

	/**
	 * Reports whether {@link #dispose()} has been called on this container.
	 *
	 * @return {@code true} once this container has been disposed
	 */
	boolean isDisposed();

	/**
	 * Same as {@link #dispose()}, so a container can be used with
	 * try-with-resources.
	 */
	@Override
	default void close() {
		this.dispose();
	}

	/**
	 * Returns the container parameters.
	 * 
	 * @return the container parameters
	 */
	public CovalentContainerParameters getParameters();

}
