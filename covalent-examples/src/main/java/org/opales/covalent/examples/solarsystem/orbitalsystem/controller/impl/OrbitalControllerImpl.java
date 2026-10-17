/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalsystem.controller.impl;

import org.opales.covalent.examples.solarsystem.orbitalsystem.Body;
import org.opales.covalent.examples.solarsystem.orbitalsystem.controller.BodyDetails;
import org.opales.covalent.examples.solarsystem.orbitalsystem.controller.OrbitalController;
import org.opales.covalent.examples.solarsystem.orbitalsystem.controller.OrbitalControllerServices;
import org.opales.covalent.examples.solarsystem.orbitalsystem.controller.OrbitalParameters;
import org.opales.covalent.examples.solarsystem.orbitalsystem.controller.OrbitalSystemServices;
import org.opales.covalent.examples.solarsystem.orbitalsystem.model.CloseApproach;
import org.opales.covalent.examples.solarsystem.orbitalsystem.model.OrbitalModelServices;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Initialization;

/**
 * The orbital system's Controller: owns the actual gravity computation
 * (Newtonian n-body, toy unit system - not SI) and close-approach
 * detection. The Model only stores state; all physics happens here,
 * mirroring how {@code NetworkController} owns the network's forward/backward
 * math while {@code NetworkModel} only stores weights.
 * <p>
 * Uses semi-implicit Euler (Euler-Cromer) integration: stable enough for
 * visually convincing orbits at a demo's scale, without a full symplectic
 * or adaptive-step integrator.
 */
@Implementation
public class OrbitalControllerImpl implements OrbitalController, OrbitalControllerServices, OrbitalSystemServices, OrbitalParameters {

	private static final String SUN_NAME = "Sun";

	private static final double SUN_MASS = 5000.0;

	private static final double SUN_RADIUS = 1.0;

	/** name, orbit radius, mass, body radius - the canonical starting configuration. */
	private static final Object[][] PLANETS = {
			{ "Mercury", 4.0, 4.0, 0.18 },
			{ "Venus", 6.0, 9.0, 0.24 },
			{ "Earth", 8.5, 10.0, 0.25 },
			{ "Mars", 11.5, 6.0, 0.20 },
	};

	private static final double DEFAULT_GRAVITATIONAL_CONSTANT = 1.0;

	/**
	 * Below this distance, two bodies are considered to be having a "close
	 * approach" - comfortably under the 2.0-unit minimum gap between the
	 * canonical circular orbits above, so it never fires under undisturbed
	 * circular motion; only perturbation (e.g. a live change to the
	 * gravitational constant) brings two bodies this close.
	 */
	private static final double CLOSE_APPROACH_THRESHOLD = 1.5;

	private static final double COMET_MASS = 0.05;

	private static final double COMET_RADIUS = 0.15;

	/** Perihelion band: comfortably inside Mercury's 4.0-unit orbit. */
	private static final double COMET_PERIHELION_MIN = 2.0;

	private static final double COMET_PERIHELION_MAX = 3.5;

	/** Aphelion band: comfortably beyond Mars' 11.5-unit orbit. */
	private static final double COMET_APHELION_MIN = 12.0;

	private static final double COMET_APHELION_MAX = 13.5;

	@UsedServices
	private OrbitalModelServices useOrbitalModelServices;

	private volatile double gravitationalConstant = DEFAULT_GRAVITATIONAL_CONSTANT;

	/**
	 * Tracks, per unordered body-name pair, whether that pair is currently
	 * within {@link #CLOSE_APPROACH_THRESHOLD} - own working state for
	 * detecting the transition (entering the zone, not merely lingering in
	 * it), the same kind of controller-internal bookkeeping
	 * {@code TrainingControllerImpl} keeps for its own scheduling.
	 */
	private final Map<String, Boolean> pairWasClose = new HashMap<>();

	/**
	 * Guards the read-modify-write sequences on the system and on {@link #pairWasClose}: a tick
	 * ({@code advance}), the user adding a comet and a reset run on different threads.
	 */
	private final Object stateLock = new Object();

