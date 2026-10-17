/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.hazardalert.controller;

import org.opales.covalent.examples.solarsystem.orbitalsystem.model.CloseApproachEvents;
import org.opales.covalent.examples.solarsystem.userconfiguration.UserConfiguration;

import org.opales.covalent.container.component.annotations.Parameters;

/**
 * The hazard filter's own live thresholds, offered by
 * {@code HazardAlertController} and read directly by its
 * {@link CloseApproachEvents.CloseApproachEventsFilter} on every
 * notification - tuning either threshold changes what passes the filter
 * without re-subscribing. Accessed externally by {@code UserConfiguration}.
 */
@Parameters
public interface HazardParameters {

	/**
	 * Returns the distance threshold below which an approach may be hazardous.
	 *
	 * @return the current distance threshold: an approach closer than this may qualify
	 */
	double getDistanceThreshold();

	/**
	 * Sets the distance threshold, read by the hazard filter on its next notification.
	 *
	 * @param distanceThreshold the new distance threshold to apply immediately
	 */
	void setDistanceThreshold(double distanceThreshold);

	/**
	 * Returns the size threshold that at least one of the two bodies must reach.
	 *
	 * @return the current size threshold: at least one of the two bodies'
	 *         radii must reach this for the approach to qualify
	 */
	double getSizeThreshold();

	/**
	 * Sets the size threshold, read by the hazard filter on its next notification.
	 *
	 * @param sizeThreshold the new size threshold to apply immediately
	 */
	void setSizeThreshold(double sizeThreshold);
}
