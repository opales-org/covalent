/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.hazardalert.model;

import org.opales.covalent.examples.solarsystem.hazardalert.controller.HazardAlertController;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Records the currently active and acknowledged hazard alerts, provided by
 * {@code HazardAlertModel} and used by {@code HazardAlertController}. The
 * Model carries no escalation logic of its own: each method here just
 * stores the given state and fires the matching {@link HazardAlertEvents}
 * notification.
 */
@Services
public interface HazardAlertModelServices {

	/**
	 * Adds a newly raised alert, and fires
	 * {@link HazardAlertEvents#onAlertRaised(Alert)}.
	 *
	 * @param alert the alert to add
	 */
	void raiseAlert(Alert alert);

	/**
	 * Marks every currently active alert as acknowledged, and fires
	 * {@link HazardAlertEvents#onAllAcknowledged()}.
	 */
	void acknowledgeAll();

	/**
	 * Returns the alerts raised and not yet acknowledged.
	 *
	 * @return every currently active (not yet acknowledged) alert
	 */
	Alert[] getActiveAlerts();
}