	/** Names successive comets uniquely ({@code Comet-1}, {@code Comet-2}, ...). */
	private final AtomicInteger cometCount = new AtomicInteger(0);

	/** Builds the initial system at initialization. */
	@Initialization
	public void initializeSystem() {
		this.reset();
	}

	@Override
	public void reset() {
		synchronized (this.stateLock) {
			this.pairWasClose.clear();
			this.cometCount.set(0);
			Body[] bodies = buildInitialBodies();
			this.useOrbitalModelServices.updateBodies(bodies, 0.0);
		}
	}

	/**
	 * Adds one comet to the simulation on demand, on a randomly generated
	 * but strongly eccentric orbit: perihelion inside Mercury's orbit,
	 * aphelion beyond Mars', so it crosses every planet's orbit rather than
	 * waiting for numerical drift to bring two bodies close (see
	 * {@link #CLOSE_APPROACH_THRESHOLD}'s javadoc). Speed at perihelion is
	 * computed from the vis-viva equation for a two-body ellipse around the
	 * Sun; starting angle and orbital direction (prograde or retrograde) are
	 * both randomized so repeated clicks give different encounters.
	 */
	@Override
	public void addComet() {
		double perihelion = COMET_PERIHELION_MIN
				+ ThreadLocalRandom.current().nextDouble() * (COMET_PERIHELION_MAX - COMET_PERIHELION_MIN);
		double aphelion = COMET_APHELION_MIN
				+ ThreadLocalRandom.current().nextDouble() * (COMET_APHELION_MAX - COMET_APHELION_MIN);
		double semiMajorAxis = (perihelion + aphelion) / 2.0;
		double theta = ThreadLocalRandom.current().nextDouble() * 2.0 * Math.PI;
		boolean prograde = ThreadLocalRandom.current().nextBoolean();

		double speedAtPerihelion = Math.sqrt(
				this.gravitationalConstant * SUN_MASS * (2.0 / perihelion - 1.0 / semiMajorAxis));
		double tangentX = prograde ? -Math.sin(theta) : Math.sin(theta);
		double tangentY = prograde ? Math.cos(theta) : -Math.cos(theta);

		String name = "Comet-" + this.cometCount.incrementAndGet();
		Body comet = new Body(
				name, COMET_MASS, COMET_RADIUS,
				perihelion * Math.cos(theta), perihelion * Math.sin(theta),
				speedAtPerihelion * tangentX, speedAtPerihelion * tangentY);

		synchronized (this.stateLock) {
			Body[] current = this.useOrbitalModelServices.getBodies();
			Body[] updated = Arrays.copyOf(current, current.length + 1);
			updated[current.length] = comet;
			this.useOrbitalModelServices.updateBodies(updated, this.useOrbitalModelServices.getSimulationTime());
		}
	}

	@Override
	public void advance(final double dt) {
		synchronized (this.stateLock) {
			this.advanceLocked(dt);
		}
	}

	private void advanceLocked(final double dt) {
		Body[] current = this.useOrbitalModelServices.getBodies();
		int n = current.length;

		double[] ax = new double[n];
		double[] ay = new double[n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (i == j) {
					continue;
				}
				double dx = current[j].x() - current[i].x();
				double dy = current[j].y() - current[i].y();
				double distanceSquared = Math.max(dx * dx + dy * dy, 1e-6);
				double distance = Math.sqrt(distanceSquared);
				double accelerationMagnitude = this.gravitationalConstant * current[j].mass() / distanceSquared;
				ax[i] += accelerationMagnitude * dx / distance;
				ay[i] += accelerationMagnitude * dy / distance;
			}
		}

		Body[] updated = new Body[n];
		for (int i = 0; i < n; i++) {
			Body body = current[i];
			double newVx = body.vx() + ax[i] * dt;
			double newVy = body.vy() + ay[i] * dt;
			double newX = body.x() + newVx * dt;
			double newY = body.y() + newVy * dt;
			updated[i] = new Body(body.name(), body.mass(), body.radius(), newX, newY, newVx, newVy);
		}

