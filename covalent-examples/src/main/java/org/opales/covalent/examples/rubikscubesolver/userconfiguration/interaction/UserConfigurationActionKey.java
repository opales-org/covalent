/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.userconfiguration.interaction;

import org.opales.covalent.hmi.action.ActionKey;

/**
 * Action identifiers registered by {@code UserConfigurationInteraction}
 * with the HMI's {@code ActionManager}, and executed from the view's
 * spinners. See {@code DatasetActionKey} in the Neural Network Trainer demo
 * for what an {@code ActionKey} enum constant is and why this indirection is
 * used instead of the View calling the Interaction Controller directly.
 */
public enum UserConfigurationActionKey implements ActionKey {

	/** Sets the Kociemba phase 2 maximum depth; executed with an {@code Integer}. */
	SetMaxDepth,

	/** Sets the solution playback speed, in moves per second; executed with an {@code Integer}. */
	SetPlaybackSpeed;
}
