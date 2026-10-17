/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container;

import java.util.EnumMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.opales.covalent.container.lifecycle.ComponentState;

/**
 * Result of a Covalent container startup: the final {@link ContainerStatus},
 * how long the startup took, how many application components ended in each
 * {@link ComponentState}, and which component types (if any) failed to start
 * cleanly. Built once by the startup sequence and handed to whatever
 * triggered it.
 */
public final class StartupReport {

	private final ContainerStatus status;

	private final float durationSeconds;

	private final Map<ComponentState, Integer> componentCounts;

	private final List<Class<?>> failedComponents;

	/**
	 * Creates a new startup report.
	 *
	 * @param status           the final container status
	 * @param durationSeconds  how long the startup took, in seconds
	 * @param componentCounts  how many application components ended in each
	 *                         {@link ComponentState}; copied defensively
	 * @param failedComponents the component types that failed to start up
	 *                         cleanly, if any; copied defensively
	 */
	public StartupReport(final ContainerStatus status, final float durationSeconds,
			final Map<ComponentState, Integer> componentCounts, final List<Class<?>> failedComponents) {
		this.status = status;
		this.durationSeconds = durationSeconds;
		Map<ComponentState, Integer> counts = new EnumMap<>(ComponentState.class);
		counts.putAll(componentCounts);
		this.componentCounts = Collections.unmodifiableMap(counts);
		this.failedComponents = List.copyOf(failedComponents);
	}

	/**
	 * Returns the final container status.
	 *
	 * @return the final container status
	 */
	public ContainerStatus getStatus() {
		return this.status;
	}

	/**
	 * Returns how long the startup took.
	 *
	 * @return how long the startup took, in seconds
	 */
	public float getDurationSeconds() {
		return this.durationSeconds;
	}

	/**
	 * Returns the per-state breakdown of application components.
	 *
	 * @return how many application components ended in each
	 *         {@link ComponentState}
	 */
	public Map<ComponentState, Integer> getComponentCounts() {
		return this.componentCounts;
	}

	/**
	 * Returns the total number of application components accounted for.
	 *
	 * @return the total number of application components accounted for in
	 *         {@link #getComponentCounts()}, across every state
	 */
	public int getApplicationComponentCount() {
		return this.componentCounts.values().stream().mapToInt(Integer::intValue).sum();
	}

	/**
	 * Returns the component types that failed to start up cleanly.
	 *
	 * @return the component types that failed to start up cleanly, if any
	 */
	public List<Class<?>> getFailedComponents() {
		return this.failedComponents;
	}
}
