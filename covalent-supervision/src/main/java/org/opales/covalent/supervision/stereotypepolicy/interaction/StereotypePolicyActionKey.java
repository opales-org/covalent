/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.stereotypepolicy.interaction;

import org.opales.covalent.hmi.action.ActionKey;

/** ActionKeys registered by {@code StereotypePolicyInteraction} at its own {@code @Activation}. */
public enum StereotypePolicyActionKey implements ActionKey {

	/** The Add gesture - {@code StereotypeRule} payload (only its ending and stereotype are read). */
	AddStereotype,

	/**
	 * Removing one line of the table, a built-in one included - {@code String} payload (the
	 * line's name ending).
	 */
	RemoveStereotype,

	/** Bringing the table back to the built-in rules only, as at installation. No payload. */
	ResetStereotypes,

	/**
	 * Opens this function's panel, or brings it forward when it is already open - from the
	 * Settings page. No payload.
	 */
	Show
}
