/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.cube.interaction.impl;

import org.opales.covalent.examples.rubikscubesolver.cube.CubeServices;
import org.opales.covalent.examples.rubikscubesolver.cube.Face;
import org.opales.covalent.examples.rubikscubesolver.cube.RotationDirection;
import org.opales.covalent.examples.rubikscubesolver.cube.controller.CubeControllerServices;
import org.opales.covalent.examples.rubikscubesolver.cube.interaction.CubeActionKey;
import org.opales.covalent.examples.rubikscubesolver.cube.interaction.CubeInteraction;
import org.opales.covalent.examples.rubikscubesolver.cube.view.CubeViewServices;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionProviderServices;

/**
 * The cube's Interaction Controller: registers this function's actions with the HMI's
 * {@code ActionManager} at activation, exactly the role {@code DatasetInteractionImpl} documents
 * in full for the Neural Network Trainer demo - the pivot that touches both {@link CubeViewServices}
 * (to command the View) and {@link CubeControllerServices} (to trigger business logic), with no JavaFX
 * code of its own.
 * <p>
 * Registers only {@code RotateFace} and {@code Scramble}: "Reset" is not one of this function's
 * actions - the widget lives in {@code SolverViewImpl} and is handled by
 * {@code SolverInteraction} - see {@code SolverActionKey#Reset}'s javadoc for why starting over
 * belongs to Solver, not Cube. This keeps {@code Cube} unaware that {@code Solver} (or any other
 * driver of the cube) exists.
 */
@Implementation
public class CubeInteractionImpl implements CubeInteraction {

	@UsedServices
	private ActionProviderServices actionProviderServices;

	@UsedServices
	private CubeViewServices useCubeViewServices;

	@UsedServices
	private CubeControllerServices useCubeControllerServices;

	/**
	 * {@link CubeServices#scramble(long)} lives on the cube's public
	 * contract, not on {@link CubeControllerServices}: this function's own
	 * "Scramble" button is, from the framework's point of view, just
	 * another caller of that public operation, exactly as
	 * {@code SolverController} is when it drives the cube
	 * programmatically.
	 */
	@UsedServices
	private CubeServices useCubeServices;

	/** Registers the {@code RotateFace} and {@code Scramble} actions with the action provider. */
	@Activation
	public void registerActions() {
		this.actionProviderServices.registerActionExecution(
				CubeActionKey.RotateFace, this::rotateFace, Face.class, RotationDirection.class);
		this.actionProviderServices.registerActionExecution(
				CubeActionKey.Scramble, this::scramble, Long.class);
	}

	/** Unregisters the actions registered at activation. */
	@Passivation
	public void unregisterActions() {
		this.actionProviderServices.unregisterActionExecution(CubeActionKey.RotateFace);
		this.actionProviderServices.unregisterActionExecution(CubeActionKey.Scramble);
	}

	private void rotateFace(final Face face, final RotationDirection direction) {
		this.useCubeControllerServices.rotateFace(face, direction);
	}

	private void scramble(final Long seed) {
		this.useCubeViewServices.setAnimating(true);
		try {
			this.useCubeServices.scramble(seed);
		} finally {
			this.useCubeViewServices.setAnimating(false);
		}
	}
}
