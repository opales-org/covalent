/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.userconfiguration.view;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Piloting commands for the configuration view, provided by
 * {@code UserConfigurationView} and used by
 * {@code UserConfigurationInteraction} to keep the displayed values in
 * sync with what was actually applied (initial values at activation, and
 * confirmed values after every change). See {@code UserConfigurationViewServices}
 * in the Neural Network Trainer demo for the fuller rationale behind this
 * split from {@code @Parameters}.
 */
@Services
public interface UserConfigurationViewServices {

	/**
	 * Shows the maximum depth in its spinner, without firing the value-change action.
	 *
	 * @param maxDepth the Kociemba phase 2 maximum depth to display
	 */
	void displayMaxDepth(int maxDepth);

	/**
	 * Shows the playback speed in its spinner, without firing the value-change action.
	 *
	 * @param movesPerSecond the number of solution moves played per second, to display
	 */
	void displayPlaybackSpeed(int movesPerSecond);

	/**
	 * Updates the read-only label that tells whether the cube is solved.
	 *
	 * @param solved whether the cube is currently solved, to display
	 */
	void displayCubeSolved(boolean solved);
}
