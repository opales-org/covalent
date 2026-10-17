/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.cockpit.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.supervision.cockpit.Cockpit;
import org.opales.covalent.supervision.cockpit.controller.impl.CockpitControllerImpl;
import org.opales.covalent.supervision.cockpit.interaction.impl.CockpitInteractionImpl;
import org.opales.covalent.supervision.cockpit.model.impl.CockpitModelImpl;
import org.opales.covalent.supervision.cockpit.view.impl.CockpitViewImpl;

/** The cockpit function: model, controller, view and interaction controller. */
@Implementation
public class CockpitImpl implements Cockpit {

	/** Creates the cockpit function. */
	public CockpitImpl() {
		// Nothing to initialize.
	}

	@Implementation
	private CockpitModelImpl model;

	@Implementation
	private CockpitControllerImpl controller;

	@Implementation
	private CockpitViewImpl view;

	@Implementation
	private CockpitInteractionImpl interaction;
}
