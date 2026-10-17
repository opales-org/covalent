/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.supervisionhmi.impl;

import org.opales.covalent.supervision.supervisionhmi.SupervisionHmi;
import org.opales.covalent.supervision.supervisionhmi.interaction.impl.SupervisionHmiInteractionImpl;
import org.opales.covalent.supervision.supervisionhmi.view.impl.SupervisionHmiViewImpl;

import org.opales.covalent.container.component.annotations.Implementation;

/**
 * The {@code SupervisionHmi} composite: deploys its View and Interaction
 * Controller together - no Model/Controller, see {@code SupervisionHmi}'s
 * own javadoc.
 */
@Implementation
public class SupervisionHmiImpl implements SupervisionHmi {

	/** Creates the supervisor's window function. */
	public SupervisionHmiImpl() {
		// Nothing to initialize.
	}

	@Implementation
	private SupervisionHmiViewImpl view;

	@Implementation
	private SupervisionHmiInteractionImpl interaction;
}
