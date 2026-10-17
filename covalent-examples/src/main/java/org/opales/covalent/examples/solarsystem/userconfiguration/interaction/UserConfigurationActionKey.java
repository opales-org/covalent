/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.userconfiguration.interaction;

import org.opales.covalent.hmi.action.ActionKey;

/**
 * Action identifiers registered by {@code UserConfigurationInteraction}
 * with the HMI's {@code ActionManager}, and executed from the view instead
 * of calling the Interaction Controller directly.
 */
public enum UserConfigurationActionKey implements ActionKey {

	/** Sets the gravitational constant, given as a {@code Double}. */
	SetGravitationalConstant,
	/** Sets the simulation time scale, given as a {@code Double}. */
	SetTimeScale,
	/** Sets the hazard distance threshold, given as a {@code Double}. */
	SetDistanceThreshold,
	/** Sets the hazard size threshold, given as a {@code Double}. */
	SetSizeThreshold;
}
