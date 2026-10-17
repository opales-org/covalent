/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.userconfiguration.view;

import org.opales.covalent.examples.solarsystem.userconfiguration.interaction.UserConfigurationInteraction;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Piloting commands for the configuration view, provided by
 * {@code UserConfigurationView} and used by
 * {@code UserConfigurationInteraction} to keep the displayed values in
 * sync with what was actually applied.
 */
@Services
public interface UserConfigurationViewServices {

	/**
	 * Shows the gravitational constant on its slider.
	 *
	 * @param gravitationalConstant the gravitational constant to display
	 */
	void displayGravitationalConstant(double gravitationalConstant);

	/**
	 * Shows the time scale on its slider.
	 *
	 * @param timeScale the simulation time scale to display
	 */
	void displayTimeScale(double timeScale);

	/**
	 * Shows the hazard distance threshold on its slider.
	 *
	 * @param distanceThreshold the hazard distance threshold to display
	 */
	void displayDistanceThreshold(double distanceThreshold);

	/**
	 * Shows the hazard size threshold on its slider.
	 *
	 * @param sizeThreshold the hazard size threshold to display
	 */
	void displaySizeThreshold(double sizeThreshold);
}
