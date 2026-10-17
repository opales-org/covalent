/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.simulationrunner.interaction;

import org.opales.covalent.hmi.action.ActionKey;

/**
 * Action identifiers registered by {@code RunnerInteraction} with the
 * HMI's {@code ActionManager}, and executed from the runner view's controls
 * (start/pause/step/reset buttons and time-scale slider).
 */
public enum RunnerActionKey implements ActionKey {

	/** Starts continuous simulation running. */
	Start,
	/** Pauses continuous simulation running. */
	Pause,
	/** Advances the simulation by exactly one step. */
	Step,
	/** Resets the simulation to its initial state. */
	Reset,
	/** Sets the time scale, given as a {@code Double}. */
	SetTimeScale;
}
