/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.stereotypepolicy.impl;

import org.opales.covalent.supervision.stereotypepolicy.StereotypePolicy;
import org.opales.covalent.supervision.stereotypepolicy.controller.impl.StereotypePolicyControllerImpl;
import org.opales.covalent.supervision.stereotypepolicy.interaction.impl.StereotypePolicyInteractionImpl;
import org.opales.covalent.supervision.stereotypepolicy.model.impl.StereotypePolicyModelImpl;
import org.opales.covalent.supervision.stereotypepolicy.view.impl.StereotypePolicyViewImpl;

import org.opales.covalent.container.component.annotations.Implementation;

/**
 * The {@code StereotypePolicy} composite: deploys its four MVIC parts together. Only this
 * class needs to be passed to {@code container.deployComponents(...)}.
 */
@Implementation
public class StereotypePolicyImpl implements StereotypePolicy {

	/** Creates the composite; the container deploys its four MVIC parts as its own parts. */
	public StereotypePolicyImpl() {
		// Nothing to initialize.
	}

	@Implementation
	private StereotypePolicyModelImpl model;

	@Implementation
	private StereotypePolicyControllerImpl controller;

	@Implementation
	private StereotypePolicyViewImpl view;

	@Implementation
	private StereotypePolicyInteractionImpl interaction;
}
