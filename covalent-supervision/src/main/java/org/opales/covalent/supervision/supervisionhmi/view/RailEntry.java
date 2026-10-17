/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.supervisionhmi.view;

/**
 * One entry of SupervisionHmi's side rail. Every hosted panel declares the
 * entry it belongs to through {@link SupervisorTabDescriptor#railEntry()};
 * the panels of one entry share its content area, as sub-tabs.
 */
public enum RailEntry {

	/** The cockpit: the observed container at a glance, and its lifecycle console. */
	COCKPIT,
	/** The component diagrams of the observed container. */
	COMPONENTS,
	/** The sequence diagrams recorded from the observed container. */
	SEQUENCES,
	/** The dependency graph of the observed container. */
	GRAPH,
	/** The threads of the observed container. */
	THREADS,
	/** The policy that excludes components from the diagrams. */
	EXCLUSIONS,
	/** The stereotype table's panel: hosted as the Settings page's Stereotypes tab, so it has no rail button of its own. */
	STEREOTYPES,
	/** The supervisor's preferences, shown in a panel of the supervisor itself. */
	SETTINGS;
}
