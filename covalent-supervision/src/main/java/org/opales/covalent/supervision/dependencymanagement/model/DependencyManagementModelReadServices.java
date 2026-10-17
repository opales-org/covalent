/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.dependencymanagement.model;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Reads the current dependency graph. {@link DependencyManagementEvents}
 * carries no state: a consumer reads it here when it handles it, so it always
 * renders the latest graph, whatever order the notifications arrive in.
 */
@Services
public interface DependencyManagementModelReadServices {

	/**
	 * Returns the current dependency graph.
	 *
	 * @return the current graph, empty if nothing is observed
	 */
	DependencyManagementGraph getGraph();
}
