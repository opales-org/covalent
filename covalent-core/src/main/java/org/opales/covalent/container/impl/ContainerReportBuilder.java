/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.impl;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.opales.covalent.container.ContainerStatus;
import org.opales.covalent.container.ShutdownReport;
import org.opales.covalent.container.StartupReport;
import org.opales.covalent.container.async.impl.AsyncManagerImpl;
import org.opales.covalent.container.component.ComponentAnnotations;
import org.opales.covalent.container.deployment.impl.DeployerImpl;
import org.opales.covalent.container.events.impl.EventsManagerImpl;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.lifecycle.impl.LifecycleManagerImpl;

/**
 * Builds startup and shutdown reports from the state of all components in a
 * container - application and internal.
 */
public class ContainerReportBuilder {

	private final DeployerImpl deployerImpl;

	private final LifecycleManagerImpl lifecycleManagerImpl;

	private final AsyncManagerImpl asyncManagerImpl;

	private final EventsManagerImpl eventsManagerImpl;

	ContainerReportBuilder(final DeployerImpl deployerImpl, final LifecycleManagerImpl lifecycleManagerImpl,
			final AsyncManagerImpl asyncManagerImpl, final EventsManagerImpl eventsManagerImpl) {
		this.deployerImpl = deployerImpl;
		this.lifecycleManagerImpl = lifecycleManagerImpl;
		this.asyncManagerImpl = asyncManagerImpl;
		this.eventsManagerImpl = eventsManagerImpl;
	}

	StartupReport buildStartupReport(final float durationSeconds) {
		ComponentSummary summary = this.summarizeComponents(true);
		return new StartupReport(summary.status(), durationSeconds, summary.applicationCounts(),
				summary.failedComponents());
	}

	ShutdownReport buildShutdownReport(final float durationSeconds) {
		return this.buildShutdownReport(durationSeconds, List.of());
	}

	ShutdownReport buildShutdownReport(final float durationSeconds, final List<String> residues) {
		ComponentSummary summary = this.summarizeComponents(false);
		ContainerStatus status = summary.status();
		if (!residues.isEmpty() && status == ContainerStatus.healthy) {
			// not a component failure, but the container is not back in the state of a new
			// one either: same class of signal as the shutdown residues above
			status = ContainerStatus.degraded;
		}
		return new ShutdownReport(status, durationSeconds, summary.applicationCounts(), summary.failedComponents(),
				residues);
	}

	static String formatApplicationSummary(final StartupReport report) {
		int total = report.getApplicationComponentCount();
		StringJoiner states = new StringJoiner(", ");
		report.getComponentCounts().forEach((state, count) -> states.add(count + " " + state));
		return total + " application component" + (total == 1 ? "" : "s")
				+ (total == 0 ? "" : " (" + states + ")");
	}

	private ComponentSummary summarizeComponents(final boolean startup) {
		Map<ComponentState, Integer> applicationCounts = new EnumMap<>(ComponentState.class);
		List<Class<?>> failedComponents = new ArrayList<>();
		boolean hasApplicationFailure = false;
		boolean hasInternalFailure = false;
		// A clean stop() undeploys every application component (see
		// stopHorizontal/stopVertical), so none should remain deployed
		// afterwards. Any that do are a sign the shutdown left the container
		// only partially torn down - not "not failed", but the shutdown
		// report must not call that healthy either.
		boolean hasApplicationShutdownResidue = false;
		for (Class<?> componentType : this.deployerImpl.getDeployedComponentTypes()) {
			ComponentState state = this.lifecycleManagerImpl.readComponentState(componentType);
			if (state == null) {
				continue;
			}
			boolean internal = ComponentAnnotations.isInternalComponent(componentType);
			if (!internal) {
				applicationCounts.merge(state, 1, Integer::sum);
			}
			if (state == ComponentState.failed) {
				failedComponents.add(componentType);
				hasApplicationFailure |= !internal;
				hasInternalFailure |= internal;
			} else if (startup && internal && state != ComponentState.activated) {
				failedComponents.add(componentType);
				hasInternalFailure = true;
			}
			if (!startup && !internal && state != ComponentState.failed) {
				hasApplicationShutdownResidue = true;
			}
		}
		// AsyncManagerImpl.passivate() blocks (bounded) until its worker
		// threads have actually terminated, but a task that does not honor
		// interruption can still exceed that bound. That is not an internal
		// component failure (the caller's task is at fault, not Covalent's own
		// internal state), but it does mean shutdown was not fully clean -
		// same class of signal as hasApplicationShutdownResidue above.
		boolean hasAsyncShutdownResidue = !startup && this.asyncManagerImpl.hadIncompleteShutdown();
		// EventsManagerImpl.passivate() blocks (bounded) the same way for its
		// own asynchronous dispatch worker pool - see hasAsyncShutdownResidue
		// above.
		boolean hasEventsShutdownResidue = !startup && this.eventsManagerImpl.hadIncompleteShutdown();
		// As there is no throttling/backpressure QoS on event production, the events dispatch queue rejecting a
		// notification because it is full is always a sign of a producer
		// outpacing its consumers (or a stalled consumer) - an
		// infrastructure malfunction, not an application component failure,
		// but not "healthy" either.
		// The asynchronous pools are treated the same way: a refused task is told to its caller, which may
		// ignore it (fire and forget), so the pool also records it.
		boolean hasRejectedEvents = !startup
				&& (this.eventsManagerImpl.hadRejectedEvents() || this.asyncManagerImpl.hadRejectedTasks());
		ContainerStatus status = hasInternalFailure ? ContainerStatus.failed
				: (hasApplicationFailure || hasApplicationShutdownResidue || hasAsyncShutdownResidue
						|| hasEventsShutdownResidue || hasRejectedEvents)
						? ContainerStatus.degraded
						: ContainerStatus.healthy;
		return new ComponentSummary(status, applicationCounts, failedComponents);
	}

	private static final class ComponentSummary {

		private final ContainerStatus status;

		private final Map<ComponentState, Integer> applicationCounts;

		private final List<Class<?>> failedComponents;

		private ComponentSummary(final ContainerStatus status, final Map<ComponentState, Integer> applicationCounts,
				final List<Class<?>> failedComponents) {
			this.status = status;
			this.applicationCounts = applicationCounts;
			this.failedComponents = failedComponents;
		}

		private ContainerStatus status() {
			return this.status;
		}

		private Map<ComponentState, Integer> applicationCounts() {
			return this.applicationCounts;
		}

		private List<Class<?>> failedComponents() {
			return this.failedComponents;
		}
	}

}
