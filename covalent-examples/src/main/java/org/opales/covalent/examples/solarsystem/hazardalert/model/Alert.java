/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.hazardalert.model;

import org.opales.covalent.examples.solarsystem.hazardalert.controller.HazardAlertController;
import org.opales.covalent.examples.solarsystem.orbitalsystem.model.CloseApproach;

/**
 * One hazard alert raised by {@code HazardAlertController} from a filtered
 * {@link CloseApproach}, and tracked by {@code HazardAlertModel} until
 * acknowledged.
 *
 * @param id           a unique identifier for this alert, assigned at creation
 * @param approach     the close approach that raised this alert
 * @param acknowledged whether the user has acknowledged this alert
 */
public record Alert(String id, CloseApproach approach, boolean acknowledged) {

	/**
	 * Returns an acknowledged copy of this alert, leaving this one unchanged.
	 *
	 * @return a copy of this alert, acknowledged
	 */
	public Alert acknowledge() {
		return new Alert(this.id, this.approach, true);
	}
}
