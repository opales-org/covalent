/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.userconfiguration.interaction.impl;

import org.opales.covalent.examples.rubikscubesolver.cube.CubeReadServices;
import org.opales.covalent.examples.rubikscubesolver.solver.kociemba.KociembaParameters;
import org.opales.covalent.examples.rubikscubesolver.solver.model.SolverPlaybackParameters;
import org.opales.covalent.examples.rubikscubesolver.userconfiguration.UserConfiguration;
import org.opales.covalent.examples.rubikscubesolver.userconfiguration.interaction.UserConfigurationActionKey;
import org.opales.covalent.examples.rubikscubesolver.userconfiguration.interaction.UserConfigurationInteraction;
import org.opales.covalent.examples.rubikscubesolver.userconfiguration.view.UserConfigurationViewServices;

import org.opales.covalent.container.component.annotations.AccessedParameters;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionProviderServices;

/**
 * The configuration composite's Interaction Controller: registers one {@code ActionManager} action per
 * user-editable parameter and applies each one directly to whichever function's Parameters interface
 * actually owns it: {@link KociembaParameters} for the phase 2 maximum depth,
 * {@link SolverPlaybackParameters} for the playback speed. See {@code UserConfigurationInteractionImpl} in the Neural Network
 * Trainer demo for the fuller rationale behind this cross-cutting, non-owning role, documented on
 * {@link UserConfiguration}.
 * <p>
 * Also reads {@link CubeReadServices#isSolved()} once, at activation, to push the cube's initial
 * solved status to the View - a one-shot read, not a live subscription, so the "solved" indicator is
 * not kept in sync with later moves. {@link CubeReadServices} exposes no events; following them would take
 * a subscription to {@code CubeEvents}, which this component does not make.
 */
@Implementation
public class UserConfigurationInteractionImpl implements UserConfigurationInteraction {

	@UsedServices
	private ActionProviderServices actionProviderServices;

	@UsedServices
	private UserConfigurationViewServices useUserConfigurationViewServices;

	@UsedServices
	private CubeReadServices useCubeReadServices;

	@AccessedParameters
	private KociembaParameters accessKociembaParameters;

	@AccessedParameters
	private SolverPlaybackParameters accessPlaybackParameters;

	/**
	 * Registers the two parameter actions, then pushes the current maximum depth,
	 * playback speed and solved status to the view.
	 */
	@Activation
	public void registerActions() {
		this.actionProviderServices.registerActionExecution(
				UserConfigurationActionKey.SetMaxDepth, this::setMaxDepth, Integer.class);
		this.actionProviderServices.registerActionExecution(
				UserConfigurationActionKey.SetPlaybackSpeed, this::setPlaybackSpeed, Integer.class);

		this.useUserConfigurationViewServices.displayMaxDepth(this.accessKociembaParameters.getMaxDepth());
		this.useUserConfigurationViewServices.displayPlaybackSpeed(this.accessPlaybackParameters.getMovesPerSecond());
		this.useUserConfigurationViewServices.displayCubeSolved(this.useCubeReadServices.isSolved());
	}

	/** Unregisters the actions registered at activation. */
	@Passivation
	public void unregisterActions() {
		this.actionProviderServices.unregisterActionExecution(UserConfigurationActionKey.SetMaxDepth);
		this.actionProviderServices.unregisterActionExecution(UserConfigurationActionKey.SetPlaybackSpeed);
	}

	private void setMaxDepth(final Integer maxDepth) {
		this.accessKociembaParameters.setMaxDepth(maxDepth);
		this.useUserConfigurationViewServices.displayMaxDepth(maxDepth);
	}

	private void setPlaybackSpeed(final Integer movesPerSecond) {
		this.accessPlaybackParameters.setMovesPerSecond(movesPerSecond);
		this.useUserConfigurationViewServices.displayPlaybackSpeed(movesPerSecond);
	}
}
