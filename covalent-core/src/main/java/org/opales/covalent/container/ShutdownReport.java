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
 * Result of a Covalent container shutdown: the final {@link ContainerStatus},
 * how long the shutdown took, how many application components ended in each
 * {@link ComponentState}, and which component types (if any) failed to shut
 * down cleanly. Built once by the shutdown sequence and handed to whatever
 * triggered it (the JVM shutdown hook, or an explicit {@code stop()} call).
 */
public final class ShutdownReport {

	private final ContainerStatus status;

	private final float durationSeconds;

	private final Map<ComponentState, Integer> componentCounts;

	private final List<Class<?>> failedComponents;

	private final List<String> residues;

	/**
	 * Creates a report with no residues.
	 *
	 * @param status           the container status after the shutdown
	 * @param durationSeconds  the shutdown duration, in seconds
	 * @param componentCounts  the application components still present, by state
	 * @param failedComponents the components in the failed state
	 */
	public ShutdownReport(final ContainerStatus status, final float durationSeconds,
			final Map<ComponentState, Integer> componentCounts, final List<Class<?>> failedComponents) {
		this(status, durationSeconds, componentCounts, failedComponents, List.of());
	}

	/**
	 * Creates a report that also lists what the shutdown left behind.
	 *
	 * @param status          the container status after the shutdown
	 * @param durationSeconds the shutdown duration, in seconds
	 * @param componentCounts the application components still present, by state
	 * @param failedComponents the components in the failed state
	 * @param residues        what the internal components still hold on behalf of
	 *                        torn-down components, empty when the container is back
	 *                        in the state of a new one
	 */
	public ShutdownReport(final ContainerStatus status, final float durationSeconds,
			final Map<ComponentState, Integer> componentCounts, final List<Class<?>> failedComponents,
			final List<String> residues) {
		this.status = status;
		this.durationSeconds = durationSeconds;
		Map<ComponentState, Integer> counts = new EnumMap<>(ComponentState.class);
		counts.putAll(componentCounts);
		this.componentCounts = Collections.unmodifiableMap(counts);
		this.failedComponents = List.copyOf(failedComponents);
		this.residues = List.copyOf(residues);
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
	 * Returns how long the shutdown took.
	 *
	 * @return how long the shutdown took, in seconds
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
	 * Returns the component types that failed to shut down cleanly.
	 *
	 * @return the component types that failed to shut down cleanly, if any
	 */
	public List<Class<?>> getFailedComponents() {
		return this.failedComponents;
	}

	/**
	 * Returns what the shutdown left behind in the container's internal
	 * components on behalf of torn-down components, one readable message per
	 * finding. Empty when the container is back in the state of a new one; a
	 * non-empty list makes the status {@link ContainerStatus#degraded}. It
	 * usually points to something the application did not release, such as a
	 * raw component (registered through the raw components services) that is
	 * still registered as a producer, provider or offeror.
	 *
	 * @return the residues, never {@code null}
	 */
	public List<String> getResidues() {
		return this.residues;
	}
}
