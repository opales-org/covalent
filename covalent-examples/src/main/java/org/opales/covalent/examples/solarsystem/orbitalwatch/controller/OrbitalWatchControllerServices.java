/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalwatch.controller;

import org.opales.covalent.examples.solarsystem.orbitalwatch.OrbitalWatch;
import org.opales.covalent.examples.solarsystem.orbitalwatch.interaction.OrbitalWatchInteraction;

import org.opales.covalent.container.component.annotations.Services;

/**
 * User-facing log control, provided by {@code OrbitalWatchController} and
 * used by {@code OrbitalWatchInteraction} to clear the recorded log
 * from the UI. Internal to the {@code OrbitalWatch} function - nothing
 * external needs it, so no separate public "OrbitalWatchServices" is
 * offered.
 */
@Services
public interface OrbitalWatchControllerServices {

	/** Clears the recorded approaches log. */
	void clear();
}
