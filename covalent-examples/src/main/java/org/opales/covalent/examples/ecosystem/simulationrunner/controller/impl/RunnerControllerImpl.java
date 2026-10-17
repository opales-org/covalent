/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.simulationrunner.controller.impl;

import org.opales.covalent.examples.ecosystem.ecosystem.Ecosystem;
import org.opales.covalent.examples.ecosystem.ecosystem.controller.EcosystemServices;
import org.opales.covalent.examples.ecosystem.simulationrunner.controller.RunnerController;
import org.opales.covalent.examples.ecosystem.simulationrunner.controller.RunnerControllerServices;
import org.opales.covalent.examples.ecosystem.simulationrunner.controller.RunnerParameters;
import org.opales.covalent.examples.ecosystem.simulationrunner.model.RunnerModelServices;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Initialization;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.lifecycle.Removal;

/**
 * The simulation run's Controller: advances {@code Ecosystem} at a
 * steady real-time pace, scaled by the user-editable {@link
 * RunnerParameters#getTimeScale()}, through
 * {@link EcosystemServices#advance(double)}. Mirrors
 * {@code TrainingControllerImpl}'s own scheduled-executor pacing pattern,
 * with a fixed pause between ticks (about 20 ticks per second, slightly
 * fewer when a tick takes long) rather than a fixed amount of work per tick.
 * <p>
 * <b>Lifecycle and background thread.</b> The ticks run on a thread of their own, outside
 * the container's control, so this class ties that thread to its lifecycle explicitly:
 * <ul>
 * <li>{@code @Initialization} creates the scheduler, once: initialization runs once per
 * instance, whereas activation and passivation may alternate any number of times.</li>
 * <li>Activation has nothing to do: the ticks start only on request.</li>
 * <li>{@code @Passivation} stops the ticks and waits for the tick in progress to end.
 * Passivation goes from the users to their providers: this function's own Model, then
 * {@code Ecosystem}, are passivated after this component, and a tick still running at that
 * point would call a passivated provider and fail. The wait lasts at most one tick, which
 * keeps passivation quick.</li>
 * <li>{@code @Removal} shuts the scheduler down: the instance is never activated again.</li>
 * </ul>
 * Keeping the scheduler across passivation is what lets the component run again once
 * reactivated; shutting it down there would leave it unable to schedule anything.
 */
@Implementation
public class RunnerControllerImpl implements RunnerController, RunnerControllerServices, RunnerParameters {

	private static final Logger log = Logger.getLogger(RunnerControllerImpl.class.getName());

	/** Real-time pause between the end of a tick and the start of the next: about 20 ticks per second. */
	private static final long TICK_INTERVAL_MILLIS = 50L;

	/** Simulation time advanced per tick at the default time scale of 1.0. */
	private static final double BASE_DT = 0.005;

	private static final double DEFAULT_TIME_SCALE = 1.0;

	@UsedServices
	private RunnerModelServices useRunnerModelServices;

	@UsedServices
	private EcosystemServices useEcosystemServices;

	private ScheduledExecutorService executor;

	/**
	 * Held for the whole of each tick. Taking it, as passivation does, waits for the tick in
	 * progress, if any, to end - without stopping the scheduler.
	 */
	private final Object tickLock = new Object();

	private volatile ScheduledFuture<?> scheduledTick;

	private volatile boolean running;

	private volatile double timeScale = DEFAULT_TIME_SCALE;

	/**
	 * Creates the scheduler running the ticks: once per instance, since initialization is not
	 * repeated when the component is passivated and activated again. A single thread keeps the
	 * ticks in sequence; it is a daemon so that it never holds the JVM open.
	 */
	@Initialization
	public void createScheduler() {
		this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "simulation-runner");
			thread.setDaemon(true);
			return thread;
		});
	}

	/**
	 * Stops the ticks, then waits for the tick in progress to end. Clearing {@code running}
	 * first, through {@link #pause()}, keeps any further tick from starting; taking
	 * {@link #tickLock} then waits for one already started. Once this returns, no tick can
	 * call a provider, all of which are passivated after this component. The scheduler itself
	 * is kept for a later reactivation.
	 */
	@Passivation
	public void stopTicking() {
		this.pause();
		synchronized (this.tickLock) {
			// Entered once the tick in progress, if any, is over.
		}
	}

	/**
	 * Shuts the scheduler down and ends its thread: removal is final, unlike passivation. The
	 * ticks are already stopped, by the passivation that always precedes removal.
	 */
	@Removal
	public void shutdownScheduler() {
		this.executor.shutdownNow();
	}

	// --- RunnerControllerServices -----------------------------------------

	@Override
	public void start() {
		if (this.running) {
			return;
		}
		this.running = true;
		this.useRunnerModelServices.recordRunningChanged(true);
		this.scheduleNextTick();
	}

	@Override
	public void pause() {
		boolean wasRunning = this.running;
		this.running = false;
		ScheduledFuture<?> pending = this.scheduledTick;
		if (pending != null) {
			pending.cancel(false);
			this.scheduledTick = null;
		}
		if (wasRunning) {
			this.useRunnerModelServices.recordRunningChanged(false);
		}
	}

	@Override
	public void step() {
		synchronized (this.tickLock) {
			this.runOneTick();
		}
	}

	@Override
	public void reset() {
		this.pause();
		// Under the lock, so that a tick already started cannot write its time over the reset.
		synchronized (this.tickLock) {
			this.useEcosystemServices.reset();
			this.useRunnerModelServices.recordTick(0.0);
		}
	}

	@Override
	public boolean isRunning() {
		return this.running;
	}

	// --- RunnerParameters ---------------------------------------------------

	@Override
	public double getTimeScale() {
		return this.timeScale;
	}

	@Override
	public void setTimeScale(final double timeScale) {
		this.timeScale = timeScale;
	}

	// --- Internal scheduling -----------------------------------------------

	private void scheduleNextTick() {
		if (!this.running) {
			return;
		}
		this.scheduledTick = this.executor.schedule(this::tick, TICK_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
	}

	private void tick() {
		if (!this.running) {
			return;
		}
		try {
			synchronized (this.tickLock) {
				// Checked again under the lock: passivation may have stopped the ticks while
				// this tick was waiting for it.
				if (!this.running) {
					return;
				}
				this.runOneTick();
			}
		} catch (RuntimeException e) {
			log.log(Level.SEVERE, "Simulation tick failed, stopping.", e);
			this.pause();
			return;
		}
		if (this.running) {
			this.scheduleNextTick();
		}
	}

	private void runOneTick() {
		double dt = BASE_DT * this.timeScale;
		this.useEcosystemServices.advance(dt);
		this.useRunnerModelServices.recordTick(this.useRunnerModelServices.getElapsedSimulationTime() + dt);
	}
}
