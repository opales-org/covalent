/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.simulationrunner.controller;

import org.opales.covalent.examples.ecosystem.simulationrunner.interaction.RunnerInteraction;
import org.opales.covalent.examples.ecosystem.userconfiguration.UserConfiguration;

import org.opales.covalent.container.component.annotations.Parameters;

/**
 * User-editable pacing of the simulation itself, offered by
 * {@code RunnerController} and accessed by {@code UserConfiguration} and
 * {@code RunnerInteraction} alike.
 */
@Parameters
public interface RunnerParameters {

	/**
	 * Returns the factor applied to the base simulation step of every tick.
	 * @return the current time scale (simulation time units advanced per real-time tick)
	 */
	double getTimeScale();

	/**
	 * Sets the time scale, applied from the next tick on.
	 * @param timeScale the time scale to apply, 1.0 being the default pace
	 */
	void setTimeScale(double timeScale);
}
