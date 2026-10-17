/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.dependencymanagement.model.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opales.covalent.supervision.dependencymanagement.model.DependencyManagementEvents;
import org.opales.covalent.supervision.dependencymanagement.model.DependencyManagementGraph;
import org.opales.covalent.supervision.dependencymanagement.model.DependencyManagementModel;
import org.opales.covalent.supervision.dependencymanagement.model.DependencyManagementModelReadServices;
import org.opales.covalent.supervision.dependencymanagement.model.DependencyManagementModelServices;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;

/** Plain state holder - see {@code DependencyManagementModelServices}'s javadoc. */
@Implementation
public class DependencyManagementModelImpl
		implements DependencyManagementModel, DependencyManagementModelServices, DependencyManagementModelReadServices {

	/** Creates the Model, holding an empty graph until the Controller sets one. */
	public DependencyManagementModelImpl() {
		// Nothing to initialize.
	}

	@ProducedEvents
	private DependencyManagementEvents produceDependencyManagementEvents;

	private volatile DependencyManagementGraph graph = new DependencyManagementGraph(Set.of(), Map.of(), List.of());

	@Override
	public void setGraph(final DependencyManagementGraph graph) {
		this.graph = graph;
		this.produceDependencyManagementEvents.onGraphChanged();
	}

	@Override
	public DependencyManagementGraph getGraph() {
		return this.graph;
	}
}
