/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.simulationrunner.model.impl;

import org.opales.covalent.examples.ecosystem.simulationrunner.model.RunnerEvents;
import org.opales.covalent.examples.ecosystem.simulationrunner.model.RunnerModel;
import org.opales.covalent.examples.ecosystem.simulationrunner.model.RunnerModelServices;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;

/**
 * The simulation run's Model: records the run's pacing state (elapsed time,
 * running flag) and fires the matching {@link RunnerEvents} notification for
 * every call. Carries no scheduling logic of its own.
 */
@Implementation
public class RunnerModelImpl implements RunnerModel, RunnerModelServices {

	@ProducedEvents
	private RunnerEvents produceRunnerEvents;

	private volatile double elapsedSimulationTime;

	private volatile boolean running;

	@Override
	public void recordTick(final double elapsedSimulationTime) {
		this.elapsedSimulationTime = elapsedSimulationTime;
		this.produceRunnerEvents.onTick(elapsedSimulationTime);
	}

	@Override
	public void recordRunningChanged(final boolean running) {
		this.running = running;
		this.produceRunnerEvents.onRunningChanged(running);
	}

	@Override
	public double getElapsedSimulationTime() {
		return this.elapsedSimulationTime;
	}

	@Override
	public boolean isRunning() {
		return this.running;
	}
}
