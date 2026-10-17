/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.cockpit.interaction;

import org.opales.covalent.hmi.action.ActionKey;

/** The cockpit's actions. */
public enum CockpitActionKey implements ActionKey {

	/** Opens or closes the cockpit's panel - no payload. */
	Toggle,

	/** A lifecycle transition on one component or composite - payload {@link LifecycleRequest}. */
	Command,

	/** A lifecycle transition on the whole observed container - payload {@code LifecycleCommand}. */
	ContainerCommand,

	/** Enters a composite in the console - payload {@code Class<?>}, the composite. */
	EnterComposite,

	/** Goes back up in the console - payload {@code Integer}, the composites entered to keep. */
	ShowConsoleLevel
}
