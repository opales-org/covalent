/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.simulationrunner.interaction;

import org.opales.covalent.hmi.action.ActionKey;

/**
 * Action identifiers registered by {@code RunnerInteraction} with the
 * HMI's {@code ActionManager}, and executed from the runner view's controls
 * (start/pause/step/reset buttons and time-scale field).
 */
public enum RunnerActionKey implements ActionKey {

	/** Starts or resumes continuous running. */
	Start,
	/** Pauses continuous running. */
	Pause,
	/** Advances the simulation by exactly one step. */
	Step,
	/** Resets the simulation to its initial state. */
	Reset,
	/** Sets the time scale; takes the time scale, a {@code Double}, as argument. */
	SetTimeScale;
}
