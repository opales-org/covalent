/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.supervisionhmi.interaction;

import org.opales.covalent.hmi.action.ActionKey;

/**
 * Action identifiers registered by {@code SupervisionHmiInteraction}
 * with the HMI's {@code ActionManager}: {@link #TabSelected} is triggered from
 * {@code SupervisionHmiView}'s own tab pane, {@link #ShowWindow} from outside the supervisor.
 */
public enum SupervisionHmiActionKey implements ActionKey {

	/**
	 * A tab of the supervisor became selected - payload {@code SupervisorTabDescriptor}, the selected tab's descriptor.
	 */
	TabSelected,

	/**
	 * The supervisor's window is shown again, in front of the other windows - no payload. Executed by
	 * {@code SupervisionHmiInteraction}; triggered from outside the supervisor's container each time the supervisor
	 * is summoned after its first start.
	 */
	ShowWindow;
}
