/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalwatch.interaction;

import org.opales.covalent.hmi.action.ActionKey;

/**
 * Action identifiers registered by {@code OrbitalWatchInteraction}
 * with the HMI's {@code ActionManager}, and executed from the watch view's
 * "Clear" button.
 */
public enum OrbitalWatchActionKey implements ActionKey {

	/** Clears the log of recorded close approaches. */
	Clear;
}
