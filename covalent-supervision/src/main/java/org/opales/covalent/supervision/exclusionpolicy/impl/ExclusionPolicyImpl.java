/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.exclusionpolicy.impl;

import org.opales.covalent.supervision.exclusionpolicy.ExclusionPolicy;
import org.opales.covalent.supervision.exclusionpolicy.controller.impl.ExclusionPolicyControllerImpl;
import org.opales.covalent.supervision.exclusionpolicy.interaction.impl.ExclusionPolicyInteractionImpl;
import org.opales.covalent.supervision.exclusionpolicy.model.impl.ExclusionPolicyModelImpl;
import org.opales.covalent.supervision.exclusionpolicy.view.impl.ExclusionPolicyViewImpl;

import org.opales.covalent.container.component.annotations.Implementation;

/**
 * The {@code ExclusionPolicy} composite: deploys its four MVIC parts
 * together. Only this class needs to be passed to
 * {@code container.deployComponents(...)}.
 */
@Implementation
public class ExclusionPolicyImpl implements ExclusionPolicy {

	/** Creates the composite; the container deploys its four MVIC parts as its own parts. */
	public ExclusionPolicyImpl() {
		// Nothing to initialize.
	}

	@Implementation
	private ExclusionPolicyModelImpl model;

	@Implementation
	private ExclusionPolicyControllerImpl controller;

	@Implementation
	private ExclusionPolicyViewImpl view;

	@Implementation
	private ExclusionPolicyInteractionImpl interaction;
}
