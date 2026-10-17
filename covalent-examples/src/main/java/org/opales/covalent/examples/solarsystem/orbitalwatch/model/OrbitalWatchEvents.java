/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalwatch.model;

import org.opales.covalent.examples.solarsystem.orbitalsystem.model.CloseApproach;
import org.opales.covalent.examples.solarsystem.orbitalwatch.view.OrbitalWatchView;

import org.opales.covalent.container.component.annotations.Events;

/**
 * Log-change notifications, produced by {@code OrbitalWatchModel} and
 * consumed by {@code OrbitalWatchView} to refresh the recorded-approaches
 * list.
 */
@Events
public interface OrbitalWatchEvents {

	/**
	 * Fired whenever a new close approach is recorded.
	 *
	 * @param approach the recorded close approach
	 */
	void onApproachRecorded(CloseApproach approach);

	/** Fired when the recorded log is cleared. */
	void onLogCleared();
}
