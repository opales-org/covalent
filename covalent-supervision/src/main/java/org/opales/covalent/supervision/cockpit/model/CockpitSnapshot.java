/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.cockpit.model;

import java.util.List;
import java.util.Map;

import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.supervision.componentdiagram.model.ComponentPortKind;
import org.opales.covalent.supervision.componentdiagram.model.ComponentStatus;

/**
 * The cockpit's figures at one moment.
 *
 * @param timeMillis when they were taken
 * @param managedCount the components the container manages, composites excluded
 * @param rawCount the components it does not manage
 * @param compositeCount the composites
 * @param serviceLinks the service links between components
 * @param eventLinks the event links
 * @param parameterLinks the parameter links
 * @param servicesPerSecond service calls over the last second
 * @param eventsPerSecond event notifications over the last second, one per subscriber
 * @param parametersPerSecond parameter reads over the last second
 * @param newFailures the failures of the last second, by port
 * @param states how many components, composites excluded, are in each state
 * @param recentTransitions the latest state changes, newest first
 * @param composites the top-level composites
 * @param hottestPorts the busiest ports, busiest first
 * @param slices the console's slices at the level it shows
 * @param consoleLog what the console did or saw, newest first
 * @param consoleFocus the composites entered in the console, outermost first, empty at the top
 * @param consoleActivated the components the console drives that are activated, at any level
 * @param consoleDriven the components the console drives, at any level: managed ones, not
 *                      Covalent's own
 */
public record CockpitSnapshot(long timeMillis, int managedCount, int rawCount, int compositeCount, int serviceLinks,
		int eventLinks, int parameterLinks, double servicesPerSecond, double eventsPerSecond, double parametersPerSecond,
		List<Failure> newFailures, Map<ComponentState, Integer> states, List<Transition> recentTransitions,
		List<CompositeSummary> composites, List<HotPort> hottestPorts, List<Slice> slices, List<LogEntry> consoleLog,
		List<Class<?>> consoleFocus, int consoleActivated, int consoleDriven) {

	/**
	 * Failed calls through one port of one component, counted over the last second.
	 *
	 * @param timeMillis when they were counted
	 * @param owner the component whose port failed
	 * @param portType the port's interface
	 * @param method the last method that failed, {@code null} when unknown
	 * @param count how many calls failed
	 */
	public record Failure(long timeMillis, Class<?> owner, Class<?> portType, String method, int count) {
	}

	/**
	 * A change of a component's lifecycle state, as the cockpit saw it.
	 *
	 * @param timeMillis when it was seen
	 * @param componentType the component
	 * @param from its previous state, {@code null} when unknown
	 * @param to its new state
	 */
	public record Transition(long timeMillis, Class<?> componentType, ComponentState from, ComponentState to) {
	}

	/**
	 * A top-level composite, with the states of the components it contains and its call rate.
	 *
	 * @param compositeType the composite
	 * @param componentStates the states of the components it contains, at any depth
	 * @param messagesPerSecond the calls through its ports, the ones shared with the outside
	 * @param status its aggregated status, {@code null} when unknown
	 */
	public record CompositeSummary(Class<?> compositeType, List<ComponentState> componentStates,
			double messagesPerSecond, ComponentStatus status) {
	}

	/**
	 * One port ranked among the busiest ones, with its call rate and its 95th percentile duration.
	 *
	 * @param portType the port's interface
	 * @param kind the port's kind
	 * @param owner the component receiving the calls
	 * @param callsPerSecond the calls over the last second
	 * @param p95Nanos the 95th percentile of their durations, {@code -1} when unknown
	 */
	public record HotPort(Class<?> portType, ComponentPortKind kind, Class<?> owner, double callsPerSecond,
			long p95Nanos) {
	}

	/**
	 * One console slice: a composite of the level shown and its direct children, or, with no
	 * composite, the components standing alone at that level.
	 *
	 * @param compositeType the composite, {@code null} for the standalone components
	 * @param state the composite's own state, {@code null} when unknown or with no composite
	 * @param messagesPerSecond the calls through the composite's ports shared with the outside
	 * @param channels its direct children, those nobody depends on first
	 * @param dependents the components outside it that depend on one inside, with the port
	 * @param internal whether it is one of Covalent's own components, shown but not driven
	 * @param navigable whether it holds composites, and so can be entered
	 */
	public record Slice(Class<?> compositeType, ComponentState state, double messagesPerSecond, List<Channel> channels,
			List<Dependent> dependents, boolean internal, boolean navigable) {
	}

	/**
	 * One component of a console slice, with its state and the components that depend on it.
	 *
	 * @param componentType the component
	 * @param state its state, {@code null} when unknown
	 * @param raw whether the container does not manage its lifecycle
	 * @param dependents the components that depend on it, with the port
	 * @param internal whether it is one of Covalent's own components, shown but not driven
	 * @param composite whether it is a composite, which can be entered
	 * @param componentStates for a composite, the known states of the components it contains,
	 *                        at any depth, composites excluded; empty otherwise
	 * @param componentCount for a composite, the components it contains, at any depth,
	 *                       composites excluded; {@code 0} otherwise
	 */
	public record Channel(Class<?> componentType, ComponentState state, boolean raw, List<Dependent> dependents,
			boolean internal, boolean composite, List<ComponentState> componentStates, int componentCount) {
	}

	/**
	 * A component that depends on another one through a given port.
	 *
	 * @param componentType a component depending on another
	 * @param portType the port through which it does
	 */
	public record Dependent(Class<?> componentType, Class<?> portType) {
	}

	/**
	 * One line of the console's log, kept as a phrase for the view to word.
	 *
	 * @param timeMillis when it happened
	 * @param text what the console did or saw, for the view to word
	 * @param failed whether it is a failure
	 */
	public record LogEntry(long timeMillis, Phrase text, boolean failed) {
	}
}
