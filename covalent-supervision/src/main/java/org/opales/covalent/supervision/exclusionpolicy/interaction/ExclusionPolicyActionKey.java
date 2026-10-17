/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.exclusionpolicy.interaction;

import org.opales.covalent.hmi.action.ActionKey;

/** ActionKeys registered by {@code ExclusionPolicyInteraction} at its own {@code @Activation}. */
public enum ExclusionPolicyActionKey implements ActionKey {

	/** Removing one row from the checklist - {@code List<Class<?>>} payload (the whole remaining list). */
	SetExcludedComponents,

	/** The Add gesture, once a type name resolves - {@code Class<?>} payload. */
	AddExcludedComponent,

	/** The "Exclude internal components" switch - {@code Boolean} payload. */
	SetExcludeInternalComponents,

	/**
	 * SupervisionHmi's permanent toolbar toggle button/menu item for this
	 * function's panel - closed by default. No payload.
	 */
	Toggle
}
