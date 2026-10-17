/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalsystem.interaction;

import org.opales.covalent.hmi.action.ActionKey;

/**
 * Action identifiers registered by {@code OrbitalInteraction} with the
 * HMI's {@code ActionManager}, and executed from the orrery view's controls
 * (clicking a body to select it, and the Add Comet button).
 */
public enum OrbitalActionKey implements ActionKey {

	/** Selects a body, given its name, to display its details. */
	SelectBody,
	/** Adds one comet to the simulation. */
	AddComet;
}
