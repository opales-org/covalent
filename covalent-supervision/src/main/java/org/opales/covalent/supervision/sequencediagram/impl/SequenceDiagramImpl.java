/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.impl;

import org.opales.covalent.supervision.sequencediagram.SequenceDiagram;
import org.opales.covalent.supervision.sequencediagram.controller.impl.SequenceDiagramControllerImpl;
import org.opales.covalent.supervision.sequencediagram.interaction.impl.SequenceDiagramInteractionImpl;
import org.opales.covalent.supervision.sequencediagram.model.impl.SequenceDiagramModelImpl;
import org.opales.covalent.supervision.sequencediagram.view.impl.SequenceDiagramViewImpl;

import org.opales.covalent.container.component.annotations.Implementation;

/**
 * The {@code SequenceDiagram} composite: deploys its four MVIC parts
 * together, one persistent instance per container - see the class
 * javadocs for how it manages several sessions internally. Only this class
 * needs to be passed to {@code container.deployComponents(...)}.
 */
@Implementation
public class SequenceDiagramImpl implements SequenceDiagram {

	/** Creates the composite; the container instantiates its four parts. */
	public SequenceDiagramImpl() {
		// Nothing to initialize.
	}

	@Implementation
	private SequenceDiagramModelImpl model;

	@Implementation
	private SequenceDiagramControllerImpl controller;

	@Implementation
	private SequenceDiagramViewImpl view;

	@Implementation
	private SequenceDiagramInteractionImpl interaction;
}
