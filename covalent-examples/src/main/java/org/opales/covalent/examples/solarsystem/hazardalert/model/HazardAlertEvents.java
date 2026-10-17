/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.hazardalert.model;

import org.opales.covalent.examples.solarsystem.hazardalert.view.HazardAlertView;

import org.opales.covalent.container.component.annotations.Events;

/**
 * Alert-change notifications, produced by {@code HazardAlertModel} and
 * consumed by {@code HazardAlertView} to refresh the alert list.
 */
@Events
public interface HazardAlertEvents {

	/**
	 * Fired when a new hazard alert is raised.
	 *
	 * @param alert the newly raised alert
	 */
	void onAlertRaised(Alert alert);

	/** Fired when every currently active alert is acknowledged. */
	void onAllAcknowledged();
}
