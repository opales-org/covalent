/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.containermanager.impl;

import org.opales.covalent.supervision.containermanager.ContainerManager;
import org.opales.covalent.supervision.containermanager.interaction.impl.ContainerManagerInteractionImpl;
import org.opales.covalent.supervision.containermanager.view.impl.ContainerManagerViewImpl;

import org.opales.covalent.container.component.annotations.Implementation;

/**
 * The {@code ContainerManager} composite: deploys its View and Interaction
 * Controller together - no Model/Controller, see {@code ContainerManager}'s
 * own javadoc.
 */
@Implementation
public class ContainerManagerImpl implements ContainerManager {

	/** Creates the composite; the container deploys its View and Interaction Controller as its own parts. */
	public ContainerManagerImpl() {
		// Nothing to initialize.
	}

	@Implementation
	private ContainerManagerViewImpl view;

	@Implementation
	private ContainerManagerInteractionImpl interaction;
}
