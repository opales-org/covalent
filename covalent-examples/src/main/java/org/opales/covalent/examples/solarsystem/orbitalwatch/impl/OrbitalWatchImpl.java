/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalwatch.impl;

import org.opales.covalent.examples.solarsystem.orbitalwatch.OrbitalWatch;
import org.opales.covalent.examples.solarsystem.orbitalwatch.controller.impl.OrbitalWatchControllerImpl;
import org.opales.covalent.examples.solarsystem.orbitalwatch.interaction.impl.OrbitalWatchInteractionImpl;
import org.opales.covalent.examples.solarsystem.orbitalwatch.model.impl.OrbitalWatchModelImpl;
import org.opales.covalent.examples.solarsystem.orbitalwatch.view.impl.OrbitalWatchViewImpl;

import org.opales.covalent.container.component.annotations.Implementation;

/**
 * The {@code OrbitalWatch} composite: deploys its four MVIC parts together.
 * Only this class needs to be passed to {@code container.deployComponents(...)}.
 */
@Implementation
public class OrbitalWatchImpl implements OrbitalWatch {

	@Implementation
	private OrbitalWatchModelImpl model;

	@Implementation
	private OrbitalWatchControllerImpl controller;

	@Implementation
	private OrbitalWatchViewImpl view;

	@Implementation
	private OrbitalWatchInteractionImpl interaction;
}
