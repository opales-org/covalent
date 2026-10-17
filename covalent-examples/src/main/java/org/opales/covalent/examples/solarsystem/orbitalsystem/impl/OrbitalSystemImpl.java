/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalsystem.impl;

import org.opales.covalent.examples.solarsystem.orbitalsystem.OrbitalSystem;
import org.opales.covalent.examples.solarsystem.orbitalsystem.controller.impl.OrbitalControllerImpl;
import org.opales.covalent.examples.solarsystem.orbitalsystem.interaction.impl.OrbitalInteractionImpl;
import org.opales.covalent.examples.solarsystem.orbitalsystem.model.impl.OrbitalModelImpl;
import org.opales.covalent.examples.solarsystem.orbitalsystem.view.impl.OrbitalViewImpl;

import org.opales.covalent.container.component.annotations.Implementation;

/**
 * The {@code OrbitalSystem} composite: deploys its four MVIC parts
 * together. Only this class needs to be passed to
 * {@code container.deployComponents(...)}.
 */
@Implementation
public class OrbitalSystemImpl implements OrbitalSystem {

	@Implementation
	private OrbitalModelImpl model;

	@Implementation
	private OrbitalControllerImpl controller;

	@Implementation
	private OrbitalViewImpl view;

	@Implementation
	private OrbitalInteractionImpl interaction;
}
