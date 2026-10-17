/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalwatch.model;

import org.opales.covalent.examples.solarsystem.orbitalsystem.model.CloseApproach;
import org.opales.covalent.examples.solarsystem.orbitalwatch.controller.OrbitalWatchController;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Records the watch's log of every close approach seen so far, provided by
 * {@code OrbitalWatchModel} and used by {@code OrbitalWatchController}. The
 * Model carries no logic of its own: each method here just stores the given
 * state and fires the matching {@link OrbitalWatchEvents} notification.
 */
@Services
public interface OrbitalWatchModelServices {

	/**
	 * Appends a close approach to the log, and fires
	 * {@link OrbitalWatchEvents#onApproachRecorded(CloseApproach)}.
	 *
	 * @param approach the close approach to record
	 */
	void recordApproach(CloseApproach approach);

	/**
	 * Clears the log, and fires {@link OrbitalWatchEvents#onLogCleared()}.
	 */
	void clearLog();

	/**
	 * Returns the logged close approaches in order of recording.
	 *
	 * @return every close approach recorded so far, oldest first
	 */
	CloseApproach[] getRecordedApproaches();
}
