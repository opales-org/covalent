/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.hazardalert.interaction;

import org.opales.covalent.hmi.action.ActionKey;

/**
 * Action identifiers registered by {@code HazardAlertInteraction} with
 * the HMI's {@code ActionManager}, and executed from the hazard alert
 * view's controls.
 */
public enum HazardAlertActionKey implements ActionKey {

	/** Marks every active hazard alert as acknowledged. */
	AcknowledgeAll;
}
