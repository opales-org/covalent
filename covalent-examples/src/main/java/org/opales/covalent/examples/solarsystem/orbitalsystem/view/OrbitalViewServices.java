/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalsystem.view;

import org.opales.covalent.examples.solarsystem.orbitalsystem.controller.BodyDetails;
import org.opales.covalent.examples.solarsystem.orbitalsystem.interaction.OrbitalInteraction;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Piloting commands for the orbital system view, provided by
 * {@code OrbitalView} and used by {@code OrbitalInteraction}.
 */
@Services
public interface OrbitalViewServices {

	/**
	 * Displays the result of an interactive "select a body" preview, and
	 * highlights that body in the rendering.
	 *
	 * @param details the selected body's current details
	 */
	void showBodyDetails(BodyDetails details);
}
