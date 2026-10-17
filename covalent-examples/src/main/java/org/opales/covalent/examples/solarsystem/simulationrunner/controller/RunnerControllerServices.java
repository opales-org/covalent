/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.simulationrunner.controller;

import org.opales.covalent.examples.solarsystem.orbitalsystem.OrbitalSystem;
import org.opales.covalent.examples.solarsystem.simulationrunner.SimulationRunner;
import org.opales.covalent.examples.solarsystem.simulationrunner.interaction.RunnerInteraction;

import org.opales.covalent.container.component.annotations.Services;

/**
 * User-facing run control, provided by {@code RunnerController} and used by
 * {@code RunnerInteraction} to drive the simulation from the UI
 * (start/pause/step/reset). Internal to the {@code SimulationRunner}
 * function - not shared with any other function, so no separate public
 * "RunnerServices" is needed the way {@code OrbitalSystem} needs both an
 * internal and a public Services interface.
 */
@Services
public interface RunnerControllerServices {

	/** Starts (or resumes) continuous simulation running. */
	void start();

	/** Pauses continuous running after the current step completes. */
	void pause();

	/** Advances the simulation by exactly one step, regardless of run/pause state. */
	void step();

	/** Resets the orbital system and the elapsed time back to their initial state. */
	void reset();

	/**
	 * Reports whether continuous running is currently active.
	 *
	 * @return {@code true} if the simulation is actively progressing, {@code false} if paused
	 */
	boolean isRunning();
}
