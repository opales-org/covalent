/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.model;

import java.util.Set;

/**
 * The whole live structure of the observed container, not narrowed to any tab's level: every
 * shown component, every composition, service, parameter and event link between them.
 *
 * @param componentTypes every component not excluded
 * @param edges every link between them, composition included
 * @param rawTypes the components the container does not manage
 */
public record ComponentDiagramStructure(Set<Class<?>> componentTypes, Set<ComponentDiagramEdge> edges,
		Set<Class<?>> rawTypes) {

	/** Copies the three sets, so that the structure is immutable. */
	public ComponentDiagramStructure {
		componentTypes = Set.copyOf(componentTypes);
		edges = Set.copyOf(edges);
		rawTypes = Set.copyOf(rawTypes);
	}
}
