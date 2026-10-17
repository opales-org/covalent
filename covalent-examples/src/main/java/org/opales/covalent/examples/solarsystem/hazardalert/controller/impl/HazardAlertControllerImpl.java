/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.hazardalert.controller.impl;

import org.opales.covalent.examples.solarsystem.hazardalert.controller.HazardAlertController;
import org.opales.covalent.examples.solarsystem.hazardalert.controller.HazardAlertControllerServices;
import org.opales.covalent.examples.solarsystem.hazardalert.controller.HazardParameters;
import org.opales.covalent.examples.solarsystem.hazardalert.model.Alert;
import org.opales.covalent.examples.solarsystem.hazardalert.model.HazardAlertModelServices;
import org.opales.covalent.examples.solarsystem.orbitalsystem.Body;
import org.opales.covalent.examples.solarsystem.orbitalsystem.OrbitalSystem;
import org.opales.covalent.examples.solarsystem.orbitalsystem.model.CloseApproach;
import org.opales.covalent.examples.solarsystem.orbitalsystem.model.CloseApproachEvents;

import java.util.concurrent.atomic.AtomicLong;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;

/**
 * The hazard alert's Controller: subscribes to {@link CloseApproachEvents}
 * manually, with a filter built from this function's own live
 * {@link HazardParameters} (distance + size), and raises an {@link Alert}
 * for every notification that passes - the filter reads
 * {@link #distanceThreshold} / {@link #sizeThreshold} directly, so tuning
 * either takes effect immediately without re-subscribing. Ignores
 * {@link #onSystemAdvanced(Body[], double)}: continuous positions are
 * {@code OrbitalSystem}'s own View's concern, not this function's.
 */
@Implementation
public class HazardAlertControllerImpl implements HazardAlertController, HazardAlertControllerServices,
		HazardParameters, CloseApproachEvents {

	private static final double DEFAULT_DISTANCE_THRESHOLD = 1.0;

	private static final double DEFAULT_SIZE_THRESHOLD = 0.22;

	/**
	 * Minimum "surfaces touching" distance used by {@link HazardFilter},
	 * regardless of the two bodies' own real radii - calibrated on what the
	 * orrery actually shows the user, not just the underlying physics.
	 * {@code OrbitalViewImpl} floors every rendered body at a radius of 3
	 * pixels, and draws the other bodies at 9 pixels per unit of real radius:
	 * every planet and comet in this demo (real radius under 0.33) therefore
	 * renders as the same 3-pixel dot, only the Sun being larger. The orrery
	 * shows 28 simulation units over 420 pixels, that is 15 pixels per unit,
	 * so two such dots visually touch once their real distance drops under
	 * 6 pixels, which is 0.4 simulation units. Without this floor, a comet-comet pass that clearly looks like a
	 * collision on screen (real radii only 0.15 each, so an unfiltered
	 * {@code approach.distance() <= approach.bodyARadius() +
	 * approach.bodyBRadius()} check would need distance &lt;= 0.3) would be
	 * silently filtered out - a Hazard alert that never fires on what the
	 * user just watched happen defeats the point of this demo.
	 */
	private static final double MIN_VISUAL_COLLISION_DISTANCE = 0.4;

	@UsedServices
	private HazardAlertModelServices useHazardAlertModelServices;

	@UsedEventsSubscribe
	private CloseApproachEvents.CloseApproachEventsSubscribe useCloseApproachEventsSubscribe;

	private final AtomicLong nextAlertId = new AtomicLong(1);

	private volatile double distanceThreshold = DEFAULT_DISTANCE_THRESHOLD;

	private volatile double sizeThreshold = DEFAULT_SIZE_THRESHOLD;

	/** Subscribes to {@link CloseApproachEvents} with a hazard filter at activation. */
	@Activation
	public void subscribeFiltered() {
		this.useCloseApproachEventsSubscribe.subscribe(this, new HazardFilter());
	}

	@Override
	public void onSystemAdvanced(final Body[] bodies, final double simulationTime) {
		// Not this function's concern - see class javadoc.
	}

	@Override
	public void onCloseApproachDetected(final CloseApproach approach) {
		String id = "hazard-" + this.nextAlertId.getAndIncrement();
		this.useHazardAlertModelServices.raiseAlert(new Alert(id, approach, false));
	}

	@Override
	public void acknowledgeAll() {
		this.useHazardAlertModelServices.acknowledgeAll();
	}

	@Override
	public double getDistanceThreshold() {
		return this.distanceThreshold;
	}

	@Override
	public void setDistanceThreshold(final double distanceThreshold) {
		this.distanceThreshold = distanceThreshold;
	}

	@Override
	public double getSizeThreshold() {
		return this.sizeThreshold;
	}

	@Override
	public void setSizeThreshold(final double sizeThreshold) {
		this.sizeThreshold = sizeThreshold;
	}

	/**
	 * Reads {@link #distanceThreshold} / {@link #sizeThreshold} directly on
	 * every call (a non-static inner class, capturing the outer Controller),
	 * so a live threshold change applies to the very next notification.
	 */
	private final class HazardFilter implements CloseApproachEvents.CloseApproachEventsFilter {

		@Override
		public boolean filterCloseApproachDetected(final CloseApproach approach) {
			boolean closeEnough = approach.distance() <= HazardAlertControllerImpl.this.distanceThreshold;
			boolean largeEnough = Math.max(approach.bodyARadius(), approach.bodyBRadius())
					>= HazardAlertControllerImpl.this.sizeThreshold;
			// An actual collision - the two bodies visibly touching or
			// overlapping, on screen or physically - is always hazardous,
			// regardless of sizeThreshold: that threshold exists to filter
			// out harmless flybys of small bodies, not to exclude a real
			// impact between two small ones. Uses whichever distance is
			// larger, the bodies' real radii sum or the visual floor every
			// body renders at (see MIN_VISUAL_COLLISION_DISTANCE), so a
			// collision the user actually sees always raises an alert, even
			// when the real bodies involved are individually too small to
			// touch at that same distance.
			boolean actualCollision = approach.distance()
					<= Math.max(MIN_VISUAL_COLLISION_DISTANCE, approach.bodyARadius() + approach.bodyBRadius());
			return !((closeEnough && largeEnough) || actualCollision);
		}
	}
}
