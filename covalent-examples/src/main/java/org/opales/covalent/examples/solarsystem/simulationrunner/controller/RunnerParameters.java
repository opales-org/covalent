/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.simulationrunner.controller;

import org.opales.covalent.examples.solarsystem.simulationrunner.interaction.RunnerInteraction;
import org.opales.covalent.examples.solarsystem.userconfiguration.UserConfiguration;

import org.opales.covalent.container.component.annotations.Parameters;

/**
 * User-editable pacing of the simulation itself, offered by
 * {@code RunnerController} and accessed by {@code UserConfiguration} and
 * {@code RunnerInteraction} alike.
 */
@Parameters
public interface RunnerParameters {

	/**
	 * Returns the time scale applied to every simulation tick.
	 *
	 * @return the current time scale: the factor applied to the base step of 0.005
	 *         simulation time units advanced at each real-time tick (1.0 by default)
	 */
	double getTimeScale();

	/**
	 * Sets the time scale applied to every simulation tick.
	 *
	 * @param timeScale the new time scale (factor applied to the base step),
	 *                  used from the next tick on
	 */
	void setTimeScale(double timeScale);
}
