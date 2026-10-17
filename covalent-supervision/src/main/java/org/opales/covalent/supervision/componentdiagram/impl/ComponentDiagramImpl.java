/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.impl;

import org.opales.covalent.supervision.componentdiagram.ComponentDiagram;
import org.opales.covalent.supervision.componentdiagram.controller.impl.ComponentDiagramControllerImpl;
import org.opales.covalent.supervision.componentdiagram.interaction.impl.ComponentDiagramInteractionImpl;
import org.opales.covalent.supervision.componentdiagram.model.impl.ComponentDiagramModelImpl;
import org.opales.covalent.supervision.componentdiagram.view.impl.ComponentDiagramViewImpl;

import org.opales.covalent.container.component.annotations.Implementation;

/**
 * The {@code ComponentDiagram} composite: deploys its four MVIC parts
 * together. Only this class needs to be passed to
 * {@code container.deployComponents(...)}.
 */
@Implementation
public class ComponentDiagramImpl implements ComponentDiagram {

	/** Creates the composite; the container instantiates its four parts. */
	public ComponentDiagramImpl() {
		// Nothing to initialize.
	}

	@Implementation
	private ComponentDiagramModelImpl model;

	@Implementation
	private ComponentDiagramControllerImpl controller;

	@Implementation
	private ComponentDiagramViewImpl view;

	@Implementation
	private ComponentDiagramInteractionImpl interaction;
}
