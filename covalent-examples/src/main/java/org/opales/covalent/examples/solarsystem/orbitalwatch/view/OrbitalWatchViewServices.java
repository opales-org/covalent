/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalwatch.view;

import org.opales.covalent.examples.solarsystem.orbitalwatch.interaction.OrbitalWatchInteraction;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Piloting commands for the watch view, provided by {@code OrbitalWatchView}
 * and used by {@code OrbitalWatchInteraction} - the same transient
 * "busy" flag convention {@code DatasetViewServices#setRegenerating(boolean)}
 * uses, shown briefly around the clear action.
 */
@Services
public interface OrbitalWatchViewServices {

	/**
	 * Indicates whether the log is being cleared.
	 *
	 * @param clearing {@code true} while a clear is in progress
	 */
	void setClearing(boolean clearing);
}
