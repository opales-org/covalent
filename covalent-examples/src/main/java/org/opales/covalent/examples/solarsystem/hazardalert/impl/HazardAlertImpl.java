/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.hazardalert.impl;

import org.opales.covalent.examples.solarsystem.hazardalert.HazardAlert;
import org.opales.covalent.examples.solarsystem.hazardalert.controller.impl.HazardAlertControllerImpl;
import org.opales.covalent.examples.solarsystem.hazardalert.interaction.impl.HazardAlertInteractionImpl;
import org.opales.covalent.examples.solarsystem.hazardalert.model.impl.HazardAlertModelImpl;
import org.opales.covalent.examples.solarsystem.hazardalert.view.impl.HazardAlertViewImpl;

import org.opales.covalent.container.component.annotations.Implementation;

/**
 * The {@code HazardAlert} composite: deploys its four MVIC parts together.
 * Only this class needs to be passed to {@code container.deployComponents(...)}.
 */
@Implementation
public class HazardAlertImpl implements HazardAlert {

	@Implementation
	private HazardAlertModelImpl model;

	@Implementation
	private HazardAlertControllerImpl controller;

	@Implementation
	private HazardAlertViewImpl view;

	@Implementation
	private HazardAlertInteractionImpl interaction;
}
