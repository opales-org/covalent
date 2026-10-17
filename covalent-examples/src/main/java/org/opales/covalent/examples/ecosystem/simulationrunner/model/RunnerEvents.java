/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.simulationrunner.model;

import org.opales.covalent.examples.ecosystem.simulationrunner.view.RunnerView;

import org.opales.covalent.container.component.annotations.Events;

/**
 * Pacing notifications, produced by {@code RunnerModel} and consumed by
 * {@code RunnerView} to drive the elapsed-time readout and the run controls'
 * play/pause state.
 */
@Events
public interface RunnerEvents {

	/**
	 * Fired after every simulation step (whether from continuous running or
	 * a manual single step).
	 *
	 * @param elapsedSimulationTime the simulation time reached
	 */
	void onTick(double elapsedSimulationTime);

	/**
	 * Fired whenever continuous running starts or stops.
	 *
	 * @param running {@code true} if continuous running just started,
	 *                {@code false} if it just stopped
	 */
	void onRunningChanged(boolean running);
}
