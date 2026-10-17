/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.containermanager.interaction;

import org.opales.covalent.hmi.action.ActionKey;

/**
 * Action identifiers registered by {@code ContainerManagerInteraction}
 * with the HMI's {@code ActionManager}, triggered from
 * {@code ContainerManagerView}'s selector widget and from the supervisor
 * shell's header and Preferences panel.
 */
public enum ContainerManagerActionKey implements ActionKey {

	/** Connects to a managed container - {@code String} payload, the container's name. */
	Connect,

	/**
	 * Switches data flows on or off for the observed container, i.e. its
	 * {@code instrumented} parameter - {@code Boolean} payload. Also a value
	 * key: its current value is published on every change, including the
	 * reset to {@link #SetFlowsEnabledByDefault}'s value on each connection.
	 */
	SetFlowsEnabled,

	/** Whether data flows are switched on when connecting to a container - {@code Boolean} payload. */
	SetFlowsEnabledByDefault;
}
