/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.controller;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for ComponentDiagram's Controller. Rebuilds the topology
 * graph from the registry/metamodel/services/parameters/events/lifecycle
 * Services and Events of the observed container, reached through the
 * {@code Observed*} mirror interfaces that are injected like any other
 * dependency, and triggered by their notifications once this function is
 * activated. Applies {@code ExclusionParameters} filtering before writing
 * the result into the Model.
 */
@Component
public interface ComponentDiagramController {

}
