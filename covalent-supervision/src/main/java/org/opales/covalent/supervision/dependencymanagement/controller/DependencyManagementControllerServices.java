/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.dependencymanagement.controller;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Commands of {@code DependencyManagementController}, used by this function's own Interaction Controller.
 * There is no public "DependencyManagementServices": nothing outside this function calls into it.
 */
@Services
public interface DependencyManagementControllerServices {

	/** Recomputes the graph from {@code DependencyGraphServices}, filtered through {@code ExclusionParameters}. */
	void refreshGraph();
}
