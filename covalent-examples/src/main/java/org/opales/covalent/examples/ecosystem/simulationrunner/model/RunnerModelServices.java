/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.simulationrunner.model;

import org.opales.covalent.examples.ecosystem.simulationrunner.controller.RunnerController;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Records the simulation run's pacing state, provided by {@code RunnerModel}
 * and used by {@code RunnerController}. The Model carries no scheduling
 * logic of its own: each method here just stores the given state and fires
 * the matching {@link RunnerEvents} notification.
 */
@Services
public interface RunnerModelServices {

	/**
	 * Records that the simulation reached a new elapsed time, and fires
	 * {@link RunnerEvents#onTick(double)}.
	 *
	 * @param elapsedSimulationTime the simulation time reached
	 */
	void recordTick(double elapsedSimulationTime);

	/**
	 * Records a change in continuous-running state, and fires
	 * {@link RunnerEvents#onRunningChanged(boolean)}.
	 *
	 * @param running the new running state
	 */
	void recordRunningChanged(boolean running);

	/**
	 * Returns the elapsed simulation time last recorded by {@link #recordTick(double)}.
	 * @return the last recorded elapsed simulation time
	 */
	double getElapsedSimulationTime();

	/**
	 * Returns the running state last recorded by {@link #recordRunningChanged(boolean)}.
	 * @return the last recorded running state
	 */
	boolean isRunning();
}
