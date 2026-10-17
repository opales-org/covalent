/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.hazardalert.view;

import org.opales.covalent.examples.solarsystem.hazardalert.interaction.HazardAlertInteraction;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Piloting commands for the hazard alert view, provided by
 * {@code HazardAlertView} and used by {@code HazardAlertInteraction} -
 * the same transient "busy" flag convention
 * {@code DatasetViewServices#setRegenerating(boolean)} uses, shown briefly
 * around the acknowledge action.
 */
@Services
public interface HazardAlertViewServices {

	/**
	 * Indicates whether an acknowledge-all is in progress.
	 *
	 * @param acknowledging {@code true} while an acknowledge is in progress
	 */
	void setAcknowledging(boolean acknowledging);
}
