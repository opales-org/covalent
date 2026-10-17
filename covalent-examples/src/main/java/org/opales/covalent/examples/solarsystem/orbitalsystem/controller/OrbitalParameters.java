/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalsystem.controller;

import org.opales.covalent.examples.solarsystem.userconfiguration.UserConfiguration;

import org.opales.covalent.container.component.annotations.Parameters;

/**
 * User-editable physical constant of the orbital system itself, offered by
 * {@code OrbitalController} and accessed externally by
 * {@code UserConfiguration} - the same role {@link
 * org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkParameters} plays for
 * the network's learning rate.
 */
@Parameters
public interface OrbitalParameters {

	/**
	 * Returns the gravitational constant scaling the attraction between bodies.
	 *
	 * @return the current gravitational constant used by the simulation
	 */
	double getGravitationalConstant();

	/**
	 * Sets the gravitational constant scaling the attraction between bodies.
	 *
	 * @param gravitationalConstant the new gravitational constant to apply
	 *                              from the next step on (this demo's own
	 *                              toy unit system, not SI)
	 */
	void setGravitationalConstant(double gravitationalConstant);
}