		double newSimulationTime = this.useOrbitalModelServices.getSimulationTime() + dt;
		this.useOrbitalModelServices.updateBodies(updated, newSimulationTime);

		this.detectCloseApproaches(updated, newSimulationTime);
	}

	private void detectCloseApproaches(final Body[] bodies, final double simulationTime) {
		for (int i = 0; i < bodies.length; i++) {
			for (int j = i + 1; j < bodies.length; j++) {
				Body a = bodies[i];
				Body b = bodies[j];
				String pairKey = pairKey(a.name(), b.name());
				double dx = b.x() - a.x();
				double dy = b.y() - a.y();
				double distance = Math.sqrt(dx * dx + dy * dy);
				boolean isClose = distance < CLOSE_APPROACH_THRESHOLD;
				boolean wasClose = Boolean.TRUE.equals(this.pairWasClose.get(pairKey));
				if (isClose && !wasClose) {
					this.useOrbitalModelServices.recordCloseApproach(new CloseApproach(
							a.name(), b.name(), distance, a.radius(), b.radius(), simulationTime));
				}
				this.pairWasClose.put(pairKey, isClose);
			}
		}
	}

	private static String pairKey(final String nameA, final String nameB) {
		return nameA.compareTo(nameB) <= 0 ? nameA + "|" + nameB : nameB + "|" + nameA;
	}

	@Override
	public BodyDetails getBodyDetails(final String bodyName) {
		Body[] bodies = this.useOrbitalModelServices.getBodies();
		Body sun = null;
		Body selected = null;
		for (Body body : bodies) {
			if (SUN_NAME.equals(body.name())) {
				sun = body;
			}
			if (body.name().equals(bodyName)) {
				selected = body;
			}
		}
		if (selected == null) {
			return null;
		}
		double distanceFromSun = 0.0;
		if (sun != null && sun != selected) {
			double dx = selected.x() - sun.x();
			double dy = selected.y() - sun.y();
			distanceFromSun = Math.sqrt(dx * dx + dy * dy);
		}
		double speed = Math.sqrt(selected.vx() * selected.vx() + selected.vy() * selected.vy());
		return new BodyDetails(selected.name(), selected.mass(), selected.radius(), distanceFromSun, speed);
	}

	@Override
	public double getGravitationalConstant() {
		return this.gravitationalConstant;
	}

	@Override
	public void setGravitationalConstant(final double gravitationalConstant) {
		this.gravitationalConstant = gravitationalConstant;
	}

	/**
	 * Builds the canonical starting configuration: the sun at the origin
	 * plus every planet in {@link #PLANETS} on a circular orbit (velocity
	 * from {@code v = sqrt(G * sunMass / r)}), all coplanar and initially
	 * aligned along the X axis for simplicity. The sun's own velocity is set
	 * so the system's total momentum is zero, keeping the barycenter
	 * stationary instead of slowly drifting across the view.
	 */
	private Body[] buildInitialBodies() {
		Body[] bodies = new Body[PLANETS.length + 1];
		double sunMomentumX = 0.0;
		double sunMomentumY = 0.0;
		for (int i = 0; i < PLANETS.length; i++) {
			String name = (String) PLANETS[i][0];
			double orbitRadius = (double) PLANETS[i][1];
			double mass = (double) PLANETS[i][2];
			double radius = (double) PLANETS[i][3];
			double speed = Math.sqrt(this.gravitationalConstant * SUN_MASS / orbitRadius);
			// Start every planet on the positive X axis, moving in +Y: a
			// simple, deterministic initial alignment (not physically
			// meaningful, purely a fixed starting picture).
			double x = orbitRadius;
			double y = 0.0;
			double vx = 0.0;
			double vy = speed;
			bodies[i + 1] = new Body(name, mass, radius, x, y, vx, vy);
			sunMomentumX -= mass * vx;
			sunMomentumY -= mass * vy;
		}
		bodies[0] = new Body(SUN_NAME, SUN_MASS, SUN_RADIUS, 0.0, 0.0,
				sunMomentumX / SUN_MASS, sunMomentumY / SUN_MASS);
		return bodies;
	}
}
