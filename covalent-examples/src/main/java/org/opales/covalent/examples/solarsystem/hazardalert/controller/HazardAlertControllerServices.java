/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.hazardalert.controller;

import org.opales.covalent.examples.solarsystem.hazardalert.HazardAlert;
import org.opales.covalent.examples.solarsystem.hazardalert.interaction.HazardAlertInteraction;

import org.opales.covalent.container.component.annotations.Services;

/**
 * User-facing alert control, provided by {@code HazardAlertController} and
 * used by {@code HazardAlertInteraction} to acknowledge alerts from
 * the UI. Internal to the {@code HazardAlert} function - nothing external
 * needs it.
 */
@Services
public interface HazardAlertControllerServices {

	/** Acknowledges every currently active alert. */
	void acknowledgeAll();
}
